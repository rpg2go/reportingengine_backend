--liquibase formatted sql
--changeset devops:011_reset_sequences endDelimiter:; runAlways:true runOnChange:true

-- Reset all sequences to the actual MAX id in each table.
-- This is required after seed data is inserted with explicit IDs, which
-- bypasses the sequence and causes duplicate-key errors on the next INSERT.

SELECT setval('report_builder_owner.row_style_style_id_seq',                  COALESCE((SELECT MAX(style_id)          FROM report_builder_owner.row_style), 1));
SELECT setval('report_builder_owner.column_definition_column_def_id_seq',     COALESCE((SELECT MAX(column_def_id)     FROM report_builder_owner.column_definition), 1));
SELECT setval('report_builder_owner.row_metric_mapping_row_metric_id_seq',    COALESCE((SELECT MAX(row_metric_id)     FROM report_builder_owner.row_metric_mapping), 1));
SELECT setval('report_builder_owner.row_formula_row_formula_id_seq',          COALESCE((SELECT MAX(row_formula_id)    FROM report_builder_owner.row_formula), 1));
SELECT setval('report_builder_owner.row_column_intersection_mapping_id_seq', COALESCE((SELECT MAX(mapping_id)        FROM report_builder_owner.row_column_intersection), 1));
SELECT setval('catalog_owner.meta_table_table_id_seq',                        COALESCE((SELECT MAX(table_id)          FROM catalog_owner.meta_table), 1));
SELECT setval('catalog_owner.meta_column_column_id_seq',                      COALESCE((SELECT MAX(column_id)         FROM catalog_owner.meta_column), 1));
SELECT setval('catalog_owner.meta_relationship_relationship_id_seq',          COALESCE((SELECT MAX(relationship_id)   FROM catalog_owner.meta_relationship), 1));
