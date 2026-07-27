package com.db.reporting.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "meta_column_value_cache", schema = "catalog_owner")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetaColumnValueCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schema_name", nullable = false, length = 63)
    private String schemaName;

    @Column(name = "table_name", nullable = false, length = 128)
    private String tableName;

    @Column(name = "column_name", nullable = false, length = 128)
    private String columnName;

    @Column(name = "distinct_value", nullable = false, columnDefinition = "TEXT")
    private String distinctValue;

    @Column(name = "last_updated_at")
    @Builder.Default
    private OffsetDateTime lastUpdatedAt = OffsetDateTime.now();
}
