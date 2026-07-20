package com.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "row_formula", schema = "reporting",
       uniqueConstraints = @UniqueConstraint(columnNames = {"report_id", "version", "row_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RowFormula {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_formula_id")
    private Integer rowFormulaId;

    @Column(name = "report_id", nullable = false, length = 50)
    private String reportId;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "row_id", nullable = false, length = 50)
    private String rowId;

    @Column(name = "formula_expr", nullable = false)
    private String formulaExpr;
}
