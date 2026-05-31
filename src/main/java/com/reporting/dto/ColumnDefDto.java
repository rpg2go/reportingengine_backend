package com.reporting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record ColumnDefDto(
    @NotBlank(message = "Column ID is required")
    @Size(max = 10, message = "Column ID must be at most 10 characters")
    String colId,

    @Size(max = 200, message = "Label must be at most 200 characters")
    String label,

    @NotNull(message = "Column type is required")
    Enums.ColType colType,

    int periodOffset,

    Integer rollingN,

    @Size(max = 1000, message = "Formula expression must be at most 1000 characters")
    String formulaExpr,

    int displayOrder
) {
    public boolean isSqlColumn() {
        return colType != Enums.ColType.CALC;
    }
}
