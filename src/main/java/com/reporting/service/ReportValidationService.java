package com.reporting.service;

import com.reporting.dto.*;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ReportValidationService {

    private final JdbcTemplate jdbcTemplate;

    public ReportValidationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final Set<String> BUILT_IN_FUNCTIONS_AND_CONSTANTS = Set.of(
        "ABS", "ACOS", "ASIN", "ATAN", "CBRT", "CEIL", "COS", "COSH", "EXP", "FLOOR",
        "LOG", "LOG10", "LOG2", "SIN", "SINH", "SQRT", "TAN", "TANH", "SIGNUM",
        "PI", "E"
    );

    private static final Set<String> SQL_KEYWORDS = Set.of(
        "SELECT", "FROM", "WHERE", "JOIN", "ON", "AND", "OR", "NOT", "NULL", "CASE", "WHEN", "THEN", "ELSE", "END",
        "SUM", "AVG", "MIN", "MAX", "COUNT", "CAST", "AS", "DOUBLE", "PRECISION", "COALESCE", "NULLIF", "ROUND",
        "ABS", "DATE", "INTERVAL", "YEAR", "MONTH", "DAY", "TRUE", "FALSE", "LIKE", "IN", "BETWEEN", "IS", "GROUP",
        "BY", "ORDER", "HAVING", "LIMIT", "OVER", "PARTITION", "ROWS", "UNBOUNDED", "PRECEDING", "FOLLOWING",
        "CURRENT", "ROW", "EXTRACT", "FLOOR", "CEIL", "TRUNCATE", "POWER", "SQRT", "DISTINCT"
    );

    public ValidationResult validateConfiguration(ReportConfigDto config) {
        List<ValidationError> errors = new ArrayList<>();

        if (config == null) {
            errors.add(ValidationError.builder()
                .elementId("GLOBAL")
                .fieldContext("config")
                .errorSeverity("CRITICAL")
                .displayMessage("Report configuration payload cannot be null")
                .build());
            return new ValidationResult(false, errors);
        }

        // 1. Fetch DWH schema catalog columns
        Map<String, Map<String, String>> schemaCache = loadSchemaCache();

        // 2. Pre-process active element sets
        Set<String> activeRowIds = new HashSet<>();
        if (config.getRows() != null) {
            for (ReportRowDto r : config.getRows()) {
                if (r.rowId() != null) {
                    activeRowIds.add(r.rowId().toUpperCase().trim());
                }
            }
        }

        Set<String> activeColIds = new HashSet<>();
        if (config.getColumns() != null) {
            for (ColumnDefDto c : config.getColumns()) {
                if (c.colId() != null) {
                    activeColIds.add(c.colId().toUpperCase().trim());
                }
            }
        }

        // 3. Graph Dependency Analyzer: Cycle detection
        analyzeColumnDependencies(config, activeColIds, errors);
        analyzeRowDependencies(config, activeRowIds, errors);

        // 4. Token Parsing & Expression validation for Column/Row formulas
        validateColumnFormulas(config, activeColIds, errors);
        validateRowFormulas(config, activeRowIds, errors);

        // 5. Database Schema & Data Type Mismatch Linters
        validateDatabaseMappings(config, schemaCache, errors);

        // 6. Global Filters and Granularity Conformed Dimension Linters
        validateGlobalFiltersAndGranularity(config, errors);

        return new ValidationResult(errors.isEmpty(), errors);
    }

    private Map<String, Map<String, String>> loadSchemaCache() {
        Map<String, Map<String, String>> cache = new HashMap<>();
        try {
            String sql = "SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_schema = 'analytics'";
            List<Map<String, Object>> columns = jdbcTemplate.queryForList(sql);
            for (Map<String, Object> colRow : columns) {
                String tblName = (String) colRow.get("table_name");
                String colName = (String) colRow.get("column_name");
                String dataType = (String) colRow.get("data_type");
                if (tblName != null && colName != null && dataType != null) {
                    String normTblShort = tblName.toLowerCase().trim();
                    String normTblFull = "analytics." + normTblShort;
                    String normCol = colName.toLowerCase().trim();

                    cache.computeIfAbsent(normTblShort, k -> new HashMap<>()).put(normCol, dataType);
                    cache.computeIfAbsent(normTblFull, k -> new HashMap<>()).put(normCol, dataType);
                }
            }
        } catch (Exception e) {
            log.error("Failed to load schema cache for validation", e);
        }
        return cache;
    }

    private void analyzeColumnDependencies(ReportConfigDto config, Set<String> activeColIds, List<ValidationError> errors) {
        if (config.getColumns() == null) return;

        Map<String, List<String>> adj = new HashMap<>();
        for (ColumnDefDto col : config.getColumns()) {
            String cid = col.colId().toUpperCase().trim();
            adj.put(cid, new ArrayList<>());
            if (col.colType() == Enums.ColType.CALC && col.formulaExpr() != null) {
                Set<String> refs = getReferencedTokens(col.formulaExpr(), activeColIds);
                adj.get(cid).addAll(refs);
            }
        }

        Map<String, Integer> state = new HashMap<>();
        for (String cid : activeColIds) {
            state.put(cid, 0); // 0 = unvisited
        }

        for (String cid : activeColIds) {
            if (state.get(cid) == 0) {
                List<String> path = new ArrayList<>();
                List<String> cycle = findCycle(cid, adj, state, path);
                if (cycle != null) {
                    errors.add(ValidationError.builder()
                        .elementId(cid)
                        .fieldContext("formulaExpr")
                        .errorSeverity("CRITICAL")
                        .displayMessage("Circular reference detected in columns: " + String.join(" -> ", cycle))
                        .build());
                    break; // report first detected cycle to avoid spamming
                }
            }
        }
    }

    private void analyzeRowDependencies(ReportConfigDto config, Set<String> activeRowIds, List<ValidationError> errors) {
        if (config.getRows() == null) return;

        Map<String, List<String>> adj = new HashMap<>();
        for (ReportRowDto row : config.getRows()) {
            String rid = row.rowId().toUpperCase().trim();
            adj.put(rid, new ArrayList<>());
            if (row.isCalcRow() && row.source() != null && row.source().getRawSql() != null) {
                Set<String> refs = getReferencedTokens(row.source().getRawSql(), activeRowIds);
                adj.get(rid).addAll(refs);
            }
        }

        Map<String, Integer> state = new HashMap<>();
        for (String rid : activeRowIds) {
            state.put(rid, 0); // 0 = unvisited
        }

        for (String rid : activeRowIds) {
            if (state.get(rid) == 0) {
                List<String> path = new ArrayList<>();
                List<String> cycle = findCycle(rid, adj, state, path);
                if (cycle != null) {
                    errors.add(ValidationError.builder()
                        .elementId(rid)
                        .fieldContext("measure_definition")
                        .errorSeverity("CRITICAL")
                        .displayMessage("Circular reference detected in rows: " + String.join(" -> ", cycle))
                        .build());
                    break;
                }
            }
        }
    }

    private List<String> findCycle(String node, Map<String, List<String>> adj, Map<String, Integer> state, List<String> path) {
        state.put(node, 1); // visiting
        path.add(node);
        for (String neighbor : adj.getOrDefault(node, Collections.emptyList())) {
            Integer neighState = state.get(neighbor);
            if (neighState != null) {
                if (neighState == 1) {
                    int idx = path.indexOf(neighbor);
                    List<String> cycle = new ArrayList<>(path.subList(idx, path.size()));
                    cycle.add(neighbor);
                    return cycle;
                } else if (neighState == 0) {
                    List<String> cycle = findCycle(neighbor, adj, state, path);
                    if (cycle != null) {
                        return cycle;
                    }
                }
            }
        }
        path.remove(path.size() - 1);
        state.put(node, 2); // visited
        return null;
    }

    private Set<String> getReferencedTokens(String formula, Set<String> allTokens) {
        if (formula == null || formula.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> refs = new HashSet<>();
        String upperFormula = formula.toUpperCase();
        List<String> sortedTokens = new ArrayList<>(allTokens);
        sortedTokens.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String token : sortedTokens) {
            String regex = "\\b" + Pattern.quote(token) + "\\b";
            if (Pattern.compile(regex).matcher(upperFormula).find()) {
                refs.add(token);
            }
        }
        return refs;
    }

    private Set<String> extractVariables(String formula) {
        if (formula == null || formula.isBlank()) {
            return Collections.emptySet();
        }
        String cleanedFormula = formula.replaceAll("'([^']|'')*'", "");
        Set<String> variables = new HashSet<>();
        Matcher matcher = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*)\\b").matcher(cleanedFormula);
        while (matcher.find()) {
            String token = matcher.group(1).toUpperCase();
            if (!BUILT_IN_FUNCTIONS_AND_CONSTANTS.contains(token)) {
                variables.add(token);
            }
        }
        return variables;
    }

    private void validateColumnFormulas(ReportConfigDto config, Set<String> activeColIds, List<ValidationError> errors) {
        if (config.getColumns() == null) return;

        for (ColumnDefDto col : config.getColumns()) {
            if (col.colType() != Enums.ColType.CALC || col.formulaExpr() == null || col.formulaExpr().isBlank()) {
                continue;
            }
            String formula = col.formulaExpr();
            String elementId = col.colId();

            // 1. Parentheses balance
            validateParentheses(formula, elementId, "formulaExpr", errors);

            // 2. Token verification
            Set<String> variables = extractVariables(formula);
            boolean hasInvalidToken = false;
            for (String var : variables) {
                if (!activeColIds.contains(var)) {
                    errors.add(ValidationError.builder()
                        .elementId(elementId)
                        .fieldContext("formulaExpr")
                        .errorSeverity("CRITICAL")
                        .displayMessage("Formula references column ID '" + var + "' which does not exist in configuration.")
                        .build());
                    hasInvalidToken = true;
                }
            }

            // 3. Mathematical syntax compiling
            if (!hasInvalidToken) {
                try {
                    ExpressionBuilder eb = new ExpressionBuilder(formula);
                    if (!variables.isEmpty()) {
                        eb.variables(variables);
                    }
                    eb.build();
                } catch (Exception e) {
                    errors.add(ValidationError.builder()
                        .elementId(elementId)
                        .fieldContext("formulaExpr")
                        .errorSeverity("CRITICAL")
                        .displayMessage("Arithmetic formula expression is invalid: " + e.getMessage())
                        .build());
                }
            }

            // 4. Naked division warnings
            validateDivisionSafety(formula, variables, elementId, "formulaExpr", errors);
        }
    }

    private void validateRowFormulas(ReportConfigDto config, Set<String> activeRowIds, List<ValidationError> errors) {
        if (config.getRows() == null) return;

        for (ReportRowDto row : config.getRows()) {
            if (!row.isCalcRow() || row.source() == null || row.source().getRawSql() == null || row.source().getRawSql().isBlank()) {
                continue;
            }
            String formula = row.source().getRawSql();
            String elementId = row.rowId();

            // 1. Parentheses balance
            validateParentheses(formula, elementId, "measure_definition", errors);

            // 2. Token verification
            Set<String> variables = extractVariables(formula);
            boolean hasInvalidToken = false;
            for (String var : variables) {
                if (!activeRowIds.contains(var)) {
                    errors.add(ValidationError.builder()
                        .elementId(elementId)
                        .fieldContext("measure_definition")
                        .errorSeverity("CRITICAL")
                        .displayMessage("Formula references row ID '" + var + "' which does not exist in configuration.")
                        .build());
                    hasInvalidToken = true;
                }
            }

            // 3. Mathematical syntax compiling
            if (!hasInvalidToken) {
                try {
                    ExpressionBuilder eb = new ExpressionBuilder(formula);
                    if (!variables.isEmpty()) {
                        eb.variables(variables);
                    }
                    eb.build();
                } catch (Exception e) {
                    errors.add(ValidationError.builder()
                        .elementId(elementId)
                        .fieldContext("measure_definition")
                        .errorSeverity("CRITICAL")
                        .displayMessage("Arithmetic formula expression is invalid: " + e.getMessage())
                        .build());
                }
            }

            // 4. Naked division warnings
            validateDivisionSafety(formula, variables, elementId, "measure_definition", errors);
        }
    }

    private void validateParentheses(String formula, String elementId, String context, List<ValidationError> errors) {
        int balance = 0;
        for (int i = 0; i < formula.length(); i++) {
            char c = formula.charAt(i);
            if (c == '(') {
                balance++;
            } else if (c == ')') {
                balance--;
                if (balance < 0) {
                    errors.add(ValidationError.builder()
                        .elementId(elementId)
                        .fieldContext(context)
                        .errorSeverity("CRITICAL")
                        .displayMessage("Formula has unmatched closed parenthesis at position " + (i + 1))
                        .build());
                    return;
                }
            }
        }
        if (balance != 0) {
            errors.add(ValidationError.builder()
                .elementId(elementId)
                .fieldContext(context)
                .errorSeverity("CRITICAL")
                .displayMessage("Formula has " + balance + " unclosed open parenthesis.")
                .build());
        }
    }

    private void validateDivisionSafety(String formula, Set<String> variables, String elementId, String context, List<ValidationError> errors) {
        if (!formula.contains("/")) return;

        // Warn for division operators where denominator contains variables that can be 0.0
        boolean hasVarInDenominator = false;
        String[] parts = formula.split("/");
        if (parts.length > 1) {
            // Check subsequent segments (denominators)
            for (int i = 1; i < parts.length; i++) {
                String denomPart = parts[i];
                Set<String> denomVars = extractVariables(denomPart);
                if (!denomVars.isEmpty()) {
                    hasVarInDenominator = true;
                    break;
                }
            }
        }

        if (hasVarInDenominator) {
            errors.add(ValidationError.builder()
                .elementId(elementId)
                .fieldContext(context)
                .errorSeverity("WARNING")
                .displayMessage("Potential division-by-zero: Naked division operator '/' detected. Ensure that the denominator cannot evaluate to zero.")
                .build());
        }
    }

    private void validateDatabaseMappings(ReportConfigDto config, Map<String, Map<String, String>> schemaCache, List<ValidationError> errors) {
        if (config.getRows() == null) return;

        for (ReportRowDto row : config.getRows()) {
            if (!row.isDataRow() || row.source() == null) {
                continue;
            }

            MeasureDefinitionDTO source = row.source();
            String rowTable = source.getTable();

            if (rowTable == null || rowTable.isBlank()) {
                errors.add(ValidationError.builder()
                    .elementId(row.rowId())
                    .fieldContext("measure_definition")
                    .errorSeverity("CRITICAL")
                    .displayMessage("No source table defined for this data row.")
                    .build());
                continue;
            }

            String normTbl = rowTable.toLowerCase().trim();
            Map<String, String> tblCols = schemaCache.get(normTbl);

            if (tblCols == null) {
                errors.add(ValidationError.builder()
                    .elementId(row.rowId())
                    .fieldContext("measure_definition")
                    .errorSeverity("CRITICAL")
                    .displayMessage("Physical table '" + rowTable + "' does not exist in the analytics schema catalog.")
                    .build());
                continue;
            }

            // Visual Mode Validation
            if (!"raw".equalsIgnoreCase(source.getMode())) {
                String colName = source.getTargetColumn();
                if (colName == null || colName.isBlank()) {
                    errors.add(ValidationError.builder()
                        .elementId(row.rowId())
                        .fieldContext("measure_definition")
                        .errorSeverity("CRITICAL")
                        .displayMessage("Target column must be specified for visual mode.")
                        .build());
                    continue;
                }

                String normCol = colName.toLowerCase().trim();
                String dataType = tblCols.get(normCol);

                if (dataType == null) {
                    errors.add(ValidationError.builder()
                        .elementId(row.rowId())
                        .fieldContext("measure_definition")
                        .errorSeverity("CRITICAL")
                        .displayMessage("Column '" + colName + "' does not exist in table '" + rowTable + "'.")
                        .build());
                } else {
                    String agg = source.getAggregation();
                    if (agg != null && (agg.equalsIgnoreCase("SUM") || agg.equalsIgnoreCase("AVG"))) {
                        if (!isNumericType(dataType)) {
                            errors.add(ValidationError.builder()
                                .elementId(row.rowId())
                                .fieldContext("measure_definition")
                                .errorSeverity("CRITICAL")
                                .displayMessage("Non-numeric column '" + colName + "' of type '" + dataType + "' cannot be used with numeric aggregation '" + agg + "'.")
                                .build());
                        }
                    }
                }
            } else {
                // Raw SQL Mode Validation
                String rawSql = source.getRawSql();
                if (rawSql == null || rawSql.isBlank()) {
                    errors.add(ValidationError.builder()
                        .elementId(row.rowId())
                        .fieldContext("measure_definition")
                        .errorSeverity("CRITICAL")
                        .displayMessage("Raw SQL expression must be provided when mode is raw.")
                        .build());
                    continue;
                }

                // A. Validate numeric aggregations inside rawSql
                validateRawSqlAggregations(rawSql, rowTable, schemaCache, row.rowId(), errors);

                // B. Validate general column references in rawSql
                validateRawSqlColumns(rawSql, rowTable, schemaCache, row.rowId(), errors);
            }
        }
    }

    private void validateRawSqlAggregations(String rawSql, String defaultTable, Map<String, Map<String, String>> schemaCache, String elementId, List<ValidationError> errors) {
        Matcher matcher = Pattern.compile("(?i)\\b(SUM|AVG)\\s*\\(\\s*([a-zA-Z0-9_\\.]+)\\s*\\)").matcher(rawSql);
        while (matcher.find()) {
            String aggFn = matcher.group(1).toUpperCase();
            String fullRef = matcher.group(2);

            String colName = fullRef;
            String tblName = defaultTable;

            if (fullRef.contains(".")) {
                int lastDotIdx = fullRef.lastIndexOf('.');
                colName = fullRef.substring(lastDotIdx + 1);
                tblName = fullRef.substring(0, lastDotIdx);
            }

            String normTbl = tblName != null ? tblName.toLowerCase().trim() : null;
            String normCol = colName.toLowerCase().trim();

            if (normTbl == null || normTbl.isBlank()) {
                continue;
            }

            Map<String, String> tblCols = schemaCache.get(normTbl);
            if (tblCols == null) {
                errors.add(ValidationError.builder()
                    .elementId(elementId)
                    .fieldContext("measure_definition")
                    .errorSeverity("CRITICAL")
                    .displayMessage("Raw SQL aggregation SUM/AVG references table '" + tblName + "' which does not exist in analytics schema.")
                    .build());
            } else {
                String dataType = tblCols.get(normCol);
                if (dataType == null) {
                    errors.add(ValidationError.builder()
                        .elementId(elementId)
                        .fieldContext("measure_definition")
                        .errorSeverity("CRITICAL")
                        .displayMessage("Column '" + colName + "' does not exist in table '" + tblName + "' referenced in raw SQL.")
                        .build());
                } else if (!isNumericType(dataType)) {
                    errors.add(ValidationError.builder()
                        .elementId(elementId)
                        .fieldContext("measure_definition")
                        .errorSeverity("CRITICAL")
                        .displayMessage("Non-numeric column '" + colName + "' of type '" + dataType + "' cannot be used with numeric aggregation '" + aggFn + "' in raw SQL.")
                        .build());
                }
            }
        }
    }

    private void validateRawSqlColumns(String rawSql, String defaultTable, Map<String, Map<String, String>> schemaCache, String elementId, List<ValidationError> errors) {
        Set<String> variables = extractVariables(rawSql);
        for (String var : variables) {
            if (SQL_KEYWORDS.contains(var)) {
                continue;
            }

            String colName = var;
            String tblName = defaultTable;

            if (var.contains(".")) {
                int lastDotIdx = var.lastIndexOf('.');
                colName = var.substring(lastDotIdx + 1);
                tblName = var.substring(0, lastDotIdx);
            }

            String normTbl = tblName != null ? tblName.toLowerCase().trim() : null;
            String normCol = colName.toLowerCase().trim();

            if (normTbl == null || normTbl.isBlank()) {
                continue;
            }

            Map<String, String> tblCols = schemaCache.get(normTbl);
            if (tblCols != null) {
                if (!tblCols.containsKey(normCol)) {
                    errors.add(ValidationError.builder()
                        .elementId(elementId)
                        .fieldContext("measure_definition")
                        .errorSeverity("CRITICAL")
                        .displayMessage("Identifier '" + colName + "' referenced in raw SQL does not exist in table '" + tblName + "'.")
                        .build());
                }
            }
        }
    }

    private boolean isNumericType(String dataType) {
        if (dataType == null) return false;
        String lower = dataType.toLowerCase();
        return lower.contains("numeric") || lower.contains("integer") || lower.contains("bigint") ||
               lower.contains("double") || lower.contains("real") || lower.contains("float") ||
               lower.contains("decimal") || lower.contains("precision") || lower.contains("smallint") ||
               lower.contains("serial");
    }

    private void validateGlobalFiltersAndGranularity(ReportConfigDto config, List<ValidationError> errors) {
        // 1. Validate Granularity
        String granularity = config.getGranularity();
        if (granularity != null && !granularity.isBlank()) {
            String normGran = granularity.trim().toLowerCase();
            if (!normGran.equals("customer_id") && !normGran.equals("location_id") && !normGran.equals("reporting_date")) {
                errors.add(ValidationError.builder()
                    .elementId("GLOBAL")
                    .fieldContext("granularity")
                    .errorSeverity("CRITICAL")
                    .displayMessage("Report granularity must be strictly one of: customer_id, location_id, reporting_date")
                    .build());
            }
        }

        // 2. Identify the active fact tables used by the report's active data rows
        Set<String> activeFactTables = new LinkedHashSet<>();
        if (config.getRows() != null) {
            for (ReportRowDto row : config.getRows()) {
                if (row.isDataRow() && row.source() != null) {
                    String tbl = row.source().getTable();
                    if (tbl != null && !tbl.isBlank()) {
                        activeFactTables.add(tbl.trim().toLowerCase());
                    }
                }
            }
        }

        // 3. Compute conformed dimensions
        Set<String> conformedDimensions = null;
        for (String factTable : activeFactTables) {
            Set<String> dims = getDimensionsForFactTable(factTable);
            if (conformedDimensions == null) {
                conformedDimensions = new HashSet<>(dims);
            } else {
                conformedDimensions.retainAll(dims);
            }
        }
        if (conformedDimensions == null) {
            conformedDimensions = Collections.emptySet();
        }

        // 4. Validate General Filters
        validateFilterTables(config.getGeneralFilters(), "generalFilters", conformedDimensions, errors);
        // 5. Validate Quick Filters
        validateFilterTables(config.getQuickFilters(), "quickFilters", conformedDimensions, errors);
    }

    private Set<String> getDimensionsForFactTable(String factTable) {
        Set<String> dims = new HashSet<>();
        try {
            String norm = factTable.trim().toLowerCase();
            String withSchema = norm.contains(".") ? norm : "analytics." + norm;
            String withoutSchema = norm.contains(".") ? norm.substring(norm.indexOf(".") + 1) : norm;

            String sql = "SELECT tv.name AS dimView " +
                         "FROM reporting.sem_join j " +
                         "JOIN reporting.sem_explore e ON e.explore_id = j.explore_id " +
                         "JOIN reporting.sem_view fv ON fv.view_id = e.fact_view_id " +
                         "JOIN reporting.sem_view tv ON tv.view_id = j.to_view_id " +
                         "WHERE fv.table_ref IN ('" + withSchema + "', '" + withoutSchema + "') " +
                         "   OR fv.name IN ('" + withSchema + "', '" + withoutSchema + "')";
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);
            if (rows != null) {
                for (Map<String, Object> r : rows) {
                    Object val = r.get("dimView");
                    if (val == null) {
                        val = r.get("dimview");
                    }
                    if (val != null) {
                        dims.add(val.toString().trim().toLowerCase());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to query dimensions for fact table: " + factTable, e);
        }
        return dims;
    }

    private void validateFilterTables(String filtersJson, String fieldName, Set<String> conformedDimensions, List<ValidationError> errors) {
        if (filtersJson == null || filtersJson.isBlank()) {
            return;
        }
        try {
            List<SqlGeneratorService.FilterCondition> filters = parseGeneralFilters(filtersJson);
            if (filters != null) {
                for (SqlGeneratorService.FilterCondition cond : filters) {
                    String dimTable = cond.getDimTable();
                    if (dimTable != null && !dimTable.isBlank()) {
                        String normDim = dimTable.trim().toLowerCase();
                        if (normDim.contains(".")) {
                            normDim = normDim.substring(normDim.lastIndexOf(".") + 1);
                        }
                        if (!conformedDimensions.contains(normDim)) {
                            errors.add(ValidationError.builder()
                                .elementId("GLOBAL")
                                .fieldContext(fieldName)
                                .errorSeverity("CRITICAL")
                                .displayMessage("Filter references unconformed dimension table '" + dimTable + "'. Valid conformed dimensions for this report layout are: " + conformedDimensions)
                                .build());
                        }
                    }
                }
            }
        } catch (Exception e) {
            errors.add(ValidationError.builder()
                .elementId("GLOBAL")
                .fieldContext(fieldName)
                .errorSeverity("CRITICAL")
                .displayMessage("Invalid JSON format for " + fieldName + ": " + e.getMessage())
                .build());
        }
    }

    private List<SqlGeneratorService.FilterCondition> parseGeneralFilters(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<SqlGeneratorService.FilterCondition>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
