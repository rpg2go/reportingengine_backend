package com.reporting.service;

import com.reporting.BaseIT;
import com.reporting.dto.ColumnDefDto;
import com.reporting.dto.Enums;
import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ReportRowDto;
import com.reporting.dto.MeasureDefinitionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportConfigService Integration Tests")
@Transactional
public class ReportConfigServiceIT extends BaseIT {

    @Autowired
    private ReportConfigService configService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("saveToDb and loadFromDb should persist and reload full config matrix correctly")
    public void saveAndLoadConfig_shouldBeConsistent() {
        // Arrange
        String reportId = "RPT_IT_TEST";
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Revenue Week", Enums.ColType.WTD, 0, null, null, 1),
            new ColumnDefDto("C2", "Calc Growth", Enums.ColType.CALC, 0, null, "C1 * 1.1", 2)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", reportId, "Gross Sales", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), null, "normal", 0, 1, Set.of("C1", "C2"), "region_id = 1"),
            new ReportRowDto("R2", reportId, "Spacer Row", Enums.RowType.blank, new MeasureDefinitionDTO("raw", null, null, null, ""), null, "normal", 0, 2, Set.of(), null)
        );

        ReportConfigDto config = new ReportConfigDto(
            reportId, "Integration Test Report", columns, rows, LocalDate.of(2026, 5, 26), 1, Enums.ReportStatus.draft,
            "analytics.fact_sales", "weekly", null, null, false, null, null
        );

        // Act
        configService.saveToDb(config);
        ReportConfigDto loaded = configService.loadFromDb(reportId, LocalDate.of(2026, 5, 26));

        // Assert
        assertThat(loaded).isNotNull();
        assertThat(loaded.getReportId()).isEqualTo(reportId);
        assertThat(loaded.getName()).isEqualTo("Integration Test Report");
        
        // Assert Columns
        assertThat(loaded.getColumns()).hasSize(2);
        assertThat(loaded.getColumns().get(0).colId()).isEqualTo("C1");
        assertThat(loaded.getColumns().get(1).formulaExpr()).isEqualTo("C1 * 1.1");

        // Assert Rows
        assertThat(loaded.getRows()).hasSize(2);
        assertThat(loaded.getRows().get(0).rowId()).isEqualTo("R1");
        assertThat(loaded.getRows().get(0).source()).isNotNull();
        assertThat(loaded.getRows().get(0).source().getRawSql()).isEqualTo("SUM(amount)");
        assertThat(loaded.getRows().get(0).filterExpr()).isEqualTo("region_id = 1");
    }

    @Test
    @DisplayName("saveToDb cleans up prior child relationships (cascade deletion) on update")
    public void saveConfig_shouldPerformCascadeDeletionsOnUpdate() {
        // Arrange first save
        String reportId = "RPT_IT_CASCADE";
        List<ColumnDefDto> cols1 = List.of(new ColumnDefDto("C1", "C1", Enums.ColType.WTD, 0, null, null, 1));
        List<ReportRowDto> rows1 = List.of(new ReportRowDto("R1", reportId, "R1", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "SUM(a)"), null, "normal", 0, 1, Set.of("C1"), null));
        ReportConfigDto config1 = new ReportConfigDto(reportId, "First save", cols1, rows1, LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        configService.saveToDb(config1);

        // Verify rows exist in DB
        Integer rowCountBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reporting.rpt_row WHERE report_id = ?", Integer.class, reportId);
        assertThat(rowCountBefore).isEqualTo(1);

        // Act: Save updated configuration without any rows
        ReportConfigDto config2 = new ReportConfigDto(reportId, "Second save", cols1, List.of(), LocalDate.now(), 1, null, "a", "w", null, null, false, null, null);
        configService.saveToDb(config2);

        // Assert: Rows and mapping tables are empty for this report (deleted cascade)
        Integer rowCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reporting.rpt_row WHERE report_id = ?", Integer.class, reportId);
        assertThat(rowCountAfter).isEqualTo(0);

        Integer mapCountAfter = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reporting.rpt_row_column_map WHERE report_id = ?", Integer.class, reportId);
        assertThat(mapCountAfter).isEqualTo(0);
    }
}
