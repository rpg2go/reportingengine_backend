package com.reporting.it.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reporting.it.BaseIT;
import com.reporting.dto.ColumnDefDto;
import com.reporting.dto.Enums;
import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ReportRowDto;
import com.reporting.dto.MeasureDefinitionDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.test.context.support.WithMockUser;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("SqlGeneratorService & Preview IT Tests")
@WithMockUser(username = "admin", roles = {"USER"})
@SuppressWarnings("null")
public class SqlGeneratorServiceIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/reports/preview-sql - dry-run query compilation returns valid query")
    public void previewSql_shouldReturnCompiledCteString() throws Exception {
        // Arrange
        List<ColumnDefDto> columns = List.of(
            new ColumnDefDto("C1", "WTD no.", Enums.ColType.WTD, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R1", "RPT_IT", "GBS gross", Enums.RowType.data, 
                new MeasureDefinitionDTO("visual", "SUM", "amount", "analytics.fact_sales", null), null, "normal", 0, 1, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto();
        config.setReportId("RPT_IT");
        config.setReportName("IT Test");
        config.setColumns(columns);
        config.setRows(rows);
        config.setReferenceDate(null);
        config.setExploreId(1);
        config.setStatus(Enums.ReportStatus.draft);
        config.setSourceTable("analytics.fact_sales");
        config.setGranularity("monthly");
        config.setTimeframeStartType("FIXED");
        config.setTimeframeStartStatic(java.time.LocalDate.parse("2025-01-01"));
        config.setTimeframeEndType("FIXED");
        config.setTimeframeEndStatic(java.time.LocalDate.parse("2025-12-31"));

        // Act & Assert
        mockMvc.perform(post("/api/reports/preview-sql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sql").exists())
                .andDo(result -> {
                    String responseBody = result.getResponse().getContentAsString();
                    String sql = objectMapper.readTree(responseBody).get("sql").asText();
                    assertThat(sql).contains("WITH");
                    assertThat(sql).contains("fact_sales");
                    assertThat(sql).contains("SUM(");
                });
    }

    @Test
    @DisplayName("validateConfiguration - enforcement validation of aggregation type mismatch")
    public void validate_nonNumericAggregation_shouldReturnCriticalError() throws Exception {
        // Arrange
        // reporting_date is a DATE column in fact_sales, using SUM on it should trigger a validation error
        List<ReportRowDto> rows = List.of(
            new ReportRowDto("R2", "RPT_IT", "Row 2", Enums.RowType.data, 
                new MeasureDefinitionDTO("visual", "SUM", "reporting_date", "analytics.fact_sales", null), null, "normal", 0, 1, Set.of("C1"), null)
        );
        ReportConfigDto config = new ReportConfigDto();
        config.setReportId("RPT_IT");
        config.setReportName("IT Test");
        config.setColumns(Collections.emptyList());
        config.setRows(rows);
        config.setReferenceDate(null);
        config.setExploreId(1);
        config.setStatus(Enums.ReportStatus.draft);
        config.setSourceTable("analytics.fact_sales");
        config.setGranularity("monthly");
        config.setTimeframeStartType("FIXED");
        config.setTimeframeStartStatic(java.time.LocalDate.parse("2025-01-01"));
        config.setTimeframeEndType("FIXED");
        config.setTimeframeEndStatic(java.time.LocalDate.parse("2025-12-31"));

        // Act & Assert
        mockMvc.perform(post("/api/reports/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(config))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors[0].elementId").value("R2"))
                .andExpect(jsonPath("$.errors[0].errorSeverity").value("CRITICAL"))
                .andExpect(jsonPath("$.errors[0].displayMessage").value(org.hamcrest.Matchers.containsString("cannot be used with numeric aggregation")));
    }
}
