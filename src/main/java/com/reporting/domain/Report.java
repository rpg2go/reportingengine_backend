package com.reporting.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "rpt_report", schema = "reporting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @Column(name = "report_id", length = 50)
    private String reportId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "explore_id")
    private Integer exploreId;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "draft"; // draft | published

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ColumnDef> columns;

    @OneToMany(mappedBy = "report", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ReportRow> rows;
}
