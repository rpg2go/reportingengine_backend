package com.reporting.util;

import com.google.cloud.bigquery.*;
import java.io.*;
import java.nio.channels.Channels;
import java.sql.*;
import java.util.*;

/**
 * Standalone utility to migrate and seed local PostgreSQL DWH analytical tables
 * (dimension and fact tables) directly into Google BigQuery.
 * Reads environment variables from the local .env file.
 */
public class BigQueryDataSeeder {

    public static void main(String[] args) {
        Map<String, String> env = loadEnvFile();
        String dbUrl = env.get("LOCAL_DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "LOCAL_DATABASE_URL environment variable is missing or empty in .env file.");
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

        String projectId = env.get("GCP_PROJECT_ID");
        if (projectId == null || projectId.isBlank()) {
            projectId = "reportbuilder-497323";
        }
        String datasetName = env.get("BIGQUERY_DATASET");
        if (datasetName == null || datasetName.isBlank()) {
            datasetName = "analytics";
        }

        System.out.println("=================================================================");
        System.out.println("BigQuery DWH Data Seeder Utility Starting...");
        System.out.println("Source PostgreSQL: " + dbUrl);
        System.out.println("Destination BigQuery: " + projectId + "." + datasetName);
        System.out.println("=================================================================");

        try {
            // Initialize BigQuery client (uses Application Default Credentials
            // automatically)
            BigQuery bigQuery = BigQueryOptions.newBuilder()
                    .setProjectId(projectId)
                    .build()
                    .getService();

            Class.forName("org.postgresql.Driver");
            try (java.sql.Connection conn = DriverManager.getConnection(dbUrl, user, password)) {
                System.out.println("Connected to PostgreSQL successfully.");

                // Tables to migrate (ordered to respect dependencies if needed)
                String[] tables = {
                        "dim_date",
                        "dim_location",
                        "dim_relationship_manager",
                        "dim_investment_hierarchy",
                        "dim_products",
                        "dim_customers",
                        "dim_countries",
                        "dim_accounts",
                        "fact_sales",
                        "fact_banking_transactions",
                        "fact_loans",
                        "fact_investments",
                        "fact_department_performance"
                };

                for (String table : tables) {
                    migrateTable(conn, bigQuery, datasetName, table);
                }

                System.out.println("\n=================================================================");
                System.out.println("BigQuery DWH Data Migration Completed successfully!");
                System.out.println("=================================================================");
            }
        } catch (Exception e) {
            System.err.println("\n[ERROR] Migration failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void migrateTable(java.sql.Connection conn, BigQuery bigQuery, String datasetName, String tableName)
            throws Exception {
        System.out.println("\nMigrating table: analytics." + tableName + " -> " + datasetName + "." + tableName);
        String sql = "SELECT * FROM analytics." + tableName;

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            // Build CSV in a temporary file to load into BigQuery
            File tempFile = File.createTempFile("bq_import_" + tableName, ".csv");
            tempFile.deleteOnExit();

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
                // Header row
                for (int i = 1; i <= columnCount; i++) {
                    writer.write(metaData.getColumnName(i));
                    if (i < columnCount)
                        writer.write(",");
                }
                writer.newLine();

                int rowCount = 0;
                while (rs.next()) {
                    rowCount++;
                    for (int i = 1; i <= columnCount; i++) {
                        Object val = rs.getObject(i);
                        if (val == null) {
                            writer.write("");
                        } else {
                            String str = val.toString();
                            // Escape commas and double quotes for CSV safety
                            if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
                                str = "\"" + str.replace("\"", "\"\"") + "\"";
                            }
                            writer.write(str);
                        }
                        if (i < columnCount)
                            writer.write(",");
                    }
                    writer.newLine();
                }
                writer.flush();
                System.out.println("Exported " + rowCount + " rows to temporary CSV file: " + tempFile.getName());

                if (rowCount == 0) {
                    System.out.println("Table is empty, skipping BigQuery upload.");
                    return;
                }
            }

            // Load CSV into BigQuery
            TableId tableId = TableId.of(datasetName, tableName);
            WriteChannelConfiguration writeChannelConfiguration = WriteChannelConfiguration.newBuilder(tableId)
                    .setFormatOptions(FormatOptions.csv().toBuilder().setSkipLeadingRows(1).build())
                    .setWriteDisposition(JobInfo.WriteDisposition.WRITE_TRUNCATE) // Truncate and overwrite
                    .build();

            TableDataWriteChannel writerChannel = bigQuery.writer(writeChannelConfiguration);
            try (OutputStream os = Channels.newOutputStream(writerChannel);
                    BufferedInputStream is = new BufferedInputStream(new FileInputStream(tempFile))) {
                byte[] buffer = new byte[10240];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }

            Job job = writerChannel.getJob();
            System.out.println("BigQuery load job created: " + job.getJobId().getJob() + ". Waiting for completion...");
            job = job.waitFor();

            if (job.getStatus().getError() != null) {
                throw new RuntimeException("BigQuery Job failed: " + job.getStatus().getError().getMessage());
            }

            System.out.println("Table " + tableName + " migrated successfully.");
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
                    if (line.isEmpty() || line.startsWith("#"))
                        continue;
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
}
