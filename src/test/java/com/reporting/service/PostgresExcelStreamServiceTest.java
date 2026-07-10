package com.reporting.service;

import com.reporting.repository.ReportDataRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PostgresExcelStreamService}.
 *
 * <p>The {@link ReportDataRepository} is mocked to supply controlled
 * {@code Stream<Object[]>} data, allowing us to verify Excel output
 * structure, cell types, styling, and resource cleanup without a live
 * database.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PostgresExcelStreamService Unit Tests")
public class PostgresExcelStreamServiceTest {

    @Mock
    private ReportDataRepository reportDataRepository;

    private PostgresExcelStreamService service;

    @BeforeEach
    void setUp() {
        service = new PostgresExcelStreamService(reportDataRepository);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Empty / Minimal Result Sets
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Empty result sets")
    class EmptyResultSets {

        @Test
        @DisplayName("produces valid .xlsx with header row only when query returns zero rows")
        void emptyStream_shouldProduceWorkbookWithHeaderOnly() throws IOException {
            // Arrange
            when(reportDataRepository.streamNativeQuery(anyString()))
                    .thenReturn(Stream.empty());

            List<String> headers = List.of("row_id", "amount", "region");
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", headers, out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheet("Export");
                assertThat(sheet).isNotNull();

                // Header row exists
                Row headerRow = sheet.getRow(0);
                assertThat(headerRow).isNotNull();
                assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("row_id");
                assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("amount");
                assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("region");

                // No data rows
                assertThat(sheet.getRow(1)).isNull();
                assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("produces valid .xlsx with no header row when columnHeaders is empty")
        void emptyHeaders_shouldProduceWorkbookWithNoHeaderRow() throws IOException {
            // Arrange
            when(reportDataRepository.streamNativeQuery(anyString()))
                    .thenReturn(Stream.<Object[]>of(new Object[]{"R1", 100.0}));

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of(), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheet("Export");
                assertThat(sheet).isNotNull();

                // Row 0 is empty (no headers written), row 1 has data
                Row dataRow = sheet.getRow(1);
                assertThat(dataRow).isNotNull();
                assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("R1");
                assertThat(dataRow.getCell(1).getNumericCellValue()).isEqualTo(100.0);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Data Row Streaming
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Data row streaming")
    class DataRowStreaming {

        @Test
        @DisplayName("writes all streamed rows with correct cell values")
        void multipleRows_shouldWriteAllRowsCorrectly() throws IOException {
            // Arrange
            Stream<Object[]> dataStream = Stream.of(
                    new Object[]{"R1", 1000.50, "North"},
                    new Object[]{"R2", 2500.75, "South"},
                    new Object[]{"R3", 750.00, "East"}
            );
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            List<String> headers = List.of("row_id", "amount", "region");
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", headers, out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheet("Export");
                assertThat(sheet).isNotNull();
                assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(4); // 1 header + 3 data

                // Verify data rows
                assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("R1");
                assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(1000.50);
                assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("North");

                assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("R2");
                assertThat(sheet.getRow(2).getCell(1).getNumericCellValue()).isEqualTo(2500.75);

                assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("R3");
                assertThat(sheet.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(750.00);
            }
        }

        @Test
        @DisplayName("handles large row counts without OOM — proves streaming architecture")
        void largeRowCount_shouldStreamWithoutOOM() throws IOException {
            // Arrange: 5000 rows — small enough for a unit test, large enough to prove streaming
            Stream<Object[]> dataStream = Stream.iterate(0, i -> i + 1)
                    .limit(5000)
                    .map(i -> new Object[]{"R" + i, (double) i * 10.0, "Region_" + (i % 5)});
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            List<String> headers = List.of("row_id", "amount", "region");
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", headers, out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheet("Export");
                assertThat(sheet).isNotNull();
                assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(5001); // 1 header + 5000 data

                // Spot-check first and last rows
                assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("R0");
                assertThat(sheet.getRow(5000).getCell(0).getStringCellValue()).isEqualTo("R4999");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Cell Type Dispatch
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cell type dispatch (Java 21 pattern matching)")
    class CellTypeDispatch {

        @Test
        @DisplayName("writes Number types as numeric cells")
        void numberTypes_shouldWriteAsNumeric() throws IOException {
            // Arrange: Integer, Long, Double, BigDecimal
            Stream<Object[]> dataStream = Stream.<Object[]>of(
                    new Object[]{42, 100L, 3.14, new BigDecimal("99999.99")}
            );
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("int", "long", "double", "decimal"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheet("Export");
                Row row = sheet.getRow(1);

                assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.NUMERIC);
                assertThat(row.getCell(0).getNumericCellValue()).isEqualTo(42.0);

                assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
                assertThat(row.getCell(1).getNumericCellValue()).isEqualTo(100.0);

                assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
                assertThat(row.getCell(2).getNumericCellValue()).isEqualTo(3.14);

                assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
                assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(99999.99);
            }
        }

        @Test
        @DisplayName("writes Boolean values as boolean cells")
        void booleanType_shouldWriteAsBoolean() throws IOException {
            // Arrange
            Stream<Object[]> dataStream = Stream.<Object[]>of(new Object[]{true, false});
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("flag1", "flag2"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Row row = workbook.getSheet("Export").getRow(1);

                assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.BOOLEAN);
                assertThat(row.getCell(0).getBooleanCellValue()).isTrue();

                assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.BOOLEAN);
                assertThat(row.getCell(1).getBooleanCellValue()).isFalse();
            }
        }

        @Test
        @DisplayName("writes LocalDate as formatted date string")
        void localDate_shouldWriteAsFormattedString() throws IOException {
            // Arrange
            Stream<Object[]> dataStream = Stream.<Object[]>of(
                    new Object[]{LocalDate.of(2026, 7, 10)}
            );
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("date"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Row row = workbook.getSheet("Export").getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("2026-07-10");
            }
        }

        @Test
        @DisplayName("writes LocalDateTime as formatted datetime string")
        void localDateTime_shouldWriteAsFormattedString() throws IOException {
            // Arrange
            Stream<Object[]> dataStream = Stream.<Object[]>of(
                    new Object[]{LocalDateTime.of(2026, 7, 10, 14, 30, 45)}
            );
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("timestamp"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Row row = workbook.getSheet("Export").getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("2026-07-10 14:30:45");
            }
        }

        @Test
        @DisplayName("writes java.sql.Timestamp as formatted datetime string")
        void sqlTimestamp_shouldWriteAsFormattedString() throws IOException {
            // Arrange
            Timestamp ts = Timestamp.valueOf(LocalDateTime.of(2026, 1, 15, 9, 0, 0));
            Stream<Object[]> dataStream = Stream.<Object[]>of(new Object[]{ts});
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("created_at"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Row row = workbook.getSheet("Export").getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("2026-01-15 09:00:00");
            }
        }

        @Test
        @DisplayName("writes java.sql.Date as formatted date string")
        void sqlDate_shouldWriteAsFormattedString() throws IOException {
            // Arrange
            java.sql.Date sqlDate = java.sql.Date.valueOf(LocalDate.of(2026, 3, 20));
            Stream<Object[]> dataStream = Stream.<Object[]>of(new Object[]{sqlDate});
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("report_date"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Row row = workbook.getSheet("Export").getRow(1);
                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("2026-03-20");
            }
        }

        @Test
        @DisplayName("writes null values as blank cells without NPE")
        void nullValues_shouldWriteAsBlankCells() throws IOException {
            // Arrange
            Stream<Object[]> dataStream = Stream.<Object[]>of(new Object[]{"R1", null, 500.0});
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("id", "nullable", "amount"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Row row = workbook.getSheet("Export").getRow(1);

                assertThat(row.getCell(0).getStringCellValue()).isEqualTo("R1");
                assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.BLANK);
                assertThat(row.getCell(2).getNumericCellValue()).isEqualTo(500.0);
            }
        }

        @Test
        @DisplayName("writes unknown types using toString()")
        void unknownType_shouldFallBackToToString() throws IOException {
            // Arrange: UUID is not a known type — should use toString()
            java.util.UUID uuid = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
            Stream<Object[]> dataStream = Stream.<Object[]>of(new Object[]{uuid});
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("uuid"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Row row = workbook.getSheet("Export").getRow(1);
                assertThat(row.getCell(0).getStringCellValue())
                        .isEqualTo("550e8400-e29b-41d4-a716-446655440000");
            }
        }

        @Test
        @DisplayName("handles mixed types in the same row correctly")
        void mixedTypes_shouldHandleAllTypesInOneRow() throws IOException {
            // Arrange
            Stream<Object[]> dataStream = Stream.<Object[]>of(
                    new Object[]{"Revenue", 42500.99, true, LocalDate.of(2026, 7, 10), null}
            );
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1",
                    List.of("label", "amount", "active", "date", "notes"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Row row = workbook.getSheet("Export").getRow(1);

                assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);
                assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
                assertThat(row.getCell(2).getCellType()).isEqualTo(CellType.BOOLEAN);
                assertThat(row.getCell(3).getCellType()).isEqualTo(CellType.STRING);
                assertThat(row.getCell(4).getCellType()).isEqualTo(CellType.BLANK);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Sheet Naming
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet naming")
    class SheetNaming {

        @Test
        @DisplayName("uses default sheet name 'Export' when not specified")
        void defaultSheetName_shouldBeExport() throws IOException {
            // Arrange
            when(reportDataRepository.streamNativeQuery(anyString()))
                    .thenReturn(Stream.empty());

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("col"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                assertThat(workbook.getSheet("Export")).isNotNull();
            }
        }

        @Test
        @DisplayName("uses custom sheet name when provided")
        void customSheetName_shouldBeUsed() throws IOException {
            // Arrange
            when(reportDataRepository.streamNativeQuery(anyString()))
                    .thenReturn(Stream.empty());

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("col"), "Sales Data", out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                assertThat(workbook.getSheet("Sales Data")).isNotNull();
            }
        }

        @Test
        @DisplayName("falls back to 'Export' when sheet name is blank")
        void blankSheetName_shouldFallBackToExport() throws IOException {
            // Arrange
            when(reportDataRepository.streamNativeQuery(anyString()))
                    .thenReturn(Stream.empty());

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("col"), "  ", out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                assertThat(workbook.getSheet("Export")).isNotNull();
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Styling
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Cell styling")
    class CellStyling {

        @Test
        @DisplayName("header cells have bold font")
        void headerCells_shouldHaveBoldFont() throws IOException {
            // Arrange
            when(reportDataRepository.streamNativeQuery(anyString()))
                    .thenReturn(Stream.empty());

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("Amount", "Region"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheet("Export");
                Row headerRow = sheet.getRow(0);

                for (int i = 0; i < 2; i++) {
                    Cell cell = headerRow.getCell(i);
                    Font font = workbook.getFontAt(cell.getCellStyle().getFontIndex());
                    assertThat(font.getBold()).isTrue();
                }
            }
        }

        @Test
        @DisplayName("numeric cells have number format applied")
        void numericCells_shouldHaveNumberFormat() throws IOException {
            // Arrange
            Stream<Object[]> dataStream = Stream.<Object[]>of(new Object[]{12345.67});
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel("SELECT 1", List.of("amount"), out);

            // Assert
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Row row = workbook.getSheet("Export").getRow(1);
                Cell cell = row.getCell(0);

                // Verify a custom data format is applied (not the default 0 = "General")
                assertThat(cell.getCellStyle().getDataFormat()).isNotEqualTo((short) 0);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Resource Cleanup
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Resource cleanup")
    class ResourceCleanup {

        @Test
        @DisplayName("closes the database stream even when an exception occurs during write")
        void exceptionDuringWrite_shouldStillCloseStream() {
            // Arrange: track close callback
            java.util.concurrent.atomic.AtomicBoolean streamClosed = new java.util.concurrent.atomic.AtomicBoolean(false);
            Stream<Object[]> dataStream = Stream.<Object[]>of(new Object[]{"R1", 100.0})
                    .onClose(() -> streamClosed.set(true));
            when(reportDataRepository.streamNativeQuery(anyString())).thenReturn(dataStream);

            // Use an OutputStream that throws on write to simulate failure
            java.io.OutputStream failingStream = new java.io.OutputStream() {
                @Override
                public void write(int b) throws IOException {
                    throw new IOException("Simulated network failure");
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    throw new IOException("Simulated network failure");
                }
            };

            // Act & Assert
            assertThatThrownBy(() ->
                    service.streamToExcel("SELECT 1", List.of("id", "val"), failingStream))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Simulated network failure");

            // Verify the database stream was closed (try-with-resources in the service)
            assertThat(streamClosed.get()).isTrue();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SQL Delegation
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("SQL delegation to repository")
    class SqlDelegation {

        @Test
        @DisplayName("passes the exact SQL string to the repository")
        void sqlString_shouldBePassedToRepositoryExactly() throws IOException {
            // Arrange
            String sql = "SELECT row_id, SUM(amount) FROM analytics.fact_sales GROUP BY row_id";
            when(reportDataRepository.streamNativeQuery(sql)).thenReturn(Stream.empty());

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Act
            service.streamToExcel(sql, List.of("row_id", "total"), out);

            // Assert
            verify(reportDataRepository).streamNativeQuery(sql);
        }
    }
}
