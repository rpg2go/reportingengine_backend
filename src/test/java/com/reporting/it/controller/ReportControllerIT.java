package com.reporting.it.controller;

import com.reporting.it.BaseIT;
import com.reporting.domain.Report;
import com.reporting.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ReportController Security Integration Tests")
public class ReportControllerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Access to /api/reports without credentials should return 401 Unauthorized")
    public void accessReports_noAuth_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Access to /api/reports with valid admin credentials should return 200 OK")
    public void accessReports_validAuth_shouldReturn200() throws Exception {
        mockMvc.perform(get("/api/reports")
                .with(httpBasic("admin", "password")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Access to /api/reports with incorrect credentials should return 401 Unauthorized")
    public void accessReports_invalidAuth_shouldReturn401() throws Exception {
        mockMvc.perform(get("/api/reports")
                .with(httpBasic("admin", "wrongpassword")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user", roles = {"USER"})
    @DisplayName("Access to /api/reports/dimensions/values with invalid table format returns Bad Request")
    public void getDimensionValues_invalidTable_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(get("/api/reports/dimensions/values")
                        .param("table", "analytics.fact_sales; DROP TABLE users")
                        .param("column", "region"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Forking a published report to a new draft version should clone headers and all child elements successfully")
    public void forkReport_published_shouldCreateNewDraft() throws Exception {
        Report publishedReport = reportRepository.findAll().stream()
                .filter(r -> "published".equalsIgnoreCase(r.getStatus()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No published reports found for testing"));

        String reportId = publishedReport.getReportId();
        int version = publishedReport.getVersion();
        int expectedNextVersion = version + 1;

        // Clean up the target version in case a previous aborted run left it behind using direct SQL
        jdbcTemplate.update("DELETE FROM reporting.report_config WHERE report_id = ? AND version = ?", reportId, expectedNextVersion);

        try {
            mockMvc.perform(post("/api/reports/" + reportId + "/version/fork")
                            .param("version", String.valueOf(version))
                            .with(httpBasic("admin", "password")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.nextDraftVersion").value(expectedNextVersion));

            mockMvc.perform(get("/api/reports/" + reportId)
                            .param("version", String.valueOf(expectedNextVersion))
                            .with(httpBasic("admin", "password")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("draft"));
        } finally {
            // Clean up the created draft version using direct SQL
            jdbcTemplate.update("DELETE FROM reporting.report_config WHERE report_id = ? AND version = ?", reportId, expectedNextVersion);
        }
    }
}
