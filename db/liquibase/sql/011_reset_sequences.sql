--changeset devops:011_reset_sequences endDelimiter:; runAlways:true

-- Reset all reporting schema sequences to the actual MAX id in each table.
-- This is required after seed data is inserted with explicit IDs, which
-- bypasses the sequence and causes duplicate-key errors on the next INSERT.

SELECT setval('reporting.rpt_style_style_id_seq',                COALESCE((SELECT MAX(style_id)          FROM reporting.rpt_style), 1));
SELECT setval('reporting.rpt_column_def_column_def_id_seq',      COALESCE((SELECT MAX(column_def_id)     FROM reporting.rpt_column_def), 1));
SELECT setval('reporting.rpt_row_metric_row_metric_id_seq',      COALESCE((SELECT MAX(row_metric_id)     FROM reporting.rpt_row_metric), 1));
SELECT setval('reporting.rpt_row_formula_row_formula_id_seq',    COALESCE((SELECT MAX(row_formula_id)    FROM reporting.rpt_row_formula), 1));
SELECT setval('reporting.rpt_row_column_map_mapping_id_seq',     COALESCE((SELECT MAX(mapping_id)        FROM reporting.rpt_row_column_map), 1));
SELECT setval('reporting.meta_table_table_id_seq',               COALESCE((SELECT MAX(table_id)          FROM reporting.meta_table), 1));
SELECT setval('reporting.meta_column_column_id_seq',             COALESCE((SELECT MAX(column_id)         FROM reporting.meta_column), 1));
SELECT setval('reporting.meta_relationship_relationship_id_seq', COALESCE((SELECT MAX(relationship_id)   FROM reporting.meta_relationship), 1));
