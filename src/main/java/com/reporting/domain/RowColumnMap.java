package com.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "row_column_intersection", schema = "reporting",
       uniqueConstraints = @UniqueConstraint(columnNames = {"report_id", "version", "row_id", "col_id"}))
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

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "row_id", nullable = false, length = 50)
    private String rowId;

    @Column(name = "col_id", nullable = false, length = 10)
    private String colId;

    @Column(name = "is_enabled", nullable = false)
    @Builder.Default
    private Boolean isEnabled = true;
}
