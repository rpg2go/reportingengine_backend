package com.reporting.repository;

import com.reporting.domain.RowColumnMap;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RowColumnMapRepository extends JpaRepository<RowColumnMap, Integer> {
    List<RowColumnMap> findByReportId(String reportId);
    void deleteByReportId(String reportId);
}
