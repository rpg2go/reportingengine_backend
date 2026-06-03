package com.reporting.service;

import com.reporting.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@DisplayName("SqlGeneratorService Unit Tests")
public class SqlGeneratorServiceTest {

    private JdbcTemplate jdbcTemplate;
    private SqlGeneratorService service;

    @BeforeEach
    public void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        
        // Mock getTimeKeyForTable
        when(jdbcTemplate.queryForObject(
            eq("SELECT time_key FROM reporting.sem_view WHERE table_ref = ?"),
            eq(String.class),
            any(Object.class)
        )).thenReturn("order_date");

        // Mock isNumericColumn
        when(jdbcTemplate.queryForObject(
            contains("information_schema.columns"),
            eq(String.class),
            any(Object.class),
            any(Object.class),
            any(Object.class)
        )).thenReturn("numeric");

        service = new SqlGeneratorService(jdbcTemplate);
    }

    @Test
    @DisplayName("generate with empty resolved metrics should return simple query message")
    public void generate_emptyResolvedMetrics_shouldReturnDummyMessage() {
        ReportConfigDto config = new ReportConfigDto();
        String sql = service.generate(config, Collections.emptyMap());
        assertThat(sql).contains("SELECT '' AS row_id, '' AS col_id, 0.0::DOUBLE PRECISION AS val WHERE FALSE");
    }

    @Test
    @DisplayName("generate with valid metrics compiles valid CTE queries")
    public void generate_validConfigAndMetrics_shouldCompileCteSql() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("visual", "SUM", "amount", "analytics.fact_sales", null), 
                null, "normal", 0, 1, Set.of("C1"), "region = 'North'")
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, null
        );

        // Act
        String sql = service.generate(config, Collections.emptyMap());

        // Assert
        assertThat(sql).contains("cte_fact_sales AS (");
        assertThat(sql).contains("FROM analytics.fact_sales");
        assertThat(sql).contains("SELECT 'R1' AS row_id, 'C1' AS col_id, CAST(SUM(val_r1_c1) AS DOUBLE PRECISION) AS val FROM combined_data");
    }

    @Test
    @DisplayName("generate with DISTINCT aggregations formats queries correctly")
    public void generate_withDistinctAggregations_shouldHandleDistinctClause() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "COUNT(DISTINCT user_id)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, null
        );

        // Act
        String sql = service.generate(config, Collections.emptyMap());

        // Assert
        assertThat(sql).contains("COUNT(DISTINCT CASE WHEN analytics.fact_sales.order_date >= '2026-05-25' AND analytics.fact_sales.order_date <= '2026-05-26' THEN (user_id) ELSE NULL END)");
    }

    @Test
    @DisplayName("validateFilterExpr should prevent SQL injection tokens")
    public void validateFilterExpr_sqlInjectionTokens_shouldThrowException() {
        List<ColumnDefDto> columns = List.of(new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1));
        
        // UNION injection
        List<ReportRowDto> rowsUnion = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), "region = 'North' UNION SELECT * FROM users")
        );
        ReportConfigDto configUnion = new ReportConfigDto("REP1", "Test", columns, rowsUnion, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        
        assertThatThrownBy(() -> service.generate(configUnion, Collections.emptyMap()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or dangerous SQL sequences");

        // Comment block injection (--)
        List<ReportRowDto> rowsComment = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), "region = 'North' -- comment")
        );
        ReportConfigDto configComment = new ReportConfigDto("REP1", "Test", columns, rowsComment, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        assertThatThrownBy(() -> service.generate(configComment, Collections.emptyMap()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or dangerous SQL sequences");
    }

    @Test
    @DisplayName("validateFilterExpr should check matching parentheses")
    public void validateFilterExpr_unmatchedParentheses_shouldThrowException() {
        List<ColumnDefDto> columns = List.of(new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1));

        // Unmatched open parenthesis
        List<ReportRowDto> rowsOpen = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), "(region = 'North'")
        );
        ReportConfigDto configOpen = new ReportConfigDto("REP1", "Test", columns, rowsOpen, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        assertThatThrownBy(() -> service.generate(configOpen, Collections.emptyMap()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unmatched parentheses");

        // Unmatched close parenthesis
        List<ReportRowDto> rowsClose = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), "region = 'North')")
        );
        ReportConfigDto configClose = new ReportConfigDto("REP1", "Test", columns, rowsClose, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        assertThatThrownBy(() -> service.generate(configClose, Collections.emptyMap()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unmatched parentheses");
    }

    @Test
    @DisplayName("generate with general filters maps Looker operators to SQL correctly")
    public void generate_withGeneralFilters_shouldMapOperators() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        
        String generalFiltersJson = "[" +
            "{\"attribute\":\"amount\",\"operator\":\"=\",\"value\":\"100\"}," +
            "{\"dimTable\":\"dim_rm\",\"attribute\":\"status\",\"operator\":\"is\",\"value\":\"active\"}" +
            "]";
            
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, generalFiltersJson
        );

        // Act
        String sql = service.generate(config, Collections.emptyMap());

        // Assert
        assertThat(sql).contains("WHERE (spine_raw.amount = '100') AND (dim_rm.status = 'active')");
    }

    @Test
    @DisplayName("generate with general filters maps negation operators with null-safety")
    public void generate_withGeneralFiltersNegation_shouldBeNullSafe() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        
        String generalFiltersJson = "[" +
            "{\"attribute\":\"region\",\"operator\":\"is not\",\"value\":\"West\"}," +
            "{\"attribute\":\"category\",\"operator\":\"!=\",\"value\":\"Furniture\"}," +
            "{\"attribute\":\"name\",\"operator\":\"not like\",\"value\":\"John\"}" +
            "]";
            
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, generalFiltersJson
        );

        // Act
        String sql = service.generate(config, Collections.emptyMap());

        // Assert
        assertThat(sql).contains("WHERE ((spine_raw.region <> 'West' OR spine_raw.region IS NULL)) " +
            "AND ((spine_raw.category <> 'Furniture' OR spine_raw.category IS NULL)) " +
            "AND ((spine_raw.name NOT LIKE '%John%' ESCAPE '\\' OR spine_raw.name IS NULL))");
    }

    @Test
    @DisplayName("generate with general filters splits IN operator values by comma")
    public void generate_withGeneralFiltersIn_shouldSplitValues() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        
        String generalFiltersJson = "[" +
            "{\"attribute\":\"country\",\"operator\":\"in\",\"value\":\"US, CA, FR\"}" +
            "]";
            
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, generalFiltersJson
        );

        // Act
        String sql = service.generate(config, Collections.emptyMap());

        // Assert
        assertThat(sql).contains("WHERE (spine_raw.country IN ('US', 'CA', 'FR'))");
    }

    @Test
    @DisplayName("generate with general filters maps special operators and ignores value")
    public void generate_withGeneralFiltersSpecial_shouldIgnoreValue() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        
        String generalFiltersJson = "[" +
            "{\"attribute\":\"region\",\"operator\":\"is blank\",\"value\":\"ignored\"}," +
            "{\"attribute\":\"category\",\"operator\":\"is not blank\",\"value\":null}," +
            "{\"attribute\":\"name\",\"operator\":\"is null\"}," +
            "{\"attribute\":\"status\",\"operator\":\"is not null\"}" +
            "]";
            
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, generalFiltersJson
        );

        // Act
        String sql = service.generate(config, Collections.emptyMap());

        // Assert
        assertThat(sql).contains("WHERE ((spine_raw.region IS NULL OR TRIM(spine_raw.region) = '')) " +
            "AND ((spine_raw.category IS NOT NULL AND TRIM(spine_raw.category) <> '')) " +
            "AND (spine_raw.name IS NULL) " +
            "AND (spine_raw.status IS NOT NULL)");
    }

    @Test
    @DisplayName("generate with general filters handles wildcard search escaping")
    public void generate_withGeneralFiltersWildcards_shouldEscape() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        
        String generalFiltersJson = "[" +
            "{\"attribute\":\"code\",\"operator\":\"like\",\"value\":\"10%_\\\\\"}," +
            "{\"attribute\":\"prefix\",\"operator\":\"starts with\",\"value\":\"abc%\"}," +
            "{\"attribute\":\"suffix\",\"operator\":\"ends with\",\"value\":\"_xyz\"}" +
            "]";
            
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, generalFiltersJson
        );

        // Act
        String sql = service.generate(config, Collections.emptyMap());

        // Assert
        assertThat(sql).contains("WHERE (spine_raw.code LIKE '%10\\%\\_\\\\%' ESCAPE '\\') " +
            "AND (spine_raw.prefix LIKE 'abc\\%%' ESCAPE '\\') " +
            "AND (spine_raw.suffix LIKE '%\\_xyz' ESCAPE '\\')");
    }

    @Test
    @DisplayName("generate with general filters compiles comparison operators correctly")
    public void generate_withGeneralFiltersComparison_shouldCompile() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        
        String generalFiltersJson = "[" +
            "{\"attribute\":\"amount\",\"operator\":\">\",\"value\":\"100\"}," +
            "{\"attribute\":\"amount\",\"operator\":\">=\",\"value\":\"150\"}," +
            "{\"attribute\":\"quantity\",\"operator\":\"<\",\"value\":\"10\"}," +
            "{\"attribute\":\"quantity\",\"operator\":\"<=\",\"value\":\"20\"}" +
            "]";
            
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, generalFiltersJson
        );

        // Act
        String sql = service.generate(config, Collections.emptyMap());

        // Assert
        assertThat(sql).contains("WHERE (spine_raw.amount > '100') " +
            "AND (spine_raw.amount >= '150') " +
            "AND (spine_raw.quantity < '10') " +
            "AND (spine_raw.quantity <= '20')");
    }

    @Test
    @DisplayName("generate with general filters containing conjunction and unknown properties should parse and compile successfully")
    public void generate_withConjunctionAndUnknownFilterProperties_shouldParseAndCompile() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        
        String generalFiltersJson = "[" +
            "{\"dimTable\":\"\",\"attribute\":\"interest_rate\",\"operator\":\"is not null\",\"value\":\"\",\"conjunction\":\"AND\",\"extraField\":\"ignoredVal\"}" +
            "]";
            
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, generalFiltersJson
        );

        // Act
        String sql = service.generate(config, Collections.emptyMap());

        // Assert
        assertThat(sql).contains("WHERE (spine_raw.interest_rate IS NOT NULL)");
    }

    @Test
    @DisplayName("generate with row filter containing raw SQL condition should compile successfully")
    public void generate_withRawSqlRowFilter_shouldCompileSuccessfully() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), "region_id = 1")
        );
        
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, null
        );

        // Act
        String sql = service.generate(config, Collections.emptyMap());

        // Assert
        assertThat(sql).contains("AND (region_id = 1)");
    }

    @Test
    @DisplayName("generate with row filter containing JSON array should parse and compile successfully")
    public void generate_withJsonArrayRowFilter_shouldParseAndCompileSuccessfully() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        String rowFilterJson = "[" +
            "{\"dimTable\":\"\",\"attribute\":\"location_id\",\"operator\":\"=\",\"value\":\"1\"}," +
            "{\"dimTable\":\"\",\"attribute\":\"interest_rate\",\"operator\":\"<=\",\"value\":\"5\"}" +
            "]";
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), rowFilterJson)
        );
        
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, null
        );

        // Act
        String sql = service.generate(config, Collections.emptyMap());

        // Assert
        assertThat(sql).contains("AND ((location_id = '1') AND (interest_rate <= '5'))");
    }
}
