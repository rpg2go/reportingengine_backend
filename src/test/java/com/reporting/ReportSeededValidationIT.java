package com.reporting;

import com.reporting.domain.Report;
import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ValidationError;
import com.reporting.dto.ValidationResult;
import com.reporting.repository.ReportRepository;
import com.reporting.service.ReportConfigService;
import com.reporting.service.ReportValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Seeded Report Validation Integration Test")
public class ReportSeededValidationIT extends BaseIT {

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private ReportConfigService configService;

    @Autowired
    private ReportValidationService validationService;

    @Test
    @DisplayName("Validate all seeded reports in the database")
    public void validateAllSeededReports() {
        List<Report> reports = reportRepository.findAll();
        System.out.println("Found " + reports.size() + " reports to validate.");
        assertThat(reports).isNotEmpty();

        int criticalErrorCount = 0;

        for (Report report : reports) {
            String reportId = report.getReportId();
            ReportConfigDto configDto = configService.loadFromDb(reportId, LocalDate.of(2026, 5, 31));
            ValidationResult result = validationService.validateConfiguration(configDto);

            System.out.println("==================================================");
            System.out.println("Validating Report: " + reportId + " (" + report.getReportName() + ")");
            System.out.println("Valid: " + result.isValid());

            for (ValidationError err : result.getErrors()) {
                System.out.println(String.format("  [%s] %s (%s): %s",
                    err.getErrorSeverity(),
                    err.getElementId(),
                    err.getFieldContext(),
                    err.getDisplayMessage()
                ));
                if ("CRITICAL".equalsIgnoreCase(err.getErrorSeverity())) {
                    criticalErrorCount++;
                }
            }
        }

        System.out.println("==================================================");
        System.out.println("Total CRITICAL validation errors found: " + criticalErrorCount);

        assertThat(criticalErrorCount).isZero();
    }
}
