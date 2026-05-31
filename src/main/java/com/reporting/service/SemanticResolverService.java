package com.reporting.service;

import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ReportRowDto;
import com.reporting.dto.ResolvedMetricDto;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SemanticResolverService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SemanticResolverService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, ResolvedMetricDto> resolveAll(ReportConfigDto config) {
        List<ReportRowDto> dataRows = config.getDataRows();
        if (dataRows.isEmpty()) {
            return Collections.emptyMap();
        }

        // Collect all unique measure names
        Set<String> measureNames = new HashSet<>();
        for (ReportRowDto row : dataRows) {
            if (row.source() != null && row.source().getRawSql() != null && !row.source().getRawSql().isBlank()) {
                measureNames.add(row.source().getRawSql());
            }
        }

        if (measureNames.isEmpty()) {
            List<Map.Entry<String, String>> missing = new ArrayList<>();
            for (ReportRowDto row : dataRows) {
                missing.add(new AbstractMap.SimpleEntry<>(row.rowId(), "(empty source)"));
            }
            throw new MetricNotFoundException(missing);
        }

        // Batch fetch measures
        Map<String, Map<String, Object>> measuresByName = fetchMeasures(measureNames);

        // Fetch joins
        Set<Integer> exploreIds = new HashSet<>();
        for (Map<String, Object> m : measuresByName.values()) {
            exploreIds.add((Integer) m.get("explore_id"));
        }
        Map<Integer, List<String>> joinsByExplore = fetchJoins(exploreIds);

        // Build results
        Map<String, ResolvedMetricDto> result = new HashMap<>();
        List<Map.Entry<String, String>> missing = new ArrayList<>();

        for (ReportRowDto row : dataRows) {
            if (row.source() == null || row.source().getRawSql() == null || row.source().getRawSql().isBlank()) {
                missing.add(new AbstractMap.SimpleEntry<>(row.rowId(), "(empty source)"));
                continue;
            }

            Map<String, Object> measureData = measuresByName.get(row.source().getRawSql());
            if (measureData == null) {
                missing.add(new AbstractMap.SimpleEntry<>(row.rowId(), row.source().getRawSql()));
                continue;
            }

            int exploreId = (Integer) measureData.get("explore_id");
            List<String> joinSqls = joinsByExplore.getOrDefault(exploreId, Collections.emptyList());

            result.put(row.rowId(), new ResolvedMetricDto(
                row.source() != null ? row.source().getRawSql() : "",
                (Integer) measureData.get("measure_id"),
                (String) measureData.get("sql_expr"),
                (String) measureData.get("agg_type"),
                (String) measureData.get("data_type"),
                (String) measureData.get("fact_table"),
                (String) measureData.get("fact_name"),
                (String) measureData.get("time_key"),
                exploreId,
                joinSqls
            ));
        }

        if (!missing.isEmpty()) {
            throw new MetricNotFoundException(missing);
        }

        return result;
    }

    private Map<String, Map<String, Object>> fetchMeasures(Set<String> measureNames) {
        String sql = """
            SELECT
                m.name          AS measure_name,
                m.measure_id,
                m.sql_expr,
                m.agg_type,
                m.data_type,
                v.table_ref     AS fact_table,
                v.name          AS fact_name,
                v.time_key,
                e.explore_id
            FROM reporting.sem_measure     m
            JOIN reporting.sem_view        v ON v.view_id    = m.view_id
            JOIN reporting.sem_explore     e ON e.fact_view_id = v.view_id
            WHERE m.name IN (:names)
            """;
        
        Map<String, Object> params = Map.of("names", measureNames);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);

        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put((String) row.get("measure_name"), row);
        }
        return result;
    }

    private Map<Integer, List<String>> fetchJoins(Set<Integer> exploreIds) {
        if (exploreIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String sql = """
            SELECT
                j.explore_id,
                j.join_type,
                fv.table_ref  AS from_table,
                tv.table_ref  AS to_table,
                j.join_sql
            FROM reporting.sem_join j
            JOIN reporting.sem_view fv ON fv.view_id = j.from_view_id
            JOIN reporting.sem_view tv ON tv.view_id = j.to_view_id
            WHERE j.explore_id IN (:ids)
            ORDER BY j.explore_id, j.join_id
            """;

        Map<String, Object> params = Map.of("ids", exploreIds);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);

        Map<Integer, List<String>> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            int exploreId = (Integer) row.get("explore_id");
            String joinEntry = String.format("%s JOIN %s ON %s",
                row.get("join_type"),
                row.get("to_table"),
                row.get("join_sql")
            );
            result.computeIfAbsent(exploreId, k -> new ArrayList<>()).add(joinEntry);
        }
        return result;
    }

    public static class MetricNotFoundException extends RuntimeException {
        private final List<Map.Entry<String, String>> missing;

        public MetricNotFoundException(List<Map.Entry<String, String>> missing) {
            super("Metrics not found: " + missing);
            this.missing = missing;
        }

        public List<Map.Entry<String, String>> getMissing() {
            return missing;
        }
    }
}
