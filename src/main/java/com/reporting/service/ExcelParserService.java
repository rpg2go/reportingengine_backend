package com.reporting.service;

import com.reporting.domain.*;
import com.reporting.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ExcelParserService {

    private final ReportRepository reportRepository;
    private final ColumnDefRepository columnDefRepository;
    private final ReportRowRepository reportRowRepository;
    private final RowMetricRepository rowMetricRepository;
    private final RowFormulaRepository rowFormulaRepository;
    private final RowColumnMapRepository rowColumnMapRepository;
    private final StyleRepository styleRepository;
    private final ImportRunRepository importRunRepository;
    private final JdbcTemplate jdbcTemplate;

    public ExcelParserService(ReportRepository reportRepository,
                              ColumnDefRepository columnDefRepository,
                              ReportRowRepository reportRowRepository,
                              RowMetricRepository rowMetricRepository,
                              RowFormulaRepository rowFormulaRepository,
                              RowColumnMapRepository rowColumnMapRepository,
                              StyleRepository styleRepository,
                              ImportRunRepository importRunRepository,
                              JdbcTemplate jdbcTemplate) {
        this.reportRepository = reportRepository;
        this.columnDefRepository = columnDefRepository;
        this.reportRowRepository = reportRowRepository;
        this.rowMetricRepository = rowMetricRepository;
        this.rowFormulaRepository = rowFormulaRepository;
        this.rowColumnMapRepository = rowColumnMapRepository;
        this.styleRepository = styleRepository;
        this.importRunRepository = importRunRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void importTemplate(InputStream excelInputStream, String filename) throws Exception {
        ImportRun run = ImportRun.builder()
            .sourceType("excel")
            .sourcePath(filename)
            .status("pending")
            .createdAt(LocalDateTime.now())
            .build();
        run = importRunRepository.save(run);

        try (Workbook workbook = WorkbookFactory.create(excelInputStream)) {
            Sheet sheet = workbook.getSheet("REPORT_DEFINITION");
            if (sheet == null) {
                throw new IllegalArgumentException("Sheet REPORT_DEFINITION not found in Excel workbook");
            }

            // 1. Read Section A (Columns, Rows 2 to 8, header row = 1)
            Row aHeaderRow = sheet.getRow(0); // index 0 is row 1
            Map<String, Integer> aHeaderMap = getHeaderMap(aHeaderRow);
            validateRequiredHeaders(aHeaderMap, Set.of("col_id", "label", "type", "offset", "rolling_n", "formula"), "Section A");

            List<ParsedColumn> parsedCols = new ArrayList<>();
            for (int r = 1; r <= 7; r++) { // rows 2 to 8 (index 1 to 7)
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String colId = getCellString(row.getCell(aHeaderMap.get("col_id")));
                if (colId.isBlank()) continue;

                ParsedColumn col = ParsedColumn.builder()
                    .colId(colId)
                    .label(getCellString(row.getCell(aHeaderMap.get("label"))))
                    .colType(getCellString(row.getCell(aHeaderMap.get("type"))).toUpperCase())
                    .periodOffset(getCellInt(row.getCell(aHeaderMap.get("offset")), 0))
                    .rollingN(getCellInt(row.getCell(aHeaderMap.get("rolling_n")), null))
                    .formulaExpr(getCellString(row.getCell(aHeaderMap.get("formula"))))
                    .displayOrder(parsedCols.size() + 1)
                    .build();
                parsedCols.add(col);
            }

            // 2. Read Section B (Rows 11+, header row = 10 / index 9)
            Row bHeaderRow = sheet.getRow(9); // index 9
            Map<String, Integer> bHeaderMap = getHeaderMap(bHeaderRow);
            validateRequiredHeaders(bHeaderMap, Set.of("report_id", "row_id", "label", "type", "source"), "Section B");

            // Find all active column mapping fields (C1, C2...)
            Map<String, Integer> colFlags = new HashMap<>();
            for (Map.Entry<String, Integer> entry : bHeaderMap.entrySet()) {
                if (entry.getKey().matches("^c\\d+$")) {
                    colFlags.put(entry.getKey().toUpperCase(), entry.getValue());
                }
            }

            List<ParsedRow> parsedRows = new ArrayList<>();
            int lastRow = sheet.getLastRowNum();
            for (int r = 10; r <= lastRow; r++) { // row 11 onward (index 10+)
                Row row = sheet.getRow(r);
                if (row == null) continue;
                String reportId = getCellString(row.getCell(bHeaderMap.get("report_id")));
                String rowId = getCellString(row.getCell(bHeaderMap.get("row_id")));
                if (reportId.isBlank() || rowId.isBlank()) continue;

                Set<String> activeCols = new HashSet<>();
                for (Map.Entry<String, Integer> colFlag : colFlags.entrySet()) {
                    String flagVal = getCellString(row.getCell(colFlag.getValue()));
                    if (!flagVal.isBlank()) {
                        activeCols.add(colFlag.getKey());
                    }
                }

                ParsedRow pr = ParsedRow.builder()
                    .reportId(reportId)
                    .rowId(rowId)
                    .label(getCellString(row.getCell(bHeaderMap.get("label"))))
                    .rowType(getCellString(row.getCell(bHeaderMap.get("type"))).toLowerCase())
                    .source(getCellString(row.getCell(bHeaderMap.get("source"))))
                    .parentRowId(bHeaderMap.containsKey("parent") ? getCellString(row.getCell(bHeaderMap.get("parent"))) : null)
                    .style(bHeaderMap.containsKey("style") ? getCellString(row.getCell(bHeaderMap.get("style"))) : "normal")
                    .indentLevel(bHeaderMap.containsKey("indent") ? getCellInt(row.getCell(bHeaderMap.get("indent")), 0) : 0)
                    .filterExpr(bHeaderMap.containsKey("filter") ? getCellString(row.getCell(bHeaderMap.get("filter"))) : null)
                    .activeCols(activeCols)
                    .displayOrder(parsedRows.size() + 1)
                    .build();
                parsedRows.add(pr);
            }

            // Group by Report ID and persist
            Map<String, List<ParsedRow>> rowsByReport = new HashMap<>();
            for (ParsedRow pr : parsedRows) {
                rowsByReport.computeIfAbsent(pr.getReportId(), k -> new ArrayList<>()).add(pr);
            }

            // Get default styles name map
            List<Style> dbStyles = styleRepository.findAll();
            Map<String, Integer> styleIdMap = new HashMap<>();
            for (Style style : dbStyles) {
                styleIdMap.put(style.getName().toLowerCase(), style.getStyleId());
            }

            for (Map.Entry<String, List<ParsedRow>> entry : rowsByReport.entrySet()) {
                String reportId = entry.getKey();
                List<ParsedRow> rRows = entry.getValue();

                // Delete old config to ensure clean import
                deleteReportConfigCascade(reportId);

                // Insert or Update Report (preserves PK identity context in JPA transaction)
                Report r = reportRepository.findById(reportId).orElse(null);
                if (r == null) {
                    r = Report.builder()
                        .reportId(reportId)
                        .name(convertToTitle(reportId))
                        .status("draft")
                        .version(1)
                        .createdAt(LocalDateTime.now())
                        .updatedAt(LocalDateTime.now())
                        .build();
                } else {
                    r.setName(convertToTitle(reportId));
                    r.setUpdatedAt(LocalDateTime.now());
                }
                r = reportRepository.save(r);

                // Insert Columns
                for (ParsedColumn pc : parsedCols) {
                    ColumnDef cd = ColumnDef.builder()
                        .report(r)
                        .colId(pc.getColId())
                        .label(pc.getLabel())
                        .colType(pc.getColType())
                        .periodOffset(pc.getPeriodOffset())
                        .rollingN(pc.getRollingN())
                        .formulaExpr(pc.getFormulaExpr())
                        .displayOrder(pc.getDisplayOrder())
                        .build();
                    columnDefRepository.save(cd);
                }

                // Insert Rows and details
                for (ParsedRow pr : rRows) {
                    Integer styleId = styleIdMap.getOrDefault(pr.getStyle().toLowerCase(), null);
                    if (styleId == null && !dbStyles.isEmpty()) {
                        styleId = styleIdMap.get("normal");
                    }

                    ReportRow rr = ReportRow.builder()
                        .rowId(pr.getRowId())
                        .reportId(reportId)
                        .parentRowId(pr.getParentRowId() != null && !pr.getParentRowId().isBlank() ? pr.getParentRowId() : null)
                        .label(pr.getLabel())
                        .rowType(pr.getRowType())
                        .displayOrder(pr.getDisplayOrder())
                        .indentLevel(pr.getIndentLevel())
                        .styleId(styleId)
                        .filterExpr(pr.getFilterExpr() != null && !pr.getFilterExpr().isBlank() ? pr.getFilterExpr() : null)
                        .build();
                    reportRowRepository.save(rr);

                    // Row Metric (DATA rows)
                    if (pr.getRowType().equalsIgnoreCase("data") && pr.getSource() != null && !pr.getSource().isBlank()) {
                        String query = "SELECT measure_id FROM reporting.sem_measure WHERE name = ? LIMIT 1";
                        List<Integer> ids = jdbcTemplate.queryForList(query, Integer.class, pr.getSource());
                        if (!ids.isEmpty()) {
                            RowMetric rm = RowMetric.builder()
                                .reportId(reportId)
                                .rowId(pr.getRowId())
                                .measureId(ids.get(0))
                                .build();
                            rowMetricRepository.save(rm);
                        }
                    }

                    // Row Formula (CALC rows)
                    if (pr.getRowType().equalsIgnoreCase("calc") && pr.getSource() != null && !pr.getSource().isBlank()) {
                        RowFormula rf = RowFormula.builder()
                            .reportId(reportId)
                            .rowId(pr.getRowId())
                            .formulaExpr(pr.getSource())
                            .build();
                        rowFormulaRepository.save(rf);
                    }

                    // Column flags (enablement)
                    for (ParsedColumn pc : parsedCols) {
                        boolean isEnabled = pr.getActiveCols().contains(pc.getColId().toUpperCase());
                        RowColumnMap rcm = RowColumnMap.builder()
                            .reportId(reportId)
                            .rowId(pr.getRowId())
                            .colId(pc.getColId())
                            .isEnabled(isEnabled)
                            .build();
                        rowColumnMapRepository.save(rcm);
                    }
                }
            }

            run.setStatus("success");
            run.setCompletedAt(LocalDateTime.now());
            importRunRepository.save(run);

        } catch (Exception e) {
            run.setStatus("failed");
            run.setErrorMsg(e.getMessage());
            run.setCompletedAt(LocalDateTime.now());
            importRunRepository.save(run);
            throw e;
        }
    }

    private void deleteReportConfigCascade(String reportId) {
        jdbcTemplate.update("DELETE FROM reporting.rpt_row_column_map WHERE report_id = ?", reportId);
        jdbcTemplate.update("DELETE FROM reporting.rpt_row_formula WHERE report_id = ?", reportId);
        jdbcTemplate.update("DELETE FROM reporting.rpt_row_metric WHERE report_id = ?", reportId);
        jdbcTemplate.update("DELETE FROM reporting.rpt_row WHERE report_id = ?", reportId);
        jdbcTemplate.update("DELETE FROM reporting.rpt_column_def WHERE report_id = ?", reportId);
    }

    private Map<String, Integer> getHeaderMap(Row row) {
        Map<String, Integer> map = new HashMap<>();
        if (row == null) return map;
        for (int c = 0; c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            String val = getCellString(cell).toLowerCase();
            if (!val.isBlank()) {
                map.put(val, c);
            }
        }
        return map;
    }

    private void validateRequiredHeaders(Map<String, Integer> map, Set<String> required, String section) {
        for (String req : required) {
            if (!map.containsKey(req)) {
                throw new IllegalArgumentException(String.format("Missing required column '%s' in %s", req, section));
            }
        }
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((int) cell.getNumericCellValue());
        }
        return "";
    }

    private int getCellInt(Cell cell, int defaultValue) {
        if (cell == null) return defaultValue;
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                return Integer.parseInt(cell.getStringCellValue().trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private Integer getCellInt(Cell cell, Integer defaultValue) {
        if (cell == null) return defaultValue;
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        } else if (cell.getCellType() == CellType.STRING) {
            try {
                return Integer.parseInt(cell.getStringCellValue().trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String convertToTitle(String reportId) {
        if (reportId == null || reportId.isBlank()) {
            return "";
        }
        if (reportId.equalsIgnoreCase("RPT_001")) {
            return "Sales Weekly Report";
        }
        String[] words = reportId.split("_");
        StringBuilder title = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) continue;
            String lower = word.toLowerCase();
            if (lower.equals("kpi") || lower.equals("aum")) {
                title.append(word.toUpperCase()).append(" ");
            } else {
                title.append(Character.toUpperCase(word.charAt(0)))
                     .append(lower.substring(1))
                     .append(" ");
            }
        }
        return title.toString().trim();
    }

    @lombok.Value
    @lombok.Builder
    private static class ParsedColumn {
        String colId;
        String label;
        String colType;
        int periodOffset;
        Integer rollingN;
        String formulaExpr;
        int displayOrder;
    }

    @lombok.Value
    @lombok.Builder
    private static class ParsedRow {
        String reportId;
        String rowId;
        String label;
        String rowType;
        String source;
        String parentRowId;
        String style;
        int indentLevel;
        String filterExpr;
        Set<String> activeCols;
        int displayOrder;
    }
}
