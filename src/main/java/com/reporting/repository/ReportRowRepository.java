package com.reporting.repository;

import com.reporting.domain.ReportRow;
import com.reporting.domain.ReportRowId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReportRowRepository extends JpaRepository<ReportRow, ReportRowId> {
    List<ReportRow> findByReportIdOrderByDisplayOrderAsc(String reportId);
    void deleteByReportId(String reportId);
}
