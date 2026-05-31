package com.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ValidationError {
    private String elementId;     // e.g., "R5", "C3", or "GLOBAL"
    private String fieldContext;   // e.g., "measure_definition", "formulaExpr", "filterExpr"
    private String errorSeverity;  // "CRITICAL" or "WARNING"
    private String displayMessage; // e.g., "Circular reference detected: R5 -> R3 -> R5"
}
