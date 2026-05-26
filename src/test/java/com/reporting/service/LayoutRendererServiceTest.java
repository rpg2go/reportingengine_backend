package com.reporting.service;

import com.reporting.dto.ColumnDefDto;
import com.reporting.dto.Enums;
import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ReportRowDto;
import org.apache.poi.ss.usermodel.Cell;
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

@DisplayName("LayoutRendererService Unit Tests")
public class LayoutRendererServiceTest {

    private final LayoutRendererService renderer = new LayoutRendererService();

    @Test
    @DisplayName("render generates valid Excel workbook with correct headers and cell values")
    public void render_validReportData_shouldGenerateCorrectExcel() throws IOException {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "Col 1 Label", Enums.ColType.WEEK, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "REP1", "Revenue", Enums.RowType.data, "m1", null, "section", 0, 1, Set.of("C1"), null),
            new ReportRowDto("R2", "REP1", "Cost", Enums.RowType.data, "m2", null, "normal", 1, 2, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto(
            "REP1", "Report A", columns, rows, null, 1, null, "a", "w", null, null, false, null, null
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
            Row headerRow = sheet.getRow(0);
            assertThat(headerRow).isNotNull();
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("Report Line");
            assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("Col 1 Label");

            // Row 1 (R1 - Revenue)
            Row r1Row = sheet.getRow(1);
            assertThat(r1Row).isNotNull();
            assertThat(r1Row.getCell(0).getStringCellValue()).isEqualTo("Revenue");
            assertThat(r1Row.getCell(1).getNumericCellValue()).isEqualTo(5000.50);

            // Row 2 (R2 - Cost, Indent Level 1)
            Row r2Row = sheet.getRow(2);
            assertThat(r2Row).isNotNull();
            // Two spaces indent
            assertThat(r2Row.getCell(0).getStringCellValue()).isEqualTo("  Cost");
            assertThat(r2Row.getCell(1).getNumericCellValue()).isEqualTo(2000.00);
        }
    }
}
