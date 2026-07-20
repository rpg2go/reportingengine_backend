package com.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "row_definition", schema = "reporting")
@IdClass(ReportRowId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportRow {

    @Id
    @Column(name = "row_id", length = 50)
    private String rowId;

    @Id
    @Column(name = "report_id", length = 50)
    private String reportId;

    @Id
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
        @JoinColumn(name = "report_id", referencedColumnName = "report_id", insertable = false, updatable = false),
        @JoinColumn(name = "version", referencedColumnName = "version", insertable = false, updatable = false)
    })
    private Report report;

    @Column(name = "parent_row_id", length = 50)
    private String parentRowId;

    @Column(name = "label", nullable = false, length = 300)
    private String label;

    @Column(name = "row_type", nullable = false, length = 20)
    private String rowType; // section | data | calc | blank

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "indent_level", nullable = false)
    @Builder.Default
    private Integer indentLevel = 0;

    @Column(name = "style_id")
    private Integer styleId;

    @Column(name = "filter_expr")
    private String filterExpr;
}
