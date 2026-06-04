package com.reporting.catalog;

import java.util.Objects;

/**
 * In-memory representation of one row from {@code reporting.meta_column}.
 *
 * <p>A {@code MetaColumn} records a single column from a registered DWH table,
 * together with the flags that the graph router uses to prefer conformed join
 * keys over non-conformed ones.</p>
 *
 * <p>Instances are immutable and are associated with their parent
 * {@link MetaTable} through the {@code tableId} foreign key.</p>
 */
public final class MetaColumn {

    /** Surrogate PK from {@code reporting.meta_column.column_id}. */
    private final int columnId;

    /**
     * FK back to {@code reporting.meta_table.table_id}.
     * Used during graph construction to attach the column to its parent node.
     */
    private final int tableId;

    /** Physical column name as it appears in the database, e.g. {@code "customer_id"}. */
    private final String columnName;

    /**
     * PostgreSQL data-type string loaded from {@code reporting.meta_column.data_type},
     * e.g. {@code "integer"}, {@code "varchar"}, {@code "date"}.
     * May be {@code null} when not recorded in the catalog.
     */
    private final String dataType;

    /**
     * {@code true} when this column is the primary key of its table.
     * The router uses this flag to identify the target column of JOIN predicates
     * on the {@code to_table} side.
     */
    private final boolean primaryKey;

    /**
     * {@code true} when this column is a foreign key referencing another table.
     * Used during validation; the actual join semantics are expressed by
     * {@link MetaRelationship} edges.
     */
    private final boolean foreignKey;

    /**
     * {@code true} when this column is a <em>conformed dimension key</em> —
     * a key that is shared across multiple fact tables and therefore safe to
     * use as a spine join key without causing fan-out.
     *
     * <p>Example conformed keys in this schema: {@code customer_id},
     * {@code location_id}, {@code rm_id}.</p>
     */
    private final boolean conformed;

    /** Optional human-readable description loaded from the catalog. */
    private final String description;

    // ─── constructor ─────────────────────────────────────────────────────────

    /**
     * Constructs a {@code MetaColumn} from catalog row data.
     *
     * @param columnId    surrogate PK from {@code reporting.meta_column}
     * @param tableId     FK to the owning {@link MetaTable}
     * @param columnName  physical column name (never blank)
     * @param dataType    PostgreSQL data type string; may be {@code null}
     * @param primaryKey  {@code true} if this is the table's primary key
     * @param foreignKey  {@code true} if this column references another table
     * @param conformed   {@code true} if this is a conformed dimension key
     * @param description optional annotation; may be {@code null}
     */
    public MetaColumn(int columnId,
                      int tableId,
                      String columnName,
                      String dataType,
                      boolean primaryKey,
                      boolean foreignKey,
                      boolean conformed,
                      String description) {
        this.columnId    = columnId;
        this.tableId     = tableId;
        this.columnName  = Objects.requireNonNull(columnName, "columnName must not be null");
        this.dataType    = dataType;
        this.primaryKey  = primaryKey;
        this.foreignKey  = foreignKey;
        this.conformed   = conformed;
        this.description = description;
    }

    // ─── public accessors ─────────────────────────────────────────────────────

    /** @return surrogate PK from {@code reporting.meta_column.column_id} */
    public int getColumnId() {
        return columnId;
    }

    /** @return FK to the owning table's {@code table_id} */
    public int getTableId() {
        return tableId;
    }

    /** @return physical column name, e.g. {@code "customer_id"} */
    public String getColumnName() {
        return columnName;
    }

    /**
     * @return PostgreSQL data type string (e.g. {@code "integer"}),
     *         or {@code null} if not recorded in the catalog
     */
    public String getDataType() {
        return dataType;
    }

    /** @return {@code true} if this column is the primary key of its table */
    public boolean isPrimaryKey() {
        return primaryKey;
    }

    /** @return {@code true} if this column is a foreign key to another table */
    public boolean isForeignKey() {
        return foreignKey;
    }

    /**
     * @return {@code true} if this is a conformed dimension key shared across
     *         multiple fact tables; conformed keys are preferred by the router
     *         to prevent data fan-out
     */
    public boolean isConformed() {
        return conformed;
    }

    /** @return optional human-readable annotation, or {@code null} */
    public String getDescription() {
        return description;
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetaColumn)) return false;
        MetaColumn other = (MetaColumn) o;
        return columnId == other.columnId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(columnId);
    }

    @Override
    public String toString() {
        return "MetaColumn{" +
               "columnId="   + columnId   +
               ", tableId="  + tableId    +
               ", name='"    + columnName + '\'' +
               ", pk="       + primaryKey +
               ", fk="       + foreignKey +
               ", conformed="+ conformed  +
               '}';
    }
}
