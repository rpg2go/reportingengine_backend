package com.reporting.service;

import com.reporting.domain.*;
import com.reporting.repository.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExcelParserService Unit Tests")
public class ExcelParserServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private ColumnDefRepository columnDefRepository;
    @Mock private ReportRowRepository reportRowRepository;
    @Mock private RowMetricRepository rowMetricRepository;
    @Mock private RowFormulaRepository rowFormulaRepository;
    @Mock private RowColumnMapRepository rowColumnMapRepository;
    @Mock private StyleRepository styleRepository;
    @Mock private ImportRunRepository importRunRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ExcelParserService service;

    private byte[] createTestExcelBytes(boolean hasRequiredSheet, boolean hasRequiredHeaders) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            if (!hasRequiredSheet) {
                wb.createSheet("wrong_sheet_name");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                wb.write(bos);
                return bos.toByteArray();
            }

            Sheet sheet = wb.createSheet("REPORT_DEFINITION");

            // Section A: Header (Row 0 / index 0)
            Row aHeader = sheet.createRow(0);
            if (hasRequiredHeaders) {
                aHeader.createCell(0).setCellValue("col_id");
                aHeader.createCell(1).setCellValue("label");
                aHeader.createCell(2).setCellValue("type");
                aHeader.createCell(3).setCellValue("offset");
                aHeader.createCell(4).setCellValue("rolling_n");
                aHeader.createCell(5).setCellValue("formula");
            } else {
                aHeader.createCell(0).setCellValue("wrong_a_header");
            }

            // Section A: Data Row (Row 1 / index 1)
            Row aData = sheet.createRow(1);
            aData.createCell(0).setCellValue("C1");
            aData.createCell(1).setCellValue("Col 1");
            aData.createCell(2).setCellValue("WEEK");
            aData.createCell(3).setCellValue(0);

            // Spacer rows...
            for (int i = 2; i < 9; i++) {
                sheet.createRow(i);
            }

            // Section B: Header (Row 9 / index 9)
            Row bHeader = sheet.createRow(9);
            bHeader.createCell(0).setCellValue("report_id");
            bHeader.createCell(1).setCellValue("row_id");
            bHeader.createCell(2).setCellValue("label");
            bHeader.createCell(3).setCellValue("type");
            bHeader.createCell(4).setCellValue("source");
            bHeader.createCell(5).setCellValue("C1"); // active flag mapping

            // Section B: Data Row (Row 10 / index 10)
            Row bData = sheet.createRow(10);
            bData.createCell(0).setCellValue("RPT_001");
            bData.createCell(1).setCellValue("R1");
            bData.createCell(2).setCellValue("Revenue");
            bData.createCell(3).setCellValue("data");
            bData.createCell(4).setCellValue("sales_total");
            bData.createCell(5).setCellValue("Y");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    @DisplayName("importTemplate should throw exception if sheet is missing")
    public void importTemplate_missingSheet_shouldThrowException() throws IOException {
        byte[] bytes = createTestExcelBytes(false, true);
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);

        when(importRunRepository.save(any(ImportRun.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.importTemplate(bis, "test.xlsx"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Sheet REPORT_DEFINITION not found");
    }

    @Test
    @DisplayName("importTemplate should throw exception if headers are missing")
    public void importTemplate_missingHeaders_shouldThrowException() throws IOException {
        byte[] bytes = createTestExcelBytes(true, false);
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);

        when(importRunRepository.save(any(ImportRun.class))).thenAnswer(i -> i.getArgument(0));

        assertThatThrownBy(() -> service.importTemplate(bis, "test.xlsx"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing required column");
    }

    @Test
    @DisplayName("importTemplate successfully parses valid workbook and persists config")
    public void importTemplate_validExcel_shouldPersistConfigurations() throws Exception {
        // Arrange
        byte[] bytes = createTestExcelBytes(true, true);
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);

        ImportRun mockRun = ImportRun.builder().runId(1).build();
        when(importRunRepository.save(any(ImportRun.class))).thenReturn(mockRun);
        when(styleRepository.findAll()).thenReturn(List.of(Style.builder().styleId(1).name("normal").build()));
        when(reportRepository.findById("RPT_001")).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenAnswer(i -> i.getArgument(0));
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq("sales_total")))
            .thenReturn(List.of(99)); // mock measure lookup ID = 99

        // Act
        service.importTemplate(bis, "test.xlsx");

        // Assert
        verify(importRunRepository, times(2)).save(any(ImportRun.class)); // pending + success status saves
        verify(reportRepository).save(argThat(report -> report.getReportId().equals("RPT_001")));
        verify(columnDefRepository).save(argThat(col -> col.getColId().equals("C1")));
        verify(reportRowRepository).save(argThat(row -> row.getRowId().equals("R1")));
        verify(rowMetricRepository).save(argThat(rm -> rm.getMeasureId() == 99));
        verify(rowColumnMapRepository).save(argThat(rcm -> rcm.getColId().equals("C1") && rcm.getIsEnabled()));
    }
}
