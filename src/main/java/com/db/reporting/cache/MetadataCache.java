package com.db.reporting.cache;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import com.db.reporting.service.AnalyticsQueryDispatcher;
import com.db.reporting.config.DatabaseSchemaProperties;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-startup metadata cache that pre-loads slow, per-request DB lookups
 * into shared, in-memory maps available for the lifetime of the JVM.
 *
 * <h2>What is cached</h2>
 * <ul>
 *   <li><b>tableColumnsCache</b> — {@code "schema.table" → Set<column_name>} for every
 *       table in the {@code analytics} schema.  Eliminates per-request
 *       {@code information_schema.columns} queries in {@link com.db.reporting.service.SqlGeneratorService}.</li>
 *   <li><b>timeKeyCache</b> — {@code table_ref → time_key} from {@code reporting.meta_table}.
 *       Eliminates per-CTE {@code SELECT time_key FROM meta_table} queries.</li>
 *   <li><b>metaTableRefs</b> — ordered set of distinct {@code table_ref} values from
 *       {@code reporting.meta_table}.  Used for heuristic table-detection during
 *       metric resolution in {@link com.db.reporting.service.ReportConfigService}.</li>
 * </ul>
 *
 * <h2>Fault tolerance</h2>
 * <p>If any section fails to load (e.g. the catalog tables do not yet exist),
 * a warning is logged and the cache section is left empty.  Callers are expected
 * to handle empty-cache results gracefully (they will fall back to their existing
 * live-query paths or defaults).</p>
 *
 * <h2>Refresh</h2>
 * <p>Call {@link #reload()} to force a full cache refresh without restarting the
 * application (e.g. after a schema migration or seeding operation).</p>
 */
@Component
public class MetadataCache {

    private static final Logger log = LoggerFactory.getLogger(MetadataCache.class);

    private final JdbcTemplate jdbc;
    private final AnalyticsQueryDispatcher analyticsQueryDispatcher;
    private final DatabaseSchemaProperties dbProperties;

    // ── column metadata: "schema.table" or "table" → lowercase column names ──
    private final Map<String, Set<String>> tableColumnsCache = new ConcurrentHashMap<>();

    // ── column types metadata: "schema.table" or "table" → Map<column_name, data_type> ──
    private final Map<String, Map<String, String>> tableColumnTypesCache = new ConcurrentHashMap<>();

    // ── time key per fact/view table: table_ref → time_key column name ────────
    private final Map<String, String> timeKeyCache = new ConcurrentHashMap<>();

    // ── ordered set of distinct meta_table table_ref values ────────────────────
    private volatile Set<String> metaTableRefs = Collections.emptySet();

    public MetadataCache(JdbcTemplate jdbc, AnalyticsQueryDispatcher analyticsQueryDispatcher, @org.springframework.lang.Nullable DatabaseSchemaProperties dbProperties) {
        this.jdbc = jdbc;
        this.analyticsQueryDispatcher = analyticsQueryDispatcher;
        this.dbProperties = dbProperties != null ? dbProperties : new DatabaseSchemaProperties();
    }

    // ─── lifecycle ────────────────────────────────────────────────────────────

    @PostConstruct
    public void load() {
        log.info("MetadataCache: starting pre-load...");
        loadTableColumns();
        loadTimeKeys();
        loadMetaTableRefs();
        log.info("MetadataCache: pre-load complete — {} tables, {} time-keys, {} meta-table refs.",
                tableColumnsCache.size(), timeKeyCache.size(), metaTableRefs.size());
    }

    /**
     * Forces a full cache refresh. Useful after migrations or seed data changes.
     */
    public void reload() {
        log.info("MetadataCache: explicit reload triggered.");
        tableColumnsCache.clear();
        tableColumnTypesCache.clear();
        timeKeyCache.clear();
        metaTableRefs = Collections.emptySet();
        load();
    }

    // ─── section 1: analytics schema column sets ──────────────────────────────

    private void loadTableColumns() {
        try {
            // Load all columns and types for all tables in the analytics schema in one query using pg_catalog
            String schemaName = dbProperties.getAnalyticsSchema();
            jdbc.query(
                "SELECT n.nspname AS schema_name, c.relname AS table_name, a.attname AS column_name, " +
                "       pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type " +
                "FROM pg_catalog.pg_attribute a " +
                "JOIN pg_catalog.pg_class c ON c.oid = a.attrelid " +
                "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE n.nspname = ? AND c.relkind = 'r' " +
                "  AND a.attnum > 0 AND NOT a.attisdropped " +
                "ORDER BY c.relname, a.attname",
                rs -> {
                    String table = rs.getString("table_name").toLowerCase();
                    String col   = rs.getString("column_name").toLowerCase();
                    String type  = rs.getString("data_type");
                    
                    String fullKey = schemaName + "." + table;

                    // Index by unqualified name
                    tableColumnsCache.computeIfAbsent(table, k -> new HashSet<>()).add(col);
                    tableColumnTypesCache.computeIfAbsent(table, k -> new LinkedHashMap<>()).put(col, type);

                    // Index by qualified name
                    tableColumnsCache.computeIfAbsent(fullKey, k -> new HashSet<>()).add(col);
                    tableColumnTypesCache.computeIfAbsent(fullKey, k -> new LinkedHashMap<>()).put(col, type);
                },
                schemaName
            );
            log.debug("MetadataCache: loaded column and type metadata for {} table keys.", tableColumnsCache.size());
        } catch (Exception ex) {
            log.warn("MetadataCache: failed to load table column metadata. Cause: {}", ex.getMessage());
        }
    }

    // ─── section 2: meta_table time keys ───────────────────────────────────────

    private void loadTimeKeys() {
        try {
            jdbc.query(
                "SELECT schema_name || '.' || table_name AS table_ref, time_key FROM " + dbProperties.getCatalogSchema() + ".meta_table WHERE time_key IS NOT NULL AND is_cached = TRUE",
                rs -> {
                    String tableRef = rs.getString("table_ref");
                    String timeKey  = rs.getString("time_key");
                    if (tableRef != null && timeKey != null) {
                        timeKeyCache.put(tableRef.trim(), timeKey.trim());
                        // Also index by short (unqualified) name
                        if (tableRef.contains(".")) {
                            String shortName = tableRef.substring(tableRef.lastIndexOf('.') + 1).trim();
                            timeKeyCache.putIfAbsent(shortName, timeKey.trim());
                        }
                    }
                }
            );
            log.debug("MetadataCache: loaded {} time-key entries.", timeKeyCache.size());
        } catch (Exception ex) {
            log.warn("MetadataCache: failed to load time-key cache from meta_table. getTimeKeyForTable() will fall back to live queries. Cause: {}", ex.getMessage());
        }
    }

    // ─── section 3: meta_table table refs ──────────────────────────────────────

    private void loadMetaTableRefs() {
        try {
            Set<String> viewTables = new LinkedHashSet<>();
            jdbc.query(
                "SELECT DISTINCT schema_name || '.' || table_name AS table_ref FROM " + dbProperties.getCatalogSchema() + ".meta_table",
                rs -> {
                    String tbl = rs.getString("table_ref");
                    if (tbl != null && !tbl.isBlank()) {
                        viewTables.add(tbl.trim());
                    }
                }
            );
            metaTableRefs = Collections.unmodifiableSet(viewTables);
            log.debug("MetadataCache: loaded {} meta-table refs.", metaTableRefs.size());
        } catch (Exception ex) {
            log.warn("MetadataCache: failed to load meta_table refs. Cause: {}", ex.getMessage());
        }
    }

    // ─── public read API ──────────────────────────────────────────────────────

    /**
     * Returns the set of lowercase column names for the given table, or {@code null}
     * if the table is not in the cache (caller should fall back to a live query).
     *
     * @param tableRef fully-qualified ({@code "analytics.fact_sales"}) or
     *                 unqualified ({@code "fact_sales"}) table name
     * @return unmodifiable set of lowercase column names, or {@code null} if unknown
     */
    public Set<String> getColumns(String tableRef) {
        if (tableRef == null || tableRef.isBlank()) return null;
        Set<String> cols = tableColumnsCache.get(tableRef.trim().toLowerCase());
        return cols != null ? Collections.unmodifiableSet(cols) : null;
    }

    /**
     * Returns the mapping of column names to their formats/types for the given table,
     * or {@code null} if the table is not in the cache.
     *
     * @param tableRef table reference
     * @return unmodifiable map of column names to types, or {@code null}
     */
    public Map<String, String> getColumnTypes(String tableRef) {
        if (tableRef == null || tableRef.isBlank()) return null;
        Map<String, String> types = tableColumnTypesCache.get(tableRef.trim().toLowerCase());
        return types != null ? Collections.unmodifiableMap(types) : null;
    }

    /**
     * Returns the shared, mutable {@code tableColumnsCache} map.
     * Callers may add entries for tables not pre-loaded (e.g. reporting schema tables),
     * so that {@code computeIfAbsent} in the SQL generator can persist those lookups.
     */
    public Map<String, Set<String>> getTableColumnsCache() {
        return tableColumnsCache;
    }

    /**
     * Returns the shared, mutable tableColumnTypesCache map.
     */
    public Map<String, Map<String, String>> getTableColumnTypesCache() {
        return tableColumnTypesCache;
    }

    /**
     * Looks up the {@code time_key} column for a given table reference.
     *
     * @param tableRef fully-qualified or unqualified table name
     * @return the time key column name, or {@code null} if not found in cache
     */
    public String getTimeKey(String tableRef) {
        if (tableRef == null || tableRef.isBlank()) return null;
        String result = timeKeyCache.get(tableRef.trim());
        if (result == null && tableRef.contains(".")) {
            result = timeKeyCache.get(tableRef.substring(tableRef.lastIndexOf('.') + 1).trim());
        }
        return result;
    }

    /**
     * Returns the ordered set of distinct {@code table_ref} values from
     * {@code catalog_owner.meta_table}.
     */
    public Set<String> getMetaTableRefs() {
        return metaTableRefs;
    }
}
