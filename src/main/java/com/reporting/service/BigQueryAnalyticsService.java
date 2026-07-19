package com.reporting.service;

import com.google.cloud.bigquery.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * BigQuery execution layer running columnar calculations, unpacking response schemas,
 * enforcing runtime query validations, and mapping datasets.
 * Loaded only in the 'sit' (production-like) profile.
 */
@Slf4j
@Service
@Profile("sit")
public class BigQueryAnalyticsService {

    private final BigQuery bigQuery;

    @Value("${gcp.project-id}")
    private String projectId;

    @Value("${bigquery.dataset}")
    private String datasetName;

    public BigQueryAnalyticsService(BigQuery bigQuery) {
        this.bigQuery = bigQuery;
    }

    /**
     * Executes SQL on BigQuery and returns a flat list of maps, matching the jdbcTemplate queryForList contract.
     * Memory-safe for average queries, but paging is handled under the hood by BigQuery.
     */
    public List<Map<String, Object>> queryForList(String sql) {
        String qualifiedSql = qualifyTableReferences(sql);
        validateQuerySafety(qualifiedSql);
        try {
            log.info("Executing BigQuery SQL query for list: {}", qualifiedSql);
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(qualifiedSql)
                    .setUseLegacySql(false)
                    .build();

            TableResult result = bigQuery.query(queryConfig);
            Schema schema = result.getSchema();
            List<Field> fields = schema != null ? schema.getFields() : Collections.emptyList();

            List<Map<String, Object>> list = new ArrayList<>();
            for (FieldValueList row : result.iterateAll()) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < fields.size(); i++) {
                    Field field = fields.get(i);
                    map.put(field.getName(), convertFieldValue(row.get(i), field));
                }
                list.add(map);
            }
            return list;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("BigQuery query execution was interrupted", e);
        } catch (Exception e) {
            log.error("BigQuery query execution failed for SQL: {}", sql, e);
            throw new RuntimeException("BigQuery execution error: " + e.getMessage(), e);
        }
    }

    /**
     * Executes SQL on BigQuery and returns a lazy stream of Object arrays, matching the reportDataRepository stream contract.
     * Prevents memory spikes by leveraging BigQuery client-side paging under the hood.
     */
    public Stream<Object[]> queryForStream(String sql) {
        String qualifiedSql = qualifyTableReferences(sql);
        validateQuerySafety(qualifiedSql);
        try {
            log.info("Executing BigQuery SQL query for stream: {}", qualifiedSql);
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(qualifiedSql)
                    .setUseLegacySql(false)
                    .build();

            TableResult result = bigQuery.query(queryConfig);
            Schema schema = result.getSchema();
            List<Field> fields = schema != null ? schema.getFields() : Collections.emptyList();
            int fieldCount = fields.size();

            return StreamSupport.stream(result.iterateAll().spliterator(), false)
                    .map(row -> {
                        Object[] values = new Object[fieldCount];
                        for (int i = 0; i < fieldCount; i++) {
                            values[i] = convertFieldValue(row.get(i), fields.get(i));
                        }
                        return values;
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("BigQuery query execution was interrupted", e);
        } catch (Exception e) {
            log.error("BigQuery query execution failed for SQL: {}", sql, e);
            throw new RuntimeException("BigQuery execution error: " + e.getMessage(), e);
        }
    }

    /**
     * Executes SQL on BigQuery and returns a list of elements mapped to a single type (e.g. distinct values list).
     */
    public <T> List<T> queryForList(String sql, Class<T> elementType) {
        String qualifiedSql = qualifyTableReferences(sql);
        validateQuerySafety(qualifiedSql);
        try {
            log.info("Executing BigQuery SQL query for typed list ({}): {}", elementType.getSimpleName(), qualifiedSql);
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(qualifiedSql)
                    .setUseLegacySql(false)
                    .build();

            TableResult result = bigQuery.query(queryConfig);
            List<T> list = new ArrayList<>();
            for (FieldValueList row : result.iterateAll()) {
                if (!row.isEmpty()) {
                    Object val = convertFieldValue(row.get(0), null);
                    if (val != null) {
                        if (elementType.isInstance(val)) {
                            list.add(elementType.cast(val));
                        } else {
                            list.add(elementType.cast(val.toString()));
                        }
                    }
                }
            }
            return list;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("BigQuery query execution was interrupted", e);
        } catch (Exception e) {
            log.error("BigQuery query execution failed for SQL: {}", sql, e);
            throw new RuntimeException("BigQuery execution error: " + e.getMessage(), e);
        }
    }

    /**
     * Executes SQL on BigQuery and returns a single object value (e.g., date existence check).
     */
    @SuppressWarnings("unchecked")
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        String formattedSql = sql;
        if (args != null && args.length > 0) {
            // Standard placeholder replacement for simple query formats
            for (Object arg : args) {
                String replacement = arg == null ? "NULL" : "'" + arg.toString().replace("'", "''") + "'";
                formattedSql = formattedSql.replaceFirst("\\?", replacement);
            }
        }

        String qualifiedSql = qualifyTableReferences(formattedSql);
        validateQuerySafety(qualifiedSql);
        try {
            log.info("Executing BigQuery SQL query for object ({}): {}", requiredType.getSimpleName(), qualifiedSql);
            QueryJobConfiguration queryConfig = QueryJobConfiguration.newBuilder(qualifiedSql)
                    .setUseLegacySql(false)
                    .build();

            TableResult result = bigQuery.query(queryConfig);
            for (FieldValueList row : result.iterateAll()) {
                if (!row.isEmpty()) {
                    Object val = convertFieldValue(row.get(0), null);
                    if (val != null) {
                        if (requiredType.isInstance(val)) {
                            return requiredType.cast(val);
                        } else if (requiredType == Boolean.class) {
                            if (val instanceof Boolean b) {
                                return (T) b;
                            }
                            return (T) Boolean.valueOf(val.toString());
                        } else if (requiredType == String.class) {
                            return (T) val.toString();
                        }
                    }
                }
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("BigQuery query execution was interrupted", e);
        } catch (Exception e) {
            log.error("BigQuery query execution failed for SQL: {}", formattedSql, e);
            throw new RuntimeException("BigQuery execution error: " + e.getMessage(), e);
        }
    }

    /**
     * Enforces query safety. Rejects any query containing mutations or SQL injection risk.
     */
    private void validateQuerySafety(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL query must not be null or blank");
        }

        String uppercaseSql = sql.trim().toUpperCase();
        if (!uppercaseSql.startsWith("SELECT") && !uppercaseSql.startsWith("WITH")) {
            throw new SecurityException("Forbidden: Only read-only SELECT or WITH statements are allowed to run against the BigQuery warehouse.");
        }

        // Check for mutation keywords
        String[] forbiddenKeywords = {"INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "MERGE", "CREATE", "TRUNCATE"};
        for (String keyword : forbiddenKeywords) {
            if (uppercaseSql.matches(".*\\b" + keyword + "\\b.*")) {
                throw new SecurityException("Forbidden: Write or DDL command detected in BigQuery SQL query: " + keyword);
            }
        }
    }

    /**
     * Unpacks response fields from BigQuery FieldValue types into standard Java equivalents.
     */
    private Object convertFieldValue(FieldValue fieldValue, Field field) {
        if (fieldValue == null || fieldValue.isNull()) {
            return null;
        }

        if (field == null) {
            return fieldValue.getValue();
        }

        LegacySQLTypeName type = field.getType();
        if (type == null) {
            return fieldValue.getValue();
        }

        try {
            if (LegacySQLTypeName.INTEGER.equals(type)) {
                return fieldValue.getLongValue();
            } else if (LegacySQLTypeName.FLOAT.equals(type)) {
                return fieldValue.getDoubleValue();
            } else if (LegacySQLTypeName.NUMERIC.equals(type) || LegacySQLTypeName.BIGNUMERIC.equals(type)) {
                return fieldValue.getNumericValue();
            } else if (LegacySQLTypeName.BOOLEAN.equals(type)) {
                return fieldValue.getBooleanValue();
            } else if (LegacySQLTypeName.DATE.equals(type)) {
                return LocalDate.parse(fieldValue.getStringValue());
            } else if (LegacySQLTypeName.DATETIME.equals(type)) {
                return LocalDateTime.parse(fieldValue.getStringValue());
            } else if (LegacySQLTypeName.TIMESTAMP.equals(type)) {
                // BigQuery returns timestamp as epoch microseconds; convert to sql.Timestamp
                return new java.sql.Timestamp(fieldValue.getTimestampValue() / 1000);
            } else {
                return fieldValue.getStringValue();
            }
        } catch (Exception e) {
            log.warn("Conversion failed for type: {}, using raw value", type, e);
            return fieldValue.getValue();
        }
    }

    /**
     * Re-writes input SQL to prepend the active GCP project ID to dataset references.
     * Wraps fully qualified table names inside standard BigQuery backticks (`project.dataset.table`)
     * to safely escape hyphens in the GCP project ID.
     */
    private String qualifyTableReferences(String sql) {
        if (sql == null) return null;
        if (projectId == null || projectId.isBlank()) return sql;

        String targetPrefix1 = "analytics";
        String targetPrefix2 = (datasetName != null && !datasetName.isBlank()) ? datasetName : "analytics";

        String resolvedSql = sql;
        resolvedSql = replaceTableRef(resolvedSql, targetPrefix1);
        if (!targetPrefix1.equalsIgnoreCase(targetPrefix2)) {
            resolvedSql = replaceTableRef(resolvedSql, targetPrefix2);
        }
        return resolvedSql;
    }

    private String replaceTableRef(String sql, String target) {
        // Matches target.tableName or `target.tableName`, and rewrites to `projectId.target.tableName`
        // Ensuring it doesn't match if it's already prefixed by projectId.
        String quotedProjectId = java.util.regex.Pattern.quote(projectId + ".");
        String quotedTarget = java.util.regex.Pattern.quote(target);
        String patternStr = "(?i)(?<!" + quotedProjectId + ")(?:`?\\b" + quotedTarget + "\\.([a-zA-Z0-9_]+)\\b`?)";
        
        String replacement = "`" + projectId + "." + target + ".$1`";
        return sql.replaceAll(patternStr, replacement);
    }
}
