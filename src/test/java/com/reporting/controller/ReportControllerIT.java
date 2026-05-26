package com.reporting.controller;

import com.reporting.BaseIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ReportController Security Integration Tests")
public class ReportControllerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

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
}
