package com.reporting.catalog;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-startup catalog loader that reads the three schema registry
 * tables ({@code catalog.meta_table}, {@code catalog.meta_column},
 * {@code catalog.meta_relationship}) via direct JDBC queries and assembles
 * an optimized, fully-linked in-memory cache.
 *
 * <h2>Cache structure</h2>
 * <ul>
 * <li>{@link #tableById} — primary index: {@code table_id → MetaTable}</li>
 * <li>{@link #tableByName} — secondary lookup by fully-qualified name
 * ({@code "analytics.fact_sales → MetaTable"})</li>
 * </ul>
 *
 * <p>
 * Both maps are exposed as unmodifiable views. All mutation happens once,
 * inside the {@link PostConstruct} initializer, making the cache effectively
 * immutable for the remainder of the application lifetime.
 * </p>
 *
 * <h2>Fault tolerance</h2>
 * <p>
 * If the catalog tables do not yet exist (e.g. migration {@code 010} has
 * not been applied), the loader logs a warning and leaves the cache empty.
 * {@link SchemaGraphRouter} will fall back to a direct
 * {@code information_schema}
 * lookup path rather than crashing the application.
 * </p>
 */
@Component
public class SchemaCatalogLoader {

    private static final Logger log = LoggerFactory.getLogger(SchemaCatalogLoader.class);

    private final JdbcTemplate jdbc;

    // ─── primary in-memory indexes ────────────────────────────────────────────

    /** Maps {@code table_id → MetaTable}. Populated during {@link #load()}. */
    private final Map<Integer, MetaTable> tableById = new ConcurrentHashMap<>();

    /**
     * Maps fully-qualified name ({@code "schema.table"}) → {@link MetaTable}.
     * Both keys are stored in lower-case to allow case-insensitive lookup.
     */
    private final Map<String, MetaTable> tableByName = new ConcurrentHashMap<>();

    // ─── constructor ─────────────────────────────────────────────────────────

    /**
     * @param jdbc Spring {@link JdbcTemplate} for direct JDBC queries;
     *             injected by Spring at startup
     */
    public SchemaCatalogLoader(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ─── lifecycle ────────────────────────────────────────────────────────────

    /**
     * Loads the full catalog from the database into memory.
     *
     * <p>
     * Execution order is critical:
     * </p>
     * <ol>
     * <li>Load all {@link MetaTable} nodes and index them.</li>
     * <li>Load all {@link MetaColumn} instances and attach them to their
     * parent {@code MetaTable} nodes.</li>
     * <li>Load all {@link MetaRelationship} edges, resolve the
     * {@code from_table_id} / {@code to_table_id} FK references to live
     * {@code MetaTable} objects, and attach each edge as an outgoing edge
     * on its source node.</li>
     * </ol>
     */
    @PostConstruct
    public void load() {
        log.info("SchemaCatalogLoader: starting catalog load from catalog.meta_* tables...");
        try {
            loadTables();
            loadColumns();
            loadRelationships();
            log.info("SchemaCatalogLoader: catalog loaded — {} tables, {} relationships.",
                    tableById.size(),
                    tableById.values().stream()
                            .mapToInt(t -> t.getOutgoingEdges().size())
                            .sum());
        } catch (Exception ex) {
            // Non-fatal: the application can still start; router will degrade gracefully.
            log.warn("SchemaCatalogLoader: could not load schema catalog (migration 010 may not be applied yet). " +
                    "Dynamic join routing will be unavailable. Cause: {}", ex.getMessage());
            tableById.clear();
            tableByName.clear();
        }
    }

    // ─── step 1: tables ───────────────────────────────────────────────────────

    /**
     * Queries {@code catalog.meta_table} and populates {@link #tableById}
     * and {@link #tableByName}.
     *
     * <p>
     * Uses a {@code RowCallbackHandler} to build objects row-by-row
     * without intermediate list allocations.
     * </p>
     */
    private void loadTables() {
        final String sql = "SELECT table_id, schema_name, table_name, table_type, time_key, description, is_cached " +
                "FROM   catalog.meta_table " +
                "ORDER  BY table_id";

        jdbc.query(sql, rs -> {
            int tableId = rs.getInt("table_id");
            String schemaName = rs.getString("schema_name");
            String tableName = rs.getString("table_name");
            String typeRaw = rs.getString("table_type");
            String timeKey = rs.getString("time_key");
            String desc = rs.getString("description");
            boolean isCached = rs.getBoolean("is_cached");
            if (rs.wasNull()) {
                isCached = true;
            }

            MetaTable.TableType tableType;
            try {
                tableType = MetaTable.TableType.of(typeRaw);
            } catch (IllegalArgumentException e) {
                log.warn("SchemaCatalogLoader: unknown table_type '{}' for table_id {}; defaulting to 'dimension'.",
                        typeRaw, tableId);
                tableType = MetaTable.TableType.dimension;
            }

            MetaTable node = new MetaTable(tableId, schemaName, tableName, tableType, timeKey, desc, isCached);
            tableById.put(tableId, node);
            tableByName.put(node.getQualifiedName().toLowerCase(), node);
            // Also index by unqualified name for case-insensitive lookups from report
            // config
            tableByName.put(tableName.toLowerCase(), node);
        });

        log.debug("SchemaCatalogLoader: loaded {} table nodes.", tableById.size());
    }

    // ─── step 2: columns ──────────────────────────────────────────────────────

    /**
     * Queries {@code catalog.meta_column} and attaches each column to its
     * parent {@link MetaTable} node already in {@link #tableById}.
     */
    private void loadColumns() {
        final String sql = "SELECT column_id, table_id, column_name, data_type, " +
                "       is_primary_key, is_foreign_key, description, is_cached, is_filterable, is_visible " +
                "FROM   catalog.meta_column " +
                "ORDER  BY table_id, column_id";

        // Collect counts for diagnostic logging
        final int[] count = { 0 };

        jdbc.query(sql, rs -> {
            int columnId = rs.getInt("column_id");
            int tableId = rs.getInt("table_id");
            String colName = rs.getString("column_name");
            String dataType = rs.getString("data_type");
            boolean isPk = rs.getBoolean("is_primary_key");
            boolean isFk = rs.getBoolean("is_foreign_key");
            String desc = rs.getString("description");
            boolean isCached = rs.getBoolean("is_cached");
            if (rs.wasNull()) {
                isCached = false;
            }
            boolean isFilterable = rs.getBoolean("is_filterable");
            if (rs.wasNull()) {
                isFilterable = false;
            }
            boolean isVisible = rs.getBoolean("is_visible");
            if (rs.wasNull()) {
                isVisible = true;
            }

            MetaTable parent = tableById.get(tableId);
            if (parent == null) {
                log.warn("SchemaCatalogLoader: orphaned column_id={} references unknown table_id={}; skipping.",
                        columnId, tableId);
                return;
            }

            MetaColumn col = new MetaColumn(columnId, tableId, colName, dataType, isPk, isFk, desc, isCached, isFilterable, isVisible);
            parent.addColumn(col);
            count[0]++;
        });

        log.debug("SchemaCatalogLoader: loaded {} column entries.", count[0]);
    }

    // ─── step 3: relationships (edges) ────────────────────────────────────────

    /**
     * Queries {@code catalog.meta_relationship} and attaches each edge as an
     * outgoing edge on its source {@link MetaTable} node.
     *
     * <p>
     * The query joins back to {@code catalog.meta_table} to retrieve the
     * fully-qualified names of both endpoint tables, but the Java side resolves
     * the {@link MetaTable} references from the already-loaded {@link #tableById}
     * map to avoid redundant object creation.
     * </p>
     */
    private void loadRelationships() {
        final String sql = "SELECT r.relationship_id, " +
                "       r.from_table_id, r.from_column, " +
                "       r.to_table_id,   r.to_column, " +
                "       r.join_type,     r.is_conformed, r.weight, r.description " +
                "FROM   catalog.meta_relationship r " +
                "ORDER  BY r.relationship_id";

        final int[] count = { 0 };

        jdbc.query(sql, rs -> {
            int relId = rs.getInt("relationship_id");
            int fromId = rs.getInt("from_table_id");
            String fromCol = rs.getString("from_column");
            int toId = rs.getInt("to_table_id");
            String toCol = rs.getString("to_column");
            String joinType = rs.getString("join_type");
            boolean isConformed = rs.getBoolean("is_conformed");
            int weight = rs.getInt("weight");
            String desc = rs.getString("description");

            MetaTable fromTable = tableById.get(fromId);
            MetaTable toTable = tableById.get(toId);

            if (fromTable == null || toTable == null) {
                log.warn("SchemaCatalogLoader: relationship_id={} references missing table(s) " +
                        "from_table_id={}, to_table_id={}; skipping.",
                        relId, fromId, toId);
                return;
            }

            MetaRelationship edge = new MetaRelationship(
                    relId, fromTable, fromCol, toTable, toCol,
                    joinType, isConformed, weight, desc);
            fromTable.addOutgoingEdge(edge);
            count[0]++;
        });

        log.debug("SchemaCatalogLoader: loaded {} relationship edges.", count[0]);
    }

    // ─── public query API ─────────────────────────────────────────────────────

    /**
     * Looks up a {@link MetaTable} by its fully-qualified or unqualified name.
     *
     * <p>
     * The lookup is case-insensitive. Both {@code "analytics.fact_sales"}
     * and {@code "fact_sales"} resolve to the same node.
     * </p>
     *
     * @param tableRef fully-qualified ({@code "schema.table"}) or
     *                 unqualified ({@code "table"}) name
     * @return the matching {@link MetaTable}, or {@code null} if not found
     */
    public MetaTable findTable(String tableRef) {
        if (tableRef == null || tableRef.isBlank()) {
            return null;
        }
        return tableByName.get(tableRef.trim().toLowerCase());
    }

    /**
     * Looks up a column inside a registered table by name in a case-insensitive manner.
     *
     * @param tableRef table name (qualified or unqualified)
     * @param columnName column name
     * @return the matching MetaColumn, or null if not found
     */
    public MetaColumn findColumn(String tableRef, String columnName) {
        MetaTable table = findTable(tableRef);
        if (table == null || columnName == null) {
            return null;
        }
        for (MetaColumn col : table.getColumns()) {
            if (columnName.equalsIgnoreCase(col.getColumnName())) {
                return col;
            }
        }
        return null;
    }

    /**
     * Returns an unmodifiable view of all registered tables indexed by
     * their surrogate {@code table_id}.
     *
     * @return immutable map of {@code table_id → MetaTable}
     */
    public Map<Integer, MetaTable> getAllTablesById() {
        return Collections.unmodifiableMap(tableById);
    }

    /**
     * Returns an unmodifiable view of all registered tables indexed by their
     * lower-cased fully-qualified name ({@code "schema.table"}).
     *
     * @return immutable map of {@code "schema.table" → MetaTable}
     */
    public Map<String, MetaTable> getAllTablesByName() {
        return Collections.unmodifiableMap(tableByName);
    }

    /**
     * Returns {@code true} when the catalog was successfully loaded and contains
     * at least one table entry. Used by {@link SchemaGraphRouter} to decide
     * whether to use graph-based routing or fall back to a legacy strategy.
     *
     * @return {@code true} if the catalog is non-empty
     */
    public boolean isCatalogAvailable() {
        return !tableById.isEmpty();
    }

    /**
     * Forces a full reload of the catalog from the database.
     *
     * <p>
     * Intended for use in integration tests and administrative endpoints
     * where the catalog data needs to be refreshed without restarting the
     * application.
     * </p>
     */
    public void reload() {
        log.info("SchemaCatalogLoader: explicit reload triggered.");
        tableById.clear();
        tableByName.clear();
        load();
    }
}
