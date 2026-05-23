package com.reporting.repository;

import com.reporting.domain.RowColumnMap;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RowColumnMapRepository extends JpaRepository<RowColumnMap, Integer> {
    List<RowColumnMap> findByReportId(String reportId);
    void deleteByReportId(String reportId);
}
