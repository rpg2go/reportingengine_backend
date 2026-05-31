-- Fix RPT_001 Row R6: change from data to calc
UPDATE reporting.rpt_row
SET row_type = 'calc'
WHERE report_id = 'RPT_001' AND row_id = 'R6';

-- Add RPT_001 Row R6 formula
INSERT INTO reporting.rpt_row_formula (report_id, row_id, formula_expr)
VALUES ('RPT_001', 'R6', 'R2+R3+R4')
ON CONFLICT (report_id, row_id) DO UPDATE SET formula_expr = EXCLUDED.formula_expr;

-- Delete RPT_001 Row R6 metric definition
DELETE FROM reporting.rpt_row_metric
WHERE report_id = 'RPT_001' AND row_id = 'R6';

-- Fix RPT_001 Row R8 ccy raw SQL to 'USD' literal
UPDATE reporting.rpt_row_metric
SET sql_expr = '''USD''',
    measure_definition = '{"mode":"raw","aggregation":null,"targetColumn":null,"table":null,"rawSql":"''USD''"}'
WHERE report_id = 'RPT_001' AND row_id = 'R8';
