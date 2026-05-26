package com.reporting.service;

import com.reporting.dto.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class SqlGeneratorService {

    public String generate(ReportConfigDto config, Map<String, ResolvedMetricDto> resolved) {
        if (resolved == null || resolved.isEmpty()) {
            return "SELECT 'No data rows' as message";
        }

        // 1. Group metrics by Explore
        Map<Integer, List<Map.Entry<String, ResolvedMetricDto>>> explores = new HashMap<>();
        for (Map.Entry<String, ResolvedMetricDto> entry : resolved.entrySet()) {
            explores.computeIfAbsent(entry.getValue().exploreId(), k -> new ArrayList<>()).add(entry);
        }

        // 2. Build CTE bodies
        List<String> cteDefs = new ArrayList<>();
        for (Map.Entry<Integer, List<Map.Entry<String, ResolvedMetricDto>>> entry : explores.entrySet()) {
            cteDefs.add(buildExploreCteBody(config, entry.getKey(), entry.getValue()));
        }

        // 3. Final Query Assembly
        StringBuilder sql = new StringBuilder("WITH\n");
        sql.append(String.join(",\n", cteDefs)).append("\n");
        sql.append("SELECT\n");

        List<String> selectFields = new ArrayList<>();
        for (Integer eid : explores.keySet()) {
            selectFields.add(String.format("  cte_%d.*", eid));
        }
        sql.append(String.join(",\n", selectFields)).append("\n");
        sql.append("FROM (SELECT 1 as dummy) d\n");

        for (Integer eid : explores.keySet()) {
            sql.append(String.format("LEFT JOIN cte_%d ON TRUE\n", eid));
        }

        return sql.toString();
    }

    private String buildExploreCteBody(ReportConfigDto config, int exploreId, List<Map.Entry<String, ResolvedMetricDto>> rowMetrics) {
        ResolvedMetricDto firstMetric = rowMetrics.get(0).getValue();
        String factTable = firstMetric.factTable();
        String timeKey = firstMetric.timeKey();
        List<String> joins = firstMetric.joinSqls();

        List<String> selectClauses = new ArrayList<>();
        for (Map.Entry<String, ResolvedMetricDto> entry : rowMetrics) {
            String rid = entry.getKey();
            ResolvedMetricDto metric = entry.getValue();
            ReportRowDto row = config.getRow(rid);

            for (ColumnDefDto col : config.getSqlColumns()) {
                LocalDate[] boundaries = DateUtils.getPeriodBoundaries(
                    config.getReferenceDate(),
                    col.colType(),
                    col.periodOffset(),
                    col.rollingN()
                );
                LocalDate start = boundaries[0];
                LocalDate end = boundaries[1];

                String rawExpr = metric.sqlExpr().trim();
                int startParen = rawExpr.indexOf("(");
                int endParen = rawExpr.lastIndexOf(")");
                String innerExpr;
                if (startParen != -1 && endParen != -1) {
                    innerExpr = rawExpr.substring(startParen + 1, endParen).trim();
                } else {
                    innerExpr = rawExpr.trim();
                }

                String aggFn = metric.aggType().toUpperCase();
                String distinctClause = "";
                if (innerExpr.toUpperCase().startsWith("DISTINCT ")) {
                    distinctClause = "DISTINCT ";
                    innerExpr = innerExpr.substring(9).trim();
                }

                String fill;
                if (aggFn.startsWith("SUM")) {
                    fill = "0";
                } else {
                    fill = "NULL";
                }

                String filterClause = "";
                if (row.filterExpr() != null && !row.filterExpr().isBlank()) {
                    validateFilterExpr(row.filterExpr());
                    filterClause = " AND (" + row.filterExpr() + ")";
                }

                String clause = String.format(
                    "%s(%sCASE WHEN %s >= '%s' AND %s <= '%s'%s THEN %s ELSE %s END)",
                    aggFn,
                    distinctClause,
                    timeKey,
                    start.toString(),
                    timeKey,
                    end.toString(),
                    filterClause,
                    innerExpr,
                    fill
                );
                selectClauses.add(String.format("  %s AS metric_%s_%s", clause, rid, col.colId()));
            }
        }

        String joinStr = (joins != null && !joins.isEmpty()) ? "\n" + String.join("\n", joins) : "";

        return String.format(
            "cte_%d AS (\n  SELECT\n  %s\n  FROM %s%s\n)",
            exploreId,
            String.join(",\n  ", selectClauses),
            factTable,
            joinStr
        );
    }

    private void validateFilterExpr(String expr) {
        if (expr == null || expr.isBlank()) {
            return;
        }
        String upper = expr.toUpperCase();
        if (upper.contains(";") || upper.contains("--") || upper.contains("/*") ||
            upper.contains("UNION") || upper.contains("INSERT") || upper.contains("UPDATE") ||
            upper.contains("DELETE") || upper.contains("DROP") || upper.contains("ALTER") ||
            upper.contains("TRUNCATE") || upper.contains("GRANT") || upper.contains("REVOKE") ||
            upper.contains("EXECUTE") || upper.contains("XP_CMDSHELL")) {
            throw new IllegalArgumentException("Invalid or dangerous SQL sequences in filter expression: " + expr);
        }
        
        // Match parentheses to prevent breaking out of the generated CASE WHEN statement
        int openParen = 0;
        for (int i = 0; i < expr.length(); i++) {
            if (expr.charAt(i) == '(') openParen++;
            else if (expr.charAt(i) == ')') {
                openParen--;
                if (openParen < 0) {
                    throw new IllegalArgumentException("Unmatched parentheses in filter expression: " + expr);
                }
            }
        }
        if (openParen != 0) {
            throw new IllegalArgumentException("Unmatched parentheses in filter expression: " + expr);
        }
    }
}
