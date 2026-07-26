package com.db.reporting.catalog;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SchemaGraphRouter Dijkstra BFS Pathfinder Unit Tests")
class SchemaGraphRouterTest {

    @Mock
    private SchemaCatalogLoader catalogLoader;

    private SchemaGraphRouter router;

    private MetaTable factSales;
    private MetaTable dimAccounts;
    private MetaTable dimCustomers;

    @BeforeEach
    void setUp() {
        router = new SchemaGraphRouter(catalogLoader);

        factSales = new MetaTable(1, "analytics", "fact_sales", MetaTable.TableType.fact, "transaction_date", "Sales Fact Table");
        dimAccounts = new MetaTable(2, "analytics", "dim_accounts", MetaTable.TableType.dimension, null, "Accounts Dim Table");
        dimCustomers = new MetaTable(3, "analytics", "dim_customers", MetaTable.TableType.dimension, null, "Customers Dim Table");
    }

    @Test
    @DisplayName("Should return empty list when catalog is unavailable")
    void shouldReturnEmptyWhenCatalogUnavailable() {
        when(catalogLoader.isCatalogAvailable()).thenReturn(false);

        List<String> joins = router.computeJoinClauses("analytics.fact_sales", Set.of("analytics.dim_customers"));
        assertThat(joins).isEmpty();
    }

    @Test
    @DisplayName("Should return empty list when baseTable or targetTables are null/blank")
    void shouldReturnEmptyWhenParametersInvalid() {
        when(catalogLoader.isCatalogAvailable()).thenReturn(true);

        assertThat(router.computeJoinClauses(null, Set.of("analytics.dim_customers"))).isEmpty();
        assertThat(router.computeJoinClauses("analytics.fact_sales", null)).isEmpty();
        assertThat(router.computeJoinClauses("analytics.fact_sales", Collections.emptySet())).isEmpty();
    }

    @Test
    @DisplayName("Should compute multi-hop shortest path via Dijkstra BFS")
    void shouldComputeMultiHopShortestPath() {
        when(catalogLoader.isCatalogAvailable()).thenReturn(true);
        when(catalogLoader.findTable("analytics.fact_sales")).thenReturn(factSales);
        when(catalogLoader.findTable("analytics.dim_customers")).thenReturn(dimCustomers);

        // Edge 1: fact_sales (account_id) -> dim_accounts (account_id) (conformed = true -> weight 1)
        MetaRelationship edge1 = new MetaRelationship(1, factSales, "account_id", dimAccounts, "account_id", "LEFT", true, 1, "Account FK");
        factSales.addOutgoingEdge(edge1);

        // Edge 2: dim_accounts (customer_id) -> dim_customers (customer_id) (conformed = true -> weight 1)
        MetaRelationship edge2 = new MetaRelationship(2, dimAccounts, "customer_id", dimCustomers, "customer_id", "LEFT", true, 1, "Customer FK");
        dimAccounts.addOutgoingEdge(edge2);

        List<String> joins = router.computeJoinClauses("analytics.fact_sales", Set.of("analytics.dim_customers"));

        assertThat(joins).hasSize(2);
        assertThat(joins.get(0)).contains("analytics.dim_accounts ON analytics.dim_accounts.account_id = analytics.fact_sales.account_id");
        assertThat(joins.get(1)).contains("analytics.dim_customers ON analytics.dim_customers.customer_id = analytics.dim_accounts.customer_id");
    }

    @Test
    @DisplayName("Should prefer conformed dimension key (weight 1) over non-conformed FK (weight 2)")
    void shouldPreferConformedEdgeOverNonConformed() {
        when(catalogLoader.isCatalogAvailable()).thenReturn(true);
        when(catalogLoader.findTable("analytics.fact_sales")).thenReturn(factSales);
        when(catalogLoader.findTable("analytics.dim_customers")).thenReturn(dimCustomers);

        // Path A: Direct non-conformed edge (weight 2)
        MetaRelationship edgeNonConformed = new MetaRelationship(1, factSales, "cust_code", dimCustomers, "cust_code", "LEFT", false, 2, "Legacy FK");
        factSales.addOutgoingEdge(edgeNonConformed);

        // Path B: Direct conformed edge (weight 1)
        MetaRelationship edgeConformed = new MetaRelationship(2, factSales, "customer_id", dimCustomers, "customer_id", "LEFT", true, 1, "Conformed FK");
        factSales.addOutgoingEdge(edgeConformed);

        List<String> joins = router.computeJoinClauses("analytics.fact_sales", Set.of("analytics.dim_customers"));

        assertThat(joins).hasSize(1);
        assertThat(joins.get(0)).contains("analytics.dim_customers.customer_id = analytics.fact_sales.customer_id");
    }
}
