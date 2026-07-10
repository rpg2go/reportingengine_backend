package com.reporting.service;

import com.reporting.dto.*;
import com.reporting.exception.CircularReferenceException;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;
import java.util.stream.Stream;

@Service
public class PostProcessorService {

    public Map<String, Map<String, Double>> process(ReportConfigDto config, List<Map<String, Object>> dbResults) {
        if (dbResults == null) {
            return process(config, Stream.empty());
        }

        List<String> granularityAliases = new ArrayList<>();
        if (config.getGranularity() != null && !config.getGranularity().isBlank()) {
            for (String s : config.getGranularity().split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    granularityAliases.add(SqlGeneratorService.getGranularityAlias(trimmed));
                }
            }
        }

        Stream<Object[]> stream = dbResults.stream().map(map -> {
            Object[] row = new Object[3 + granularityAliases.size()];
            row[0] = getMapValueCaseInsensitive(map, "row_id");
            row[1] = getMapValueCaseInsensitive(map, "col_id");
            row[2] = getMapValueCaseInsensitive(map, "val");
            for (int i = 0; i < granularityAliases.size(); i++) {
                row[3 + i] = getMapValueCaseInsensitive(map, granularityAliases.get(i));
            }
            return row;
        });

        return process(config, stream);
    }

    public Map<String, Map<String, Double>> process(ReportConfigDto config, Stream<Object[]> dbResults) {
        // 1. Initialize result matrix with 0.0 values
        Map<String, Map<String, Double>> matrix = new HashMap<>();
        for (ReportRowDto row : config.getRows()) {
            Map<String, Double> colVals = new HashMap<>();
            for (ColumnDefDto col : config.getColumns()) {
                colVals.put(col.colId().toUpperCase(), 0.0);
                if (col.colType() == Enums.ColType.ROLLING) {
                    int rollingN = col.rollingN() != null ? col.rollingN() : 1;
                    for (int i = 1; i <= rollingN; i++) {
                        colVals.put((col.colId() + "_" + i).toUpperCase(), 0.0);
                    }
                }
            }
            matrix.put(row.rowId().toUpperCase(), colVals);
        }

        // 2. Populate DATA rows from SQL database query results
        List<String> granularityAliases = new ArrayList<>();
        if (config.getGranularity() != null && !config.getGranularity().isBlank()) {
            for (String s : config.getGranularity().split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    granularityAliases.add(SqlGeneratorService.getGranularityAlias(trimmed));
                }
            }
        }

        if (dbResults != null) {
            dbResults.forEach(row -> {
                if (row.length < 3) {
                    return;
                }
                String rid = row[0] != null ? row[0].toString().toUpperCase() : "";
                String cid = row[1] != null ? row[1].toString().toUpperCase() : "";
                Object valObj = row[2];
                double val = 0.0;
                if (valObj instanceof Number) {
                    val = ((Number) valObj).doubleValue();
                } else if (valObj != null) {
                    try {
                        val = Double.parseDouble(valObj.toString());
                    } catch (NumberFormatException e) {
                        // ignore
                    }
                }

                // Dynamically reconstruct row_id for breakdown rows if granularity is defined
                if (!granularityAliases.isEmpty()) {
                    boolean isBreakdown = false;
                    for (int i = 0; i < granularityAliases.size(); i++) {
                        int indexInRow = 3 + i;
                        if (indexInRow < row.length && row[indexInRow] != null) {
                            isBreakdown = true;
                            break;
                        }
                    }
                    if (isBreakdown) {
                        StringBuilder sb = new StringBuilder(rid);
                        for (int i = 0; i < granularityAliases.size(); i++) {
                            sb.append("|");
                            int indexInRow = 3 + i;
                            if (indexInRow < row.length && row[indexInRow] != null) {
                                sb.append(row[indexInRow].toString());
                            }
                        }
                        rid = sb.toString().toUpperCase();
                    }
                }

                if (rid.contains("|")) {
                    String parentRid = rid.substring(0, rid.indexOf("|"));
                    if (matrix.containsKey(parentRid)) {
                        if (!matrix.containsKey(rid)) {
                            Map<String, Double> colVals = new HashMap<>();
                            for (Map.Entry<String, Double> entry : matrix.get(parentRid).entrySet()) {
                                colVals.put(entry.getKey(), 0.0);
                            }
                            matrix.put(rid, colVals);
                        }
                        matrix.get(rid).put(cid, val);
                    }
                } else {
                    if (matrix.containsKey(rid) && matrix.get(rid).containsKey(cid)) {
                        matrix.get(rid).put(cid, val);
                    }
                }
            });
        }

        // 3. Phase 1 (Column Calculations): Evaluates sqlColumn: false metrics (horizontal column formulas)
        for (ColumnDefDto col : config.getCalcColumns()) {
            String cid = col.colId().toUpperCase();
            List<String> rowIds = new ArrayList<>(matrix.keySet());
            for (String rid : rowIds) {
                String parentRid = rid.contains("|") ? rid.substring(0, rid.indexOf("|")) : rid;
                ReportRowDto row = config.getRow(parentRid);
                if (row != null && row.isEnabledFor(cid)) {
                    double val = evaluateFormula(col.formulaExpr(), matrix.get(rid));
                    matrix.get(rid).put(cid, val);
                }
            }
        }

        // 4. Phase 2 (Cross-Row Calculations): Evaluates calculated rows vertically using topological sorting
        List<String> evalOrder = getTopologicalOrder(config);
        for (String rid : evalOrder) {
            ReportRowDto row = config.getRow(rid);
            if (row != null && row.isCalcRow()) {
                Set<String> suffixes = new HashSet<>();
                for (String matrixRowId : matrix.keySet()) {
                    if (matrixRowId.contains("|")) {
                        suffixes.add(matrixRowId.substring(matrixRowId.indexOf("|") + 1));
                    }
                }

                for (ColumnDefDto col : config.getColumns()) {
                    List<String> colIdsToEval = new ArrayList<>();
                    colIdsToEval.add(col.colId().toUpperCase());
                    if (col.colType() == Enums.ColType.ROLLING) {
                        int rollingN = col.rollingN() != null ? col.rollingN() : 1;
                        for (int i = 1; i <= rollingN; i++) {
                            colIdsToEval.add((col.colId() + "_" + i).toUpperCase());
                        }
                    }

                    for (String cid : colIdsToEval) {
                        String checkCid = cid.contains("_") ? cid.substring(0, cid.indexOf("_")) : cid;
                        if (row.isEnabledFor(checkCid)) {
                            // 1. Evaluate for the parent row
                            Map<String, Double> parentContext = new HashMap<>();
                            for (Map.Entry<String, Map<String, Double>> mEntry : matrix.entrySet()) {
                                if (!mEntry.getKey().contains("|")) {
                                    parentContext.put(mEntry.getKey(), mEntry.getValue().getOrDefault(cid, 0.0));
                                }
                            }
                            String formula = (row.source() != null) ? row.source().getRawSql() : "";
                            double parentVal = evaluateFormula(formula, parentContext);
                            matrix.get(rid).put(cid, parentVal);

                            // 2. Evaluate for each granularity suffix combination
                            for (String suffix : suffixes) {
                                String subRid = rid + "|" + suffix;
                                if (!matrix.containsKey(subRid)) {
                                    Map<String, Double> colVals = new HashMap<>();
                                    for (ColumnDefDto c : config.getColumns()) {
                                        colVals.put(c.colId().toUpperCase(), 0.0);
                                        if (c.colType() == Enums.ColType.ROLLING) {
                                            int rollingN = c.rollingN() != null ? c.rollingN() : 1;
                                            for (int i = 1; i <= rollingN; i++) {
                                                colVals.put((c.colId() + "_" + i).toUpperCase(), 0.0);
                                            }
                                        }
                                    }
                                    matrix.put(subRid, colVals);
                                }

                                Map<String, Double> subContext = new HashMap<>();
                                for (ReportRowDto r : config.getRows()) {
                                    String lookupRid = r.rowId().toUpperCase();
                                    String lookupSubRid = lookupRid + "|" + suffix;
                                    double val = 0.0;
                                    if (matrix.containsKey(lookupSubRid)) {
                                        val = matrix.get(lookupSubRid).getOrDefault(cid, 0.0);
                                    } else {
                                        val = matrix.get(lookupRid).getOrDefault(cid, 0.0);
                                    }
                                    subContext.put(lookupRid, val);
                                }

                                double subVal = evaluateFormula(formula, subContext);
                                matrix.get(subRid).put(cid, subVal);
                            }
                        }
                    }
                }
            }
        }

        return matrix;
    }

    public List<String> getTopologicalOrder(ReportConfigDto config) {
        Set<String> allRowIds = new HashSet<>();
        for (ReportRowDto r : config.getRows()) {
            allRowIds.add(r.rowId().toUpperCase());
        }

        Map<String, List<String>> adj = new HashMap<>();
        for (ReportRowDto r : config.getRows()) {
            String rid = r.rowId().toUpperCase();
            adj.put(rid, new ArrayList<>());
            if (r.isCalcRow() && r.source() != null && r.source().getRawSql() != null) {
                List<String> refs = getReferencedRows(r.source().getRawSql(), allRowIds);
                adj.get(rid).addAll(refs); // r depends on refs
            }
        }

        List<String> order = new ArrayList<>();
        Map<String, Integer> state = new HashMap<>();
        for (String rid : allRowIds) {
            state.put(rid, 0); // 0 = unvisited
        }

        for (String rid : allRowIds) {
            if (state.get(rid) == 0) {
                dfs(rid, adj, state, order);
            }
        }

        return order;
    }

    private void dfs(String node, Map<String, List<String>> adj, Map<String, Integer> state, List<String> order) {
        state.put(node, 1); // visiting
        for (String dep : adj.getOrDefault(node, Collections.emptyList())) {
            int depState = state.getOrDefault(dep, 0);
            if (depState == 1) {
                throw new CircularReferenceException("Circular dependency reference detected involving row: " + node + " and " + dep);
            } else if (depState == 0) {
                dfs(dep, adj, state, order);
            }
        }
        state.put(node, 2); // visited
        order.add(node);
    }

    private List<String> getReferencedRows(String formula, Set<String> allRowIds) {
        List<String> refs = new ArrayList<>();
        if (formula == null || formula.isBlank()) {
            return refs;
        }
        List<String> sortedRowIds = new ArrayList<>(allRowIds);
        sortedRowIds.sort((a, b) -> Integer.compare(b.length(), a.length()));
        String upperFormula = formula.toUpperCase();
        for (String rid : sortedRowIds) {
            String regex = "\\b" + Pattern.quote(rid) + "\\b";
            if (Pattern.compile(regex).matcher(upperFormula).find()) {
                refs.add(rid);
            }
        }
        return refs;
    }

    public double evaluateFormula(String formula, Map<String, Double> context) {
        if (formula == null || formula.isBlank() || context == null || context.isEmpty()) {
            return 0.0;
        }

        String cleanedFormula = formula.trim();

        // Sort keys by length in descending order to avoid partial matching (e.g. R11 replaced before R1).
        List<String> sortedKeys = new ArrayList<>(context.keySet());
        sortedKeys.sort((a, b) -> Integer.compare(b.length(), a.length()));

        Set<String> variables = new HashSet<>();
        for (String key : sortedKeys) {
            // Find word boundaries to avoid replacing subparts
            String regex = "\\b" + Pattern.quote(key) + "\\b";
            Matcher matcher = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(cleanedFormula);
            if (matcher.find()) {
                variables.add(key);
            }
        }

        if (variables.isEmpty()) {
            try {
                return new ExpressionBuilder(cleanedFormula).build().evaluate();
            } catch (Exception e) {
                return 0.0;
            }
        }

        try {
            ExpressionBuilder builder = new ExpressionBuilder(cleanedFormula);
            builder.variables(variables);
            Expression expression = builder.build();

            for (String var : variables) {
                expression.setVariable(var, context.getOrDefault(var, 0.0));
            }

            double result = expression.evaluate();
            return Double.isNaN(result) || Double.isInfinite(result) ? 0.0 : result;
        } catch (ArithmeticException e) {
            return 0.0; // division by zero
        } catch (Exception e) {
            return 0.0;
        }
    }

    private Object getMapValueCaseInsensitive(Map<String, Object> map, String key) {
        if (map == null || key == null) {
            return null;
        }
        if (map.containsKey(key)) {
            return map.get(key);
        }
        for (String mk : map.keySet()) {
            if (mk.equalsIgnoreCase(key)) {
                return map.get(mk);
            }
        }
        return null;
    }
}
