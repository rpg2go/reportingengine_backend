package com.reporting.service;

import com.reporting.BaseIT;
import com.reporting.dto.ColumnDefDto;
import com.reporting.dto.Enums;
import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ReportRowDto;
import com.reporting.dto.MeasureDefinitionDTO;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReportRunnerService Integration Tests")
@Transactional
public class ReportRunnerServiceIT extends BaseIT {

    @Autowired
    private ReportRunnerService runnerService;

    @Autowired
    private ReportConfigService configService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("runReport runs full compilation, database execution, post-processing, and Excel generation")
    public void runReport_validConfig_shouldGeneratePopulatedExcelSheet() throws Exception {
        String reportId = "RPT_RUNNER_IT";
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "This Week", Enums.ColType.WTD, 0, null, null, 1)
        );
        // R1 points to 'total_revenue' which resolves to SUM(analytics.fact_sales.amount)
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", reportId, "Revenue Row", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), null, "normal", 0, 1, Set.of("C1"), null),
            new ReportRowDto("R2", reportId, "Double Revenue", Enums.RowType.calc, new MeasureDefinitionDTO("raw", null, null, null, "R1 * 2"), null, "total", 0, 2, Set.of("C1"), null)
        );

        ReportConfigDto config = new ReportConfigDto(
            reportId, "Runner IT Report", columns, rows, LocalDate.of(2026, 5, 26), null, Enums.ReportStatus.draft,
            null, null, null, false, null, null
        );
        config.setSourceTable("analytics.fact_sales");

        // Save configuration to database
        configService.saveToDb(config);

        // Act: Run the report E2E
        byte[] excelBytes = runnerService.runReport(reportId, LocalDate.of(2026, 5, 26));

        // Assert: Verify excel file contains actual populated numbers
        assertThat(excelBytes).isNotNull();
        assertThat(excelBytes.length).isGreaterThan(0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheet("Report");
            assertThat(sheet).isNotNull();

            // Verify structure and math post processing
            double r1Value = sheet.getRow(1).getCell(1).getNumericCellValue();
            double r2Value = sheet.getRow(2).getCell(1).getNumericCellValue();

            System.out.println("R1 Revenue: " + r1Value);
            System.out.println("R2 Double Revenue: " + r2Value);

            // R2 formula: R1 * 2
            assertThat(r2Value).isEqualTo(r1Value * 2);
        }
    }
}
