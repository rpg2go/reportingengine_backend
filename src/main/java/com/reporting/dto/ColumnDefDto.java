package com.reporting.dto;

public record ColumnDefDto(
    String colId,
    String label,
    Enums.ColType colType,
    int periodOffset,
    Integer rollingN,
    String formulaExpr,
    int displayOrder
) {
    public boolean isSqlColumn() {
        return colType != Enums.ColType.CALC;
    }
}
