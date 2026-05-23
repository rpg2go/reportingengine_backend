package com.reporting.repository;

import com.reporting.domain.ReportRow;
import com.reporting.domain.ReportRowId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportRowRepository extends JpaRepository<ReportRow, ReportRowId> {
    List<ReportRow> findByReportIdOrderByDisplayOrderAsc(String reportId);
    void deleteByReportId(String reportId);
}
