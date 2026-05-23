package com.reporting.repository;

import com.reporting.domain.RowFormula;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RowFormulaRepository extends JpaRepository<RowFormula, Integer> {
    List<RowFormula> findByReportId(String reportId);
    void deleteByReportId(String reportId);
}
