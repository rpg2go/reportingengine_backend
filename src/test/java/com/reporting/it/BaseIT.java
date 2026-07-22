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
        PostgreSQLContainer<?> container = null;
        try {
            String dbUser = System.getenv("SPRING_DATASOURCE_USERNAME");
            if (dbUser == null || dbUser.isBlank()) dbUser = System.getProperty("SPRING_DATASOURCE_USERNAME", "user");
            String dbPass = System.getenv("SPRING_DATASOURCE_PASSWORD");
            if (dbPass == null || dbPass.isBlank()) dbPass = System.getProperty("SPRING_DATASOURCE_PASSWORD", "password");
            container = new PostgreSQLContainer<>("postgres:18-alpine")
                    .withDatabaseName("reporting_db")
                    .withUsername(dbUser)
                    .withPassword(dbPass);
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
            String urlVal = System.getenv("DATABASE_URL");
            if (urlVal == null || urlVal.isBlank()) urlVal = System.getProperty("DATABASE_URL", "jdbc:postgresql://127.0.0.1:5433/reporting_db");
            String userVal = System.getenv("SPRING_DATASOURCE_USERNAME");
            if (userVal == null || userVal.isBlank()) userVal = System.getProperty("SPRING_DATASOURCE_USERNAME", "user");
            String passVal = System.getenv("SPRING_DATASOURCE_PASSWORD");
            if (passVal == null || passVal.isBlank()) passVal = System.getProperty("SPRING_DATASOURCE_PASSWORD", "password");
            final String finalUrl = urlVal;
            final String finalUser = userVal;
            final String finalPass = passVal;
            registry.add("spring.datasource.url", () -> finalUrl);
            registry.add("spring.datasource.username", () -> finalUser);
            registry.add("spring.datasource.password", () -> finalPass);
        }
    }
}
