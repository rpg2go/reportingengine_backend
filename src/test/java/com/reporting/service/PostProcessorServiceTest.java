package com.reporting.service;

import com.reporting.dto.ColumnDefDto;
import com.reporting.dto.Enums;
import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ReportRowDto;
import com.reporting.dto.MeasureDefinitionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostProcessorService Unit Tests")
public class PostProcessorServiceTest {

    private final PostProcessorService service = new PostProcessorService();

    @Test
    @DisplayName("evaluateFormula with simple expressions")
    public void evaluateFormula_simpleExpressions() {
        assertThat(service.evaluateFormula("1 + 2 * 3", Map.of("dummy", 0.0))).isEqualTo(7.0);
        assertThat(service.evaluateFormula("(10 - 2) / 4", Map.of("dummy", 0.0))).isEqualTo(2.0);
    }

    @Test
    @DisplayName("evaluateFormula with context variables")
    public void evaluateFormula_withVariables() {
        Map<String, Double> context = Map.of("R1", 10.0, "R2", 5.0, "R11", 100.0);
        
        // Exact matching
        assertThat(service.evaluateFormula("R1 + R2", context)).isEqualTo(15.0);
        assertThat(service.evaluateFormula("R1 / R2", context)).isEqualTo(2.0);
        
        // Test key sorting (R11 must not get partially replaced by R1)
        assertThat(service.evaluateFormula("R11 / R1", context)).isEqualTo(10.0);
        
        // Exact matching with lowercase variables and context
        Map<String, Double> lowercaseContext = Map.of("r1", 10.0, "r2", 5.0);
        assertThat(service.evaluateFormula("r1 + r2", lowercaseContext)).isEqualTo(15.0);
    }

    @Test
    @DisplayName("evaluateFormula edge cases: division by zero, invalid characters, etc.")
    public void evaluateFormula_edgeCases() {
        // Division by zero should return 0.0, not crash
        assertThat(service.evaluateFormula("5 / 0", Map.of())).isEqualTo(0.0);
        
        // Invalid formulas
        assertThat(service.evaluateFormula("invalid++formula", Map.of())).isEqualTo(0.0);
        assertThat(service.evaluateFormula(null, Map.of())).isEqualTo(0.0);
        assertThat(service.evaluateFormula("", Map.of())).isEqualTo(0.0);
    }

    @Test
    @DisplayName("process full report matrix with DATA and CALC elements")
    public void process_fullMatrixCalculations() {
        // Arrange
        // Columns: C1 (SQL), C2 (SQL), C3 (CALC formula: C1 - C2)
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1),
            new ColumnDefDto("C2", "Col 2", Enums.ColType.WTD, -1, null, null, 2),
            new ColumnDefDto("C3", "Col 3", Enums.ColType.CALC, 0, null, "C1 - C2", 3)
        );

        // Rows: R1 (DATA), R2 (DATA), R3 (CALC formula: R1 + R2)
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "m1"), null, "normal", 0, 1, Set.of("C1", "C2", "C3"), null),
            new ReportRowDto("R2", "REP1", "Row 2", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "m2"), null, "normal", 0, 2, Set.of("C1", "C2", "C3"), null),
            new ReportRowDto("R3", "REP1", "Total Row", Enums.RowType.calc, new MeasureDefinitionDTO("raw", null, null, null, "R1 + R2"), null, "total", 0, 3, Set.of("C1", "C2", "C3"), null)
        );

        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, null
        );

        // Map results. In PostProcessorService, flat list of row_id/col_id/val maps
        List<Map<String, Object>> dbResults = List.of(
            Map.of("row_id", "R1", "col_id", "C1", "val", 100.0),
            Map.of("row_id", "R1", "col_id", "C2", "val", 40.0),
            Map.of("row_id", "R2", "col_id", "C1", "val", 200.0),
            Map.of("row_id", "R2", "col_id", "C2", "val", 50.0)
        );

        // Act
        Map<String, Map<String, Double>> matrix = service.process(config, dbResults);

        // Assert
        // Check R1
        assertThat(matrix.get("R1").get("C1")).isEqualTo(100.0);
        assertThat(matrix.get("R1").get("C2")).isEqualTo(40.0);
        assertThat(matrix.get("R1").get("C3")).isEqualTo(60.0); // 100 - 40

        // Check R2
        assertThat(matrix.get("R2").get("C1")).isEqualTo(200.0);
        assertThat(matrix.get("R2").get("C2")).isEqualTo(50.0);
        assertThat(matrix.get("R2").get("C3")).isEqualTo(150.0); // 200 - 50

        // Check R3 (R1 + R2)
        assertThat(matrix.get("R3").get("C1")).isEqualTo(300.0); // 100 + 200
        assertThat(matrix.get("R3").get("C2")).isEqualTo(90.0);  // 40 + 50
        assertThat(matrix.get("R3").get("C3")).isEqualTo(210.0); // 60 + 150 (and C1 - C2 check: 300 - 90 = 210)
    }

    @Test
    @DisplayName("process ROLLING columns initializes subcolumns and evaluates cross-row formulas for subcolumns")
    public void process_rollingMatrixAndCalculations() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C7", "3-Mo Rolling", Enums.ColType.ROLLING, 0, 3, "MONTH", "", 1)
        );

        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "m1"), null, "normal", 0, 1, Set.of("C7"), null),
            new ReportRowDto("R2", "REP1", "Row 2", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "m2"), null, "normal", 0, 2, Set.of("C7"), null),
            new ReportRowDto("R3", "REP1", "Total Row", Enums.RowType.calc, new MeasureDefinitionDTO("raw", null, null, null, "R1 + R2"), null, "total", 0, 3, Set.of("C7"), null)
        );

        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "monthly", null, null, false, null, null
        );

        List<Map<String, Object>> dbResults = List.of(
            Map.of("row_id", "R1", "col_id", "C7", "val", 1000.0),
            Map.of("row_id", "R1", "col_id", "C7_1", "val", 100.0),
            Map.of("row_id", "R1", "col_id", "C7_2", "val", 200.0),
            Map.of("row_id", "R1", "col_id", "C7_3", "val", 300.0),
            Map.of("row_id", "R2", "col_id", "C7", "val", 2000.0),
            Map.of("row_id", "R2", "col_id", "C7_1", "val", 50.0),
            Map.of("row_id", "R2", "col_id", "C7_2", "val", 150.0),
            Map.of("row_id", "R2", "col_id", "C7_3", "val", 250.0)
        );

        // Act
        Map<String, Map<String, Double>> matrix = service.process(config, dbResults);

        // Assert
        // Check R1
        assertThat(matrix.get("R1").get("C7")).isEqualTo(1000.0);
        assertThat(matrix.get("R1").get("C7_1")).isEqualTo(100.0);
        assertThat(matrix.get("R1").get("C7_2")).isEqualTo(200.0);
        assertThat(matrix.get("R1").get("C7_3")).isEqualTo(300.0);

        // Check R2
        assertThat(matrix.get("R2").get("C7")).isEqualTo(2000.0);
        assertThat(matrix.get("R2").get("C7_1")).isEqualTo(50.0);
        assertThat(matrix.get("R2").get("C7_2")).isEqualTo(150.0);
        assertThat(matrix.get("R2").get("C7_3")).isEqualTo(250.0);

        // Check R3 (Total: R1 + R2)
        assertThat(matrix.get("R3").get("C7")).isEqualTo(3000.0);
        assertThat(matrix.get("R3").get("C7_1")).isEqualTo(150.0);
        assertThat(matrix.get("R3").get("C7_2")).isEqualTo(350.0);
        assertThat(matrix.get("R3").get("C7_3")).isEqualTo(550.0);
    }

    @Test
    @DisplayName("process granularity unpivoted columns reconstructs row ID with suffixes")
    public void process_granularityReconstruction() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
        );

        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "m1"), null, "normal", 0, 1, Set.of("C1"), null)
        );

        // Config has granularity defined
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "dim_location.country_name", null, null, false, null, null
        );

        // Results have country_name as separate column
        List<Map<String, Object>> dbResults = List.of(
            // Total row
            Map.of("row_id", "R1", "col_id", "C1", "val", 100.0),
            // Germany breakdown row
            Map.of("row_id", "R1", "col_id", "C1", "val", 40.0, "country_name", "Germany"),
            // Romania breakdown row
            Map.of("row_id", "R1", "col_id", "C1", "val", 60.0, "country_name", "Romania")
        );

        // Act
        Map<String, Map<String, Double>> matrix = service.process(config, dbResults);

        // Assert
        // Standard total row
        assertThat(matrix.get("R1").get("C1")).isEqualTo(100.0);
        // Breakdown rows reconstruct the row_id with suffix
        assertThat(matrix.get("R1|GERMANY").get("C1")).isEqualTo(40.0);
        assertThat(matrix.get("R1|ROMANIA").get("C1")).isEqualTo(60.0);
    }

    @Test
    @DisplayName("process with Stream<Object[]> evaluates correctly including granularity")
    public void process_streamOverloadEvaluatesCorrectly() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
        );

        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "m1"), null, "normal", 0, 1, Set.of("C1"), null),
            new ReportRowDto("R2", "REP1", "Calc 1", Enums.RowType.calc, new MeasureDefinitionDTO("raw", null, null, null, "R1 * 3"), null, "normal", 0, 2, Set.of("C1"), null)
        );

        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "country_name", null, null, false, null, null
        );

        // Row array layout: row_id, col_id, val, country_name
        java.util.stream.Stream<Object[]> dbResults = java.util.stream.Stream.of(
            new Object[]{"R1", "C1", 10.0, null},
            new Object[]{"R1", "C1", 6.0, "France"},
            new Object[]{"R1", "C1", 4.0, "Romania"}
        );

        // Act
        Map<String, Map<String, Double>> matrix = service.process(config, dbResults);

        // Assert
        // Standard parent rows
        assertThat(matrix.get("R1").get("C1")).isEqualTo(10.0);
        assertThat(matrix.get("R2").get("C1")).isEqualTo(30.0); // 10.0 * 3

        // Breakdown rows
        assertThat(matrix.get("R1|FRANCE").get("C1")).isEqualTo(6.0);
        assertThat(matrix.get("R2|FRANCE").get("C1")).isEqualTo(18.0); // 6.0 * 3

        assertThat(matrix.get("R1|ROMANIA").get("C1")).isEqualTo(4.0);
        assertThat(matrix.get("R2|ROMANIA").get("C1")).isEqualTo(12.0); // 4.0 * 3
    }
}
