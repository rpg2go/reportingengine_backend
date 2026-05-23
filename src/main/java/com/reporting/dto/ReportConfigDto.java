package com.reporting.dto;

import java.time.LocalDate;
import java.util.List;

public class ReportConfigDto {
    private String reportId;
    private String name;
    private List<ColumnDefDto> columns;
    private List<ReportRowDto> rows;
    private LocalDate referenceDate;
    private Integer exploreId;
    private Enums.ReportStatus status;

    public ReportConfigDto() {
        this.referenceDate = LocalDate.now();
        this.status = Enums.ReportStatus.draft;
    }

    public ReportConfigDto(String reportId, String name, List<ColumnDefDto> columns, List<ReportRowDto> rows, LocalDate referenceDate, Integer exploreId, Enums.ReportStatus status) {
        this.reportId = reportId;
        this.name = name;
        this.columns = columns;
        this.rows = rows;
        this.referenceDate = referenceDate != null ? referenceDate : LocalDate.now();
        this.exploreId = exploreId;
        this.status = status != null ? status : Enums.ReportStatus.draft;
    }

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<ColumnDefDto> getColumns() { return columns; }
    public void setColumns(List<ColumnDefDto> columns) { this.columns = columns; }

    public List<ReportRowDto> getRows() { return rows; }
    public void setRows(List<ReportRowDto> rows) { this.rows = rows; }

    public LocalDate getReferenceDate() { return referenceDate; }
    public void setReferenceDate(LocalDate referenceDate) { this.referenceDate = referenceDate; }

    public Integer getExploreId() { return exploreId; }
    public void setExploreId(Integer exploreId) { this.exploreId = exploreId; }

    public Enums.ReportStatus getStatus() { return status; }
    public void setStatus(Enums.ReportStatus status) { this.status = status; }

    // Convenience Getters
    public List<ColumnDefDto> getSqlColumns() {
        return columns.stream().filter(ColumnDefDto::isSqlColumn).toList();
    }

    public List<ColumnDefDto> getCalcColumns() {
        return columns.stream().filter(c -> c.colType() == Enums.ColType.CALC).toList();
    }

    public List<ReportRowDto> getDataRows() {
        return rows.stream().filter(ReportRowDto::isDataRow).toList();
    }

    public List<ReportRowDto> getCalcRows() {
        return rows.stream().filter(ReportRowDto::isCalcRow).toList();
    }

    public List<String> getColIds() {
        return columns.stream().map(ColumnDefDto::colId).toList();
    }

    public ColumnDefDto getColumn(String colId) {
        return columns.stream()
            .filter(c -> c.colId().equalsIgnoreCase(colId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Column not found: " + colId));
    }

    public ReportRowDto getRow(String rowId) {
        return rows.stream()
            .filter(r -> r.rowId().equalsIgnoreCase(rowId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Row not found: " + rowId));
    }
}
