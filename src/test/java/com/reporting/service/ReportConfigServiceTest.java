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
        when(reportRepository.findFirstByReportIdOrderByVersionDesc("INVALID_ID")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadFromDb("INVALID_ID", LocalDate.now()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Report not found");
    }

    @Test
    @DisplayName("saveToDb throws IllegalStateException if attempting to modify a published report")
    public void saveToDb_onPublishedReport_shouldThrowException() {
        ReportConfigDto dto = new ReportConfigDto();
        dto.setReportId("RPT_1");
        dto.setStatus(Enums.ReportStatus.draft);
        dto.setVersion(1);

        when(jdbcTemplate.queryForObject(
            org.mockito.ArgumentMatchers.eq("SELECT EXISTS(SELECT 1 FROM reporting.rpt_report WHERE report_id = ? AND version = ?)"),
            org.mockito.ArgumentMatchers.eq(Boolean.class),
            org.mockito.ArgumentMatchers.eq("RPT_1"),
            org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(true);

        java.util.Map<String, Object> mockRecord = java.util.Map.of("status", "published");
        when(jdbcTemplate.queryForMap(
            org.mockito.ArgumentMatchers.eq("SELECT status FROM reporting.rpt_report WHERE report_id = ? AND version = ?"),
            org.mockito.ArgumentMatchers.eq("RPT_1"),
            org.mockito.ArgumentMatchers.eq(1)
        )).thenReturn(mockRecord);

        assertThatThrownBy(() -> service.saveToDb(dto))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("is PUBLISHED and cannot be modified");
    }
}
