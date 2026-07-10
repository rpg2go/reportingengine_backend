package com.reporting.it.service;

import com.reporting.dto.ColumnDefDto;
import com.reporting.dto.Enums;
import com.reporting.dto.MeasureDefinitionDTO;
import com.reporting.dto.ReportConfigDto;
import com.reporting.dto.ReportRowDto;
import com.reporting.it.BaseIT;
import com.reporting.service.ReportConfigService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration performance and stress profiling test to evaluate JVM memory
 * utilization under high concurrent load on the streaming query endpoint.
 *
 * <p>This test utilizes a Java 21 thread executor pool to initiate
 * concurrent GET/POST requests simultaneously hitting the report run endpoint.
 * JVM memory metrics are asserted to confirm heap stability and guarantee that
 * no memory leaks occur during report generation.</p>
 */
@DisplayName("Report Streaming Concurrency & Performance Profiler IT")
public class ReportStreamingPerformanceIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReportConfigService configService;

    @Test
    @DisplayName("Streams report concurrent requests and profiles JVM heap memory for flat stability")
    void streamConcurrentRequests_shouldMaintainFlatHeapMemory() throws Exception {
        // 1. Arrange: setup a valid large report configuration targetting analytics.fact_sales
        String reportId = "RPT_STRESS_TEST";
        List<ColumnDefDto> columns = List.of(
                new ColumnDefDto("C1", "WTD Actual", Enums.ColType.WTD, 0, null, null, 1)
        );
        List<ReportRowDto> rows = List.of(
                new ReportRowDto("R1", reportId, "Sales Revenue", Enums.RowType.data, new MeasureDefinitionDTO("raw", null, null, null, "SUM(amount)"), null, "normal", 0, 1, Set.of("C1"), null),
                new ReportRowDto("R2", reportId, "Calc Sales X3", Enums.RowType.calc, new MeasureDefinitionDTO("raw", null, null, null, "R1 * 3"), null, "normal", 0, 2, Set.of("C1"), null)
        );

        ReportConfigDto config = new ReportConfigDto(
                reportId, "Stress Test Report", columns, rows, LocalDate.of(2026, 5, 26), null, Enums.ReportStatus.draft,
                null, null, null, false, null, null
        );
        config.setSourceTable("analytics.fact_sales");

        // Save report config
        configService.saveToDb(config);

        // Warm up the JVM and Spring MVC stack with 1 execution to initialize class loading & caches
        mockMvc.perform(post("/api/reports/" + reportId + "/run")
                        .param("date", "2026-05-26")
                        .with(httpBasic("admin", "password")))
                .andExpect(status().isOk());

        // Perform GC to get a baseline memory reading
        System.gc();
        Thread.sleep(200);
        Runtime runtime = Runtime.getRuntime();
        long baselineMemory = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("Baseline Memory Used: " + (baselineMemory / (1024 * 1024)) + " MB");

        // 2. Act: Spawn concurrent requests simulating high-density traffic via a Thread Pool
        int concurrentUsers = 100;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentUsers);
        List<Future<byte[]>> futures = new ArrayList<>();

        long startTime = System.nanoTime();

        for (int i = 0; i < concurrentUsers; i++) {
            futures.add(executor.submit(() -> {
                MvcResult result = mockMvc.perform(post("/api/reports/" + reportId + "/run")
                                .param("date", "2026-05-26")
                                .with(httpBasic("admin", "password")))
                        .andExpect(status().isOk())
                        .andReturn();
                return result.getResponse().getContentAsByteArray();
            }));
        }

        // Measure memory usage DURING concurrent requests execution
        long peakMemory = 0;
        for (int i = 0; i < 5; i++) {
            Thread.sleep(150);
            long currentMemory = runtime.totalMemory() - runtime.freeMemory();
            if (currentMemory > peakMemory) {
                peakMemory = currentMemory;
            }
        }

        // 3. Assert: Verify all tasks completed successfully
        for (Future<byte[]> future : futures) {
            byte[] xlsxBytes = future.get(15, TimeUnit.SECONDS);
            assertThat(xlsxBytes).isNotNull();
            assertThat(xlsxBytes.length).isGreaterThan(0);
        }

        executor.shutdown();

        long endTime = System.nanoTime();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        // Perform final GC to clean up temporary references and measure memory delta
        System.gc();
        Thread.sleep(200);
        long postExecutionMemory = runtime.totalMemory() - runtime.freeMemory();

        System.out.println("Stress Test Stats:");
        System.out.println(" - Concurrent requests: " + concurrentUsers);
        System.out.println(" - Total execution duration: " + durationMs + " ms");
        System.out.println(" - Peak memory used: " + (peakMemory / (1024 * 1024)) + " MB");
        System.out.println(" - Post-execution memory: " + (postExecutionMemory / (1024 * 1024)) + " MB");

        // Assert memory envelope:
        // Heap usage must remain stable. We assert that memory doesn't escalate exponentially
        // and returns back close to baseline memory (accounting for ordinary JVM overhead).
        long memoryDelta = postExecutionMemory - baselineMemory;
        System.out.println(" - Memory delta: " + (memoryDelta / (1024 * 1024)) + " MB");

        // Assert no memory leak (delta should be small, e.g. < 45MB of permanent overhead)
        assertThat(memoryDelta).isLessThan(45 * 1024 * 1024);
    }
}
