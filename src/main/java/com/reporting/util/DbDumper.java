package com.reporting.util;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DbDumper {

    public static void main(String[] args) {
        String dbUrl = System.getenv("LOCAL_DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getenv("DATABASE_URL");
        }
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = "jdbc:postgresql://localhost:5432/agentic_ai";
        }

        // Convert standard postgres:// URL to jdbc:postgresql:// if necessary
        if (dbUrl.startsWith("postgresql://")) {
            dbUrl = dbUrl.replace("postgresql://", "jdbc:postgresql://");
        } else if (dbUrl.startsWith("postgres://")) {
            dbUrl = dbUrl.replace("postgres://", "jdbc:postgresql://");
        }

        // Parse credentials if embedded
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

        System.out.println("Connecting to local database: " + dbUrl);

        String outputFile = "db/migrations/008_seed_report_templates.sql";
        System.out.println("Output seed file: " + outputFile);

        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);

        try (Connection conn = DriverManager.getConnection(dbUrl, props);
             PrintWriter writer = new PrintWriter(new FileWriter(outputFile))) {

            writer.println("-- =============================================================================");
            writer.println("-- Migration 008: Seed Report Templates (Exported from Local Database)");
            writer.println("-- Schema: reporting");
            writer.println("-- Description: Seeds report definitions, column configurations, rows,");
            writer.println("--              formulas, and metrics.");
            writer.println("-- Date: " + java.time.LocalDate.now());
            writer.println("-- =============================================================================");
            writer.println();
            writer.println("BEGIN;");
            writer.println();

            // 1. Dump rpt_report
            System.out.println("Dumping reporting.rpt_report...");
            dumpTable(conn, writer, "reporting.rpt_report", new String[]{
                "report_id", "name", "description", "explore_id", "version", "status",
                "source_table", "granularity", "timeframe_start", "timeframe_end", "timeframe_today",
                "quick_filters", "general_filters"
            }, "report_id");

            // 2. Dump rpt_column_def
            System.out.println("Dumping reporting.rpt_column_def...");
            dumpTable(conn, writer, "reporting.rpt_column_def", new String[]{
                "report_id", "col_id", "label", "col_type", "period_offset", "rolling_n",
                "formula_expr", "display_order"
            }, "report_id, display_order");

            // 3. Dump rpt_row
            System.out.println("Dumping reporting.rpt_row...");
            dumpTable(conn, writer, "reporting.rpt_row", new String[]{
                "row_id", "report_id", "parent_row_id", "label", "row_type", "display_order",
                "indent_level", "style_id", "filter_expr"
            }, "report_id, display_order");

            // 4. Dump rpt_row_metric
            System.out.println("Dumping reporting.rpt_row_metric...");
            dumpTable(conn, writer, "reporting.rpt_row_metric", new String[]{
                "report_id", "row_id", "measure_id", "explore_id", "sql_expr"
            }, "report_id, row_id");

            // 5. Dump rpt_row_formula
            System.out.println("Dumping reporting.rpt_row_formula...");
            dumpTable(conn, writer, "reporting.rpt_row_formula", new String[]{
                "report_id", "row_id", "formula_expr"
            }, "report_id, row_id");

            // 6. Dump rpt_row_column_map
            System.out.println("Dumping reporting.rpt_row_column_map...");
            dumpTable(conn, writer, "reporting.rpt_row_column_map", new String[]{
                "report_id", "row_id", "col_id", "is_enabled"
            }, "report_id, row_id, col_id");

            writer.println("COMMIT;");
            System.out.println("Seeds exported successfully to " + outputFile);

        } catch (Exception e) {
            System.err.println("Error dumping database: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void dumpTable(Connection conn, PrintWriter writer, String tableName, String[] columns, String orderBy) throws SQLException {
        writer.println("-- -----------------------------------------------------------------------------");
        writer.println("-- Data for table " + tableName);
        writer.println("-- -----------------------------------------------------------------------------");

        String selectSql = "SELECT " + String.join(", ", columns) + " FROM " + tableName;
        if (orderBy != null && !orderBy.isBlank()) {
            selectSql += " ORDER BY " + orderBy;
        }

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectSql)) {

            int count = 0;
            while (rs.next()) {
                count++;
                List<String> values = new ArrayList<>();
                for (String col : columns) {
                    Object val = rs.getObject(col);
                    if (val == null) {
                        values.add("NULL");
                    } else if (val instanceof Boolean) {
                        values.add(val.toString().toUpperCase());
                    } else if (val instanceof Number) {
                        values.add(val.toString());
                    } else {
                        // String / Text, escape single quotes
                        String escaped = val.toString().replace("'", "''");
                        values.add("'" + escaped + "'");
                    }
                }

                writer.println(String.format(
                    "INSERT INTO %s (%s) VALUES (%s) ON CONFLICT DO NOTHING;",
                    tableName,
                    String.join(", ", columns),
                    String.join(", ", values)
                ));
            }
            System.out.println("Dumped " + count + " rows from " + tableName);
            writer.println();
        }
    }
}
