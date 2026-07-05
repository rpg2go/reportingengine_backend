package com.reporting.service;
import com.reporting.dto.HierarchicalColumnDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Service for exporting hierarchical column structures into styled Excel sheets.
 * Groups L1 parents and L2 children, calculates column span offsets,
 * merges parent ranges, and formats cell regions with professional styling.
 *
 * @since 1.1.0
 */
@Service
public class ExcelExporterService {

    /**
     * Generates a styled Excel workbook byte array from a list of hierarchical columns.
     *
     * @param columns list of hierarchical column definitions
     * @return byte array representing the XSSFWorkbook
     * @throws IOException if workbook writing fails
     */
    public byte[] exportColumns(List<HierarchicalColumnDto> columns) throws IOException {
        if (columns == null) {
            columns = Collections.emptyList();
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Hierarchical Columns");
            sheet.setDisplayGridlines(true);

            // Create styles for visual aesthetics
            CellStyle parentStyle = createHeaderStyle(workbook, IndexedColors.GREY_25_PERCENT.getIndex());
            CellStyle childStyle = createHeaderStyle(workbook, IndexedColors.WHITE.getIndex());

            // Rows setup (Row 3 / Index 3 is Parent L1, Row 4 / Index 4 is Child L2)
            Row parentRow = sheet.getRow(3);
            if (parentRow == null) {
                parentRow = sheet.createRow(3);
            }
            Row childRow = sheet.getRow(4);
            if (childRow == null) {
                childRow = sheet.createRow(4);
            }

            // Separate L1 parent columns and index L2 child columns by their parentId
            List<HierarchicalColumnDto> parents = new ArrayList<>();
            Map<String, List<HierarchicalColumnDto>> childMap = new LinkedHashMap<>();

            for (HierarchicalColumnDto col : columns) {
                if ("L2".equalsIgnoreCase(col.getTierLevel())) {
                    String pId = col.getParentId();
                    if (pId != null && !pId.isBlank()) {
                        childMap.computeIfAbsent(pId, k -> new ArrayList<>()).add(col);
                    }
                } else {
                    parents.add(col);
                }
            }

            int colCursor = 0;

            for (HierarchicalColumnDto parent : parents) {
                List<HierarchicalColumnDto> children = childMap.getOrDefault(parent.getColId(), Collections.emptyList());

                if (children.isEmpty()) {
                    // Standalone L1 column: Merge vertically Row 3 to Row 4 (indices 3 to 4)
                    Cell parentCell = parentRow.createCell(colCursor);
                    parentCell.setCellValue(parent.getLabel());

                    CellRangeAddress region = new CellRangeAddress(3, 4, colCursor, colCursor);
                    sheet.addMergedRegion(region);
                    applyBordersAndStyle(region, sheet, parentStyle);

                    sheet.setColumnWidth(colCursor, 4500); // 15-18 characters wide
                    colCursor++;
                } else {
                    // Parent with children: Merge L1 parent horizontally
                    int startCol = colCursor;
                    int endCol = colCursor + children.size() - 1;

                    Cell parentCell = parentRow.createCell(colCursor);
                    parentCell.setCellValue(parent.getLabel());

                    CellRangeAddress region = new CellRangeAddress(3, 3, startCol, endCol);
                    sheet.addMergedRegion(region);
                    applyBordersAndStyle(region, sheet, parentStyle);

                    // Write children on childRow
                    for (int i = 0; i < children.size(); i++) {
                        int childCol = startCol + i;
                        HierarchicalColumnDto child = children.get(i);

                        Cell childCell = childRow.createCell(childCol);
                        childCell.setCellValue(child.getLabel());
                        childCell.setCellStyle(childStyle);

                        // Apply thin borders to single child cells
                        CellRangeAddress childRegion = new CellRangeAddress(4, 4, childCol, childCol);
                        applyBordersAndStyle(childRegion, sheet, childStyle);

                        sheet.setColumnWidth(childCol, 4000);
                    }
                    colCursor += children.size();
                }
            }

            // Write and return byte array
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    /**
     * Creates a standard centered, bold header style with borders and a background pattern.
     */
    private CellStyle createHeaderStyle(Workbook workbook, short backgroundColor) {
        CellStyle style = workbook.createCellStyle();
        
        // Font
        Font font = workbook.createFont();
        font.setFontName("Segoe UI");
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);

        // Alignment
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        // Fill
        if (backgroundColor != IndexedColors.WHITE.getIndex()) {
            style.setFillForegroundColor(backgroundColor);
            style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        // Borders
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    /**
     * Applies styling and thin borders around the entire merged cell region.
     */
    private void applyBordersAndStyle(CellRangeAddress region, Sheet sheet, CellStyle style) {
        for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                row = sheet.createRow(r);
            }
            for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                Cell cell = row.getCell(c);
                if (cell == null) {
                    cell = row.createCell(c);
                }
                cell.setCellStyle(style);
            }
        }

        // Set borders cleanly on the edges of the merged range
        RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
    }
}
