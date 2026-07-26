package com.db.reporting.repository;

import com.db.reporting.domain.ReportRow;
import com.db.reporting.domain.ReportRowId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReportRowRepository extends JpaRepository<ReportRow, ReportRowId> {
    List<ReportRow> findByReportIdOrderByDisplayOrderAsc(String reportId);
    List<ReportRow> findByReportIdAndVersionOrderByDisplayOrderAsc(String reportId, Integer version);
    void deleteByReportId(String reportId);
}
