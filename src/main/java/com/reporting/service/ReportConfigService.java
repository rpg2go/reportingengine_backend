package com.reporting.service;

import com.reporting.domain.*;
import com.reporting.dto.*;
import com.reporting.repository.*;
import org.springframework.jdbc.core.RowCallbackHandler;
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
        // ── Single-pass optimized load using direct JDBC queries ────────────────────
        // Replaces 6+ sequential JPA repository calls with 4 targeted JDBC queries,
        // drastically reducing DB round-trips and JPA entity-hydration overhead.

        // 1. Report header (JPA — single PK lookup, fast)
        Report report = reportRepository.findById(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));

        // 2. Columns — direct JDBC avoids JPA JOIN on parent Report entity
        List<ColumnDefDto> columns = jdbcTemplate.query(
            "SELECT col_id, label, col_type, COALESCE(period_offset,0) AS period_offset, " +
            "rolling_n, formula_expr, display_order " +
            "FROM reporting.rpt_column_def WHERE report_id = ? ORDER BY display_order",
            (rs, rowNum) -> new ColumnDefDto(
                rs.getString("col_id"),
                rs.getString("label"),
                Enums.ColType.valueOf(rs.getString("col_type").toUpperCase()),
                rs.getInt("period_offset"),
                (Integer) rs.getObject("rolling_n"),
                rs.getString("formula_expr"),
                rs.getInt("display_order")
            ), reportId);

        // 3. Row metrics — sql_expr or fallback to measure name
        Map<String, String> measureNamesByRow = new HashMap<>();
        jdbcTemplate.query(
            "SELECT rm.row_id, COALESCE(rm.sql_expr, sm.name, '') AS source " +
            "FROM reporting.rpt_row_metric rm " +
            "LEFT JOIN reporting.sem_measure sm ON sm.measure_id = rm.measure_id " +
            "WHERE rm.report_id = ?",
            (RowCallbackHandler) rs -> measureNamesByRow.put(rs.getString("row_id").toUpperCase(), rs.getString("source")),
            reportId);

        // 4. Row formulas
        Map<String, String> formulasByRow = new HashMap<>();
        jdbcTemplate.query(
            "SELECT row_id, formula_expr FROM reporting.rpt_row_formula WHERE report_id = ?",
            (RowCallbackHandler) rs -> formulasByRow.put(rs.getString("row_id").toUpperCase(), rs.getString("formula_expr")),
            reportId);

        // 5. Active column flags (row × col mapping) — single query
        Map<String, Set<String>> activeColsByRow = new HashMap<>();
        jdbcTemplate.query(
            "SELECT row_id, col_id FROM reporting.rpt_row_column_map " +
            "WHERE report_id = ? AND is_enabled = TRUE",
            (RowCallbackHandler) rs -> activeColsByRow
                .computeIfAbsent(rs.getString("row_id").toUpperCase(), k -> new HashSet<>())
                .add(rs.getString("col_id").toUpperCase()),
            reportId);

        // 6. Style name map — tiny table, single query
        Map<Integer, String> styleNameMap = new HashMap<>();
        jdbcTemplate.query(
            "SELECT style_id, name FROM reporting.rpt_style",
            (RowCallbackHandler) rs -> styleNameMap.put(rs.getInt("style_id"), rs.getString("name")));

        // 7. Rows joined with style name — single query
        List<ReportRowDto> rows = jdbcTemplate.query(
            "SELECT r.row_id, r.report_id, r.label, r.row_type, r.parent_row_id, " +
            "r.indent_level, r.display_order, r.filter_expr, r.style_id " +
            "FROM reporting.rpt_row r WHERE r.report_id = ? ORDER BY r.display_order",
            (rs, rowNum) -> {
                String rid = rs.getString("row_id").toUpperCase();
                String rowType = rs.getString("row_type");
                String source = "";
                if ("data".equalsIgnoreCase(rowType)) {
                    source = measureNamesByRow.getOrDefault(rid, "");
                } else if ("calc".equalsIgnoreCase(rowType)) {
                    source = formulasByRow.getOrDefault(rid, "");
                }
                Integer styleId = (Integer) rs.getObject("style_id");
                String styleName = styleId != null ? styleNameMap.getOrDefault(styleId, "normal") : "normal";
                return new ReportRowDto(
                    rs.getString("row_id"),
                    rs.getString("report_id"),
                    rs.getString("label"),
                    Enums.RowType.valueOf(rowType.toLowerCase()),
                    source,
                    rs.getString("parent_row_id"),
                    styleName,
                    rs.getInt("indent_level"),
                    rs.getInt("display_order"),
                    activeColsByRow.getOrDefault(rid, Collections.emptySet()),
                    rs.getString("filter_expr")
                );
            }, reportId);

        return new ReportConfigDto(
            report.getReportId(),
            report.getName(),
            columns,
            rows,
            referenceDate,
            report.getExploreId(),
            Enums.ReportStatus.valueOf(report.getStatus().toLowerCase()),
            report.getSourceTable(),
            report.getGranularity(),
            report.getTimeframeStart(),
            report.getTimeframeEnd(),
            report.getTimeframeToday(),
            report.getQuickFilters(),
            report.getGeneralFilters()
        );
    }

    @Transactional
    public void saveToDb(ReportConfigDto dto) {
        String reportId = dto.getReportId();

        // 1. Delete previous configuration cascade to ensure clean overwrite (only delete child tables)
        jdbcTemplate.update("DELETE FROM reporting.rpt_row_column_map WHERE report_id = ?", reportId);
        jdbcTemplate.update("DELETE FROM reporting.rpt_row_formula WHERE report_id = ?", reportId);
        jdbcTemplate.update("DELETE FROM reporting.rpt_row_metric WHERE report_id = ?", reportId);
        jdbcTemplate.update("DELETE FROM reporting.rpt_row WHERE report_id = ?", reportId);
        jdbcTemplate.update("DELETE FROM reporting.rpt_column_def WHERE report_id = ?", reportId);

        // 2. Get standard styles name map
        List<Style> dbStyles = styleRepository.findAll();
        Map<String, Integer> styleIdMap = new HashMap<>();
        for (Style style : dbStyles) {
            styleIdMap.put(style.getName().toLowerCase(), style.getStyleId());
        }

        // 3. Save or Update Report Header (without session deletion issues)
        Report r;
        if (reportRepository.existsById(reportId)) {
            r = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
            r.setName(dto.getName());
            r.setExploreId(dto.getExploreId());
            r.setStatus(dto.getStatus() != null ? dto.getStatus().name() : "draft");
            r.setSourceTable(dto.getSourceTable());
            r.setGranularity(dto.getGranularity());
            r.setTimeframeStart(dto.getTimeframeStart());
            r.setTimeframeEnd(dto.getTimeframeEnd());
            r.setTimeframeToday(dto.getTimeframeToday() != null ? dto.getTimeframeToday() : false);
            r.setQuickFilters(dto.getQuickFilters());
            r.setGeneralFilters(dto.getGeneralFilters());
            r.setUpdatedAt(java.time.LocalDateTime.now());
        } else {
            r = Report.builder()
                .reportId(reportId)
                .name(dto.getName())
                .description("Report defined via UI builder")
                .exploreId(dto.getExploreId())
                .version(1)
                .status(dto.getStatus() != null ? dto.getStatus().name() : "draft")
                .sourceTable(dto.getSourceTable())
                .granularity(dto.getGranularity())
                .timeframeStart(dto.getTimeframeStart())
                .timeframeEnd(dto.getTimeframeEnd())
                .timeframeToday(dto.getTimeframeToday() != null ? dto.getTimeframeToday() : false)
                .quickFilters(dto.getQuickFilters())
                .generalFilters(dto.getGeneralFilters())
                .createdAt(java.time.LocalDateTime.now())
                .updatedAt(java.time.LocalDateTime.now())
                .build();
        }
        r = reportRepository.save(r);

        // 4. Save Column Definitions
        if (dto.getColumns() != null) {
            for (int i = 0; i < dto.getColumns().size(); i++) {
                ColumnDefDto cdDto = dto.getColumns().get(i);
                ColumnDef cd = ColumnDef.builder()
                    .report(r)
                    .colId(cdDto.colId())
                    .label(cdDto.label())
                    .colType(cdDto.colType().name())
                    .periodOffset(cdDto.periodOffset())
                    .rollingN(cdDto.rollingN())
                    .formulaExpr(cdDto.formulaExpr())
                    .displayOrder(i + 1)
                    .build();
                columnDefRepository.save(cd);
            }
        }

        // 5. Save Report Rows
        if (dto.getRows() != null) {
            for (int i = 0; i < dto.getRows().size(); i++) {
                ReportRowDto rrDto = dto.getRows().get(i);
                Integer styleId = styleIdMap.getOrDefault(rrDto.style().toLowerCase(), null);
                if (styleId == null && !dbStyles.isEmpty()) {
                    styleId = styleIdMap.get("normal");
                }

                ReportRow rr = ReportRow.builder()
                    .rowId(rrDto.rowId())
                    .reportId(reportId)
                    .parentRowId(rrDto.parentRowId() != null && !rrDto.parentRowId().isBlank() ? rrDto.parentRowId() : null)
                    .label(rrDto.label())
                    .rowType(rrDto.rowType().name())
                    .displayOrder(i + 1)
                    .indentLevel(rrDto.indentLevel())
                    .styleId(styleId)
                    .filterExpr(rrDto.filterExpr() != null && !rrDto.filterExpr().isBlank() ? rrDto.filterExpr() : null)
                    .build();
                reportRowRepository.save(rr);

                // Row Metric (DATA rows)
                if (rrDto.rowType() == Enums.RowType.data && rrDto.source() != null && !rrDto.source().isBlank()) {
                    RowMetric rm = RowMetric.builder()
                        .reportId(reportId)
                        .rowId(rrDto.rowId())
                        .sqlExpr(rrDto.source()) // directly save custom SQL expression / aggregation
                        .exploreId(dto.getExploreId())
                        .build();
                    rowMetricRepository.save(rm);
                }

                // Row Formula (CALC rows)
                if (rrDto.rowType() == Enums.RowType.calc && rrDto.source() != null && !rrDto.source().isBlank()) {
                    RowFormula rf = RowFormula.builder()
                        .reportId(reportId)
                        .rowId(rrDto.rowId())
                        .formulaExpr(rrDto.source())
                        .build();
                    rowFormulaRepository.save(rf);
                }

                // Column flags mapping (enablement grid)
                if (dto.getColumns() != null) {
                    for (ColumnDefDto col : dto.getColumns()) {
                        boolean isEnabled = rrDto.activeCols() != null && rrDto.activeCols().contains(col.colId().toUpperCase());
                        RowColumnMap rcm = RowColumnMap.builder()
                            .reportId(reportId)
                            .rowId(rrDto.rowId())
                            .colId(col.colId())
                            .isEnabled(isEnabled)
                            .build();
                        rowColumnMapRepository.save(rcm);
                    }
                }
            }
        }
    }
}
