package com.reporting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reporting.config.SecurityConfig;
import com.reporting.dto.ReportConfigDto;
import com.reporting.service.SqlGeneratorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReportPreviewController.class)
@Import(SecurityConfig.class)
@DisplayName("ReportPreviewController Unit Tests")
@WithMockUser(username = "admin", roles = {"USER"})
@SuppressWarnings({"null", "unchecked", "rawtypes"})
public class ReportPreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SqlGeneratorService sqlGeneratorService;

    @Test
    @DisplayName("POST /api/reports/preview-sql: compiles and returns PostgreSQL query preview")
    public void previewSql_shouldReturnCompiledSql() throws Exception {
        ReportConfigDto config = new ReportConfigDto();
        config.setReportId("RPT_1");
        config.setReportName("Weekly Preview");

        String compiledQuery = "SELECT * FROM analytics.fact_sales WHERE region = 'EMEA'";
        when(sqlGeneratorService.generateMatrixQuery(any(ReportConfigDto.class)))
                .thenReturn(compiledQuery);

        mockMvc.perform(post("/api/reports/preview-sql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sql").value(compiledQuery));
    }

    @Test
    @DisplayName("POST /api/reports/preview-sql: returns 500 when dry-run generation throws error")
    public void previewSql_shouldReturnErrorOnException() throws Exception {
        ReportConfigDto config = new ReportConfigDto();
        config.setReportId("RPT_1");

        when(sqlGeneratorService.generateMatrixQuery(any(ReportConfigDto.class)))
                .thenThrow(new RuntimeException("Syntax error in custom formula"));

        mockMvc.perform(post("/api/reports/preview-sql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config))
                        .with(csrf()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to compile SQL: Syntax error in custom formula"));
    }
}
