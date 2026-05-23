package com.reporting.service;

import com.reporting.dto.*;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

@Service
public class PostProcessorService {

    public Map<String, Map<String, Double>> process(ReportConfigDto config, Map<String, Object> dbResults) {
        // Initialize result matrix with 0.0 values
        Map<String, Map<String, Double>> matrix = new HashMap<>();
        for (ReportRowDto row : config.getRows()) {
            Map<String, Double> colVals = new HashMap<>();
            for (ColumnDefDto col : config.getColumns()) {
                colVals.put(col.colId().toUpperCase(), 0.0);
            }
            matrix.put(row.rowId().toUpperCase(), colVals);
        }

        // Populate DATA rows from SQL database query results
        if (dbResults != null) {
            for (Map.Entry<String, Object> entry : dbResults.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                String[] parts = key.split("_");
                if (parts.length >= 3) {
                    String rid = parts[1].toUpperCase();
                    String cid = parts[2].toUpperCase();
                    if (matrix.containsKey(rid) && matrix.get(rid).containsKey(cid)) {
                        double doubleVal = 0.0;
                        if (val instanceof Number) {
                            doubleVal = ((Number) val).doubleValue();
                        } else if (val != null) {
                            try {
                                doubleVal = Double.parseDouble(val.toString());
                            } catch (NumberFormatException e) {
                                // ignore
                            }
                        }
                        matrix.get(rid).put(cid, doubleVal);
                    }
                }
            }
        }

        // 2. Execute CALC Columns (Horizontal)
        // E.g. C3 = (C1 - C2) / C2
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

        // 3. Execute calc Rows (Vertical)
        // E.g. R4 = R2 - R3
        // We iterate 3 times to resolve nested levels
        for (int i = 0; i < 3; i++) {
            for (ReportRowDto row : config.getCalcRows()) {
                String rid = row.rowId().toUpperCase();
                for (ColumnDefDto col : config.getColumns()) {
                    String cid = col.colId().toUpperCase();
                    if (row.isEnabledFor(cid)) {
                        // Extract context for THIS column across all row IDs
                        Map<String, Double> colContext = new HashMap<>();
                        for (Map.Entry<String, Map<String, Double>> mEntry : matrix.entrySet()) {
                            colContext.put(mEntry.getKey(), mEntry.getValue().getOrDefault(cid, 0.0));
                        }
                        double val = evaluateFormula(row.source(), colContext);
                        matrix.get(rid).put(cid, val);
                    }
                }
            }
        }

        return matrix;
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
