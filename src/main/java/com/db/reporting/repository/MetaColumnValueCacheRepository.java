package com.db.reporting.repository;

import com.db.reporting.domain.MetaColumnValueCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MetaColumnValueCacheRepository extends JpaRepository<MetaColumnValueCache, Long> {

    List<MetaColumnValueCache> findByTableNameAndColumnNameOrderByDistinctValueAsc(String tableName, String columnName);

    List<MetaColumnValueCache> findBySchemaNameAndTableNameAndColumnNameOrderByDistinctValueAsc(String schemaName, String tableName, String columnName);
}
