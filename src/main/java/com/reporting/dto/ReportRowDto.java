package com.reporting.dto;

import java.util.Set;

public record ReportRowDto(
    String rowId,
    String reportId,
    String label,
    Enums.RowType rowType,
    String source,
    String parentRowId,
    String style,
    int indentLevel,
    int displayOrder,
    Set<String> activeCols,
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
