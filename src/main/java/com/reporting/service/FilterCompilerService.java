package com.reporting.service;

import com.reporting.service.SqlGeneratorService.RowFilterGroup;
import com.reporting.service.SqlGeneratorService.RowFilterRule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Modern compiler service for parenthetical filter syntax trees.
 * Upgraded to Java 21 Record Patterns, Sealed Interfaces, and Switch Pattern Matching.
 *
 * @since 1.2.0
 */
@Service
public class FilterCompilerService {

    /**
     * Translates a deserialized {@link RowFilterGroup} tree into a type-safe {@link FilterNode} AST.
     *
     * @param group the input row filter group
     * @return the root node of the AST, or null if group is null
     */
    public FilterNode buildAst(RowFilterGroup group) {
        if (group == null) {
            return null;
        }
        List<FilterNode> children = new ArrayList<>();
        
        if (group.getRules() != null) {
            for (RowFilterRule rule : group.getRules()) {
                if (rule != null) {
                    children.add(new RuleNode(
                        rule.getTableName(),
                        rule.getColumnName(),
                        rule.getOperator(),
                        rule.getValue()
                    ));
                }
            }
        }
        
        if (group.getChildGroups() != null) {
            for (RowFilterGroup child : group.getChildGroups()) {
                FilterNode childAst = buildAst(child);
                if (childAst != null) {
                    children.add(childAst);
                }
            }
        }
        
        return new GroupNode(group.getLogicalOperator(), children);
    }

    /**
     * Compiles a {@link FilterNode} AST into a structured SQL WHERE condition string.
     * Utilizes Java 21 switch pattern matching expressions.
     *
     * @param node the root AST node to compile
     * @return compiled SQL string block, or empty string
     */
    public String compile(FilterNode node) {
        if (node == null) {
            return "";
        }
        return switch (node) {
            case RuleNode rule -> compileRule(rule);
            case GroupNode group -> compileGroup(group);
        };
    }

    private String compileRule(RuleNode rule) {
        if (rule.columnName() == null || rule.columnName().isBlank()) {
            return "";
        }
        
        String col = (rule.tableName() != null && !rule.tableName().isBlank()) 
            ? (rule.tableName().trim() + "." + rule.columnName().trim()) 
            : rule.columnName().trim();
            
        String op = rule.operator() != null ? rule.operator().trim().toLowerCase() : "is";
        List<String> values = rule.values() != null ? rule.values() : Collections.emptyList();
        
        if (rule.tableName() != null && !rule.tableName().isBlank() && !rule.tableName().matches("^[a-zA-Z0-9_\\.]+$")) {
            throw new IllegalArgumentException("Invalid table name in filter: " + rule.tableName());
        }
        if (!rule.columnName().matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid column name in filter: " + rule.columnName());
        }

        String result;
        switch (op) {
            case "=":
            case "is": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("'", "''");
                result = String.format("%s = '%s'", col, escapedVal);
                break;
            }
            case "!=":
            case "<>":
            case "is not":
            case "is different from": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("'", "''");
                result = String.format("(%s <> '%s' OR %s IS NULL)", col, escapedVal, col);
                break;
            }
            case "contains":
            case "like": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                if ("contains".equals(op)) {
                    result = String.format("%s ILIKE '%%%s%%' ESCAPE '\\'", col, escapedVal);
                } else {
                    result = String.format("%s LIKE '%%%s%%' ESCAPE '\\'", col, escapedVal);
                }
                break;
            }
            case "does not contains":
            case "not like": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                if ("does not contains".equals(op)) {
                    result = String.format("(%s NOT ILIKE '%%%s%%' ESCAPE '\\' OR %s IS NULL)", col, escapedVal, col);
                } else {
                    result = String.format("(%s NOT LIKE '%%%s%%' ESCAPE '\\' OR %s IS NULL)", col, escapedVal, col);
                }
                break;
            }
            case "start with":
            case "starts with": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                if ("start with".equals(op)) {
                    result = String.format("%s ILIKE '%s%%' ESCAPE '\\'", col, escapedVal);
                } else {
                    result = String.format("%s LIKE '%s%%' ESCAPE '\\'", col, escapedVal);
                }
                break;
            }
            case "end with":
            case "ends with": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                if ("end with".equals(op)) {
                    result = String.format("%s ILIKE '%%%s' ESCAPE '\\'", col, escapedVal);
                } else {
                    result = String.format("%s LIKE '%%%s' ESCAPE '\\'", col, escapedVal);
                }
                break;
            }
            case "is blank": {
                result = String.format("(%s IS NULL OR TRIM(CAST(%s AS TEXT)) = '')", col, col);
                break;
            }
            case "is not blank": {
                result = String.format("(%s IS NOT NULL AND TRIM(CAST(%s AS TEXT)) <> '')", col, col);
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
            case "in":
            case "in list": {
                List<String> quoted = new ArrayList<>();
                for (String v : values) {
                    quoted.add("'" + v.replace("'", "''") + "'");
                }
                if (quoted.isEmpty()) {
                    result = String.format("%s IN (NULL)", col);
                } else {
                    result = String.format("%s IN (%s)", col, String.join(", ", quoted));
                }
                break;
            }
            case "not in":
            case "not in list": {
                List<String> quoted = new ArrayList<>();
                for (String v : values) {
                    quoted.add("'" + v.replace("'", "''") + "'");
                }
                if (quoted.isEmpty()) {
                    result = String.format("%s NOT IN (NULL)", col);
                } else {
                    result = String.format("%s NOT IN (%s)", col, String.join(", ", quoted));
                }
                break;
            }
            case ">":
            case "greater_than":
            case "is greater then": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("'", "''");
                result = String.format("%s > '%s'", col, escapedVal);
                break;
            }
            case ">=":
            case "greater_equal":
            case "is greater or equal": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("'", "''");
                result = String.format("%s >= '%s'", col, escapedVal);
                break;
            }
            case "<":
            case "less_than":
            case "is less then": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("'", "''");
                result = String.format("%s < '%s'", col, escapedVal);
                break;
            }
            case "<=":
            case "less_equal":
            case "is less or equal": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("'", "''");
                result = String.format("%s <= '%s'", col, escapedVal);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported filter operator in recursive rules: " + op);
        }
        
        validateFilterExpr(result);
        return result;
    }

    private String compileGroup(GroupNode group) {
        List<String> parts = new ArrayList<>();
        for (FilterNode child : group.children()) {
            String compiledChild = compile(child);
            if (compiledChild != null && !compiledChild.isBlank()) {
                parts.add(compiledChild);
            }
        }
        if (parts.isEmpty()) {
            return "";
        }
        String conj = " " + (group.logicalOperator() != null ? group.logicalOperator().trim().toUpperCase() : "AND") + " ";
        return "(" + String.join(conj, parts) + ")";
    }

    /**
     * Validates compiled SQL fragments to protect against basic SQL injection structures.
     */
    public void validateFilterExpr(String expr) {
        if (expr == null || expr.isBlank()) {
            return;
        }
        String upper = expr.toUpperCase();
        if (upper.contains(";") || upper.contains("--") || upper.contains("/*") || upper.contains("*/")) {
            throw new IllegalArgumentException("Invalid or dangerous SQL sequences in filter expression: " + expr);
        }
        
        int openParen = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') {
                openParen++;
            } else if (c == ')') {
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
