package com.reporting.controller;

import com.reporting.domain.Report;
import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ValidationResult;
import com.reporting.repository.ReportRepository;
import com.reporting.service.ReportConfigService;
import com.reporting.service.ReportRunnerService;
import com.reporting.service.ReportValidationService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for report template CRUD, execution, and structural validation.
 *
 * <p>Schema and dimension discovery endpoints have been extracted to
 * {@link SchemaDiscoveryController}. Version lifecycle endpoints live in
 * {@link ReportVersionController}. Raw grid execution for the frontend live
 * view is in {@link ReportExecutionController}.</p>
 *
 * @see SchemaDiscoveryController
 * @see ReportVersionController
 * @see ReportExecutionController
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
@SuppressWarnings("null")
public class ReportController {

    private final ReportRepository reportRepository;
    private final ReportConfigService configService;
    private final ReportRunnerService runnerService;
    private final ReportValidationService validationService;

    public ReportController(ReportRepository reportRepository,
                            ReportConfigService configService,
                            ReportRunnerService runnerService,
                            ReportValidationService validationService) {
        this.reportRepository  = reportRepository;
        this.configService     = configService;
        this.runnerService     = runnerService;
        this.validationService = validationService;
    }

    // ─── catalog ──────────────────────────────────────────────────────────────

    /**
     * Returns the latest published (or draft) version for every distinct report,
     * used to populate the report catalogue list view.
     *
     * @return unordered list of latest {@link Report} header rows
     */
    @GetMapping
    public ResponseEntity<List<Report>> listReports() {
        return ResponseEntity.ok(reportRepository.findLatestPublishedPerReport());
    }

    // ─── config load ──────────────────────────────────────────────────────────

    /**
     * Loads the full configuration DTO for a single report.
     *
     * @param id      the report identifier
     * @param version optional specific version; defaults to the latest version
     * @param date    optional reference date for rolling column boundaries; defaults to today
     * @return the fully hydrated {@link ReportConfigDto}
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReportConfigDto> getReportConfig(
            @PathVariable("id") String id,
            @RequestParam(value = "version", required = false) Integer version,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate refDate = date != null ? date : LocalDate.now();
        if (version != null) {
            return ResponseEntity.ok(configService.loadFromDb(id, version, refDate));
        }
        return ResponseEntity.ok(configService.loadFromDb(id, refDate));
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    /**
     * Creates a new report configuration.
     *
     * @param configDto the report configuration to persist; {@code reportId} must be set
     * @return 200 OK on success, 400 if {@code reportId} is missing
     */
    @PostMapping
    public ResponseEntity<?> createReport(@Valid @RequestBody ReportConfigDto configDto) {
        if (configDto.getReportId() == null || configDto.getReportId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Report ID is required"));
        }
        try {
            configService.saveToDb(configDto);
            return ResponseEntity.ok(Map.of("message", "Report created successfully"));
        } catch (Exception e) {
            log.error("Failed to create report configuration", e);
            return ResponseEntity.status(500).body(Map.of("message", "Failed to create report configuration. Please verify configuration parameters."));
        }
    }

    /**
     * Updates (cascade-overwrites) an existing report configuration.
     *
     * <p>This performs a cascade delete on all child records (columns, rows, metrics,
     * formulas, column maps) before re-inserting from the DTO.</p>
     *
     * @param id        the report identifier (must match {@code configDto.reportId})
     * @param configDto the new report configuration
     * @return 200 OK on success, 500 on persistence failure
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> saveReport(
            @PathVariable("id") String id,
            @Valid @RequestBody ReportConfigDto configDto) {
        configDto.setReportId(id);
        try {
            configService.saveToDb(configDto);
            return ResponseEntity.ok(Map.of("message", "Report saved successfully"));
        } catch (Exception e) {
            log.error("Failed to save report configuration", e);
            return ResponseEntity.status(500).body(Map.of("message", "Failed to save report configuration. Please try again or contact your administrator."));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReport(@PathVariable("id") String id) {
        try {
            configService.deleteReport(id);
            return ResponseEntity.ok(Map.of("message", "Report deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete report ID: {}", id, e);
            return ResponseEntity.status(500).body(Map.of("message", "Failed to delete report. Please verify report status and try again."));
        }
    }

    // ─── execution ────────────────────────────────────────────────────────────

    /**
     * Generates and executes the report, returning the rendered Excel file as a
     * binary download.
     *
     * @param id      the report identifier
     * @param version optional specific version; defaults to the latest
     * @param date    optional reference date; defaults to today
     * @return {@code application/octet-stream} attachment with filename
     *         {@code "<id>_<date>.xlsx"}
     */
    @PostMapping("/{id}/run")
    public ResponseEntity<byte[]> runReport(
            @PathVariable("id") String id,
            @RequestParam(value = "version", required = false) Integer version,
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate refDate = date != null ? date : LocalDate.now();
        try {
            log.info("Running report generation for report ID: {} version: {} with refDate: {}", id, version, refDate);
            byte[] xlsxBytes = runnerService.runReport(id, version, refDate);
            String filename = String.format("%s_%s.xlsx", id, refDate);

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(xlsxBytes);
        } catch (Exception e) {
            log.error("Failed to run report ID: {} with refDate: {}", id, refDate, e);
            return ResponseEntity.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\": \"Failed to run and compile report. Please verify configuration parameters.\"}".getBytes());
        }
    }

    // ─── validation ───────────────────────────────────────────────────────────

    /**
     * Runs structural and semantic analysis on a report configuration to identify
     * issues before saving or running.
     *
     * @param configDto the configuration to validate
     * @return a {@link ValidationResult} containing any errors or warnings found
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validateReport(@RequestBody ReportConfigDto configDto) {
        log.info("Validating report config for ID: {}", configDto.getReportId());
        return ResponseEntity.ok(validationService.validateConfiguration(configDto));
    }
}
