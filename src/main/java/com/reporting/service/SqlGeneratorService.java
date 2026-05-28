package com.reporting.service;

import com.reporting.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
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

        List<FilterCondition> generalFilters = parseGeneralFilters(config.getGeneralFilters());
        List<String> compiledFilters = new ArrayList<>();
        for (FilterCondition cond : generalFilters) {
            String sqlCond = compileFilterCondition(cond);
            if (sqlCond != null && !sqlCond.isBlank()) {
                compiledFilters.add("(" + sqlCond + ")");
            }
        }
        
        String whereClause = "";
        if (!compiledFilters.isEmpty()) {
            whereClause = "\n  WHERE " + String.join(" AND ", compiledFilters);
        }

        String joinStr = (joins != null && !joins.isEmpty()) ? "\n" + String.join("\n", joins) : "";

        return String.format(
            "cte_%d AS (\n  SELECT\n  %s\n  FROM %s%s%s\n)",
            exploreId,
            String.join(",\n  ", selectClauses),
            factTable,
            joinStr,
            whereClause
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

    private List<FilterCondition> parseGeneralFilters(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, new TypeReference<List<FilterCondition>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse general filters JSON: " + json, e);
        }
    }

    public String compileFilterCondition(FilterCondition cond) {
        if (cond == null) {
            return "";
        }
        
        String dimTable = cond.getDimTable();
        String attribute = cond.getAttribute();
        String operator = cond.getOperator();
        String value = cond.getValue();
        
        if (attribute == null || attribute.isBlank()) {
            throw new IllegalArgumentException("Filter attribute cannot be blank");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("Filter operator cannot be blank");
        }
        
        if (dimTable != null && !dimTable.isBlank() && !dimTable.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid table name in filter: " + dimTable);
        }
        if (!attribute.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid column name in filter: " + attribute);
        }
        
        String col = (dimTable != null && !dimTable.isBlank()) ? (dimTable.trim() + "." + attribute.trim()) : attribute.trim();
        String op = operator.trim().toLowerCase();
        
        String result;
        switch (op) {
            case "=":
            case "is": {
                String escapedVal = (value != null ? value : "").replace("'", "''");
                result = String.format("%s = '%s'", col, escapedVal);
                break;
            }
            case "is not":
            case "!=": {
                String escapedVal = (value != null ? value : "").replace("'", "''");
                result = String.format("(%s <> '%s' OR %s IS NULL)", col, escapedVal, col);
                break;
            }
            case "like": {
                String escapedVal = (value != null ? value : "").replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                result = String.format("%s LIKE '%%%s%%' ESCAPE '\\'", col, escapedVal);
                break;
            }
            case "not like": {
                String escapedVal = (value != null ? value : "").replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                result = String.format("(%s NOT LIKE '%%%s%%' ESCAPE '\\' OR %s IS NULL)", col, escapedVal, col);
                break;
            }
            case "starts with": {
                String escapedVal = (value != null ? value : "").replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                result = String.format("%s LIKE '%s%%' ESCAPE '\\'", col, escapedVal);
                break;
            }
            case "ends with": {
                String escapedVal = (value != null ? value : "").replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                result = String.format("%s LIKE '%%%s' ESCAPE '\\'", col, escapedVal);
                break;
            }
            case "is blank": {
                result = String.format("(%s IS NULL OR TRIM(%s) = '')", col, col);
                break;
            }
            case "is not blank": {
                result = String.format("(%s IS NOT NULL AND TRIM(%s) <> '')", col, col);
                break;
            }
            case "is null": {
                result = String.format("%s IS NULL", col);
                break;
            }
            case "is not null": {
                result = String.format("%s IS NOT NULL", col);
                break;
            }
            case "in": {
                String valStr = value != null ? value : "";
                String[] parts = valStr.split(",");
                List<String> list = new ArrayList<>();
                for (String part : parts) {
                    list.add("'" + part.trim().replace("'", "''") + "'");
                }
                if (list.isEmpty()) {
                    result = String.format("%s IN (NULL)", col);
                } else {
                    result = String.format("%s IN (%s)", col, String.join(", ", list));
                }
                break;
            }
            case ">": {
                String escapedVal = (value != null ? value : "").replace("'", "''");
                result = String.format("%s > '%s'", col, escapedVal);
                break;
            }
            case ">=": {
                String escapedVal = (value != null ? value : "").replace("'", "''");
                result = String.format("%s >= '%s'", col, escapedVal);
                break;
            }
            case "<": {
                String escapedVal = (value != null ? value : "").replace("'", "''");
                result = String.format("%s < '%s'", col, escapedVal);
                break;
            }
            case "<=": {
                String escapedVal = (value != null ? value : "").replace("'", "''");
                result = String.format("%s <= '%s'", col, escapedVal);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported filter operator: " + operator);
        }
        
        validateFilterExpr(result);
        return result;
    }

    public static class FilterCondition {
        private String dimTable;
        private String attribute;
        private String operator;
        private String value;

        public FilterCondition() {}

        public FilterCondition(String dimTable, String attribute, String operator, String value) {
            this.dimTable = dimTable;
            this.attribute = attribute;
            this.operator = operator;
            this.value = value;
        }

        public String getDimTable() { return dimTable; }
        public void setDimTable(String dimTable) { this.dimTable = dimTable; }
        public String getAttribute() { return attribute; }
        public void setAttribute(String attribute) { this.attribute = attribute; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}
