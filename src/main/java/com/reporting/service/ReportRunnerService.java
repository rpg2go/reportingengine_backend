package com.reporting.service;

import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ResolvedMetricDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ReportRunnerService {

    private final ReportConfigService configService;
    private final SemanticResolverService resolverService;
    private final SqlGeneratorService generatorService;
    private final PostProcessorService postProcessorService;
    private final LayoutRendererService rendererService;
    private final JdbcTemplate jdbcTemplate;

    public ReportRunnerService(ReportConfigService configService,
                               SemanticResolverService resolverService,
                               SqlGeneratorService generatorService,
                               PostProcessorService postProcessorService,
                               LayoutRendererService rendererService,
                               JdbcTemplate jdbcTemplate) {
        this.configService = configService;
        this.resolverService = resolverService;
        this.generatorService = generatorService;
        this.postProcessorService = postProcessorService;
        this.rendererService = rendererService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public byte[] runReport(String reportId, LocalDate referenceDate) throws Exception {
        return runReport(reportId, null, referenceDate);
    }

    public byte[] runReport(String reportId, Integer version, LocalDate referenceDate) throws Exception {
        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();

        log.info("Starting report run execution for reportId: {} version: {} with referenceDate: {}", reportId, version, refDate);

        // 1. Load Config
        ReportConfigDto config = version != null
            ? configService.loadFromDb(reportId, version, refDate)
            : configService.loadFromDb(reportId, refDate);
        log.info("Loaded configuration for reportId: {}, Columns count: {}, Rows count: {}", 
            reportId, config.getColumns().size(), config.getRows().size());

        // 2. Resolve Metrics (Bypassed in the new dynamic metadata architecture)
        Map<String, ResolvedMetricDto> resolved = Collections.emptyMap();

        // 3. Generate SQL
        String sql = generatorService.generate(config, resolved);
        log.debug("Generated query SQL: \n{}", sql);

        // 4. Execute SQL
        List<Map<String, Object>> rawData;
        try {
            log.info("Executing generated DWH queries for reportId: {}", reportId);
            rawData = jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("Failed to execute generated report SQL query for reportId: {}. Generated SQL: \n{}", reportId, sql, e);
            throw new RuntimeException("Database execution failed for generated SQL query: " + e.getMessage(), e);
        }

        // 5. Post-Process
        log.info("Post-processing raw data results with formulas");
        Map<String, Map<String, Double>> processedData = postProcessorService.process(config, rawData);

        // 6. Render
        log.info("Rendering report results into Excel template format");
        return rendererService.render(config, processedData);
    }
}
