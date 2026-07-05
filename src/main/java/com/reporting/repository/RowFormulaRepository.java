package com.reporting.repository;

import com.reporting.domain.RowFormula;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RowFormulaRepository extends JpaRepository<RowFormula, Integer> {
    List<RowFormula> findByReportId(String reportId);
    void deleteByReportId(String reportId);
}
