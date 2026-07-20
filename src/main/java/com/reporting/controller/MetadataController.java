package com.reporting.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;
import com.reporting.cache.MetadataCache;
import com.reporting.catalog.SchemaCatalogLoader;
import com.reporting.catalog.MetaTable;
import com.reporting.catalog.MetaColumn;
import com.reporting.service.AnalyticsQueryDispatcher;

import java.util.List;
import java.util.Collections;

@Slf4j
@RestController
@RequestMapping("/api/metadata")
@SuppressWarnings("null")
public class MetadataController {

    private final JdbcTemplate jdbcTemplate;
    private final MetadataCache metadataCache;
    private final SchemaCatalogLoader schemaCatalogLoader;
    private final AnalyticsQueryDispatcher analyticsQueryDispatcher;

    public MetadataController(JdbcTemplate jdbcTemplate, MetadataCache metadataCache, SchemaCatalogLoader schemaCatalogLoader, AnalyticsQueryDispatcher analyticsQueryDispatcher) {
        this.jdbcTemplate = jdbcTemplate;
        this.metadataCache = metadataCache;
        this.schemaCatalogLoader = schemaCatalogLoader;
        this.analyticsQueryDispatcher = analyticsQueryDispatcher;
    }

    @GetMapping("/distinct-values")
    public ResponseEntity<List<String>> getDistinctValues(
            @RequestParam("table") String table,
            @RequestParam("column") String column) {
        
        log.info("Requesting distinct values for table: {}, column: {}", table, column);

        if (table == null || table.trim().isEmpty() || column == null || column.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Collections.emptyList());
        }

        // Validate column against ^[a-zA-Z0-9_]+$
        if (!column.matches("^[a-zA-Z0-9_]+$")) {
            log.warn("Security Alert: Invalid column name block: {}", column);
            return ResponseEntity.badRequest().body(Collections.emptyList());
        }

        String resolvedTable = resolveTableRef(table);
        if (resolvedTable == null) {
            if (table.contains(".")) {
                resolvedTable = table;
            } else {
                resolvedTable = "analytics." + table;
            }
        }

        // Validate table parts (split by .) against ^[a-zA-Z0-9_]+$
        String[] tableParts = resolvedTable.split("\\.");
        if (tableParts.length == 0 || tableParts.length > 2) {
            log.warn("Security Alert: Invalid table name format: {}", resolvedTable);
            return ResponseEntity.badRequest().body(Collections.emptyList());
        }
        for (String part : tableParts) {
            if (!part.matches("^[a-zA-Z0-9_]+$")) {
                log.warn("Security Alert: Invalid table name block: {}", resolvedTable);
                return ResponseEntity.badRequest().body(Collections.emptyList());
            }
        }

        // Try the cache first
        String cacheKey = (resolvedTable + "." + column).toLowerCase();
        List<String> cachedValues = metadataCache.getCachedColumnValues(cacheKey);
        if (cachedValues != null && !cachedValues.isEmpty()) {
            log.info("MetadataCache: hit for distinct values: {}", cacheKey);
            return ResponseEntity.ok(cachedValues);
        }

        // Whitelist check: autocomplete queries are only allowed on visible, and either filterable or value-cached columns.
        MetaColumn metaCol = schemaCatalogLoader.findColumn(resolvedTable, column);
        if (metaCol != null) {
            if (!metaCol.isVisible() || (!metaCol.isCached() && !metaCol.isFilterable())) {
                log.warn("Performance safety block: Denied autocomplete lookup on invisible/non-filterable/non-cached column: {}.{}", resolvedTable, column);
                return ResponseEntity.badRequest().body(Collections.emptyList());
            }
        } else {
            MetaTable metaTable = schemaCatalogLoader.findTable(resolvedTable);
            if (metaTable != null) {
                log.warn("Blocked autocomplete lookup because column {}.{} does not exist in schema catalog.", resolvedTable, column);
                return ResponseEntity.badRequest().body(Collections.emptyList());
            }
        }

        try {
            // Build the SQL query securely using the validated table and column
            String castType = analyticsQueryDispatcher.isSitActive() ? "STRING" : "TEXT";
            String sql = String.format(
                "SELECT DISTINCT CAST(%s AS %s) FROM %s WHERE %s IS NOT NULL ORDER BY 1 LIMIT 100",
                column, castType, resolvedTable, column
            );

            List<String> values = analyticsQueryDispatcher.queryForList(sql, String.class);
            return ResponseEntity.ok(values);
        } catch (Exception e) {
            log.error("Failed to query distinct values for {}.{}", resolvedTable, column, e);
            return ResponseEntity.status(500).body(Collections.emptyList());
        }
    }

    private String resolveTableRef(String table) {
        if (table == null) {
            return null;
        }
        if (table.contains(".")) {
            return table;
        }
        String sql = "SELECT schema_name || '.' || table_name AS table_ref FROM catalog.meta_table WHERE table_name = ?";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, table);
        } catch (Exception e) {
            return null;
        }
    }
}
