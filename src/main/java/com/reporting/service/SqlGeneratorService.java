package com.reporting.service;

import com.reporting.cache.MetadataCache;
import com.reporting.catalog.SchemaGraphRouter;
import com.reporting.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlGeneratorService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Catalog-driven graph router that replaces all hardcoded join mappings.
     * Resolves multi-hop LEFT JOIN chains dynamically from the
     * {@code reporting.meta_relationship} catalog at query-generation time.
     */
    private final SchemaGraphRouter schemaGraphRouter;

    /**
     * Startup-loaded metadata cache: column sets, time-keys, sem-measures.
     * Eliminates repeated {@code information_schema} and {@code sem_view} round-trips
     * during query generation.
     */
    private final MetadataCache metadataCache;

    @Autowired
    public SqlGeneratorService(JdbcTemplate jdbcTemplate, SchemaGraphRouter schemaGraphRouter, MetadataCache metadataCache) {
        this.jdbcTemplate    = jdbcTemplate;
        this.schemaGraphRouter = schemaGraphRouter;
        this.metadataCache   = metadataCache;
    }

    public SqlGeneratorService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null, null);
    }

    public String generateMatrixQuery(ReportConfigDto config) {
        return generate(config, Collections.emptyMap());
    }

    public String generate(ReportConfigDto config, Map<String, ResolvedMetricDto> resolved) {
        List<String> selectedGranularities = new ArrayList<>();
        if (config.getGranularity() != null && !config.getGranularity().isBlank()) {
            for (String s : config.getGranularity().split(",")) {
                String trimmed = s.trim();
                if (!trimmed.isEmpty()) {
                    selectedGranularities.add(trimmed);
                }
            }
        }
        return generate(config, resolved, selectedGranularities);
    }

    public String generate(ReportConfigDto config, Map<String, ResolvedMetricDto> resolved, List<String> selectedGranularities) {
        // Parse Filters
        List<FilterCondition> quickFilters = parseGeneralFilters(config.getQuickFilters());

        // 1. Discover all unique sourceTable entries
        Set<String> uniqueTables = new LinkedHashSet<>();
        if (config.getRows() != null) {
            for (ReportRowDto row : config.getRows()) {
                if (row.isDataRow() && row.source() != null) {
                    String tbl = row.source().getTable();
                    if (tbl != null && !tbl.isBlank()) {
                        uniqueTables.add(tbl.trim());
                    }
                }
            }
        }

        if (uniqueTables.isEmpty()) {
            return "SELECT '' AS row_id, '' AS col_id, 0.0::DOUBLE PRECISION AS val WHERE FALSE";
        }

        // 2. Identify the conformed key variable linking these domains
        Map<String, Set<String>> tableColumnsCache = new HashMap<>();
        String conformedKey = findConformedKey(uniqueTables, tableColumnsCache);

        List<String> granularities = new ArrayList<>(selectedGranularities);
        if (granularities.isEmpty()) {
            granularities.add(conformedKey);
        }

        List<String> granularityAliases = new ArrayList<>();
        for (String g : granularities) {
            granularityAliases.add(getGranularityAlias(g));
        }

        // 3. Build CTE definitions for each unique table
        Map<String, String> tableToCteName = new HashMap<>();
        for (String table : uniqueTables) {
            String shortTbl = table;
            if (table.contains(".")) {
                shortTbl = table.substring(table.lastIndexOf(".") + 1);
            }
            String cteName = "cte_" + shortTbl.trim().toLowerCase();
            tableToCteName.put(table.trim().toLowerCase(), cteName);
        }

        List<String> cteDefinitions = new ArrayList<>();
        for (String factTable : uniqueTables) {
            String cteName = tableToCteName.get(factTable.trim().toLowerCase());

            // ── Collect dimension tables required by this fact CTE ─────────────────
            // We gather targets from two sources:
            //   (a) dimensions explicitly referenced by structured row filter conditions
            //   (b) the conformed-key dimension when the fact table does not carry the
            //       conformed key directly (e.g. fact_banking_transactions uses
            //       account_id, not customer_id; the router resolves the hop chain).
            Set<String> dimensionTargets = new LinkedHashSet<>();

            // (a) Structured row-level filter dimensions for this fact table
            for (ReportRowDto row : config.getRows()) {
                if (row.isDataRow() && row.source() != null) {
                    String rowTable = row.source().getTable();
                    if (rowTable != null && rowTable.trim().equalsIgnoreCase(factTable.trim())) {
                        String rowFilter = row.filterExpr();
                        if (rowFilter != null && !rowFilter.isBlank()) {
                            String trimmed = rowFilter.trim();
                            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                                List<FilterCondition> conds = parseGeneralFilters(trimmed);
                                for (FilterCondition cond : conds) {
                                    if (cond.getDimTable() != null && !cond.getDimTable().isBlank()) {
                                        dimensionTargets.add(cond.getDimTable().trim());
                                    }
                                }
                            } else if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                                try {
                                    ObjectMapper mapper = new ObjectMapper();
                                    mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                                    RowFilterGroup rootGroup = mapper.readValue(trimmed, RowFilterGroup.class);
                                    collectReferencedTables(rootGroup, factTable, dimensionTargets, tableColumnsCache);
                                } catch (Exception e) {
                                    // ignore/handle parsing errors in compilation later
                                }
                            }
                        }
                    }
                }
            }

            // Process general filters: collect targets and compile SQL block for this table
            String generalBlock = compileGeneralFiltersForTable(config.getGeneralFilters(), factTable, dimensionTargets, tableColumnsCache);
            if (generalBlock != null && !generalBlock.isBlank()) {
                generalBlock = "(" + generalBlock + ")";
            } else {
                generalBlock = "";
            }

            for (FilterCondition cond : quickFilters) {
                if (cond.getDimTable() != null && !cond.getDimTable().isBlank()) {
                    if (isFilterApplicable(factTable, cond, tableColumnsCache)) {
                        dimensionTargets.add(cond.getDimTable().trim());
                    }
                }
            }

            // (b) When the fact table does not directly hold the conformed key, add the
            //     conformed dimension as a target so the router can resolve the chain.
            //     The router handles all intermediate hops (e.g. fact_banking_transactions
            //     → dim_accounts → dim_customers) without any hardcoded table names.
            if (!columnExists(factTable, conformedKey, tableColumnsCache)) {
                // Determine which dimension table owns the conformed key as a PK.
                // The catalog's graph router will find the shortest path via FKs.
                // We delegate to the router rather than naming tables explicitly.
                // Adding the conformed key dimension name allows the router to build
                // the intermediate hop chain automatically.
                //
                // We attempt to resolve by name convention first; if the catalog is
                // unavailable the router will return an empty list and the CTE
                // will still compile (without the bridging join).
                String conformedDimGuess = resolveConformedDimension(conformedKey);
                if (conformedDimGuess != null) {
                    dimensionTargets.add(conformedDimGuess);
                }
            }

            // Pre-resolve granularity target dimensions so they are joined correctly
            for (String g : granularities) {
                String cleanGran = g.trim();
                if (cleanGran.contains(".")) {
                    String tblPart = cleanGran.substring(0, cleanGran.lastIndexOf("."));
                    if (tblPart.contains(".")) {
                        tblPart = tblPart.substring(tblPart.lastIndexOf(".") + 1);
                    }
                    if (!tblPart.equalsIgnoreCase(factTable)) {
                        dimensionTargets.add(tblPart.trim());
                    }
                } else {
                    if ("customer_id".equalsIgnoreCase(cleanGran) && factTable.toLowerCase().contains("fact_banking_transactions")) {
                        dimensionTargets.add("dim_accounts");
                    } else {
                        String conformedDim = resolveConformedDimension(cleanGran);
                        if (conformedDim != null) {
                            dimensionTargets.add(conformedDim.trim());
                        }
                    }
                }
            }

            // ── Delegate all JOIN clause assembly to SchemaGraphRouter ────────────
            // The router returns a topologically ordered, de-duplicated list of SQL
            // LEFT JOIN strings.  No table names, column names, or multi-hop logic
            // are hardcoded here.
            List<String> joinClauses = schemaGraphRouter != null
                ? schemaGraphRouter.computeJoinClauses(factTable, dimensionTargets)
                : Collections.emptyList();

            // Build FROM and JOIN
            StringBuilder fromClause = new StringBuilder("FROM " + factTable);
            for (String joinClause : joinClauses) {
                fromClause.append("\n    ").append(joinClause);
            }

            // Granularity fields Select & Group expressions
            List<String> selectKeyExprs = new ArrayList<>();
            List<String> groupByExprs = new ArrayList<>();
            for (String g : granularities) {
                String expr = resolveGranularityExpression(factTable, g, dimensionTargets, tableColumnsCache);
                String alias = getGranularityAlias(g);
                String selectKeyExpr = expr.contains(" AS ") ? expr : (expr + " AS " + alias);
                selectKeyExprs.add(selectKeyExpr);
                if (!expr.contains("CAST(NULL")) {
                    groupByExprs.add(expr);
                }
            }
            String groupByExpr = buildGroupByClause(groupByExprs);

            // ── Hierarchical Parenthetical Partitioning Strategy ─────────────────
            
            // 1. Compile Quick Filters Block
            StringBuilder quickBuilder = new StringBuilder();
            for (FilterCondition cond : quickFilters) {
                if (isFilterApplicable(factTable, cond, tableColumnsCache)) {
                    String sqlCond = compileFactFilter(factTable, cond);
                    if (sqlCond != null && !sqlCond.isBlank()) {
                        if (quickBuilder.length() > 0) {
                            quickBuilder.append(" AND ");
                        }
                        quickBuilder.append(sqlCond);
                    }
                }
            }
            String quickBlock = "";
            if (quickBuilder.length() > 0) {
                quickBlock = "(" + quickBuilder.toString() + ")";
            }

            // 3. Master Logic Intersection Execution
            String combinedFilters = "";
            if (!quickBlock.isEmpty() && !generalBlock.isEmpty()) {
                combinedFilters = quickBlock + " AND " + generalBlock;
            } else if (!quickBlock.isEmpty()) {
                combinedFilters = quickBlock;
            } else if (!generalBlock.isEmpty()) {
                combinedFilters = generalBlock;
            }

            String whereClause = "";
            if (!combinedFilters.isEmpty()) {
                whereClause = "\n    WHERE " + combinedFilters;
            }

            // Build aggregations for each active data row + column for this table
            List<String> selectList = new ArrayList<>();
            selectList.addAll(selectKeyExprs);

            for (ReportRowDto row : config.getRows()) {
                if (!row.isDataRow()) {
                    continue;
                }
                MeasureDefinitionDTO mdef = row.source();
                if (mdef == null) {
                    continue;
                }
                String rowTable = mdef.getTable();
                if (rowTable == null || rowTable.isBlank() || !rowTable.trim().equalsIgnoreCase(factTable.trim())) {
                    continue;
                }

                for (ColumnDefDto col : config.getColumns()) {
                    if (col.colType() == Enums.ColType.CALC) {
                        continue;
                    }
                    if (!row.isEnabledFor(col.colId())) {
                        continue;
                    }

                    // Reuse outer scope timeKey and prefixedTimeKey

                    String timeKey = getTimeKeyForTable(factTable);
                    String prefixedTimeKey = factTable + "." + timeKey;

                    LocalDate[] boundaries = DateUtils.getPeriodBoundaries(
                        config.getReferenceDate(),
                        col.colType(),
                        col.periodOffset(),
                        col.rollingN(),
                        col.effectiveRollingGrain()   // DAY | WEEK | MONTH (null → WEEK)
                    );
                    LocalDate start = boundaries[0];
                    LocalDate end = boundaries[1];

                    String dateConstraint = String.format("%s >= '%s' AND %s <= '%s'", prefixedTimeKey, start.toString(), prefixedTimeKey, end.toString());

                    String rowFilter = row.filterExpr();
                    String filterClause = "";
                    if (rowFilter != null && !rowFilter.isBlank()) {
                        String compiledRowFilter = compileRowFilter(rowFilter);
                        if (compiledRowFilter != null && !compiledRowFilter.isBlank()) {
                            filterClause = " AND (" + compiledRowFilter + ")";
                        }
                    }

                    String metricClause = compileMetricClause(factTable, mdef, dateConstraint, filterClause);
                    String alias = "val_" + row.rowId().toLowerCase() + "_" + col.colId().toLowerCase();
                    selectList.add(String.format("CAST(%s AS DOUBLE PRECISION) AS %s", metricClause, alias));

                    if (col.colType() == Enums.ColType.ROLLING) {
                        int rollingN = col.rollingN() != null ? col.rollingN() : 1;
                        String grain = col.effectiveRollingGrain();
                        for (int i = 1; i <= rollingN; i++) {
                            LocalDate subStart;
                            LocalDate subEnd;
                            if ("DAY".equals(grain)) {
                                subStart = config.getReferenceDate().minusDays(i);
                                subEnd = config.getReferenceDate().minusDays(i);
                            } else if ("MONTH".equals(grain)) {
                                LocalDate[] b = DateUtils.getPeriodBoundaries(config.getReferenceDate(), Enums.ColType.MTD, -i, null, null);
                                subStart = b[0];
                                subEnd = b[1];
                            } else { // WEEK
                                LocalDate[] b = DateUtils.getPeriodBoundaries(config.getReferenceDate(), Enums.ColType.WEEK, -i, null, null);
                                subStart = b[0];
                                subEnd = b[1];
                            }
                            String subDateConstraint = String.format("%s >= '%s' AND %s <= '%s'", prefixedTimeKey, subStart.toString(), prefixedTimeKey, subEnd.toString());
                            String subMetricClause = compileMetricClause(factTable, mdef, subDateConstraint, filterClause);
                            String subAlias = "val_" + row.rowId().toLowerCase() + "_" + col.colId().toLowerCase() + "_" + i;
                            selectList.add(String.format("CAST(%s AS DOUBLE PRECISION) AS %s", subMetricClause, subAlias));
                        }
                    }
                }
            }

            String cteSql = String.format(
                "  %s AS (\n    SELECT\n      %s\n    %s%s\n    GROUP BY %s\n  )",
                cteName,
                String.join(",\n      ", selectList),
                fromClause.toString(),
                whereClause,
                groupByExpr
            );
            cteDefinitions.add(cteSql);
        }

        // 4. Build unified_spine CTE using UNION DISTINCT
        List<String> spineSelects = new ArrayList<>();
        String aliasCsv = String.join(", ", granularityAliases);
        List<String> spineFilters = new ArrayList<>();
        for (String alias : granularityAliases) {
            spineFilters.add(alias + " IS NOT NULL");
        }
        String spineFilterStr = String.join(" AND ", spineFilters);

        for (String table : uniqueTables) {
            String cteName = tableToCteName.get(table.trim().toLowerCase());
            spineSelects.add(String.format("    SELECT %s FROM %s WHERE %s", aliasCsv, cteName, spineFilterStr));
        }

        String unifiedSpineCte = String.format(
            "  unified_spine AS (\n" +
            "    SELECT DISTINCT %s FROM (\n" +
            "%s\n" +
            "    ) spine_raw\n" +
            "  )",
            aliasCsv,
            String.join("\n    UNION DISTINCT\n", spineSelects)
        );
        cteDefinitions.add(unifiedSpineCte);

        // 5. Build combined_data CTE
        List<String> combinedSelectList = new ArrayList<>();
        for (String alias : granularityAliases) {
            combinedSelectList.add("s." + alias);
        }

        for (String table : uniqueTables) {
            String cteName = tableToCteName.get(table.trim().toLowerCase());
            for (ReportRowDto row : config.getRows()) {
                if (!row.isDataRow()) {
                    continue;
                }
                MeasureDefinitionDTO mdef = row.source();
                if (mdef == null) {
                    continue;
                }
                String rowTable = mdef.getTable();
                if (rowTable == null || rowTable.isBlank() || !rowTable.trim().equalsIgnoreCase(table.trim())) {
                    continue;
                }
                for (ColumnDefDto col : config.getColumns()) {
                    if (col.colType() == Enums.ColType.CALC) {
                        continue;
                    }
                    if (!row.isEnabledFor(col.colId())) {
                        continue;
                    }
                    String alias = "val_" + row.rowId().toLowerCase() + "_" + col.colId().toLowerCase();
                    combinedSelectList.add(String.format("COALESCE(%s.%s, 0) AS %s", cteName, alias, alias));

                    if (col.colType() == Enums.ColType.ROLLING) {
                        int rollingN = col.rollingN() != null ? col.rollingN() : 1;
                        for (int i = 1; i <= rollingN; i++) {
                            String subColId = col.colId() + "_" + i;
                            String subAlias = "val_" + row.rowId().toLowerCase() + "_" + subColId.toLowerCase();
                            combinedSelectList.add(String.format("COALESCE(%s.%s, 0) AS %s", cteName, subAlias, subAlias));
                        }
                    }
                }
            }
        }

        StringBuilder combinedFrom = new StringBuilder("FROM unified_spine s");
        for (String table : uniqueTables) {
            String cteName = tableToCteName.get(table.trim().toLowerCase());
            List<String> joinConditions = new ArrayList<>();
            for (String alias : granularityAliases) {
                joinConditions.add(String.format("%s.%s = s.%s", cteName, alias, alias));
            }
            combinedFrom.append(String.format("\n    LEFT JOIN %s ON %s", cteName, String.join(" AND ", joinConditions)));
        }

        String combinedCte = String.format(
            "  combined_data AS (\n    SELECT\n      %s\n    %s\n  )",
            String.join(",\n      ", combinedSelectList),
            combinedFrom.toString()
        );
        cteDefinitions.add(combinedCte);

        // 6. Build final selects for unpivoting
        List<String> finalSelects = new ArrayList<>();

        // Find first non-CALC column to define the report's current reference date boundaries
        ColumnDefDto currentPeriodCol = null;
        for (ColumnDefDto col : config.getColumns()) {
            if (col.colType() != Enums.ColType.CALC) {
                currentPeriodCol = col;
                break;
            }
        }

        LocalDate startBound = null;
        LocalDate endBound = null;
        if (currentPeriodCol != null) {
            LocalDate[] boundaries = DateUtils.getPeriodBoundaries(
                config.getReferenceDate(),
                currentPeriodCol.colType(),
                currentPeriodCol.periodOffset(),
                currentPeriodCol.rollingN(),
                currentPeriodCol.effectiveRollingGrain()
            );
            startBound = boundaries[0];
            endBound = boundaries[1];
        }

        String firstTable = uniqueTables.iterator().next();
        String timeKey = getTimeKeyForTable(firstTable);
        String timeKeyAlias = getGranularityAlias(timeKey);

        StringBuilder granTotalCols = new StringBuilder();
        StringBuilder granBreakdownCols = new StringBuilder();
        for (String gAlias : granularityAliases) {
            granTotalCols.append(", CAST(NULL AS VARCHAR) AS ").append(gAlias);
            granBreakdownCols.append(", ").append(gAlias);
        }

        for (ReportRowDto row : config.getRows()) {
            if (!row.isDataRow()) {
                continue;
            }
            MeasureDefinitionDTO mdef = row.source();
            if (mdef == null) {
                continue;
            }
            for (ColumnDefDto col : config.getColumns()) {
                if (col.colType() == Enums.ColType.CALC) {
                    continue;
                }
                if (!row.isEnabledFor(col.colId())) {
                    continue;
                }

                String alias = "val_" + row.rowId().toLowerCase() + "_" + col.colId().toLowerCase();
                String finalAgg = "SUM";
                if ("visual".equalsIgnoreCase(mdef.getMode())) {
                    String agg = mdef.getAggregation() != null ? mdef.getAggregation().trim().toUpperCase() : "SUM";
                    if ("AVG".equals(agg) || "MAX".equals(agg) || "MIN".equals(agg)) {
                        finalAgg = agg;
                    }
                }

                // Standard total select
                String select = String.format(
                    "SELECT '%s' AS row_id, '%s' AS col_id, CAST(%s(%s) AS DOUBLE PRECISION) AS val%s FROM combined_data",
                    row.rowId().toUpperCase(),
                    col.colId().toUpperCase(),
                    finalAgg,
                    alias,
                    granTotalCols.toString()
                );
                finalSelects.add(select);

                // Filter sub-rows to only show the specific reporting date period if present in granularity
                String filterClause = "";
                if (startBound != null && endBound != null && granularityAliases.contains(timeKeyAlias)) {
                    filterClause = String.format(" WHERE %s >= '%s' AND %s <= '%s'", timeKeyAlias, startBound.toString(), timeKeyAlias, endBound.toString());
                }

                // Granularity breakdown select
                if (!granularityAliases.isEmpty()) {
                    String selectGran = String.format(
                        "SELECT '%s' AS row_id, '%s' AS col_id, CAST(%s(%s) AS DOUBLE PRECISION) AS val%s FROM combined_data%s GROUP BY %s",
                        row.rowId().toUpperCase(),
                        col.colId().toUpperCase(),
                        finalAgg,
                        alias,
                        granBreakdownCols.toString(),
                        filterClause,
                        String.join(", ", granularityAliases)
                    );
                    finalSelects.add(selectGran);
                }

                if (col.colType() == Enums.ColType.ROLLING) {
                    int rollingN = col.rollingN() != null ? col.rollingN() : 1;
                    for (int i = 1; i <= rollingN; i++) {
                        String subColId = col.colId() + "_" + i;
                        String subAlias = "val_" + row.rowId().toLowerCase() + "_" + subColId.toLowerCase();
                        String subSelect = String.format(
                            "SELECT '%s' AS row_id, '%s' AS col_id, CAST(%s(%s) AS DOUBLE PRECISION) AS val%s FROM combined_data",
                            row.rowId().toUpperCase(),
                            subColId.toUpperCase(),
                            finalAgg,
                            subAlias,
                            granTotalCols.toString()
                        );
                        finalSelects.add(subSelect);

                        if (!granularityAliases.isEmpty()) {
                            String subSelectGran = String.format(
                                "SELECT '%s' AS row_id, '%s' AS col_id, CAST(%s(%s) AS DOUBLE PRECISION) AS val%s FROM combined_data%s GROUP BY %s",
                                row.rowId().toUpperCase(),
                                subColId.toUpperCase(),
                                finalAgg,
                                subAlias,
                                granBreakdownCols.toString(),
                                filterClause,
                                String.join(", ", granularityAliases)
                            );
                            finalSelects.add(subSelectGran);
                        }
                    }
                }
            }
        }

        if (finalSelects.isEmpty()) {
            StringBuilder sb = new StringBuilder("SELECT '' AS row_id, '' AS col_id, 0.0::DOUBLE PRECISION AS val");
            for (String gAlias : granularityAliases) {
                sb.append(", CAST(NULL AS VARCHAR) AS ").append(gAlias);
            }
            sb.append(" WHERE FALSE");
            return sb.toString();
        }

        StringBuilder sql = new StringBuilder("WITH\n");
        sql.append(String.join(",\n", cteDefinitions)).append("\n");
        sql.append(String.join("\nUNION ALL\n", finalSelects));

        return sql.toString();
    }

    public String buildGroupByClause(List<String> granularities) {
        if (granularities == null || granularities.isEmpty()) {
            return "1";
        }
        return String.join(", ", granularities);
    }

    public static String getGranularityAlias(String granularity) {
        if (granularity.contains(".")) {
            return granularity.substring(granularity.lastIndexOf(".") + 1);
        }
        return granularity;
    }

    private String resolveGranularityExpression(String factTable, String granularity, Set<String> dimensionTargets, Map<String, Set<String>> cache) {
        String cleanGran = granularity.trim();
        String alias = getGranularityAlias(cleanGran);
        
        // If qualified, e.g., dim_customers.city or analytics.dim_customers.city
        if (cleanGran.contains(".")) {
            // Make sure the dimension table is added to the targets so it gets joined
            String tblPart = cleanGran.substring(0, cleanGran.lastIndexOf("."));
            if (tblPart.contains(".")) {
                tblPart = tblPart.substring(tblPart.lastIndexOf(".") + 1);
            }
            if (!tblPart.equalsIgnoreCase(factTable)) {
                dimensionTargets.add(tblPart);
            }
            return cleanGran;
        }
        
        // Special hardcoded rule for customer_id in banking transactions
        if ("customer_id".equalsIgnoreCase(cleanGran) && factTable.toLowerCase().contains("fact_banking_transactions")) {
            dimensionTargets.add("dim_accounts");
            return "analytics.dim_accounts.customer_id";
        }
        
        // Check if it exists in the fact table
        if (columnExists(factTable, cleanGran, cache)) {
            return factTable + "." + cleanGran;
        }
        
        // Check if it exists in a conformed dimension table
        String conformedDim = resolveConformedDimension(cleanGran);
        if (conformedDim != null) {
            dimensionTargets.add(conformedDim);
            String keyColumn = cleanGran;
            if (columnExists(conformedDim, keyColumn, cache)) {
                return conformedDim + "." + keyColumn;
            } else if (columnExists(conformedDim, "id", cache)) {
                return conformedDim + ".id";
            }
        }
        
        // Fallback: search all loaded dimension tables in targets
        for (String dim : dimensionTargets) {
            if (columnExists(dim, cleanGran, cache)) {
                return dim + "." + cleanGran;
            }
        }
        
        // Ultimate fallback
        return "CAST(NULL AS VARCHAR) AS " + alias;
    }

    private String findConformedKey(Set<String> factTables, Map<String, Set<String>> cache) {
        if (factTables.isEmpty()) {
            return "customer_id";
        }

        List<String> priorityKeys = List.of("customer_id", "account_id", "location_id", "rm_id");
        for (String key : priorityKeys) {
            boolean existsInAll = true;
            for (String table : factTables) {
                if (!columnExists(table, key, cache)) {
                    existsInAll = false;
                    break;
                }
            }
            if (existsInAll) {
                return key;
            }
        }

        String firstTable = factTables.iterator().next();
        Set<String> firstTableCols = cache.get(firstTable.trim().toLowerCase());
        if (firstTableCols != null) {
            for (String col : firstTableCols) {
                if (col.endsWith("_id")) {
                    boolean existsInAll = true;
                    for (String table : factTables) {
                        if (!columnExists(table, col, cache)) {
                            existsInAll = false;
                            break;
                        }
                    }
                    if (existsInAll) {
                        return col;
                    }
                }
            }
        }

        return "customer_id";
    }

    /**
     * Maps a conformed dimension key name to the dimension table that owns it
     * as a primary key.  Used when a fact table does not directly carry the
     * conformed key so that the graph router can plan the intermediate hop chain.
     *
     * <p>This method does <em>not</em> hardcode join predicates; it only provides
     * the target table name.  All JOIN SQL is generated by
     * {@link com.reporting.catalog.SchemaGraphRouter#computeJoinClauses}.</p>
     *
     * @param conformedKey the conformed dimension key, e.g. {@code "customer_id"}
     * @return the unqualified dimension table name, or {@code null} when unknown
     */
    private String resolveConformedDimension(String conformedKey) {
        if (conformedKey == null || conformedKey.isBlank()) {
            return null;
        }
        return switch (conformedKey.trim().toLowerCase()) {
            case "customer_id"   -> "dim_customers";
            case "location_id"   -> "dim_location";
            case "rm_id"         -> "dim_relationship_manager";
            case "date_key"      -> "dim_date";
            case "account_id"    -> "dim_accounts";
            case "product_id"    -> "dim_products";
            case "hier_id"       -> "dim_investment_hierarchy";
            default              -> null;
        };
    }

    /**
     * Rewrites a router-generated JOIN clause so that the ON predicate's
     * fact-table qualifier is replaced with {@code spine_raw}, which is the
     * alias used for the unified-spine sub-query in the spine CTE.
     *
     * <p>For example:</p>
     * <pre>{@code
     * Input:  "LEFT JOIN analytics.dim_location ON analytics.dim_location.id = analytics.fact_sales.location_id"
     * Output: "LEFT JOIN analytics.dim_location ON analytics.dim_location.id = spine_raw.location_id"
     * }</pre>
     *
     * @param joinClause    the raw JOIN clause produced by the router
     * @param factTableName the fact table whose qualifier must be removed
     * @return the rewritten JOIN clause with {@code spine_raw} qualification
     */
    private String rewriteSpineJoinClause(String joinClause, String factTableName) {
        if (joinClause == null || factTableName == null) {
            return joinClause;
        }
        // Replace both qualified (schema.table.column) and unqualified (table.column) fact-table prefixes
        String qualified = java.util.regex.Pattern.quote(factTableName.trim());
        String shortName = factTableName.contains(".")
            ? java.util.regex.Pattern.quote(factTableName.substring(factTableName.lastIndexOf(".") + 1).trim())
            : qualified;
        String result = joinClause.replaceAll("(?i)" + qualified + "\\.", "spine_raw.");
        result = result.replaceAll("(?i)(?<!\\.)" + shortName + "\\.", "spine_raw.");
        return result;
    }

    private String getTimeKeyForTable(String table) {
        // Fast path: check startup cache first (avoids a live DB round-trip per CTE)
        if (metadataCache != null) {
            String cached = metadataCache.getTimeKey(table);
            if (cached != null) {
                return cached;
            }
        }
        // Fallback: live query (handles tables not in sem_view or cache miss)
        try {
            return jdbcTemplate.queryForObject(
                "SELECT time_key FROM reporting.sem_view WHERE table_ref = ?",
                String.class,
                table
            );
        } catch (Exception e) {
            return "reporting_date";
        }
    }

    private boolean columnExists(String table, String column, Map<String, Set<String>> cache) {
        if (table == null || column == null || column.isBlank()) {
            return false;
        }
        String cleanTable = table.trim().toLowerCase();
        String cleanCol   = column.trim().toLowerCase();

        // Use the shared startup cache (backed by the per-request map for non-analytics tables)
        Map<String, Set<String>> effectiveCache = (metadataCache != null)
            ? metadataCache.getTableColumnsCache()
            : cache;

        Set<String> columns = effectiveCache.computeIfAbsent(cleanTable, t -> {
            String schema = "analytics";
            String tableName = t;
            if (t.contains(".")) {
                String[] parts = t.split("\\.");
                schema = parts[0].trim();
                tableName = parts[1].trim();
            }
            // Only reaches here for tables not pre-loaded at startup (e.g. reporting schema tables)
            String sql = "SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?";
            try {
                List<String> list = jdbcTemplate.queryForList(sql, String.class, schema, tableName);
                Set<String> set = new HashSet<>();
                for (String col : list) {
                    set.add(col.toLowerCase());
                }
                return set;
            } catch (Exception e) {
                return Collections.emptySet();
            }
        });

        return columns.contains(cleanCol);
    }

    private boolean isNumericColumn(String table, String column) {
        if (table == null || column == null || column.isBlank()) {
            return false;
        }
        String schema = "analytics";
        String tableName = table.trim();
        if (tableName.contains(".")) {
            String[] parts = tableName.split("\\.");
            schema = parts[0].trim();
            tableName = parts[1].trim();
        }
        String sql = "SELECT data_type FROM information_schema.columns " +
                     "WHERE table_schema = ? AND table_name = ? AND column_name = ?";
        try {
            String dataType = jdbcTemplate.queryForObject(sql, String.class, schema, tableName, column);
            if (dataType == null) {
                return false;
            }
            dataType = dataType.toLowerCase();
            return dataType.contains("int") || dataType.contains("num") || 
                   dataType.contains("double") || dataType.contains("real") || 
                   dataType.contains("float") || dataType.contains("decimal") || 
                   dataType.contains("precision");
        } catch (Exception e) {
            return false;
        }
    }

    public String makeDivisionSafe(String sql) {
        if (sql == null || !sql.contains("/")) {
            return sql;
        }
        StringBuilder sb = new StringBuilder();
        int len = sql.length();
        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);
            if (c == '/') {
                // Find start of denominator
                int start = i + 1;
                while (start < len && Character.isWhitespace(sql.charAt(start))) {
                    start++;
                }

                // If it already starts with NULLIF (case-insensitive) followed by '(', skip wrapping it
                if (start + 6 < len && sql.substring(start, start + 6).toUpperCase().equals("NULLIF")) {
                    int checkParen = start + 6;
                    while (checkParen < len && Character.isWhitespace(sql.charAt(checkParen))) {
                        checkParen++;
                    }
                    if (checkParen < len && sql.charAt(checkParen) == '(') {
                        sb.append('/');
                        continue;
                    }
                }

                // Extract the denominator expression
                int end = start;
                if (end < len && sql.charAt(end) == '(') {
                    // It starts with a parenthesis, so parse the balanced group
                    int open = 1;
                    end++;
                    while (end < len && open > 0) {
                        char ec = sql.charAt(end);
                        if (ec == '(') {
                            open++;
                        } else if (ec == ')') {
                            open--;
                        }
                        end++;
                    }
                } else {
                    // Simple identifier, column, function, or number.
                    // Keep consuming letters, digits, dots, underscores, and balanced parentheses/parameters
                    while (end < len) {
                        char ec = sql.charAt(end);
                        if (Character.isLetterOrDigit(ec) || ec == '_' || ec == '.') {
                            end++;
                        } else if (ec == '(') {
                            int open = 1;
                            end++;
                            while (end < len && open > 0) {
                                char innerC = sql.charAt(end);
                                if (innerC == '(') {
                                    open++;
                                } else if (innerC == ')') {
                                    open--;
                                }
                                end++;
                            }
                        } else {
                            break;
                        }
                    }
                }

                String divisor = sql.substring(start, end);
                sb.append("/ NULLIF(").append(divisor).append(", 0)");
                i = end - 1;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public String injectConstraintsIntoRawSql(String rawSql, String constraints, String fill) {
        String upper = rawSql.toUpperCase();
        String[] functions = {"SUM", "AVG", "COUNT", "MIN", "MAX"};
        String result = rawSql;
        boolean replaced = false;
        for (String fn : functions) {
            String target = fn + "(";
            int idx = upper.indexOf(target);
            if (idx != -1) {
                int openParen = 1;
                int end = idx + target.length();
                while (end < rawSql.length() && openParen > 0) {
                    char ec = rawSql.charAt(end);
                    if (ec == '(') {
                        openParen++;
                    } else if (ec == ')') {
                        openParen--;
                    }
                    end++;
                }
                if (openParen == 0) {
                    String inner = rawSql.substring(idx + target.length(), end - 1);
                    String distinctPrefix = "";
                    String cleanedInner = inner;
                    
                    String innerTrim = inner.trim();
                    if (innerTrim.toUpperCase().startsWith("DISTINCT ")) {
                        distinctPrefix = "DISTINCT ";
                        cleanedInner = innerTrim.substring(9).trim();
                    }
                    
                    String replacedFn = fn + "(" + distinctPrefix + "CASE WHEN " + constraints + " THEN (" + cleanedInner + ") ELSE " + fill + " END)";
                    result = result.substring(0, idx) + replacedFn + result.substring(end);
                    upper = result.toUpperCase();
                    replaced = true;
                }
            }
        }
        if (!replaced) {
            result = "SUM(CASE WHEN " + constraints + " THEN (" + rawSql + ") ELSE " + fill + " END)";
        }
        return result;
    }

    private void validateFilterExpr(String expr) {
        if (expr == null || expr.isBlank()) {
            return;
        }
        String upper = expr.toUpperCase();
        if (upper.contains(";") || upper.contains("--") || upper.contains("/*") ||
            upper.contains("UNION") || upper.contains("INSERT") || upper.contains("UPDATE") ||
            upper.contains("DELETE") || upper.contains("DROP") || upper.contains("ALTER") ||
            upper.contains("TRUNCATE") || upper.contains("GRANT") || upper.contains("REVOKE") ||
            upper.contains("EXECUTE") || upper.contains("XP_CMDSHELL")) {
            throw new IllegalArgumentException("Invalid or dangerous SQL sequences in filter expression: " + expr);
        }
        
        int openParen = 0;
        for (int i = 0; i < expr.length(); i++) {
            if (expr.charAt(i) == '(') {
                openParen++;
            } else if (expr.charAt(i) == ')') {
                openParen--;
                if (openParen < 0) {
                    throw new IllegalArgumentException("Unmatched parentheses in filter expression: " + expr);
                }
            }
        }
        if (openParen != 0) {
            throw new IllegalArgumentException("Unmatched parentheses in filter expression: " + expr);
        }
    }

    private String compileRowFilter(String rowFilter) {
        if (rowFilter == null || rowFilter.isBlank()) {
            return "";
        }
        String trimmed = rowFilter.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            List<FilterCondition> conds = parseGeneralFilters(trimmed);
            StringBuilder sb = new StringBuilder();
            FilterCondition lastCond = null;
            for (FilterCondition cond : conds) {
                String sqlCond = compileFilterCondition(cond);
                if (sqlCond != null && !sqlCond.isBlank()) {
                    if (sb.length() > 0) {
                        String conj = "AND";
                        if (lastCond != null && lastCond.getConjunction() != null) {
                            String c = lastCond.getConjunction().trim().toUpperCase();
                            if ("AND".equals(c) || "OR".equals(c)) {
                                conj = c;
                            }
                        }
                        sb.append(" ").append(conj).append(" ");
                    }
                    sb.append("(").append(sqlCond).append(")");
                    lastCond = cond;
                }
            }
            return sb.toString();
        } else if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                RowFilterGroup rootGroup = mapper.readValue(trimmed, RowFilterGroup.class);
                return compileRowGroup(rootGroup);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse recursive row filter group JSON: " + trimmed, e);
            }
        } else {
            validateFilterExpr(trimmed);
            return trimmed;
        }
    }

    private List<FilterCondition> parseGeneralFilters(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return mapper.readValue(json, new TypeReference<List<FilterCondition>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse general filters JSON: " + json, e);
        }
    }

    public String compileFilterCondition(FilterCondition cond) {
        if (cond == null) {
            return "";
        }
        
        String dimTable = cond.getDimTable();
        String attribute = cond.getAttribute();
        String operator = cond.getOperator();
        String value = cond.getValue();
        
        if (attribute == null || attribute.isBlank()) {
            throw new IllegalArgumentException("Filter attribute cannot be blank");
        }
        if (operator == null || operator.isBlank()) {
            throw new IllegalArgumentException("Filter operator cannot be blank");
        }
        
        if (dimTable != null && !dimTable.isBlank() && !dimTable.matches("^[a-zA-Z0-9_\\.]+$")) {
            throw new IllegalArgumentException("Invalid table name in filter: " + dimTable);
        }
        if (!attribute.matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid column name in filter: " + attribute);
        }
        
        String col = (dimTable != null && !dimTable.isBlank()) ? (dimTable.trim() + "." + attribute.trim()) : attribute.trim();
        String op = operator.trim().toLowerCase();
        
        if (value == null || value.trim().isEmpty()) {
            if (!"is blank".equals(op) && !"is not blank".equals(op) && 
                !"is null".equals(op) && !"is not null".equals(op)) {
                return "";
            }
        }
        
        String result;
        switch (op) {
            case "=":
            case "is": {
                String escapedVal = (value != null ? value : "").replace("'", "''");
                result = String.format("%s = '%s'", col, escapedVal);
                break;
            }
            case "is not":
            case "!=":
            case "<>":
            case "is different from": {
                String escapedVal = (value != null ? value : "").replace("'", "''");
                result = String.format("(%s <> '%s' OR %s IS NULL)", col, escapedVal, col);
                break;
            }
            case "contains":
            case "like": {
                String escapedVal = (value != null ? value : "").replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                if ("contains".equals(op)) {
                    result = String.format("%s ILIKE '%%%s%%' ESCAPE '\\'", col, escapedVal);
                } else {
                    result = String.format("%s LIKE '%%%s%%' ESCAPE '\\'", col, escapedVal);
                }
                break;
            }
            case "does not contains":
            case "not like": {
                String escapedVal = (value != null ? value : "").replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                if ("does not contains".equals(op)) {
                    result = String.format("(%s NOT ILIKE '%%%s%%' ESCAPE '\\' OR %s IS NULL)", col, escapedVal, col);
                } else {
                    result = String.format("(%s NOT LIKE '%%%s%%' ESCAPE '\\' OR %s IS NULL)", col, escapedVal, col);
                }
                break;
            }
            case "start with":
            case "starts with": {
                String escapedVal = (value != null ? value : "").replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                if ("start with".equals(op)) {
                    result = String.format("%s ILIKE '%s%%' ESCAPE '\\'", col, escapedVal);
                } else {
                    result = String.format("%s LIKE '%s%%' ESCAPE '\\'", col, escapedVal);
                }
                break;
            }
            case "end with":
            case "ends with": {
                String escapedVal = (value != null ? value : "").replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                if ("end with".equals(op)) {
                    result = String.format("%s ILIKE '%%%s' ESCAPE '\\'", col, escapedVal);
                } else {
                    result = String.format("%s LIKE '%%%s' ESCAPE '\\'", col, escapedVal);
                }
                break;
            }
            case "is blank": {
                result = String.format("(%s IS NULL OR TRIM(CAST(%s AS TEXT)) = '')", col, col);
                break;
            }
            case "is not blank": {
                result = String.format("(%s IS NOT NULL AND TRIM(CAST(%s AS TEXT)) <> '')", col, col);
                break;
            }
            case "is null": {
                result = String.format("%s IS NULL", col);
                break;
            }
            case "is not null": {
                result = String.format("%s IS NOT NULL", col);
                break;
            }
            case "in":
            case "in list": {
                String valStr = value != null ? value : "";
                String[] parts = valStr.split(",");
                List<String> list = new ArrayList<>();
                for (String part : parts) {
                    list.add("'" + part.trim().replace("'", "''") + "'");
                }
                if (list.isEmpty()) {
                    result = String.format("%s IN (NULL)", col);
                } else {
                    result = String.format("%s IN (%s)", col, String.join(", ", list));
                }
                break;
            }
            case "not in":
            case "not in list": {
                String valStr = value != null ? value : "";
                String[] parts = valStr.split(",");
                List<String> list = new ArrayList<>();
                for (String part : parts) {
                    list.add("'" + part.trim().replace("'", "''") + "'");
                }
                if (list.isEmpty()) {
                    result = String.format("%s NOT IN (NULL)", col);
                } else {
                    result = String.format("%s NOT IN (%s)", col, String.join(", ", list));
                }
                break;
            }
            case ">":
            case "greater_than":
            case "is greater then": {
                String escapedVal = (value != null ? value : "").replace("'", "''");
                result = String.format("%s > '%s'", col, escapedVal);
                break;
            }
            case ">=":
            case "greater_equal":
            case "is greater or equal": {
                String escapedVal = (value != null ? value : "").replace("'", "''");
                result = String.format("%s >= '%s'", col, escapedVal);
                break;
            }
            case "<":
            case "less_than":
            case "is less then": {
                String escapedVal = (value != null ? value : "").replace("'", "''");
                result = String.format("%s < '%s'", col, escapedVal);
                break;
            }
            case "<=":
            case "less_equal":
            case "is less or equal": {
                String escapedVal = (value != null ? value : "").replace("'", "''");
                result = String.format("%s <= '%s'", col, escapedVal);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported filter operator: " + operator);
        }
        
        validateFilterExpr(result);
        return result;
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class FilterCondition {
        private String dimTable;
        private String attribute;
        private String operator;
        private String value;
        private String conjunction;

        public FilterCondition() {}

        public FilterCondition(String dimTable, String attribute, String operator, String value) {
            this.dimTable = dimTable;
            this.attribute = attribute;
            this.operator = operator;
            this.value = value;
        }

        public FilterCondition(String dimTable, String attribute, String operator, String value, String conjunction) {
            this.dimTable = dimTable;
            this.attribute = attribute;
            this.operator = operator;
            this.value = value;
            this.conjunction = conjunction;
        }

        public String getDimTable() { return dimTable; }
        public void setDimTable(String dimTable) { this.dimTable = dimTable; }
        public String getAttribute() { return attribute; }
        public void setAttribute(String attribute) { this.attribute = attribute; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getConjunction() { return conjunction; }
        public void setConjunction(String conjunction) { this.conjunction = conjunction; }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class RowFilterRule {
        private String tableName;
        private String columnName;
        private String operator;
        private List<String> value;

        public RowFilterRule() {}

        public RowFilterRule(String tableName, String columnName, String operator, List<String> value) {
            this.tableName = tableName;
            this.columnName = columnName;
            this.operator = operator;
            this.value = value;
        }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public String getOperator() { return operator; }
        public void setOperator(String operator) { this.operator = operator; }
        public List<String> getValue() { return value; }
        public void setValue(List<String> value) { this.value = value; }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class TableFilterScope {
        private String tableName;
        private RowFilterGroup filtersGroup;

        public TableFilterScope() {}
        public TableFilterScope(String tableName, RowFilterGroup filtersGroup) {
            this.tableName = tableName;
            this.filtersGroup = filtersGroup;
        }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public RowFilterGroup getFiltersGroup() { return filtersGroup; }
        public void setFiltersGroup(RowFilterGroup filtersGroup) { this.filtersGroup = filtersGroup; }
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class RowFilterGroup {
        private String id;
        private String logicalOperator;
        private List<RowFilterRule> rules;
        private List<RowFilterGroup> childGroups;

        public RowFilterGroup() {}

        public RowFilterGroup(String id, String logicalOperator, List<RowFilterRule> rules, List<RowFilterGroup> childGroups) {
            this.id = id;
            this.logicalOperator = logicalOperator;
            this.rules = rules;
            this.childGroups = childGroups;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLogicalOperator() { return logicalOperator; }
        public void setLogicalOperator(String logicalOperator) { this.logicalOperator = logicalOperator; }
        public List<RowFilterRule> getRules() { return rules; }
        public void setRules(List<RowFilterRule> rules) { this.rules = rules; }
        public List<RowFilterGroup> getChildGroups() { return childGroups; }
        public void setChildGroups(List<RowFilterGroup> childGroups) { this.childGroups = childGroups; }
    }

    public String compileRowFilterRule(RowFilterRule rule) {
        if (rule == null || rule.getColumnName() == null || rule.getColumnName().isBlank()) {
            return "";
        }
        
        String col = (rule.getTableName() != null && !rule.getTableName().isBlank()) 
            ? (rule.getTableName().trim() + "." + rule.getColumnName().trim()) 
            : rule.getColumnName().trim();
            
        String op = rule.getOperator() != null ? rule.getOperator().trim().toLowerCase() : "is";
        List<String> values = rule.getValue() != null ? rule.getValue() : Collections.emptyList();
        
        if (rule.getTableName() != null && !rule.getTableName().isBlank() && !rule.getTableName().matches("^[a-zA-Z0-9_\\.]+$")) {
            throw new IllegalArgumentException("Invalid table name in filter: " + rule.getTableName());
        }
        if (!rule.getColumnName().matches("^[a-zA-Z0-9_]+$")) {
            throw new IllegalArgumentException("Invalid column name in filter: " + rule.getColumnName());
        }

        String result;
        switch (op) {
            case "=":
            case "is": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("'", "''");
                result = String.format("%s = '%s'", col, escapedVal);
                break;
            }
            case "!=":
            case "<>":
            case "is not":
            case "is different from": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("'", "''");
                result = String.format("(%s <> '%s' OR %s IS NULL)", col, escapedVal, col);
                break;
            }
            case "contains":
            case "like": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                if ("contains".equals(op)) {
                    result = String.format("%s ILIKE '%%%s%%' ESCAPE '\\'", col, escapedVal);
                } else {
                    result = String.format("%s LIKE '%%%s%%' ESCAPE '\\'", col, escapedVal);
                }
                break;
            }
            case "does not contains":
            case "not like": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                if ("does not contains".equals(op)) {
                    result = String.format("(%s NOT ILIKE '%%%s%%' ESCAPE '\\' OR %s IS NULL)", col, escapedVal, col);
                } else {
                    result = String.format("(%s NOT LIKE '%%%s%%' ESCAPE '\\' OR %s IS NULL)", col, escapedVal, col);
                }
                break;
            }
            case "start with":
            case "starts with": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                if ("start with".equals(op)) {
                    result = String.format("%s ILIKE '%s%%' ESCAPE '\\'", col, escapedVal);
                } else {
                    result = String.format("%s LIKE '%s%%' ESCAPE '\\'", col, escapedVal);
                }
                break;
            }
            case "end with":
            case "ends with": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("'", "''");
                if ("end with".equals(op)) {
                    result = String.format("%s ILIKE '%%%s' ESCAPE '\\'", col, escapedVal);
                } else {
                    result = String.format("%s LIKE '%%%s' ESCAPE '\\'", col, escapedVal);
                }
                break;
            }
            case "is blank": {
                result = String.format("(%s IS NULL OR TRIM(CAST(%s AS TEXT)) = '')", col, col);
                break;
            }
            case "is not blank": {
                result = String.format("(%s IS NOT NULL AND TRIM(CAST(%s AS TEXT)) <> '')", col, col);
                break;
            }
            case "is null": {
                result = String.format("%s IS NULL", col);
                break;
            }
            case "is not null": {
                result = String.format("%s IS NOT NULL", col);
                break;
            }
            case "in":
            case "in list": {
                List<String> quoted = new ArrayList<>();
                for (String v : values) {
                    quoted.add("'" + v.replace("'", "''") + "'");
                }
                if (quoted.isEmpty()) {
                    result = String.format("%s IN (NULL)", col);
                } else {
                    result = String.format("%s IN (%s)", col, String.join(", ", quoted));
                }
                break;
            }
            case "not in":
            case "not in list": {
                List<String> quoted = new ArrayList<>();
                for (String v : values) {
                    quoted.add("'" + v.replace("'", "''") + "'");
                }
                if (quoted.isEmpty()) {
                    result = String.format("%s NOT IN (NULL)", col);
                } else {
                    result = String.format("%s NOT IN (%s)", col, String.join(", ", quoted));
                }
                break;
            }
            case ">":
            case "greater_than":
            case "is greater then": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("'", "''");
                result = String.format("%s > '%s'", col, escapedVal);
                break;
            }
            case ">=":
            case "greater_equal":
            case "is greater or equal": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("'", "''");
                result = String.format("%s >= '%s'", col, escapedVal);
                break;
            }
            case "<":
            case "less_than":
            case "is less then": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("'", "''");
                result = String.format("%s < '%s'", col, escapedVal);
                break;
            }
            case "<=":
            case "less_equal":
            case "is less or equal": {
                String val = values.isEmpty() ? "" : values.get(0);
                String escapedVal = val.replace("'", "''");
                result = String.format("%s <= '%s'", col, escapedVal);
                break;
            }
            default:
                throw new IllegalArgumentException("Unsupported filter operator in recursive rules: " + op);
        }
        
        validateFilterExpr(result);
        return result;
    }

    public String compileRowGroup(RowFilterGroup group) {
        if (group == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        
        if (group.getRules() != null) {
            for (RowFilterRule rule : group.getRules()) {
                String compiledRule = compileRowFilterRule(rule);
                if (compiledRule != null && !compiledRule.isBlank()) {
                    parts.add(compiledRule);
                }
            }
        }
        
        if (group.getChildGroups() != null) {
            for (RowFilterGroup child : group.getChildGroups()) {
                String compiledChild = compileRowGroup(child);
                if (compiledChild != null && !compiledChild.isBlank()) {
                    parts.add(compiledChild);
                }
            }
        }
        
        if (parts.isEmpty()) {
            return "";
        }
        
        String conj = " " + (group.getLogicalOperator() != null ? group.getLogicalOperator().trim().toUpperCase() : "AND") + " ";
        return "(" + String.join(conj, parts) + ")";
    }


    private boolean isRuleApplicable(String factTable, RowFilterRule rule, Map<String, Set<String>> cache) {
        if (rule == null) return false;
        String tbl = rule.getTableName();
        String col = rule.getColumnName();
        if (tbl == null || tbl.isBlank()) {
            return columnExists(factTable, col, cache);
        }
        if (tbl.trim().equalsIgnoreCase(factTable.trim())) {
            return true;
        }
        if (schemaGraphRouter != null) {
            try {
                String resolvedDim = resolveConformedDimension(findConformedKey(Set.of(factTable), cache));
                if (resolvedDim != null && resolvedDim.trim().equalsIgnoreCase(tbl.trim())) {
                    return true;
                }
                List<String> joins = schemaGraphRouter.computeJoinClauses(factTable, Set.of(tbl.trim()));
                return joins != null && !joins.isEmpty();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private RowFilterGroup pruneGroupForTable(RowFilterGroup group, String factTable, Map<String, Set<String>> cache) {
        if (group == null) return null;
        
        List<RowFilterRule> applicableRules = new ArrayList<>();
        if (group.getRules() != null) {
            for (RowFilterRule rule : group.getRules()) {
                if (isRuleApplicable(factTable, rule, cache)) {
                    applicableRules.add(rule);
                }
            }
        }
        
        List<RowFilterGroup> applicableChildren = new ArrayList<>();
        if (group.getChildGroups() != null) {
            for (RowFilterGroup child : group.getChildGroups()) {
                RowFilterGroup prunedChild = pruneGroupForTable(child, factTable, cache);
                if (prunedChild != null) {
                    applicableChildren.add(prunedChild);
                }
            }
        }
        
        if (applicableRules.isEmpty() && applicableChildren.isEmpty()) {
            return null;
        }
        
        return new RowFilterGroup(
            group.getId(),
            group.getLogicalOperator(),
            applicableRules,
            applicableChildren
        );
    }

    private void collectReferencedTables(RowFilterGroup group, String factTable, Set<String> targets, Map<String, Set<String>> cache) {
        if (group == null) return;
        if (group.getRules() != null) {
            for (RowFilterRule rule : group.getRules()) {
                if (isRuleApplicable(factTable, rule, cache)) {
                    String tbl = rule.getTableName();
                    if (tbl != null && !tbl.isBlank() && !tbl.trim().equalsIgnoreCase(factTable.trim())) {
                        targets.add(tbl.trim());
                    }
                }
            }
        }
        if (group.getChildGroups() != null) {
            for (RowFilterGroup child : group.getChildGroups()) {
                collectReferencedTables(child, factTable, targets, cache);
            }
        }
    }

    private String compileGeneralFiltersForTable(
            String generalFiltersStr,
            String factTable,
            Set<String> dimensionTargets,
            Map<String, Set<String>> cache) {
        if (generalFiltersStr == null || generalFiltersStr.isBlank()) {
            return "";
        }
        String trimmed = generalFiltersStr.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                RowFilterGroup rootGroup = mapper.readValue(trimmed, RowFilterGroup.class);
                collectReferencedTables(rootGroup, factTable, dimensionTargets, cache);
                RowFilterGroup pruned = pruneGroupForTable(rootGroup, factTable, cache);
                return compileRowGroup(pruned);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to parse recursive general filter group JSON: " + trimmed, e);
            }
        } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            try {
                ObjectMapper mapper = new ObjectMapper();
                mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                List<TableFilterScope> scopes = mapper.readValue(trimmed, new TypeReference<List<TableFilterScope>>() {});
                
                boolean isScopedModel = false;
                if (!scopes.isEmpty()) {
                    TableFilterScope first = scopes.get(0);
                    if (first.getTableName() != null || first.getFiltersGroup() != null) {
                        isScopedModel = true;
                    }
                }
                
                if (isScopedModel) {
                    List<String> compiledScopes = new ArrayList<>();
                    for (TableFilterScope scope : scopes) {
                        String scopeTable = scope.getTableName();
                        RowFilterGroup group = scope.getFiltersGroup();
                        if (group == null) continue;
                        
                        if (isTableScopeApplicable(factTable, scopeTable, cache)) {
                            mapScopeTable(group, scopeTable);
                            collectReferencedTables(group, factTable, dimensionTargets, cache);
                            RowFilterGroup pruned = pruneGroupForTable(group, factTable, cache);
                            String compiledGroup = compileRowGroup(pruned);
                            if (compiledGroup != null && !compiledGroup.isBlank()) {
                                compiledScopes.add(compiledGroup);
                            }
                        }
                    }
                    if (compiledScopes.isEmpty()) {
                        return "";
                    }
                    return String.join(" AND ", compiledScopes);
                }
            } catch (Exception e) {
                // Fall through to legacy flat condition parsing
            }

            List<FilterCondition> conds = parseGeneralFilters(trimmed);
            for (FilterCondition cond : conds) {
                if (cond.getDimTable() != null && !cond.getDimTable().isBlank()) {
                    if (isFilterApplicable(factTable, cond, cache)) {
                        dimensionTargets.add(cond.getDimTable().trim());
                    }
                }
            }
            StringBuilder generalBuilder = new StringBuilder();
            FilterCondition lastGenCond = null;
            for (FilterCondition cond : conds) {
                if (isFilterApplicable(factTable, cond, cache)) {
                    String sqlCond = compileFactFilter(factTable, cond);
                    if (sqlCond != null && !sqlCond.isBlank()) {
                        if (generalBuilder.length() > 0) {
                            String conj = "AND";
                            if (lastGenCond != null && lastGenCond.getConjunction() != null) {
                                String c = lastGenCond.getConjunction().trim().toUpperCase();
                                if ("AND".equals(c) || "OR".equals(c)) {
                                    conj = c;
                                }
                            }
                            generalBuilder.append(" ").append(conj).append(" ");
                        }
                        generalBuilder.append("(").append(sqlCond).append(")");
                        lastGenCond = cond;
                    }
                }
            }
            return generalBuilder.toString();
        } else {
            validateFilterExpr(trimmed);
            return trimmed;
        }
    }

    private boolean isTableScopeApplicable(String factTable, String scopeTable, Map<String, Set<String>> cache) {
        if (scopeTable == null || scopeTable.isBlank()) {
            return true;
        }
        if (scopeTable.trim().equalsIgnoreCase(factTable.trim())) {
            return true;
        }
        if (schemaGraphRouter != null) {
            try {
                String conformedKey = findConformedKey(Set.of(factTable), cache);
                String resolvedDim = resolveConformedDimension(conformedKey);
                if (resolvedDim != null && resolvedDim.trim().equalsIgnoreCase(scopeTable.trim())) {
                    return true;
                }
                List<String> joins = schemaGraphRouter.computeJoinClauses(factTable, Set.of(scopeTable.trim()));
                return joins != null && !joins.isEmpty();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private void mapScopeTable(RowFilterGroup group, String scopeTable) {
        if (group == null) return;
        if (group.getRules() != null) {
            for (RowFilterRule rule : group.getRules()) {
                if (rule.getTableName() == null || rule.getTableName().isBlank()) {
                    rule.setTableName(scopeTable);
                }
            }
        }
        if (group.getChildGroups() != null) {
            for (RowFilterGroup child : group.getChildGroups()) {
                mapScopeTable(child, scopeTable);
            }
        }
    }

    private boolean isFilterApplicable(String factTable, FilterCondition cond, Map<String, Set<String>> cache) {
        if (cond == null) {
            return false;
        }
        String dimTable = cond.getDimTable();
        String attribute = cond.getAttribute();
        
        if (dimTable == null || dimTable.isBlank()) {
            return columnExists(factTable, attribute, cache);
        }
        
        if (schemaGraphRouter != null) {
            try {
                String resolvedDim = resolveConformedDimension(findConformedKey(Set.of(factTable), cache));
                if (resolvedDim != null && resolvedDim.trim().equalsIgnoreCase(dimTable.trim())) {
                    return true;
                }
                List<String> joins = schemaGraphRouter.computeJoinClauses(factTable, Set.of(dimTable.trim()));
                return joins != null && !joins.isEmpty();
            } catch (Exception e) {
                return false;
            }
        }
        return false;
    }

    private String compileFactFilter(String factTable, FilterCondition cond) {
        if (cond == null) return "";
        String dimTable = cond.getDimTable();
        String attribute = cond.getAttribute();
        
        if (dimTable != null && !dimTable.isBlank()) {
            return compileFilterCondition(cond);
        }
        
        FilterCondition clone = new FilterCondition(
            factTable,
            attribute,
            cond.getOperator(),
            cond.getValue()
        );
        return compileFilterCondition(clone);
    }

    private String compileMetricClause(
            String factTable,
            MeasureDefinitionDTO mdef,
            String dateConstraint,
            String filterClause) {
        String metricClause = "";
        if ("visual".equalsIgnoreCase(mdef.getMode())) {
            String agg = mdef.getAggregation() != null ? mdef.getAggregation().trim().toUpperCase() : "SUM";
            String originalColName = mdef.getTargetColumn() != null ? mdef.getTargetColumn().trim() : "";

            if (("SUM".equals(agg) || "AVG".equals(agg)) && !isNumericColumn(factTable, originalColName)) {
                throw new IllegalArgumentException("Column '" + originalColName + "' in table " + factTable + " is not numeric and cannot be aggregated with " + agg);
            }

            String colName = factTable + "." + originalColName;
            String fill = "SUM".equals(agg) ? "0" : "NULL";

            if ("COUNT_DISTINCT".equals(agg) || "COUNT DISTINCT".equals(agg)) {
                metricClause = String.format(
                    "COUNT(DISTINCT CASE WHEN %s%s THEN %s ELSE NULL END)",
                    dateConstraint,
                    filterClause,
                    colName
                );
            } else {
                metricClause = String.format(
                    "%s(CASE WHEN %s%s THEN %s ELSE %s END)",
                    agg,
                    dateConstraint,
                    filterClause,
                    colName,
                    fill
                );
            }
        } else { // raw mode
            String raw = mdef.getRawSql();
            if (raw == null) {
                raw = "";
            }
            String processedRaw = raw;
            String shortTbl = factTable;
            if (factTable.contains(".")) {
                shortTbl = factTable.substring(factTable.lastIndexOf(".") + 1);
            }
            String escapedFull = java.util.regex.Pattern.quote(factTable.trim()) + "\\.";
            String escapedShort = java.util.regex.Pattern.quote(shortTbl.trim()) + "\\.";
            processedRaw = processedRaw.replaceAll("(?i)" + escapedFull, factTable + ".");
            processedRaw = processedRaw.replaceAll("(?i)(?<!\\.)" + escapedShort, factTable + ".");

            processedRaw = makeDivisionSafe(processedRaw);
            validateFilterExpr(processedRaw);

            boolean hasAgg = false;
            String[] functions = {"SUM", "AVG", "COUNT", "MIN", "MAX"};
            String upperRaw = processedRaw.toUpperCase();
            String fill = "0";
            for (String fn : functions) {
                if (upperRaw.contains(fn + "(")) {
                    hasAgg = true;
                    if ("SUM".equals(fn)) {
                        fill = "0";
                    } else {
                        fill = "NULL";
                    }
                    break;
                }
            }

            if (hasAgg) {
                metricClause = injectConstraintsIntoRawSql(processedRaw, dateConstraint + filterClause, fill);
            } else {
                metricClause = String.format(
                    "SUM(CASE WHEN %s%s THEN (%s) ELSE 0 END)",
                    dateConstraint,
                    filterClause,
                    processedRaw
                );
            }
        }
        return metricClause;
    }
}
