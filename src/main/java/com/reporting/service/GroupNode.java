package com.reporting.service;

import java.util.List;

/**
 * Record representing a logical conjunction/disjunction group node in the filter AST.
 *
 * @since 1.2.0
 */
public record GroupNode(
    String logicalOperator,
    List<FilterNode> children
) implements FilterNode {
    public GroupNode {
        if (children == null) {
            children = List.of();
        }
    }
}
