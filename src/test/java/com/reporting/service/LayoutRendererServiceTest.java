package com.reporting.service;

import com.reporting.dto.ColumnDefDto;
import com.reporting.dto.Enums;
import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ReportRowDto;
import com.reporting.dto.MeasureDefinitionDTO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("deprecation")
@DisplayName("LayoutRendererService Unit Tests")
public class LayoutRendererServiceTest {

    private final LayoutRendererService renderer = new LayoutRendererService();

    @Test
    @DisplayName("render generates valid Excel workbook with correct headers and cell values")
    public void render_validReportData_shouldGenerateCorrectExcel() throws IOException {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1 Label", Enums.ColType.WTD, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Revenue", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "m1"), null, "section", 0, 1, Set.of("C1"), null),
            new ReportRowDto("R2", "REP1", "Cost", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "m2"), null, "normal", 1, 2, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Report A", columns, rows, null, 1, null, "a", null, null, null, false, null, null
        );

        Map<String, Map<String, Double>> data = Map.of(
            "R1", Map.of("C1", 5000.50),
            "R2", Map.of("C1", 2000.00)
        );

        // Act
        byte[] excelBytes = renderer.render(config, data);

        // Assert
        assertThat(excelBytes).isNotNull();
        assertThat(excelBytes.length).isGreaterThan(0);

        // Parse generated Excel to verify structure
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheet("Report");
            assertThat(sheet).isNotNull();

            // Header Row (Row 0)
            Row headerRow0 = sheet.getRow(0);
            assertThat(headerRow0).isNotNull();
            assertThat(headerRow0.getCell(0).getStringCellValue()).isEqualTo("Report Line");
            assertThat(headerRow0.getCell(1).getStringCellValue()).isEqualTo("Col 1 Label");

            // Row 2 (R1 - Revenue)
            Row r1Row = sheet.getRow(2);
            assertThat(r1Row).isNotNull();
            assertThat(r1Row.getCell(0).getStringCellValue()).isEqualTo("Revenue");
            assertThat(r1Row.getCell(1).getNumericCellValue()).isEqualTo(5000.50);

            // Row 3 (R2 - Cost, Indent Level 1)
            Row r2Row = sheet.getRow(3);
            assertThat(r2Row).isNotNull();
            // Two spaces indent
            assertThat(r2Row.getCell(0).getStringCellValue()).isEqualTo("  Cost");
            assertThat(r2Row.getCell(1).getNumericCellValue()).isEqualTo(2000.00);
        }
    }

    @Test
    @DisplayName("render expands ROLLING columns and retrieves values correctly")
    public void render_rollingColumns_shouldExpandAndRenderCorrectly() throws IOException {
        // Arrange
        java.time.LocalDate refDate = java.time.LocalDate.of(2026, 6, 9);
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C7", "3-Mo Rolling", Enums.ColType.ROLLING, 0, 3, "MONTH", "", 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Revenue", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "m1"), null, "section", 0, 1, Set.of("C7"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Report A", columns, rows, refDate, 1, null, "a", null, null, null, false, null, null
        );

        // Data matches sub-column keys or fallback parent key
        Map<String, Map<String, Double>> data = Map.of(
            "R1", Map.of(
                "C7_1", 100.0,
                "C7_2", 200.0
                // C7_3 is missing, should fall back to parent C7 if present in query results, or default to 0.0
            )
        );

        // Act
        byte[] excelBytes = renderer.render(config, data);

        // Assert
        assertThat(excelBytes).isNotNull();
        assertThat(excelBytes.length).isGreaterThan(0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheet("Report");
            assertThat(sheet).isNotNull();

            // Header Row (Row 0)
            Row headerRow0 = sheet.getRow(0);
            assertThat(headerRow0).isNotNull();
            assertThat(headerRow0.getCell(0).getStringCellValue()).isEqualTo("Report Line");
            assertThat(headerRow0.getCell(1).getStringCellValue()).isEqualTo("3-Mo Rolling");

            // Header Row (Row 1)
            Row headerRow1 = sheet.getRow(1);
            assertThat(headerRow1).isNotNull();
            // C7_3: March 2026, C7_2: April 2026, C7_1: May 2026
            assertThat(headerRow1.getCell(1).getStringCellValue()).isEqualTo("March 2026");
            assertThat(headerRow1.getCell(2).getStringCellValue()).isEqualTo("April 2026");
            assertThat(headerRow1.getCell(3).getStringCellValue()).isEqualTo("May 2026");

            // Row 2 (R1 - Revenue)
            Row r1Row = sheet.getRow(2);
            assertThat(r1Row).isNotNull();
            assertThat(r1Row.getCell(0).getStringCellValue()).isEqualTo("Revenue");
            assertThat(r1Row.getCell(1).getNumericCellValue()).isEqualTo(0.0);
            assertThat(r1Row.getCell(2).getNumericCellValue()).isEqualTo(200.0);
            assertThat(r1Row.getCell(3).getNumericCellValue()).isEqualTo(100.0);
        }
    }

    @Test
    @DisplayName("render includes sorted granularity sub-rows underneath their parent rows in separate columns")
    public void render_withGranularitySubRows_shouldRenderThemSortedAndIndented() throws IOException {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1 Label", Enums.ColType.WTD, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Revenue", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "m1"), null, "normal", 0, 1, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Report A", columns, rows, null, 1, null, "a", "country,region", null, null, false, null, null
        );

        Map<String, Map<String, Double>> data = Map.of(
            "R1", Map.of("C1", 1000.00),
            "R1|USA|East", Map.of("C1", 600.00),
            "R1|Canada|West", Map.of("C1", 400.00)
        );

        // Act
        byte[] excelBytes = renderer.render(config, data);

        // Assert
        assertThat(excelBytes).isNotNull();
        assertThat(excelBytes.length).isGreaterThan(0);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheet("Report");
            assertThat(sheet).isNotNull();

            // Total rows rendered: 2 header + 1 parent + 2 sub-rows = 5 rows
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(5);

            // Row 0 (Header)
            Row headerRow0 = sheet.getRow(0);
            assertThat(headerRow0.getCell(0).getStringCellValue()).isEqualTo("Report Line");
            assertThat(headerRow0.getCell(1).getStringCellValue()).isEqualTo("country");
            assertThat(headerRow0.getCell(2).getStringCellValue()).isEqualTo("region");
            assertThat(headerRow0.getCell(3).getStringCellValue()).isEqualTo("Col 1 Label");

            // Row 2 (R1 - Parent row)
            Row r1Row = sheet.getRow(2);
            assertThat(r1Row.getCell(0).getStringCellValue()).isEqualTo("Revenue");
            assertThat(r1Row.getCell(1).getStringCellValue()).isEqualTo("-");
            assertThat(r1Row.getCell(2).getStringCellValue()).isEqualTo("-");
            assertThat(r1Row.getCell(3).getNumericCellValue()).isEqualTo(1000.00);

            // Row 3 (Canada|West - sorted alphabetically before USA|East)
            Row subRow1 = sheet.getRow(3);
            assertThat(subRow1.getCell(0).getStringCellValue()).isEqualTo("  ├ ");
            assertThat(subRow1.getCell(1).getStringCellValue()).isEqualTo("Canada");
            assertThat(subRow1.getCell(2).getStringCellValue()).isEqualTo("West");
            assertThat(subRow1.getCell(3).getNumericCellValue()).isEqualTo(400.00);

            // Row 4 (USA|East - last sub-row, gets '└' connector)
            Row subRow2 = sheet.getRow(4);
            assertThat(subRow2.getCell(0).getStringCellValue()).isEqualTo("  └ ");
            assertThat(subRow2.getCell(1).getStringCellValue()).isEqualTo("USA");
            assertThat(subRow2.getCell(2).getStringCellValue()).isEqualTo("East");
            assertThat(subRow2.getCell(3).getNumericCellValue()).isEqualTo(600.00);
        }
    }
}
