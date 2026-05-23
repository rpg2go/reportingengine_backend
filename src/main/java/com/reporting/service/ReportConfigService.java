package com.reporting.service;

import com.reporting.domain.*;
import com.reporting.dto.*;
import com.reporting.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ReportConfigService {

    private final ReportRepository reportRepository;
    private final ColumnDefRepository columnDefRepository;
    private final ReportRowRepository reportRowRepository;
    private final RowMetricRepository rowMetricRepository;
    private final RowFormulaRepository rowFormulaRepository;
    private final RowColumnMapRepository rowColumnMapRepository;
    private final StyleRepository styleRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public ReportConfigService(ReportRepository reportRepository,
                               ColumnDefRepository columnDefRepository,
                               ReportRowRepository reportRowRepository,
                               RowMetricRepository rowMetricRepository,
                               RowFormulaRepository rowFormulaRepository,
                               RowColumnMapRepository rowColumnMapRepository,
                               StyleRepository styleRepository,
                               org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        this.reportRepository = reportRepository;
        this.columnDefRepository = columnDefRepository;
        this.reportRowRepository = reportRowRepository;
        this.rowMetricRepository = rowMetricRepository;
        this.rowFormulaRepository = rowFormulaRepository;
        this.rowColumnMapRepository = rowColumnMapRepository;
        this.styleRepository = styleRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public ReportConfigDto loadFromDb(String reportId, LocalDate referenceDate) {
        Report report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        // 1. Columns
        List<ColumnDef> dbCols = columnDefRepository.findByReportReportIdOrderByDisplayOrderAsc(reportId);
        List<ColumnDefDto> columns = dbCols.stream()
            .map(c -> new ColumnDefDto(
                c.getColId(),
                c.getLabel(),
                Enums.ColType.valueOf(c.getColType().toUpperCase()),
                c.getPeriodOffset() != null ? c.getPeriodOffset() : 0,
                c.getRollingN(),
                c.getFormulaExpr(),
                c.getDisplayOrder()
            )).collect(Collectors.toList());

        // 2. Map row metrics to their logical measure name
        List<RowMetric> dbMetrics = rowMetricRepository.findByReportId(reportId);
        Map<String, String> measureNamesByRow = new HashMap<>();
        if (!dbMetrics.isEmpty()) {
            List<Integer> measureIds = dbMetrics.stream().map(RowMetric::getMeasureId).toList();
            String sql = "SELECT measure_id, name FROM reporting.sem_measure WHERE measure_id IN (" +
                measureIds.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
            
            jdbcTemplate.query(sql, rs -> {
                int mid = rs.getInt("measure_id");
                String name = rs.getString("name");
                for (RowMetric rm : dbMetrics) {
                    if (rm.getMeasureId() == mid) {
                        measureNamesByRow.put(rm.getRowId().toUpperCase(), name);
                    }
                }
            });
        }

        // 3. Map row formulas
        List<RowFormula> dbFormulas = rowFormulaRepository.findByReportId(reportId);
        Map<String, String> formulasByRow = dbFormulas.stream()
            .collect(Collectors.toMap(
                f -> f.getRowId().toUpperCase(),
                RowFormula::getFormulaExpr
            ));

        // 4. Map active columns
        List<RowColumnMap> dbColMaps = rowColumnMapRepository.findByReportId(reportId);
        Map<String, Set<String>> activeColsByRow = new HashMap<>();
        for (RowColumnMap rcm : dbColMaps) {
            if (rcm.getIsEnabled()) {
                activeColsByRow.computeIfAbsent(rcm.getRowId().toUpperCase(), k -> new HashSet<>())
                    .add(rcm.getColId().toUpperCase());
            }
        }

        // 5. Styles map
        List<Style> dbStyles = styleRepository.findAll();
        Map<Integer, String> styleNameMap = dbStyles.stream()
            .collect(Collectors.toMap(Style::getStyleId, Style::getName));

        // 6. Rows
        List<ReportRow> dbRows = reportRowRepository.findByReportIdOrderByDisplayOrderAsc(reportId);
        List<ReportRowDto> rows = dbRows.stream()
            .map(r -> {
                String rid = r.getRowId().toUpperCase();
                
                String source = "";
                if (r.getRowType().equalsIgnoreCase("data")) {
                    source = measureNamesByRow.getOrDefault(rid, "");
                } else if (r.getRowType().equalsIgnoreCase("calc")) {
                    source = formulasByRow.getOrDefault(rid, "");
                }

                String styleName = r.getStyleId() != null ? styleNameMap.getOrDefault(r.getStyleId(), "normal") : "normal";

                return new ReportRowDto(
                    r.getRowId(),
                    r.getReportId(),
                    r.getLabel(),
                    Enums.RowType.valueOf(r.getRowType().toLowerCase()),
                    source,
                    r.getParentRowId(),
                    styleName,
                    r.getIndentLevel() != null ? r.getIndentLevel() : 0,
                    r.getDisplayOrder(),
                    activeColsByRow.getOrDefault(rid, Collections.emptySet()),
                    r.getFilterExpr()
                );
            }).collect(Collectors.toList());

        return new ReportConfigDto(
            report.getReportId(),
            report.getName(),
            columns,
            rows,
            referenceDate,
            report.getExploreId(),
            Enums.ReportStatus.valueOf(report.getStatus().toLowerCase())
        );
    }
}
