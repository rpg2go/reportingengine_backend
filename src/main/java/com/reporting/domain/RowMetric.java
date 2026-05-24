package com.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rpt_row_metric", schema = "reporting",
       uniqueConstraints = @UniqueConstraint(columnNames = {"report_id", "row_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RowMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_metric_id")
    private Integer rowMetricId;

    @Column(name = "report_id", nullable = false, length = 50)
    private String reportId;

    @Column(name = "row_id", nullable = false, length = 50)
    private String rowId;

    @Column(name = "measure_id")
    private Integer measureId;

    @Column(name = "sql_expr", length = 500)
    private String sqlExpr;

    @Column(name = "explore_id")
    private Integer exploreId;
}
