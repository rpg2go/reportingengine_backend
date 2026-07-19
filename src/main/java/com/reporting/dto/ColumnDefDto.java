package com.reporting.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object representing a single column definition in a report layout.
 *
 * <p>{@code rollingGrain} is only meaningful when {@code colType == ROLLING}.
 * Accepted values are {@code "DAY"}, {@code "WEEK"}, and {@code "MONTH"}.
 * When absent or {@code null}, the engine defaults to {@code "WEEK"} for
 * backward-compatibility with configurations saved before this field was introduced.</p>
 */
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

    /**
     * Time grain used when {@code colType == ROLLING}.
     * Accepted values: {@code "DAY"}, {@code "WEEK"}, {@code "MONTH"}.
     * {@code null} is treated as {@code "WEEK"} (legacy default).
     */
    @Pattern(
        regexp = "^(DAY|WEEK|MONTH|YEAR)$",
        message = "rollingGrain must be one of: DAY, WEEK, MONTH, YEAR"
    )
    String rollingGrain,

    @Size(max = 1000, message = "Formula expression must be at most 1000 characters")
    String formulaExpr,

    String tierLevel,

    String parentId,

    int displayOrder
) {
    public ColumnDefDto(String colId, String label, Enums.ColType colType, int periodOffset, Integer rollingN, String formulaExpr, int displayOrder) {
        this(colId, label, colType, periodOffset, rollingN, null, formulaExpr, "L1", null, displayOrder);
    }

    public ColumnDefDto(String colId, String label, Enums.ColType colType, int periodOffset, Integer rollingN, String rollingGrain, String formulaExpr, int displayOrder) {
        this(colId, label, colType, periodOffset, rollingN, rollingGrain, formulaExpr, "L1", null, displayOrder);
    }

    public boolean isSqlColumn() {
        return colType != Enums.ColType.CALC && colType != Enums.ColType.HEADER;
    }

    /**
     * Returns the effective rolling grain, defaulting to {@code "WEEK"} when
     * the field is absent (e.g. reports saved before this field was introduced).
     *
     * @return one of {@code "DAY"}, {@code "WEEK"}, or {@code "MONTH"}
     */
    public String effectiveRollingGrain() {
        return (rollingGrain != null && !rollingGrain.isBlank()) ? rollingGrain.toUpperCase() : "WEEK";
    }
}

