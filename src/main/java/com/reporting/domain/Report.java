package com.reporting.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "report_config", schema = "reporting")
@IdClass(ReportPk.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @Column(name = "report_id", length = 50)
    private String reportId;

    @Id
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "report_name", nullable = false, length = 200)
    private String reportName;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "draft"; // draft | in_review | published

    @Column(name = "granularity", length = 1000)
    private String granularity;



    @Column(name = "quick_filters")
    private String quickFilters;

    @Column(name = "general_filters")
    private String generalFilters;

    @Column(name = "source_table", length = 150)
    private String sourceTable;

    @Column(name = "source_field", length = 150)
    private String sourceField;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "reporting_date_type", length = 16)
    @Builder.Default
    private String reportingDateType = "DYNAMIC";

    @Column(name = "reporting_date_static")
    private java.time.LocalDate reportingDateStatic;

    @Column(name = "reporting_date_expression", length = 8)
    @Builder.Default
    private String reportingDateExpression = "T-2";

    @Column(name = "timeframe_start_type", length = 16)
    @Builder.Default
    private String timeframeStartType = "FIXED";

    @Column(name = "timeframe_start_static")
    @Builder.Default
    private java.time.LocalDate timeframeStartStatic = java.time.LocalDate.of(2022, 1, 1);

    @Column(name = "timeframe_start_expression", length = 8)
    private String timeframeStartExpression;

    @Column(name = "timeframe_end_type", length = 16)
    @Builder.Default
    private String timeframeEndType = "DYNAMIC";

    @Column(name = "timeframe_end_static")
    private java.time.LocalDate timeframeEndStatic;

    @Column(name = "timeframe_end_expression", length = 8)
    @Builder.Default
    private String timeframeEndExpression = "T-2";

    @JsonIgnore
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ColumnDef> columns;

    @JsonIgnore
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ReportRow> rows;
}
