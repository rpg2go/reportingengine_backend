package com.reporting.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST controller exposing schema and dimension metadata for the Analytics DWH.
 *
 * <p>These endpoints power the report builder UI — table/column pickers,
 * filter autocomplete, join metadata, and the full semantic model explorer.
 * All queries are read-only and target either {@code information_schema},
 * {@code pg_catalog}, or the {@code reporting.sem_*} semantic layer tables.</p>
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
public class SchemaDiscoveryController {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SchemaDiscoveryController(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ─── table / column discovery ─────────────────────────────────────────────

    /**
     * Lists all base tables in the {@code analytics} schema.
     *
     * @return fully-qualified table names ({@code "analytics.table_name"})
     */
    @GetMapping("/tables")
    public ResponseEntity<List<String>> listTables() {
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
     * @param table table name (qualified or unqualified; resolved via {@code sem_view} if needed)
     * @return alphabetically ordered column names, or 400 if the table cannot be resolved
     */
    @GetMapping("/table-columns")
    public ResponseEntity<List<String>> listTableColumns(@RequestParam("table") String table) {
        String resolved = resolveTableRef(table);
        if (resolved == null || !resolved.contains(".")) {
            return ResponseEntity.badRequest().build();
        }
        String[] parts = resolved.split("\\.");
        String schema    = parts[0];
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
     * @return map of column names to their PostgreSQL data types, or 400 if unresolvable
     */
    @GetMapping("/column-types")
    public ResponseEntity<Map<String, String>> getColumnTypes(@RequestParam("table") String table) {
        String resolved = resolveTableRef(table);
        if (resolved == null || !resolved.contains(".")) {
            return ResponseEntity.badRequest().build();
        }
        String[] parts = resolved.split("\\.");
        String schema    = parts[0];
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
     * Returns up to 100 distinct values for a dimension column (1,500 for date keys).
     *
     * <p>The table parameter is validated against the live analytics catalog whitelist.
     * The column parameter is validated against {@code ^[a-zA-Z0-9_]+$}.</p>
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
            queryColumn, resolved, queryColumn, queryColumn, limit
        );

        List<String> values = jdbcTemplate.getJdbcOperations().query(sql, (rs, rowNum) -> {
            Object val = rs.getObject(1);
            return val != null ? val.toString() : "";
        });
        return ResponseEntity.ok(values);
    }

    // ─── semantic model ───────────────────────────────────────────────────────

    /**
     * Returns the full {@code sem_*} semantic model: views, explores, joins,
     * dimensions, and measures.
     *
     * @return map with keys {@code views}, {@code explores}, {@code joins},
     *         {@code dimensions}, {@code measures}
     */
    @GetMapping("/semantic-model")
    public ResponseEntity<Map<String, Object>> getSemanticModel() {
        Map<String, Object> model = new LinkedHashMap<>();

        model.put("views", jdbcTemplate.queryForList(
            "SELECT view_id, name, label, table_ref, view_type, primary_key, time_key, description " +
            "FROM reporting.sem_view ORDER BY name",
            Collections.emptyMap()
        ));
        model.put("explores", jdbcTemplate.queryForList(
            "SELECT e.explore_id, e.name, e.label, v.name AS fact_view_name, e.sql_always_where " +
            "FROM reporting.sem_explore e " +
            "JOIN reporting.sem_view v ON v.view_id = e.fact_view_id ORDER BY e.name",
            Collections.emptyMap()
        ));
        model.put("joins", jdbcTemplate.queryForList(
            "SELECT j.join_id, e.name AS explore_name, fv.name AS from_view, tv.name AS to_view, " +
            "       j.join_sql, j.join_type " +
            "FROM reporting.sem_join j " +
            "JOIN reporting.sem_explore e  ON e.explore_id  = j.explore_id " +
            "JOIN reporting.sem_view fv    ON fv.view_id    = j.from_view_id " +
            "JOIN reporting.sem_view tv    ON tv.view_id    = j.to_view_id " +
            "ORDER BY j.join_id",
            Collections.emptyMap()
        ));
        model.put("dimensions", jdbcTemplate.queryForList(
            "SELECT d.dimension_id, v.name AS view_name, d.name, d.label, d.column_ref, d.data_type, d.description " +
            "FROM reporting.sem_dimension d " +
            "JOIN reporting.sem_view v ON v.view_id = d.view_id ORDER BY v.name, d.name",
            Collections.emptyMap()
        ));

        return ResponseEntity.ok(model);
    }

    /**
     * Returns the dimension join metadata for a specific fact table (from {@code sem_join}).
     *
     * @param factTable the physical fact table reference (e.g. {@code "analytics.fact_sales"})
     * @return list of join descriptors with {@code dimView}, {@code joinType}, and {@code joinSql}
     */
    @GetMapping("/dimension-joins")
    public ResponseEntity<List<Map<String, Object>>> getDimensionJoins(@RequestParam("factTable") String factTable) {
        log.info("Fetching dimension joins for fact table: {}", factTable);
        String sql = """
            SELECT
                tv.name  AS dimView,
                j.join_type AS joinType,
                j.join_sql  AS joinSql
            FROM reporting.sem_join j
            JOIN reporting.sem_explore e  ON e.explore_id  = j.explore_id
            JOIN reporting.sem_view fv    ON fv.view_id    = e.fact_view_id
            JOIN reporting.sem_view tv    ON tv.view_id    = j.to_view_id
            WHERE fv.table_ref = :factTable
            ORDER BY j.join_id
            """;
        List<Map<String, Object>> joins = jdbcTemplate.query(sql, Map.of("factTable", factTable), (rs, rowNum) -> {
            Map<String, Object> join = new LinkedHashMap<>();
            join.put("dimView",   rs.getString("dimView"));
            join.put("joinType",  rs.getString("joinType"));
            join.put("joinSql",   rs.getString("joinSql"));
            return join;
        });
        return ResponseEntity.ok(joins);
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    /**
     * Resolves a logical table name to a fully-qualified physical table reference.
     *
     * <p>If the name already contains a dot it is returned as-is. Otherwise a
     * lookup against {@code reporting.sem_view} is attempted; if that fails the
     * name is prefixed with {@code "analytics."}.</p>
     *
     * @param table logical or physical table name
     * @return fully-qualified table reference
     */
    private String resolveTableRef(String table) {
        if (table == null) return null;
        if (table.contains(".")) return table;
        try {
            String ref = jdbcTemplate.getJdbcOperations().queryForObject(
                "SELECT table_ref FROM reporting.sem_view WHERE name = ?",
                String.class, table
            );
            if (ref != null) return ref;
        } catch (Exception e) {
            // sem_view not yet populated or table not found — fall through
        }
        // Default: assume analytics schema
        return "analytics." + table;
    }
}
