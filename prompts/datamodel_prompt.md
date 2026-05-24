You are a senior data architect and analytics engineer with deep expertise in BI semantic layers, data warehousing (BigQuery), and metadata-driven systems.

I need you to design a **production-grade database schema** for a unified semantic and reporting layer.

---

# 🧠 CONTEXT

I have:

- A Google BigQuery data warehouse (star schema: ~5 fact tables, ~75 dimensions)
- Looker (LookML) semantic layer already defining:
    - dimensions
    - measures
    - joins (explores)

- A custom Excel-based reporting configuration system that defines:
    - report structure (rows, hierarchy)
    - layout (columns, formatting)
    - time intelligence (YTD, MTD, WTD, rolling weeks)
    - calculated rows and columns

---

# 🎯 OBJECTIVE

Design a **metadata-driven database model** that can:

1. Store and represent Looker semantic layer concepts
2. Store Excel-based report definitions
3. Support dynamic SQL generation
4. Support complex report layouts (financial statements)
5. Support time intelligence and derived metrics
6. Be scalable, maintainable, and extensible

---

# 🧩 REQUIRED CAPABILITIES

## Database Used

PostgreSQL

## 🔷 Semantic Layer Support (LookML equivalent)

Must support:

- Models (LookML models)
- Explores (entry points)
- Views (tables)
- Dimensions
- Measures
- Joins (relationships)
- Derived metrics (formulas)
- SQL expressions

---

## 🔷 Reporting Layer Support

Must support:

- Reports (multiple per system)
- Report rows (hierarchical structure)
- Report columns (time-based and calculated)
- Row-based formulas (e.g. R2 - R3)
- Column-based formulas (e.g. WoW %)
- Layout properties (indent, order)
- Style references (formatting)

---

## 🔷 Time Intelligence

Must support:

- WEEK (with offsets)
- MTD (month-to-date)
- YTD (year-to-date)
- Rolling N periods
- Custom time filters

---

## 🔷 Multi-source Support

The model should allow:

- Importing LookML definitions
- Importing Excel report templates
- Future extensibility (e.g. dbt metrics)

---

## 🔷 Versioning & Governance

Must support:

- Versioning of semantic definitions
- Versioning of reports
- Draft vs published states
- Audit fields (created_at, updated_at)

---

# 📦 WHAT I NEED FROM YOU

---

## 1. 🏗️ Database Schema Design

Provide:

- Full list of tables
- Columns with data types
- Primary keys / foreign keys
- Relationships between tables

Organize into logical groups:

- semantic layer tables
- reporting layer tables
- shared/common tables

---

## 2. 🧠 Mapping Between LookML and DB

Explain clearly:

- how LookML constructs map to your schema
    - dimension → ?
    - measure → ?
    - explore → ?
    - join → ?

---

## 3. ⚙️ How Excel Template Maps to DB

Explain:

- how REPORT_DEFINITION sheet maps into tables
- how rows, columns, and formulas are stored

---

## 4. 🔄 Data Flow

Describe:

- Excel → ingestion → DB
- LookML → ingestion → DB
- DB → execution engine → SQL → report

---

## 5. 🚀 Query Generation Support

Explain how this schema enables:

- dynamic SQL generation
- join resolution
- metric computation

---

## 6. ⚠️ Design Trade-offs

Discuss:

- normalization vs performance
- flexibility vs governance
- complexity risks

---

## 7. 💡 Bonus (if possible)

- Suggest BigQuery-specific optimizations
- Suggest indexing / partitioning strategy
- Suggest how to cache or precompute metrics

---

# 🎯 FINAL GOAL

Design a system equivalent to a **Reporting Engine semantic + reporting layer**, where:

- LookML + Excel = input sources
- Database = single source of truth
- Backend = execution engine

---

Be very concrete and implementation-oriented.
Avoid generic answers.
Focus on real-world scalability and maintainability.
