package com.reporting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for application database schemas (PostgreSQL / Cloud SQL)
 * and default fallback dataset / date dimension settings.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.db")
public class DatabaseSchemaProperties {

    /**
     * PostgreSQL schema name for catalog metadata tables (e.g. "catalog_owner").
     */
    private String catalogSchema = "catalog_owner";

    /**
     * PostgreSQL schema name for report builder storage tables (e.g. "report_builder_owner").
     */
    private String reportBuilderSchema = "report_builder_owner";

    /**
     * Fallback analytics schema / BigQuery dataset name when not specified in meta_table (e.g. "analytics").
     */
    private String analyticsSchema = "analytics";

    /**
     * Default date dimension table name (e.g. "dim_date").
     */
    private String dateTable = "dim_date";

    /**
     * Default date column name in date dimension table (e.g. "date_key", "dt_key", "dt_val", "reporting_date").
     */
    private String dateColumn = "date_key";
}
