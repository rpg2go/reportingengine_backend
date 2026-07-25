package com.reporting.it;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureMockMvc
public abstract class BaseIT {

    public static final PostgreSQLContainer<?> postgres;

    static {
        boolean useTestcontainers = "true".equalsIgnoreCase(System.getenv("USE_TESTCONTAINERS"))
                || "true".equalsIgnoreCase(System.getProperty("USE_TESTCONTAINERS"));
        PostgreSQLContainer<?> container = null;
        if (useTestcontainers) {
            try {
                String dbUser = System.getenv("SPRING_DATASOURCE_USERNAME");
                if (dbUser == null || dbUser.isBlank()) dbUser = System.getProperty("SPRING_DATASOURCE_USERNAME");
                String dbPass = System.getenv("SPRING_DATASOURCE_PASSWORD");
                if (dbPass == null || dbPass.isBlank()) dbPass = System.getProperty("SPRING_DATASOURCE_PASSWORD");
                container = new PostgreSQLContainer<>("postgres:18-alpine")
                        .withDatabaseName("reporting_db");
                if (dbUser != null && !dbUser.isBlank()) {
                    container.withUsername(dbUser);
                }
                if (dbPass != null && !dbPass.isBlank()) {
                    container.withPassword(dbPass);
                }
                container.start();
                System.out.println("Testcontainers PostgreSQL started successfully at port: " + container.getFirstMappedPort());
            } catch (Exception e) {
                System.err.println("Testcontainers failed to start. Falling back to pre-deployed PostgreSQL database. Error: " + e.getMessage());
                container = null;
            }
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
            String urlVal = getEnvOrProp("SPRING_DATASOURCE_URL");
            if (urlVal == null) urlVal = getEnvOrProp("DATABASE_URL");
            String userVal = getEnvOrProp("SPRING_DATASOURCE_USERNAME");
            String passVal = getEnvOrProp("SPRING_DATASOURCE_PASSWORD");

            if (urlVal == null || userVal == null || passVal == null) {
                throw new IllegalStateException("Required database environment variables (SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD) are missing from environment, system properties, and .env file.");
            }

            final String finalUrl = urlVal;
            final String finalUser = userVal;
            final String finalPass = passVal;
            registry.add("spring.datasource.url", () -> finalUrl);
            registry.add("spring.datasource.username", () -> finalUser);
            registry.add("spring.datasource.password", () -> finalPass);
        }
    }

    private static String getEnvOrProp(String name) {
        String val = System.getenv(name);
        if (val == null || val.isBlank()) {
            val = System.getProperty(name);
        }
        if (val == null || val.isBlank() || val.startsWith("${")) {
            val = loadFromDotEnv(name);
        }
        return (val != null && !val.isBlank() && !val.startsWith("${")) ? val : null;
    }

    private static String loadFromDotEnv(String key) {
        try {
            java.nio.file.Path envPath = java.nio.file.Paths.get(".env");
            if (!java.nio.file.Files.exists(envPath)) {
                envPath = java.nio.file.Paths.get("../.env");
            }
            if (java.nio.file.Files.exists(envPath)) {
                for (String line : java.nio.file.Files.readAllLines(envPath)) {
                    line = line.trim();
                    if (line.startsWith("#") || !line.contains("=")) continue;
                    int eqIdx = line.indexOf('=');
                    String k = line.substring(0, eqIdx).trim();
                    String v = line.substring(eqIdx + 1).trim();
                    if (k.equals(key)) {
                        return v;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
