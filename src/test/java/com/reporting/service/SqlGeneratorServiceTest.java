package com.reporting.service;

import com.reporting.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SqlGeneratorService Unit Tests")
public class SqlGeneratorServiceTest {

    private final SqlGeneratorService service = new SqlGeneratorService();

    @Test
    @DisplayName("generate with empty resolved metrics should return simple query message")
    public void generate_emptyResolvedMetrics_shouldReturnDummyMessage() {
        ReportConfigDto config = new ReportConfigDto();
        String sql = service.generate(config, Collections.emptyMap());
        assertThat(sql).contains("SELECT 'No data rows' as message");
    }

    @Test
    @DisplayName("generate with valid metrics compiles valid CTE queries")
    public void generate_validConfigAndMetrics_shouldCompileCteSql() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, "m1", null, "normal", 0, 1, Set.of("C1"), "region = 'North'")
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, null
        );

        Map<String, ResolvedMetricDto> resolved = Map.of(
            "R1", new ResolvedMetricDto(
                "m1", 101, "SUM(amount)", "SUM", "NUMERIC", "analytics.fact_sales", "fact_sales", "order_date", 1, List.of("LEFT JOIN analytics.dim_region r ON r.id = region_id")
            )
        );

        // Act
        String sql = service.generate(config, resolved);

        // Assert
        assertThat(sql).contains("WITH");
        assertThat(sql).contains("cte_1 AS (");
        assertThat(sql).contains("SELECT");
        assertThat(sql).contains("SUM(CASE WHEN order_date >= '2026-05-25' AND order_date <= '2026-05-26' AND (region = 'North') THEN amount ELSE 0 END) AS metric_R1_C1");
        assertThat(sql).contains("FROM analytics.fact_sales");
        assertThat(sql).contains("LEFT JOIN analytics.dim_region r ON r.id = region_id");
        assertThat(sql).contains("SELECT\n  cte_1.*\nFROM (SELECT 1 as dummy) d\nLEFT JOIN cte_1 ON TRUE");
    }

    @Test
    @DisplayName("generate with DISTINCT aggregations formats queries correctly")
    public void generate_withDistinctAggregations_shouldHandleDistinctClause() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, "m1", null, "normal", 0, 1, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, null
        );

        Map<String, ResolvedMetricDto> resolved = Map.of(
            "R1", new ResolvedMetricDto(
                "m1", 102, "COUNT(DISTINCT user_id)", "COUNT", "INTEGER", "analytics.fact_sales", "fact_sales", "order_date", 1, null
            )
        );

        // Act
        String sql = service.generate(config, resolved);

        // Assert
        assertThat(sql).contains("COUNT(DISTINCT CASE WHEN order_date >= '2026-05-25' AND order_date <= '2026-05-26' THEN user_id ELSE NULL END) AS metric_R1_C1");
    }

    @Test
    @DisplayName("validateFilterExpr should prevent SQL injection tokens")
    public void validateFilterExpr_sqlInjectionTokens_shouldThrowException() {
        List<ColumnDefDto> columns = List.of(new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1));
        
        // UNION injection
        List<ReportRowDto> rowsUnion = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, "m1", null, "normal", 0, 1, Set.of("C1"), "region = 'North' UNION SELECT * FROM users")
        );
        ReportConfigDto configUnion = new ReportConfigDto("REP1", "Test", columns, rowsUnion, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        Map<String, ResolvedMetricDto> resolved = Map.of("R1", new ResolvedMetricDto("m1", 101, "SUM(amount)", "SUM", "NUMERIC", "a", "f", "d", 1, null));
        
        assertThatThrownBy(() -> service.generate(configUnion, resolved))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or dangerous SQL sequences");

        // Comment block injection (--)
        List<ReportRowDto> rowsComment = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, "m1", null, "normal", 0, 1, Set.of("C1"), "region = 'North' -- comment")
        );
        ReportConfigDto configComment = new ReportConfigDto("REP1", "Test", columns, rowsComment, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        assertThatThrownBy(() -> service.generate(configComment, resolved))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or dangerous SQL sequences");
    }

    @Test
    @DisplayName("validateFilterExpr should check matching parentheses")
    public void validateFilterExpr_unmatchedParentheses_shouldThrowException() {
        List<ColumnDefDto> columns = List.of(new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1));
        Map<String, ResolvedMetricDto> resolved = Map.of("R1", new ResolvedMetricDto("m1", 101, "SUM(amount)", "SUM", "NUMERIC", "a", "f", "d", 1, null));

        // Unmatched open parenthesis
        List<ReportRowDto> rowsOpen = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, "m1", null, "normal", 0, 1, Set.of("C1"), "(region = 'North'")
        );
        ReportConfigDto configOpen = new ReportConfigDto("REP1", "Test", columns, rowsOpen, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        assertThatThrownBy(() -> service.generate(configOpen, resolved))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unmatched parentheses");

        // Unmatched close parenthesis
        List<ReportRowDto> rowsClose = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, "m1", null, "normal", 0, 1, Set.of("C1"), "region = 'North')")
        );
        ReportConfigDto configClose = new ReportConfigDto("REP1", "Test", columns, rowsClose, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        assertThatThrownBy(() -> service.generate(configClose, resolved))
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
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, "m1", null, "normal", 0, 1, Set.of("C1"), null)
        );
        
        String generalFiltersJson = "[" +
            "{\"attribute\":\"amount\",\"operator\":\"=\",\"value\":\"100\"}," +
            "{\"dimTable\":\"dim_rm\",\"attribute\":\"status\",\"operator\":\"is\",\"value\":\"active\"}" +
            "]";
            
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, generalFiltersJson
        );

        Map<String, ResolvedMetricDto> resolved = Map.of(
            "R1", new ResolvedMetricDto(
                "m1", 101, "SUM(amount)", "SUM", "NUMERIC", "analytics.fact_sales", "fact_sales", "order_date", 1, null
            )
        );

        // Act
        String sql = service.generate(config, resolved);

        // Assert
        assertThat(sql).contains("WHERE (amount = '100') AND (dim_rm.status = 'active')");
    }

    @Test
    @DisplayName("generate with general filters maps negation operators with null-safety")
    public void generate_withGeneralFiltersNegation_shouldBeNullSafe() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, "m1", null, "normal", 0, 1, Set.of("C1"), null)
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

        Map<String, ResolvedMetricDto> resolved = Map.of(
            "R1", new ResolvedMetricDto(
                "m1", 101, "SUM(amount)", "SUM", "NUMERIC", "analytics.fact_sales", "fact_sales", "order_date", 1, null
            )
        );

        // Act
        String sql = service.generate(config, resolved);

        // Assert
        assertThat(sql).contains("WHERE ((region <> 'West' OR region IS NULL)) " +
            "AND ((category <> 'Furniture' OR category IS NULL)) " +
            "AND ((name NOT LIKE '%John%' ESCAPE '\\' OR name IS NULL))");
    }

    @Test
    @DisplayName("generate with general filters splits IN operator values by comma")
    public void generate_withGeneralFiltersIn_shouldSplitValues() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, "m1", null, "normal", 0, 1, Set.of("C1"), null)
        );
        
        String generalFiltersJson = "[" +
            "{\"attribute\":\"country\",\"operator\":\"in\",\"value\":\"US, CA, FR\"}" +
            "]";
            
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, generalFiltersJson
        );

        Map<String, ResolvedMetricDto> resolved = Map.of(
            "R1", new ResolvedMetricDto(
                "m1", 101, "SUM(amount)", "SUM", "NUMERIC", "analytics.fact_sales", "fact_sales", "order_date", 1, null
            )
        );

        // Act
        String sql = service.generate(config, resolved);

        // Assert
        assertThat(sql).contains("WHERE (country IN ('US', 'CA', 'FR'))");
    }

    @Test
    @DisplayName("generate with general filters maps special operators and ignores value")
    public void generate_withGeneralFiltersSpecial_shouldIgnoreValue() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, "m1", null, "normal", 0, 1, Set.of("C1"), null)
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

        Map<String, ResolvedMetricDto> resolved = Map.of(
            "R1", new ResolvedMetricDto(
                "m1", 101, "SUM(amount)", "SUM", "NUMERIC", "analytics.fact_sales", "fact_sales", "order_date", 1, null
            )
        );

        // Act
        String sql = service.generate(config, resolved);

        // Assert
        assertThat(sql).contains("WHERE ((region IS NULL OR TRIM(region) = '')) " +
            "AND ((category IS NOT NULL AND TRIM(category) <> '')) " +
            "AND (name IS NULL) " +
            "AND (status IS NOT NULL)");
    }

    @Test
    @DisplayName("generate with general filters handles wildcard search escaping")
    public void generate_withGeneralFiltersWildcards_shouldEscape() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, "m1", null, "normal", 0, 1, Set.of("C1"), null)
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

        Map<String, ResolvedMetricDto> resolved = Map.of(
            "R1", new ResolvedMetricDto(
                "m1", 101, "SUM(amount)", "SUM", "NUMERIC", "analytics.fact_sales", "fact_sales", "order_date", 1, null
            )
        );

        // Act
        String sql = service.generate(config, resolved);

        // Assert
        assertThat(sql).contains("WHERE (code LIKE '%10\\%\\_\\\\%' ESCAPE '\\') " +
            "AND (prefix LIKE 'abc\\%%' ESCAPE '\\') " +
            "AND (suffix LIKE '%\\_xyz' ESCAPE '\\')");
    }

    @Test
    @DisplayName("generate with general filters compiles comparison operators correctly")
    public void generate_withGeneralFiltersComparison_shouldCompile() {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Row 1", Enums.RowType.data, "m1", null, "normal", 0, 1, Set.of("C1"), null)
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

        Map<String, ResolvedMetricDto> resolved = Map.of(
            "R1", new ResolvedMetricDto(
                "m1", 101, "SUM(amount)", "SUM", "NUMERIC", "analytics.fact_sales", "fact_sales", "order_date", 1, null
            )
        );

        // Act
        String sql = service.generate(config, resolved);

        // Assert
        assertThat(sql).contains("WHERE (amount > '100') " +
            "AND (amount >= '150') " +
            "AND (quantity < '10') " +
            "AND (quantity <= '20')");
    }
}

