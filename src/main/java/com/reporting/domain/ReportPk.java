package com.reporting.domain;

import java.io.Serializable;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportPk implements Serializable {
    private String reportId;
    private Integer version;
}
