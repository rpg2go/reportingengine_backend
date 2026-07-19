package com.reporting.service;

import com.reporting.domain.*;
import com.reporting.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportCloneService {

    private final ReportRepository reportRepository;
    private final ColumnDefRepository columnDefRepository;
    private final ReportRowRepository reportRowRepository;
    private final RowMetricRepository rowMetricRepository;
    private final RowFormulaRepository rowFormulaRepository;
    private final RowColumnMapRepository rowColumnMapRepository;

    /**
     * Performs a deep, transactional duplication of an existing report configuration template.
     * The cloned configuration is initialized with the specified name, version 1, status DRAFT,
     * and safe detached mirror entities of all child elements.
     *
     * @param sourceId      the ID of the report to clone from
     * @param newReportName the user-specified title for the cloned report
     * @return the newly saved cloned Report entity
     */
    @Transactional
    public Report cloneReport(String sourceId, String newReportName) {
        if (newReportName == null || newReportName.trim().isBlank()) {
            throw new IllegalArgumentException("New report name cannot be empty");
        }

        // Validate that the name does not match any existing active report
        if (reportRepository.existsByReportNameAndDeletedFalse(newReportName.trim())) {
            throw new IllegalArgumentException("A report named '" + newReportName.trim() + "' already exists");
        }

        // Generate and verify a unique reportId
        String generatedId = sanitizeToReportId(newReportName.trim());
        String finalId = generatedId;
        int suffix = 1;
        while (reportRepository.findById(new ReportPk(finalId, 1)).isPresent()) {
            String suffixStr = "_" + suffix;
            if (generatedId.length() + suffixStr.length() > 50) {
                finalId = generatedId.substring(0, 50 - suffixStr.length()) + suffixStr;
            } else {
                finalId = generatedId + suffixStr;
            }
            suffix++;
        }

        log.info("Cloning report '{}' (ID: {}) to '{}' (new ID: {})", sourceId, sourceId, newReportName, finalId);

        // Load the latest version of the original report
        Report source = reportRepository.findFirstByReportIdAndDeletedFalseOrderByVersionDesc(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Source report not found: " + sourceId));

        // Create the new parent Report entity
        Report clonedParent = Report.builder()
            .reportId(finalId)
            .version(1)
            .reportName(newReportName.trim())
            .description(source.getDescription() != null ? source.getDescription() : "Cloned from " + source.getReportName())
            .status("draft")
            .granularity(source.getGranularity())
            .quickFilters(source.getQuickFilters())
            .generalFilters(source.getGeneralFilters())
            .sourceTable(source.getSourceTable())
            .sourceField(source.getSourceField())
            .reportingDateType(source.getReportingDateType())
            .reportingDateStatic(source.getReportingDateStatic())
            .reportingDateExpression(source.getReportingDateExpression())
            .timeframeStartType(source.getTimeframeStartType())
            .timeframeStartStatic(source.getTimeframeStartStatic())
            .timeframeStartExpression(source.getTimeframeStartExpression())
            .timeframeEndType(source.getTimeframeEndType())
            .timeframeEndStatic(source.getTimeframeEndStatic())
            .timeframeEndExpression(source.getTimeframeEndExpression())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .deleted(false)
            .build();

        clonedParent = reportRepository.save(clonedParent);

        // Retrieve all child elements of the source report's specific version
        List<ColumnDef> sourceColumns = columnDefRepository.findByReportReportIdAndReportVersionOrderByDisplayOrderAsc(sourceId, source.getVersion());
        List<ReportRow> sourceRows = reportRowRepository.findByReportIdAndVersionOrderByDisplayOrderAsc(sourceId, source.getVersion());
        List<RowMetric> sourceMetrics = rowMetricRepository.findByReportIdAndVersion(sourceId, source.getVersion());
        List<RowFormula> sourceFormulas = rowFormulaRepository.findByReportIdAndVersion(sourceId, source.getVersion());
        List<RowColumnMap> sourceMaps = rowColumnMapRepository.findByReportIdAndVersion(sourceId, source.getVersion());

        // Clone ColumnDef structures
        List<ColumnDef> clonedColumns = new ArrayList<>();
        for (ColumnDef col : sourceColumns) {
            ColumnDef clonedCol = ColumnDef.builder()
                .report(clonedParent)
                .colId(col.getColId())
                .label(col.getLabel())
                .colType(col.getColType())
                .periodOffset(col.getPeriodOffset())
                .rollingN(col.getRollingN())
                .rollingGrain(col.getRollingGrain())
                .formulaExpr(col.getFormulaExpr())
                .tierLevel(col.getTierLevel())
                .parentId(col.getParentId())
                .displayOrder(col.getDisplayOrder())
                .build();
            clonedColumns.add(clonedCol);
        }
        columnDefRepository.saveAll(clonedColumns);

        // Clone ReportRow structures
        List<ReportRow> clonedRows = new ArrayList<>();
        for (ReportRow row : sourceRows) {
            ReportRow clonedRow = ReportRow.builder()
                .rowId(row.getRowId())
                .reportId(clonedParent.getReportId())
                .version(1)
                .report(clonedParent)
                .parentRowId(row.getParentRowId())
                .label(row.getLabel())
                .rowType(row.getRowType())
                .displayOrder(row.getDisplayOrder())
                .indentLevel(row.getIndentLevel())
                .styleId(row.getStyleId())
                .filterExpr(row.getFilterExpr())
                .build();
            clonedRows.add(clonedRow);
        }
        reportRowRepository.saveAll(clonedRows);

        // Clone RowMetric structures
        List<RowMetric> clonedMetrics = new ArrayList<>();
        for (RowMetric metric : sourceMetrics) {
            RowMetric clonedMetric = RowMetric.builder()
                .reportId(clonedParent.getReportId())
                .version(1)
                .rowId(metric.getRowId())
                .sqlExpr(metric.getSqlExpr())
                .measureDefinition(metric.getMeasureDefinition())
                .build();
            clonedMetrics.add(clonedMetric);
        }
        rowMetricRepository.saveAll(clonedMetrics);

        // Clone RowFormula structures
        List<RowFormula> clonedFormulas = new ArrayList<>();
        for (RowFormula formula : sourceFormulas) {
            RowFormula clonedFormula = RowFormula.builder()
                .reportId(clonedParent.getReportId())
                .version(1)
                .rowId(formula.getRowId())
                .formulaExpr(formula.getFormulaExpr())
                .build();
            clonedFormulas.add(clonedFormula);
        }
        rowFormulaRepository.saveAll(clonedFormulas);

        // Clone RowColumnMap structures
        List<RowColumnMap> clonedMaps = new ArrayList<>();
        for (RowColumnMap map : sourceMaps) {
            RowColumnMap clonedMap = RowColumnMap.builder()
                .reportId(clonedParent.getReportId())
                .version(1)
                .rowId(map.getRowId())
                .colId(map.getColId())
                .isEnabled(map.getIsEnabled())
                .build();
            clonedMaps.add(clonedMap);
        }
        rowColumnMapRepository.saveAll(clonedMaps);

        log.info("Successfully cloned report layout into '{}' (ID: {})", clonedParent.getReportName(), clonedParent.getReportId());
        return clonedParent;
    }

    private String sanitizeToReportId(String name) {
        if (name == null || name.isBlank()) {
            return "CLONED_REPORT";
        }
        String sanitized = name.toUpperCase()
            .replaceAll("[^A-Z0-9_\\s-]", "")
            .replaceAll("[\\s-]+", "_")
            .replaceAll("_{2,}", "_");

        if (sanitized.startsWith("_")) {
            sanitized = sanitized.substring(1);
        }
        if (sanitized.endsWith("_")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        if (sanitized.isBlank()) {
            return "CLONED_REPORT";
        }
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }
        return sanitized;
    }
}
