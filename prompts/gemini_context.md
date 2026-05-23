Project Context: Hybrid Metadata Reporting Engine
1. The Problem Statement
Infrastructure: BigQuery environment with 80+ tables (5 Facts, 75 Dimensions).

Bottleneck: Standard SDLC takes ~3 months for simple report changes.

Goal: Enable non-technical business users to define pixel-perfect financial layouts and complex derivations without writing SQL or waiting for deployments.

2. The Solution Architecture (Hybrid Model)
The system relies on three decoupled layers:

Layer 1: Semantic Registry (semantic_model.yaml)

Acts as the "Source of Truth" outside of Looker.

Maps business metrics (e.g., total_revenue) to physical BigQuery SQL and handles automated join logic between facts and dimensions.

Centralizes governance; changing a formula here updates all connected reports.

Layer 2: Layout Engine (hybrid_reporting_template.xlsx)

Section A (Columns): Defines Time Intelligence (MTD, YTD, WoW) using type and offset.

Section B (Body): Defines the visual hierarchy (Parent/Child), row-level math (e.g., R2-R3), and styling (Header, Total, Normal).

Layer 3: Orchestration (Python)

SQL Generator: Parses the Excel and YAML to build a single, optimized BigQuery "Super-Query" using CTEs and CASE WHEN logic for time-based columns.

Post-Processor: Executes row-based calculations (e.g., Gross Profit) and column-based formulas (e.g., WoW %) in-memory after the data is fetched.

3. Key Technical Components for Antigravity
If you are asking the Antigravity agent to generate code, reference these logic blocks:

Join Resolver: Logic to traverse the YAML to find the path from a Fact table to a requested Dimension.

Time Intelligence Mapper: Function that converts Excel offsets (e.g., -1) into SQL DATE_TRUNC and DATE_ADD statements.

Formula Parser: A safe evaluator to process string-based formulas like (C1-C2)/C2 or R1-R5.