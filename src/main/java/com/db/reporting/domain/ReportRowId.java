package com.db.reporting.domain;

import java.io.Serializable;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ReportRowId implements Serializable {
    private String rowId;
    private String reportId;
    private Integer version;
}
