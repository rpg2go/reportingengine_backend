package com.reporting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reporting.config.SecurityConfig;
import com.reporting.domain.Report;
import com.reporting.dto.Enums;
import com.reporting.dto.ReportConfigDto;
import com.reporting.repository.ReportRepository;
import com.reporting.service.ExcelParserService;
import com.reporting.service.ReportConfigService;
import com.reporting.service.ReportRunnerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ReportController.class)
@Import(SecurityConfig.class)
@DisplayName("ReportController Unit Tests")
@WithMockUser(username = "admin", roles = {"USER"})
public class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private ReportRepository reportRepository;
    @MockBean private ReportConfigService configService;
    @MockBean private ExcelParserService parserService;
    @MockBean private ReportRunnerService runnerService;
    @MockBean private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("GET /api/reports: lists reports successfully")
    public void listReports_shouldReturnReportsList() throws Exception {
        Report report = Report.builder().reportId("RPT_1").name("Sales").status("draft").build();
        when(reportRepository.findAll()).thenReturn(List.of(report));

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportId").value("RPT_1"))
                .andExpect(jsonPath("$[0].name").value("Sales"));
    }

    @Test
    @DisplayName("GET /api/reports/{id}: loads config DTO successfully")
    public void getReportConfig_shouldReturnConfigDto() throws Exception {
        ReportConfigDto dto = new ReportConfigDto();
        dto.setReportId("RPT_1");
        dto.setName("Weekly Sales");
        when(configService.loadFromDb(eq("RPT_1"), any())).thenReturn(dto);

        mockMvc.perform(get("/api/reports/RPT_1").param("date", "2026-05-26"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value("RPT_1"))
                .andExpect(jsonPath("$.name").value("Weekly Sales"));
    }

    @Test
    @DisplayName("POST /api/reports/import: parses empty file returns Bad Request")
    public void importTemplate_emptyFile_shouldReturnBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        mockMvc.perform(multipart("/api/reports/import").file(emptyFile).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("File is empty"));
    }

    @Test
    @DisplayName("POST /api/reports/import: successfully imports Excel spreadsheet")
    public void importTemplate_validFile_shouldReturnSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "template.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "excel-mock-bytes".getBytes());
        doNothing().when(parserService).importTemplate(any(), eq("template.xlsx"));

        mockMvc.perform(multipart("/api/reports/import").file(file).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Template imported successfully"));
    }

    @Test
    @DisplayName("POST /api/reports: returns Bad Request when report ID is missing")
    public void createReport_missingReportId_shouldReturnBadRequest() throws Exception {
        ReportConfigDto dto = new ReportConfigDto();
        dto.setReportId(""); // Empty report ID
        
        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Report ID is required"));
    }

    @Test
    @DisplayName("POST /api/reports: successfully saves new config")
    public void createReport_validConfig_shouldReturnSuccess() throws Exception {
        ReportConfigDto dto = new ReportConfigDto();
        dto.setReportId("RPT_NEW");
        dto.setName("New Report");
        doNothing().when(configService).saveToDb(any());

        mockMvc.perform(post("/api/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Report created successfully"));
    }

    @Test
    @DisplayName("GET /api/reports/table-columns: returns bad request if table name has no schema dot")
    public void listTableColumns_noSchemaDot_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/reports/table-columns").param("table", "fact_sales"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/reports/dimensions/values: rejects injection queries on column name")
    public void getDimensionValues_sqlInjectionInColumn_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/reports/dimensions/values")
                        .param("table", "analytics.fact_sales")
                        .param("column", "region; DROP TABLE users"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/reports/dimensions/values: rejects non-whitelisted table names")
    public void getDimensionValues_nonWhitelistedTable_shouldReturnBadRequest() throws Exception {
        // Mock listTables call inside the controller
        JdbcOperations mockJdbcOperations = mock(JdbcOperations.class);
        when(jdbcTemplate.getJdbcOperations()).thenReturn(mockJdbcOperations);
        when(mockJdbcOperations.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("analytics.fact_sales")); // whitelist only contains fact_sales

        // Requesting non-whitelisted analytics.fact_inventory
        mockMvc.perform(get("/api/reports/dimensions/values")
                        .param("table", "analytics.fact_inventory")
                        .param("column", "region"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/reports/dimensions/values: returns distinct dimension values when inputs are safe")
    public void getDimensionValues_validInputs_shouldReturnValues() throws Exception {
        JdbcOperations mockJdbcOperations = mock(JdbcOperations.class);
        when(jdbcTemplate.getJdbcOperations()).thenReturn(mockJdbcOperations);
        when(mockJdbcOperations.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("analytics.fact_sales")); // whitelist

        when(mockJdbcOperations.query(anyString(), any(RowMapper.class)))
                .thenReturn(List.of("North", "South", "West"));

        mockMvc.perform(get("/api/reports/dimensions/values")
                        .param("table", "analytics.fact_sales")
                        .param("column", "region"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("North"))
                .andExpect(jsonPath("$[1]").value("South"));
    }
}
