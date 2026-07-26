package com.db.reporting.service;

import com.google.cloud.bigquery.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BigQueryAnalyticsService Unit Tests")
class BigQueryAnalyticsServiceTest {

    @Mock
    private BigQuery bigQuery;

    @Mock
    private TableResult tableResult;

    @Mock
    private Schema schema;

    private BigQueryAnalyticsService service;

    @BeforeEach
    void setUp() {
        service = new BigQueryAnalyticsService(bigQuery);
        ReflectionTestUtils.setField(service, "projectId", "test-gcp-project");
        ReflectionTestUtils.setField(service, "datasetName", "analytics");
        ReflectionTestUtils.setField(service, "maxBytesBilled", 1073741824L); // 1GB
    }

    @Nested
    @DisplayName("SQL Qualification and Table Reference Rewriting")
    class QualificationTests {

        @Test
        @DisplayName("Should qualify table reference with GCP project ID and backticks")
        void shouldQualifyTableReferences() throws InterruptedException {
            when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(tableResult);
            when(tableResult.getSchema()).thenReturn(schema);
            when(tableResult.iterateAll()).thenReturn(Collections.emptyList());

            service.queryForList("SELECT * FROM analytics.fact_sales");

            ArgumentCaptor<QueryJobConfiguration> captor = ArgumentCaptor.forClass(QueryJobConfiguration.class);
            verify(bigQuery).query(captor.capture());

            String compiledSql = captor.getValue().getQuery();
            assertThat(compiledSql).contains("`test-gcp-project.analytics.fact_sales`");
        }
    }

    @Nested
    @DisplayName("Query Safety Validations")
    class SecurityValidationTests {

        @Test
        @DisplayName("Should throw IllegalArgumentException when SQL is null or blank")
        void shouldThrowOnBlankSql() {
            assertThatThrownBy(() -> service.queryForList(""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SQL query must not be null or blank");
        }

        @Test
        @DisplayName("Should throw SecurityException when SQL does not start with SELECT or WITH")
        void shouldThrowOnNonSelectQuery() {
            assertThatThrownBy(() -> service.queryForList("SHOW TABLES"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Forbidden: Only read-only SELECT or WITH statements are allowed");
        }

        @Test
        @DisplayName("Should throw SecurityException when SQL contains mutation keyword DELETE")
        void shouldThrowOnMutationKeyword() {
            assertThatThrownBy(() -> service.queryForList("SELECT * FROM analytics.fact_sales; DELETE FROM analytics.fact_sales"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Forbidden: Write or DDL command detected");
        }

        @Test
        @DisplayName("Should throw SecurityException when SQL contains DDL keyword DROP")
        void shouldThrowOnDdlKeyword() {
            assertThatThrownBy(() -> service.queryForList("SELECT * FROM analytics.fact_sales; DROP TABLE analytics.fact_sales"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Forbidden: Write or DDL command detected");
        }
    }

    @Nested
    @DisplayName("Typed Query Execution and Result Mapping")
    class QueryExecutionTests {

        @Test
        @DisplayName("queryForObject should bind positional parameters and return converted result")
        void shouldExecuteQueryForObjectWithArgs() throws InterruptedException {
            when(bigQuery.query(any(QueryJobConfiguration.class))).thenReturn(tableResult);

            FieldValue mockFieldValue = mock(FieldValue.class);
            when(mockFieldValue.isNull()).thenReturn(false);
            when(mockFieldValue.getValue()).thenReturn(true);

            FieldValueList mockRow = mock(FieldValueList.class);
            when(mockRow.isEmpty()).thenReturn(false);
            when(mockRow.get(0)).thenReturn(mockFieldValue);

            when(tableResult.iterateAll()).thenReturn(List.of(mockRow));

            Boolean exists = service.queryForObject(
                    "SELECT COUNT(1) > 0 FROM analytics.dim_date WHERE date_key = CAST(? AS DATE)",
                    Boolean.class,
                    "2026-07-26"
            );

            assertThat(exists).isTrue();

            ArgumentCaptor<QueryJobConfiguration> captor = ArgumentCaptor.forClass(QueryJobConfiguration.class);
            verify(bigQuery).query(captor.capture());
            assertThat(captor.getValue().getPositionalParameters()).hasSize(1);
        }
    }
}
