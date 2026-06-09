package com.reporting.service;

import com.reporting.dto.*;
import org.apache.poi.ss.usermodel.*;
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

@Service
public class LayoutRendererService {

    private static class ExpandedColumn {
        private final String colId;
        private final String label;
        private final Enums.ColType colType;
        private final int periodOffset;
        private final Integer rollingN;
        private final String rollingGrain;
        private final String formulaExpr;
        private final int displayOrder;
        private final boolean isExpandedSubCol;
        private final String parentColId;

        public ExpandedColumn(String colId, String label, Enums.ColType colType, int periodOffset, 
                              Integer rollingN, String rollingGrain, String formulaExpr, 
                              int displayOrder, boolean isExpandedSubCol, String parentColId) {
            this.colId = colId;
            this.label = label;
            this.colType = colType;
            this.periodOffset = periodOffset;
            this.rollingN = rollingN;
            this.rollingGrain = rollingGrain;
            this.formulaExpr = formulaExpr;
            this.displayOrder = displayOrder;
            this.isExpandedSubCol = isExpandedSubCol;
            this.parentColId = parentColId;
        }

        public String getColId() { return colId; }
        public String getLabel() { return label; }
        public Enums.ColType getColType() { return colType; }
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

    private List<ExpandedColumn> getExpandedColumns(ReportConfigDto config) {
        List<ExpandedColumn> expanded = new ArrayList<>();
        LocalDate refDate = config.getReferenceDate() != null ? config.getReferenceDate() : LocalDate.now();

        for (ColumnDefDto col : config.getColumns()) {
            if (col.colType() == Enums.ColType.ROLLING) {
                int rollingN = col.rollingN() != null ? col.rollingN() : 1;
                String grain = col.effectiveRollingGrain();

                for (int i = 1; i <= rollingN; i++) {
                    String subColId = col.colId() + "_" + i;
                    String label = "";

                    switch (grain) {
                        case "DAY": {
                            LocalDate target = refDate.minusDays(i);
                            label = formatShortDay(target);
                            break;
                        }
                        case "MONTH": {
                            LocalDate target = refDate.minusMonths(i);
                            label = formatMonthYear(target);
                            break;
                        }
                        case "WEEK":
                        default: {
                            LocalDate refMonday = refDate.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                            LocalDate targetMonday = refMonday.minusWeeks(i);
                            LocalDate targetSunday = targetMonday.plusDays(6);
                            label = formatWeekRange(targetMonday, targetSunday);
                            break;
                        }
                    }

                    expanded.add(new ExpandedColumn(
                        subColId,
                        label,
                        col.colType(),
                        -i,
                        null,
                        null,
                        "",
                        col.displayOrder(),
                        true,
                        col.colId()
                    ));
                }
            } else {
                expanded.add(new ExpandedColumn(
                    col.colId(),
                    col.label(),
                    col.colType(),
                    col.periodOffset(),
                    col.rollingN(),
                    col.rollingGrain(),
                    col.formulaExpr(),
                    col.displayOrder(),
                    false,
                    null
                ));
            }
        }
        return expanded;
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

            // Row 1: Column Headers
            Row headerRow = sheet.createRow(0);
            Cell headerLabelCell = headerRow.createCell(0);
            headerLabelCell.setCellValue("Report Line");
            headerLabelCell.setCellStyle(headerStyle);

            List<ExpandedColumn> expandedCols = getExpandedColumns(config);
            int colIdx = 1;
            for (ExpandedColumn col : expandedCols) {
                Cell cell = headerRow.createCell(colIdx++);
                cell.setCellValue(col.getLabel());
                cell.setCellStyle(headerStyle);
            }

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
            int rowIdx = 1;
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
                    for (int c = 1; c <= expandedCols.size(); c++) {
                        row.createCell(c).setCellStyle(textStyle);
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i <= expandedCols.size(); i++) {
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
}
