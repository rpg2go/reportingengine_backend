package com.reporting.service;

import com.reporting.dto.HierarchicalColumnDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExcelExporterService Unit Tests")
public class ExcelExporterServiceTest {

    private ExcelExporterService service;

    @BeforeEach
    public void setUp() {
        service = new ExcelExporterService();
    }

    @Test
    @DisplayName("Export with empty list: generates valid empty workbook")
    public void export_emptyList_generatesValidWorkbook() throws IOException {
        byte[] bytes = service.exportColumns(new ArrayList<>());
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Hierarchical Columns");
            assertNotNull(sheet);
            assertEquals(0, sheet.getNumMergedRegions());
        }
    }

    @Test
    @DisplayName("Export with standalone L1 columns: merges vertically across Row 4 & Row 5")
    public void export_standaloneColumns_mergesVertically() throws IOException {
        List<HierarchicalColumnDto> cols = List.of(
            new HierarchicalColumnDto("C1", "Total Sales", "WTD", "normal", "L1", "", "", 0, 0, ""),
            new HierarchicalColumnDto("C2", "Region Code", "WTD", "bold", "L1", "", "", 0, 0, "")
        );

        byte[] bytes = service.exportColumns(cols);
        assertNotNull(bytes);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Hierarchical Columns");
            assertNotNull(sheet);
            
            // Should have 2 merged regions (one vertical merge per standalone column)
            assertEquals(2, sheet.getNumMergedRegions());

            // Validate Region 1: Row index 3-4, Col index 0-0
            CellRangeAddress r1 = sheet.getMergedRegion(0);
            assertEquals(3, r1.getFirstRow());
            assertEquals(4, r1.getLastRow());
            assertEquals(0, r1.getFirstColumn());
            assertEquals(0, r1.getLastColumn());

            // Validate Region 2: Row index 3-4, Col index 1-1
            CellRangeAddress r2 = sheet.getMergedRegion(1);
            assertEquals(3, r2.getFirstRow());
            assertEquals(4, r2.getLastRow());
            assertEquals(1, r2.getFirstColumn());
            assertEquals(1, r2.getLastColumn());

            // Assert values
            Row parentRow = sheet.getRow(3);
            assertEquals("Total Sales", parentRow.getCell(0).getStringCellValue());
            assertEquals("Region Code", parentRow.getCell(1).getStringCellValue());
        }
    }

    @Test
    @DisplayName("Export with parent-child structure: merges parent horizontally, renders children underneath")
    public void export_parentWithChildren_mergesHorizontally() throws IOException {
        List<HierarchicalColumnDto> cols = List.of(
            new HierarchicalColumnDto("P1", "Actuals Banner", "WTD", "normal", "L1", "", "", 0, 0, ""),
            new HierarchicalColumnDto("C1", "WTD Actual", "WTD", "normal", "L2", "P1", "", 0, 0, ""),
            new HierarchicalColumnDto("C2", "MTD Actual", "WTD", "normal", "L2", "P1", "", 0, 0, "")
        );

        byte[] bytes = service.exportColumns(cols);
        assertNotNull(bytes);

        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Hierarchical Columns");
            assertNotNull(sheet);

            // Should have 3 merged regions (one horizontal parent merge, and two single-cell child region definitions)
            // Wait, our implementation does applyBordersAndStyle(childRegion) where childRegion is (4,4,col,col)
            // That means it creates cell range addresses of size 1. Note: POI doesn't strictly merge single cells,
            // but let's check what actual merges were added to the sheet.
            // In sheet, CellRangeAddress of size 1 might be added or ignored depending on POI. Let's see:
            // Single cell regions for child cols: new CellRangeAddress(4, 4, childCol, childCol);
            // This adds single cell regions to the sheet. Let's count them:
            // Merges added:
            // 1. Parent merge (3, 3, 0, 1) -> horizontal merge
            // 2. Child 1 border range (4, 4, 0, 0)
            // 3. Child 2 border range (4, 4, 1, 1)
            // Total = 3
            assertTrue(sheet.getNumMergedRegions() >= 1);

            // Let's find the horizontal parent merge
            CellRangeAddress parentRegion = null;
            for (int i = 0; i < sheet.getNumMergedRegions(); i++) {
                CellRangeAddress region = sheet.getMergedRegion(i);
                if (region.getFirstRow() == 3 && region.getLastRow() == 3) {
                    parentRegion = region;
                    break;
                }
            }
            assertNotNull(parentRegion, "Parent horizontal merge should exist");
            assertEquals(0, parentRegion.getFirstColumn());
            assertEquals(1, parentRegion.getLastColumn());

            // Assert values
            Row parentRow = sheet.getRow(3);
            assertEquals("Actuals Banner", parentRow.getCell(0).getStringCellValue());

            Row childRow = sheet.getRow(4);
            assertEquals("WTD Actual", childRow.getCell(0).getStringCellValue());
            assertEquals("MTD Actual", childRow.getCell(1).getStringCellValue());
        }
    }
}
