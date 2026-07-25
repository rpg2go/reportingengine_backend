-- -----------------------------------------------------------------------------
-- 016_update_column_def_check_constraints.sql
-- Update column_definition check constraints to support QTD col_type and QUARTER rolling_grain
-- -----------------------------------------------------------------------------

ALTER TABLE report_builder_owner.column_definition DROP CONSTRAINT IF EXISTS column_definition_col_type_check;
ALTER TABLE report_builder_owner.column_definition ADD CONSTRAINT column_definition_col_type_check CHECK (col_type IN ('WTD', 'MTD', 'QTD', 'YTD', 'ROLLING', 'CALC', 'HEADER'));

ALTER TABLE report_builder_owner.column_definition DROP CONSTRAINT IF EXISTS column_definition_rolling_grain_check;
ALTER TABLE report_builder_owner.column_definition ADD CONSTRAINT column_definition_rolling_grain_check CHECK (rolling_grain IS NULL OR rolling_grain IN ('DAY', 'WEEK', 'MONTH', 'QUARTER', 'YEAR'));
