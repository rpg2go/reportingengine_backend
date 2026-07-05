package com.reporting.controller;

import com.reporting.dto.ReportConfigDto;
import com.reporting.service.SqlGeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = {"http://127.0.0.1:4200", "http://localhost:4200"})
public class ReportPreviewController {

    private final SqlGeneratorService sqlGeneratorService;

    public ReportPreviewController(SqlGeneratorService sqlGeneratorService) {
        this.sqlGeneratorService = sqlGeneratorService;
    }

    @PostMapping("/preview-sql")
    public ResponseEntity<Map<String, String>> previewSql(@RequestBody ReportConfigDto config) {
        log.info("Generating dry-run query compilation preview for report ID: {}", config.getReportId());
        try {
            // Generate SQL directly using SqlGeneratorService without DB execution or transaction wrapper
            String sql = sqlGeneratorService.generateMatrixQuery(config);
            return ResponseEntity.ok(Map.of("sql", sql));
        } catch (Exception e) {
            log.error("Failed to generate report preview SQL", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", "Failed to compile SQL: " + e.getMessage()
            ));
        }
    }
}
