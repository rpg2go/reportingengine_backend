package com.reporting.service;

import com.reporting.dto.*;
import com.reporting.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReportConfigService Unit Tests")
public class ReportConfigServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private ColumnDefRepository columnDefRepository;
    @Mock private ReportRowRepository reportRowRepository;
    @Mock private RowMetricRepository rowMetricRepository;
    @Mock private RowFormulaRepository rowFormulaRepository;
    @Mock private RowColumnMapRepository rowColumnMapRepository;
    @Mock private StyleRepository styleRepository;
    @Mock private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ReportConfigService service;

    @Test
    @DisplayName("loadFromDb throws IllegalArgumentException if report does not exist")
    public void loadFromDb_nonExistentReport_shouldThrowException() {
        when(reportRepository.findById("INVALID_ID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadFromDb("INVALID_ID", LocalDate.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Report not found");
    }
}
