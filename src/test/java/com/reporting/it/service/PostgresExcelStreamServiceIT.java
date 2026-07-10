package com.reporting.it.service;

import com.reporting.it.BaseIT;
import com.reporting.repository.ReportDataRepository;
import com.reporting.service.PostgresExcelStreamService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link PostgresExcelStreamService} and
 * {@link ReportDataRepository} running against a real PostgreSQL database
 * via Testcontainers.
 *
 * <p>These tests verify the full cursor lifecycle: SQL execution with
 * server-side cursors, stream consumption within a {@code @Transactional}
 * scope, and Excel output correctness.</p>
 *
 * <p>The {@code analytics.fact_sales} table schema is:</p>
 * <pre>
 *   id SERIAL PRIMARY KEY,
 *   reporting_date DATE,
 *   product_id INTEGER,
 *   customer_id INTEGER,
 *   location_id INTEGER,
 *   rm_id INTEGER,
 *   quantity INTEGER,
 *   amount DECIMAL(15, 2)
 * </pre>
 */
@DisplayName("PostgresExcelStreamService Integration Tests")
public class PostgresExcelStreamServiceIT extends BaseIT {

    @Autowired
    private PostgresExcelStreamService streamService;

    @Autowired
    private ReportDataRepository reportDataRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ═══════════════════════════════════════════════════════════════════════
    // ReportDataRepository Cursor Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("ReportDataRepository cursor streaming")
    class CursorStreaming {

        @Test
        @DisplayName("opens a cursor and streams rows from analytics.fact_sales")
        @Transactional(readOnly = true)
        void streamFromFactSales_shouldReturnRowsViaServerCursor() {
            // Arrange: analytics.fact_sales is seeded by BaseIT migrations
            String sql = "SELECT reporting_date, amount, quantity FROM analytics.fact_sales LIMIT 10";

            // Act
            long rowCount;
            try (Stream<Object[]> stream = reportDataRepository.streamNativeQuery(sql)) {
                rowCount = stream.peek(row -> {
                    // Each row should have 3 columns
                    assertThat(row).hasSize(3);
                    // reporting_date (column 0) should not be null
                    assertThat(row[0]).isNotNull();
                }).count();
            }

            // Assert: seeded data should have at least some rows
            assertThat(rowCount).isGreaterThan(0).isLessThanOrEqualTo(10);
        }

        @Test
        @DisplayName("streams an empty result set without error for non-matching query")
        @Transactional(readOnly = true)
        void emptyResultSet_shouldStreamWithoutError() {
            // Arrange: query that matches nothing
            String sql = "SELECT 1 WHERE 1 = 0";

            // Act
            long rowCount;
            try (Stream<Object[]> stream = reportDataRepository.streamNativeQuery(sql)) {
                rowCount = stream.count();
            }

            // Assert
            assertThat(rowCount).isZero();
        }

        @Test
        @DisplayName("correctly normalizes single-column query results into Object[]")
        @Transactional(readOnly = true)
        void singleColumnQuery_shouldNormalizeToObjectArray() {
            // Arrange
            String sql = "SELECT COUNT(*) FROM analytics.fact_sales";

            // Act
            Object[] result;
            try (Stream<Object[]> stream = reportDataRepository.streamNativeQuery(sql)) {
                result = stream.findFirst().orElseThrow();
            }

            // Assert: single value wrapped in Object[1]
            assertThat(result).hasSize(1);
            assertThat(result[0]).isNotNull();
            assertThat(((Number) result[0]).longValue()).isGreaterThanOrEqualTo(0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Full Pipeline: Cursor → SXSSFWorkbook → OutputStream
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full streaming pipeline")
    class FullPipeline {

        @Test
        @DisplayName("streams fact_sales into a valid Excel file with correct row count")
        void streamFactSales_shouldProduceValidExcelWithAllRows() throws IOException {
            // Arrange
            String sql = "SELECT reporting_date, amount, quantity FROM analytics.fact_sales ORDER BY reporting_date LIMIT 50";
            List<String> headers = List.of("reporting_date", "amount", "quantity");
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Count expected rows first
            Integer expectedCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM (SELECT 1 FROM analytics.fact_sales LIMIT 50) sub",
                    Integer.class);

            // Act
            streamService.streamToExcel(sql, headers, out);

            // Assert
            assertThat(out.size()).isGreaterThan(0);
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheet("Export");
                assertThat(sheet).isNotNull();

                // Header row + data rows
                assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(expectedCount + 1);

                // Verify header labels
                Row headerRow = sheet.getRow(0);
                assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("reporting_date");
                assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("amount");
                assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("quantity");

                // Verify at least one data row has non-null values
                if (expectedCount > 0) {
                    Row firstDataRow = sheet.getRow(1);
                    assertThat(firstDataRow).isNotNull();
                    assertThat(firstDataRow.getCell(0)).isNotNull();
                }
            }
        }

        @Test
        @DisplayName("streams aggregated data with GROUP BY into Excel")
        void streamAggregatedQuery_shouldProduceCorrectExcel() throws IOException {
            // Arrange: aggregated query matching how SqlGeneratorService builds queries
            String sql = """
                    SELECT
                        'TOTAL' AS row_id,
                        SUM(amount) AS total_amount,
                        COUNT(*) AS record_count
                    FROM analytics.fact_sales
                    """;
            List<String> headers = List.of("row_id", "total_amount", "record_count");
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            streamService.streamToExcel(sql, headers, out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheet("Export");
                assertThat(sheet).isNotNull();
                assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2); // 1 header + 1 aggregate row

                Row dataRow = sheet.getRow(1);
                assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("TOTAL");
                // Amount and count should be numeric
                assertThat(dataRow.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
                assertThat(dataRow.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            }
        }

        @Test
        @DisplayName("uses custom sheet name in the output workbook")
        void customSheetName_shouldAppearInOutput() throws IOException {
            // Arrange
            String sql = "SELECT 1 AS val";
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            streamService.streamToExcel(sql, List.of("val"), "Revenue Report", out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                assertThat(workbook.getSheet("Revenue Report")).isNotNull();
                assertThat(workbook.getSheet("Export")).isNull();
            }
        }

        @Test
        @DisplayName("handles empty result set from real database without error")
        void emptyResult_shouldProduceHeaderOnlyExcel() throws IOException {
            // Arrange: query that returns zero rows
            String sql = "SELECT reporting_date, amount FROM analytics.fact_sales WHERE 1 = 0";
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            streamService.streamToExcel(sql, List.of("reporting_date", "amount"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheet("Export");
                assertThat(sheet).isNotNull();
                assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1); // header only
                assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("reporting_date");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Type Handling from Real PostgreSQL
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("PostgreSQL data type handling")
    class DataTypeHandling {

        @Test
        @DisplayName("handles mixed PostgreSQL types: text, numeric, date, boolean, null")
        void mixedPostgresTypes_shouldMapToCorrectCellTypes() throws IOException {
            // Arrange: synthesize a row with multiple PostgreSQL data types
            String sql = """
                    SELECT
                        'Revenue'::text AS label,
                        12345.67::numeric AS amount,
                        DATE '2026-07-10' AS report_date,
                        TRUE AS is_active,
                        NULL::text AS notes
                    """;
            List<String> headers = List.of("label", "amount", "report_date", "is_active", "notes");
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            streamService.streamToExcel(sql, headers, out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheet("Export");
                Row row = sheet.getRow(1);

                // Text → STRING
                assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("Revenue");

                // Numeric → NUMERIC
                assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
                assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(12345.67);

                // Date → STRING (formatted as yyyy-MM-dd)
                assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.STRING);
                assertThat(row.getCell(2).getStringCellValue()).isEqualTo("2026-07-10");

                // Boolean → BOOLEAN
                assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.BOOLEAN);
                assertThat(row.getCell(3).getBooleanCellValue()).isTrue();

                // NULL → BLANK
                assertThat(row.getCell(4).getCellType()).isEqualTo(CellType.BLANK);
            }
        }
    }
}
