package com.reporting.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;

public class MigrationRunner {

    public static void main(String[] args) {
        String dbUrl = System.getenv("NEON_DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getenv("DATABASE_URL");
        }
        if (dbUrl == null || dbUrl.isBlank()) {
            if (args.length > 0 && !args[0].isBlank()) {
                dbUrl = args[0];
            } else {
                System.err.println("Error: NEON_DATABASE_URL or DATABASE_URL environment variable or command-line argument is not set.");
                System.exit(1);
            }
        }

        String user = null;
        String password = null;
        // Convert standard postgres:// URI to JDBC properties and URL
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

        System.out.println("Connecting to database: " + dbUrl);

        File migrationsDir = new File("db/migrations");
        if (!migrationsDir.exists() || !migrationsDir.isDirectory()) {
            System.err.println("Error: db/migrations directory not found.");
            System.exit(1);
        }

        File[] files = migrationsDir.listFiles((dir, name) -> name.endsWith(".sql"));
        if (files == null || files.length == 0) {
            System.out.println("No SQL migrations found to apply.");
            System.exit(0);
        }

        Arrays.sort(files, Comparator.comparing(File::getName));

        java.util.Properties props = new java.util.Properties();
        if (user != null) {
            props.setProperty("user", user);
        }
        if (password != null) {
            props.setProperty("password", password);
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, props)) {
            System.out.println("Connection successful. Setting up migration tracking...");
            conn.setAutoCommit(true);

            // Ensure reporting schema and schema_migrations table exist
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS reporting;");
                stmt.execute("CREATE TABLE IF NOT EXISTS reporting.schema_migrations (" +
                             "migration_name VARCHAR(255) PRIMARY KEY," +
                             "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                             ");");
            }

            // Retrieve already applied migrations
            java.util.Set<String> appliedMigrations = new java.util.HashSet<>();
            try (Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery("SELECT migration_name FROM reporting.schema_migrations")) {
                while (rs.next()) {
                    appliedMigrations.add(rs.getString("migration_name"));
                }
            }

            // Check if reporting.rpt_report table already exists (indicating schema already initialized)
            boolean rptReportExists = false;
            try (Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(
                     "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'reporting' AND table_name = 'rpt_report')"
                 )) {
                if (rs.next()) {
                    rptReportExists = rs.getBoolean(1);
                }
            }

            // If tables exist but schema_migrations is not populated for 000-007, pre-populate them to skip execution
            if (rptReportExists) {
                for (File file : files) {
                    String name = file.getName();
                    if (name.compareTo("008_") < 0) { // All files before 008
                        if (!appliedMigrations.contains(name)) {
                            System.out.println("Table reporting.rpt_report exists. Marking migration as already applied: " + name);
                            try (java.sql.PreparedStatement pstmt = conn.prepareStatement(
                                "INSERT INTO reporting.schema_migrations (migration_name) VALUES (?) ON CONFLICT DO NOTHING"
                            )) {
                                pstmt.setString(1, name);
                                pstmt.executeUpdate();
                            }
                            appliedMigrations.add(name);
                        }
                    }
                }
            }

            boolean anyApplied = false;
            for (File file : files) {
                String name = file.getName();
                if (appliedMigrations.contains(name)) {
                    System.out.println("Migration " + name + " is already applied. Skipping.");
                    continue;
                }

                System.out.println("Running migration: " + name);
                String sql = Files.readString(file.toPath());
                
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                    
                    // Record applied migration
                    try (java.sql.PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO reporting.schema_migrations (migration_name) VALUES (?)"
                    )) {
                        pstmt.setString(1, name);
                        pstmt.executeUpdate();
                    }
                    anyApplied = true;
                } catch (Exception e) {
                    System.err.println("Error executing migration " + name + ": " + e.getMessage());
                    System.exit(1);
                }
            }

            if (anyApplied) {
                System.out.println("All pending migrations applied successfully!");
            } else {
                System.out.println("No new migrations to apply. Database is up to date.");
            }

        } catch (Exception e) {
            System.err.println("Database connection error: " + e.getMessage());
            System.exit(1);
        }
    }
}
