package com.reporting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public record ReportRowDto(
    @NotBlank(message = "Row ID is required")
    @Size(max = 50, message = "Row ID must be at most 50 characters")
    String rowId,

    @Size(max = 50, message = "Report ID must be at most 50 characters")
    String reportId,

    @NotBlank(message = "Label is required")
    @Size(max = 300, message = "Label must be at most 300 characters")
    String label,

    @NotNull(message = "Row type is required")
    Enums.RowType rowType,

    MeasureDefinition source,

    @Size(max = 50, message = "Parent Row ID must be at most 50 characters")
    String parentRowId,

    String style,

    int indentLevel,

    int displayOrder,

    Set<String> activeCols,

    @Size(max = 2000, message = "Filter expression must be at most 2000 characters")
    String filterExpr
) {
    public boolean isDataRow() {
        return rowType == Enums.RowType.data;
    }

    public boolean isCalcRow() {
        return rowType == Enums.RowType.calc;
    }

    public boolean isVisible() {
        return rowType != Enums.RowType.blank;
    }

    public boolean isEnabledFor(String colId) {
        return activeCols.contains(colId.toUpperCase());
    }
}
