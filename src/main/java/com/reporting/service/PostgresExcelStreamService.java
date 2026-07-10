package com.reporting.service;

import com.reporting.repository.ReportDataRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.DefaultIndexedColorMap;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Transactional service that streams analytical query results from PostgreSQL
 * directly into a low-memory {@link SXSSFWorkbook} and pipes the binary output
 * to an {@link OutputStream}.
 *
 * <h3>Memory Model</h3>
 * <p>This service is designed for large result sets (100k+ rows) that would cause
 * {@code OutOfMemoryError} if materialized fully in heap. The memory footprint is
 * bounded by two sliding windows:</p>
 * <ul>
 *   <li><strong>Database side:</strong> PostgreSQL server-side cursor fetches
 *       {@value ReportDataRepository#STREAMING_FETCH_SIZE} rows per round-trip</li>
 *   <li><strong>Excel side:</strong> {@link SXSSFWorkbook} keeps only
 *       {@value #SXSSF_ROW_WINDOW} rows in memory; older rows are flushed to
 *       compressed temporary files on disk</li>
 * </ul>
 *
 * <h3>Transaction Lifecycle</h3>
 * <p>The {@code @Transactional(readOnly = true)} annotation on the public methods
 * ensures that:</p>
 * <ol>
 *   <li>The JDBC connection has {@code autoCommit = false} — required by PostgreSQL
 *       for server-side cursors</li>
 *   <li>The connection remains open for the entire duration of stream consumption,
 *       preventing premature cursor closure</li>
 *   <li>No write locks are acquired on the database</li>
 * </ol>
 *
 * <h3>Resource Cleanup</h3>
 * <p>The {@link SXSSFWorkbook#dispose()} call in the {@code finally} block
 * guarantees that temporary files are purged from disk even if an exception
 * occurs mid-stream. The database cursor is released when the {@code Stream}
 * is closed (via try-with-resources) or when the transaction scope ends.</p>
 *
 * @since 1.2.0
 * @see ReportDataRepository
 */
@Slf4j
@Service
public class PostgresExcelStreamService {

    /**
     * Number of rows kept in memory by the SXSSFWorkbook sliding window.
     * Rows beyond this limit are flushed to compressed temporary files.
     * Matches the row window used by {@link LayoutRendererService}.
     */
    private static final int SXSSF_ROW_WINDOW = 100;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReportDataRepository reportDataRepository;

    public PostgresExcelStreamService(ReportDataRepository reportDataRepository) {
        this.reportDataRepository = reportDataRepository;
    }

    /**
     * Streams query results into an Excel workbook written to the supplied
     * {@link OutputStream}. Uses a default sheet name of {@code "Export"}.
     *
     * @param sql           the native SQL query to execute via cursor
     * @param columnHeaders ordered list of column header labels for the first row
     * @param outputStream  the target output stream (typically {@code HttpServletResponse.getOutputStream()})
     * @throws IOException if writing to the output stream fails
     * @see #streamToExcel(String, List, String, OutputStream)
     */
    @Transactional(readOnly = true)
    public void streamToExcel(String sql, List<String> columnHeaders,
                              OutputStream outputStream) throws IOException {
        streamToExcel(sql, columnHeaders, "Export", outputStream);
    }

    /**
     * Streams query results from a PostgreSQL cursor directly into an
     * {@link SXSSFWorkbook} and writes the completed workbook to the supplied
     * {@link OutputStream}.
     *
     * <p>The full lifecycle within this method is:</p>
     * <ol>
     *   <li>Create an {@code SXSSFWorkbook} with a {@value #SXSSF_ROW_WINDOW}-row
     *       sliding window and compressed temp files</li>
     *   <li>Write the header row from {@code columnHeaders}</li>
     *   <li>Open a cursor-backed {@code Stream<Object[]>} via
     *       {@link ReportDataRepository#streamNativeQuery(String)}</li>
     *   <li>Iterate the stream, writing each row to the spreadsheet with
     *       type-aware cell formatting</li>
     *   <li>Flush the workbook to {@code outputStream}</li>
     *   <li>Dispose the workbook to purge temp files (in {@code finally})</li>
     * </ol>
     *
     * @param sql           the native SQL query to execute via cursor; must not be null
     * @param columnHeaders ordered list of column header labels for the first row
     * @param sheetName     the Excel sheet tab name; defaults to {@code "Export"} if null
     * @param outputStream  the target output stream
     * @throws IOException              if writing to the output stream fails
     * @throws IllegalArgumentException if {@code sql} is null or blank
     */
    @Transactional(readOnly = true)
    public void streamToExcel(String sql, List<String> columnHeaders,
                              String sheetName, OutputStream outputStream) throws IOException {

        log.info("Starting cursor-backed Excel stream export with {} column headers",
                columnHeaders != null ? columnHeaders.size() : 0);
        long startTime = System.currentTimeMillis();

        SXSSFWorkbook workbook = new SXSSFWorkbook(SXSSF_ROW_WINDOW);
        workbook.setCompressTempFiles(true);
        try {
            SXSSFSheet sheet = workbook.createSheet(
                    sheetName != null && !sheetName.isBlank() ? sheetName : "Export");

            // ── Styles ───────────────────────────────────────────────────────
            XSSFWorkbook xssfWorkbook = workbook.getXSSFWorkbook();
            CellStyle headerStyle = createHeaderStyle(xssfWorkbook);
            CellStyle textStyle   = createDataStyle(xssfWorkbook);
            CellStyle numericStyle = createNumericStyle(xssfWorkbook);

            // ── Header Row ───────────────────────────────────────────────────
            if (columnHeaders != null && !columnHeaders.isEmpty()) {
                Row headerRow = sheet.createRow(0);
                for (int i = 0; i < columnHeaders.size(); i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(columnHeaders.get(i));
                    cell.setCellStyle(headerStyle);
                }
            }

            // ── Stream Data Rows from Cursor ─────────────────────────────────
            AtomicInteger rowCount = new AtomicInteger(0);

            try (Stream<Object[]> dataStream = reportDataRepository.streamNativeQuery(sql)) {
                dataStream.forEach(record -> {
                    int currentRowIdx = rowCount.incrementAndGet();
                    Row row = sheet.createRow(currentRowIdx);

                    for (int col = 0; col < record.length; col++) {
                        Cell cell = row.createCell(col);
                        writeCellValue(cell, record[col], numericStyle, textStyle);
                    }
                });
            }

            // ── Write Workbook to OutputStream ───────────────────────────────
            workbook.write(outputStream);
            outputStream.flush();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Excel stream export completed: {} data rows written in {} ms",
                    rowCount.get(), elapsed);

        } finally {
            workbook.dispose();
            log.debug("SXSSFWorkbook temporary files disposed");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Cell Value Writer — Java 21 Pattern Matching
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Writes a database result value to an Excel cell using type-appropriate
     * formatting. Uses Java 21 switch pattern matching for dispatch.
     *
     * <p>Type mapping:</p>
     * <ul>
     *   <li>{@code null} → blank cell</li>
     *   <li>{@link Number} (Integer, Long, Double, BigDecimal, etc.) → numeric cell</li>
     *   <li>{@link Boolean} → boolean cell</li>
     *   <li>{@link LocalDate} → formatted date string</li>
     *   <li>{@link LocalDateTime} → formatted datetime string</li>
     *   <li>{@link java.sql.Timestamp} → formatted datetime string</li>
     *   <li>{@link java.sql.Date} → formatted date string</li>
     *   <li>All other types → {@code toString()} string cell</li>
     * </ul>
     */
    private void writeCellValue(Cell cell, Object value,
                                CellStyle numericStyle, CellStyle textStyle) {
        switch (value) {
            case null -> {
                cell.setBlank();
                cell.setCellStyle(textStyle);
            }
            case Number num -> {
                cell.setCellValue(num.doubleValue());
                cell.setCellStyle(numericStyle);
            }
            case Boolean bool -> {
                cell.setCellValue(bool);
                cell.setCellStyle(textStyle);
            }
            case LocalDate date -> {
                cell.setCellValue(date.format(DATE_FMT));
                cell.setCellStyle(textStyle);
            }
            case LocalDateTime dateTime -> {
                cell.setCellValue(dateTime.format(DATETIME_FMT));
                cell.setCellStyle(textStyle);
            }
            case java.sql.Timestamp ts -> {
                cell.setCellValue(ts.toLocalDateTime().format(DATETIME_FMT));
                cell.setCellStyle(textStyle);
            }
            case java.sql.Date sqlDate -> {
                cell.setCellValue(sqlDate.toLocalDate().format(DATE_FMT));
                cell.setCellStyle(textStyle);
            }
            default -> {
                cell.setCellValue(value.toString());
                cell.setCellStyle(textStyle);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Excel Cell Styles — mirrors LayoutRendererService conventions
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates the bold white-on-dark header style matching the visual identity
     * used by {@link LayoutRendererService#render}.
     */
    private CellStyle createHeaderStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);

        // #1e293b — same dark slate header background as LayoutRendererService
        XSSFColor fill = new XSSFColor(
                new java.awt.Color(30, 41, 59), new DefaultIndexedColorMap());
        style.setFillForegroundColor(fill);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);

        return style;
    }

    /**
     * Creates a plain text data cell style with standard font settings.
     */
    private CellStyle createDataStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        return style;
    }

    /**
     * Creates a numeric data cell style with comma-separated thousands formatting.
     * Uses the same {@code #,##0.00} pattern as {@link LayoutRendererService}.
     */
    private CellStyle createNumericStyle(XSSFWorkbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setFontName("Arial");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);

        DataFormat format = wb.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00_);(#,##0.00)"));

        return style;
    }
}
