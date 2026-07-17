package com.reporting.service;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Element;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPCell;
import com.reporting.dto.ColumnDefDto;
import com.reporting.dto.Enums;
import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ReportRowDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.awt.Color;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PdfRendererService {

    private final LayoutRendererService layoutRendererService;

    public PdfRendererService(LayoutRendererService layoutRendererService) {
        this.layoutRendererService = layoutRendererService;
    }

    public byte[] render(ReportConfigDto config, Map<String, Map<String, Double>> data) throws Exception {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);
        document.open();

        // 1. Report Title
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.decode("#0f172a"));
        Paragraph title = new Paragraph(config.getReportName(), titleFont);
        title.setSpacingAfter(8);
        document.add(title);

        // 2. Reference Date / Metadata
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.decode("#475569"));
        String dateStr = config.getReferenceDate() != null 
            ? config.getReferenceDate().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))
            : LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US));
        Paragraph meta = new Paragraph("Reporting Date: " + dateStr + "  |  Version: " + config.getVersion(), metaFont);
        meta.setSpacingAfter(20);
        document.add(meta);

        // 3. Columns resolution
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

        // Calculate total columns in table
        int totalCols = 1 + granularityHeaders.size(); // Report Line + Granularity Columns
        List<LayoutRendererService.ExpandedColumn> expandedCols = layoutRendererService.getExpandedColumns(config);
        totalCols += expandedCols.size();

        PdfPTable table = new PdfPTable(totalCols);
        table.setWidthPercentage(100f);
        
        // Let's set column widths: "Report Line" gets more width, others get equal width
        float[] widths = new float[totalCols];
        widths[0] = 3.5f; // Report Line gets 3.5x width
        for (int i = 1; i <= granularityHeaders.size(); i++) {
            widths[i] = 1.8f;
        }
        for (int i = granularityHeaders.size() + 1; i < totalCols; i++) {
            widths[i] = 1.3f;
        }
        table.setWidths(widths);

        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Color headerBg = Color.decode("#1e293b");

        // Row 0 Header
        PdfPCell cell = new PdfPCell(new Phrase("Report Line", headerFont));
        cell.setBackgroundColor(headerBg);
        cell.setRowspan(2);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        table.addCell(cell);

        for (String gHeader : granularityHeaders) {
            cell = new PdfPCell(new Phrase(gHeader, headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setRowspan(2);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(5);
            table.addCell(cell);
        }

        // Parent child hierarchy map
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

        // Row 0 (L1 headers)
        List<ColumnDefDto> row2ColsToRender = new ArrayList<>();
        for (ColumnDefDto col : l1Cols) {
            LocalDate colRefDate = layoutRendererService.getAdjustedRefDate(refDate, col, config.getColumns());

            if (col.colType() == Enums.ColType.ROLLING) {
                List<LayoutRendererService.ExpandedColumn> subCols = layoutRendererService.expandRollingColumn(col, colRefDate);
                int span = subCols.size();
                if (span > 0) {
                    cell = new PdfPCell(new Phrase(col.label(), headerFont));
                    cell.setBackgroundColor(headerBg);
                    cell.setColspan(span);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setPadding(5);
                    table.addCell(cell);

                    for (LayoutRendererService.ExpandedColumn sc : subCols) {
                        row2ColsToRender.add(new ColumnDefDto(sc.getColId(), sc.getLabel(), Enums.ColType.WTD, 0, null, null, 0));
                    }
                }
            } else if (col.colType() == Enums.ColType.HEADER) {
                List<ColumnDefDto> children = l2ChildrenMap.getOrDefault(col.colId().trim().toUpperCase(), Collections.emptyList());
                if (!children.isEmpty()) {
                    int totalSpan = 0;
                    List<ColumnDefDto> childLeaves = new ArrayList<>();
                    for (ColumnDefDto child : children) {
                        LocalDate childRefDate = layoutRendererService.getAdjustedRefDate(refDate, child, config.getColumns());
                        if (child.colType() == Enums.ColType.ROLLING) {
                            List<LayoutRendererService.ExpandedColumn> subCols = layoutRendererService.expandRollingColumn(child, childRefDate);
                            totalSpan += subCols.size();
                            for (LayoutRendererService.ExpandedColumn sc : subCols) {
                                childLeaves.add(new ColumnDefDto(sc.getColId(), sc.getLabel(), Enums.ColType.WTD, 0, null, null, 0));
                            }
                        } else {
                            totalSpan += 1;
                            childLeaves.add(child);
                        }
                    }

                    if (totalSpan > 0) {
                        cell = new PdfPCell(new Phrase(col.label(), headerFont));
                        cell.setBackgroundColor(headerBg);
                        cell.setColspan(totalSpan);
                        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                        cell.setPadding(5);
                        table.addCell(cell);

                        row2ColsToRender.addAll(childLeaves);
                    }
                } else {
                    cell = new PdfPCell(new Phrase(col.label(), headerFont));
                    cell.setBackgroundColor(headerBg);
                    cell.setRowspan(2);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setPadding(5);
                    table.addCell(cell);
                }
            } else {
                List<ColumnDefDto> children = l2ChildrenMap.getOrDefault(col.colId().trim().toUpperCase(), Collections.emptyList());
                if (!children.isEmpty()) {
                    int totalSpan = children.size();
                    cell = new PdfPCell(new Phrase(col.label(), headerFont));
                    cell.setBackgroundColor(headerBg);
                    cell.setColspan(totalSpan);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setPadding(5);
                    table.addCell(cell);

                    row2ColsToRender.addAll(children);
                } else {
                    cell = new PdfPCell(new Phrase(col.label(), headerFont));
                    cell.setBackgroundColor(headerBg);
                    cell.setRowspan(2);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setPadding(5);
                    table.addCell(cell);
                }
            }
        }

        // Orphaned L2s (in case there are any)
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
                    cell = new PdfPCell(new Phrase(col.label(), headerFont));
                    cell.setBackgroundColor(headerBg);
                    cell.setRowspan(2);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setPadding(5);
                    table.addCell(cell);
                }
            }
        }

        // Row 1 (L2 children)
        for (ColumnDefDto child : row2ColsToRender) {
            cell = new PdfPCell(new Phrase(child.label(), headerFont));
            cell.setBackgroundColor(headerBg);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(5);
            table.addCell(cell);
        }

        // Row fonts
        Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.decode("#1b4f72"));
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.BLACK);
        Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.BLACK);

        Color sectionBg = Color.decode("#d6eaf8");
        Color totalBg = Color.decode("#ebf5fb");
        Color zebraBg = Color.decode("#f8fafc");

        int dataRowIdx = 0;
        for (ReportRowDto reportRow : config.getRows()) {
            boolean isSection = "section".equalsIgnoreCase(reportRow.style());
            boolean isTotal = "total".equalsIgnoreCase(reportRow.style());
            Font rowFont = isSection ? sectionFont : (isTotal ? boldFont : normalFont);
            Color rowBg = isSection ? sectionBg : (isTotal ? totalBg : (dataRowIdx % 2 == 1 ? zebraBg : Color.WHITE));

            // Cell 0: Label
            String label = reportRow.label();
            if (reportRow.indentLevel() > 0) {
                label = "  ".repeat(reportRow.indentLevel()) + label;
            }
            cell = new PdfPCell(new Phrase(label, rowFont));
            cell.setBackgroundColor(rowBg);
            cell.setPadding(4);
            table.addCell(cell);

            // Granularity cells: empty for parent rows
            for (int i = 0; i < granularityHeaders.size(); i++) {
                cell = new PdfPCell(new Phrase("-", rowFont));
                cell.setBackgroundColor(rowBg);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(4);
                table.addCell(cell);
            }

            // Columns data
            for (LayoutRendererService.ExpandedColumn col : expandedCols) {
                cell = new PdfPCell();
                cell.setBackgroundColor(rowBg);
                cell.setPadding(4);
                cell.setHorizontalAlignment(Element.ALIGN_RIGHT);

                String checkColId = col.isExpandedSubCol() ? col.getParentColId() : col.getColId();
                if (reportRow.isEnabledFor(checkColId)) {
                    Map<String, Double> rowData = data.getOrDefault(reportRow.rowId(), Map.of());
                    Double val = rowData.get(col.getColId().toUpperCase());
                    if (val == null && col.isExpandedSubCol()) {
                        val = rowData.get(col.getParentColId().toUpperCase());
                    }
                    double value = val != null ? val : 0.0;
                    // Format number: #,##0.00;(#,##0.00)
                    String formatted = value >= 0 
                        ? String.format(Locale.US, "%,.2f", value) 
                        : String.format(Locale.US, "(%,.2f)", Math.abs(value));
                    cell.setPhrase(new Phrase(formatted, rowFont));
                } else {
                    cell.setPhrase(new Phrase("", rowFont));
                }
                table.addCell(cell);
            }

            dataRowIdx++;

            // Check granularity sub-rows
            List<String> subKeys = new ArrayList<>();
            for (String key : data.keySet()) {
                if (key.startsWith(reportRow.rowId() + "|")) {
                    subKeys.add(key);
                }
            }

            if (!subKeys.isEmpty()) {
                // sort alphabetically by segment values
                subKeys.sort(String::compareTo);

                for (int skIdx = 0; skIdx < subKeys.size(); skIdx++) {
                    String subRowKey = subKeys.get(skIdx);
                    String[] parts = subRowKey.split("\\|");
                    String connector = (skIdx == subKeys.size() - 1) ? "  \u2514 " : "  \u251c ";

                    Color subRowBg = dataRowIdx % 2 == 1 ? zebraBg : Color.WHITE;

                    // Label cell
                    cell = new PdfPCell(new Phrase(connector, normalFont));
                    cell.setBackgroundColor(subRowBg);
                    cell.setPadding(4);
                    table.addCell(cell);

                    // Granularity columns
                    for (int gIdx = 0; gIdx < granularityHeaders.size(); gIdx++) {
                        String val = (gIdx + 1 < parts.length) ? parts[gIdx + 1] : "-";
                        cell = new PdfPCell(new Phrase(val, normalFont));
                        cell.setBackgroundColor(subRowBg);
                        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                        cell.setPadding(4);
                        table.addCell(cell);
                    }

                    // Columns
                    for (LayoutRendererService.ExpandedColumn col : expandedCols) {
                        cell = new PdfPCell();
                        cell.setBackgroundColor(subRowBg);
                        cell.setPadding(4);
                        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);

                        String checkColId = col.isExpandedSubCol() ? col.getParentColId() : col.getColId();
                        if (reportRow.isEnabledFor(checkColId)) {
                            Map<String, Double> subRowData = data.getOrDefault(subRowKey, Map.of());
                            Double val = subRowData.get(col.getColId().toUpperCase());
                            if (val == null && col.isExpandedSubCol()) {
                                val = subRowData.get(col.getParentColId().toUpperCase());
                            }
                            double value = val != null ? val : 0.0;
                            String formatted = value >= 0 
                                ? String.format(Locale.US, "%,.2f", value) 
                                : String.format(Locale.US, "(%,.2f)", Math.abs(value));
                            cell.setPhrase(new Phrase(formatted, normalFont));
                        } else {
                            cell.setPhrase(new Phrase("", normalFont));
                        }
                        table.addCell(cell);
                    }
                    dataRowIdx++;
                }
            }
        }

        document.add(table);
        document.close();
        return baos.toByteArray();
    }
}
