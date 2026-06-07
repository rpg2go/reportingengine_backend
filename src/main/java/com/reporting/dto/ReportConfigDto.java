package com.reporting.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public class ReportConfigDto {
    @NotBlank(message = "Report ID is required")
    @Size(max = 50, message = "Report ID must be at most 50 characters")
    private String reportId;

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must be at most 200 characters")
    private String name;

    @NotNull(message = "Columns list cannot be null")
    @Valid
    private List<ColumnDefDto> columns;

    @NotNull(message = "Rows list cannot be null")
    @Valid
    private List<ReportRowDto> rows;

    private LocalDate referenceDate;
    private Integer exploreId;

    @NotNull(message = "Status cannot be null")
    private Enums.ReportStatus status;


    @Size(max = 100, message = "Granularity must be at most 100 characters")
    private String granularity;

    @Size(max = 50, message = "Timeframe start must be at most 50 characters")
    private String timeframeStart;

    @Size(max = 50, message = "Timeframe end must be at most 50 characters")
    private String timeframeEnd;

    private Boolean timeframeToday;
    private String quickFilters;
    private String generalFilters;
    private Integer version;

    public ReportConfigDto() {
        this.referenceDate = LocalDate.now();
        this.status = Enums.ReportStatus.draft;
        this.timeframeToday = false;
    }

    public ReportConfigDto(String reportId, String name, List<ColumnDefDto> columns, List<ReportRowDto> rows, 
                           LocalDate referenceDate, Integer exploreId, Enums.ReportStatus status,
                           String granularity, String timeframeStart, String timeframeEnd,
                           Boolean timeframeToday, String quickFilters, String generalFilters) {
        this.reportId = reportId;
        this.name = name;
        this.columns = columns;
        this.rows = rows;
        this.referenceDate = referenceDate != null ? referenceDate : LocalDate.now();
        this.exploreId = exploreId;
        this.status = status != null ? status : Enums.ReportStatus.draft;
        this.granularity = granularity;
        this.timeframeStart = timeframeStart;
        this.timeframeEnd = timeframeEnd;
        this.timeframeToday = timeframeToday != null ? timeframeToday : false;
        this.quickFilters = quickFilters;
        this.generalFilters = generalFilters;
    }

    @Deprecated
    public ReportConfigDto(String reportId, String name, List<ColumnDefDto> columns, List<ReportRowDto> rows, 
                           LocalDate referenceDate, Integer exploreId, Enums.ReportStatus status,
                           String sourceTable, String granularity, String timeframeStart, String timeframeEnd,
                           Boolean timeframeToday, String quickFilters, String generalFilters) {
        this(reportId, name, columns, rows, referenceDate, exploreId, status, granularity, timeframeStart, timeframeEnd, timeframeToday, quickFilters, generalFilters);
        if (rows != null && sourceTable != null && !sourceTable.isBlank()) {
            for (ReportRowDto r : rows) {
                if (r.source() != null && (r.source().getSourceTable() == null || r.source().getSourceTable().isBlank())) {
                    r.source().setSourceTable(sourceTable);
                }
            }
        }
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


    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }

    public String getTimeframeStart() { return timeframeStart; }
    public void setTimeframeStart(String timeframeStart) { this.timeframeStart = timeframeStart; }

    public String getTimeframeEnd() { return timeframeEnd; }
    public void setTimeframeEnd(String timeframeEnd) { this.timeframeEnd = timeframeEnd; }

    public Boolean getTimeframeToday() { return timeframeToday; }
    public void setTimeframeToday(Boolean timeframeToday) { this.timeframeToday = timeframeToday; }

    public String getQuickFilters() { return quickFilters; }
    public void setQuickFilters(String quickFilters) { this.quickFilters = quickFilters; }

    public String getGeneralFilters() { return generalFilters; }
    public void setGeneralFilters(String generalFilters) { this.generalFilters = generalFilters; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    // Convenience Getters
    @JsonIgnore
    public List<ColumnDefDto> getSqlColumns() {
        return columns.stream().filter(ColumnDefDto::isSqlColumn).toList();
    }

    @JsonIgnore
    public List<ColumnDefDto> getCalcColumns() {
        return columns.stream().filter(c -> c.colType() == Enums.ColType.CALC).toList();
    }

    @JsonIgnore
    public List<ReportRowDto> getDataRows() {
        return rows.stream().filter(ReportRowDto::isDataRow).toList();
    }

    @JsonIgnore
    public List<ReportRowDto> getCalcRows() {
        return rows.stream().filter(ReportRowDto::isCalcRow).toList();
    }

    @JsonIgnore
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
