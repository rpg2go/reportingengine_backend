package com.reporting.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class MeasureDefinition {
    private String mode; // "visual" or "raw"
    private String aggregation; // SUM, COUNT, AVG, etc.
    private String targetColumn;
    private String table;
    private String rawSql;
}
