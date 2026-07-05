package com.reporting.service;

import java.util.List;

/**
 * Record representing a terminal rule/condition node in the filter AST.
 *
 * @since 1.2.0
 */
public record RuleNode(
    String tableName,
    String columnName,
    String operator,
    List<String> values
) implements FilterNode {
    public RuleNode {
        if (values == null) {
            values = List.of();
        }
    }
}
