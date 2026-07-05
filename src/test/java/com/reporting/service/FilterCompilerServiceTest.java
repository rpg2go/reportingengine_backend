package com.reporting.service;

import com.reporting.service.SqlGeneratorService.RowFilterGroup;
import com.reporting.service.SqlGeneratorService.RowFilterRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FilterCompilerService Unit Tests")
public class FilterCompilerServiceTest {

    private final FilterCompilerService compilerService = new FilterCompilerService();

    @Test
    @DisplayName("compile flat rule node successfully")
    public void compile_flatRuleNode() {
        RuleNode node = new RuleNode("analytics.fact_sales", "amount", ">=", List.of("1000"));
        String sql = compilerService.compile(node);
        assertThat(sql).isEqualTo("analytics.fact_sales.amount >= '1000'");
    }

    @Test
    @DisplayName("compile rule node with operators mapping successfully")
    public void compile_ruleNodeOperators() {
        // equal operator
        RuleNode eqNode = new RuleNode("dim_customers", "country", "=", List.of("US"));
        assertThat(compilerService.compile(eqNode)).isEqualTo("dim_customers.country = 'US'");

        // is not operator
        RuleNode neNode = new RuleNode("dim_customers", "country", "is not", List.of("US"));
        assertThat(compilerService.compile(neNode)).isEqualTo("(dim_customers.country <> 'US' OR dim_customers.country IS NULL)");

        // contains operator
        RuleNode containsNode = new RuleNode("dim_customers", "name", "contains", List.of("Corp"));
        assertThat(compilerService.compile(containsNode)).isEqualTo("dim_customers.name ILIKE '%Corp%' ESCAPE '\\'");

        // is blank operator
        RuleNode blankNode = new RuleNode("dim_customers", "region", "is blank", List.of());
        assertThat(compilerService.compile(blankNode)).isEqualTo("(dim_customers.region IS NULL OR TRIM(CAST(dim_customers.region AS TEXT)) = '')");
    }

    @Test
    @DisplayName("compile nested group node with logical conjunctions successfully")
    public void compile_nestedGroupNode() {
        RuleNode r1 = new RuleNode("fact_sales", "amount", ">", List.of("5000"));
        RuleNode r2 = new RuleNode("dim_customers", "status", "=", List.of("active"));
        
        GroupNode root = new GroupNode("AND", List.of(r1, r2));
        String sql = compilerService.compile(root);
        assertThat(sql).isEqualTo("(fact_sales.amount > '5000' AND dim_customers.status = 'active')");
    }

    @Test
    @DisplayName("compile deeply nested conjunction and disjunction groups successfully")
    public void compile_deeplyNestedConjunctionDisjunction() {
        RuleNode r1 = new RuleNode("fact_sales", "amount", ">", List.of("5000"));
        RuleNode r2 = new RuleNode("dim_customers", "status", "=", List.of("active"));
        GroupNode g1 = new GroupNode("AND", List.of(r1, r2));

        RuleNode r3 = new RuleNode("dim_products", "category", "=", List.of("loans"));
        GroupNode root = new GroupNode("OR", List.of(g1, r3));

        String sql = compilerService.compile(root);
        assertThat(sql).isEqualTo("((fact_sales.amount > '5000' AND dim_customers.status = 'active') OR dim_products.category = 'loans')");
    }

    @Test
    @DisplayName("buildAst maps deserialized RowFilterGroup DTO to AST FilterNode successfully")
    public void buildAst_mapsDtoToAst() {
        RowFilterRule rule = new RowFilterRule("fact_sales", "amount", ">", List.of("1000"));
        RowFilterGroup group = new RowFilterGroup("g1", "AND", List.of(rule), List.of());

        FilterNode ast = compilerService.buildAst(group);
        assertThat(ast).isInstanceOf(GroupNode.class);

        GroupNode gNode = (GroupNode) ast;
        assertThat(gNode.logicalOperator()).isEqualTo("AND");
        assertThat(gNode.children()).hasSize(1);
        assertThat(gNode.children().get(0)).isInstanceOf(RuleNode.class);

        RuleNode rNode = (RuleNode) gNode.children().get(0);
        assertThat(rNode.tableName()).isEqualTo("fact_sales");
        assertThat(rNode.columnName()).isEqualTo("amount");
        assertThat(rNode.operator()).isEqualTo(">");
        assertThat(rNode.values()).containsExactly("1000");
    }

    @Test
    @DisplayName("validateFilterExpr throws exception on dangerous SQL patterns")
    public void validateFilterExpr_throwsOnDangerousPatterns() {
        assertThatThrownBy(() -> compilerService.validateFilterExpr("amount > 1000; DROP TABLE users;"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or dangerous SQL sequences");

        assertThatThrownBy(() -> compilerService.validateFilterExpr("amount > 1000 -- comment"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid or dangerous SQL sequences");
    }

    @Test
    @DisplayName("validateFilterExpr throws exception on unmatched parentheses")
    public void validateFilterExpr_throwsOnUnmatchedParentheses() {
        assertThatThrownBy(() -> compilerService.validateFilterExpr("((amount > 1000)"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unmatched parentheses");

        assertThatThrownBy(() -> compilerService.validateFilterExpr("(amount > 1000))"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unmatched parentheses");
    }
}
