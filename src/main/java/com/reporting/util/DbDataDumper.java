package com.reporting.util;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.sql.*;
import java.util.*;

public class DbDataDumper {

    public static void main(String[] args) {
        Map<String, String> env = loadEnvFile();
        String dbUrl = System.getProperty("db.url");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = env.get("NEON_DATABASE_URL");
        }
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getenv("NEON_DATABASE_URL");
        }
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = env.get("LOCAL_DATABASE_URL");
        }
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = "jdbc:postgresql://127.0.0.1:5433/agentic_ai";
        }

        String user = "user";
        String password = "password";

        // Convert standard postgres:// URI to JDBC URL and parse credentials
        if (dbUrl.startsWith("postgresql://") || dbUrl.startsWith("postgres://")) {
            String schemeSep = "://";
            String cleanUrl = dbUrl.substring(dbUrl.indexOf(schemeSep) + schemeSep.length());
            int atIdx = cleanUrl.indexOf("@");
            if (atIdx != -1) {
                String credentials = cleanUrl.substring(0, atIdx);
                String rest = cleanUrl.substring(atIdx + 1);
                
                int colonIdx = credentials.indexOf(":");
                if (colonIdx != -1) {
                    user = credentials.substring(0, colonIdx);
                    password = credentials.substring(colonIdx + 1);
                } else {
                    user = credentials;
                }
                dbUrl = "jdbc:postgresql://" + rest;
            } else {
                dbUrl = "jdbc:postgresql://" + cleanUrl;
            }
        }

        System.out.println("DbDataDumper: Connecting to " + dbUrl);

        try (Connection conn = DriverManager.getConnection(dbUrl, user, password)) {
            System.out.println("Connection successful. Dumping data...");

            // 1. Dump analytics.* data
            dumpAnalyticsData(conn);

            // 2. Dump reporting data
            dumpReportingData(conn);

            // 3. Dump reporting.meta_* data
            dumpCatalogData(conn);

            System.out.println("All data dumped successfully!");
        } catch (Exception e) {
            System.err.println("Error during data dump: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static Map<String, String> loadEnvFile() {
        Map<String, String> env = new HashMap<>();
        java.io.File envFile = new java.io.File(".env");
        if (envFile.exists()) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int eqIdx = line.indexOf("=");
                    if (eqIdx != -1) {
                        String key = line.substring(0, eqIdx).trim();
                        String value = line.substring(eqIdx + 1).trim();
                        env.put(key, value);
                    }
                }
            } catch (Exception e) {
                System.err.println("Warning: could not read .env file: " + e.getMessage());
            }
        }
        return env;
    }

    private static void dumpAnalyticsData(Connection conn) throws Exception {
        String path = "db/liquibase/sql/005_seed_analytics_data.sql";
        try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {
            writer.println("--liquibase formatted sql");
            writer.println("--changeset devops:005_seed_analytics_data runOnChange:true endDelimiter:;");
            writer.println();

            // Order of insertion to respect foreign keys
            String[] tables = {
                "analytics.dim_date",
                "analytics.dim_location",
                "analytics.dim_relationship_manager",
                "analytics.dim_investment_hierarchy",
                "analytics.dim_products",
                "analytics.dim_customers",
                "analytics.dim_countries",
                "analytics.dim_accounts",
                "analytics.fact_sales",
                "analytics.fact_banking_transactions",
                "analytics.fact_loans",
                "analytics.fact_investments",
                "analytics.fact_department_performance"
            };

            for (String table : tables) {
                dumpTableData(conn, writer, table);
            }
        }
        System.out.println("Dumped analytics data to " + path);
    }

    private static void dumpReportingData(Connection conn) throws Exception {
        String path = "db/liquibase/sql/006_seed_reporting_data.sql";
        try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {
            writer.println("--liquibase formatted sql");
            writer.println("--changeset devops:006_seed_reporting_data runOnChange:true endDelimiter:;");
            writer.println();

            String[] tables = {
                "reporting.row_style",
                "reporting.report_config",
                "reporting.column_definition",
                "reporting.row_definition",
                "reporting.row_metric_mapping",
                "reporting.row_formula",
                "reporting.row_column_intersection"
            };

            for (String table : tables) {
                dumpTableData(conn, writer, table);
            }
        }
        System.out.println("Dumped reporting data to " + path);
    }

    private static void dumpCatalogData(Connection conn) throws Exception {
        String path = "db/liquibase/sql/007_seed_catalog_data.sql";
        try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {
            writer.println("--liquibase formatted sql");
            writer.println("--changeset devops:007_seed_catalog_data runOnChange:true endDelimiter:;");
            writer.println();

            String[] tables = {
                "catalog.meta_table",
                "catalog.meta_column",
                "catalog.meta_relationship"
            };

            for (String table : tables) {
                dumpTableData(conn, writer, table);
            }
        }
        System.out.println("Dumped catalog data to " + path);
    }


    private static void dumpTableData(Connection conn, PrintWriter writer, String tableName) throws Exception {
        System.out.println("Querying table: " + tableName);
        
        // 1. Get column names and types using DatabaseMetaData
        String schema = tableName.substring(0, tableName.indexOf("."));
        String table = tableName.substring(tableName.indexOf(".") + 1);
        
        List<String> columns = new ArrayList<>();
        List<Integer> columnTypes = new ArrayList<>();
        
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, schema, table, null)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                if ("report_config".equals(table) && "name".equals(colName)) {
                    colName = "report_name";
                }
                columns.add(colName);
                columnTypes.add(rs.getInt("DATA_TYPE"));
            }
        }

        if (columns.isEmpty()) {
            System.out.println("Skipping table " + tableName + " (no columns found or table doesn't exist).");
            writer.println("SELECT 1;");
            return;
        }

        // 2. Select all rows
        String sql = "SELECT * FROM " + tableName;
        int rowCount = 0;
        
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            while (rs.next()) {
                rowCount++;
                StringBuilder sb = new StringBuilder();
                sb.append("INSERT INTO ").append(tableName).append(" (");
                for (int i = 0; i < columns.size(); i++) {
                    sb.append(columns.get(i));
                    if (i < columns.size() - 1) sb.append(", ");
                }
                sb.append(") VALUES (");
                
                for (int i = 0; i < columns.size(); i++) {
                    Object val = rs.getObject(i + 1);
                    int type = columnTypes.get(i);
                    
                    if (val == null) {
                        sb.append("NULL");
                    } else if (type == Types.VARCHAR || type == Types.CHAR || type == Types.LONGVARCHAR || type == Types.OTHER) {
                        String s = val.toString().replace("'", "''");
                        sb.append("'").append(s).append("'");
                    } else if (type == Types.DATE) {
                        sb.append("'").append(val.toString()).append("'");
                    } else if (type == Types.TIMESTAMP || type == Types.TIMESTAMP_WITH_TIMEZONE) {
                        sb.append("'").append(val.toString()).append("'");
                    } else if (type == Types.BOOLEAN || type == Types.BIT) {
                        sb.append(val.toString());
                    } else {
                        sb.append(val.toString());
                    }
                    
                    if (i < columns.size() - 1) sb.append(", ");
                }
                sb.append(") ON CONFLICT DO NOTHING;");
                writer.println(sb.toString());
            }
        } catch (Exception e) {
            System.err.println("Warning: failed to dump data for table " + tableName + ": " + e.getMessage());
        }
        
        System.out.println("Dumped " + rowCount + " rows from " + tableName);
        if (rowCount == 0) {
            writer.println("SELECT 1;");
        }
        writer.println();
    }
}
