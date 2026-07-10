package com.reporting.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * In-memory representation of one row from {@code reporting.meta_table}.
 *
 * <p>Instances are immutable once constructed and are held inside
 * {@link SchemaCatalogLoader}'s registry for the lifetime of the application.
 * The {@code columns} and {@code outgoingEdges} lists are populated
 * incrementally by the loader after the object is first created; they become
 * read-only from that point forward.</p>
 */
public final class MetaTable {

    /** Surrogate PK from {@code reporting.meta_table.table_id}. */
    private final int tableId;

    /** Physical PostgreSQL schema, e.g. {@code "analytics"}. */
    private final String schemaName;

    /** Physical table name without schema prefix, e.g. {@code "fact_sales"}. */
    private final String tableName;

    /**
     * Logical table classification loaded from {@code table_type}.
     * One of {@code fact}, {@code dimension}, or {@code bridge}.
     */
    private final TableType tableType;

    /**
     * Name of the date/timestamp column used to slice time-period boundaries
     * inside generated CTE aggregation blocks (e.g. {@code "reporting_date"}).
     * {@code null} when the table has no inherent time axis.
     */
    private final String timeKey;

    /** Human-readable description loaded from {@code reporting.meta_table.description}. */
    private final String description;

    /** Flag to control whether columns/types are pre-loaded/cached in memory. */
    private final boolean isCached;

    // ─── mutable collections populated post-construction by the loader ───────

    /** All key columns registered for this table. */
    private final List<MetaColumn> columns = new ArrayList<>();

    /**
     * Directed FK edges originating from this table.
     * Each edge encodes one LEFT JOIN hop, including its cost weight.
     */
    private final List<MetaRelationship> outgoingEdges = new ArrayList<>();

    // ─── constructor ─────────────────────────────────────────────────────────

    /**
     * Constructs a {@code MetaTable} from catalog row data.
     *
     * @param tableId    surrogate PK
     * @param schemaName physical schema name (never blank)
     * @param tableName  physical table name  (never blank)
     * @param tableType  logical role of the table in the star schema
     * @param timeKey    date column used for period slicing; may be {@code null}
     * @param description human-readable annotation; may be {@code null}
     */
    public MetaTable(int tableId,
                     String schemaName,
                     String tableName,
                     TableType tableType,
                     String timeKey,
                     String description) {
        this(tableId, schemaName, tableName, tableType, timeKey, description, true);
    }

    /**
     * Constructs a {@code MetaTable} with explicit isCached configuration.
     */
    public MetaTable(int tableId,
                     String schemaName,
                     String tableName,
                     TableType tableType,
                     String timeKey,
                     String description,
                     boolean isCached) {
        this.tableId     = tableId;
        this.schemaName  = Objects.requireNonNull(schemaName, "schemaName must not be null");
        this.tableName   = Objects.requireNonNull(tableName,  "tableName must not be null");
        this.tableType   = Objects.requireNonNull(tableType,  "tableType must not be null");
        this.timeKey     = timeKey;
        this.description = description;
        this.isCached    = isCached;
    }

    // ─── package-private mutators (loader only) ───────────────────────────────

    /**
     * Registers a column as belonging to this table.
     * Called exclusively by {@link SchemaCatalogLoader} during initialization.
     *
     * @param column the column metadata to attach
     */
    void addColumn(MetaColumn column) {
        columns.add(Objects.requireNonNull(column, "column must not be null"));
    }

    public void addOutgoingEdge(MetaRelationship edge) {
        outgoingEdges.add(Objects.requireNonNull(edge, "edge must not be null"));
    }

    // ─── public accessors ─────────────────────────────────────────────────────

    /** @return surrogate PK matching {@code reporting.meta_table.table_id} */
    public int getTableId() {
        return tableId;
    }

    /** @return physical PostgreSQL schema name, e.g. {@code "analytics"} */
    public String getSchemaName() {
        return schemaName;
    }

    /** @return unqualified table name, e.g. {@code "fact_sales"} */
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns the fully-qualified table name in {@code schema.table} form,
     * suitable for direct use inside SQL FROM/JOIN clauses.
     *
     * @return e.g. {@code "analytics.fact_sales"}
     */
    public String getQualifiedName() {
        return schemaName + "." + tableName;
    }

    /** @return logical classification of the table (fact / dimension / bridge) */
    public TableType getTableType() {
        return tableType;
    }

    /**
     * @return date column used for time-boundary filtering inside generated CTEs,
     *         or {@code null} if this table has no inherent time axis
     */
    public String getTimeKey() {
        return timeKey;
    }

    /** @return human-readable description, or {@code null} */
    public String getDescription() {
        return description;
    }

    /** @return true if schema column metadata should be cached in-memory */
    public boolean isCached() {
        return isCached;
    }

    /**
     * Returns an unmodifiable view of the columns registered for this table.
     *
     * @return immutable list of {@link MetaColumn} instances
     */
    public List<MetaColumn> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    /**
     * Returns an unmodifiable view of directed FK edges that originate from
     * this table.  Each edge represents one possible LEFT JOIN hop the graph
     * router may traverse.
     *
     * @return immutable list of {@link MetaRelationship} instances
     */
    public List<MetaRelationship> getOutgoingEdges() {
        return Collections.unmodifiableList(outgoingEdges);
    }

    // ─── Object overrides ─────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MetaTable)) return false;
        MetaTable other = (MetaTable) o;
        return tableId == other.tableId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(tableId);
    }

    @Override
    public String toString() {
        return "MetaTable{" +
               "tableId="    + tableId     +
               ", qualified='" + getQualifiedName() + '\'' +
               ", type="     + tableType   +
               ", timeKey='" + timeKey     + '\'' +
               '}';
    }

    // ─── nested enum ──────────────────────────────────────────────────────────

    /**
     * Logical classification of a registered table, stored as the
     * {@code table_type} discriminator column in {@code reporting.meta_table}.
     */
    public enum TableType {
        /** A fact table that holds measurable events (e.g. {@code fact_sales}). */
        fact,
        /** A dimension table that provides descriptive attributes (e.g. {@code dim_customers}). */
        dimension,
        /** A bridge / associative table resolving many-to-many relationships. */
        bridge;

        /**
         * Case-insensitive factory that parses catalog string values.
         *
         * @param raw the raw string from the database column
         * @return the matching enum constant
         * @throws IllegalArgumentException if the value is unrecognised
         */
        public static TableType of(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("table_type cannot be blank");
            }
            return TableType.valueOf(raw.trim().toLowerCase());
        }
    }
}
