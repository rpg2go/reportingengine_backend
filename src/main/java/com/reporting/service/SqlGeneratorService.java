package com.reporting.service;

import com.reporting.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class SqlGeneratorService {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public SqlGeneratorService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String generateMatrixQuery(ReportConfigDto config) {
        return generate(config, Collections.emptyMap());
    }

    public String generate(ReportConfigDto config, Map<String, ResolvedMetricDto> resolved) {
        List<String> selectQueries = new ArrayList<>();

        // Parse Filters
        List<FilterCondition> generalFilters = parseGeneralFilters(config.getGeneralFilters());
        List<FilterCondition> quickFilters = parseGeneralFilters(config.getQuickFilters());

        String defaultTable = config.getSourceTable();
        if (defaultTable == null || defaultTable.isBlank()) {
            defaultTable = "analytics.fact_sales"; // fallback default
        }

        // 1. Determine unique tables used across all rows
        Set<String> uniqueTables = new LinkedHashSet<>();
        uniqueTables.add(defaultTable.trim());

        if (config.getRows() != null) {
            for (ReportRowDto row : config.getRows()) {
                if (row.isDataRow() && row.source() != null) {
                    String tbl = row.source().getTable();
                    if (tbl != null && !tbl.isBlank()) {
                        uniqueTables.add(tbl.trim());
                    }
                }
            }
        }

        // 2. Build CTE definitions for each unique table
        Map<String, String> tableToCteName = new HashMap<>();
        List<String> cteDefinitions = new ArrayList<>();
        Map<String, Set<String>> tableColumnsCache = new HashMap<>();

        for (String table : uniqueTables) {
            String cteName;
            if (isSameTable(table, defaultTable)) {
                cteName = "base_data";
            } else {
                String localName = table;
                if (localName.contains(".")) {
                    localName = localName.substring(localName.lastIndexOf(".") + 1);
                }
                cteName = "base_data_" + localName;
            }
            tableToCteName.put(normalizeTableName(table), cteName);

            // Filter columns that belong to this table (or are joins/dimTables)
            List<String> compiledFilters = new ArrayList<>();
            for (FilterCondition cond : generalFilters) {
                if (isSameTable(table, defaultTable)) {
                    String sqlCond = compileFilterCondition(cond);
                    if (sqlCond != null && !sqlCond.isBlank()) {
                        compiledFilters.add("(" + sqlCond + ")");
                    }
                } else if (cond.getDimTable() != null && !cond.getDimTable().isBlank()) {
                    String sqlCond = compileFilterCondition(cond);
                    if (sqlCond != null && !sqlCond.isBlank()) {
                        compiledFilters.add("(" + sqlCond + ")");
                    }
                } else if (columnExists(table, cond.getAttribute(), tableColumnsCache)) {
                    String sqlCond = compileFilterCondition(cond);
                    if (sqlCond != null && !sqlCond.isBlank()) {
                        compiledFilters.add("(" + sqlCond + ")");
                    }
                }
            }
            for (FilterCondition cond : quickFilters) {
                if (isSameTable(table, defaultTable)) {
                    String sqlCond = compileFilterCondition(cond);
                    if (sqlCond != null && !sqlCond.isBlank()) {
                        compiledFilters.add("(" + sqlCond + ")");
                    }
                } else if (cond.getDimTable() != null && !cond.getDimTable().isBlank()) {
                    String sqlCond = compileFilterCondition(cond);
                    if (sqlCond != null && !sqlCond.isBlank()) {
                        compiledFilters.add("(" + sqlCond + ")");
                    }
                } else if (columnExists(table, cond.getAttribute(), tableColumnsCache)) {
                    String sqlCond = compileFilterCondition(cond);
                    if (sqlCond != null && !sqlCond.isBlank()) {
                        compiledFilters.add("(" + sqlCond + ")");
                    }
                }
            }

            String whereClause = "";
            if (!compiledFilters.isEmpty()) {
                whereClause = "\n    WHERE " + String.join(" AND ", compiledFilters);
            }

            cteDefinitions.add(String.format("  %s AS (\n    SELECT * FROM %s%s\n  )", cteName, table, whereClause));
        }

        // 3. Build Select Statements for Data Rows and Non-Calc Columns
        if (config.getRows() != null && config.getColumns() != null) {
            for (ReportRowDto row : config.getRows()) {
                if (!row.isDataRow()) {
                    continue;
                }

                MeasureDefinition mdef = row.source();
                if (mdef == null) {
                    continue;
                }

                String rowTable = mdef.getTable();
                if (rowTable == null || rowTable.isBlank()) {
                    rowTable = defaultTable;
                }
                rowTable = rowTable.trim();

                String cteName = tableToCteName.get(normalizeTableName(rowTable));
                if (cteName == null) {
                    cteName = "base_data";
                }

                String timeKey = getTimeKeyForTable(rowTable);

                String rowFilter = row.filterExpr();
                String filterClause = "";
                if (rowFilter != null && !rowFilter.isBlank()) {
                    String compiledRowFilter = compileRowFilter(rowFilter);
                    if (compiledRowFilter != null && !compiledRowFilter.isBlank()) {
                        filterClause = " AND (" + compiledRowFilter + ")";
                    }
                }

                for (ColumnDefDto col : config.getColumns()) {
                    if (col.colType() == Enums.ColType.CALC) {
                        continue;
                    }
                    if (!row.isEnabledFor(col.colId())) {
                        continue;
                    }

                    LocalDate[] boundaries = DateUtils.getPeriodBoundaries(
                        config.getReferenceDate(),
                        col.colType(),
                        col.periodOffset(),
                        col.rollingN()
                    );
                    LocalDate start = boundaries[0];
                    LocalDate end = boundaries[1];

                    String dateConstraint = String.format("%s >= '%s' AND %s <= '%s'", timeKey, start.toString(), timeKey, end.toString());

                    String clause = "";
                    if ("visual".equalsIgnoreCase(mdef.getMode())) {
                        String agg = mdef.getAggregation() != null ? mdef.getAggregation().trim().toUpperCase() : "SUM";
                        String originalColName = mdef.getTargetColumn() != null ? mdef.getTargetColumn().trim() : "";

                        if (("SUM".equals(agg) || "AVG".equals(agg)) && !isNumericColumn(rowTable, originalColName)) {
                            throw new IllegalArgumentException("Column '" + originalColName + "' in table " + rowTable + " is not numeric and cannot be aggregated with " + agg);
                        }

                        String colName = originalColName;
                        if (rowTable != null && !rowTable.isBlank()) {
                            String shortTbl = rowTable;
                            if (rowTable.contains(".")) {
                                shortTbl = rowTable.substring(rowTable.lastIndexOf(".") + 1);
                            }
                            String escapedFull = java.util.regex.Pattern.quote(rowTable.trim()) + "\\.";
                            String escapedShort = java.util.regex.Pattern.quote(shortTbl.trim()) + "\\.";
                            colName = colName.replaceAll("(?i)" + escapedFull, cteName + ".");
                            colName = colName.replaceAll("(?i)" + escapedShort, cteName + ".");
                        }

                        String fill = "SUM".equals(agg) ? "0" : "NULL";
                        if ("COUNT_DISTINCT".equals(agg)) {
                            clause = String.format(
                                "COUNT(DISTINCT CASE WHEN %s%s THEN %s ELSE %s END)",
                                dateConstraint,
                                filterClause,
                                colName,
                                fill
                            );
                        } else {
                            clause = String.format(
                                "%s(CASE WHEN %s%s THEN %s ELSE %s END)",
                                agg,
                                dateConstraint,
                                filterClause,
                                colName,
                                fill
                            );
                        }
                    } else { // "raw" mode
                        String raw = mdef.getRawSql();
                        if (raw == null) {
                            raw = "";
                        }
                        if (rowTable != null && !rowTable.isBlank()) {
                            String shortTbl = rowTable;
                            if (rowTable.contains(".")) {
                                shortTbl = rowTable.substring(rowTable.lastIndexOf(".") + 1);
                            }
                            String escapedFull = java.util.regex.Pattern.quote(rowTable.trim()) + "\\.";
                            String escapedShort = java.util.regex.Pattern.quote(shortTbl.trim()) + "\\.";
                            raw = raw.replaceAll("(?i)" + escapedFull, cteName + ".");
                            raw = raw.replaceAll("(?i)" + escapedShort, cteName + ".");
                        }
                        raw = makeDivisionSafe(raw);
                        validateFilterExpr(raw);

                        boolean hasAgg = false;
                        String[] functions = {"SUM", "AVG", "COUNT", "MIN", "MAX"};
                        String upperRaw = raw.toUpperCase();
                        String fill = "0";
                        for (String fn : functions) {
                            if (upperRaw.contains(fn + "(")) {
                                hasAgg = true;
                                if ("SUM".equals(fn)) {
                                    fill = "0";
                                } else {
                                    fill = "NULL";
                                }
                                break;
                            }
                        }

                        if (hasAgg) {
                            clause = injectConstraintsIntoRawSql(raw, dateConstraint + filterClause, fill);
                        } else {
                            clause = String.format(
                                "SUM(CASE WHEN %s%s THEN (%s) ELSE 0 END)",
                                dateConstraint,
                                filterClause,
                                raw
                            );
                        }
                    }

                    String select = String.format(
                        "SELECT '%s' AS row_id, '%s' AS col_id, CAST(%s AS DOUBLE PRECISION) AS val FROM %s",
                        row.rowId().toUpperCase(),
                        col.colId().toUpperCase(),
                        clause,
                        cteName
                    );
                    selectQueries.add(select);
                }
            }
        }

        if (selectQueries.isEmpty()) {
            return String.format(
                "WITH base_data AS (\n  SELECT * FROM %s\n)\nSELECT '' AS row_id, '' AS col_id, 0.0::DOUBLE PRECISION AS val WHERE FALSE",
                defaultTable
            );
        }

        if (cteDefinitions.size() == 1) {
            StringBuilder sql = new StringBuilder("WITH ");
            sql.append(cteDefinitions.get(0).trim()).append("\n");
            sql.append(String.join("\nUNION ALL\n", selectQueries));
            return sql.toString();
        }

        StringBuilder sql = new StringBuilder("WITH\n");
        sql.append(String.join(",\n", cteDefinitions)).append("\n");
        sql.append(String.join("\nUNION ALL\n", selectQueries));

        return sql.toString();
    }

    private String getTimeKeyForTable(String table) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT time_key FROM reporting.sem_view WHERE table_ref = ?",
                String.class,
                table
            );
        } catch (Exception e) {
            return "reporting_date"; // Fallback to reporting_date as default for banking facts
        }
    }

    private boolean columnExists(String table, String column, Map<String, Set<String>> cache) {
        if (table == null || column == null || column.isBlank()) {
            return false;
        }
        String cleanTable = table.trim().toLowerCase();
        String cleanCol = column.trim().toLowerCase();
        
        Set<String> columns = cache.computeIfAbsent(cleanTable, t -> {
            String schema = "analytics";
            String tableName = t;
            if (t.contains(".")) {
                String[] parts = t.split("\\.");
                schema = parts[0].trim();
                tableName = parts[1].trim();
            }
            String sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?";
            try {
                List<String> list = jdbcTemplate.queryForList(sql, String.class, schema, tableName);
                Set<String> set = new HashSet<>();
                for (String col : list) {
                    set.add(col.toLowerCase());
                }
                return set;
            } catch (Exception e) {
                return Collections.emptySet();
            }
        });
        
        return columns.contains(cleanCol);
    }

    private boolean isNumericColumn(String table, String column) {
        if (table == null || column == null || column.isBlank()) {
            return false;
        }
        String schema = "analytics";
        String tableName = table.trim();
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.");
            schema = parts[0].trim();
            tableName = parts[1].trim();
        }
        String sql = "SELECT data_type FROM information_schema.columns " +
                     "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        try {
            String dataType = jdbcTemplate.queryForObject(sql, String.class, schema, tableName, column);
            if (dataType == null) {
                return false;
            }
            dataType = dataType.toLowerCase();
            return dataType.contains("int") || dataType.contains("num") || 
                   dataType.contains("double") || dataType.contains("real") || 
                   dataType.contains("float") || dataType.contains("decimal") || 
                   dataType.contains("precision");
        } catch (Exception e) {
            return false;
        }
    }

    public String makeDivisionSafe(String sql) {
        if (sql == null || !sql.contains("/")) {
            return sql;
        }
        StringBuilder sb = new StringBuilder();
        int len = sql.length();
        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);
            if (c == '/') {
                sb.append("/ NULLIF(");
                int start = i + 1;
                while (start < len && Character.isWhitespace(sql.charAt(start))) {
                    start++;
                }
                int end = start;
                if (end < len && sql.charAt(end) == '(') {
                    int open = 1;
                    end++;
                    while (end < len && open > 0) {
                        char ec = sql.charAt(end);
                        if (ec == '(') {
                            open++;
                        } else if (ec == ')') {
                            open--;
                        }
                        end++;
                    }
                } else {
                    while (end < len && (Character.isLetterOrDigit(sql.charAt(end)) || sql.charAt(end) == '_' || sql.charAt(end) == '.')) {
                        end++;
                    }
                }
                String divisor = sql.substring(start, end);
                sb.append(divisor).append(", 0)");
                i = end - 1;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public String injectConstraintsIntoRawSql(String rawSql, String constraints, String fill) {
        String upper = rawSql.toUpperCase();
        String[] functions = {"SUM", "AVG", "COUNT", "MIN", "MAX"};
        String result = rawSql;
        boolean replaced = false;
        for (String fn : functions) {
            String target = fn + "(";
            int idx = upper.indexOf(target);
            if (idx != -1) {
                int openParen = 1;
                int end = idx + target.length();
                while (end < rawSql.length() && openParen > 0) {
                    char ec = rawSql.charAt(end);
                    if (ec == '(') {
                        openParen++;
                    } else if (ec == ')') {
                        openParen--;
                    }
                    end++;
                }
                if (openParen == 0) {
                    String inner = rawSql.substring(idx + target.length(), end - 1);
                    String distinctPrefix = "";
                    String cleanedInner = inner;
                    
                    String innerTrim = inner.trim();
                    if (innerTrim.toUpperCase().startsWith("DISTINCT ")) {
                        distinctPrefix = "DISTINCT ";
                        cleanedInner = innerTrim.substring(9).trim();
                    }
                    
                    String replacedFn = fn + "(" + distinctPrefix + "CASE WHEN " + constraints + " THEN (" + cleanedInner + ") ELSE " + fill + " END)";
                    result = result.substring(0, idx) + replacedFn + result.substring(end);
                    upper = result.toUpperCase();
                    replaced = true;
                }
            }
        }
        if (!replaced) {
            result = "SUM(CASE WHEN " + constraints + " THEN (" + rawSql + ") ELSE " + fill + " END)";
        }
        return result;
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
        
        int openParen = 0;
        for (int i = 0; i < expr.length(); i++) {
            if (expr.charAt(i) == '(') {
                openParen++;
            } else if (expr.charAt(i) == ')') {
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

    private String compileRowFilter(String rowFilter) {
        if (rowFilter == null || rowFilter.isBlank()) {
            return "";
        }
        String trimmed = rowFilter.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            List<FilterCondition> conds = parseGeneralFilters(trimmed);
            List<String> compiled = new ArrayList<>();
            for (FilterCondition cond : conds) {
                String sqlCond = compileFilterCondition(cond);
                if (sqlCond != null && !sqlCond.isBlank()) {
                    compiled.add("(" + sqlCond + ")");
                }
            }
            if (compiled.isEmpty()) {
                return "";
            }
            return String.join(" AND ", compiled);
        } else {
            validateFilterExpr(trimmed);
            return trimmed;
        }
    }

    private List<FilterCondition> parseGeneralFilters(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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

    private boolean isSameTable(String t1, String t2) {
        if (t1 == null || t2 == null) {
            return false;
        }
        String s1 = t1.trim().toLowerCase();
        String s2 = t2.trim().toLowerCase();
        if (s1.equals(s2)) {
            return true;
        }
        if (s1.contains(".")) {
            s1 = s1.substring(s1.lastIndexOf(".") + 1);
        }
        if (s2.contains(".")) {
            s2 = s2.substring(s2.lastIndexOf(".") + 1);
        }
        return s1.equals(s2);
    }

    private String normalizeTableName(String table) {
        if (table == null) {
            return "";
        }
        String s = table.trim().toLowerCase();
        if (s.contains(".")) {
            s = s.substring(s.lastIndexOf(".") + 1);
        }
        return s;
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class FilterCondition {
        private String dimTable;
        private String attribute;
        private String operator;
        private String value;
        private String conjunction;

        public FilterCondition() {}

        public FilterCondition(String dimTable, String attribute, String operator, String value) {
            this.dimTable = dimTable;
            this.attribute = attribute;
            this.operator = operator;
            this.value = value;
        }

        public FilterCondition(String dimTable, String attribute, String operator, String value, String conjunction) {
            this.dimTable = dimTable;
            this.attribute = attribute;
            this.operator = operator;
            this.value = value;
            this.conjunction = conjunction;
        }

        public String getDimTable() { return dimTable; }
        public void setDimTable(String dimTable) { this.dimTable = dimTable; }
        public String getAttribute() { return attribute; }
        public void setAttribute(String attribute) { this.attribute = attribute; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getConjunction() { return conjunction; }
        public void setConjunction(String conjunction) { this.conjunction = conjunction; }
    }
}
