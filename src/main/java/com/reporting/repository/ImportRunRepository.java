package com.reporting.repository;

import com.reporting.domain.ImportRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportRunRepository extends JpaRepository<ImportRun, Integer> {
}
