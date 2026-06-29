package com.reporting.service;

import com.reporting.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SemanticResolverService Unit Tests")
public class SemanticResolverServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private SemanticResolverService service;

    @Test
    @DisplayName("resolveAll with no data rows returns empty map")
    public void resolveAll_noDataRows_shouldReturnEmptyMap() {
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test", List.of(), List.of(), null, null, null, null, null, null, null, null, null, null
        );
        Map<String, ResolvedMetricDto> resolved = service.resolveAll(config);
        assertThat(resolved).isEmpty();
    }

    @Test
    @DisplayName("resolveAll with empty source fields throws MetricNotFoundException")
    public void resolveAll_emptySourceFields_shouldThrowException() {
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, ""), null, "normal", 0, 1, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test", List.of(), rows, null, null, null, null, null, null, null, null, null, null
        );

        assertThatThrownBy(() -> service.resolveAll(config))
            .isInstanceOf(SemanticResolverService.MetricNotFoundException.class)
            .hasMessageContaining("Metrics not found");
    }

    @Test
    @DisplayName("resolveAll with successfully resolved measures and joins returns mapped results")
    public void resolveAll_validMeasuresAndJoins_shouldReturnResolvedMetricsMap() {
        // Arrange
        List<ColumnDefDto> columns = List.of(new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1));
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Sales", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "fact_sales_amount"), null, "normal", 0, 1, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test", columns, rows, null, null, null, null, null, null, null, null, null, null
        );

        // Mock database queries for fetchMeasures
        Map<String, Object> mockMeasureRow = Map.of(
            "measure_name", "fact_sales_amount",
            "measure_id", 501,
            "sql_expr", "SUM(amount)",
            "agg_type", "SUM",
            "data_type", "NUMERIC",
            "fact_table", "analytics.fact_sales",
            "fact_name", "fact_sales",
            "time_key", "order_date",
            "explore_id", 12
        );
        when(jdbcTemplate.queryForList(anyString(), anyMap())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("sem_measure")) {
                return List.of(mockMeasureRow);
            } else if (sql.contains("sem_join")) {
                // Mock joins query response for fetchJoins
                Map<String, Object> mockJoinRow = Map.of(
                    "explore_id", 12,
                    "join_type", "LEFT",
                    "from_table", "analytics.fact_sales",
                    "to_table", "analytics.dim_store",
                    "join_sql", "fact_sales.store_id = dim_store.id"
                );
                return List.of(mockJoinRow);
            }
            return Collections.emptyList();
        });

        // Act
        Map<String, ResolvedMetricDto> resolved = service.resolveAll(config);

        // Assert
        assertThat(resolved).containsKey("R1");
        ResolvedMetricDto metric = resolved.get("R1");
        assertThat(metric.sqlExpr()).isEqualTo("SUM(amount)");
        assertThat(metric.exploreId()).isEqualTo(12);
        assertThat(metric.joinSqls()).hasSize(1);
        assertThat(metric.joinSqls().get(0)).isEqualTo("LEFT JOIN analytics.dim_store ON fact_sales.store_id = dim_store.id");
    }

    @Test
    @DisplayName("resolveAll throws MetricNotFoundException when measures are missing in DB")
    public void resolveAll_missingMeasure_shouldThrowMetricNotFoundException() {
        // Arrange
        List<ColumnDefDto> columns = List.of(new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1));
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Sales", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "non_existent_measure"), null, "normal", 0, 1, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test", columns, rows, null, null, null, null, null, null, null, null, null, null
        );

        when(jdbcTemplate.queryForList(anyString(), anyMap())).thenReturn(Collections.emptyList());

        // Act & Assert
        assertThatThrownBy(() -> service.resolveAll(config))
            .isInstanceOf(SemanticResolverService.MetricNotFoundException.class)
            .satisfies(e -> {
                SemanticResolverService.MetricNotFoundException ex = (SemanticResolverService.MetricNotFoundException) e;
                assertThat(ex.getMissing()).hasSize(1);
                assertThat(ex.getMissing().get(0).getKey()).isEqualTo("R1");
                assertThat(ex.getMissing().get(0).getValue()).isEqualTo("non_existent_measure");
            });
    }
}
