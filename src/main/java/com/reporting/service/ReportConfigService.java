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
            "rolling_n, rolling_grain, formula_expr, display_order " +
            "FROM reporting.rpt_column_def WHERE report_id = ? ORDER BY display_order",
            (rs, rowNum) -> new ColumnDefDto(
                rs.getString("col_id"),
                rs.getString("label"),
                Enums.ColType.valueOf(rs.getString("col_type").toUpperCase()),
                rs.getInt("period_offset"),
                (Integer) rs.getObject("rolling_n"),
                rs.getString("rolling_grain"),
                rs.getString("formula_expr"),
                rs.getInt("display_order")
            ), reportId);

        // 3. Row metrics — sql_expr or fallback to measure_definition JSON
        // 2.5 Load semantic measures for translation lookup
        Map<String, SemanticMeasureInfo> semMeasures = new HashMap<>();
        jdbcTemplate.query(
            "SELECT sm.name, sm.sql_expr, sv.table_ref " +
            "FROM reporting.sem_measure sm " +
            "JOIN reporting.sem_view sv ON sv.view_id = sm.view_id",
            (RowCallbackHandler) rs -> {
                String name = rs.getString("name").toLowerCase();
                semMeasures.put(name, new SemanticMeasureInfo(
                    rs.getString("sql_expr"),
                    rs.getString("table_ref")
                ));
            }
        );

        // 2.6 Load semantic view table references for resilient table detection
        Set<String> semViewTables = new LinkedHashSet<>();
        jdbcTemplate.query(
            "SELECT DISTINCT table_ref FROM reporting.sem_view WHERE table_ref IS NOT NULL",
            (RowCallbackHandler) rs -> {
                String tbl = rs.getString("table_ref");
                if (tbl != null && !tbl.isBlank()) {
                    semViewTables.add(tbl.trim());
                }
            }
        );

        // 3. Row metrics — sql_expr or fallback to measure_definition JSON
        Map<String, MeasureDefinitionDTO> measuresByRow = new HashMap<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        jdbcTemplate.query(
            "SELECT rm.row_id, rm.measure_definition, rm.sql_expr, sm.name AS sem_name, sm.sql_expr AS sem_sql_expr, sv.table_ref AS sem_table " +
            "FROM reporting.rpt_row_metric rm " +
            "LEFT JOIN reporting.sem_measure sm ON sm.measure_id = rm.measure_id " +
            "LEFT JOIN reporting.sem_view sv ON sv.view_id = sm.view_id " +
            "WHERE rm.report_id = ?",
            (RowCallbackHandler) rs -> {
                String rid = rs.getString("row_id").toUpperCase();
                String measureDefStr = rs.getString("measure_definition");
                MeasureDefinitionDTO mdef = null;
                if (measureDefStr != null && !measureDefStr.isBlank()) {
                    try {
                        mdef = mapper.readValue(measureDefStr, MeasureDefinitionDTO.class);
                    } catch (Exception e) {
                        // ignore
                    }
                }
                if (mdef == null) {
                    // Fallback to sql_expr or sem_sql_expr or sem_name
                    String expr = rs.getString("sql_expr");
                    if (expr == null || expr.isBlank()) {
                        expr = rs.getString("sem_sql_expr");
                    }
                    if (expr == null || expr.isBlank()) {
                        expr = rs.getString("sem_name");
                    }
                    if (expr == null) {
                        expr = "";
                    }
                    String table = rs.getString("sem_table");
                    mdef = MeasureDefinitionDTO.builder()
                        .rawExpression(expr)
                        .sourceTable(table)
                        .build();
                }

                // Apply fallback translation layer if rawSql matches a semantic measure
                if (mdef.getRawSql() != null) {
                    String cleanSql = mdef.getRawSql().trim().toLowerCase();
                    if (semMeasures.containsKey(cleanSql)) {
                        SemanticMeasureInfo info = semMeasures.get(cleanSql);
                        mdef = MeasureDefinitionDTO.builder()
                            .rawExpression(info.sqlExpr)
                            .sourceTable(info.tableRef)
                            .build();
                    }
                }

                // Resilient Table Detection: If table is null/blank, scan rawSql for known table_refs
                if (mdef.getTable() == null || mdef.getTable().isBlank()) {
                    if (mdef.getRawSql() != null && !mdef.getRawSql().isBlank()) {
                        String raw = mdef.getRawSql();
                        for (String tbl : semViewTables) {
                            String shortTbl = tbl;
                            if (tbl.contains(".")) {
                                shortTbl = tbl.substring(tbl.lastIndexOf(".") + 1);
                            }
                            String escapedFull = java.util.regex.Pattern.quote(tbl);
                            String escapedShort = java.util.regex.Pattern.quote(shortTbl);
                            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                                "\\b(" + escapedFull + "|" + escapedShort + ")\\b",
                                java.util.regex.Pattern.CASE_INSENSITIVE
                            );
                            if (pattern.matcher(raw).find()) {
                                mdef.setTable(tbl);
                                break;
                            }
                        }
                    }
                }

                measuresByRow.put(rid, mdef);
            },
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
                MeasureDefinitionDTO source = null;
                if ("data".equalsIgnoreCase(rowType)) {
                    source = measuresByRow.get(rid);
                } else if ("calc".equalsIgnoreCase(rowType)) {
                    String formula = formulasByRow.getOrDefault(rid, "");
                    source = MeasureDefinitionDTO.builder()
                        .rawExpression(formula)
                        .build();
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

        ReportConfigDto dto = new ReportConfigDto(
            report.getReportId(),
            report.getName(),
            columns,
            rows,
            referenceDate,
            report.getExploreId(),
            Enums.ReportStatus.valueOf(report.getStatus().toLowerCase()),
            report.getGranularity(),
            report.getTimeframeStart(),
            report.getTimeframeEnd(),
            report.getTimeframeToday(),
            report.getQuickFilters(),
            report.getGeneralFilters()
        );
        dto.setVersion(report.getVersion());
        return dto;
    }

    @Transactional
    public void saveToDb(ReportConfigDto dto) {
        String reportId = dto.getReportId();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

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

        // 3. Save or Update Report Header
        boolean exists = jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM reporting.rpt_report WHERE report_id = ?)",
            Boolean.class,
            reportId
        );

        String incomingStatus = dto.getStatus() != null ? dto.getStatus().name() : "draft";
        Integer newVersion = 1;

        if (exists) {
            // Retrieve current version and status from DB
            Map<String, Object> currentRecord = jdbcTemplate.queryForMap(
                "SELECT version, status FROM reporting.rpt_report WHERE report_id = ?",
                reportId
            );
            int currentVersion = ((Number) currentRecord.get("version")).intValue();
            String currentStatus = (String) currentRecord.get("status");

            // Validate status transitions and version integrity
            if ("draft".equalsIgnoreCase(currentStatus) && "published".equalsIgnoreCase(incomingStatus)) {
                if (dto.getVersion() == null || dto.getVersion() != currentVersion + 1) {
                    throw new IllegalArgumentException("Version mismatch. To publish, version must be exactly " + (currentVersion + 1) + " (incoming: " + dto.getVersion() + ").");
                }
                newVersion = dto.getVersion();
            } else {
                newVersion = dto.getVersion() != null ? dto.getVersion() : currentVersion;
            }

            jdbcTemplate.update(
                "UPDATE reporting.rpt_report SET name = ?, explore_id = ?, version = ?, status = ?, granularity = ?, " +
                "timeframe_start = ?, timeframe_end = ?, timeframe_today = ?, quick_filters = ?, general_filters = ?, " +
                "updated_at = NOW() WHERE report_id = ?",
                dto.getName(),
                dto.getExploreId(),
                newVersion,
                incomingStatus,
                dto.getGranularity(),
                dto.getTimeframeStart(),
                dto.getTimeframeEnd(),
                dto.getTimeframeToday() != null ? dto.getTimeframeToday() : false,
                dto.getQuickFilters(),
                dto.getGeneralFilters(),
                reportId
            );
        } else {
            newVersion = dto.getVersion() != null ? dto.getVersion() : 1;
            jdbcTemplate.update(
                "INSERT INTO reporting.rpt_report (report_id, name, description, explore_id, version, status, granularity, " +
                "timeframe_start, timeframe_end, timeframe_today, quick_filters, general_filters, created_at, updated_at) " +
                "VALUES (?, ?, 'Report defined via UI builder', ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                reportId,
                dto.getName(),
                dto.getExploreId(),
                newVersion,
                incomingStatus,
                dto.getGranularity(),
                dto.getTimeframeStart(),
                dto.getTimeframeEnd(),
                dto.getTimeframeToday() != null ? dto.getTimeframeToday() : false,
                dto.getQuickFilters(),
                dto.getGeneralFilters()
            );
        }

        // 4. Save Column Definitions via JDBC
        if (dto.getColumns() != null) {
            String insertColSql = "INSERT INTO reporting.rpt_column_def (report_id, col_id, label, col_type, period_offset, rolling_n, rolling_grain, formula_expr, display_order) " +
                                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            for (int i = 0; i < dto.getColumns().size(); i++) {
                ColumnDefDto cdDto = dto.getColumns().get(i);
                jdbcTemplate.update(
                    insertColSql,
                    reportId,
                    cdDto.colId(),
                    cdDto.label(),
                    cdDto.colType().name(),
                    cdDto.periodOffset(),
                    cdDto.rollingN(),
                    cdDto.rollingGrain(),
                    cdDto.formulaExpr(),
                    i + 1
                );
            }
        }

        // 5. Save Report Rows via JDBC
        if (dto.getRows() != null) {
            String insertRowSql = "INSERT INTO reporting.rpt_row (row_id, report_id, parent_row_id, label, row_type, display_order, indent_level, style_id, filter_expr) " +
                                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            String insertMetricSql = "INSERT INTO reporting.rpt_row_metric (report_id, row_id, sql_expr, measure_definition, explore_id) " +
                                     "VALUES (?, ?, ?, ?, ?)";
            String insertFormulaSql = "INSERT INTO reporting.rpt_row_formula (report_id, row_id, formula_expr) " +
                                      "VALUES (?, ?, ?)";
            String insertColMapSql = "INSERT INTO reporting.rpt_row_column_map (report_id, row_id, col_id, is_enabled) " +
                                     "VALUES (?, ?, ?, ?)";

            for (int i = 0; i < dto.getRows().size(); i++) {
                ReportRowDto rrDto = dto.getRows().get(i);
                Integer styleId = styleIdMap.getOrDefault(rrDto.style().toLowerCase(), null);
                if (styleId == null && !dbStyles.isEmpty()) {
                    styleId = styleIdMap.get("normal");
                }

                String parentRowId = rrDto.parentRowId() != null && !rrDto.parentRowId().isBlank() ? rrDto.parentRowId() : null;
                String filterExpr = rrDto.filterExpr() != null && !rrDto.filterExpr().isBlank() ? rrDto.filterExpr() : null;

                jdbcTemplate.update(
                    insertRowSql,
                    rrDto.rowId(),
                    reportId,
                    parentRowId,
                    rrDto.label(),
                    rrDto.rowType().name(),
                    i + 1,
                    rrDto.indentLevel(),
                    styleId,
                    filterExpr
                );

                // Row Metric (DATA rows)
                if (rrDto.rowType() == Enums.RowType.data && rrDto.source() != null) {
                    String defJson = null;
                    String sqlExpr = "";
                    try {
                        defJson = mapper.writeValueAsString(rrDto.source());
                        if ("visual".equalsIgnoreCase(rrDto.source().getMode())) {
                            String agg = rrDto.source().getAggregation() != null ? rrDto.source().getAggregation().trim().toUpperCase() : "SUM";
                            String col = rrDto.source().getTargetColumn() != null ? rrDto.source().getTargetColumn().trim() : "";
                            sqlExpr = agg + "(" + col + ")";
                        } else {
                            sqlExpr = rrDto.source().getRawSql();
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                    jdbcTemplate.update(
                        insertMetricSql,
                        reportId,
                        rrDto.rowId(),
                        sqlExpr,
                        defJson,
                        dto.getExploreId()
                    );
                }

                // Row Formula (CALC rows)
                if (rrDto.rowType() == Enums.RowType.calc && rrDto.source() != null) {
                    String formulaExpr = rrDto.source().getRawSql() != null ? rrDto.source().getRawSql() : "";
                    jdbcTemplate.update(
                        insertFormulaSql,
                        reportId,
                        rrDto.rowId(),
                        formulaExpr
                    );
                }

                // Column flags mapping (enablement grid)
                if (dto.getColumns() != null) {
                    for (ColumnDefDto col : dto.getColumns()) {
                        boolean isEnabled = rrDto.activeCols() != null && rrDto.activeCols().contains(col.colId().toUpperCase());
                        jdbcTemplate.update(
                            insertColMapSql,
                            reportId,
                            rrDto.rowId(),
                            col.colId(),
                            isEnabled
                        );
                    }
                }
            }
        }
    }

    private static class SemanticMeasureInfo {
        final String sqlExpr;
        final String tableRef;
        SemanticMeasureInfo(String sqlExpr, String tableRef) {
            this.sqlExpr = sqlExpr;
            this.tableRef = tableRef;
        }
    }
}
