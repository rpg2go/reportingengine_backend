package com.reporting.service;

import com.reporting.dto.*;
import com.reporting.exception.CircularReferenceException;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Service
public class PostProcessorService {

    public Map<String, Map<String, Double>> process(ReportConfigDto config, List<Map<String, Object>> dbResults) {
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

        // 2. Populate DATA rows from SQL database query results (flat matrix format)
        if (dbResults != null) {
            for (Map<String, Object> map : dbResults) {
                String rid = map.get("row_id") != null ? map.get("row_id").toString().toUpperCase() : "";
                String cid = map.get("col_id") != null ? map.get("col_id").toString().toUpperCase() : "";
                Object valObj = map.get("val");
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
                if (matrix.containsKey(rid) && matrix.get(rid).containsKey(cid)) {
                    matrix.get(rid).put(cid, val);
                }
            }
        }

        // 3. Phase 1 (Column Calculations): Evaluates sqlColumn: false metrics (horizontal column formulas)
        for (ColumnDefDto col : config.getCalcColumns()) {
            String cid = col.colId().toUpperCase();
            for (ReportRowDto row : config.getRows()) {
                String rid = row.rowId().toUpperCase();
                if (row.isEnabledFor(cid)) {
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
                            // Extract context for THIS column across all row IDs
                            Map<String, Double> colContext = new HashMap<>();
                            for (Map.Entry<String, Map<String, Double>> mEntry : matrix.entrySet()) {
                                colContext.put(mEntry.getKey(), mEntry.getValue().getOrDefault(cid, 0.0));
                            }
                            String formula = (row.source() != null) ? row.source().getRawSql() : "";
                            double val = evaluateFormula(formula, colContext);
                            matrix.get(rid).put(cid, val);
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
}
