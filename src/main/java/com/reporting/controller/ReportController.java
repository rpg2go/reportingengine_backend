package com.reporting.controller;

import com.reporting.domain.Report;
import com.reporting.dto.ReportConfigDto;
import com.reporting.repository.ReportRepository;
import com.reporting.service.ExcelParserService;
import com.reporting.service.ReportConfigService;
import com.reporting.service.ReportRunnerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportRepository reportRepository;
    private final ReportConfigService configService;
    private final ExcelParserService parserService;
    private final ReportRunnerService runnerService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReportController(ReportRepository reportRepository,
                            ReportConfigService configService,
                            ExcelParserService parserService,
                            ReportRunnerService runnerService,
                            NamedParameterJdbcTemplate jdbcTemplate) {
        this.reportRepository = reportRepository;
        this.configService = configService;
        this.parserService = parserService;
        this.runnerService = runnerService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public ResponseEntity<List<Report>> listReports() {
        return ResponseEntity.ok(reportRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportConfigDto> getReportConfig(
            @PathVariable("id") String id,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate refDate = date != null ? date : LocalDate.now();
        return ResponseEntity.ok(configService.loadFromDb(id, refDate));
    }

    @PostMapping("/import")
    public ResponseEntity<?> importTemplate(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "File is empty"));
        }
        try {
            parserService.importTemplate(file.getInputStream(), file.getOriginalFilename());
            return ResponseEntity.ok(Map.of("message", "Template imported successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Failed to import template: " + e.getMessage()));
        }
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<byte[]> runReport(
            @PathVariable("id") String id,
            @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate refDate = date != null ? date : LocalDate.now();
        try {
            byte[] xlsxBytes = runnerService.runReport(id, refDate);
            String filename = String.format("%s_%s.xlsx", id, refDate.toString());

            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(xlsxBytes);
        } catch (Exception e) {
            return ResponseEntity.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .body(("{\"message\": \"Execution failed: " + e.getMessage().replace("\"", "\\\"") + "\"}").getBytes());
        }
    }

    @GetMapping("/semantic-model")
    public ResponseEntity<Map<String, Object>> getSemanticModel() {
        Map<String, Object> model = new HashMap<>();
        
        List<Map<String, Object>> views = jdbcTemplate.queryForList(
            "SELECT view_id, name, label, table_ref, view_type, primary_key, time_key, description FROM reporting.sem_view ORDER BY name",
            Collections.emptyMap()
        );
        
        List<Map<String, Object>> explores = jdbcTemplate.queryForList(
            "SELECT e.explore_id, e.name, e.label, v.name as fact_view_name, e.sql_always_where FROM reporting.sem_explore e JOIN reporting.sem_view v ON v.view_id = e.fact_view_id ORDER BY e.name",
            Collections.emptyMap()
        );
        
        List<Map<String, Object>> joins = jdbcTemplate.queryForList(
            "SELECT j.join_id, e.name as explore_name, fv.name as from_view, tv.name as to_view, j.join_sql, j.join_type FROM reporting.sem_join j JOIN reporting.sem_explore e ON e.explore_id = j.explore_id JOIN reporting.sem_view fv ON fv.view_id = j.from_view_id JOIN reporting.sem_view tv ON tv.view_id = j.to_view_id ORDER BY j.join_id",
            Collections.emptyMap()
        );
        
        List<Map<String, Object>> dimensions = jdbcTemplate.queryForList(
            "SELECT d.dimension_id, v.name as view_name, d.name, d.label, d.column_ref, d.data_type, d.description FROM reporting.sem_dimension d JOIN reporting.sem_view v ON v.view_id = d.view_id ORDER BY v.name, d.name",
            Collections.emptyMap()
        );
        
        List<Map<String, Object>> measures = jdbcTemplate.queryForList(
            "SELECT m.measure_id, v.name as view_name, m.name, m.label, m.sql_expr, m.agg_type, m.data_type, m.description FROM reporting.sem_measure m JOIN reporting.sem_view v ON v.view_id = m.view_id ORDER BY v.name, m.name",
            Collections.emptyMap()
        );
        
        model.put("views", views);
        model.put("explores", explores);
        model.put("joins", joins);
        model.put("dimensions", dimensions);
        model.put("measures", measures);
        
        return ResponseEntity.ok(model);
    }
}
