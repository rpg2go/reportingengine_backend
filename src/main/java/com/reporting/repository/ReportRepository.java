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
    
    Optional<Report> findFirstByReportIdOrderByVersionDesc(String reportId);
    
    List<Report> findByReportIdOrderByVersionDesc(String reportId);

    @Query("SELECT r FROM Report r WHERE r.reportId = :reportId AND r.version = :version")
    Optional<Report> findByReportIdAndVersion(@Param("reportId") String reportId, @Param("version") Integer version);

    @Query("SELECT r FROM Report r WHERE r.version = (SELECT MAX(r2.version) FROM Report r2 WHERE r2.reportId = r.reportId) ORDER BY r.reportId ASC")
    List<Report> findLatestVersionPerReport();
}
