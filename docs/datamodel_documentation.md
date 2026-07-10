# Data Model Specification & Migration Guide

This document defines the database architecture, schema structures, and relationships for the Reporting Engine. It serves as a blueprint for configuring report templates and registering analytical Data Warehouse (DWH) tables.

---

## 📐 Schema Separation

The platform utilizes two PostgreSQL schemas inside the database to decouple reporting configurations and DWH structures:

1.  **`reporting` Schema**: Stores the metadata catalog of DWH structures ([meta_table](file:///Users/mariusdruga/Workspace/reportingengine_backend/db/liquibase/sql/003_create_catalog_tables.sql#L9-L18), [meta_column](file:///Users/mariusdruga/Workspace/reportingengine_backend/db/liquibase/sql/003_create_catalog_tables.sql#L23-L35), and [meta_relationship](file:///Users/mariusdruga/Workspace/reportingengine_backend/db/liquibase/sql/003_create_catalog_tables.sql#L40-L51)) along with report templates, style layouts, and grid mapping coordinates.
2.  **`analytics` Schema**: Represents the actual Data Warehouse (DWH) containing physical facts (e.g., `fact_sales`) and dimensions (e.g., `dim_date`, `dim_location`).

```mermaid
erDiagram
    reporting-meta_table ||--o{ reporting-meta_column : defines
    reporting-meta_table ||--o{ reporting-meta_relationship : joins
    
    reporting-rpt_report ||--o{ reporting-rpt_column_def : "defines columns"
    reporting-rpt_report ||--o{ reporting-rpt_row : "defines rows"
    reporting-rpt_row ||--|| reporting-rpt_row_metric : "binds metrics"
    reporting-rpt_row ||--|| reporting-rpt_row_formula : "binds formulas"
    reporting-rpt_row_column_map }|--|| reporting-rpt_row : references
    reporting-rpt_row_column_map }|--|| reporting-rpt_column_def : references
    
    reporting-rpt_report }|--|| analytics-fact_table : "queries data directly (resolves join pathways via meta_relationship)"
```

---

## 🗄️ 1. The `reporting` Schema: Metadata Catalog & Configurations

The `reporting` schema contains two main sub-groups of tables: the **DWH Metadata Catalog** (replacing the deprecated `sem_*` layers) and the **Report Template Layout Configurations**.

### A. DWH Metadata Catalog (Schema Registry)

These tables register the physical table structures, columns, and foreign key relationships of the Data Warehouse. At startup, [SchemaCatalogLoader.java](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/main/java/com/reporting/catalog/SchemaCatalogLoader.java) caches this graph in-memory, and [SchemaGraphRouter.java](file:///Users/mariusdruga/Workspace/reportingengine_backend/src/main/java/com/reporting/catalog/SchemaGraphRouter.java) executes Dijkstra's BFS to resolve LEFT JOIN chains between facts and dimensions.

#### 1. `reporting.meta_table`

Registers physical tables inside the DWH.
- `table_id` (SERIAL PRIMARY KEY)
- `schema_name` (VARCHAR(63) NOT NULL DEFAULT 'analytics')
- `table_name` (VARCHAR(128) NOT NULL) — physical table name
- `label` (VARCHAR(256)) — UI display name
- `table_type` (VARCHAR(20) NOT NULL CHECK (table_type IN ('fact', 'dimension', 'bridge')))
- `time_key` (VARCHAR(128)) — column name containing the date key (e.g. `'reporting_date'`)
- `description` (TEXT)

#### 2. `reporting.meta_column`

Registers physical columns of the tables.
- `column_id` (SERIAL PRIMARY KEY)
- `table_id` (INTEGER REFERENCES `meta_table(table_id) ON DELETE CASCADE`)
- `column_name` (VARCHAR(128) NOT NULL)
- `label` (VARCHAR(256))
- `data_type` (VARCHAR(64))
- `is_primary_key` (BOOLEAN DEFAULT FALSE)
- `is_foreign_key` (BOOLEAN DEFAULT FALSE)
- `is_conformed` (BOOLEAN DEFAULT FALSE)
- `is_filterable` (BOOLEAN DEFAULT FALSE)
- `description` (TEXT)

#### 3. `reporting.meta_relationship`

Defines physical join routes between tables.
- `relationship_id` (SERIAL PRIMARY KEY)
- `from_table_id` (INTEGER REFERENCES `meta_table(table_id) ON DELETE CASCADE`) — source table
- `from_column` (VARCHAR(128) NOT NULL) — source column
- `to_table_id` (INTEGER REFERENCES `meta_table(table_id) ON DELETE CASCADE`) — target table
- `to_column` (VARCHAR(128) NOT NULL) — target column
- `join_type` (VARCHAR(20) DEFAULT 'LEFT' CHECK (join_type IN ('LEFT', 'INNER', 'RIGHT')))
- `is_conformed` (BOOLEAN DEFAULT FALSE)
- `weight` (INTEGER DEFAULT 1) — Dijktra edge cost (1 = conformed key, 2 = non-conformed FK)
- `description` (TEXT)

---

### B. Report Template Configurations (Normalized Layouts)

These tables define layouts, columns, row styles, metrics, and active coordinates.

#### 1. `reporting.rpt_report`

Defines report headers.
- `report_id` (VARCHAR(50)) — alphanumeric identifier (e.g., `'SALES_OVERVIEW'`)
- `version` (INTEGER DEFAULT 1) — version number
- `report_name` (VARCHAR(200) NOT NULL)
- `description` (TEXT)
- `status` (VARCHAR(20) DEFAULT 'draft' CHECK (status IN ('draft', 'in_review', 'published')))
- `source_table` (VARCHAR(150)) — physical fact table scanned (e.g. `'analytics.fact_sales'`)
- `source_field` (VARCHAR(150)) — fallback field mapping
- `granularity` (VARCHAR(1000)) — physical `GROUP BY` column (e.g. `'dim_location.country_name'`)
- `timeframe_start` (VARCHAR(50)) — offset parameter start
- `timeframe_end` (VARCHAR(50)) — offset parameter end
- `timeframe_today` (BOOLEAN DEFAULT FALSE) — anchors relative to execution date
- `quick_filters` (TEXT) — JSON configuration for distinct dropdown values
- `general_filters` (TEXT) — JSON array for push-down fact filter logic
- `deleted` (BOOLEAN DEFAULT FALSE) — soft-delete flag
- **Primary Key**: `(report_id, version)`

#### 2. `reporting.rpt_style`

Stores cell style attributes.
- `style_id` (SERIAL PRIMARY KEY)
- `name` (VARCHAR(50) NOT NULL UNIQUE) — layout categories (e.g. `'section'`, `'total'`, `'normal'`)
- `font_size` (INTEGER DEFAULT 11)
- `is_bold` (BOOLEAN DEFAULT FALSE)
- `border_top` (BOOLEAN DEFAULT FALSE)
- `border_bottom` (BOOLEAN DEFAULT FALSE)
- `alignment` (VARCHAR(10) CHECK (alignment IN ('left', 'center', 'right')))
- `color_hex` (VARCHAR(7))
- `bg_color_hex` (VARCHAR(7))

#### 3. `reporting.rpt_column_def`

Defines report headers and rolling period configurations.
- `column_def_id` (SERIAL PRIMARY KEY)
- `report_id` (VARCHAR(50))
- `version` (INTEGER)
- `col_id` (VARCHAR(10) NOT NULL) — column grid ID (e.g. `'C1'`)
- `label` (VARCHAR(200))
- `col_type` (VARCHAR(20) CHECK (col_type IN ('WTD', 'MTD', 'YTD', 'ROLLING', 'CALC', 'HEADER')))
- `period_offset` (INTEGER DEFAULT 0) — relative offset (e.g. `0` = current, `-1` = prior period)
- `period_type` (VARCHAR(50)) — time scope details
- `rolling_n` (INTEGER) — rolling boundary count
- `rolling_grain` (VARCHAR(10) CHECK (rolling_grain IN ('DAY', 'WEEK', 'MONTH', 'YEAR')))
- `formula_expr` (TEXT) — expression for `'CALC'` columns
- `display_order` (INTEGER NOT NULL) — ordering index
- `tier_level` (VARCHAR(10) DEFAULT 'L1' CHECK (tier_level IN ('L1', 'L2', 'L3')))
- `parent_id` (VARCHAR(50))
- **Foreign Key**: `(report_id, version) REFERENCES rpt_report (report_id, version) ON DELETE CASCADE`
- **Unique Constraint**: `(report_id, version, col_id)`

#### 4. `reporting.rpt_row`

Defines layout rows.
- `row_id` (VARCHAR(50) NOT NULL) — row grid ID (e.g. `'R1'`)
- `report_id` (VARCHAR(50))
- `version` (INTEGER)
- `parent_row_id` (VARCHAR(50)) — hierarchical parent reference
- `label` (VARCHAR(300) NOT NULL)
- `row_type` (VARCHAR(20) CHECK (row_type IN ('section', 'data', 'calc', 'blank')))
- `display_order` (INTEGER NOT NULL)
- `indent_level` (INTEGER DEFAULT 0)
- `style_id` (INTEGER REFERENCES `rpt_style(style_id)`)
- `filter_expr` (TEXT) — row-level DWH custom filters (e.g. `'category = ''Software'''`)
- **Primary Key**: `(report_id, version, row_id)`
- **Foreign Key**: `(report_id, version) REFERENCES rpt_report (report_id, version) ON DELETE CASCADE`
- **Self-referential FK**: `(report_id, version, parent_row_id) REFERENCES rpt_row (report_id, version, row_id) ON DELETE CASCADE`

#### 5. `reporting.rpt_row_metric`

Links `'data'` rows to physical SQL aggregate expressions.
- `row_metric_id` (SERIAL PRIMARY KEY)
- `report_id` (VARCHAR(50))
- `version` (INTEGER)
- `row_id` (VARCHAR(50))
- `sql_expr` (TEXT) — SQL aggregation (e.g. `'SUM(analytics.fact_sales.amount)'`)
- `measure_definition` (TEXT) — metadata JSON
- **Foreign Key**: `(report_id, version, row_id) REFERENCES rpt_row (report_id, version, row_id) ON DELETE CASCADE`
- **Unique Constraint**: `(report_id, version, row_id)`

#### 6. `reporting.rpt_row_formula`

Links `'calc'` rows to algebraic formulas evaluated via `exp4j`.
- `row_formula_id` (SERIAL PRIMARY KEY)
- `report_id` (VARCHAR(50))
- `version` (INTEGER)
- `row_id` (VARCHAR(50))
- `formula_expr` (TEXT) — algebraic expression (e.g. `'R2 / R3'`)
- **Foreign Key**: `(report_id, version, row_id) REFERENCES rpt_row (report_id, version, row_id) ON DELETE CASCADE`
- **Unique Constraint**: `(report_id, version, row_id)`

#### 7. `reporting.rpt_row_column_map`

Indicates active layout coordinates (grid intersections).
- `mapping_id` (SERIAL PRIMARY KEY)
- `report_id` (VARCHAR(50))
- `version` (INTEGER)
- `row_id` (VARCHAR(50))
- `col_id` (VARCHAR(10))
- `is_enabled` (BOOLEAN DEFAULT TRUE)
- **Foreign Key (Row)**: `(report_id, version, row_id) REFERENCES rpt_row (report_id, version, row_id) ON DELETE CASCADE`
- **Foreign Key (Col)**: `(report_id, version, col_id) REFERENCES rpt_column_def (report_id, version, col_id) ON DELETE CASCADE`
- **Unique Constraint**: `(report_id, version, row_id, col_id)`

---

## 🚀 Blueprint for Migrating to a New Data Model

Follow this three-step blueprint when registering new DWH tables (e.g. `analytics.fact_inventory`) and setting up report template structures to support execution:

### Step 1: Create the Analytics Tables (`analytics` schema)

Define the physical fact table containing a partition-key date column and key dimension links:

```sql
CREATE TABLE analytics.fact_inventory (
    id            SERIAL PRIMARY KEY,
    reporting_date DATE NOT NULL,  -- partition key
    warehouse_id  INTEGER NOT NULL,
    supplier_id   INTEGER NOT NULL,
    stock_qty     INTEGER NOT NULL,
    unit_cost     NUMERIC(15,2) NOT NULL
);
```

### Step 2: Populate the Metadata Catalog (`reporting` schema)

Register the metadata catalog configurations to allow `SchemaGraphRouter` to discover relationships and build dynamic SQL joins:

```sql
-- 1. Register the Table
INSERT INTO reporting.meta_table (schema_name, table_name, label, table_type, time_key, description)
VALUES ('analytics', 'fact_inventory', 'Inventory Fact', 'fact', 'reporting_date', 'Inventory level counts');

-- 2. Register columns
INSERT INTO reporting.meta_column (table_id, column_name, label, data_type, is_primary_key, is_foreign_key, is_conformed)
VALUES 
  ((SELECT table_id FROM reporting.meta_table WHERE table_name = 'fact_inventory'), 'id', 'ID', 'integer', TRUE, FALSE, FALSE),
  ((SELECT table_id FROM reporting.meta_table WHERE table_name = 'fact_inventory'), 'reporting_date', 'Date', 'date', FALSE, TRUE, FALSE),
  ((SELECT table_id FROM reporting.meta_table WHERE table_name = 'fact_inventory'), 'warehouse_id', 'Warehouse ID', 'integer', FALSE, TRUE, TRUE);

-- 3. Register relationships (joins)
INSERT INTO reporting.meta_relationship (from_table_id, from_column, to_table_id, to_column, join_type, weight)
VALUES (
    (SELECT table_id FROM reporting.meta_table WHERE table_name = 'fact_inventory'),
    'warehouse_id',
    (SELECT table_id FROM reporting.meta_table WHERE table_name = 'dim_location'),
    'id',
    'LEFT',
    1
);
```

### Step 3: Populate the Report Template Configuration

Now insert the configuration template mapping directly to the physical facts:

```sql
-- 1. Insert Report Header
INSERT INTO reporting.rpt_report (report_id, report_name, version, status, source_table, granularity)
VALUES ('INV_STATUS', 'Warehouse Inventory Status', 1, 'published', 'analytics.fact_inventory', 'dim_location.country_name');

-- 2. Define Columns (C1 = Current Week, C2 = Prior Week)
INSERT INTO reporting.rpt_column_def (report_id, col_id, label, col_type, period_offset, display_order)
VALUES 
  ('INV_STATUS', 'C1', 'Current Week', 'WTD', 0, 1),
  ('INV_STATUS', 'C2', 'Prior Week', 'WTD', -1, 2);

-- 3. Define Rows
INSERT INTO reporting.rpt_row (report_id, row_id, label, row_type, display_order, indent_level)
VALUES 
  ('INV_STATUS', 'R1', 'INVENTORY REPORT', 'section', 1, 0),
  ('INV_STATUS', 'R2', 'Stock Quantity On Hand', 'data', 2, 1),
  ('INV_STATUS', 'R3', 'Average Unit Cost', 'data', 3, 1),
  ('INV_STATUS', 'R4', 'Total Value on Hand', 'calc', 4, 1);

-- 4. Map Data Rows to physical aggregates
INSERT INTO reporting.rpt_row_metric (report_id, row_id, sql_expr)
VALUES 
  ('INV_STATUS', 'R2', 'SUM(analytics.fact_inventory.stock_qty)'),
  ('INV_STATUS', 'R3', 'AVG(analytics.fact_inventory.unit_cost)');

-- 5. Map Calc Row to algebra
INSERT INTO reporting.rpt_row_formula (report_id, row_id, formula_expr)
VALUES ('INV_STATUS', 'R4', 'R2 * R3');

-- 6. Enable the grid cells mapping
INSERT INTO reporting.rpt_row_column_map (report_id, row_id, col_id, is_enabled)
VALUES 
  ('INV_STATUS', 'R2', 'C1', TRUE),
  ('INV_STATUS', 'R2', 'C2', TRUE),
  ('INV_STATUS', 'R3', 'C1', TRUE),
  ('INV_STATUS', 'R3', 'C2', TRUE),
  ('INV_STATUS', 'R4', 'C1', TRUE),
  ('INV_STATUS', 'R4', 'C2', TRUE);
```
Once seeded, report generation compiles inventory statistics instantly without needing backend code updates.
