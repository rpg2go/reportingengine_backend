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

    @NotBlank(message = "Report Name is required")
    @Size(max = 200, message = "Report Name must be at most 200 characters")
    private String reportName;

    @NotNull(message = "Columns list cannot be null")
    @Valid
    private List<ColumnDefDto> columns;

    @NotNull(message = "Rows list cannot be null")
    @Valid
    private List<ReportRowDto> rows;

    @com.fasterxml.jackson.annotation.JsonAlias({"reportingDate", "referenceDate"})
    private LocalDate referenceDate;
    private Integer exploreId;

    @NotNull(message = "Status cannot be null")
    private Enums.ReportStatus status;


    private String granularity;

    private String quickFilters;
    private String generalFilters;
    private Integer version;
    private String sourceTable;
    private String sourceField;

    @Size(max = 16, message = "Reporting date type must be at most 16 characters")
    private String reportingDateType;

    private LocalDate reportingDateStatic;

    @Size(max = 8, message = "Reporting date expression must be at most 8 characters")
    private String reportingDateExpression;

    @Size(max = 16, message = "Timeframe start type must be at most 16 characters")
    private String timeframeStartType;

    private LocalDate timeframeStartStatic;

    @Size(max = 8, message = "Timeframe start expression must be at most 8 characters")
    private String timeframeStartExpression;

    @Size(max = 16, message = "Timeframe end type must be at most 16 characters")
    private String timeframeEndType;

    private LocalDate timeframeEndStatic;

    @Size(max = 8, message = "Timeframe end expression must be at most 8 characters")
    private String timeframeEndExpression;

    public ReportConfigDto() {
        this.referenceDate = LocalDate.now();
        this.status = Enums.ReportStatus.draft;
        this.reportingDateType = "DYNAMIC";
        this.reportingDateExpression = "T-2";
        this.timeframeStartType = "FIXED";
        this.timeframeStartStatic = LocalDate.of(2022, 1, 1);
        this.timeframeEndType = "DYNAMIC";
        this.timeframeEndExpression = "T-2";
    }

    public ReportConfigDto(String reportId, String reportName, List<ColumnDefDto> columns, List<ReportRowDto> rows, 
                           LocalDate referenceDate, Integer exploreId, Enums.ReportStatus status,
                           String granularity, String quickFilters, String generalFilters) {
        this.reportId = reportId;
        this.reportName = reportName;
        this.columns = columns;
        this.rows = rows;
        this.referenceDate = referenceDate != null ? referenceDate : LocalDate.now();
        this.exploreId = exploreId;
        this.status = status != null ? status : Enums.ReportStatus.draft;
        this.granularity = granularity;
        this.quickFilters = quickFilters;
        this.generalFilters = generalFilters;
    }

    @Deprecated
    public ReportConfigDto(String reportId, String reportName, List<ColumnDefDto> columns, List<ReportRowDto> rows, 
                           LocalDate referenceDate, Integer exploreId, Enums.ReportStatus status,
                           String sourceTable, String granularity, String timeframeStart, String timeframeEnd,
                           Boolean timeframeToday, String quickFilters, String generalFilters) {
        this(reportId, reportName, columns, rows, referenceDate, exploreId, status, granularity, quickFilters, generalFilters);
        if (rows != null && sourceTable != null && !sourceTable.isBlank()) {
            for (ReportRowDto r : rows) {
                if (r.source() != null && (r.source().getSourceTable() == null || r.source().getSourceTable().isBlank())) {
                    r.source().setSourceTable(sourceTable);
                }
            }
        }
    }

    @Deprecated
    public ReportConfigDto(String reportId, String reportName, List<ColumnDefDto> columns, List<ReportRowDto> rows, 
                           LocalDate referenceDate, Integer exploreId, Enums.ReportStatus status,
                           String granularity, String timeframeStart, String timeframeEnd,
                           Boolean timeframeToday, String quickFilters, String generalFilters) {
        this(reportId, reportName, columns, rows, referenceDate, exploreId, status, granularity, quickFilters, generalFilters);
    }

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }

    public String getReportName() { return reportName; }
    public void setReportName(String reportName) { this.reportName = reportName; }

    public List<ColumnDefDto> getColumns() { return columns; }
    public void setColumns(List<ColumnDefDto> columns) { this.columns = columns; }

    public List<ReportRowDto> getRows() { return rows; }
    public void setRows(List<ReportRowDto> rows) {
        this.rows = rows;
        if (rows != null && sourceTable != null && !sourceTable.isBlank()) {
            for (ReportRowDto r : rows) {
                if (r.source() != null && (r.source().getSourceTable() == null || r.source().getSourceTable().isBlank())) {
                    r.source().setSourceTable(sourceTable);
                }
            }
        }
    }

    public LocalDate getReferenceDate() { return referenceDate; }
    public void setReferenceDate(LocalDate referenceDate) { this.referenceDate = referenceDate; }
    public void setReportingDate(String reportingDate) {
        if (reportingDate == null || reportingDate.isBlank()) {
            this.referenceDate = null;
            return;
        }
        try {
            this.referenceDate = LocalDate.parse(reportingDate.trim());
        } catch (Exception e) {
            // Quietly ignore parsing failures for relative date expressions (like 'T-2')
            this.referenceDate = null;
        }
    }

    public Integer getExploreId() { return exploreId; }
    public void setExploreId(Integer exploreId) { this.exploreId = exploreId; }

    public Enums.ReportStatus getStatus() { return status; }
    public void setStatus(Enums.ReportStatus status) { this.status = status; }


    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }



    public String getQuickFilters() { return quickFilters; }
    public void setQuickFilters(String quickFilters) { this.quickFilters = quickFilters; }

    public String getGeneralFilters() { return generalFilters; }
    public void setGeneralFilters(String generalFilters) { this.generalFilters = generalFilters; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public String getSourceTable() { return sourceTable; }
    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
        if (rows != null && sourceTable != null && !sourceTable.isBlank()) {
            for (ReportRowDto r : rows) {
                if (r.source() != null && (r.source().getSourceTable() == null || r.source().getSourceTable().isBlank())) {
                    r.source().setSourceTable(sourceTable);
                }
            }
        }
    }

    public String getSourceField() { return sourceField; }
    public void setSourceField(String sourceField) { this.sourceField = sourceField; }

    // Convenience Getters
    @JsonIgnore
    public List<ColumnDefDto> getSqlColumns() {
        return columns.stream().filter(c -> c.isSqlColumn()).toList();
    }

    @JsonIgnore
    public List<ColumnDefDto> getCalcColumns() {
        return columns.stream().filter(c -> c.colType() == Enums.ColType.CALC).toList();
    }

    @JsonIgnore
    public List<ReportRowDto> getDataRows() {
        return rows.stream().filter(r -> r.isDataRow()).toList();
    }

    @JsonIgnore
    public List<ReportRowDto> getCalcRows() {
        return rows.stream().filter(r -> r.isCalcRow()).toList();
    }

    @JsonIgnore
    public List<String> getColIds() {
        return columns.stream().map(c -> c.colId()).toList();
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

    public String getReportingDateType() { return reportingDateType; }
    public void setReportingDateType(String reportingDateType) { this.reportingDateType = reportingDateType; }

    public LocalDate getReportingDateStatic() { return reportingDateStatic; }
    public void setReportingDateStatic(LocalDate reportingDateStatic) { this.reportingDateStatic = reportingDateStatic; }

    public String getReportingDateExpression() { return reportingDateExpression; }
    public void setReportingDateExpression(String reportingDateExpression) { this.reportingDateExpression = reportingDateExpression; }

    public String getTimeframeStartType() { return timeframeStartType; }
    public void setTimeframeStartType(String timeframeStartType) { this.timeframeStartType = timeframeStartType; }

    public LocalDate getTimeframeStartStatic() { return timeframeStartStatic; }
    public void setTimeframeStartStatic(LocalDate timeframeStartStatic) { this.timeframeStartStatic = timeframeStartStatic; }

    public String getTimeframeStartExpression() { return timeframeStartExpression; }
    public void setTimeframeStartExpression(String timeframeStartExpression) { this.timeframeStartExpression = timeframeStartExpression; }

    public String getTimeframeEndType() { return timeframeEndType; }
    public void setTimeframeEndType(String timeframeEndType) { this.timeframeEndType = timeframeEndType; }

    public LocalDate getTimeframeEndStatic() { return timeframeEndStatic; }
    public void setTimeframeEndStatic(LocalDate timeframeEndStatic) { this.timeframeEndStatic = timeframeEndStatic; }

    public String getTimeframeEndExpression() { return timeframeEndExpression; }
    public void setTimeframeEndExpression(String timeframeEndExpression) { this.timeframeEndExpression = timeframeEndExpression; }
}
