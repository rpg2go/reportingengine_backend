package com.reporting.service;

import com.reporting.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportValidationService Unit Tests")
public class ReportValidationServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ReportValidationService validationService;

    @BeforeEach
    public void setUp() {
        // Setup mock response for information_schema query
        List<Map<String, Object>> mockColumns = List.of(
            Map.of("table_name", "fact_sales", "column_name", "amount", "data_type", "numeric"),
            Map.of("table_name", "fact_sales", "column_name", "product_id", "data_type", "varchar")
        );
        lenient().when(jdbcTemplate.queryForList(anyString())).thenReturn(mockColumns);
    }

    @Test
    @DisplayName("validateConfiguration - detecting circular column references")
    public void validate_circularColumnReferences_shouldReportCriticalError() {
        // Arrange: C1 depends on C2, C2 depends on C1
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.CALC, 0, null, "C2 + 10", 1),
            new ColumnDefDto("C2", "Col 2", Enums.ColType.CALC, 0, null, "C1 * 0.5", 2)
        );
        ReportConfigDto config = new ReportConfigDto(
            "RPT1", "Test", columns, Collections.emptyList(), null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "monthly", null, null, false, null, null
        );

        // Act
        ValidationResult result = validationService.validateConfiguration(config);

        // Assert
        assertThat(result.isValid()).isFalse();
        Optional<ValidationError> circularErr = result.getErrors().stream()
            .filter(e -> e.getDisplayMessage().contains("Circular reference detected in columns"))
            .findFirst();
        assertThat(circularErr).isPresent();
        assertThat(circularErr.get().getErrorSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("validateConfiguration - detecting circular row references")
    public void validate_circularRowReferences_shouldReportCriticalError() {
        // Arrange: R1 depends on R2, R2 depends on R1
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "RPT1", "Row 1", Enums.RowType.calc, new MeasureDefinition("raw", null, null, null, "R2"), null, "normal", 0, 1, Set.of(), null),
            new ReportRowDto("R2", "RPT1", "Row 2", Enums.RowType.calc, new MeasureDefinition("raw", null, null, null, "R1"), null, "normal", 0, 2, Set.of(), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "RPT1", "Test", Collections.emptyList(), rows, null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "monthly", null, null, false, null, null
        );

        // Act
        ValidationResult result = validationService.validateConfiguration(config);

        // Assert
        assertThat(result.isValid()).isFalse();
        Optional<ValidationError> circularErr = result.getErrors().stream()
            .filter(e -> e.getDisplayMessage().contains("Circular reference detected in rows"))
            .findFirst();
        assertThat(circularErr).isPresent();
        assertThat(circularErr.get().getErrorSeverity()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("validateConfiguration - missing column or row reference tokens")
    public void validate_missingReferenceTokens_shouldReportCriticalError() {
        // Arrange: C1 references C99 (non-existent)
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.CALC, 0, null, "C99 + 10", 1)
        );
        ReportConfigDto config = new ReportConfigDto(
            "RPT1", "Test", columns, Collections.emptyList(), null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "monthly", null, null, false, null, null
        );

        // Act
        ValidationResult result = validationService.validateConfiguration(config);

        // Assert
        assertThat(result.isValid()).isFalse();
        Optional<ValidationError> tokenErr = result.getErrors().stream()
            .filter(e -> e.getDisplayMessage().contains("Formula references column ID 'C99'"))
            .findFirst();
        assertThat(tokenErr).isPresent();
    }

    @Test
    @DisplayName("validateConfiguration - unbalanced parentheses and arithmetic syntax errors")
    public void validate_unbalancedParentheses_shouldReportCriticalError() {
        // Arrange: C1 has unclosed parenthesis
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.CALC, 0, null, "(C1 * 2", 1)
        );
        ReportConfigDto config = new ReportConfigDto(
            "RPT1", "Test", columns, Collections.emptyList(), null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "monthly", null, null, false, null, null
        );

        // Act
        ValidationResult result = validationService.validateConfiguration(config);

        // Assert
        assertThat(result.isValid()).isFalse();
        Optional<ValidationError> parenErr = result.getErrors().stream()
            .filter(e -> e.getDisplayMessage().contains("unclosed open parenthesis"))
            .findFirst();
        assertThat(parenErr).isPresent();
    }

    @Test
    @DisplayName("validateConfiguration - naked division should trigger warning")
    public void validate_nakedDivision_shouldTriggerWarning() {
        // Arrange: C1 is visual/regular column, C2 is CALC referencing C1 with division
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1),
            new ColumnDefDto("C2", "Col 2", Enums.ColType.CALC, 0, null, "100 / C1", 2)
        );
        ReportConfigDto config = new ReportConfigDto(
            "RPT1", "Test", columns, Collections.emptyList(), null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "monthly", null, null, false, null, null
        );

        // Act
        ValidationResult result = validationService.validateConfiguration(config);

        // Assert
        Optional<ValidationError> divWarning = result.getErrors().stream()
            .filter(e -> e.getErrorSeverity().equals("WARNING") && e.getDisplayMessage().contains("Potential division-by-zero"))
            .findFirst();
        assertThat(divWarning).isPresent();
    }

    @Test
    @DisplayName("validateConfiguration - database column validation in visual mode (existence and type check)")
    public void validate_databaseVisualModeCheck_shouldValidateExistenceAndNumericAggs() {
        // Row 1: references nonexistent column -> should fail
        // Row 2: numeric aggregation on non-numeric (varchar) column -> should fail
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "RPT1", "Row 1", Enums.RowType.data, 
                new MeasureDefinition("visual", "SUM", "non_existent_col", "analytics.fact_sales", null), null, "normal", 0, 1, Set.of(), null),
            new ReportRowDto("R2", "RPT1", "Row 2", Enums.RowType.data, 
                new MeasureDefinition("visual", "SUM", "product_id", "analytics.fact_sales", null), null, "normal", 0, 2, Set.of(), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "RPT1", "Test", Collections.emptyList(), rows, null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "monthly", null, null, false, null, null
        );

        // Act
        ValidationResult result = validationService.validateConfiguration(config);

        // Assert
        assertThat(result.isValid()).isFalse();
        
        Optional<ValidationError> nonExistentColErr = result.getErrors().stream()
            .filter(e -> e.getElementId().equals("R1") && e.getDisplayMessage().contains("does not exist in table"))
            .findFirst();
        assertThat(nonExistentColErr).isPresent();

        Optional<ValidationError> nonNumericErr = result.getErrors().stream()
            .filter(e -> e.getElementId().equals("R2") && e.getDisplayMessage().contains("cannot be used with numeric aggregation"))
            .findFirst();
        assertThat(nonNumericErr).isPresent();
    }

    @Test
    @DisplayName("validateConfiguration - database column validation in raw mode")
    public void validate_databaseRawModeCheck_shouldValidateExistenceAndNumericAggs() {
        // Row 1: raw sql with numeric agg on non-numeric (varchar) column -> should fail
        // Row 2: raw sql referencing nonexistent table or column -> should fail
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "RPT1", "Row 1", Enums.RowType.data, 
                new MeasureDefinition("raw", null, null, "analytics.fact_sales", "SUM(product_id)"), null, "normal", 0, 1, Set.of(), null),
            new ReportRowDto("R2", "RPT1", "Row 2", Enums.RowType.data, 
                new MeasureDefinition("raw", null, null, "analytics.fact_sales", "SUM(non_existent)"), null, "normal", 0, 2, Set.of(), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "RPT1", "Test", Collections.emptyList(), rows, null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "monthly", null, null, false, null, null
        );

        // Act
        ValidationResult result = validationService.validateConfiguration(config);

        // Assert
        assertThat(result.isValid()).isFalse();
        
        Optional<ValidationError> rawNonNumericErr = result.getErrors().stream()
            .filter(e -> e.getElementId().equals("R1") && e.getDisplayMessage().contains("Non-numeric column 'product_id'"))
            .findFirst();
        assertThat(rawNonNumericErr).isPresent();

        Optional<ValidationError> rawNonExistentErr = result.getErrors().stream()
            .filter(e -> e.getElementId().equals("R2") && e.getDisplayMessage().contains("Column 'non_existent' does not exist in table"))
            .findFirst();
        assertThat(rawNonExistentErr).isPresent();
    }

    @Test
    @DisplayName("validateConfiguration - circular reference throws CircularReferenceException in graph builder")
    public void validate_circularReferenceThrowsCircularReferenceException() {
        PostProcessorService postProcessorService = new PostProcessorService();
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R3", "RPT1", "Row 3", Enums.RowType.calc, new MeasureDefinition("raw", null, null, null, "R4"), null, "normal", 0, 1, Set.of("C1"), null),
            new ReportRowDto("R4", "RPT1", "Row 4", Enums.RowType.calc, new MeasureDefinition("raw", null, null, null, "R3"), null, "normal", 0, 2, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "RPT1", "Test", List.of(new ColumnDefDto("C1", "C1", Enums.ColType.WEEK, 0, null, null, 1)), rows, null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "monthly", null, null, false, null, null
        );

        org.junit.jupiter.api.Assertions.assertThrows(com.reporting.exception.CircularReferenceException.class, () -> {
            postProcessorService.process(config, Collections.emptyList());
        });
    }

    @Test
    @DisplayName("validateConfiguration - calculated column formula references non-existent column ID")
    public void validate_calculatedColumnFormulaReferencesNonExistentColId() {
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "C1", Enums.ColType.WEEK, 0, null, null, 1),
            new ColumnDefDto("C2", "C2", Enums.ColType.WEEK, 0, null, null, 2),
            new ColumnDefDto("C3", "C3", Enums.ColType.WEEK, 0, null, null, 3),
            new ColumnDefDto("C4", "C4", Enums.ColType.WEEK, 0, null, null, 4),
            new ColumnDefDto("C5", "C5", Enums.ColType.WEEK, 0, null, null, 5),
            new ColumnDefDto("C6", "C6", Enums.ColType.WEEK, 0, null, null, 6),
            new ColumnDefDto("C7", "C7", Enums.ColType.CALC, 0, null, "C9 + C1", 7)
        );
        ReportConfigDto config = new ReportConfigDto(
            "RPT1", "Test", columns, Collections.emptyList(), null, 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "monthly", null, null, false, null, null
        );

        ValidationResult result = validationService.validateConfiguration(config);

        assertThat(result.isValid()).isFalse();
        Optional<ValidationError> tokenErr = result.getErrors().stream()
            .filter(e -> e.getDisplayMessage().contains("Formula references column ID 'C9'"))
            .findFirst();
        assertThat(tokenErr).isPresent();
    }

    @Test
    @DisplayName("SqlGeneratorService - safe division wrapping")
    public void testSafeDivisionWrapping() {
        SqlGeneratorService sqlGen = new SqlGeneratorService(null);
        String rawFormula = "amount / target_value";
        String safeFormula = sqlGen.makeDivisionSafe(rawFormula);
        assertThat(safeFormula).isEqualTo("amount / NULLIF(target_value, 0)");
    }
}
