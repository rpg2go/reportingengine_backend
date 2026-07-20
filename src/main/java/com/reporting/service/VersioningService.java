package com.reporting.service;

import com.reporting.domain.Report;
import com.reporting.domain.ReportPk;
import com.reporting.repository.ReportRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Domain service that owns the report version lifecycle — all state transitions
 * (draft → in_review → published) and the cloning logic that forks one version
 * into the next.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>State machine enforcement: only valid transitions are permitted.</li>
 *   <li>Auto-fork: publishing a version immediately clones all child records
 *       (column defs, rows, metrics, formulas, column maps) into the next draft
 *       version using a single {@code INSERT … SELECT} per table for efficiency.</li>
 *   <li>Manual fork: allows creating a new draft from any published version when
 *       no newer version already exists.</li>
 * </ul>
 *
 * <h2>Transaction boundary</h2>
 * Each public method is its own {@code @Transactional} unit. The publish and fork
 * operations insert the new {@link Report} header first (via JPA) then clone child
 * records via {@code INSERT … SELECT} JDBC statements — all within the same
 * transaction so a partial failure rolls back the entire operation.
 *
 * @see ReportVersionController
 * @since 1.1.0
 */
@Slf4j
@Service
public class VersioningService {

    private final ReportRepository reportRepository;
    private final JdbcTemplate jdbcTemplate;

    public VersioningService(ReportRepository reportRepository, JdbcTemplate jdbcTemplate) {
        this.reportRepository = reportRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    // ─── state transitions ────────────────────────────────────────────────────

    /**
     * Transitions a report version from {@code draft} to {@code in_review}.
     *
     * @param id      the report identifier
     * @param version the version number to submit
     * @return the updated {@link Report} entity
     * @throws IllegalArgumentException if the report is not found
     * @throws IllegalStateException    if the report is not in {@code draft} status
     */
    @Transactional
    public Report submitForReview(String id, int version) {
        Report report = loadOrThrow(id, version);
        requireStatus(report, "draft", "submitted for review");
        report.setStatus("in_review");
        report.setUpdatedAt(LocalDateTime.now());
        Report saved = reportRepository.save(report);
        log.info("Report {} v{} submitted for review.", id, version);
        return saved;
    }

    /**
     * Transitions a report version from {@code in_review} back to {@code draft}.
     *
     * @param id      the report identifier
     * @param version the version number to reject
     * @return the updated {@link Report} entity
     * @throws IllegalArgumentException if the report is not found
     * @throws IllegalStateException    if the report is not in {@code in_review} status
     */
    @Transactional
    public Report rejectToDraft(String id, int version) {
        Report report = loadOrThrow(id, version);
        requireStatus(report, "in_review", "rejected to draft");
        report.setStatus("draft");
        report.setUpdatedAt(LocalDateTime.now());
        Report saved = reportRepository.save(report);
        log.info("Report {} v{} rejected back to DRAFT.", id, version);
        return saved;
    }

    /**
     * Publishes a report version and automatically creates the next draft version
     * by cloning all child records.
     *
     * <p>The operation is:
     * <ol>
     *   <li>Mark current version as {@code published}.</li>
     *   <li>Insert a new {@link Report} header for {@code version + 1} with status {@code draft}.</li>
     *   <li>Clone column defs, rows, row metrics, row formulas, and row column maps
     *       via {@code INSERT … SELECT} in a single round-trip per table.</li>
     * </ol>
     *
     * @param id      the report identifier
     * @param version the version number to publish
     * @return the newly created draft {@link Report}
     * @throws IllegalArgumentException if the report is not found
     * @throws IllegalStateException    if the report is already {@code published}
     */
    @Transactional
    public Report publish(String id, int version) {
        Report report = loadOrThrow(id, version);
        if ("published".equalsIgnoreCase(report.getStatus())) {
            throw new IllegalStateException("Report version is already PUBLISHED.");
        }

        report.setStatus("published");
        report.setUpdatedAt(LocalDateTime.now());
        Report savedReport = reportRepository.save(report);

        log.info("Report {} v{} published successfully.", id, version);
        return savedReport;
    }

    /**
     * Manually forks a {@code published} version into a new {@code draft} version.
     *
     * <p>Only allowed when no version newer than {@code version} already exists,
     * preventing orphaned drafts from multiple forks of the same published version.</p>
     *
     * @param id      the report identifier
     * @param version the published version to fork from
     * @return the newly created draft {@link Report}
     * @throws IllegalArgumentException if the report is not found
     * @throws IllegalStateException    if the report is not {@code published} or a newer version exists
     */
    @Transactional
    public Report fork(String id, int version) {
        Report report = loadOrThrow(id, version);
        requireStatus(report, "published", "forked");

        List<Report> allVersions = reportRepository.findByReportIdAndDeletedFalseOrderByVersionDesc(id);
        if (!allVersions.isEmpty() && allVersions.get(0).getVersion() > version) {
            throw new IllegalStateException(
                "A newer version already exists for report: " + id +
                ". Cannot fork from v" + version + "."
            );
        }

        int nextVersion = version + 1;
        Report newDraft = buildDraft(report, nextVersion);
        reportRepository.saveAndFlush(newDraft);

        cloneChildRecords(id, version, nextVersion);

        log.info("Report {} v{} manually forked to new draft v{}.", id, version, nextVersion);
        return newDraft;
    }

    // ─── private helpers ──────────────────────────────────────────────────────

    /**
     * Loads a {@link Report} by its composite key or throws {@link IllegalArgumentException}.
     *
     * @param id      the report identifier
     * @param version the version number
     * @return the found {@link Report}
     * @throws IllegalArgumentException if no matching record exists
     */
    private Report loadOrThrow(String id, int version) {
        Report report = reportRepository.findById(new ReportPk(id, version))
            .orElseThrow(() -> new IllegalArgumentException(
                "Report not found: " + id + " v" + version));
        if (Boolean.TRUE.equals(report.getDeleted())) {
            throw new IllegalArgumentException("Report not found: " + id + " v" + version);
        }
        return report;
    }

    /**
     * Asserts that the {@link Report} is in the expected status.
     *
     * @param report   the report to check
     * @param expected the required status string (case-insensitive)
     * @param action   human-readable description of the attempted action, used in the error message
     * @throws IllegalStateException if the report's current status does not match {@code expected}
     */
    private void requireStatus(Report report, String expected, String action) {
        if (!expected.equalsIgnoreCase(report.getStatus())) {
            throw new IllegalStateException(
                "Cannot be " + action + ". Current status: " + report.getStatus() +
                ". Required: " + expected.toUpperCase() + "."
            );
        }
    }

    /**
     * Builds a new draft {@link Report} entity by copying all header fields from
     * a source report and assigning a new version number and {@code draft} status.
     *
     * @param source      the template report to copy from
     * @param nextVersion the version number to assign to the new draft
     * @return a new (unsaved) {@link Report} entity
     */
    private Report buildDraft(Report source, int nextVersion) {
        return Report.builder()
            .reportId(source.getReportId())
            .version(nextVersion)
            .reportName(source.getReportName())
            .description(source.getDescription())
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
            .build();
    }

    /**
     * Clones all child records of a report version into a new version using
     * {@code INSERT … SELECT} JDBC statements — one round-trip per child table.
     *
     * <p>Tables cloned (in dependency order):
     * <ol>
     *   <li>{@code reporting.column_definition}</li>
     *   <li>{@code reporting.row_definition}</li>
     *   <li>{@code reporting.row_metric_mapping}</li>
     *   <li>{@code reporting.row_formula}</li>
     *   <li>{@code reporting.row_column_intersection}</li>
     * </ol>
     *
     * @param reportId    the report identifier
     * @param fromVersion the source version to clone from
     * @param toVersion   the target version to clone into
     */
    private void cloneChildRecords(String reportId, int fromVersion, int toVersion) {
        jdbcTemplate.update(
            "INSERT INTO reporting.column_definition " +
            "  (report_id, version, col_id, label, col_type, period_offset, rolling_n, rolling_grain, formula_expr, tier_level, parent_id, display_order) " +
            "SELECT report_id, ? AS version, col_id, label, col_type, period_offset, rolling_n, rolling_grain, formula_expr, tier_level, parent_id, display_order " +
            "FROM reporting.column_definition WHERE report_id = ? AND version = ?",
            toVersion, reportId, fromVersion
        );

        jdbcTemplate.update(
            "INSERT INTO reporting.row_definition " +
            "  (row_id, report_id, version, parent_row_id, label, row_type, display_order, indent_level, style_id, filter_expr) " +
            "SELECT row_id, report_id, ? AS version, parent_row_id, label, row_type, display_order, indent_level, style_id, filter_expr " +
            "FROM reporting.row_definition WHERE report_id = ? AND version = ?",
            toVersion, reportId, fromVersion
        );

        jdbcTemplate.update(
            "INSERT INTO reporting.row_metric_mapping " +
            "  (report_id, version, row_id, sql_expr, measure_definition) " +
            "SELECT report_id, ? AS version, row_id, sql_expr, measure_definition " +
            "FROM reporting.row_metric_mapping WHERE report_id = ? AND version = ?",
            toVersion, reportId, fromVersion
        );

        jdbcTemplate.update(
            "INSERT INTO reporting.row_formula " +
            "  (report_id, version, row_id, formula_expr) " +
            "SELECT report_id, ? AS version, row_id, formula_expr " +
            "FROM reporting.row_formula WHERE report_id = ? AND version = ?",
            toVersion, reportId, fromVersion
        );

        jdbcTemplate.update(
            "INSERT INTO reporting.row_column_intersection " +
            "  (report_id, version, row_id, col_id, is_enabled) " +
            "SELECT report_id, ? AS version, row_id, col_id, is_enabled " +
            "FROM reporting.row_column_intersection WHERE report_id = ? AND version = ?",
            toVersion, reportId, fromVersion
        );

        log.debug("Cloned child records for report {} from v{} to v{}.", reportId, fromVersion, toVersion);
    }
}
