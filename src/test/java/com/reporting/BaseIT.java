package com.reporting;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class BaseIT {

    public static final PostgreSQLContainer<?> postgres;

    static {
        PostgreSQLContainer<?> container = null;
        try {
            // Attempt to initialize and start Testcontainers PostgreSQL
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("agentic_ai")
                    .withUsername("user")
                    .withPassword("password");
            container.start();
            System.out.println("Testcontainers PostgreSQL started successfully at port: " + container.getFirstMappedPort());
        } catch (Exception e) {
            System.err.println("Testcontainers failed to start. Falling back to local PostgreSQL database. Error: " + e.getMessage());
            container = null;
        }
        postgres = container;
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (postgres != null && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        } else {
            // Fallback connection to the running docker container
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:5432/agentic_ai");
            registry.add("spring.datasource.username", () -> "user");
            registry.add("spring.datasource.password", () -> "password");
        }
    }

    @BeforeAll
    public static void setUpDatabase() {
        String jdbcUrl;
        String username;
        String password;

        if (postgres != null && postgres.isRunning()) {
            jdbcUrl = postgres.getJdbcUrl();
            username = postgres.getUsername();
            password = postgres.getPassword();
        } else {
            jdbcUrl = "jdbc:postgresql://127.0.0.1:5432/agentic_ai";
            username = "user";
            password = "password";
        }

        runMigrations(jdbcUrl, username, password);
    }

    private static void runMigrations(String jdbcUrl, String user, String password) {
        System.out.println("Initializing test database migrations on: " + jdbcUrl);
        
        java.util.Properties props = new java.util.Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, props)) {
            conn.setAutoCommit(true);

            // Check if schema is already populated
            boolean tablesExist = false;
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS reporting;");
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_schema = 'reporting' AND table_name = 'rpt_report')")) {
                    if (rs.next()) {
                        tablesExist = rs.getBoolean(1);
                    }
                }
            }

            // Setup migration tracking table
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS reporting.schema_migrations (" +
                             "migration_name VARCHAR(255) PRIMARY KEY," +
                             "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                             ");");
            }

            // Retrieve already applied migrations
            java.util.Set<String> applied = new java.util.HashSet<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT migration_name FROM reporting.schema_migrations")) {
                while (rs.next()) {
                    applied.add(rs.getString("migration_name"));
                }
            }

            // Run migrations from db/migrations directory
            File migrationsDir = new File("db/migrations");
            if (!migrationsDir.exists() || !migrationsDir.isDirectory()) {
                throw new IOException("migrations directory not found.");
            }

            File[] files = migrationsDir.listFiles((dir, name) -> name.endsWith(".sql"));
            if (files == null || files.length == 0) {
                System.out.println("No SQL migrations found to apply.");
                return;
            }

            Arrays.sort(files, Comparator.comparing(File::getName));

            // If tables exist but schema_migrations is not populated for 000-007, pre-populate them to skip execution
            if (tablesExist) {
                for (File file : files) {
                    String name = file.getName();
                    if (name.compareTo("008_") < 0) { // All files before 008
                        if (!applied.contains(name)) {
                            System.out.println("Table reporting.rpt_report exists. Marking migration as already applied in test DB: " + name);
                            try (java.sql.PreparedStatement pstmt = conn.prepareStatement(
                                "INSERT INTO reporting.schema_migrations (migration_name) VALUES (?) ON CONFLICT DO NOTHING"
                            )) {
                                pstmt.setString(1, name);
                                pstmt.executeUpdate();
                            }
                            applied.add(name);
                        }
                    }
                }
            }

            for (File file : files) {
                String name = file.getName();
                if (applied.contains(name)) {
                    continue;
                }
                
                System.out.println("Applying migration file to test DB: " + name);
                String sql = Files.readString(file.toPath());
                
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }

                try (java.sql.PreparedStatement pstmt = conn.prepareStatement(
                        "INSERT INTO reporting.schema_migrations (migration_name) VALUES (?) ON CONFLICT DO NOTHING")) {
                    pstmt.setString(1, name);
                    pstmt.executeUpdate();
                }
            }
            System.out.println("Unapplied migrations successfully applied to test database.");
        } catch (Exception e) {
            System.err.println("Failed to run database migrations during test setup: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
