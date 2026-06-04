package com.reporting.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Autonomous, catalog-driven graph pathfinder that resolves multi-hop LEFT JOIN
 * chains between a fact table and any set of dimension tables at query-generation
 * time.
 *
 * <h2>Algorithm</h2>
 * <p>The router runs a <strong>weighted Dijkstra BFS</strong> over the directed
 * graph loaded by {@link SchemaCatalogLoader}.  Each graph node is a
 * {@link MetaTable}; each directed edge is a {@link MetaRelationship}.  Edge
 * costs are:</p>
 * <ul>
 *   <li><strong>weight = 1</strong> — conformed dimension key (preferred).</li>
 *   <li><strong>weight = 2</strong> — non-conformed FK (traversed only when no
 *       lower-cost path exists).</li>
 * </ul>
 *
 * <p>Fan-out / Cartesian-product protection: if two or more path candidates
 * reach the same target table, the algorithm selects the one with the lowest
 * accumulated weight (most conformed hops).  Cycle detection uses a visited-set
 * populated per-target-search, not globally, so unrelated searches cannot
 * poison each other's state.</p>
 *
 * <h2>Fallback</h2>
 * <p>If {@link SchemaCatalogLoader} is unavailable (e.g. migration 010 has not
 * been applied), the router returns an empty list and logs a warning.  The
 * calling {@code SqlGeneratorService} must then handle the absent clauses
 * gracefully (e.g. emitting no JOIN).</p>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * List<String> joins = router.computeJoinClauses(
 *     "analytics.fact_banking_transactions",
 *     Set.of("analytics.dim_customers", "analytics.dim_countries")
 * );
 * // Returns:
 * //   "LEFT JOIN analytics.dim_accounts ON analytics.dim_accounts.id = analytics.fact_banking_transactions.account_id"
 * //   "LEFT JOIN analytics.dim_customers ON analytics.dim_customers.id = analytics.dim_accounts.customer_id"
 * //   "LEFT JOIN analytics.dim_countries ON analytics.dim_countries.iso_code = analytics.dim_customers.country_code"
 * }</pre>
 */
@Service
public class SchemaGraphRouter {

    private static final Logger log = LoggerFactory.getLogger(SchemaGraphRouter.class);

    private final SchemaCatalogLoader catalog;

    // ─── constructor ─────────────────────────────────────────────────────────

    /**
     * @param catalog the startup-loaded schema catalog; injected by Spring
     */
    public SchemaGraphRouter(SchemaCatalogLoader catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog must not be null");
    }

    // ─── public API ───────────────────────────────────────────────────────────

    /**
     * Computes the ordered list of SQL LEFT JOIN clauses needed to connect
     * {@code baseTable} to every table in {@code targetTables}.
     *
     * <p>The method runs one Dijkstra search per target table.  For each target
     * it records the cheapest (lowest-weight) path of edges, then performs a
     * topological merge of all paths into a single, de-duplicated ordered list
     * that can be appended verbatim to a SQL FROM clause.</p>
     *
     * @param baseTable    fully-qualified or unqualified name of the fact table
     *                     that forms the root of each path search
     *                     (e.g. {@code "analytics.fact_banking_transactions"})
     * @param targetTables set of dimension table names (fully-qualified or
     *                     unqualified) that must be reachable from
     *                     {@code baseTable} via FK hops
     * @return an ordered, de-duplicated list of SQL JOIN clause strings.
     *         Returns an empty list when the catalog is unavailable or when
     *         no path can be found to a requested target.
     */
    public List<String> computeJoinClauses(String baseTable, Set<String> targetTables) {
        if (!catalog.isCatalogAvailable()) {
            log.warn("SchemaGraphRouter.computeJoinClauses: catalog unavailable — returning empty join list.");
            return Collections.emptyList();
        }
        if (baseTable == null || baseTable.isBlank()) {
            return Collections.emptyList();
        }
        if (targetTables == null || targetTables.isEmpty()) {
            return Collections.emptyList();
        }

        MetaTable root = catalog.findTable(baseTable);
        if (root == null) {
            log.warn("SchemaGraphRouter: base table '{}' not found in catalog.", baseTable);
            return Collections.emptyList();
        }

        // Collect unique resolved targets (skip the base table itself)
        List<MetaTable> resolvedTargets = new ArrayList<>();
        for (String targetRef : targetTables) {
            if (targetRef == null || targetRef.isBlank()) continue;
            MetaTable target = catalog.findTable(targetRef);
            if (target == null) {
                log.warn("SchemaGraphRouter: target table '{}' not found in catalog; skipping.", targetRef);
                continue;
            }
            if (target.getTableId() == root.getTableId()) {
                continue; // already the base table — no join needed
            }
            if (!resolvedTargets.contains(target)) {
                resolvedTargets.add(target);
            }
        }

        if (resolvedTargets.isEmpty()) {
            return Collections.emptyList();
        }

        // For each target, find the cheapest edge path from root → target.
        // Collect the full ordered sequence of edges across all paths, then
        // de-duplicate while preserving topological dependency order.
        LinkedHashSet<String> orderedJoinClauses = new LinkedHashSet<>();

        for (MetaTable target : resolvedTargets) {
            List<MetaRelationship> path = dijkstra(root, target);
            if (path == null || path.isEmpty()) {
                log.warn("SchemaGraphRouter: no path found from '{}' to '{}'; " +
                         "this dimension will be joined without a routing clause.",
                         root.getQualifiedName(), target.getQualifiedName());
                continue;
            }

            if (log.isDebugEnabled()) {
                log.debug("SchemaGraphRouter: path {} → {} = {}",
                          root.getQualifiedName(),
                          target.getQualifiedName(),
                          describePath(path));
            }

            for (MetaRelationship edge : path) {
                orderedJoinClauses.add(edge.toJoinClause());
            }
        }

        return new ArrayList<>(orderedJoinClauses);
    }

    // ─── Dijkstra implementation ──────────────────────────────────────────────

    /**
     * Runs Dijkstra's shortest-path algorithm on the directed schema graph,
     * returning the ordered list of {@link MetaRelationship} edges that form
     * the minimum-cost path from {@code source} to {@code target}.
     *
     * <p>Implementation notes:</p>
     * <ul>
     *   <li>A {@link PriorityQueue} ordered by accumulated cost drives the
     *       frontier expansion.</li>
     *   <li>Each {@link DijkstraState} records the table reached, the
     *       accumulated cost, and the edge that was traversed to arrive there
     *       (enabling path reconstruction without a separate predecessor map
     *       for the typical small graph sizes encountered here).</li>
     *   <li>The visited set prevents re-expansion of already-settled nodes,
     *       which also eliminates cycles in schemas where a table could be
     *       reached via multiple paths.</li>
     *   <li>Edge cost ties are broken by {@code is_conformed}: a conformed
     *       edge is emitted at weight = 1, so it will always be preferred over
     *       a non-conformed edge of weight = 2.</li>
     * </ul>
     *
     * @param source origin table (the fact table or an intermediate dimension)
     * @param target dimension table to reach
     * @return ordered edge list forming the shortest path, or {@code null}
     *         if the target is unreachable
     */
    private List<MetaRelationship> dijkstra(MetaTable source, MetaTable target) {
        // Priority queue ordered by accumulated path cost (ascending)
        PriorityQueue<DijkstraState> frontier = new PriorityQueue<>();
        // Settled nodes – once a node is settled, the cheapest path to it is known
        Set<Integer> settled = new HashSet<>();
        // Best-known cost to reach each table_id
        Map<Integer, Integer> bestCost = new HashMap<>();

        frontier.add(new DijkstraState(source, 0, new ArrayList<>()));
        bestCost.put(source.getTableId(), 0);

        while (!frontier.isEmpty()) {
            DijkstraState current = frontier.poll();

            // Skip if we've already found a cheaper path to this node
            if (settled.contains(current.table.getTableId())) {
                continue;
            }
            settled.add(current.table.getTableId());

            // Goal check
            if (current.table.getTableId() == target.getTableId()) {
                return current.pathEdges;
            }

            // Expand outgoing edges from the current node
            for (MetaRelationship edge : current.table.getOutgoingEdges()) {
                MetaTable neighbor = edge.getToTable();
                int neighborId     = neighbor.getTableId();

                if (settled.contains(neighborId)) {
                    continue; // already settled with optimal cost
                }

                int newCost = current.cost + edge.getWeight();
                int knownCost = bestCost.getOrDefault(neighborId, Integer.MAX_VALUE);

                if (newCost < knownCost) {
                    bestCost.put(neighborId, newCost);
                    // Build the extended path
                    List<MetaRelationship> extendedPath = new ArrayList<>(current.pathEdges);
                    extendedPath.add(edge);
                    frontier.add(new DijkstraState(neighbor, newCost, extendedPath));
                }
            }
        }

        // Target was unreachable from source
        return null;
    }

    // ─── internal state carrier ───────────────────────────────────────────────

    /**
     * Immutable frontier state used by the Dijkstra priority queue.
     *
     * <p>Implements {@link Comparable} so that the {@link PriorityQueue}
     * returns the lowest-cost state first.</p>
     */
    private static final class DijkstraState implements Comparable<DijkstraState> {

        /** The table node this state represents. */
        final MetaTable table;

        /** Accumulated edge-weight cost of the path from the source to this node. */
        final int cost;

        /**
         * Ordered list of edges traversed from the source to reach {@link #table}.
         * Copied (not shared) on each expansion to ensure immutability.
         */
        final List<MetaRelationship> pathEdges;

        DijkstraState(MetaTable table, int cost, List<MetaRelationship> pathEdges) {
            this.table     = table;
            this.cost      = cost;
            this.pathEdges = pathEdges;
        }

        @Override
        public int compareTo(DijkstraState other) {
            return Integer.compare(this.cost, other.cost);
        }
    }

    // ─── diagnostic helpers ───────────────────────────────────────────────────

    /**
     * Produces a compact human-readable path description for debug logging.
     *
     * @param path ordered list of relationship edges
     * @return a string like {@code "fact_banking_transactions→dim_accounts→dim_customers"}
     */
    private String describePath(List<MetaRelationship> path) {
        if (path.isEmpty()) return "(empty)";
        StringBuilder sb = new StringBuilder();
        sb.append(path.get(0).getFromTable().getTableName());
        for (MetaRelationship edge : path) {
            sb.append(" →[").append(edge.getFromColumn()).append("]→ ");
            sb.append(edge.getToTable().getTableName());
        }
        return sb.toString();
    }
}
