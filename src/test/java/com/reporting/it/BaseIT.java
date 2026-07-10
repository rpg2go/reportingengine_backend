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
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:5433/agentic_ai");
            registry.add("spring.datasource.username", () -> "user");
            registry.add("spring.datasource.password", () -> "password");
        }
    }
}
