package com.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rpt_row_column_map", schema = "reporting",
       uniqueConstraints = @UniqueConstraint(columnNames = {"report_id", "row_id", "col_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RowColumnMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Integer mappingId;

    @Column(name = "report_id", nullable = false, length = 50)
    private String reportId;

    @Column(name = "row_id", nullable = false, length = 50)
    private String rowId;

    @Column(name = "col_id", nullable = false, length = 10)
    private String colId;

    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = true;
}
