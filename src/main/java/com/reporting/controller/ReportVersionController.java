package com.reporting.controller;

import com.reporting.domain.Report;
import com.reporting.domain.ReportPk;
import com.reporting.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/reports/{id}/version")
@RequiredArgsConstructor
public class ReportVersionController {

    private final ReportRepository reportRepository;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/list")
    public ResponseEntity<List<Report>> listVersions(@PathVariable("id") String id) {
        return ResponseEntity.ok(reportRepository.findByReportIdOrderByVersionDesc(id));
    }

    @PostMapping("/submit-review")
    @Transactional
    public ResponseEntity<?> submitForReview(
            @PathVariable("id") String id,
            @RequestParam("version") Integer version) {
        Report report = reportRepository.findById(new ReportPk(id, version))
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + id + " v" + version));

        if (!"draft".equalsIgnoreCase(report.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only DRAFT reports can be submitted for review. Current status: " + report.getStatus()));
        }

        report.setStatus("in_review");
        report.setUpdatedAt(LocalDateTime.now());
        reportRepository.save(report);

        log.info("Report {} v{} submitted for review.", id, version);
        return ResponseEntity.ok(Map.of("message", "Report submitted for review successfully.", "status", "in_review"));
    }

    @PostMapping("/reject")
    @Transactional
    public ResponseEntity<?> rejectToDraft(
            @PathVariable("id") String id,
            @RequestParam("version") Integer version) {
        Report report = reportRepository.findById(new ReportPk(id, version))
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + id + " v" + version));

        if (!"in_review".equalsIgnoreCase(report.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only IN_REVIEW reports can be rejected. Current status: " + report.getStatus()));
        }

        report.setStatus("draft");
        report.setUpdatedAt(LocalDateTime.now());
        reportRepository.save(report);

        log.info("Report {} v{} rejected back to DRAFT.", id, version);
        return ResponseEntity.ok(Map.of("message", "Report rejected back to draft successfully.", "status", "draft"));
    }

    @PostMapping("/publish")
    @Transactional
    public ResponseEntity<?> publish(
            @PathVariable("id") String id,
            @RequestParam("version") Integer version) {
        Report report = reportRepository.findById(new ReportPk(id, version))
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + id + " v" + version));

        if ("published".equalsIgnoreCase(report.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Report version is already PUBLISHED."));
        }

        // 1. Freeze and lock current row permanently
        report.setStatus("published");
        report.setUpdatedAt(LocalDateTime.now());
        reportRepository.save(report);

        // 2. Automatically fork and clone to a new DRAFT row with version + 1
        int nextVersion = version + 1;
        
        Report newDraft = Report.builder()
                .reportId(id)
                .version(nextVersion)
                .name(report.getName())
                .description(report.getDescription())
                .exploreId(report.getExploreId())
                .status("draft")
                .granularity(report.getGranularity())
                .timeframeStart(report.getTimeframeStart())
                .timeframeEnd(report.getTimeframeEnd())
                .timeframeToday(report.getTimeframeToday())
                .quickFilters(report.getQuickFilters())
                .generalFilters(report.getGeneralFilters())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        reportRepository.saveAndFlush(newDraft);

        // 3. Clone child configurations (columns, rows, metrics, formulas, column map)
        // Clone Column Definitions
        jdbcTemplate.update(
            "INSERT INTO reporting.rpt_column_def (report_id, version, col_id, label, col_type, period_offset, rolling_n, rolling_grain, formula_expr, display_order) " +
            "SELECT report_id, ? AS version, col_id, label, col_type, period_offset, rolling_n, rolling_grain, formula_expr, display_order " +
            "FROM reporting.rpt_column_def WHERE report_id = ? AND version = ?",
            nextVersion, id, version
        );

        // Clone Rows
        jdbcTemplate.update(
            "INSERT INTO reporting.rpt_row (row_id, report_id, version, parent_row_id, label, row_type, display_order, indent_level, style_id, filter_expr) " +
            "SELECT row_id, report_id, ? AS version, parent_row_id, label, row_type, display_order, indent_level, style_id, filter_expr " +
            "FROM reporting.rpt_row WHERE report_id = ? AND version = ?",
            nextVersion, id, version
        );

        // Clone Row Metrics
        jdbcTemplate.update(
            "INSERT INTO reporting.rpt_row_metric (report_id, version, row_id, measure_id, explore_id, sql_expr, measure_definition) " +
            "SELECT report_id, ? AS version, row_id, measure_id, explore_id, sql_expr, measure_definition " +
            "FROM reporting.rpt_row_metric WHERE report_id = ? AND version = ?",
            nextVersion, id, version
        );

        // Clone Row Formulas
        jdbcTemplate.update(
            "INSERT INTO reporting.rpt_row_formula (report_id, version, row_id, formula_expr) " +
            "SELECT report_id, ? AS version, row_id, formula_expr " +
            "FROM reporting.rpt_row_formula WHERE report_id = ? AND version = ?",
            nextVersion, id, version
        );

        // Clone Row Column Maps
        jdbcTemplate.update(
            "INSERT INTO reporting.rpt_row_column_map (report_id, version, row_id, col_id, is_enabled) " +
            "SELECT report_id, ? AS version, row_id, col_id, is_enabled " +
            "FROM reporting.rpt_row_column_map WHERE report_id = ? AND version = ?",
            nextVersion, id, version
        );

        log.info("Report {} v{} published. Created new draft v{}.", id, version, nextVersion);
        return ResponseEntity.ok(Map.of(
                "message", "Report published successfully. New draft v" + nextVersion + " created.",
                "publishedVersion", version,
                "nextDraftVersion", nextVersion
        ));
    }

    @PostMapping("/fork")
    @Transactional
    public ResponseEntity<?> fork(
            @PathVariable("id") String id,
            @RequestParam("version") Integer version) {
        Report report = reportRepository.findById(new ReportPk(id, version))
                .orElseThrow(() -> new IllegalArgumentException("Report not found: " + id + " v" + version));

        if (!"published".equalsIgnoreCase(report.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Only PUBLISHED reports can be forked."));
        }

        // Check if a draft or higher version already exists to prevent duplicate forks
        List<Report> allVersions = reportRepository.findByReportIdOrderByVersionDesc(id);
        if (!allVersions.isEmpty() && allVersions.get(0).getVersion() > version) {
            return ResponseEntity.badRequest().body(Map.of("message", "A newer version already exists for report: " + id));
        }

        int nextVersion = version + 1;
        
        Report newDraft = Report.builder()
                .reportId(id)
                .version(nextVersion)
                .name(report.getName())
                .description(report.getDescription())
                .exploreId(report.getExploreId())
                .status("draft")
                .granularity(report.getGranularity())
                .timeframeStart(report.getTimeframeStart())
                .timeframeEnd(report.getTimeframeEnd())
                .timeframeToday(report.getTimeframeToday())
                .quickFilters(report.getQuickFilters())
                .generalFilters(report.getGeneralFilters())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        reportRepository.saveAndFlush(newDraft);

        // Clone Columns
        jdbcTemplate.update(
            "INSERT INTO reporting.rpt_column_def (report_id, version, col_id, label, col_type, period_offset, rolling_n, rolling_grain, formula_expr, display_order) " +
            "SELECT report_id, ? AS version, col_id, label, col_type, period_offset, rolling_n, rolling_grain, formula_expr, display_order " +
            "FROM reporting.rpt_column_def WHERE report_id = ? AND version = ?",
            nextVersion, id, version
        );

        // Clone Rows
        jdbcTemplate.update(
            "INSERT INTO reporting.rpt_row (row_id, report_id, version, parent_row_id, label, row_type, display_order, indent_level, style_id, filter_expr) " +
            "SELECT row_id, report_id, ? AS version, parent_row_id, label, row_type, display_order, indent_level, style_id, filter_expr " +
            "FROM reporting.rpt_row WHERE report_id = ? AND version = ?",
            nextVersion, id, version
        );

        // Clone Row Metrics
        jdbcTemplate.update(
            "INSERT INTO reporting.rpt_row_metric (report_id, version, row_id, explore_id, sql_expr, measure_definition) " +
            "SELECT report_id, ? AS version, row_id, explore_id, sql_expr, measure_definition " +
            "FROM reporting.rpt_row_metric WHERE report_id = ? AND version = ?",
            nextVersion, id, version
        );

        // Clone Row Formulas
        jdbcTemplate.update(
            "INSERT INTO reporting.rpt_row_formula (report_id, version, row_id, formula_expr) " +
            "SELECT report_id, ? AS version, row_id, formula_expr " +
            "FROM reporting.rpt_row_formula WHERE report_id = ? AND version = ?",
            nextVersion, id, version
        );

        // Clone Row Column Maps
        jdbcTemplate.update(
            "INSERT INTO reporting.rpt_row_column_map (report_id, version, row_id, col_id, is_enabled) " +
            "SELECT report_id, ? AS version, row_id, col_id, is_enabled " +
            "FROM reporting.rpt_row_column_map WHERE report_id = ? AND version = ?",
            nextVersion, id, version
        );

        log.info("Report {} v{} manually forked to new draft v{}.", id, version, nextVersion);
        return ResponseEntity.ok(Map.of(
                "message", "New draft version v" + nextVersion + " created successfully.",
                "nextDraftVersion", nextVersion
        ));
    }
}
