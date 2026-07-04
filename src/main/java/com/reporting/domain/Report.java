package com.reporting.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "rpt_report", schema = "reporting")
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
    private Integer version = 1;

    @Column(name = "report_name", nullable = false, length = 200)
    private String reportName;

    @Column(name = "description")
    private String description;

    @Column(name = "explore_id")
    private Integer exploreId;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "draft"; // draft | in_review | published

    @Column(name = "granularity", length = 1000)
    private String granularity;

    @Column(name = "timeframe_start", length = 50)
    private String timeframeStart;

    @Column(name = "timeframe_end", length = 50)
    private String timeframeEnd;

    @Column(name = "timeframe_today")
    private Boolean timeframeToday = false;

    @Column(name = "quick_filters")
    private String quickFilters;

    @Column(name = "general_filters")
    private String generalFilters;

    @Column(name = "source_table", length = 150)
    private String sourceTable;

    @Column(name = "source_field", length = 150)
    private String sourceField;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @JsonIgnore
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ColumnDef> columns;

    @JsonIgnore
    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ReportRow> rows;
}
