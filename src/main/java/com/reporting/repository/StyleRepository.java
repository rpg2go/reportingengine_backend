package com.reporting.repository;

import com.reporting.domain.Style;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface StyleRepository extends JpaRepository<Style, Integer> {
    Optional<Style> findByName(String name);
}
