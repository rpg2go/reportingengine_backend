package com.reporting.service;

import com.reporting.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import com.reporting.catalog.SchemaGraphRouter;

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
    private SchemaGraphRouter schemaGraphRouter;
    private SqlGeneratorService service;

    @BeforeEach
    public void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        schemaGraphRouter = mock(SchemaGraphRouter.class);
        
        // Mock getTimeKeyForTable
        when(jdbcTemplate.queryForObject(
            eq("SELECT time_key FROM reporting.meta_table WHERE schema_name || '.' || table_name = ?"),
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

        // Mock queryForList for columnExists
        when(jdbcTemplate.queryForList(
            any(String.class),
            eq(String.class),
            any(Object.class),
            any(Object.class)
        )).thenReturn(List.of(
            "customer_id", "amount", "order_date", "quantity", "region", "category", "name", "status", "country", "code", "prefix", "suffix", "interest_rate"
        ));

        // Mock SchemaGraphRouter
        when(schemaGraphRouter.computeJoinClauses(anyString(), anySet()))
            .thenReturn(List.of("LEFT JOIN dim_rm ON dim_rm.id = analytics.fact_sales.rm_id"));

        service = new SqlGeneratorService(jdbcTemplate, schemaGraphRouter, null);
    }

    @Test
    @DisplayName("generate with empty resolved metrics should return simple query message")
    public void generate_emptyResolvedMetrics_shouldReturnDummyMessage() {
        ReportConfigDto config = new ReportConfigDto();
        String sql = service.generate(config);
        assertThat(sql).contains("SELECT '' AS row_id, '' AS col_id, 0.0::DOUBLE PRECISION AS val WHERE FALSE");
    }

    @Test
    @DisplayName("generate with valid metrics compiles valid CTE queries")
    public void generate_validConfigAndMetrics_shouldCompileCteSql() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
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
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("cte_fact_sales AS (");
        assertThat(sql).contains("FROM analytics.fact_sales");
        assertThat(sql).contains("SELECT 'R1' AS row_id, 'C1' AS col_id, CAST(SUM(val_r1_c1) AS DOUBLE PRECISION) AS val, CAST(NULL AS VARCHAR) AS weekly FROM combined_data");
    }

    @Test
    @DisplayName("generate with DISTINCT aggregations formats queries correctly")
    public void generate_withDistinctAggregations_shouldHandleDistinctClause() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
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
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("COUNT(DISTINCT CASE WHEN analytics.fact_sales.order_date >= '2026-05-25' AND analytics.fact_sales.order_date <= '2026-05-26' THEN (user_id) ELSE NULL END)");
    }

    @Test
    @DisplayName("validateFilterExpr should prevent SQL injection tokens")
    public void validateFilterExpr_sqlInjectionTokens_shouldThrowException() {
        List<ColumnDefDto> columns = List.of(new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1));
        
        // UNION injection
        List<ReportRowDto> rowsUnion = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), "region = 'North' UNION SELECT * FROM users")
        );
        ReportConfigDto configUnion = new ReportConfigDto("REP1", "Test", columns, rowsUnion, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        
        assertThatThrownBy(() -> service.generate(configUnion))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or dangerous SQL sequences");

        // Comment block injection (--)
        List<ReportRowDto> rowsComment = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), "region = 'North' -- comment")
        );
        ReportConfigDto configComment = new ReportConfigDto("REP1", "Test", columns, rowsComment, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        assertThatThrownBy(() -> service.generate(configComment))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or dangerous SQL sequences");
    }

    @Test
    @DisplayName("validateFilterExpr should check matching parentheses")
    public void validateFilterExpr_unmatchedParentheses_shouldThrowException() {
        List<ColumnDefDto> columns = List.of(new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1));

        // Unmatched open parenthesis
        List<ReportRowDto> rowsOpen = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), "(region = 'North'")
        );
        ReportConfigDto configOpen = new ReportConfigDto("REP1", "Test", columns, rowsOpen, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        assertThatThrownBy(() -> service.generate(configOpen))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unmatched parentheses");

        // Unmatched close parenthesis
        List<ReportRowDto> rowsClose = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), "region = 'North')")
        );
        ReportConfigDto configClose = new ReportConfigDto("REP1", "Test", columns, rowsClose, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        assertThatThrownBy(() -> service.generate(configClose))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unmatched parentheses");
    }

    @Test
    @DisplayName("generate with general filters maps Looker operators to SQL correctly")
    public void generate_withGeneralFilters_shouldMapOperators() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
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
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("WHERE ((analytics.fact_sales.amount = '100') AND (dim_rm.status = 'active'))");
    }

    @Test
    @DisplayName("generate with general filters maps negation operators with null-safety")
    public void generate_withGeneralFiltersNegation_shouldBeNullSafe() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
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
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("WHERE (((analytics.fact_sales.region <> 'West' OR analytics.fact_sales.region IS NULL)) " +
            "AND ((analytics.fact_sales.category <> 'Furniture' OR analytics.fact_sales.category IS NULL)) " +
            "AND ((analytics.fact_sales.name NOT LIKE '%John%' ESCAPE '\\' OR analytics.fact_sales.name IS NULL)))");
    }

    @Test
    @DisplayName("generate with general filters splits IN operator values by comma")
    public void generate_withGeneralFiltersIn_shouldSplitValues() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
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
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("WHERE ((analytics.fact_sales.country IN ('US', 'CA', 'FR')))");
    }

    @Test
    @DisplayName("generate with general filters maps special operators and ignores value")
    public void generate_withGeneralFiltersSpecial_shouldIgnoreValue() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
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
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("WHERE (((analytics.fact_sales.region IS NULL OR TRIM(CAST(analytics.fact_sales.region AS TEXT)) = '')) " +
            "AND ((analytics.fact_sales.category IS NOT NULL AND TRIM(CAST(analytics.fact_sales.category AS TEXT)) <> '')) " +
            "AND (analytics.fact_sales.name IS NULL) " +
            "AND (analytics.fact_sales.status IS NOT NULL))");
    }

    @Test
    @DisplayName("generate with general filters handles wildcard search escaping")
    public void generate_withGeneralFiltersWildcards_shouldEscape() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
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
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("WHERE ((analytics.fact_sales.code LIKE '%10\\%\\_\\\\%' ESCAPE '\\') " +
            "AND (analytics.fact_sales.prefix LIKE 'abc\\%%' ESCAPE '\\') " +
            "AND (analytics.fact_sales.suffix LIKE '%\\_xyz' ESCAPE '\\'))");
    }

    @Test
    @DisplayName("generate with general filters compiles comparison operators correctly")
    public void generate_withGeneralFiltersComparison_shouldCompile() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
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
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("WHERE ((analytics.fact_sales.amount > '100') " +
            "AND (analytics.fact_sales.amount >= '150') " +
            "AND (analytics.fact_sales.quantity < '10') " +
            "AND (analytics.fact_sales.quantity <= '20'))");
    }

    @Test
    @DisplayName("generate with general filters compiles all 16 operators correctly")
    public void generate_withAllSixteenOperators_shouldCompile() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        
        String generalFiltersJson = "[" +
            "{\"attribute\":\"name\",\"operator\":\"is\",\"value\":\"Alice\"}," +
            "{\"attribute\":\"name\",\"operator\":\"is not\",\"value\":\"Bob\"}," +
            "{\"attribute\":\"name\",\"operator\":\"contains\",\"value\":\"li\"}," +
            "{\"attribute\":\"name\",\"operator\":\"does not contains\",\"value\":\"za\"}," +
            "{\"attribute\":\"name\",\"operator\":\"start with\",\"value\":\"Al\"}," +
            "{\"attribute\":\"name\",\"operator\":\"end with\",\"value\":\"ce\"}," +
            "{\"attribute\":\"name\",\"operator\":\"is blank\",\"value\":\"\"}," +
            "{\"attribute\":\"name\",\"operator\":\"is not blank\",\"value\":\"\"}," +
            "{\"attribute\":\"name\",\"operator\":\"is null\",\"value\":\"\"}," +
            "{\"attribute\":\"name\",\"operator\":\"is not null\",\"value\":\"\"}," +
            "{\"attribute\":\"name\",\"operator\":\"in list\",\"value\":\"Alice, Bob\"}," +
            "{\"attribute\":\"name\",\"operator\":\"not in list\",\"value\":\"Charlie, Dave\"}," +
            "{\"attribute\":\"name\",\"operator\":\"is different from\",\"value\":\"Eve\"}," +
            "{\"attribute\":\"amount\",\"operator\":\"is greater then\",\"value\":\"10\"}," +
            "{\"attribute\":\"amount\",\"operator\":\"is greater or equal\",\"value\":\"20\"}," +
            "{\"attribute\":\"amount\",\"operator\":\"is less then\",\"value\":\"30\"}," +
            "{\"attribute\":\"amount\",\"operator\":\"is less or equal\",\"value\":\"40\"}" +
            "]";
            
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, generalFiltersJson
        );

        // Act
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("analytics.fact_sales.name = 'Alice'");
        assertThat(sql).contains("(analytics.fact_sales.name <> 'Bob' OR analytics.fact_sales.name IS NULL)");
        assertThat(sql).contains("analytics.fact_sales.name ILIKE '%li%' ESCAPE '\\'");
        assertThat(sql).contains("(analytics.fact_sales.name NOT ILIKE '%za%' ESCAPE '\\' OR analytics.fact_sales.name IS NULL)");
        assertThat(sql).contains("analytics.fact_sales.name ILIKE 'Al%' ESCAPE '\\'");
        assertThat(sql).contains("analytics.fact_sales.name ILIKE '%ce' ESCAPE '\\'");
        assertThat(sql).contains("(analytics.fact_sales.name IS NULL OR TRIM(CAST(analytics.fact_sales.name AS TEXT)) = '')");
        assertThat(sql).contains("(analytics.fact_sales.name IS NOT NULL AND TRIM(CAST(analytics.fact_sales.name AS TEXT)) <> '')");
        assertThat(sql).contains("analytics.fact_sales.name IS NULL");
        assertThat(sql).contains("analytics.fact_sales.name IS NOT NULL");
        assertThat(sql).contains("analytics.fact_sales.name IN ('Alice', 'Bob')");
        assertThat(sql).contains("analytics.fact_sales.name NOT IN ('Charlie', 'Dave')");
        assertThat(sql).contains("(analytics.fact_sales.name <> 'Eve' OR analytics.fact_sales.name IS NULL)");
        assertThat(sql).contains("analytics.fact_sales.amount > '10'");
        assertThat(sql).contains("analytics.fact_sales.amount >= '20'");
        assertThat(sql).contains("analytics.fact_sales.amount < '30'");
        assertThat(sql).contains("analytics.fact_sales.amount <= '40'");
    }

    @Test
    @DisplayName("generate with general filters containing conjunction and unknown properties should parse and compile successfully")
    public void generate_withConjunctionAndUnknownFilterProperties_shouldParseAndCompile() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
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
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("WHERE ((analytics.fact_sales.interest_rate IS NOT NULL))");
    }

    @Test
    @DisplayName("generate with row filter containing raw SQL condition should compile successfully")
    public void generate_withRawSqlRowFilter_shouldCompileSuccessfully() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
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
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("AND (region_id = 1)");
    }

    @Test
    @DisplayName("generate with row filter containing JSON array should parse and compile successfully")
    public void generate_withJsonArrayRowFilter_shouldParseAndCompileSuccessfully() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
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
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("AND ((location_id = '1') AND (interest_rate <= '5'))");
    }

    @Test
    @DisplayName("generate with quick filters compiles with sequential AND and single outer parenthesis")
    public void generate_withQuickFilters_shouldCompileWithSequentialAnd() {
        // Arrange
        List<ColumnDefDto> columns = List.of(new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1));
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        String quickFiltersJson = "[" +
            "{\"attribute\":\"region\",\"operator\":\"=\",\"value\":\"East\"}," +
            "{\"attribute\":\"status\",\"operator\":\"=\",\"value\":\"active\"}" +
            "]";
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, quickFiltersJson, null
        );

        // Act
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("WHERE (analytics.fact_sales.region = 'East' AND analytics.fact_sales.status = 'active')");
    }

    @Test
    @DisplayName("generate with both quick and general filters welds them together using AND")
    public void generate_withBothQuickAndGeneralFilters_shouldWeldWithAnd() {
        // Arrange
        List<ColumnDefDto> columns = List.of(new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1));
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        String quickFiltersJson = "[" +
            "{\"attribute\":\"region\",\"operator\":\"=\",\"value\":\"East\"}," +
            "{\"attribute\":\"status\",\"operator\":\"=\",\"value\":\"active\"}" +
            "]";
        String generalFiltersJson = "[" +
            "{\"attribute\":\"category\",\"operator\":\"=\",\"value\":\"Office\",\"conjunction\":\"OR\"}," +
            "{\"attribute\":\"amount\",\"operator\":\">\",\"value\":\"500\"}" +
            "]";
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, quickFiltersJson, generalFiltersJson
        );

        // Act
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("WHERE (analytics.fact_sales.region = 'East' AND analytics.fact_sales.status = 'active') " +
            "AND ((analytics.fact_sales.category = 'Office') OR (analytics.fact_sales.amount > '500'))");
    }

    @Test
    @DisplayName("generate with quick filters ignores individual conjunction properties and links using AND")
    public void generate_withQuickFiltersHavingConjunction_shouldIgnoreConjunction() {
        // Arrange
        List<ColumnDefDto> columns = List.of(new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1));
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        String quickFiltersJson = "[" +
            "{\"attribute\":\"region\",\"operator\":\"=\",\"value\":\"East\",\"conjunction\":\"OR\"}," +
            "{\"attribute\":\"status\",\"operator\":\"=\",\"value\":\"active\"}" +
            "]";
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, quickFiltersJson, null
        );

        // Act
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("WHERE (analytics.fact_sales.region = 'East' AND analytics.fact_sales.status = 'active')");
    }

    @Test
    @DisplayName("generate with general filters respecting UI toggled OR conjunction")
    public void generate_withGeneralFiltersOrConjunction_shouldRespectOr() {
        // Arrange
        List<ColumnDefDto> columns = List.of(new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1));
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        String generalFiltersJson = "[" +
            "{\"attribute\":\"category\",\"operator\":\"=\",\"value\":\"Furniture\",\"conjunction\":\"OR\"}," +
            "{\"attribute\":\"category\",\"operator\":\"=\",\"value\":\"Office\"}" +
            "]";
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, generalFiltersJson
        );

        // Act
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("WHERE ((analytics.fact_sales.category = 'Furniture') OR (analytics.fact_sales.category = 'Office'))");
    }

    @Test
    @DisplayName("generate with ROLLING columns expands query correctly with parent and sub-period selects")
    public void generate_withRollingColumns_shouldExpandQueryToSubPeriods() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C7", "3-Mo Rolling", Enums.ColType.ROLLING, 0, 3, "MONTH", "", 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("visual", "SUM", "amount", "analytics.fact_sales", null), 
                null, "normal", 0, 1, Set.of("C7"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 6, 9), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "monthly", null, null, false, null, null
        );

        // Act
        String sql = service.generate(config);

        // Assert
        // Parent alias: val_r1_c7
        assertThat(sql).contains("val_r1_c7");
        // Sub-column aliases: val_r1_c7_1, val_r1_c7_2, val_r1_c7_3
        assertThat(sql).contains("val_r1_c7_1");
        assertThat(sql).contains("val_r1_c7_2");
        assertThat(sql).contains("val_r1_c7_3");

        // Sub-column select in final unpivoting SELECT
        assertThat(sql).contains("SELECT 'R1' AS row_id, 'C7_1' AS col_id, CAST(SUM(val_r1_c7_1) AS DOUBLE PRECISION) AS val, CAST(NULL AS VARCHAR) AS monthly FROM combined_data");
        assertThat(sql).contains("SELECT 'R1' AS row_id, 'C7_2' AS col_id, CAST(SUM(val_r1_c7_2) AS DOUBLE PRECISION) AS val, CAST(NULL AS VARCHAR) AS monthly FROM combined_data");
        assertThat(sql).contains("SELECT 'R1' AS row_id, 'C7_3' AS col_id, CAST(SUM(val_r1_c7_3) AS DOUBLE PRECISION) AS val, CAST(NULL AS VARCHAR) AS monthly FROM combined_data");
    }

    @Test
    @DisplayName("generate with granularity applies sub-rows unpivoting and pre-resolves join targets")
    public void generate_withGranularity_shouldIncludeSubRowsUnpivotingAndJoins() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("visual", "SUM", "amount", "analytics.fact_sales", null), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "dim_location.country_name", null, null, false, null, null
        );

        reset(schemaGraphRouter);
        when(schemaGraphRouter.computeJoinClauses(anyString(), anySet()))
            .thenReturn(List.of("LEFT JOIN dim_location ON dim_location.id = analytics.fact_sales.location_id"));

        // Act
        String sql = service.generate(config);

        // Assert
        verify(schemaGraphRouter).computeJoinClauses(eq("analytics.fact_sales"), argThat(set -> set.contains("dim_location")));
        assertThat(sql).contains("SELECT 'R1' AS row_id, 'C1' AS col_id, CAST(SUM(val_r1_c1) AS DOUBLE PRECISION) AS val, CAST(NULL AS VARCHAR) AS country_name FROM combined_data");
        assertThat(sql).contains("SELECT 'R1' AS row_id, 'C1' AS col_id, CAST(SUM(val_r1_c1) AS DOUBLE PRECISION) AS val, country_name FROM combined_data GROUP BY country_name");
    }

    @Test
    @DisplayName("generate with recursive row filter groups compiles nested parenthetical rules")
    public void generate_withRecursiveRowFilters_shouldCompileNestedParentheses() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
        );
        
        String recursiveFilterJson = "{" +
            "\"id\":\"root\"," +
            "\"logicalOperator\":\"OR\"," +
            "\"rules\":[" +
                "{\"tableName\":\"fact_investments\",\"columnName\":\"a\",\"operator\":\"is\",\"value\":[\"1\"]}" +
            "]," +
            "\"childGroups\":[" +
                "{" +
                    "\"id\":\"child1\"," +
                    "\"logicalOperator\":\"AND\"," +
                    "\"rules\":[" +
                        "{\"tableName\":\"fact_investments\",\"columnName\":\"b\",\"operator\":\"in list\",\"value\":[\"2\",\"3\"]}" +
                    "]," +
                    "\"childGroups\":[]" +
                "}" +
            "]" +
        "}";
        
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("visual", "SUM", "amount", "fact_investments", null), 
                null, "normal", 0, 1, Set.of("C1"), recursiveFilterJson)
        );
        
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "fact_investments", "weekly", null, null, false, null, null
        );

        // Act
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("(fact_investments.a = '1' OR (fact_investments.b IN ('2', '3')))");
    }

    @Test
    @DisplayName("generate with recursive general filter groups compiles nested parenthetical rules")
    public void generate_withRecursiveGeneralFilters_shouldCompileNestedParentheses() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("visual", "SUM", "amount", "analytics.fact_sales", null), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        
        String recursiveFilterJson = "{" +
            "\"id\":\"root\"," +
            "\"logicalOperator\":\"OR\"," +
            "\"rules\":[" +
                "{\"tableName\":\"analytics.fact_sales\",\"columnName\":\"region\",\"operator\":\"=\",\"value\":[\"North\"]}" +
            "]," +
            "\"childGroups\":[" +
                "{" +
                    "\"id\":\"child1\"," +
                    "\"logicalOperator\":\"AND\"," +
                    "\"rules\":[" +
                        "{\"tableName\":\"analytics.fact_sales\",\"columnName\":\"status\",\"operator\":\"in list\",\"value\":[\"active\",\"pending\"]}" +
                    "]," +
                    "\"childGroups\":[]" +
                "}" +
            "]" +
        "}";
        
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, recursiveFilterJson
        );

        // Act
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("(analytics.fact_sales.region = 'North' OR (analytics.fact_sales.status IN ('active', 'pending')))");
    }

    @Test
    @DisplayName("generate with ROLLING colType and YEAR grain should compile sub-columns like rolling columns")
    public void generate_rollingWithYearGrain_shouldCompileSubColumns() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.ROLLING, 0, 3, "YEAR", null, "L1", null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("visual", "SUM", "amount", "analytics.fact_sales", null), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, null
        );

        // Act
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("val_r1_c1_1");
        assertThat(sql).contains("val_r1_c1_2");
        assertThat(sql).contains("val_r1_c1_3");
    }

    @Test
    @DisplayName("generate with HEADER colType should skip header columns during SQL generation")
    public void generate_withHeaderColType_shouldIgnoreHeaderColumn() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Header Col", Enums.ColType.HEADER, 0, 0, null, null, "L1", null, null, 1),
            new ColumnDefDto("C2", "Col 2", Enums.ColType.WTD, 0, 0, null, null, "L1", null, null, 2)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("visual", "SUM", "amount", "analytics.fact_sales", null), 
                null, "normal", 0, 1, Set.of("C1", "C2"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, null
        );

        // Act
        String sql = service.generate(config);

        // Assert
        assertThat(sql).doesNotContain("val_r1_c1");
        assertThat(sql).contains("val_r1_c2");
    }

    @Test
    @DisplayName("generate with PREVIOUS_YEAR periodType should subtract one year from reference date")
    public void generate_withPreviousYearPeriodType_shouldSubtractOneYear() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WTD, 0, 0, null, null, "L1", null, "PREVIOUS_YEAR", 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, 
                new MeasureDefinitionDTO("visual", "SUM", "amount", "analytics.fact_sales", null), 
                null, "normal", 0, 1, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, null
        );

        // Act
        String sql = service.generate(config);

        // Assert
        assertThat(sql).contains("analytics.fact_sales.order_date >= '2025-05-26' AND analytics.fact_sales.order_date <= '2025-05-26'");
    }
}

