package com.db.reporting.controller;

import com.db.reporting.config.SecurityConfiguration;
import com.db.reporting.cache.MetadataCache;
import com.db.reporting.catalog.SchemaCatalogLoader;
import com.db.reporting.catalog.MetaColumn;
import com.db.reporting.catalog.MetaTable;
import com.db.reporting.service.AnalyticsQueryDispatcher;
import com.db.reporting.service.ColumnFilterCacheService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = { SchemaDiscoveryController.class })
@Import(SecurityConfiguration.class)
@DisplayName("SchemaDiscoveryController Slice Tests")
@WithMockUser(username = "admin", roles = { "USER" })
@SuppressWarnings("null")
public class SchemaDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NamedParameterJdbcTemplate jdbcTemplate;

    @MockitoBean
    private MetadataCache metadataCache;

    @MockitoBean
    private SchemaCatalogLoader schemaCatalogLoader;

    @MockitoBean
    private AnalyticsQueryDispatcher analyticsQueryDispatcher;

    @MockitoBean
    private ColumnFilterCacheService columnFilterCacheService;

    @Test
    @DisplayName("GET /api/reports/tables: returns list of fully-qualified analytics tables")
    public void listTables_shouldReturnQualifiedTableList() throws Exception {
        when(metadataCache.getTableColumnsCache()).thenReturn(Map.of(
                "analytics.fact_sales", Set.of("id", "amount"),
                "analytics.dim_customers", Set.of("id", "name")
        ));

        mockMvc.perform(get("/api/reports/tables")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("analytics.dim_customers"))
                .andExpect(jsonPath("$[1]").value("analytics.fact_sales"));
    }

    @Test
    @DisplayName("GET /api/reports/table-columns: returns visible column names for registered table")
    public void listTableColumns_shouldReturnColumns() throws Exception {
        MetaTable metaTable = mock(MetaTable.class);
        MetaColumn col1 = new MetaColumn(1, 1, "amount", "NUMERIC", false, false, "Amount", true, true, true);
        MetaColumn col2 = new MetaColumn(2, 1, "region", "VARCHAR", false, false, "Region", true, true, true);
        when(metaTable.getColumns()).thenReturn(List.of(col1, col2));

        when(schemaCatalogLoader.findTable("analytics.fact_sales")).thenReturn(metaTable);
        when(metadataCache.getColumns("analytics.fact_sales")).thenReturn(Set.of("amount", "region"));

        mockMvc.perform(get("/api/reports/table-columns")
                        .param("table", "analytics.fact_sales")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("amount"))
                .andExpect(jsonPath("$[1]").value("region"));
    }

    @Test
    @DisplayName("GET /api/reports/column-values: fetches distinct autocomplete values")
    public void getColumnValues_shouldReturnDistinctValues() throws Exception {
        MetaTable metaTable = new MetaTable(1, "analytics", "fact_sales", MetaTable.TableType.fact, "date_key", "Fact Sales");
        MetaColumn col = new MetaColumn(1, metaTable.getTableId(), "region", "VARCHAR", false, false, "Region", true, true, true);

        when(metadataCache.getTableColumnsCache()).thenReturn(Map.of("analytics.fact_sales", Set.of("region")));
        when(schemaCatalogLoader.findColumn("analytics.fact_sales", "region")).thenReturn(col);
        when(analyticsQueryDispatcher.queryForList(anyString(), eq(String.class)))
                .thenReturn(List.of("EMEA", "LATAM", "NORTH_AMERICA"));

        mockMvc.perform(get("/api/reports/column-values")
                        .param("table", "analytics.fact_sales")
                        .param("column", "region")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("EMEA"))
                .andExpect(jsonPath("$[1]").value("LATAM"))
                .andExpect(jsonPath("$[2]").value("NORTH_AMERICA"));

        mockMvc.perform(get("/api/reports/dimensions/values")
                        .param("table", "analytics.fact_sales")
                        .param("column", "region")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("EMEA"));
    }
}
