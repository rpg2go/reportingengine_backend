package com.reporting.service;

import com.reporting.cache.MetadataCache;
import com.reporting.domain.*;
import com.reporting.dto.*;
import com.reporting.repository.*;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
public class ReportConfigService {

    private final ReportRepository reportRepository;
    private final StyleRepository styleRepository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    private final MetadataCache metadataCache;

    public ReportConfigService(ReportRepository reportRepository,
                              StyleRepository styleRepository,
                              org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
                              MetadataCache metadataCache) {
        this.reportRepository = reportRepository;
        this.styleRepository = styleRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.metadataCache = metadataCache;
    }

    @Transactional(readOnly = true)
    public ReportConfigDto loadFromDb(String reportId, LocalDate referenceDate) {
        Report report = reportRepository.findFirstByReportIdAndDeletedFalseOrderByVersionDesc(reportId)
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId));
        return loadFromDb(reportId, report.getVersion(), referenceDate);
    }

    @Transactional(readOnly = true)
    public ReportConfigDto loadFromDb(String reportId, Integer version, LocalDate referenceDate) {
        // 1. Report header
        Report report = reportRepository.findById(new ReportPk(reportId, version))
            .orElseThrow(() -> new IllegalArgumentException("Report not found: " + reportId + " v" + version));
        if (Boolean.TRUE.equals(report.getDeleted())) {
            throw new IllegalArgumentException("Report not found: " + reportId + " v" + version);
        }

        // 2. Columns — direct JDBC query filtered by version
        List<ColumnDefDto> columns = jdbcTemplate.query(
            "SELECT col_id, label, col_type, COALESCE(period_offset,0) AS period_offset, " +
            "rolling_n, rolling_grain, formula_expr, COALESCE(tier_level, 'L1') AS tier_level, parent_id, display_order " +
            "FROM reporting.rpt_column_def WHERE report_id = ? AND version = ? ORDER BY display_order",
            (rs, rowNum) -> new ColumnDefDto(
                rs.getString("col_id"),
                rs.getString("label"),
                Enums.ColType.valueOf(rs.getString("col_type").toUpperCase()),
                rs.getInt("period_offset"),
                (Integer) rs.getObject("rolling_n"),
                rs.getString("rolling_grain"),
                rs.getString("formula_expr"),
                rs.getString("tier_level"),
                rs.getString("parent_id"),
                rs.getInt("display_order")
            ), reportId, version);

        // 2.5 Load catalog table references for heuristic table detection
        Set<String> metaTableRefs = new LinkedHashSet<>(metadataCache.getMetaTableRefs());
        if (metaTableRefs.isEmpty()) {
            jdbcTemplate.query(
                "SELECT DISTINCT schema_name || '.' || table_name AS table_ref FROM reporting.meta_table",
                (RowCallbackHandler) rs -> {
                    String tbl = rs.getString("table_ref");
                    if (tbl != null && !tbl.isBlank()) {
                        metaTableRefs.add(tbl.trim());
                    }
                }
            );
        }

        // 3. Row metrics — filtered by version
        Map<String, MeasureDefinitionDTO> measuresByRow = new HashMap<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        jdbcTemplate.query(
            "SELECT rm.row_id, rm.measure_definition, rm.sql_expr " +
            "FROM reporting.rpt_row_metric rm " +
            "WHERE rm.report_id = ? AND rm.version = ?",
            (RowCallbackHandler) rs -> {
                String rid = rs.getString("row_id").toUpperCase();
                String measureDefStr = rs.getString("measure_definition");
                MeasureDefinitionDTO mdef = null;
                if (measureDefStr != null && !measureDefStr.isBlank()) {
                    try {
                        mdef = mapper.readValue(measureDefStr, MeasureDefinitionDTO.class);
                    } catch (Exception e) {
                        // ignore malformed JSON
                    }
                }
                if (mdef == null) {
                    String expr = rs.getString("sql_expr");
                    mdef = MeasureDefinitionDTO.builder()
                        .rawExpression(expr != null ? expr : "")
                        .build();
                }

                // Heuristic: if no source table was set, detect it from the sql_expr
                if (mdef.getTable() == null || mdef.getTable().isBlank()) {
                    String raw = mdef.getRawSql();
                    if (raw != null && !raw.isBlank()) {
                        for (String tbl : metaTableRefs) {
                            String shortTbl = tbl.contains(".") ? tbl.substring(tbl.lastIndexOf(".") + 1) : tbl;
                            String escapedFull  = java.util.regex.Pattern.quote(tbl);
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
            reportId, version);

        // 4. Row formulas
        Map<String, String> formulasByRow = new HashMap<>();
        jdbcTemplate.query(
            "SELECT row_id, formula_expr FROM reporting.rpt_row_formula WHERE report_id = ? AND version = ?",
            (RowCallbackHandler) rs -> formulasByRow.put(rs.getString("row_id").toUpperCase(), rs.getString("formula_expr")),
            reportId, version);

        // 5. Active column flags
        Map<String, Set<String>> activeColsByRow = new HashMap<>();
        jdbcTemplate.query(
            "SELECT row_id, col_id FROM reporting.rpt_row_column_map " +
            "WHERE report_id = ? AND version = ? AND is_enabled = TRUE",
            (RowCallbackHandler) rs -> activeColsByRow
                .computeIfAbsent(rs.getString("row_id").toUpperCase(), k -> new HashSet<>())
                .add(rs.getString("col_id").toUpperCase()),
            reportId, version);

        // 6. Style name map
        Map<Integer, String> styleNameMap = new HashMap<>();
        jdbcTemplate.query(
            "SELECT style_id, name FROM reporting.rpt_style",
            (RowCallbackHandler) rs -> styleNameMap.put(rs.getInt("style_id"), rs.getString("name")));

        // 7. Rows
        List<ReportRowDto> rows = jdbcTemplate.query(
            "SELECT r.row_id, r.report_id, r.label, r.row_type, r.parent_row_id, " +
            "r.indent_level, r.display_order, r.filter_expr, r.style_id " +
            "FROM reporting.rpt_row r WHERE r.report_id = ? AND r.version = ? ORDER BY r.display_order",
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
            }, reportId, version);

        ReportConfigDto dto = new ReportConfigDto(
            report.getReportId(),
            report.getReportName(),
            columns,
            rows,
            referenceDate,
            null, // exploreId (deprecated)
            Enums.ReportStatus.valueOf(report.getStatus().toLowerCase()),
            report.getGranularity(),
            report.getQuickFilters(),
            report.getGeneralFilters()
        );
        dto.setSourceTable(report.getSourceTable());
        dto.setSourceField(report.getSourceField());
        dto.setVersion(report.getVersion());
        dto.setReportingDateType(report.getReportingDateType());
        dto.setReportingDateStatic(report.getReportingDateStatic());
        dto.setReportingDateExpression(report.getReportingDateExpression());
        dto.setTimeframeStartType(report.getTimeframeStartType());
        dto.setTimeframeStartStatic(report.getTimeframeStartStatic());
        dto.setTimeframeStartExpression(report.getTimeframeStartExpression());
        dto.setTimeframeEndType(report.getTimeframeEndType());
        dto.setTimeframeEndStatic(report.getTimeframeEndStatic());
        dto.setTimeframeEndExpression(report.getTimeframeEndExpression());
        return dto;
    }

    @Transactional
    public void saveToDb(ReportConfigDto dto) {
        String reportId = dto.getReportId();
        Integer version = dto.getVersion() != null ? dto.getVersion() : 1;
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        // 1. Delete previous configuration cascade for this specific version only
        jdbcTemplate.update("DELETE FROM reporting.rpt_row_column_map WHERE report_id = ? AND version = ?", reportId, version);
        jdbcTemplate.update("DELETE FROM reporting.rpt_row_formula WHERE report_id = ? AND version = ?", reportId, version);
        jdbcTemplate.update("DELETE FROM reporting.rpt_row_metric WHERE report_id = ? AND version = ?", reportId, version);
        jdbcTemplate.update("DELETE FROM reporting.rpt_row WHERE report_id = ? AND version = ?", reportId, version);
        jdbcTemplate.update("DELETE FROM reporting.rpt_column_def WHERE report_id = ? AND version = ?", reportId, version);

        // 2. Get standard styles name map
        List<Style> dbStyles = styleRepository.findAll();
        Map<String, Integer> styleIdMap = new HashMap<>();
        for (Style style : dbStyles) {
            styleIdMap.put(style.getName().toLowerCase(), style.getStyleId());
        }

        Boolean existsObj = jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM reporting.rpt_report WHERE report_id = ? AND version = ?)",
            Boolean.class,
            reportId,
            version
        );
        boolean exists = existsObj != null && existsObj;

        String incomingStatus = dto.getStatus() != null ? dto.getStatus().name() : "draft";

        // Enforce mutual exclusivity of polymorphic date fields:
        // Clear static date if dynamic mode is active, and vice versa.
        String reportingDateType = dto.getReportingDateType();
        LocalDate reportingDateStatic = null;
        String reportingDateExpression = null;
        if ("FIXED".equalsIgnoreCase(reportingDateType)) {
            reportingDateStatic = dto.getReportingDateStatic();
        } else { // DYNAMIC
            reportingDateExpression = dto.getReportingDateExpression();
        }

        String timeframeStartType = dto.getTimeframeStartType();
        LocalDate timeframeStartStatic = null;
        String timeframeStartExpression = null;
        if ("FIXED".equalsIgnoreCase(timeframeStartType)) {
            timeframeStartStatic = dto.getTimeframeStartStatic();
        } else { // DYNAMIC
            timeframeStartExpression = dto.getTimeframeStartExpression();
        }

        String timeframeEndType = dto.getTimeframeEndType();
        LocalDate timeframeEndStatic = null;
        String timeframeEndExpression = null;
        if ("FIXED".equalsIgnoreCase(timeframeEndType)) {
            timeframeEndStatic = dto.getTimeframeEndStatic();
        } else { // DYNAMIC
            timeframeEndExpression = dto.getTimeframeEndExpression();
        }

        if (exists) {
            Map<String, Object> currentRecord = jdbcTemplate.queryForMap(
                "SELECT status FROM reporting.rpt_report WHERE report_id = ? AND version = ?",
                reportId,
                version
            );
            String currentStatus = (String) currentRecord.get("status");
            if ("published".equalsIgnoreCase(currentStatus)) {
                throw new IllegalStateException("Report " + reportId + " version " + version + " is PUBLISHED and cannot be modified.");
            }

            jdbcTemplate.update(
                "UPDATE reporting.rpt_report SET report_name = ?, status = ?, granularity = ?, " +
                "quick_filters = ?, general_filters = ?, " +
                "source_table = ?, source_field = ?, " +
                "reporting_date_type = ?, reporting_date_static = ?, reporting_date_expression = ?, " +
                "timeframe_start_type = ?, timeframe_start_static = ?, timeframe_start_expression = ?, " +
                "timeframe_end_type = ?, timeframe_end_static = ?, timeframe_end_expression = ?, " +
                "updated_at = NOW() WHERE report_id = ? AND version = ?",
                dto.getReportName(),
                incomingStatus.toLowerCase(),
                dto.getGranularity(),
                dto.getQuickFilters(),
                dto.getGeneralFilters(),
                dto.getSourceTable(),
                dto.getSourceField(),
                reportingDateType,
                reportingDateStatic,
                reportingDateExpression,
                timeframeStartType,
                timeframeStartStatic,
                timeframeStartExpression,
                timeframeEndType,
                timeframeEndStatic,
                timeframeEndExpression,
                reportId,
                version
            );
        } else {
            jdbcTemplate.update(
                "INSERT INTO reporting.rpt_report (report_id, report_name, description, version, status, granularity, " +
                "quick_filters, general_filters, source_table, source_field, " +
                "reporting_date_type, reporting_date_static, reporting_date_expression, " +
                "timeframe_start_type, timeframe_start_static, timeframe_start_expression, " +
                "timeframe_end_type, timeframe_end_static, timeframe_end_expression, " +
                "created_at, updated_at) " +
                "VALUES (?, ?, 'Report defined via UI builder', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                reportId,
                dto.getReportName(),
                version,
                incomingStatus.toLowerCase(),
                dto.getGranularity(),
                dto.getQuickFilters(),
                dto.getGeneralFilters(),
                dto.getSourceTable(),
                dto.getSourceField(),
                reportingDateType,
                reportingDateStatic,
                reportingDateExpression,
                timeframeStartType,
                timeframeStartStatic,
                timeframeStartExpression,
                timeframeEndType,
                timeframeEndStatic,
                timeframeEndExpression
            );
        }

        // 4. Save Column Definitions via JDBC
        if (dto.getColumns() != null) {
            String insertColSql = "INSERT INTO reporting.rpt_column_def (report_id, version, col_id, label, col_type, period_offset, rolling_n, rolling_grain, formula_expr, tier_level, parent_id, display_order) " +
                                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            for (int i = 0; i < dto.getColumns().size(); i++) {
                ColumnDefDto cdDto = dto.getColumns().get(i);
                jdbcTemplate.update(
                    insertColSql,
                    reportId,
                    version,
                    cdDto.colId(),
                    cdDto.label(),
                    cdDto.colType().name(),
                    cdDto.periodOffset(),
                    cdDto.rollingN(),
                    cdDto.rollingGrain(),
                    cdDto.formulaExpr(),
                    cdDto.tierLevel() != null ? cdDto.tierLevel() : "L1",
                    cdDto.parentId(),
                    i + 1
                );
            }
        }

        // 5. Save Report Rows via JDBC
        if (dto.getRows() != null) {
            String insertRowSql = "INSERT INTO reporting.rpt_row (row_id, report_id, version, parent_row_id, label, row_type, display_order, indent_level, style_id, filter_expr) " +
                                  "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            String insertMetricSql = "INSERT INTO reporting.rpt_row_metric (report_id, version, row_id, sql_expr, measure_definition) " +
                                     "VALUES (?, ?, ?, ?, ?)";
            String insertFormulaSql = "INSERT INTO reporting.rpt_row_formula (report_id, version, row_id, formula_expr) " +
                                      "VALUES (?, ?, ?, ?)";
            String insertColMapSql = "INSERT INTO reporting.rpt_row_column_map (report_id, version, row_id, col_id, is_enabled) " +
                                     "VALUES (?, ?, ?, ?, ?)";

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
                    version,
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
                        version,
                        rrDto.rowId(),
                        sqlExpr,
                        defJson
                    );
                }

                // Row Formula (CALC rows)
                if (rrDto.rowType() == Enums.RowType.calc && rrDto.source() != null) {
                    String formulaExpr = rrDto.source().getRawSql() != null ? rrDto.source().getRawSql() : "";
                    jdbcTemplate.update(
                        insertFormulaSql,
                        reportId,
                        version,
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
                            version,
                            rrDto.rowId(),
                            col.colId(),
                            isEnabled
                        );
                    }
                }
            }
        }
    }

    @Transactional
    public void deleteReport(String reportId) {
        Boolean hasPublishedObj = jdbcTemplate.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM reporting.rpt_report WHERE report_id = ? AND status = 'published')",
            Boolean.class,
            reportId
        );
        boolean hasPublished = hasPublishedObj != null && hasPublishedObj;

        if (hasPublished) {
            jdbcTemplate.update("UPDATE reporting.rpt_report SET deleted = true WHERE report_id = ?", reportId);
        } else {
            jdbcTemplate.update("DELETE FROM reporting.rpt_report WHERE report_id = ?", reportId);
        }
    }
}
