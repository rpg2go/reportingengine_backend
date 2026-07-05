package com.reporting.repository;

import com.reporting.domain.Report;
import com.reporting.domain.ReportPk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, ReportPk> {

    Optional<Report> findFirstByReportIdAndDeletedFalseOrderByVersionDesc(String reportId);

    List<Report> findByReportIdAndDeletedFalseOrderByVersionDesc(String reportId);

    @Query("SELECT r FROM Report r WHERE r.reportId = :reportId AND r.version = :version AND r.deleted = false")
    Optional<Report> findByReportIdAndVersion(@Param("reportId") String reportId, @Param("version") Integer version);

    /**
     * Returns the highest-version row per report, regardless of status.
     * Used internally; prefer {@link #findLatestPublishedPerReport()} for catalog views.
     */
    @Query("SELECT r FROM Report r WHERE r.deleted = false AND r.version = (SELECT MAX(r2.version) FROM Report r2 WHERE r2.reportId = r.reportId AND r2.deleted = false) ORDER BY r.reportId ASC")
    List<Report> findLatestVersionPerReport();

    /**
     * Returns the latest {@code published} or {@code in_review} version per report for the
     * catalog list view. Falls back to the latest draft if no published version exists yet
     * (i.e. the report has never been published).
     *
     * <p>This prevents the catalog from flipping back to {@code draft} immediately after
     * a publish+auto-fork, because auto-fork creates a new draft at version+1 which would
     * otherwise become the MAX(version) row returned by {@link #findLatestVersionPerReport()}.</p>
     */
    @Query("""
        SELECT r FROM Report r
        WHERE r.deleted = false AND r.version = (
            SELECT COALESCE(
                MAX(CASE WHEN r2.status IN ('published', 'in_review') THEN r2.version ELSE NULL END),
                MAX(r2.version)
            )
            FROM Report r2 WHERE r2.reportId = r.reportId AND r2.deleted = false
        )
        ORDER BY r.reportId ASC
        """)
    List<Report> findLatestPublishedPerReport();

    /**
     * Returns any active draft versions that sit on top of a published version for
     * the given set of report IDs. Used by the catalog endpoint to surface a
     * "draft in progress" badge alongside the published row.
     *
     * @param reportIds the report IDs to check
     * @return list of draft {@link Report} rows where a higher-version draft exists
     *         beyond the latest published version
     */
    @Query("""
        SELECT r FROM Report r
        WHERE r.reportId IN :reportIds
          AND r.deleted = false
          AND r.status = 'draft'
          AND r.version > (
              SELECT COALESCE(MAX(r2.version), 0)
              FROM Report r2
              WHERE r2.reportId = r.reportId AND r2.status = 'published' AND r2.deleted = false
          )
        ORDER BY r.reportId ASC, r.version DESC
        """)
    List<Report> findActiveDraftsForReports(@Param("reportIds") List<String> reportIds);
}

