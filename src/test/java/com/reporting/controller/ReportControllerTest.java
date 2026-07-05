package com.reporting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reporting.config.SecurityConfig;
import com.reporting.domain.Report;
import com.reporting.dto.Enums;
import com.reporting.dto.ReportConfigDto;
import com.reporting.repository.ReportRepository;
import com.reporting.service.ReportConfigService;
import com.reporting.service.ReportRunnerService;
import com.reporting.service.ReportValidationService;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = { ReportController.class, SchemaDiscoveryController.class })
@Import(SecurityConfig.class)
@DisplayName("ReportController Unit Tests")
@WithMockUser(username = "admin", roles = { "USER" })
public class ReportControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private ReportRepository reportRepository;
        @MockBean
        private ReportConfigService configService;
        @MockBean
        private ReportRunnerService runnerService;
        @MockBean
        private ReportValidationService validationService;
        @MockBean
        private NamedParameterJdbcTemplate jdbcTemplate;

        @Test
        @DisplayName("GET /api/reports: lists reports successfully")
        public void listReports_shouldReturnReportsList() throws Exception {
                Report report = Report.builder().reportId("RPT_1").reportName("Sales").status("draft").build();
                when(reportRepository.findLatestPublishedPerReport()).thenReturn(List.of(report));

                mockMvc.perform(get("/api/reports"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].reportId").value("RPT_1"))
                                .andExpect(jsonPath("$[0].reportName").value("Sales"));
        }

        @Test
        @DisplayName("GET /api/reports/{id}: loads config DTO successfully")
        public void getReportConfig_shouldReturnConfigDto() throws Exception {
                ReportConfigDto dto = new ReportConfigDto();
                dto.setReportId("RPT_1");
                dto.setReportName("Weekly Sales");
                when(configService.loadFromDb(eq("RPT_1"), any())).thenReturn(dto);

                mockMvc.perform(get("/api/reports/RPT_1").param("date", "2026-05-26"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.reportId").value("RPT_1"))
                                .andExpect(jsonPath("$.reportName").value("Weekly Sales"));
        }

        private ReportConfigDto createValidReportConfigDto() {
                ReportConfigDto dto = new ReportConfigDto();
                dto.setReportId("RPT_NEW");
                dto.setReportName("New Report");
                dto.setColumns(Collections.emptyList());
                dto.setRows(Collections.emptyList());
                dto.setStatus(Enums.ReportStatus.draft);
                return dto;
        }

        @Test
        @DisplayName("POST /api/reports: returns Bad Request when report ID is missing")
        public void createReport_missingReportId_shouldReturnBadRequest() throws Exception {
                ReportConfigDto dto = createValidReportConfigDto();
                dto.setReportId(""); // Empty report ID

                mockMvc.perform(post("/api/reports")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto))
                                .with(csrf()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Validation failed"))
                                .andExpect(jsonPath("$.errors.reportId").value("Report ID is required"));
        }

        @Test
        @DisplayName("POST /api/reports: successfully saves new config")
        public void createReport_validConfig_shouldReturnSuccess() throws Exception {
                ReportConfigDto dto = createValidReportConfigDto();
                doNothing().when(configService).saveToDb(any());

                mockMvc.perform(post("/api/reports")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto))
                                .with(csrf()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Report created successfully"));
        }

        @Test
        @DisplayName("PUT /api/reports/{id}: successfully saves existing config")
        public void saveReport_validConfig_shouldReturnSuccess() throws Exception {
                ReportConfigDto dto = createValidReportConfigDto();
                dto.setReportId("RPT_NEW");
                doNothing().when(configService).saveToDb(any());

                mockMvc.perform(put("/api/reports/RPT_NEW")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto))
                                .with(csrf()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Report saved successfully"));
        }

        @Test
        @DisplayName("PUT /api/reports/{id}: returns Bad Request when reportName is blank")
        public void saveReport_blankReportName_shouldReturnBadRequest() throws Exception {
                ReportConfigDto dto = createValidReportConfigDto();
                dto.setReportName("   "); // Blank name

                mockMvc.perform(put("/api/reports/RPT_NEW")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(dto))
                                .with(csrf()))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.message").value("Validation failed"))
                                .andExpect(jsonPath("$.errors.reportName").value("Report Name is required"));
        }

        @Test
        @DisplayName("GET /api/reports/table-columns: returns bad request if table name has no schema dot")
        public void listTableColumns_noSchemaDot_shouldReturnBadRequest() throws Exception {
                mockMvc.perform(get("/api/reports/table-columns").param("table", "fact_sales"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("GET /api/reports/column-types: returns bad request if table name has no schema dot")
        public void getColumnTypes_noSchemaDot_shouldReturnBadRequest() throws Exception {
                mockMvc.perform(get("/api/reports/column-types").param("table", "fact_sales"))
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

        @Test
        @DisplayName("GET /api/reports/dimension-joins: returns list of joins successfully")
        public void getDimensionJoins_shouldReturnJoinsList() throws Exception {
                Map<String, Object> mockJoin = Map.of(
                                "dimView", "dim_relationship_manager",
                                "joinType", "LEFT",
                                "joinSql", "fv.rm_id = tv.id");
                when(jdbcTemplate.query(anyString(), anyMap(), any(RowMapper.class)))
                                .thenReturn(List.of(mockJoin));

                mockMvc.perform(get("/api/reports/dimension-joins").param("factTable", "analytics.fact_sales"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].dimView").value("dim_relationship_manager"))
                                .andExpect(jsonPath("$[0].joinType").value("LEFT"))
                                .andExpect(jsonPath("$[0].joinSql").value("fv.rm_id = tv.id"));
        }

        @Test
        @DisplayName("GET /api/reports/table-columns: resolves dim view name without dot and returns columns")
        public void listTableColumns_dimTableWithoutDot_shouldResolveAndReturnColumns() throws Exception {
                JdbcOperations mockJdbcOperations = mock(JdbcOperations.class);
                when(jdbcTemplate.getJdbcOperations()).thenReturn(mockJdbcOperations);
                when(mockJdbcOperations.queryForObject(anyString(), eq(String.class), eq("dim_products")))
                                .thenReturn("analytics.dim_products");
                when(mockJdbcOperations.queryForList(anyString(), eq(String.class), eq("analytics"),
                                eq("dim_products")))
                                .thenReturn(List.of("id", "name", "category"));

                mockMvc.perform(get("/api/reports/table-columns").param("table", "dim_products"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0]").value("id"))
                                .andExpect(jsonPath("$[1]").value("name"));
        }

        @Test
        @DisplayName("GET /api/reports/column-types: resolves dim view name without dot and returns column types")
        public void getColumnTypes_dimTableWithoutDot_shouldResolveAndReturnTypes() throws Exception {
                JdbcOperations mockJdbcOperations = mock(JdbcOperations.class);
                when(jdbcTemplate.getJdbcOperations()).thenReturn(mockJdbcOperations);
                when(mockJdbcOperations.queryForObject(anyString(), eq(String.class), eq("dim_products")))
                                .thenReturn("analytics.dim_products");

                doAnswer(invocation -> {
                        org.springframework.jdbc.core.RowCallbackHandler handler = invocation.getArgument(1);
                        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                        when(rs.getString("column_name")).thenReturn("id", "name");
                        when(rs.getString("data_type")).thenReturn("integer", "character varying");

                        handler.processRow(rs);
                        handler.processRow(rs);
                        return null;
                }).when(mockJdbcOperations).query(anyString(),
                                any(org.springframework.jdbc.core.RowCallbackHandler.class), eq("analytics"),
                                eq("dim_products"));

                mockMvc.perform(get("/api/reports/column-types").param("table", "dim_products"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.id").value("integer"))
                                .andExpect(jsonPath("$.name").value("character varying"));
        }

        @Test
        @DisplayName("GET /api/reports/dimensions/values: resolves dim view name without dot and returns values")
        public void getDimensionValues_dimTableWithoutDot_shouldResolveAndReturnValues() throws Exception {
                JdbcOperations mockJdbcOperations = mock(JdbcOperations.class);
                when(jdbcTemplate.getJdbcOperations()).thenReturn(mockJdbcOperations);
                when(mockJdbcOperations.queryForObject(anyString(), eq(String.class), eq("dim_products")))
                                .thenReturn("analytics.dim_products");
                when(mockJdbcOperations.queryForList(anyString(), eq(String.class)))
                                .thenReturn(List.of("analytics.dim_products"));
                when(mockJdbcOperations.query(anyString(), any(RowMapper.class)))
                                .thenReturn(List.of("Gadget", "Widget"));

                mockMvc.perform(get("/api/reports/dimensions/values")
                                .param("table", "dim_products")
                                .param("column", "category"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0]").value("Gadget"))
                                .andExpect(jsonPath("$[1]").value("Widget"));
        }

        @Test
        @DisplayName("GET /api/reports/dimensions/values: maps reporting_date to date_key for dim_date")
        public void getDimensionValues_dimDateReportingDate_shouldMapToDateKeyAndReturnValues() throws Exception {
                JdbcOperations mockJdbcOperations = mock(JdbcOperations.class);
                when(jdbcTemplate.getJdbcOperations()).thenReturn(mockJdbcOperations);
                when(mockJdbcOperations.queryForObject(anyString(), eq(String.class), eq("dim_date")))
                                .thenReturn("analytics.dim_date");
                when(mockJdbcOperations.queryForList(anyString(), eq(String.class)))
                                .thenReturn(List.of("analytics.dim_date"));
                when(mockJdbcOperations.query(contains("date_key"), any(RowMapper.class)))
                                .thenReturn(List.of("2024-01-01", "2024-01-02"));

                mockMvc.perform(get("/api/reports/dimensions/values")
                                .param("table", "dim_date")
                                .param("column", "reporting_date"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0]").value("2024-01-01"))
                                .andExpect(jsonPath("$[1]").value("2024-01-02"));
        }

        @Test
        @DisplayName("DELETE /api/reports/{id}: deletes report successfully")
        public void deleteReport_shouldReturnOk() throws Exception {
                doNothing().when(configService).deleteReport("RPT_1");

                mockMvc.perform(delete("/api/reports/RPT_1")
                                .with(csrf()))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.message").value("Report deleted successfully"));

                verify(configService, times(1)).deleteReport("RPT_1");
        }

        @Test
        @DisplayName("DELETE /api/reports/{id}: returns 500 when service throws exception")
        public void deleteReport_shouldReturnInternalServerErrorOnFailure() throws Exception {
                doThrow(new RuntimeException("Database error")).when(configService).deleteReport("RPT_1");

                mockMvc.perform(delete("/api/reports/RPT_1")
                                .with(csrf()))
                                .andExpect(status().isInternalServerError())
                                .andExpect(jsonPath("$.message").value("Failed to delete report: Database error"));
        }
}
