package com.reporting.controller;

import com.reporting.cache.MetadataCache;
import com.reporting.catalog.SchemaCatalogLoader;
import com.reporting.catalog.MetaTable;
import com.reporting.catalog.MetaColumn;
import com.reporting.service.AnalyticsQueryDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller exposing schema and dimension metadata for the Analytics DWH.
 *
 * <p>
 * These endpoints power the report builder UI — table/column pickers,
 * filter autocomplete, join metadata, and the full database schema catalog
 * explorer.
 * All queries are read-only and target either {@code information_schema},
 * {@code pg_catalog}, or the {@code reporting.meta_*} database catalog tables.
 * </p>
 *
 * <h2>Security</h2>
 * All table and column parameters are validated against an alphanumeric-plus-
 * underscore whitelist and cross-checked against the live {@code analytics}
 * schema catalog before any dynamic SQL is executed.
 *
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@SuppressWarnings("null")
public class SchemaDiscoveryController {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MetadataCache metadataCache;
    private final SchemaCatalogLoader schemaCatalogLoader;
    private final AnalyticsQueryDispatcher analyticsQueryDispatcher;

    public SchemaDiscoveryController(NamedParameterJdbcTemplate jdbcTemplate,
            MetadataCache metadataCache,
            SchemaCatalogLoader schemaCatalogLoader,
            AnalyticsQueryDispatcher analyticsQueryDispatcher) {
        this.jdbcTemplate = jdbcTemplate;
        this.metadataCache = metadataCache;
        this.schemaCatalogLoader = schemaCatalogLoader;
        this.analyticsQueryDispatcher = analyticsQueryDispatcher;
    }

    // ─── table / column discovery ─────────────────────────────────────────────

    /**
     * Lists all base tables in the {@code analytics} schema.
     *
     * @return fully-qualified table names ({@code "analytics.table_name"})
     */
    @GetMapping("/tables")
    public ResponseEntity<List<String>> listTables() {
        Set<String> keys = metadataCache.getTableColumnsCache().keySet();
        if (!keys.isEmpty()) {
            List<String> qualifiedTables = keys.stream()
                    .filter(k -> k.startsWith("analytics."))
                    .sorted()
                    .collect(Collectors.toList());
            if (!qualifiedTables.isEmpty()) {
                return ResponseEntity.ok(qualifiedTables);
            }
        }

        String sql = "SELECT n.nspname || '.' || c.relname AS full_name " +
                "FROM pg_catalog.pg_class c " +
                "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE n.nspname = 'analytics' AND c.relkind = 'r' " +
                "ORDER BY c.relname";
        List<String> tables = jdbcTemplate.getJdbcOperations().queryForList(sql, String.class);
        return ResponseEntity.ok(tables);
    }

    /**
     * Lists all column names for the given table.
     *
     * @param table table name (qualified or unqualified; resolved via
     *              {@code meta_table} if needed)
     * @return alphabetically ordered column names, or 400 if the table cannot be
     *         resolved
     */
    @GetMapping("/table-columns")
    public ResponseEntity<List<String>> listTableColumns(@RequestParam("table") String table) {
        String resolved = resolveTableRef(table);
        if (resolved == null || !resolved.contains(".")) {
            return ResponseEntity.badRequest().build();
        }

        // Check if registered in metadata catalog, and filter by is_visible flag
        MetaTable metaTable = schemaCatalogLoader.findTable(resolved);
        if (metaTable != null) {
            List<String> visibleCols = metaTable.getColumns().stream()
                    .filter(MetaColumn::isVisible)
                    .map(MetaColumn::getColumnName)
                    .sorted()
                    .collect(Collectors.toList());
            return ResponseEntity.ok(visibleCols);
        }

        Set<String> cachedCols = metadataCache.getColumns(resolved);
        if (cachedCols != null && !cachedCols.isEmpty()) {
            List<String> sortedCols = new ArrayList<>(cachedCols);
            Collections.sort(sortedCols);
            return ResponseEntity.ok(sortedCols);
        }

        String[] parts = resolved.split("\\.");
        String schema = parts[0];
        String tableName = parts[1];

        String sql = "SELECT a.attname AS column_name " +
                "FROM pg_catalog.pg_attribute a " +
                "JOIN pg_catalog.pg_class c ON c.oid = a.attrelid " +
                "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE n.nspname = ? AND c.relname = ? " +
                "  AND a.attnum > 0 AND NOT a.attisdropped " +
                "ORDER BY a.attname";
        List<String> columns = jdbcTemplate.getJdbcOperations().queryForList(sql, String.class, schema, tableName);
        return ResponseEntity.ok(columns);
    }

    /**
     * Returns a map of {@code column_name → data_type} for the given table.
     *
     * @param table table name (qualified or unqualified)
     * @return map of column names to their PostgreSQL data types, or 400 if
     *         unresolvable
     */
    @GetMapping("/column-types")
    public ResponseEntity<Map<String, String>> getColumnTypes(@RequestParam("table") String table) {
        String resolved = resolveTableRef(table);
        if (resolved == null || !resolved.contains(".")) {
            return ResponseEntity.badRequest().build();
        }

        Map<String, String> cachedTypes = metadataCache.getColumnTypes(resolved);
        if (cachedTypes != null && !cachedTypes.isEmpty()) {
            return ResponseEntity.ok(cachedTypes);
        }

        String[] parts = resolved.split("\\.");
        String schema = parts[0];
        String tableName = parts[1];

        String sql = "SELECT a.attname AS column_name, pg_catalog.format_type(a.atttypid, a.atttypmod) AS data_type " +
                "FROM pg_catalog.pg_attribute a " +
                "JOIN pg_catalog.pg_class c ON c.oid = a.attrelid " +
                "JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE n.nspname = ? AND c.relname = ? " +
                "  AND a.attnum > 0 AND NOT a.attisdropped";

        Map<String, String> colTypes = new LinkedHashMap<>();
        jdbcTemplate.getJdbcOperations().query(sql, rs -> {
            colTypes.put(rs.getString("column_name"), rs.getString("data_type"));
        }, schema, tableName);

        return ResponseEntity.ok(colTypes);
    }

    // ─── dimension value autocomplete ─────────────────────────────────────────

    /**
     * Returns up to 100 distinct values for a dimension column (1,500 for date
     * keys).
     *
     * <p>
     * The table parameter is validated against the live analytics catalog
     * whitelist.
     * The column parameter is validated against {@code ^[a-zA-Z0-9_]+$}.
     * </p>
     *
     * @param table  table name (qualified or unqualified)
     * @param column column name to query distinct values for
     * @return ordered list of distinct string values, or 400 on validation failure
     */
    @GetMapping("/dimensions/values")
    public ResponseEntity<List<String>> getDimensionValues(
            @RequestParam("table") String table,
            @RequestParam("column") String column) {

        String resolved = resolveTableRef(table);
        if (resolved == null) {
            return ResponseEntity.badRequest().build();
        }

        // Map logical reporting_date column to physical date_key for dim_date
        String queryColumn = column;
        if ("reporting_date".equals(column) && ("dim_date".equals(table) || "analytics.dim_date".equals(resolved))) {
            queryColumn = "date_key";
        }

        // Try the cache first
        String cacheKey = (resolved + "." + queryColumn).toLowerCase();
        List<String> cachedValues = metadataCache.getCachedColumnValues(cacheKey);
        if (cachedValues != null && !cachedValues.isEmpty()) {
            log.info("MetadataCache: hit for autocomplete values: {}", cacheKey);
            return ResponseEntity.ok(cachedValues);
        }

        // Whitelist check: autocomplete queries are only allowed on visible, and either
        // filterable or value-cached columns.
        MetaColumn metaCol = schemaCatalogLoader.findColumn(resolved, queryColumn);
        if (metaCol != null) {
            if (!metaCol.isVisible() || (!metaCol.isCached() && !metaCol.isFilterable())) {
                log.warn(
                        "Performance safety block: Denied autocomplete lookup on invisible/non-filterable/non-cached column: {}.{}",
                        resolved, queryColumn);
                return ResponseEntity.badRequest().build();
            }
        } else {
            MetaTable metaTable = schemaCatalogLoader.findTable(resolved);
            if (metaTable != null) {
                log.warn("Blocked autocomplete lookup because column {}.{} does not exist in schema catalog.", resolved,
                        queryColumn);
                return ResponseEntity.badRequest().build();
            }
        }

        log.info("Fetching dimension values for table: {} (resolved: {}), column: {} (queried as: {})",
                table, resolved, column, queryColumn);

        if (!resolved.startsWith("analytics.") || !queryColumn.matches("^[a-zA-Z0-9_]+$")) {
            log.warn("Invalid table format or column regex mismatch. Table: {}, Column: {}", resolved, queryColumn);
            return ResponseEntity.badRequest().build();
        }

        // Whitelist validation against live analytics catalog
        List<String> validTables = listTables().getBody();
        if (validTables == null || !validTables.contains(resolved)) {
            log.warn("Requested table is not in the analytics catalog whitelist: {}", resolved);
            return ResponseEntity.badRequest().build();
        }

        int limit = 100;
        if ("date_key".equals(queryColumn) && "analytics.dim_date".equals(resolved)) {
            limit = 1500;
        }

        String sql = String.format(
                "SELECT DISTINCT %s FROM %s WHERE %s IS NOT NULL ORDER BY %s LIMIT %d",
                queryColumn, resolved, queryColumn, queryColumn, limit);

        List<String> values = analyticsQueryDispatcher.queryForList(sql, String.class);
        return ResponseEntity.ok(values);
    }

    // ─── schema catalog ───────────────────────────────────────────────────────

    /**
     * Returns the full database schema catalog: tables, joins, and filterable
     * columns.
     * Maps fact tables as explores and includes empty measures for frontend UI
     * compatibility.
     *
     * @return map with keys {@code views}, {@code explores}, {@code joins},
     *         {@code dimensions}, {@code measures}
     */
    @GetMapping("/schema-catalog")
    public ResponseEntity<Map<String, Object>> getSchemaCatalog() {
        Map<String, Object> model = new LinkedHashMap<>();

        model.put("views", jdbcTemplate.queryForList(
                "SELECT table_id, table_name AS name, label, schema_name || '.' || table_name AS table_ref, " +
                        "       table_type AS view_type, time_key, description " +
                        "FROM catalog_owner.meta_table ORDER BY table_name",
                Collections.emptyMap()));
        model.put("explores", jdbcTemplate.queryForList(
                "SELECT table_id AS explore_id, table_name AS name, label, table_name AS fact_view_name, description " +
                        "FROM catalog_owner.meta_table WHERE table_type = 'fact' ORDER BY table_name",
                Collections.emptyMap()));
        model.put("joins", jdbcTemplate.queryForList(
                "SELECT r.relationship_id AS join_id, ft.table_name AS explore_name, ft.table_name AS from_view, tt.table_name AS to_view, "
                        +
                        "       r.join_type, " +
                        "       tt.schema_name || '.' || tt.table_name || ' ON ' || " +
                        "       tt.schema_name || '.' || tt.table_name || '.' || r.to_column || ' = ' || " +
                        "       ft.schema_name || '.' || ft.table_name || '.' || r.from_column AS join_sql " +
                        "FROM catalog_owner.meta_relationship r " +
                        "JOIN catalog_owner.meta_table ft ON ft.table_id = r.from_table_id " +
                        "JOIN catalog_owner.meta_table tt ON tt.table_id = r.to_table_id " +
                        "ORDER BY r.relationship_id",
                Collections.emptyMap()));
        model.put("dimensions", jdbcTemplate.queryForList(
                "SELECT c.column_id, t.table_name AS view_name, c.column_name AS name, c.label, " +
                        "       t.schema_name || '.' || t.table_name || '.' || c.column_name AS column_ref, " +
                        "       c.data_type, c.description, c.is_filterable, c.is_cached, c.is_visible " +
                        "FROM catalog_owner.meta_column c " +
                        "JOIN catalog_owner.meta_table t ON t.table_id = c.table_id " +
                        "WHERE c.is_primary_key = FALSE AND c.is_visible = TRUE " +
                        "ORDER BY t.table_name, c.column_name",
                Collections.emptyMap()));
        model.put("measures", Collections.emptyList());

        return ResponseEntity.ok(model);
    }

    /**
     * Returns the dimension join metadata for a specific fact table (from
     * {@code meta_relationship}).
     *
     * @param factTable the physical fact table reference (e.g.
     *                  {@code "analytics.fact_sales"})
     * @return list of join descriptors with {@code dimView}, {@code joinType}, and
     *         {@code joinSql}
     */
    @GetMapping("/dimension-joins")
    public ResponseEntity<List<Map<String, Object>>> getDimensionJoins(@RequestParam("factTable") String factTable) {
        log.info("Fetching dimension joins for fact table: {}", factTable);

        MetaTable table = schemaCatalogLoader.findTable(factTable);
        if (table != null) {
            List<Map<String, Object>> joins = table.getOutgoingEdges().stream()
                    .map(edge -> {
                        Map<String, Object> join = new LinkedHashMap<>();
                        join.put("dimView", edge.getToTable().getTableName());
                        join.put("joinType", edge.getJoinType());
                        join.put("joinSql", edge.getToTable().getQualifiedName() + " ON " +
                                edge.getToTable().getQualifiedName() + "." + edge.getToColumn() + " = " +
                                edge.getFromTable().getQualifiedName() + "." + edge.getFromColumn());
                        return join;
                    })
                    .collect(Collectors.toList());
            return ResponseEntity.ok(joins);
        }

        String sql = """
                SELECT
                    tt.table_name AS dimView,
                    r.join_type   AS joinType,
                    tt.schema_name || '.' || tt.table_name || ' ON ' ||
                    tt.schema_name || '.' || tt.table_name || '.' || r.to_column || ' = ' ||
                    ft.schema_name || '.' || ft.table_name || '.' || r.from_column AS joinSql
                FROM catalog_owner.meta_relationship r
                JOIN catalog_owner.meta_table ft ON ft.table_id = r.from_table_id
                JOIN catalog_owner.meta_table tt ON tt.table_id = r.to_table_id
                WHERE ft.schema_name || '.' || ft.table_name = :factTable
                ORDER BY r.relationship_id
                """;
        List<Map<String, Object>> joins = jdbcTemplate.query(sql, Map.of("factTable", factTable), (rs, rowNum) -> {
            Map<String, Object> join = new LinkedHashMap<>();
            join.put("dimView", rs.getString("dimView"));
            join.put("joinType", rs.getString("joinType"));
            join.put("joinSql", rs.getString("joinSql"));
            return join;
        });
        return ResponseEntity.ok(joins);
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    /**
     * Resolves a logical table name to a fully-qualified physical table reference.
     *
     * <p>
     * If the name already contains a dot it is returned as-is. Otherwise a
     * lookup against {@code reporting.meta_table} is attempted; if that fails the
     * name is prefixed with {@code "analytics."}.
     * </p>
     *
     * @param table logical or physical table name
     * @return fully-qualified table reference
     */
    private String resolveTableRef(String table) {
        if (table == null)
            return null;
        if (table.contains("."))
            return table;
        try {
            return jdbcTemplate.getJdbcOperations().queryForObject(
                    "SELECT schema_name || '.' || table_name AS table_ref FROM catalog_owner.meta_table WHERE table_name = ?",
                    String.class, table);
        } catch (Exception e) {
            return null;
        }
    }
}
