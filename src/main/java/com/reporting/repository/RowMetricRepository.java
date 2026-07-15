package com.reporting.repository;

import com.reporting.domain.RowMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RowMetricRepository extends JpaRepository<RowMetric, Integer> {
    List<RowMetric> findByReportId(String reportId);
    List<RowMetric> findByReportIdAndVersion(String reportId, Integer version);
    void deleteByReportId(String reportId);
}
