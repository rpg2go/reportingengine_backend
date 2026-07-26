package com.reporting.service;

import com.reporting.repository.ReportDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsQueryDispatcher Unit Tests")
class AnalyticsQueryDispatcherTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private ReportDataRepository reportDataRepository;

    @Mock
    private BigQueryAnalyticsService bigQueryAnalyticsService;

    @Mock
    private Environment environment;

    private AnalyticsQueryDispatcher dispatcher;

    @Nested
    @DisplayName("Dev Profile Routing (Local PostgreSQL)")
    class DevProfileTests {

        @BeforeEach
        void setUp() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
            dispatcher = new AnalyticsQueryDispatcher(
                    jdbcTemplate,
                    reportDataRepository,
                    Optional.empty(),
                    environment
            );
        }

        @Test
        @DisplayName("Should detect dev profile as not sit")
        void shouldDetectDevProfile() {
            assertThat(dispatcher.isSitActive()).isFalse();
        }

        @Test
        @DisplayName("queryForList should route to local JdbcTemplate")
        void queryForListRoutesToJdbcTemplate() {
            String sql = "SELECT * FROM analytics.fact_sales";
            when(jdbcTemplate.queryForList(sql)).thenReturn(List.of(Map.of("amount", 100.0)));

            List<Map<String, Object>> result = dispatcher.queryForList(sql);

            assertThat(result).hasSize(1);
            verify(jdbcTemplate).queryForList(sql);
            verifyNoInteractions(bigQueryAnalyticsService);
        }

        @Test
        @DisplayName("queryForObject should route to local JdbcTemplate")
        void queryForObjectRoutesToJdbcTemplate() {
            String sql = "SELECT COUNT(1) > 0 FROM analytics.dim_date WHERE date_key = CAST(? AS DATE)";
            when(jdbcTemplate.queryForObject(sql, Boolean.class, "2026-07-26")).thenReturn(true);

            Boolean exists = dispatcher.queryForObject(sql, Boolean.class, "2026-07-26");

            assertThat(exists).isTrue();
            verify(jdbcTemplate).queryForObject(sql, Boolean.class, "2026-07-26");
        }
    }

    @Nested
    @DisplayName("SIT Profile Routing (Google Cloud BigQuery)")
    class SitProfileTests {

        @BeforeEach
        void setUp() {
            when(environment.getActiveProfiles()).thenReturn(new String[]{"sit"});
            dispatcher = new AnalyticsQueryDispatcher(
                    jdbcTemplate,
                    reportDataRepository,
                    Optional.of(bigQueryAnalyticsService),
                    environment
            );
        }

        @Test
        @DisplayName("Should detect sit profile as active")
        void shouldDetectSitProfile() {
            assertThat(dispatcher.isSitActive()).isTrue();
        }

        @Test
        @DisplayName("queryForList should route to BigQueryAnalyticsService")
        void queryForListRoutesToBigQuery() {
            String sql = "SELECT * FROM analytics.fact_sales";
            when(bigQueryAnalyticsService.queryForList(sql)).thenReturn(List.of(Map.of("amount", 500.0)));

            List<Map<String, Object>> result = dispatcher.queryForList(sql);

            assertThat(result).hasSize(1);
            verify(bigQueryAnalyticsService).queryForList(sql);
            verifyNoInteractions(jdbcTemplate);
        }
    }
}
