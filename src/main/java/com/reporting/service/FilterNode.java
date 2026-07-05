package com.reporting.service;

/**
 * Sealed interface representing a node in the filter abstract syntax tree (AST).
 *
 * @since 1.2.0
 */
public sealed interface FilterNode permits RuleNode, GroupNode {
}
