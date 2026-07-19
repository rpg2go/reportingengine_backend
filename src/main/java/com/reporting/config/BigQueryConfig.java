package com.reporting.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.io.IOException;
import java.util.Collections;

/**
 * Spring configuration class for BigQuery client library setup under 'sit' (production-like) profile.
 * Prepares a thread-safe BigQuery service bean with identity credentials scopes and GCP project parameters.
 */
@Slf4j
@Configuration
@Profile("sit")
public class BigQueryConfig {

    @Value("${gcp.project-id}")
    private String projectId;

    @Bean
    public BigQuery bigQuery() {
        log.info("Initializing thread-safe Google BigQuery service connection factory for project: {}", projectId);
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("Google BigQuery initialization failed: 'gcp.project-id' (GCP_PROJECT_ID) is not configured in the active environment.");
        }
        try {
            // Load credentials using Application Default Credentials (ADC)
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/bigquery"));

            return BigQueryOptions.newBuilder()
                    .setProjectId(projectId)
                    .setCredentials(credentials)
                    .build()
                    .getService();
        } catch (IOException e) {
            log.warn("Failed to initialize Google BigQuery Client using Application Default Credentials (ADC). " +
                     "Attempting fallback to project-only initialization (valid for GCP IAM environments like Cloud Run).", e);
            // Fallback: try default settings (often works inside GCP environments like Cloud Run automatically)
            return BigQueryOptions.newBuilder()
                    .setProjectId(projectId)
                    .build()
                    .getService();
        }
    }
}
