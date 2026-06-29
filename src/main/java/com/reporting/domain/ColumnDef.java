package com.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rpt_column_def", schema = "reporting",
       uniqueConstraints = @UniqueConstraint(columnNames = {"report_id", "version", "col_id"}))
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
    @JoinColumns({
        @JoinColumn(name = "report_id", referencedColumnName = "report_id", nullable = false),
        @JoinColumn(name = "version", referencedColumnName = "version", nullable = false)
    })
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

    @Column(name = "rolling_grain", length = 10)
    private String rollingGrain;

    @Column(name = "formula_expr")
    private String formulaExpr;

    @Column(name = "tier_level", nullable = false, length = 10)
    private String tierLevel = "L1"; // L1 | L2

    @Column(name = "parent_id", length = 50)
    private String parentId;

    @Column(name = "period_type", length = 50)
    private String periodType;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
