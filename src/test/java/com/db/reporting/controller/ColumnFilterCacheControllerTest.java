package com.db.reporting.controller;

import com.db.reporting.config.SecurityConfiguration;
import com.db.reporting.service.ColumnFilterCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ColumnFilterCacheController.class)
@Import(SecurityConfiguration.class)
@DisplayName("ColumnFilterCacheController Unit Tests")
@WithMockUser(username = "admin", roles = {"USER"})
@SuppressWarnings("null")
public class ColumnFilterCacheControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ColumnFilterCacheService filterCacheService;

    @Test
    @DisplayName("GET /api/v1/metadata/filters/{tableName}/{columnName}: returns cached filter payload")
    public void getCachedFilterValues_validTableAndColumn_shouldReturnPayload() throws Exception {
        when(filterCacheService.getFilterValues(eq("dim_products"), eq("product_type")))
                .thenReturn(List.of("Lending", "Mortgage"));

        mockMvc.perform(get("/api/v1/metadata/filters/dim_products/product_type"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tableName").value("dim_products"))
                .andExpect(jsonPath("$.columnName").value("product_type"))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.values[0]").value("Lending"))
                .andExpect(jsonPath("$.values[1]").value("Mortgage"));
    }
}
