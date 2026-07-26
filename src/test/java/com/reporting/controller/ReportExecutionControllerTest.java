package com.reporting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reporting.config.SecurityConfiguration;
import com.reporting.dto.ReportConfigDto;
import com.reporting.service.AnalyticsQueryDispatcher;
import com.reporting.service.PostProcessorService;
import com.reporting.service.ReportConfigService;
import com.reporting.service.SqlGeneratorService;
import com.reporting.cache.MetadataCache;
import com.reporting.catalog.SchemaCatalogLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = { ReportExecutionController.class })
@Import(SecurityConfiguration.class)
@DisplayName("ReportExecutionController Slice Tests")
@WithMockUser(username = "admin", roles = { "USER" })
@SuppressWarnings("null")
public class ReportExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReportConfigService configService;

    @MockitoBean
    private SqlGeneratorService generatorService;

    @MockitoBean
    private PostProcessorService postProcessorService;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private AnalyticsQueryDispatcher analyticsQueryDispatcher;

    @MockitoBean
    private MetadataCache metadataCache;

    @MockitoBean
    private SchemaCatalogLoader schemaCatalogLoader;

    @Test
    @DisplayName("POST /api/reports/{id}/execute: successful raw grid cell execution")
    public void executeReport_success_shouldReturnUnpivotedCoordinates() throws Exception {
        String reportId = "RPT_SALES";
        ReportExecutionController.ExecuteRequest request = new ReportExecutionController.ExecuteRequest();
        request.setReportingDate("2026-07-26");

        // 1. Mock DWH dim_date existence check
        when(analyticsQueryDispatcher.queryForObject(anyString(), eq(Boolean.class), eq("2026-07-26")))
                .thenReturn(true);

        // 2. Mock load config
        ReportConfigDto config = new ReportConfigDto();
        config.setReportId(reportId);
        when(configService.loadFromDb(eq(reportId), any(LocalDate.class))).thenReturn(config);

        // 3. Mock SQL generation
        when(generatorService.generate(any(ReportConfigDto.class))).thenReturn("SELECT * FROM analytics.fact_sales");

        // 4. Mock execution raw data
        when(analyticsQueryDispatcher.queryForList(anyString())).thenReturn(List.of(Map.of("amount", 1500.0)));

        // 5. Mock post-processor
        when(postProcessorService.process(any(), anyList()))
                .thenReturn(Map.of("R1", Map.of("C1", 1500.0)));

        mockMvc.perform(post("/api/reports/" + reportId + "/execute")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rowId").value("R1"))
                .andExpect(jsonPath("$[0].colId").value("C1"))
                .andExpect(jsonPath("$[0].val").value(1500.0));
    }

    @Test
    @DisplayName("POST /api/reports/{id}/execute: returns 400 Bad Request on invalid date format")
    public void executeReport_invalidDate_shouldReturn400() throws Exception {
        String reportId = "RPT_SALES";
        ReportExecutionController.ExecuteRequest request = new ReportExecutionController.ExecuteRequest();
        request.setReportingDate("invalid-date-str");

        mockMvc.perform(post("/api/reports/" + reportId + "/execute")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid reportingDate format. Must be YYYY-MM-DD."));
    }
}
