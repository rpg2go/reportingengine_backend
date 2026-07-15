package com.reporting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reporting.config.SecurityConfiguration;
import com.reporting.domain.Report;
import com.reporting.service.ReportCloneService;
import com.reporting.cache.MetadataCache;
import com.reporting.catalog.SchemaCatalogLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = { ReportCloneController.class })
@Import(SecurityConfiguration.class)
@DisplayName("ReportCloneController Unit Tests")
@WithMockUser(username = "admin", roles = { "USER" })
@SuppressWarnings("null")
public class ReportCloneControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReportCloneService reportCloneService;

    // Required mocks for context bootstrap in typical configurations
    @MockitoBean
    private MetadataCache metadataCache;
    @MockitoBean
    private SchemaCatalogLoader schemaCatalogLoader;

    @Test
    @DisplayName("POST /api/v1/reports/{id}/clone: clones report successfully with 201 Created")
    public void cloneReport_success_shouldReturn201() throws Exception {
        String sourceId = "RPT_1";
        String newName = "New Sales Report";
        Report cloned = Report.builder()
            .reportId("NEW_SALES_REPORT")
            .version(1)
            .reportName(newName)
            .status("draft")
            .build();

        when(reportCloneService.cloneReport(eq(sourceId), eq(newName))).thenReturn(cloned);

        mockMvc.perform(post("/api/v1/reports/" + sourceId + "/clone")
                .param("newName", newName)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reportId").value("NEW_SALES_REPORT"))
            .andExpect(jsonPath("$.reportName").value(newName))
            .andExpect(jsonPath("$.status").value("draft"))
            .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("POST /api/v1/reports/{id}/clone: returns 400 Bad Request on duplicate name")
    public void cloneReport_duplicateName_shouldReturn400() throws Exception {
        String sourceId = "RPT_1";
        String newName = "Existing Report";

        when(reportCloneService.cloneReport(eq(sourceId), eq(newName)))
            .thenThrow(new IllegalArgumentException("A report named 'Existing Report' already exists"));

        mockMvc.perform(post("/api/v1/reports/" + sourceId + "/clone")
                .param("newName", newName)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("A report named 'Existing Report' already exists"));
    }
}
