package com.reporting.service;

import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ResolvedMetricDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

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
        LocalDate refDate = referenceDate != null ? referenceDate : LocalDate.now();

        // 1. Load Config
        ReportConfigDto config = configService.loadFromDb(reportId, refDate);

        // 2. Resolve Metrics
        Map<String, ResolvedMetricDto> resolved = resolverService.resolveAll(config);

        // 3. Generate SQL
        String sql = generatorService.generate(config, resolved);

        // 4. Execute SQL
        Map<String, Object> rawData;
        try {
            rawData = jdbcTemplate.queryForMap(sql);
        } catch (Exception e) {
            rawData = Collections.emptyMap();
        }

        // 5. Post-Process
        Map<String, Map<String, Double>> processedData = postProcessorService.process(config, rawData);

        // 6. Render
        return rendererService.render(config, processedData);
    }
}
