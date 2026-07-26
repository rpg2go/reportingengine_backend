package com.db.reporting.repository;

import com.db.reporting.domain.Style;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StyleRepository extends JpaRepository<Style, Integer> {
    Optional<Style> findByName(String name);
}
