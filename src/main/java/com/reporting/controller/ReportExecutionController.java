package com.reporting.controller;

import com.reporting.dto.ReportConfigDto;
import com.reporting.service.ReportConfigService;
import com.reporting.service.SqlGeneratorService;
import com.reporting.service.PostProcessorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/reports")
public class ReportExecutionController {

    private final ReportConfigService configService;
    private final SqlGeneratorService generatorService;
    private final PostProcessorService postProcessorService;
    private final JdbcTemplate jdbcTemplate;

    public ReportExecutionController(ReportConfigService configService,
                                     SqlGeneratorService generatorService,
                                     PostProcessorService postProcessorService,
                                     JdbcTemplate jdbcTemplate) {
        this.configService = configService;
        this.generatorService = generatorService;
        this.postProcessorService = postProcessorService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/{reportId}/execute")
    public ResponseEntity<?> executeReport(
            @PathVariable("reportId") String reportId,
            @RequestBody ExecuteRequest request) {
        try {
            log.info("Executing report execution endpoint for reportId: {} with date: {} and filters: {}", reportId, request.getReportingDate(), request.getRuntimeFilters());

            LocalDate refDate;
            if (request.getReportingDate() != null && !request.getReportingDate().isBlank()) {
                // 1. Validate Date Format (standard PostgreSQL date formats YYYY-MM-DD)
                try {
                    refDate = LocalDate.parse(request.getReportingDate());
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Invalid reportingDate format. Must be YYYY-MM-DD."));
                }
                
                // 2. Validate Presence in DWH (dim_date catalog check)
                String checkSql = "SELECT EXISTS(SELECT 1 FROM analytics.dim_date WHERE date_key = CAST(? AS DATE))";
                Boolean exists = jdbcTemplate.queryForObject(checkSql, Boolean.class, request.getReportingDate());
                if (exists == null || !exists) {
                    return ResponseEntity.badRequest().body(Map.of("message", "The reportingDate '" + request.getReportingDate() + "' does not exist in the DWH dim_date catalog."));
                }
            } else {
                refDate = LocalDate.now();
            }

            // 3. Load the master template config DTO
            ReportConfigDto config = configService.loadFromDb(reportId, refDate);

            // 2. Inject consumer's runtime quick filter overrides
            overrideQuickFilters(config, request.getRuntimeFilters());

            // 3. Generate query SQL
            String sql = generatorService.generate(config, Collections.emptyMap());
            log.debug("Generated report execution SQL: \n{}", sql);

            // 4. Run database query via direct JDBC
            List<Map<String, Object>> rawData = jdbcTemplate.queryForList(sql);

            // 5. Post-process data with formula evaluations
            Map<String, Map<String, Double>> processedData = postProcessorService.process(config, rawData);

            // 6. Unpivot processed matrix coordinates to a clean flat array
            List<Map<String, Object>> unpivotedData = new ArrayList<>();
            for (Map.Entry<String, Map<String, Double>> rowEntry : processedData.entrySet()) {
                String rowId = rowEntry.getKey();
                for (Map.Entry<String, Double> colEntry : rowEntry.getValue().entrySet()) {
                    String colId = colEntry.getKey();
                    Double val = colEntry.getValue();
                    
                    Map<String, Object> cell = new HashMap<>();
                    cell.put("rowId", rowId);
                    cell.put("colId", colId);
                    cell.put("val", val);
                    unpivotedData.add(cell);
                }
            }

            return ResponseEntity.ok(unpivotedData);
        } catch (Exception e) {
            log.error("Failed to execute report ID: {}", reportId, e);
            return ResponseEntity.status(500).body(Map.of("message", "Execution failed: " + e.getMessage()));
        }
    }

    private void overrideQuickFilters(ReportConfigDto config, List<RuntimeFilter> runtimeFilters) {
        if (runtimeFilters == null || runtimeFilters.isEmpty() || config.getQuickFilters() == null) {
            return;
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<SqlGeneratorService.FilterCondition> quickFilters = mapper.readValue(
                config.getQuickFilters(),
                new com.fasterxml.jackson.core.type.TypeReference<List<SqlGeneratorService.FilterCondition>>() {}
            );

            for (RuntimeFilter rf : runtimeFilters) {
                if (rf.getTableColumn() == null) continue;
                String tableColumn = rf.getTableColumn().trim();
                String dimTable = "";
                String attribute = tableColumn;
                if (tableColumn.contains(".")) {
                    int dotIdx = tableColumn.indexOf(".");
                    dimTable = tableColumn.substring(0, dotIdx);
                    attribute = tableColumn.substring(dotIdx + 1);
                }

                for (SqlGeneratorService.FilterCondition cond : quickFilters) {
                    String condDimTable = cond.getDimTable() != null ? cond.getDimTable().trim() : "";
                    String condAttr = cond.getAttribute() != null ? cond.getAttribute().trim() : "";
                    if (condDimTable.equalsIgnoreCase(dimTable) && condAttr.equalsIgnoreCase(attribute)) {
                        cond.setValue(rf.getValue());
                    }
                }
            }

            config.setQuickFilters(mapper.writeValueAsString(quickFilters));
        } catch (Exception e) {
            log.error("Failed to override quick filters: {}", e.getMessage(), e);
        }
    }

    public static class ExecuteRequest {
        private String reportingDate;
        private List<RuntimeFilter> runtimeFilters;

        public ExecuteRequest() {}

        public String getReportingDate() { return reportingDate; }
        public void setReportingDate(String reportingDate) { this.reportingDate = reportingDate; }
        public List<RuntimeFilter> getRuntimeFilters() { return runtimeFilters; }
        public void setRuntimeFilters(List<RuntimeFilter> runtimeFilters) { this.runtimeFilters = runtimeFilters; }
    }

    public static class RuntimeFilter {
        private String tableColumn;
        private String value;

        public RuntimeFilter() {}

        public String getTableColumn() { return tableColumn; }
        public void setTableColumn(String tableColumn) { this.tableColumn = tableColumn; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }

        @Override
        public String toString() {
            return "{" + tableColumn + "=" + value + "}";
        }
    }
}
