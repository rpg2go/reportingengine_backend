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

    @Test
    @DisplayName("saveToDb throws IllegalArgumentException on status transition from draft to published if version is not exactly +1")
    public void saveToDb_versionMismatchOnPublish_shouldThrowException() {
        ReportConfigDto dto = new ReportConfigDto();
        dto.setReportId("RPT_1");
        dto.setStatus(Enums.ReportStatus.published);
        dto.setVersion(1); // Mismatch: existing is 1, so publishing requires version 2

        when(jdbcTemplate.queryForObject(
            org.mockito.ArgumentMatchers.eq("SELECT EXISTS(SELECT 1 FROM reporting.rpt_report WHERE report_id = ?)"),
            org.mockito.ArgumentMatchers.eq(Boolean.class),
            org.mockito.ArgumentMatchers.eq("RPT_1")
        )).thenReturn(true);

        java.util.Map<String, Object> mockRecord = java.util.Map.of("version", 1, "status", "draft");
        when(jdbcTemplate.queryForMap(
            org.mockito.ArgumentMatchers.eq("SELECT version, status FROM reporting.rpt_report WHERE report_id = ?"),
            org.mockito.ArgumentMatchers.eq("RPT_1")
        )).thenReturn(mockRecord);

        assertThatThrownBy(() -> service.saveToDb(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Version mismatch. To publish, version must be exactly 2");
    }
}
