package com.reporting.service;

import com.reporting.dto.ReportConfigDto;
import com.reporting.repository.ReportDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Service
public class ReportRunnerService {

    private final ReportConfigService configService;
    private final SqlGeneratorService generatorService;
    private final PostProcessorService postProcessorService;
    private final LayoutRendererService rendererService;
    private final JdbcTemplate jdbcTemplate;
    private final ReportDataRepository reportDataRepository;

    public ReportRunnerService(ReportConfigService configService,
                               SqlGeneratorService generatorService,
                               PostProcessorService postProcessorService,
                               LayoutRendererService rendererService,
                               JdbcTemplate jdbcTemplate,
                               ReportDataRepository reportDataRepository) {
        this.configService = configService;
        this.generatorService = generatorService;
        this.postProcessorService = postProcessorService;
        this.rendererService = rendererService;
        this.jdbcTemplate = jdbcTemplate;
        this.reportDataRepository = reportDataRepository;
    }

    @Transactional(readOnly = true)
    public byte[] runReport(String reportId, LocalDate referenceDate) throws Exception {
        return runReport(reportId, null, referenceDate);
    }

    @Transactional(readOnly = true)
    public byte[] runReport(String reportId, Integer version, LocalDate referenceDate) throws Exception {
        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();

        log.info("Starting report run execution for reportId: {} version: {} with referenceDate: {}", reportId, version, refDate);

        // 1. Load Config
        ReportConfigDto config = version != null
            ? configService.loadFromDb(reportId, version, refDate)
            : configService.loadFromDb(reportId, refDate);
        log.info("Loaded configuration for reportId: {}, Columns count: {}, Rows count: {}", 
            reportId, config.getColumns().size(), config.getRows().size());
        log.info("Report filters used - Quick Filters: {}, General Filters: {}", config.getQuickFilters(), config.getGeneralFilters());

        // 3. Generate SQL
        String sql = generatorService.generate(config);
        log.info("Generated query SQL: \n{}", sql);

        // 4. Execute SQL and Post-Process via database cursor
        Map<String, Map<String, Double>> processedData;
        try {
            log.info("Executing and post-processing generated DWH queries via streaming cursor for reportId: {}", reportId);
            try (Stream<Object[]> dataStream = reportDataRepository.streamNativeQuery(sql)) {
                processedData = postProcessorService.process(config, dataStream);
            }
        } catch (Exception e) {
            log.error("Failed to execute and process streaming report SQL query for reportId: {}. Generated SQL: \n{}", reportId, sql, e);
            throw new RuntimeException("Database execution or processing failed for generated SQL query: " + e.getMessage(), e);
        }

        // 6. Render
        log.info("Rendering report results into Excel template format");
        return rendererService.render(config, processedData);
    }
}
