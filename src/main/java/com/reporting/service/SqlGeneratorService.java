package com.reporting.service;

import com.reporting.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        // Parse Filters
        List<FilterCondition> generalFilters = parseGeneralFilters(config.getGeneralFilters());
        List<FilterCondition> quickFilters = parseGeneralFilters(config.getQuickFilters());

        // 1. Discover all unique sourceTable entries
        Set<String> uniqueTables = new LinkedHashSet<>();
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

        if (uniqueTables.isEmpty()) {
            return "SELECT '' AS row_id, '' AS col_id, 0.0::DOUBLE PRECISION AS val WHERE FALSE";
        }

        // 2. Identify the conformed key variable linking these domains
        Map<String, Set<String>> tableColumnsCache = new HashMap<>();
        String conformedKey = findConformedKey(uniqueTables, tableColumnsCache);

        // 3. Build CTE definitions for each unique table
        Map<String, String> tableToCteName = new HashMap<>();
        for (String table : uniqueTables) {
            String shortTbl = table;
            if (table.contains(".")) {
                shortTbl = table.substring(table.lastIndexOf(".") + 1);
            }
            String cteName = "cte_" + shortTbl.trim().toLowerCase();
            tableToCteName.put(table.trim().toLowerCase(), cteName);
        }

        List<String> cteDefinitions = new ArrayList<>();
        for (String factTable : uniqueTables) {
            String cteName = tableToCteName.get(factTable.trim().toLowerCase());
            
            // Check dimensions to join for conformed key or filters
            Set<String> joinedDims = new LinkedHashSet<>();
            if (!columnExists(factTable, conformedKey, tableColumnsCache)) {
                if ("customer_id".equalsIgnoreCase(conformedKey) && factTable.toLowerCase().contains("fact_banking_transactions")) {
                    joinedDims.add("dim_accounts");
                }
            }
            // Scan row filters for referenced dimensions
            for (ReportRowDto row : config.getRows()) {
                if (row.isDataRow() && row.source() != null) {
                    String rowTable = row.source().getTable();
                    if (rowTable != null && rowTable.trim().equalsIgnoreCase(factTable.trim())) {
                        String rowFilter = row.filterExpr();
                        if (rowFilter != null && !rowFilter.isBlank()) {
                            String trimmed = rowFilter.trim();
                            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                                List<FilterCondition> conds = parseGeneralFilters(trimmed);
                                for (FilterCondition cond : conds) {
                                    if (cond.getDimTable() != null && !cond.getDimTable().isBlank()) {
                                        joinedDims.add(cond.getDimTable().trim());
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Build FROM and JOIN
            StringBuilder fromClause = new StringBuilder("FROM " + factTable);
            for (String dim : joinedDims) {
                String join = getJoinClauseForDim(factTable, dim);
                if (!join.isEmpty()) {
                    fromClause.append("\n    ").append(join);
                }
            }

            // Conformed Key Select & Group expressions
            String selectKeyExpr;
            String groupByExpr;
            if ("customer_id".equalsIgnoreCase(conformedKey) && factTable.toLowerCase().contains("fact_banking_transactions")) {
                selectKeyExpr = "analytics.dim_accounts.customer_id AS customer_id";
                groupByExpr = "analytics.dim_accounts.customer_id";
            } else if (columnExists(factTable, conformedKey, tableColumnsCache)) {
                selectKeyExpr = factTable + "." + conformedKey + " AS " + conformedKey;
                groupByExpr = factTable + "." + conformedKey;
            } else {
                selectKeyExpr = "CAST(NULL AS INTEGER) AS " + conformedKey;
                groupByExpr = "1";
            }

            String whereClause = "";

            // Build aggregations for each active data row + column for this table
            List<String> selectList = new ArrayList<>();
            selectList.add(selectKeyExpr);

            for (ReportRowDto row : config.getRows()) {
                if (!row.isDataRow()) {
                    continue;
                }
                MeasureDefinitionDTO mdef = row.source();
                if (mdef == null) {
                    continue;
                }
                String rowTable = mdef.getTable();
                if (rowTable == null || rowTable.isBlank() || !rowTable.trim().equalsIgnoreCase(factTable.trim())) {
                    continue;
                }

                for (ColumnDefDto col : config.getColumns()) {
                    if (col.colType() == Enums.ColType.CALC) {
                        continue;
                    }
                    if (!row.isEnabledFor(col.colId())) {
                        continue;
                    }

                    String timeKey = getTimeKeyForTable(factTable);
                    String prefixedTimeKey = factTable + "." + timeKey;

                    LocalDate[] boundaries = DateUtils.getPeriodBoundaries(
                        config.getReferenceDate(),
                        col.colType(),
                        col.periodOffset(),
                        col.rollingN()
                    );
                    LocalDate start = boundaries[0];
                    LocalDate end = boundaries[1];

                    String dateConstraint = String.format("%s >= '%s' AND %s <= '%s'", prefixedTimeKey, start.toString(), prefixedTimeKey, end.toString());

                    String rowFilter = row.filterExpr();
                    String filterClause = "";
                    if (rowFilter != null && !rowFilter.isBlank()) {
                        String compiledRowFilter = compileRowFilter(rowFilter);
                        if (compiledRowFilter != null && !compiledRowFilter.isBlank()) {
                            filterClause = " AND (" + compiledRowFilter + ")";
                        }
                    }

                    String metricClause = "";
                    if ("visual".equalsIgnoreCase(mdef.getMode())) {
                        String agg = mdef.getAggregation() != null ? mdef.getAggregation().trim().toUpperCase() : "SUM";
                        String originalColName = mdef.getTargetColumn() != null ? mdef.getTargetColumn().trim() : "";

                        if (("SUM".equals(agg) || "AVG".equals(agg)) && !isNumericColumn(factTable, originalColName)) {
                            throw new IllegalArgumentException("Column '" + originalColName + "' in table " + factTable + " is not numeric and cannot be aggregated with " + agg);
                        }

                        String colName = factTable + "." + originalColName;
                        String fill = "SUM".equals(agg) ? "0" : "NULL";

                        if ("COUNT_DISTINCT".equals(agg) || "COUNT DISTINCT".equals(agg)) {
                            metricClause = String.format(
                                "COUNT(DISTINCT CASE WHEN %s%s THEN %s ELSE NULL END)",
                                dateConstraint,
                                filterClause,
                                colName
                            );
                        } else {
                            metricClause = String.format(
                                "%s(CASE WHEN %s%s THEN %s ELSE %s END)",
                                agg,
                                dateConstraint,
                                filterClause,
                                colName,
                                fill
                            );
                        }
                    } else { // raw mode
                        String raw = mdef.getRawSql();
                        if (raw == null) {
                            raw = "";
                        }
                        String processedRaw = raw;
                        String shortTbl = factTable;
                        if (factTable.contains(".")) {
                            shortTbl = factTable.substring(factTable.lastIndexOf(".") + 1);
                        }
                        String escapedFull = java.util.regex.Pattern.quote(factTable.trim()) + "\\.";
                        String escapedShort = java.util.regex.Pattern.quote(shortTbl.trim()) + "\\.";
                        processedRaw = processedRaw.replaceAll("(?i)" + escapedFull, factTable + ".");
                        processedRaw = processedRaw.replaceAll("(?i)" + escapedShort, factTable + ".");

                        processedRaw = makeDivisionSafe(processedRaw);
                        validateFilterExpr(processedRaw);

                        boolean hasAgg = false;
                        String[] functions = {"SUM", "AVG", "COUNT", "MIN", "MAX"};
                        String upperRaw = processedRaw.toUpperCase();
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
                            metricClause = injectConstraintsIntoRawSql(processedRaw, dateConstraint + filterClause, fill);
                        } else {
                            metricClause = String.format(
                                "SUM(CASE WHEN %s%s THEN (%s) ELSE 0 END)",
                                dateConstraint,
                                filterClause,
                                processedRaw
                            );
                        }
                    }

                    String alias = "val_" + row.rowId().toLowerCase() + "_" + col.colId().toLowerCase();
                    selectList.add(String.format("CAST(%s AS DOUBLE PRECISION) AS %s", metricClause, alias));
                }
            }

            String cteSql = String.format(
                "  %s AS (\n    SELECT\n      %s\n    %s%s\n    GROUP BY %s\n  )",
                cteName,
                String.join(",\n      ", selectList),
                fromClause.toString(),
                whereClause,
                groupByExpr
            );
            cteDefinitions.add(cteSql);
        }

        // 4. Build unified_spine CTE using UNION DISTINCT and applying global filters
        List<String> spineSelects = new ArrayList<>();
        for (String table : uniqueTables) {
            String cteName = tableToCteName.get(table.trim().toLowerCase());
            spineSelects.add(String.format("    SELECT %s FROM %s WHERE %s IS NOT NULL", conformedKey, cteName, conformedKey));
        }
        
        Set<String> spineJoinedDims = new LinkedHashSet<>();
        for (FilterCondition cond : generalFilters) {
            if (cond.getDimTable() != null && !cond.getDimTable().isBlank()) {
                spineJoinedDims.add(cond.getDimTable().trim());
            }
        }
        for (FilterCondition cond : quickFilters) {
            if (cond.getDimTable() != null && !cond.getDimTable().isBlank()) {
                spineJoinedDims.add(cond.getDimTable().trim());
            }
        }
        
        StringBuilder spineJoins = new StringBuilder();
        for (String dim : spineJoinedDims) {
            spineJoins.append("\n    ").append(getSpineJoinClause(dim, conformedKey));
        }
        
        List<String> spineCompiledFilters = new ArrayList<>();
        for (FilterCondition cond : generalFilters) {
            String sqlCond = compileSpineFilter(cond, conformedKey);
            if (sqlCond != null && !sqlCond.isBlank()) {
                spineCompiledFilters.add("(" + sqlCond + ")");
            }
        }
        for (FilterCondition cond : quickFilters) {
            String sqlCond = compileSpineFilter(cond, conformedKey);
            if (sqlCond != null && !sqlCond.isBlank()) {
                spineCompiledFilters.add("(" + sqlCond + ")");
            }
        }
        
        String spineWhereClause = "";
        if (!spineCompiledFilters.isEmpty()) {
            spineWhereClause = "\n    WHERE " + String.join(" AND ", spineCompiledFilters);
        }
        
        String unifiedSpineCte = String.format(
            "  unified_spine AS (\n" +
            "    SELECT DISTINCT spine_raw.%s FROM (\n" +
            "%s\n" +
            "    ) spine_raw%s%s\n" +
            "  )",
            conformedKey,
            String.join("\n    UNION DISTINCT\n", spineSelects),
            spineJoins.toString(),
            spineWhereClause
        );
        cteDefinitions.add(unifiedSpineCte);

        // 5. Build combined_data CTE
        List<String> combinedSelectList = new ArrayList<>();
        combinedSelectList.add("s." + conformedKey);

        for (String table : uniqueTables) {
            String cteName = tableToCteName.get(table.trim().toLowerCase());
            for (ReportRowDto row : config.getRows()) {
                if (!row.isDataRow()) {
                    continue;
                }
                MeasureDefinitionDTO mdef = row.source();
                if (mdef == null) {
                    continue;
                }
                String rowTable = mdef.getTable();
                if (rowTable == null || rowTable.isBlank() || !rowTable.trim().equalsIgnoreCase(table.trim())) {
                    continue;
                }
                for (ColumnDefDto col : config.getColumns()) {
                    if (col.colType() == Enums.ColType.CALC) {
                        continue;
                    }
                    if (!row.isEnabledFor(col.colId())) {
                        continue;
                    }
                    String alias = "val_" + row.rowId().toLowerCase() + "_" + col.colId().toLowerCase();
                    combinedSelectList.add(String.format("COALESCE(%s.%s, 0) AS %s", cteName, alias, alias));
                }
            }
        }

        StringBuilder combinedFrom = new StringBuilder("FROM unified_spine s");
        for (String table : uniqueTables) {
            String cteName = tableToCteName.get(table.trim().toLowerCase());
            combinedFrom.append(String.format("\n    LEFT JOIN %s ON %s.%s = s.%s", cteName, cteName, conformedKey, conformedKey));
        }

        String combinedCte = String.format(
            "  combined_data AS (\n    SELECT\n      %s\n    %s\n  )",
            String.join(",\n      ", combinedSelectList),
            combinedFrom.toString()
        );
        cteDefinitions.add(combinedCte);

        // 6. Build final selects for unpivoting
        List<String> finalSelects = new ArrayList<>();
        for (ReportRowDto row : config.getRows()) {
            if (!row.isDataRow()) {
                continue;
            }
            MeasureDefinitionDTO mdef = row.source();
            if (mdef == null) {
                continue;
            }
            for (ColumnDefDto col : config.getColumns()) {
                if (col.colType() == Enums.ColType.CALC) {
                    continue;
                }
                if (!row.isEnabledFor(col.colId())) {
                    continue;
                }

                String alias = "val_" + row.rowId().toLowerCase() + "_" + col.colId().toLowerCase();
                String finalAgg = "SUM";
                if ("visual".equalsIgnoreCase(mdef.getMode())) {
                    String agg = mdef.getAggregation() != null ? mdef.getAggregation().trim().toUpperCase() : "SUM";
                    if ("AVG".equals(agg) || "MAX".equals(agg) || "MIN".equals(agg)) {
                        finalAgg = agg;
                    }
                }

                String select = String.format(
                    "SELECT '%s' AS row_id, '%s' AS col_id, CAST(%s(%s) AS DOUBLE PRECISION) AS val FROM combined_data",
                    row.rowId().toUpperCase(),
                    col.colId().toUpperCase(),
                    finalAgg,
                    alias
                );
                finalSelects.add(select);
            }
        }

        if (finalSelects.isEmpty()) {
            return "SELECT '' AS row_id, '' AS col_id, 0.0::DOUBLE PRECISION AS val WHERE FALSE";
        }

        StringBuilder sql = new StringBuilder("WITH\n");
        sql.append(String.join(",\n", cteDefinitions)).append("\n");
        sql.append(String.join("\nUNION ALL\n", finalSelects));

        return sql.toString();
    }

    private String findConformedKey(Set<String> factTables, Map<String, Set<String>> cache) {
        if (factTables.isEmpty()) {
            return "customer_id";
        }

        List<String> priorityKeys = List.of("customer_id", "account_id", "location_id", "rm_id");
        for (String key : priorityKeys) {
            boolean existsInAll = true;
            for (String table : factTables) {
                if (!columnExists(table, key, cache)) {
                    existsInAll = false;
                    break;
                }
            }
            if (existsInAll) {
                return key;
            }
        }

        String firstTable = factTables.iterator().next();
        Set<String> firstTableCols = cache.get(firstTable.trim().toLowerCase());
        if (firstTableCols != null) {
            for (String col : firstTableCols) {
                if (col.endsWith("_id")) {
                    boolean existsInAll = true;
                    for (String table : factTables) {
                        if (!columnExists(table, col, cache)) {
                            existsInAll = false;
                            break;
                        }
                    }
                    if (existsInAll) {
                        return col;
                    }
                }
            }
        }

        return "customer_id";
    }

    private String getJoinClauseForDim(String factTable, String dimTable) {
        String fact = factTable.toLowerCase().trim();
        if (fact.contains(".")) {
            fact = fact.substring(fact.lastIndexOf(".") + 1);
        }
        String dim = dimTable.toLowerCase().trim();
        if (dim.contains(".")) {
            dim = dim.substring(dim.lastIndexOf(".") + 1);
        }

        if ("dim_date".equalsIgnoreCase(dim)) {
            return "JOIN analytics.dim_date ON analytics.dim_date.date_key = " + factTable + ".reporting_date";
        }
        if ("dim_products".equalsIgnoreCase(dim)) {
            return "JOIN analytics.dim_products ON analytics.dim_products.id = " + factTable + ".product_id";
        }
        if ("dim_customers".equalsIgnoreCase(dim)) {
            if ("fact_banking_transactions".equalsIgnoreCase(fact)) {
                return "JOIN analytics.dim_accounts ON analytics.dim_accounts.id = " + factTable + ".account_id " +
                       "JOIN analytics.dim_customers ON analytics.dim_customers.id = analytics.dim_accounts.customer_id";
            }
            return "JOIN analytics.dim_customers ON analytics.dim_customers.id = " + factTable + ".customer_id";
        }
        if ("dim_location".equalsIgnoreCase(dim)) {
            return "JOIN analytics.dim_location ON analytics.dim_location.id = " + factTable + ".location_id";
        }
        if ("dim_relationship_manager".equalsIgnoreCase(dim)) {
            return "JOIN analytics.dim_relationship_manager ON analytics.dim_relationship_manager.id = " + factTable + ".rm_id";
        }
        if ("dim_investment_hierarchy".equalsIgnoreCase(dim)) {
            return "JOIN analytics.dim_investment_hierarchy ON analytics.dim_investment_hierarchy.id = " + factTable + ".hier_id";
        }
        if ("dim_accounts".equalsIgnoreCase(dim)) {
            return "JOIN analytics.dim_accounts ON analytics.dim_accounts.id = " + factTable + ".account_id";
        }
        if ("dim_countries".equalsIgnoreCase(dim)) {
            if ("fact_banking_transactions".equalsIgnoreCase(fact)) {
                return "JOIN analytics.dim_accounts ON analytics.dim_accounts.id = " + factTable + ".account_id " +
                       "JOIN analytics.dim_customers ON analytics.dim_customers.id = analytics.dim_accounts.customer_id " +
                       "JOIN analytics.dim_countries ON analytics.dim_countries.iso_code = analytics.dim_customers.country_code";
            }
            return "JOIN analytics.dim_customers ON analytics.dim_customers.id = " + factTable + ".customer_id " +
                   "JOIN analytics.dim_countries ON analytics.dim_countries.iso_code = analytics.dim_customers.country_code";
        }

        return "";
    }

    private String getTimeKeyForTable(String table) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT time_key FROM reporting.sem_view WHERE table_ref = ?",
                String.class,
                table
            );
        } catch (Exception e) {
            return "reporting_date";
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
        
        if (dimTable != null && !dimTable.isBlank() && !dimTable.matches("^[a-zA-Z0-9_\\.]+$")) {
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

    private String getSpineJoinClause(String dimTable, String conformedKey) {
        String dim = dimTable.toLowerCase().trim();
        if (dim.contains(".")) {
            dim = dim.substring(dim.lastIndexOf(".") + 1);
        }
        
        if ("dim_date".equalsIgnoreCase(dim)) {
            String joinKey = "reporting_date".equalsIgnoreCase(conformedKey) ? "reporting_date" : conformedKey;
            return "JOIN analytics.dim_date ON analytics.dim_date.date_key = spine_raw." + joinKey;
        }
        if ("dim_customers".equalsIgnoreCase(dim)) {
            return "JOIN analytics.dim_customers ON analytics.dim_customers.id = spine_raw." + conformedKey;
        }
        if ("dim_location".equalsIgnoreCase(dim)) {
            return "JOIN analytics.dim_location ON analytics.dim_location.id = spine_raw." + conformedKey;
        }
        if ("dim_relationship_manager".equalsIgnoreCase(dim)) {
            return "JOIN analytics.dim_relationship_manager ON analytics.dim_relationship_manager.id = spine_raw." + conformedKey;
        }
        if ("dim_accounts".equalsIgnoreCase(dim)) {
            return "JOIN analytics.dim_accounts ON analytics.dim_accounts.id = spine_raw." + conformedKey;
        }
        return "JOIN analytics." + dim + " ON analytics." + dim + ".id = spine_raw." + conformedKey;
    }

    private String compileSpineFilter(FilterCondition cond, String conformedKey) {
        if (cond == null) return "";
        String dimTable = cond.getDimTable();
        String attribute = cond.getAttribute();
        
        if (dimTable != null && !dimTable.isBlank()) {
            return compileFilterCondition(cond);
        }
        
        FilterCondition clone = new FilterCondition(
            "spine_raw",
            attribute,
            cond.getOperator(),
            cond.getValue()
        );
        return compileFilterCondition(clone);
    }
}
