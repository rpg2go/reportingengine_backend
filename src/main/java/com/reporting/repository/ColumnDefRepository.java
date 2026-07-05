package com.reporting.repository;

import com.reporting.domain.ColumnDef;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ColumnDefRepository extends JpaRepository<ColumnDef, Integer> {
    List<ColumnDef> findByReportReportIdOrderByDisplayOrderAsc(String reportId);
    void deleteByReportReportId(String reportId);
}
