package com.reporting.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MeasureDefinitionDTO {
    @JsonAlias("table")
    private String sourceTable;     // e.g., "analytics.fact_sales" or "analytics.fact_investments"
    
    private String targetColumn;    // e.g., "amount" or "principal_amount"
    
    private String aggregation;     // "SUM", "COUNT", "AVG", "MIN", "MAX"
    
    @JsonAlias("rawSql")
    private String rawExpression;   // Optional: For user-written free-text SQL pass-through overrides

    // Compatibility methods for existing codebase
    public String getTable() {
        return sourceTable;
    }

    public void setTable(String table) {
        this.sourceTable = table;
    }

    public String getRawSql() {
        return rawExpression;
    }

    public void setRawSql(String rawSql) {
        this.rawExpression = rawSql;
    }

    public String getMode() {
        if (rawExpression != null && !rawExpression.isBlank()) {
            return "raw";
        }
        return "visual";
    }

    public void setMode(String mode) {
        // Keep for Jackson compatibility
    }

    // Constructor matching the old MeasureDefinition signature for unit tests and seamless compilation
    public MeasureDefinitionDTO(String mode, String aggregation, String targetColumn, String sourceTable, String rawExpression) {
        this.sourceTable = sourceTable;
        this.targetColumn = targetColumn;
        this.aggregation = aggregation;
        this.rawExpression = rawExpression;
    }
}
