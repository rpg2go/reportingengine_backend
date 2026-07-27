package com.db.reporting.service;

import com.db.reporting.config.CacheConfig;
import com.db.reporting.config.DatabaseSchemaProperties;
import com.db.reporting.domain.MetaColumnValueCache;
import com.db.reporting.repository.MetaColumnValueCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service serving pre-cached distinct column values (for dimension or fact tables)
 * directly from Caffeine memory.
 */
@Slf4j
@Service
public class ColumnFilterCacheService {

    private final MetaColumnValueCacheRepository cacheRepository;
    private final DatabaseSchemaProperties dbProperties;

    @org.springframework.beans.factory.annotation.Autowired
    public ColumnFilterCacheService(MetaColumnValueCacheRepository cacheRepository,
                                    @org.springframework.lang.Nullable DatabaseSchemaProperties dbProperties) {
        this.cacheRepository = cacheRepository;
        this.dbProperties = dbProperties != null ? dbProperties : new DatabaseSchemaProperties();
    }

    public ColumnFilterCacheService(MetaColumnValueCacheRepository cacheRepository) {
        this(cacheRepository, new DatabaseSchemaProperties());
    }

    /**
     * Fetches distinct values for a database table column (dimension or fact table).
     * Results are cached in Caffeine memory under the 'filterValues' cache using key 'tableName:columnName'.
     *
     * @param tableName unqualified or qualified table name (e.g., "dim_products", "fact_sales")
     * @param columnName column name (e.g., "product_type", "channel_code")
     * @return sorted list of distinct string values
     */
    @Cacheable(value = CacheConfig.FILTER_VALUES_CACHE, key = "#tableName + ':' + #columnName")
    @Transactional(readOnly = true)
    public List<String> getFilterValues(String tableName, String columnName) {
        String cleanTableName = extractUnqualifiedTableName(tableName);
        log.info("Cache miss for column filter values on [{}:{}]. Querying {}.meta_column_value_cache...", cleanTableName, columnName, dbProperties.getCatalogSchema());

        List<MetaColumnValueCache> records = cacheRepository
                .findByTableNameAndColumnNameOrderByDistinctValueAsc(cleanTableName, columnName);

        List<String> values = records.stream()
                .map(MetaColumnValueCache::getDistinctValue)
                .filter(val -> val != null && !val.isBlank())
                .distinct()
                .collect(Collectors.toList());

        log.info("Found {} distinct cached values for [{}:{}]", values.size(), cleanTableName, columnName);
        return values;
    }

    private String extractUnqualifiedTableName(String table) {
        if (table == null) {
            return "";
        }
        if (table.contains(".")) {
            return table.substring(table.lastIndexOf('.') + 1);
        }
        return table;
    }
}
