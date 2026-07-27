package com.db.reporting.controller;

import com.db.reporting.service.ColumnFilterCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller exposing cached column filter dropdown values for dimension and fact tables.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/metadata/filters")
public class ColumnFilterCacheController {

    private final ColumnFilterCacheService filterCacheService;

    public ColumnFilterCacheController(ColumnFilterCacheService filterCacheService) {
        this.filterCacheService = filterCacheService;
    }

    /**
     * Endpoint fetching pre-cached distinct filter values for dropdown components.
     * Served straight out of Caffeine memory (<5ms latency).
     *
     * @param tableName table name (e.g. dim_products, fact_sales)
     * @param columnName column name (e.g. product_type, channel_code)
     * @return JSON response payload containing tableName, columnName, count, and values list
     */
    @GetMapping("/{tableName}/{columnName}")
    public ResponseEntity<Map<String, Object>> getCachedFilterValues(
            @PathVariable String tableName,
            @PathVariable String columnName) {

        log.info("REST request for cached column filter values: table [{}], column [{}]", tableName, columnName);

        List<String> values = filterCacheService.getFilterValues(tableName, columnName);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("tableName", tableName);
        response.put("columnName", columnName);
        response.put("count", values.size());
        response.put("values", values);

        return ResponseEntity.ok(response);
    }
}
