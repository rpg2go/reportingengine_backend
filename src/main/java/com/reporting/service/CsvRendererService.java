package com.reporting.service;

import com.reporting.dto.ColumnDefDto;
import com.reporting.dto.Enums;
import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ReportRowDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@Service
public class CsvRendererService {

    private final LayoutRendererService layoutRendererService;

    public CsvRendererService(LayoutRendererService layoutRendererService) {
        this.layoutRendererService = layoutRendererService;
    }

    public byte[] render(ReportConfigDto config, Map<String, Map<String, Double>> data) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(baos, false, StandardCharsets.UTF_8)) {
            List<ColumnDefDto> cols = new ArrayList<>(config.getColumns());
            cols.sort(Comparator.comparingInt(ColumnDefDto::displayOrder));

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

            // Build parent-child mapping
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

            // Row 0: L1 header row
            List<String> row0 = new ArrayList<>();
            row0.add("Report Line");
            for (String g : granularityHeaders) {
                row0.add(g);
            }

            for (ColumnDefDto col : l1Cols) {
                LocalDate colRefDate = layoutRendererService.getAdjustedRefDate(refDate, col, config.getColumns());

                if (col.colType() == Enums.ColType.ROLLING) {
                    List<LayoutRendererService.ExpandedColumn> subCols = layoutRendererService.expandRollingColumn(col, colRefDate);
                    int span = subCols.size();
                    if (span > 0) {
                        row0.add(col.label());
                        for (int i = 1; i < span; i++) {
                            row0.add("");
                        }
                    }
                } else if (col.colType() == Enums.ColType.HEADER) {
                    List<ColumnDefDto> children = l2ChildrenMap.getOrDefault(col.colId().trim().toUpperCase(), Collections.emptyList());
                    if (!children.isEmpty()) {
                        int totalSpan = 0;
                        for (ColumnDefDto child : children) {
                            LocalDate childRefDate = layoutRendererService.getAdjustedRefDate(refDate, child, config.getColumns());
                            if (child.colType() == Enums.ColType.ROLLING) {
                                List<LayoutRendererService.ExpandedColumn> subCols = layoutRendererService.expandRollingColumn(child, childRefDate);
                                totalSpan += subCols.size();
                            } else {
                                totalSpan += 1;
                            }
                        }
                        if (totalSpan > 0) {
                            row0.add(col.label());
                            for (int i = 1; i < totalSpan; i++) {
                                row0.add("");
                            }
                        }
                    } else {
                        row0.add(col.label());
                    }
                } else {
                    List<ColumnDefDto> children = l2ChildrenMap.getOrDefault(col.colId().trim().toUpperCase(), Collections.emptyList());
                    if (!children.isEmpty()) {
                        int totalSpan = children.size();
                        row0.add(col.label());
                        for (int i = 1; i < totalSpan; i++) {
                            row0.add("");
                        }
                    } else {
                        row0.add(col.label());
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
                        row0.add(col.label());
                    }
                }
            }

            // Write row0 to CSV
            writeCsvRow(writer, row0);

            // Row 1: L2 header row
            List<String> row1 = new ArrayList<>();
            row1.add(""); // Report Line spans both rows, so Row 1 has empty string
            for (int i = 0; i < granularityHeaders.size(); i++) {
                row1.add("");
            }

            for (ColumnDefDto col : l1Cols) {
                if (col.colType() == Enums.ColType.ROLLING) {
                    LocalDate colRefDate = layoutRendererService.getAdjustedRefDate(refDate, col, config.getColumns());
                    List<LayoutRendererService.ExpandedColumn> subCols = layoutRendererService.expandRollingColumn(col, colRefDate);
                    for (LayoutRendererService.ExpandedColumn sc : subCols) {
                        row1.add(sc.getLabel());
                    }
                } else if (col.colType() == Enums.ColType.HEADER) {
                    List<ColumnDefDto> children = l2ChildrenMap.getOrDefault(col.colId().trim().toUpperCase(), Collections.emptyList());
                    if (!children.isEmpty()) {
                        for (ColumnDefDto child : children) {
                            LocalDate childRefDate = layoutRendererService.getAdjustedRefDate(refDate, child, config.getColumns());
                            if (child.colType() == Enums.ColType.ROLLING) {
                                List<LayoutRendererService.ExpandedColumn> subCols = layoutRendererService.expandRollingColumn(child, childRefDate);
                                for (LayoutRendererService.ExpandedColumn sc : subCols) {
                                    row1.add(sc.getLabel());
                                }
                            } else {
                                row1.add(child.label());
                            }
                        }
                    } else {
                        row1.add("");
                    }
                } else {
                    List<ColumnDefDto> children = l2ChildrenMap.getOrDefault(col.colId().trim().toUpperCase(), Collections.emptyList());
                    if (!children.isEmpty()) {
                        for (ColumnDefDto child : children) {
                            row1.add(child.label());
                        }
                    } else {
                        row1.add("");
                    }
                }
            }

            // Orphaned L2s get empty in row1 since they span both
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
                        row1.add("");
                    }
                }
            }

            writeCsvRow(writer, row1);

            // Body rows
            List<LayoutRendererService.ExpandedColumn> expandedCols = layoutRendererService.getExpandedColumns(config);

            for (ReportRowDto reportRow : config.getRows()) {
                List<String> bodyRow = new ArrayList<>();
                // Indent level for label
                String label = reportRow.label();
                if (reportRow.indentLevel() > 0) {
                    label = "  ".repeat(reportRow.indentLevel()) + label;
                }
                bodyRow.add(label);

                // Granularity
                for (int i = 0; i < granularityHeaders.size(); i++) {
                    bodyRow.add("-");
                }

                // Data columns
                for (LayoutRendererService.ExpandedColumn col : expandedCols) {
                    String checkColId = col.isExpandedSubCol() ? col.getParentColId() : col.getColId();
                    if (reportRow.isEnabledFor(checkColId)) {
                        Map<String, Double> rowData = data.getOrDefault(reportRow.rowId(), Map.of());
                        Double val = rowData.get(col.getColId().toUpperCase());
                        if (val == null && col.isExpandedSubCol()) {
                            val = rowData.get(col.getParentColId().toUpperCase());
                        }
                        double value = val != null ? val : 0.0;
                        bodyRow.add(String.format(Locale.US, "%.2f", value));
                    } else {
                        bodyRow.add("");
                    }
                }

                writeCsvRow(writer, bodyRow);

                // Granularity sub-rows
                List<String> subKeys = new ArrayList<>();
                for (String key : data.keySet()) {
                    if (key.startsWith(reportRow.rowId() + "|")) {
                        subKeys.add(key);
                    }
                }

                if (!subKeys.isEmpty()) {
                    subKeys.sort(String::compareTo);

                    for (int skIdx = 0; skIdx < subKeys.size(); skIdx++) {
                        String subRowKey = subKeys.get(skIdx);
                        String[] parts = subRowKey.split("\\|");
                        String connector = (skIdx == subKeys.size() - 1) ? "  \u2514 " : "  \u251c ";

                        List<String> subRow = new ArrayList<>();
                        subRow.add(connector);

                        for (int gIdx = 0; gIdx < granularityHeaders.size(); gIdx++) {
                            String val = (gIdx + 1 < parts.length) ? parts[gIdx + 1] : "-";
                            subRow.add(val);
                        }

                        for (LayoutRendererService.ExpandedColumn col : expandedCols) {
                            String checkColId = col.isExpandedSubCol() ? col.getParentColId() : col.getColId();
                            if (reportRow.isEnabledFor(checkColId)) {
                                Map<String, Double> subRowData = data.getOrDefault(subRowKey, Map.of());
                                Double val = subRowData.get(col.getColId().toUpperCase());
                                if (val == null && col.isExpandedSubCol()) {
                                    val = subRowData.get(col.getParentColId().toUpperCase());
                                }
                                double value = val != null ? val : 0.0;
                                subRow.add(String.format(Locale.US, "%.2f", value));
                            } else {
                                subRow.add("");
                            }
                        }

                        writeCsvRow(writer, subRow);
                    }
                }
            }
        }

        return baos.toByteArray();
    }

    private void writeCsvRow(PrintWriter writer, List<String> fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(escapeCsvField(fields.get(i)));
        }
        writer.println(sb.toString());
    }

    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
