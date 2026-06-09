-- =============================================================================
-- Migration 013: Fix rolling columns enablement and default grain
-- Schema: reporting
-- Description: Enables rolling columns for data/calc rows and sets default rolling grain.
-- Date: 2026-06-09
-- =============================================================================

-- 1. Enable rolling columns (type ROLLING) in rpt_row_column_map for data and calc rows
UPDATE reporting.rpt_row_column_map rcm
SET is_enabled = TRUE
FROM reporting.rpt_column_def cd, reporting.rpt_row r
WHERE rcm.report_id = cd.report_id 
  AND rcm.col_id = cd.col_id 
  AND rcm.report_id = r.report_id 
  AND rcm.row_id = r.row_id
  AND cd.col_type = 'ROLLING'
  AND r.row_type IN ('data', 'calc');

-- 2. Set default rolling grain to 'MONTH' for 3-Mo Rolling columns where grain is null
UPDATE reporting.rpt_column_def
SET rolling_grain = 'MONTH'
WHERE col_type = 'ROLLING' 
  AND rolling_grain IS NULL 
  AND (label ILIKE '%mo%' OR label ILIKE '%month%' OR label ILIKE '%3-mo%');
