package com.reporting.dto;

import java.util.List;

public record ResolvedMetricDto(
    String measureName,
    int measureId,
    String sqlExpr,
    String aggType,
    String dataType,
    String factTable,
    String factName,
    String timeKey,
    int exploreId,
    List<String> joinSqls
) {
    public boolean needsJoins() {
        return joinSqls != null && !joinSqls.isEmpty();
    }
}
