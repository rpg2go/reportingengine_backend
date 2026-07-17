package com.reporting.service;

import com.reporting.dto.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;

@Service
public class LayoutRendererService {

    public static class ExpandedColumn {
        private final String colId;
        private final String label;
        private final boolean isExpandedSubCol;
        private final String parentColId;

        public ExpandedColumn(String colId, String label, boolean isExpandedSubCol, String parentColId) {
            this.colId = colId;
            this.label = label;
            this.isExpandedSubCol = isExpandedSubCol;
            this.parentColId = parentColId;
        }

        public String getColId() { return colId; }
        public String getLabel() { return label; }
        public boolean isExpandedSubCol() { return isExpandedSubCol; }
        public String getParentColId() { return parentColId; }
    }

    private static String formatShortDay(LocalDate date) {
        return date.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM", java.util.Locale.US));
    }

    private static String formatMonthYear(LocalDate date) {
        return date.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale.US));
    }

    private static String formatWeekRange(LocalDate start, LocalDate end) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.US);
        return start.format(fmt) + " - " + end.format(fmt);
    }

    public LocalDate getAdjustedRefDate(LocalDate refDate, ColumnDefDto col, List<ColumnDefDto> allCols) {
        String periodType = getEffectivePeriodType(col, allCols);
        if (periodType != null && "PREVIOUS_YEAR".equalsIgnoreCase(periodType.trim())) {
            return refDate.minusYears(1);
        }
        return refDate;
    }

    private String getEffectivePeriodType(ColumnDefDto col, List<ColumnDefDto> allCols) {
        if (col.periodType() != null && !col.periodType().isBlank()) {
            return col.periodType();
        }
        if ("L2".equalsIgnoreCase(col.tierLevel()) && col.parentId() != null) {
            String parentKey = col.parentId().trim().toUpperCase();
            for (ColumnDefDto parent : allCols) {
                if (parentKey.equals(parent.colId().trim().toUpperCase())) {
                    if (parent.periodType() != null && !parent.periodType().isBlank()) {
                        return parent.periodType();
                    }
                }
            }
        }
        return "";
    }

    public List<ExpandedColumn> expandRollingColumn(ColumnDefDto col, LocalDate colRefDate) {
        List<ExpandedColumn> subCols = new ArrayList<>();
        int rollingN = col.rollingN() != null ? col.rollingN() : 1;
        String grain = col.effectiveRollingGrain();

        for (int i = 1; i <= rollingN; i++) {
            String subColId = col.colId() + "_" + i;
            String label = "";

            switch (grain) {
                case "DAY": {
                    LocalDate target = colRefDate.minusDays(i);
                    label = formatShortDay(target);
                    break;
                }
                case "MONTH": {
                    LocalDate target = colRefDate.minusMonths(i);
                    label = formatMonthYear(target);
                    break;
                }
                case "YEAR": {
                    LocalDate target = colRefDate.minusYears(i);
                    label = String.valueOf(target.getYear());
                    break;
                }
                case "WEEK":
                default: {
                    LocalDate refMonday = colRefDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                    LocalDate targetMonday = refMonday.minusWeeks(i);
                    LocalDate targetSunday = targetMonday.plusDays(6);
                    label = formatWeekRange(targetMonday, targetSunday);
                    break;
                }
            }

            subCols.add(new ExpandedColumn(
                subColId,
                label,
                true,
                col.colId()
            ));
        }
        return subCols;
    }

    public List<ExpandedColumn> getExpandedColumns(ReportConfigDto config) {
        List<ColumnDefDto> cols = new ArrayList<>(config.getColumns());
        cols.sort(Comparator.comparingInt(ColumnDefDto::displayOrder));

        // Build L2 children map and find L1 columns
        Map<String, List<ColumnDefDto>> l2ChildrenMap = new HashMap<>();
        List<ColumnDefDto> l1Cols = new ArrayList<>();
        for (ColumnDefDto col : cols) {
            if ("L2".equalsIgnoreCase(col.tierLevel()) && col.parentId() != null && !col.parentId().isBlank()) {
                String key = col.parentId().trim().toUpperCase();
                l2ChildrenMap.computeIfAbsent(key, k -> new ArrayList<>()).add(col);
            } else {
                l1Cols.add(col);
            }
        }

        List<ExpandedColumn> leaves = new ArrayList<>();
        LocalDate refDate = config.getReferenceDate() != null ? config.getReferenceDate() : LocalDate.now();

        for (ColumnDefDto col : l1Cols) {
            LocalDate colRefDate = getAdjustedRefDate(refDate, col, config.getColumns());

            if (col.colType() == Enums.ColType.ROLLING) {
                leaves.addAll(expandRollingColumn(col, colRefDate));
            } else if (col.colType() == Enums.ColType.HEADER) {
                List<ColumnDefDto> children = l2ChildrenMap.getOrDefault(col.colId().trim().toUpperCase(), Collections.emptyList());
                if (!children.isEmpty()) {
                    for (ColumnDefDto child : children) {
                        LocalDate childRefDate = getAdjustedRefDate(refDate, child, config.getColumns());
                        if (child.colType() == Enums.ColType.ROLLING) {
                            leaves.addAll(expandRollingColumn(child, childRefDate));
                        } else {
                            leaves.add(new ExpandedColumn(child.colId(), child.label(), false, null));
                        }
                    }
                } else {
                    // HEADER with no children: show itself as placeholder
                    leaves.add(new ExpandedColumn(col.colId(), col.label(), false, null));
                }
            } else {
                List<ColumnDefDto> children = l2ChildrenMap.getOrDefault(col.colId().trim().toUpperCase(), Collections.emptyList());
                if (!children.isEmpty()) {
                    for (ColumnDefDto child : children) {
                        LocalDate childRefDate = getAdjustedRefDate(refDate, child, config.getColumns());
                        if (child.colType() == Enums.ColType.ROLLING) {
                            leaves.addAll(expandRollingColumn(child, childRefDate));
                        } else {
                            leaves.add(new ExpandedColumn(child.colId(), child.label(), false, null));
                        }
                    }
                } else {
                    leaves.add(new ExpandedColumn(col.colId(), col.label(), false, null));
                }
            }
        }

        // Add orphaned L2s
        for (ColumnDefDto col : cols) {
            if ("L2".equalsIgnoreCase(col.tierLevel())) {
                boolean hasValidParent = false;
                if (col.parentId() != null && !col.parentId().isBlank()) {
                    String pKey = col.parentId().trim().toUpperCase();
                    for (ColumnDefDto p : l1Cols) {
                        if (pKey.equals(p.colId().trim().toUpperCase())) {
                            hasValidParent = true;
                            break;
                        }
                    }
                }
                if (!hasValidParent) {
                    LocalDate colRefDate = getAdjustedRefDate(refDate, col, config.getColumns());
                    if (col.colType() == Enums.ColType.ROLLING) {
                        leaves.addAll(expandRollingColumn(col, colRefDate));
                    } else {
                        leaves.add(new ExpandedColumn(col.colId(), col.label(), false, null));
                    }
                }
            }
        }

        return leaves;
    }

    public byte[] render(ReportConfigDto config, Map<String, Map<String, Double>> data) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            Sheet sheet = workbook.createSheet("Report");
            // Enable column tracking for auto-sizing in streaming sheet
            if (sheet instanceof SXSSFSheet) {
                ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();
            }

            XSSFWorkbook xssfWorkbook = workbook.getXSSFWorkbook();

            // Setup styles
            CellStyle headerStyle = createHeaderStyle(xssfWorkbook);
            CellStyle sectionStyle = createSectionStyle(xssfWorkbook);
            CellStyle totalStyle = createTotalStyle(xssfWorkbook);
            CellStyle normalStyle = createNormalStyle(xssfWorkbook);
            CellStyle highlightStyle = createHighlightStyle(xssfWorkbook);

            // Row 1 & 2: Column Headers (Row indices 0 and 1)
            Row headerRow0 = sheet.createRow(0);
            Row headerRow1 = sheet.createRow(1);

            Cell headerLabelCell = headerRow0.createCell(0);
            headerLabelCell.setCellValue("Report Line");
            headerLabelCell.setCellStyle(headerStyle);
            CellRangeAddress labelRegion = new CellRangeAddress(0, 1, 0, 0);
            sheet.addMergedRegion(labelRegion);
            applyBordersAndStyle(labelRegion, sheet, headerStyle);

            List<String> granularityHeaders = new ArrayList<>();
            if (config.getGranularity() != null && !config.getGranularity().isBlank()) {
                for (String g : config.getGranularity().split(",")) {
                    String clean = g.trim();
                    if (clean.contains(".")) {
                        granularityHeaders.add(clean.substring(clean.lastIndexOf(".") + 1));
                    } else {
                        granularityHeaders.add(clean);
                    }
                }
            }

            int colIdx = 1;
            for (String gHeader : granularityHeaders) {
                Cell cell = headerRow0.createCell(colIdx);
                cell.setCellValue(gHeader);
                cell.setCellStyle(headerStyle);

                CellRangeAddress region = new CellRangeAddress(0, 1, colIdx, colIdx);
                sheet.addMergedRegion(region);
                applyBordersAndStyle(region, sheet, headerStyle);
                colIdx++;
            }

            List<ColumnDefDto> cols = new ArrayList<>(config.getColumns());
            cols.sort(Comparator.comparingInt(ColumnDefDto::displayOrder));

            // Build L2 children map and find L1 columns
            Map<String, List<ColumnDefDto>> l2ChildrenMap = new HashMap<>();
            List<ColumnDefDto> l1Cols = new ArrayList<>();
            for (ColumnDefDto col : cols) {
                if ("L2".equalsIgnoreCase(col.tierLevel()) && col.parentId() != null && !col.parentId().isBlank()) {
                    String key = col.parentId().trim().toUpperCase();
                    l2ChildrenMap.computeIfAbsent(key, k -> new ArrayList<>()).add(col);
                } else {
                    l1Cols.add(col);
                }
            }

            LocalDate refDate = config.getReferenceDate() != null ? config.getReferenceDate() : LocalDate.now();

            for (ColumnDefDto col : l1Cols) {
                LocalDate colRefDate = getAdjustedRefDate(refDate, col, config.getColumns());

                if (col.colType() == Enums.ColType.ROLLING) {
                    List<ExpandedColumn> subCols = expandRollingColumn(col, colRefDate);
                    int span = subCols.size();
                    if (span > 0) {
                        int startCol = colIdx;
                        int endCol = colIdx + span - 1;

                        Cell parentCell = headerRow0.createCell(startCol);
                        parentCell.setCellValue(col.label());

                        CellRangeAddress region = new CellRangeAddress(0, 0, startCol, endCol);
                        sheet.addMergedRegion(region);
                        applyBordersAndStyle(region, sheet, headerStyle);

                        for (int i = 0; i < span; i++) {
                            Cell childCell = headerRow1.createCell(startCol + i);
                            childCell.setCellValue(subCols.get(i).getLabel());
                            childCell.setCellStyle(headerStyle);
                        }
                        colIdx += span;
                    }
                } else if (col.colType() == Enums.ColType.HEADER) {
                    List<ColumnDefDto> children = l2ChildrenMap.getOrDefault(col.colId().trim().toUpperCase(), Collections.emptyList());
                    if (!children.isEmpty()) {
                        int totalSpan = 0;
                        List<String> childLabels = new ArrayList<>();
                        for (ColumnDefDto child : children) {
                            LocalDate childRefDate = getAdjustedRefDate(refDate, child, config.getColumns());
                            if (child.colType() == Enums.ColType.ROLLING) {
                                List<ExpandedColumn> subCols = expandRollingColumn(child, childRefDate);
                                totalSpan += subCols.size();
                                for (ExpandedColumn sc : subCols) {
                                    childLabels.add(sc.getLabel());
                                }
                            } else {
                                totalSpan += 1;
                                childLabels.add(child.label());
                            }
                        }

                        if (totalSpan > 0) {
                            int startCol = colIdx;
                            int endCol = colIdx + totalSpan - 1;

                            Cell parentCell = headerRow0.createCell(startCol);
                            parentCell.setCellValue(col.label());

                            CellRangeAddress region = new CellRangeAddress(0, 0, startCol, endCol);
                            sheet.addMergedRegion(region);
                            applyBordersAndStyle(region, sheet, headerStyle);

                            for (int i = 0; i < totalSpan; i++) {
                                Cell childCell = headerRow1.createCell(startCol + i);
                                childCell.setCellValue(childLabels.get(i));
                                childCell.setCellStyle(headerStyle);
                            }
                            colIdx += totalSpan;
                        }
                    } else {
                        // HEADER with no children: span both rows
                        Cell parentCell = headerRow0.createCell(colIdx);
                        parentCell.setCellValue(col.label());

                        CellRangeAddress region = new CellRangeAddress(0, 1, colIdx, colIdx);
                        sheet.addMergedRegion(region);
                        applyBordersAndStyle(region, sheet, headerStyle);
                        colIdx++;
                    }
                } else {
                    // Ordinary L1 columns: may have L2 children
                    List<ColumnDefDto> children = l2ChildrenMap.getOrDefault(col.colId().trim().toUpperCase(), Collections.emptyList());
                    if (!children.isEmpty()) {
                        int totalSpan = children.size();
                        int startCol = colIdx;
                        int endCol = colIdx + totalSpan - 1;

                        Cell parentCell = headerRow0.createCell(startCol);
                        parentCell.setCellValue(col.label());

                        CellRangeAddress region = new CellRangeAddress(0, 0, startCol, endCol);
                        sheet.addMergedRegion(region);
                        applyBordersAndStyle(region, sheet, headerStyle);

                        for (int i = 0; i < totalSpan; i++) {
                            Cell childCell = headerRow1.createCell(startCol + i);
                            childCell.setCellValue(children.get(i).label());
                            childCell.setCellStyle(headerStyle);
                        }
                        colIdx += totalSpan;
                    } else {
                        // Truly standalone: spans both rows
                        Cell parentCell = headerRow0.createCell(colIdx);
                        parentCell.setCellValue(col.label());

                        CellRangeAddress region = new CellRangeAddress(0, 1, colIdx, colIdx);
                        sheet.addMergedRegion(region);
                        applyBordersAndStyle(region, sheet, headerStyle);
                        colIdx++;
                    }
                }
            }

            // Orphaned L2s
            for (ColumnDefDto col : cols) {
                if ("L2".equalsIgnoreCase(col.tierLevel())) {
                    boolean hasValidParent = false;
                    if (col.parentId() != null && !col.parentId().isBlank()) {
                        String pKey = col.parentId().trim().toUpperCase();
                        for (ColumnDefDto p : l1Cols) {
                            if (pKey.equals(p.colId().trim().toUpperCase())) {
                                hasValidParent = true;
                                break;
                            }
                        }
                    }
                    if (!hasValidParent) {
                        Cell parentCell = headerRow0.createCell(colIdx);
                        parentCell.setCellValue(col.label());

                        CellRangeAddress region = new CellRangeAddress(0, 1, colIdx, colIdx);
                        sheet.addMergedRegion(region);
                        applyBordersAndStyle(region, sheet, headerStyle);
                        colIdx++;
                    }
                }
            }

            List<ExpandedColumn> expandedCols = getExpandedColumns(config);

            // Create Number Formats
            DataFormat format = workbook.createDataFormat();
            short numFormat = format.getFormat("#,##0.00_);(#,##0.00)");

            // Create column styles with number format applied
            CellStyle headerNumStyle = cloneWithFormat(xssfWorkbook, headerStyle, numFormat);
            CellStyle sectionNumStyle = cloneWithFormat(xssfWorkbook, sectionStyle, numFormat);
            CellStyle totalNumStyle = cloneWithFormat(xssfWorkbook, totalStyle, numFormat);
            CellStyle normalNumStyle = cloneWithFormat(xssfWorkbook, normalStyle, numFormat);
            CellStyle highlightNumStyle = cloneWithFormat(xssfWorkbook, highlightStyle, numFormat);

            // Render Body Rows
            int rowIdx = 2;
            for (ReportRowDto reportRow : config.getRows()) {
                Row row = sheet.createRow(rowIdx++);
                
                // Col A: Label
                Cell labelCell = row.createCell(0);
                String labelText = reportRow.label();
                if (reportRow.indentLevel() > 0) {
                    labelText = "  ".repeat(reportRow.indentLevel()) + labelText;
                }
                labelCell.setCellValue(labelText);

                // Select correct style
                CellStyle textStyle = normalStyle;
                CellStyle numStyle = normalNumStyle;

                String styleKey = reportRow.style() != null ? reportRow.style().toLowerCase() : "normal";
                switch (styleKey) {
                    case "header":
                        textStyle = headerStyle;
                        numStyle = headerNumStyle;
                        break;
                    case "section":
                        textStyle = sectionStyle;
                        numStyle = sectionNumStyle;
                        break;
                    case "total":
                        textStyle = totalStyle;
                        numStyle = totalNumStyle;
                        break;
                    case "highlight":
                        textStyle = highlightStyle;
                        numStyle = highlightNumStyle;
                        break;
                }

                labelCell.setCellStyle(textStyle);

                // Render Column Data (C1, C2...)
                if (reportRow.rowType() != Enums.RowType.blank) {
                    int dataColIdx = 1;
                    for (int g = 0; g < granularityHeaders.size(); g++) {
                        Cell cell = row.createCell(dataColIdx++);
                        cell.setCellValue("-");
                        cell.setCellStyle(textStyle);
                    }

                    for (ExpandedColumn col : expandedCols) {
                        Cell dataCell = row.createCell(dataColIdx++);
                        dataCell.setCellStyle(numStyle);
                        
                        String checkColId = col.isExpandedSubCol() ? col.getParentColId() : col.getColId();
                        if (reportRow.isEnabledFor(checkColId)) {
                            Map<String, Double> rowData = data.getOrDefault(reportRow.rowId().toUpperCase(), Map.of());
                            Double val = rowData.get(col.getColId().toUpperCase());
                            if (val == null && col.isExpandedSubCol()) {
                                val = rowData.get(col.getParentColId().toUpperCase());
                            }
                            dataCell.setCellValue(val != null ? val : 0.0);
                        }
                    }
                } else {
                    // Blank spacer row styling
                    int dataColIdx = 1;
                    for (int c = 1; c <= granularityHeaders.size() + expandedCols.size(); c++) {
                        row.createCell(dataColIdx++).setCellStyle(textStyle);
                    }
                }

                // If this is a data row, find and render granularity sub-rows
                if (reportRow.rowType() == Enums.RowType.data) {
                    String prefix = reportRow.rowId().toUpperCase() + "|";
                    List<String> subRowKeys = new ArrayList<>();
                    for (String key : data.keySet()) {
                        if (key.toUpperCase().startsWith(prefix)) {
                            subRowKeys.add(key);
                        }
                    }

                    if (!subRowKeys.isEmpty()) {
                        // Sort them alphabetically by the label representation
                        subRowKeys.sort((k1, k2) -> {
                            String part1 = k1.substring(prefix.length());
                            String part2 = k2.substring(prefix.length());
                            String label1 = String.join(", ", part1.split("\\|"));
                            String label2 = String.join(", ", part2.split("\\|"));
                            return label1.compareToIgnoreCase(label2);
                        });

                        // Render each sub-row
                        for (int i = 0; i < subRowKeys.size(); i++) {
                            String subRowKey = subRowKeys.get(i);
                            boolean isLast = (i == subRowKeys.size() - 1);
                            Row subRow = sheet.createRow(rowIdx++);

                            // Column A: Sub-row label with connector
                            Cell subLabelCell = subRow.createCell(0);
                            String connector = isLast ? "└ " : "├ ";
                            
                            String indentPrefix = "  ".repeat(reportRow.indentLevel() + 1) + connector;
                            subLabelCell.setCellValue(indentPrefix);
                            subLabelCell.setCellStyle(normalStyle);

                            String[] segments = subRowKey.substring(prefix.length()).split("\\|");
                            int dataColIdx = 1;
                            for (int sIdx = 0; sIdx < granularityHeaders.size(); sIdx++) {
                                Cell cell = subRow.createCell(dataColIdx++);
                                String val = (sIdx < segments.length) ? segments[sIdx] : "";
                                cell.setCellValue(val);
                                cell.setCellStyle(normalStyle);
                            }

                            // Render Column Data (C1, C2...)
                            for (ExpandedColumn col : expandedCols) {
                                Cell dataCell = subRow.createCell(dataColIdx++);
                                dataCell.setCellStyle(normalNumStyle);
                                
                                String checkColId = col.isExpandedSubCol() ? col.getParentColId() : col.getColId();
                                if (reportRow.isEnabledFor(checkColId)) {
                                    Map<String, Double> subRowData = data.getOrDefault(subRowKey, Map.of());
                                    Double val = subRowData.get(col.getColId().toUpperCase());
                                    if (val == null && col.isExpandedSubCol()) {
                                        val = subRowData.get(col.getParentColId().toUpperCase());
                                    }
                                    dataCell.setCellValue(val != null ? val : 0.0);
                                }
                            }
                        }
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i <= granularityHeaders.size() + expandedCols.size(); i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1024);
            }

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            return bos.toByteArray();
        }
    }

    private CellStyle cloneWithFormat(XSSFWorkbook wb, CellStyle source, short format) {
        CellStyle target = wb.createCellStyle();
        target.cloneStyleFrom(source);
        target.setDataFormat(format);
        return target;
    }

    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);

        // Alignment
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);

        // Header cells background tint fill: #1e293b
        XSSFColor fill = new XSSFColor(new java.awt.Color(30, 41, 59), new DefaultIndexedColorMap()); 
        style.setFillForegroundColor(fill);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createSectionStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setColor(new XSSFColor(new java.awt.Color(27, 79, 114), new DefaultIndexedColorMap()));
        style.setFont(font);

        XSSFColor fill = new XSSFColor(new java.awt.Color(214, 234, 248), new DefaultIndexedColorMap()); // #D6EAF8
        style.setFillForegroundColor(fill);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        return style;
    }

    private CellStyle createTotalStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        style.setFont(font);

        XSSFColor fill = new XSSFColor(new java.awt.Color(235, 245, 251), new DefaultIndexedColorMap()); // #EBF5FB
        style.setFillForegroundColor(fill);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.DOUBLE);
        return style;
    }

    private CellStyle createNormalStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    private CellStyle createHighlightStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        font.setBold(true);
        font.setColor(new XSSFColor(new java.awt.Color(133, 20, 75), new DefaultIndexedColorMap())); // #85144b
        style.setFont(font);

        XSSFColor fill = new XSSFColor(new java.awt.Color(255, 220, 0), new DefaultIndexedColorMap()); // #FFDC00
        style.setFillForegroundColor(fill);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
 
         style.setBorderTop(BorderStyle.THIN);
         style.setBorderBottom(BorderStyle.THIN);
         return style;
     }

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
        RegionUtil.setBorderTop(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderBottom(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderLeft(BorderStyle.THIN, region, sheet);
        RegionUtil.setBorderRight(BorderStyle.THIN, region, sheet);
    }
}
