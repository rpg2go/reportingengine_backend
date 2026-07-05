package com.reporting.cache;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
 *       {@code information_schema.columns} queries in {@link com.reporting.service.SqlGeneratorService}.</li>
 *   <li><b>timeKeyCache</b> — {@code table_ref → time_key} from {@code reporting.meta_table}.
 *       Eliminates per-CTE {@code SELECT time_key FROM meta_table} queries.</li>
 *   <li><b>metaTableRefs</b> — ordered set of distinct {@code table_ref} values from
 *       {@code reporting.meta_table}.  Used for heuristic table-detection during
 *       metric resolution in {@link com.reporting.service.ReportConfigService}.</li>
 * </ul>
 *
 * <h2>Fault tolerance</h2>
 * <p>If any section fails to load (e.g. the semantic tables do not yet exist),
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

    // ── column metadata: "schema.table" or "table" → lowercase column names ──
    private final Map<String, Set<String>> tableColumnsCache = new ConcurrentHashMap<>();

    // ── time key per fact/view table: table_ref → time_key column name ────────
    private final Map<String, String> timeKeyCache = new ConcurrentHashMap<>();

    // ── ordered set of distinct meta_table table_ref values ────────────────────
    private volatile Set<String> metaTableRefs = Collections.emptySet();

    public MetadataCache(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
        timeKeyCache.clear();
        metaTableRefs = Collections.emptySet();
        load();
    }

    // ─── section 1: analytics schema column sets ──────────────────────────────

    private void loadTableColumns() {
        try {
            // Load all columns for all tables in the analytics schema in one query
            jdbc.query(
                "SELECT table_name, column_name " +
                "FROM information_schema.columns " +
                "WHERE table_schema = 'analytics' " +
                "ORDER BY table_name, ordinal_position",
                rs -> {
                    String table = rs.getString("table_name").toLowerCase();
                    String col   = rs.getString("column_name").toLowerCase();
                    // Index by both unqualified and qualified names
                    tableColumnsCache.computeIfAbsent(table, k -> new HashSet<>()).add(col);
                    tableColumnsCache.computeIfAbsent("analytics." + table, k -> new HashSet<>()).add(col);
                }
            );
            log.debug("MetadataCache: loaded column metadata for {} table keys.", tableColumnsCache.size());
        } catch (Exception ex) {
            log.warn("MetadataCache: failed to load table column metadata. columnExists() will fall back to live queries. Cause: {}", ex.getMessage());
        }
    }

    // ─── section 2: meta_table time keys ───────────────────────────────────────

    private void loadTimeKeys() {
        try {
            jdbc.query(
                "SELECT schema_name || '.' || table_name AS table_ref, time_key FROM reporting.meta_table WHERE time_key IS NOT NULL",
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
                "SELECT DISTINCT schema_name || '.' || table_name AS table_ref FROM reporting.meta_table",
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
     * Returns the shared, mutable {@code tableColumnsCache} map.
     * Callers may add entries for tables not pre-loaded (e.g. reporting schema tables),
     * so that {@code computeIfAbsent} in the SQL generator can persist those lookups.
     */
    public Map<String, Set<String>> getTableColumnsCache() {
        return tableColumnsCache;
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
     * {@code reporting.meta_table}.
     */
    public Set<String> getMetaTableRefs() {
        return metaTableRefs;
    }
}
