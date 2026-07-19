package com.reporting.service;

import com.reporting.repository.ReportDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Routing manager executing local JDBC database operations under 'dev' profile
 * and swapping to GoogleSQL dialect executions running over the BigQuery client SDK under 'sit' profile.
 */
@Slf4j
@Component
public class AnalyticsQueryDispatcher {

    private final JdbcTemplate jdbcTemplate;
    private final ReportDataRepository reportDataRepository;
    private final Optional<BigQueryAnalyticsService> gcpProdAnalyticsService;
    private final Environment environment;

    public AnalyticsQueryDispatcher(JdbcTemplate jdbcTemplate,
                                    ReportDataRepository reportDataRepository,
                                    Optional<BigQueryAnalyticsService> gcpProdAnalyticsService,
                                    Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.reportDataRepository = reportDataRepository;
        this.gcpProdAnalyticsService = gcpProdAnalyticsService;
        this.environment = environment;
    }

    /**
     * Determines whether the active profile is 'sit'.
     */
    public boolean isSitActive() {
        return Arrays.asList(environment.getActiveProfiles()).contains("sit");
    }

    /**
     * Executes SQL query returning a stream of rows as Object[].
     */
    public Stream<Object[]> queryForStream(String sql) {
        if (isSitActive() && gcpProdAnalyticsService.isPresent()) {
            log.info("Routing stream query to BigQuery (sit profile)");
            return gcpProdAnalyticsService.get().queryForStream(sql);
        } else {
            log.info("Routing stream query to Local PostgreSQL (dev profile)");
            return reportDataRepository.streamNativeQuery(sql);
        }
    }

    /**
     * Executes SQL query returning a list of maps (row values indexed by column names).
     */
    public List<Map<String, Object>> queryForList(String sql) {
        if (isSitActive() && gcpProdAnalyticsService.isPresent()) {
            log.info("Routing query to BigQuery (sit profile)");
            return gcpProdAnalyticsService.get().queryForList(sql);
        } else {
            log.info("Routing query to Local PostgreSQL (dev profile)");
            return jdbcTemplate.queryForList(sql);
        }
    }

    /**
     * Executes SQL query returning a list of single column values of specific type.
     */
    public <T> List<T> queryForList(String sql, Class<T> elementType) {
        if (isSitActive() && gcpProdAnalyticsService.isPresent()) {
            log.info("Routing typed list query to BigQuery (sit profile)");
            return gcpProdAnalyticsService.get().queryForList(sql, elementType);
        } else {
            log.info("Routing typed list query to Local PostgreSQL (dev profile)");
            return jdbcTemplate.queryForList(sql, elementType);
        }
    }

    /**
     * Executes SQL query returning a single object matching target type.
     */
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        if (isSitActive() && gcpProdAnalyticsService.isPresent()) {
            log.info("Routing query for object to BigQuery (sit profile)");
            return gcpProdAnalyticsService.get().queryForObject(sql, requiredType, args);
        } else {
            log.info("Routing query for object to Local PostgreSQL (dev profile)");
            return jdbcTemplate.queryForObject(sql, requiredType, args);
        }
    }
}
