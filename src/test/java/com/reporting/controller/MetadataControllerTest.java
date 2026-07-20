package com.reporting.controller;

import com.reporting.config.SecurityConfiguration;
import com.reporting.cache.MetadataCache;
import com.reporting.catalog.SchemaCatalogLoader;
import com.reporting.service.AnalyticsQueryDispatcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MetadataController.class)
@Import(SecurityConfiguration.class)
@DisplayName("MetadataController Unit Tests")
@WithMockUser(username = "admin", roles = {"USER"})
@SuppressWarnings("null")
public class MetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MetadataCache metadataCache;

    @MockitoBean
    private SchemaCatalogLoader schemaCatalogLoader;

    @MockitoBean
    private AnalyticsQueryDispatcher analyticsQueryDispatcher;

    @Test
    @DisplayName("GET /api/metadata/distinct-values: returns values when table is registered in sem_view")
    public void getDistinctValues_registeredTable_shouldResolveAndReturnValues() throws Exception {
        when(jdbcTemplate.queryForObject(
                eq("SELECT schema_name || '.' || table_name AS table_ref FROM catalog.meta_table WHERE table_name = ?"),
                eq(String.class),
                eq("dim_investment_hierarchy")
        )).thenReturn("analytics.dim_investment_hierarchy");

        when(analyticsQueryDispatcher.isSitActive()).thenReturn(false);

        String expectedSql = "SELECT DISTINCT CAST(asset_class AS TEXT) FROM analytics.dim_investment_hierarchy WHERE asset_class IS NOT NULL ORDER BY 1 LIMIT 100";
        when(analyticsQueryDispatcher.queryForList(eq(expectedSql), eq(String.class)))
                .thenReturn(List.of("Alternatives", "Equities"));

        mockMvc.perform(get("/api/metadata/distinct-values")
                        .param("table", "dim_investment_hierarchy")
                        .param("column", "asset_class"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("Alternatives"))
                .andExpect(jsonPath("$[1]").value("Equities"));
    }

    @Test
    @DisplayName("GET /api/metadata/distinct-values: falls back to analytics schema if not found in sem_view")
    public void getDistinctValues_unregisteredTable_shouldFallbackAndReturnValues() throws Exception {
        when(jdbcTemplate.queryForObject(
                eq("SELECT schema_name || '.' || table_name AS table_ref FROM catalog.meta_table WHERE table_name = ?"),
                eq(String.class),
                eq("fact_sales")
        )).thenThrow(new RuntimeException("not found"));

        when(analyticsQueryDispatcher.isSitActive()).thenReturn(false);

        String expectedSql = "SELECT DISTINCT CAST(region AS TEXT) FROM analytics.fact_sales WHERE region IS NOT NULL ORDER BY 1 LIMIT 100";
        when(analyticsQueryDispatcher.queryForList(eq(expectedSql), eq(String.class)))
                .thenReturn(List.of("US", "EU"));

        mockMvc.perform(get("/api/metadata/distinct-values")
                        .param("table", "fact_sales")
                        .param("column", "region"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("US"))
                .andExpect(jsonPath("$[1]").value("EU"));
    }

    @Test
    @DisplayName("GET /api/metadata/distinct-values: rejects sql injection in table or column name")
    public void getDistinctValues_sqlInjection_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/metadata/distinct-values")
                        .param("table", "fact_sales; drop table users;")
                        .param("column", "region"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/metadata/distinct-values")
                        .param("table", "fact_sales")
                        .param("column", "region; drop table users;"))
                .andExpect(status().isBadRequest());
    }
}
