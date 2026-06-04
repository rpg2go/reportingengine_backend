package com.reporting.catalog;

import java.util.Objects;

/**
 * In-memory representation of one row from {@code reporting.meta_relationship}.
 *
 * <p>A {@code MetaRelationship} models a single directed FK edge in the schema
 * dependency graph.  The edge encodes the exact SQL predicate fragment needed
 * to produce a LEFT JOIN clause between two physical tables, along with a cost
 * weight that allows {@link SchemaGraphRouter}'s pathfinder to prefer conformed
 * dimension keys over non-conformed ones when multiple paths exist.</p>
 *
 * <p>Graph semantics:</p>
 * <ul>
 *   <li><strong>Source node</strong>: {@link #getFromTable()} — the table that
 *       holds the FK column.</li>
 *   <li><strong>Target node</strong>: {@link #getToTable()} — the table that
 *       owns the PK being referenced.</li>
 *   <li><strong>Edge label</strong>: the pair ({@link #getFromColumn()},
 *       {@link #getToColumn()}) fully describes the JOIN ON predicate.</li>
 * </ul>
 *
 * <p>Instances are immutable and held in the {@link SchemaCatalogLoader} cache.</p>
 */
public final class MetaRelationship {

    /** Surrogate PK from {@code reporting.meta_relationship.relationship_id}. */
    private final int relationshipId;

    /**
     * The table that holds the FK column (the "many" side in a star schema).
     * Never {@code null} after catalog load.
     */
    private final MetaTable fromTable;

    /** Physical name of the FK column on {@link #fromTable}, e.g. {@code "customer_id"}. */
    private final String fromColumn;

    /**
     * The table that owns the PK being referenced (the "one" side).
     * Never {@code null} after catalog load.
     */
    private final MetaTable toTable;

    /** Physical name of the PK/unique column on {@link #toTable}, e.g. {@code "id"}. */
    private final String toColumn;

    /**
     * SQL JOIN type to emit when traversing this edge.
     * Stored in the catalog as {@code "LEFT"}, {@code "INNER"}, or {@code "RIGHT"};
     * defaults to {@code "LEFT"} in all seeded rows.
     */
    private final String joinType;

    /**
     * {@code true} when this edge traverses a conformed dimension key.
     * Conformed edges are preferred by the BFS/Dijkstra pathfinder because
     * they are safe to use as spine joins without risking row fan-out or
     * Cartesian products.
     */
    private final boolean conformed;

    /**
     * Dijkstra edge cost loaded from {@code reporting.meta_relationship.weight}.
     * <ul>
     *   <li>Conformed edges: weight = 1 (preferred).</li>
     *   <li>Non-conformed edges: weight = 2 (traversed only when no conformed
     *       path exists).</li>
     * </ul>
     */
    private final int weight;

    /** Optional human-readable description loaded from the catalog. */
    private final String description;

    // ─── constructor ─────────────────────────────────────────────────────────

    /**
     * Constructs a {@code MetaRelationship} from catalog row data.
     *
     * @param relationshipId surrogate PK
     * @param fromTable      the table that carries the FK (never {@code null})
     * @param fromColumn     FK column name on {@code fromTable} (never blank)
     * @param toTable        the table that owns the PK (never {@code null})
     * @param toColumn       PK/unique column name on {@code toTable} (never blank)
     * @param joinType       SQL JOIN keyword, e.g. {@code "LEFT"}
     * @param conformed      {@code true} if this edge crosses a conformed key
     * @param weight         Dijkstra cost (1 = conformed / preferred, 2 = non-conformed)
     * @param description    optional annotation; may be {@code null}
     */
    public MetaRelationship(int relationshipId,
                            MetaTable fromTable,
                            String fromColumn,
                            MetaTable toTable,
                            String toColumn,
                            String joinType,
                            boolean conformed,
                            int weight,
                            String description) {
        this.relationshipId = relationshipId;
        this.fromTable      = Objects.requireNonNull(fromTable,  "fromTable must not be null");
        this.fromColumn     = Objects.requireNonNull(fromColumn, "fromColumn must not be null");
        this.toTable        = Objects.requireNonNull(toTable,    "toTable must not be null");
        this.toColumn       = Objects.requireNonNull(toColumn,   "toColumn must not be null");
        this.joinType       = (joinType != null && !joinType.isBlank()) ? joinType.toUpperCase() : "LEFT";
        this.conformed      = conformed;
        this.weight         = weight > 0 ? weight : 1;
        this.description    = description;
    }

    // ─── public accessors ─────────────────────────────────────────────────────

    /** @return surrogate PK from {@code reporting.meta_relationship.relationship_id} */
    public int getRelationshipId() {
        return relationshipId;
    }

    /** @return the table that holds the FK column (graph source node) */
    public MetaTable getFromTable() {
        return fromTable;
    }

    /** @return physical FK column name on {@link #fromTable} */
    public String getFromColumn() {
        return fromColumn;
    }

    /** @return the table that owns the PK being referenced (graph target node) */
    public MetaTable getToTable() {
        return toTable;
    }

    /** @return physical PK/unique column name on {@link #toTable} */
    public String getToColumn() {
        return toColumn;
    }

    /**
     * @return SQL JOIN keyword ({@code "LEFT"}, {@code "INNER"}, {@code "RIGHT"})
     *         to use when emitting the join clause for this edge
     */
    public String getJoinType() {
        return joinType;
    }

    /**
     * @return {@code true} when this edge traverses a conformed dimension key;
     *         the pathfinder prioritises conformed edges to prevent fan-out
     */
    public boolean isConformed() {
        return conformed;
    }

    /**
     * @return Dijkstra edge cost — lower is preferred.
     *         Conformed edges carry weight 1; non-conformed carry weight 2.
     */
    public int getWeight() {
        return weight;
    }

    /** @return optional human-readable annotation, or {@code null} */
    public String getDescription() {
        return description;
    }

    // ─── SQL generation helper ────────────────────────────────────────────────

    /**
     * Renders the SQL JOIN clause fragment for this edge.
     *
     * <p>Example output:
     * <pre>{@code
     * LEFT JOIN analytics.dim_customers ON analytics.dim_customers.id = analytics.fact_sales.customer_id
     * }</pre>
     *
     * @return a complete, fully-qualified SQL JOIN clause string
     */
    public String toJoinClause() {
        String toQualified   = toTable.getQualifiedName();
        String fromQualified = fromTable.getQualifiedName();
        return String.format(
            "%s JOIN %s ON %s.%s = %s.%s",
            joinType,
            toQualified,
            toQualified,  toColumn,
            fromQualified, fromColumn
        );
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetaRelationship)) return false;
        MetaRelationship other = (MetaRelationship) o;
        return relationshipId == other.relationshipId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(relationshipId);
    }

    @Override
    public String toString() {
        return "MetaRelationship{" +
               "id="          + relationshipId                 +
               ", from='"     + fromTable.getQualifiedName()   + "'." + fromColumn +
               ", to='"       + toTable.getQualifiedName()     + "'." + toColumn   +
               ", type="      + joinType                       +
               ", conformed=" + conformed                      +
               ", weight="    + weight                         +
               '}';
    }
}
