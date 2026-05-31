package com.reporting.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;

public class ReportMigrator {

    private static class SemanticMeasure {
        final int id;
        final String name;
        final String sqlExpr;
        final String tableRef;

        SemanticMeasure(int id, String name, String sqlExpr, String tableRef) {
            this.id = id;
            this.name = name;
            this.sqlExpr = sqlExpr;
            this.tableRef = tableRef;
        }
    }

    public static void main(String[] args) {
        String dbUrl = System.getenv("LOCAL_DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getenv("DATABASE_URL");
        }
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = "jdbc:postgresql://localhost:5432/agentic_ai";
        }

        if (dbUrl.startsWith("postgresql://")) {
            dbUrl = dbUrl.replace("postgresql://", "jdbc:postgresql://");
        } else if (dbUrl.startsWith("postgres://")) {
            dbUrl = dbUrl.replace("postgres://", "jdbc:postgresql://");
        }

        String user = "user";
        String password = "password";
        if (dbUrl.contains("@")) {
            try {
                String clean = dbUrl.substring(dbUrl.indexOf("://") + 3);
                int atIdx = clean.indexOf("@");
                String credentials = clean.substring(0, atIdx);
                String rest = clean.substring(atIdx + 1);
                int colonIdx = credentials.indexOf(":");
                if (colonIdx != -1) {
                    user = credentials.substring(0, colonIdx);
                    password = credentials.substring(colonIdx + 1);
                } else {
                    user = credentials;
                }
                dbUrl = "jdbc:postgresql://" + rest;
            } catch (Exception e) {
                // ignore
            }
        }

        System.out.println("Connecting to database for migration: " + dbUrl);

        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);

        try (Connection conn = DriverManager.getConnection(dbUrl, props)) {
            conn.setAutoCommit(false);

            // 1. Load semantic measures
            Map<Integer, SemanticMeasure> byId = new HashMap<>();
            Map<String, SemanticMeasure> byName = new HashMap<>();

            String loadSemSql = 
                "SELECT m.measure_id, m.name, m.sql_expr, v.table_ref " +
                "FROM reporting.sem_measure m " +
                "JOIN reporting.sem_view v ON v.view_id = m.view_id";

            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(loadSemSql)) {
                while (rs.next()) {
                    int id = rs.getInt("measure_id");
                    String name = rs.getString("name");
                    String sqlExpr = rs.getString("sql_expr");
                    String tableRef = rs.getString("table_ref");

                    SemanticMeasure measure = new SemanticMeasure(id, name, sqlExpr, tableRef);
                    byId.put(id, measure);
                    byName.put(name.toLowerCase(), measure);
                }
            }
            System.out.println("Loaded " + byId.size() + " semantic measures from database.");

            // 2. Load all row metrics
            String loadMetricsSql = "SELECT report_id, row_id, measure_id, sql_expr, measure_definition FROM reporting.rpt_row_metric";
            List<Map<String, Object>> metrics = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(loadMetricsSql)) {
                while (rs.next()) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("report_id", rs.getString("report_id"));
                    m.put("row_id", rs.getString("row_id"));
                    m.put("measure_id", rs.getObject("measure_id"));
                    m.put("sql_expr", rs.getString("sql_expr"));
                    m.put("measure_definition", rs.getString("measure_definition"));
                    metrics.add(m);
                }
            }
            System.out.println("Found " + metrics.size() + " row metrics in database.");

            // 3. Process and update
            String updateSql = "UPDATE reporting.rpt_row_metric SET measure_id = NULL, sql_expr = ?, measure_definition = ? WHERE report_id = ? AND row_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateSql)) {
                ObjectMapper mapper = new ObjectMapper();
                int migratedCount = 0;

                for (Map<String, Object> metric : metrics) {
                    String reportId = (String) metric.get("report_id");
                    String rowId = (String) metric.get("row_id");
                    Integer mId = (Integer) metric.get("measure_id");
                    String sqlExpr = (String) metric.get("sql_expr");
                    String measureDefStr = (String) metric.get("measure_definition");

                    SemanticMeasure sm = null;
                    if (mId != null) {
                        sm = byId.get(mId);
                    }
                    if (sm == null && sqlExpr != null) {
                        sm = byName.get(sqlExpr.trim().toLowerCase());
                    }

                    String finalSqlExpr;
                    String finalJson;

                    if (sm != null) {
                        finalSqlExpr = sm.sqlExpr;
                        Map<String, Object> defMap = new LinkedHashMap<>();
                        defMap.put("mode", "raw");
                        defMap.put("aggregation", null);
                        defMap.put("targetColumn", null);
                        defMap.put("table", sm.tableRef);
                        defMap.put("rawSql", sm.sqlExpr);
                        finalJson = mapper.writeValueAsString(defMap);
                    } else {
                        // Already raw SQL or formula, keep as is but make sure measure_definition is set
                        finalSqlExpr = (sqlExpr != null) ? sqlExpr : "";
                        if (measureDefStr == null || measureDefStr.isBlank()) {
                            Map<String, Object> defMap = new LinkedHashMap<>();
                            defMap.put("mode", "raw");
                            defMap.put("aggregation", null);
                            defMap.put("targetColumn", null);
                            defMap.put("table", null);
                            defMap.put("rawSql", finalSqlExpr);
                            finalJson = mapper.writeValueAsString(defMap);
                        } else {
                            finalJson = measureDefStr;
                        }
                    }

                    pstmt.setString(1, finalSqlExpr);
                    pstmt.setString(2, finalJson);
                    pstmt.setString(3, reportId);
                    pstmt.setString(4, rowId);
                    pstmt.addBatch();
                    migratedCount++;
                }

                pstmt.executeBatch();
                System.out.println("Migrated " + migratedCount + " metrics in batch.");
            }

            conn.commit();
            System.out.println("Migration committed successfully.");

        } catch (Exception e) {
            System.err.println("Error running migration: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
