package com.reporting.repository;

import com.reporting.domain.ColumnDef;
import com.reporting.domain.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ColumnDefRepository extends JpaRepository<ColumnDef, Integer> {
    List<ColumnDef> findByReportReportIdOrderByDisplayOrderAsc(String reportId);
    void deleteByReportReportId(String reportId);
}
