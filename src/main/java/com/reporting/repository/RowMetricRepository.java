package com.reporting.repository;

import com.reporting.domain.RowMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RowMetricRepository extends JpaRepository<RowMetric, Integer> {
    List<RowMetric> findByReportId(String reportId);
    void deleteByReportId(String reportId);
}
