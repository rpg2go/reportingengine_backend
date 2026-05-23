package com.reporting.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "import_run", schema = "reporting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    private Integer runId;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType; // excel | yaml

    @Column(name = "source_path", nullable = false)
    private String sourcePath;

    @Column(name = "report_id", length = 50)
    private String reportId;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending"; // pending | success | failed

    @Column(name = "error_msg")
    private String errorMsg;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
