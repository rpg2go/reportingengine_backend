package com.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rpt_column_def", schema = "reporting",
       uniqueConstraints = @UniqueConstraint(columnNames = {"report_id", "col_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ColumnDef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "column_def_id")
    private Integer columnDefId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", referencedColumnName = "report_id", nullable = false)
    private Report report;

    @Column(name = "col_id", nullable = false, length = 10)
    private String colId; // C1, C2...

    @Column(name = "label", length = 200)
    private String label;

    @Column(name = "col_type", nullable = false, length = 20)
    private String colType; // WEEK | MTD | YTD | ROLLING | CALC

    @Column(name = "period_offset")
    private Integer periodOffset = 0;

    @Column(name = "rolling_n")
    private Integer rollingN;

    @Column(name = "formula_expr")
    private String formulaExpr;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
