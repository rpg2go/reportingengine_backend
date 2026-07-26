package com.db.reporting.controller;

import com.db.reporting.config.SecurityConfiguration;
import com.db.reporting.domain.Report;
import com.db.reporting.repository.ReportRepository;
import com.db.reporting.service.VersioningService;
import com.db.reporting.cache.MetadataCache;
import com.db.reporting.catalog.SchemaCatalogLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = { ReportVersionController.class })
@Import(SecurityConfiguration.class)
@DisplayName("ReportVersionController Slice Tests")
@WithMockUser(username = "admin", roles = { "USER" })
@SuppressWarnings("null")
public class ReportVersionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReportRepository reportRepository;

    @MockitoBean
    private VersioningService versioningService;

    @MockitoBean
    private MetadataCache metadataCache;

    @MockitoBean
    private SchemaCatalogLoader schemaCatalogLoader;

    @Test
    @DisplayName("GET /api/reports/{id}/version/list: lists all versions ordered descending")
    public void listVersions_shouldReturnVersionList() throws Exception {
        String reportId = "RPT_10";
        Report v1 = Report.builder().reportId(reportId).version(1).status("published").build();
        Report v2 = Report.builder().reportId(reportId).version(2).status("draft").build();

        when(reportRepository.findByReportIdAndDeletedFalseOrderByVersionDesc(reportId))
                .thenReturn(List.of(v2, v1));

        mockMvc.perform(get("/api/reports/" + reportId + "/version/list")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].version").value(2))
                .andExpect(jsonPath("$[1].version").value(1));
    }

    @Test
    @DisplayName("POST /api/reports/{id}/version/submit-review: submits draft for review successfully")
    public void submitForReview_success_shouldReturn200() throws Exception {
        String reportId = "RPT_10";
        int version = 1;

        mockMvc.perform(post("/api/reports/" + reportId + "/version/submit-review")
                        .param("version", String.valueOf(version))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("in_review"));
    }

    @Test
    @DisplayName("POST /api/reports/{id}/version/publish: publishes report version successfully")
    public void publish_success_shouldReturn200() throws Exception {
        String reportId = "RPT_10";
        int version = 1;

        mockMvc.perform(post("/api/reports/" + reportId + "/version/publish")
                        .param("version", String.valueOf(version))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedVersion").value(1));
    }

    @Test
    @DisplayName("POST /api/reports/{id}/version/fork: creates next draft version")
    public void fork_success_shouldReturnNextDraftVersion() throws Exception {
        String reportId = "RPT_10";
        int version = 1;
        Report newDraft = Report.builder().reportId(reportId).version(2).status("draft").build();

        when(versioningService.fork(eq(reportId), eq(version))).thenReturn(newDraft);

        mockMvc.perform(post("/api/reports/" + reportId + "/version/fork")
                        .param("version", String.valueOf(version))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextDraftVersion").value(2));
    }

    @Test
    @DisplayName("POST /api/reports/{id}/version/submit-review: returns 400 Bad Request on invalid state transition")
    public void submitForReview_invalidState_shouldReturn400() throws Exception {
        String reportId = "RPT_10";
        int version = 1;

        doThrow(new IllegalStateException("Cannot be submitted for review. Current status: PUBLISHED"))
                .when(versioningService).submitForReview(eq(reportId), eq(version));

        mockMvc.perform(post("/api/reports/" + reportId + "/version/submit-review")
                        .param("version", String.valueOf(version))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Cannot be submitted for review. Current status: PUBLISHED"));
    }
}
