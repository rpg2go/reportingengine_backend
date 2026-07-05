--liquibase formatted sql
--changeset devops:012_fix_seeded_reports endDelimiter:;

-- 1. LOAN_EXPOSURE
UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(principal_amount * interest_rate / 100.0)',
    measure_definition = '{"sourceTable":"analytics.fact_loans","targetColumn":null,"aggregation":null,"rawExpression":"SUM(principal_amount * interest_rate / 100.0)","mode":"raw","rawSql":"SUM(principal_amount * interest_rate / 100.0)","table":"analytics.fact_loans"}'
WHERE report_id = 'LOAN_EXPOSURE' AND row_id = 'R2' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(principal_amount * 0.02)',
    measure_definition = '{"sourceTable":"analytics.fact_loans","targetColumn":null,"aggregation":null,"rawExpression":"SUM(principal_amount * 0.02)","mode":"raw","rawSql":"SUM(principal_amount * 0.02)","table":"analytics.fact_loans"}'
WHERE report_id = 'LOAN_EXPOSURE' AND row_id = 'R3' AND version = 1;

-- 2. CUSTOMER_INSIGHTS
UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(principal_amount)',
    measure_definition = '{"sourceTable":"analytics.fact_loans","targetColumn":"principal_amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_loans"}'
WHERE report_id = 'CUSTOMER_INSIGHTS' AND row_id = 'R2' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'COUNT(id)',
    measure_definition = '{"sourceTable":"analytics.fact_loans","targetColumn":"id","aggregation":"COUNT","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_loans"}'
WHERE report_id = 'CUSTOMER_INSIGHTS' AND row_id = 'R3' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(principal_amount * interest_rate / 100.0)',
    measure_definition = '{"sourceTable":"analytics.fact_loans","targetColumn":null,"aggregation":null,"rawExpression":"SUM(principal_amount * interest_rate / 100.0)","mode":"raw","rawSql":"SUM(principal_amount * interest_rate / 100.0)","table":"analytics.fact_loans"}'
WHERE report_id = 'CUSTOMER_INSIGHTS' AND row_id = 'R4' AND version = 1;

-- 3. SEGMENTED_FINANCIAL_KPI
UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(amount)',
    measure_definition = '{"sourceTable":"analytics.fact_sales","targetColumn":"amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_sales"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R1' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(amount)',
    measure_definition = '{"sourceTable":"analytics.fact_sales","targetColumn":"amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_sales"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R2' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(amount)',
    measure_definition = '{"sourceTable":"analytics.fact_sales","targetColumn":"amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_sales"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R3' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(amount)',
    measure_definition = '{"sourceTable":"analytics.fact_sales","targetColumn":"amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_sales"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R4' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(CASE WHEN transaction_type = ''credit'' THEN amount ELSE 0 END)',
    measure_definition = '{"sourceTable":"analytics.fact_banking_transactions","targetColumn":null,"aggregation":null,"rawExpression":"SUM(CASE WHEN transaction_type = ''credit'' THEN amount ELSE 0 END)","mode":"raw","rawSql":"SUM(CASE WHEN transaction_type = ''credit'' THEN amount ELSE 0 END)","table":"analytics.fact_banking_transactions"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R6' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(CASE WHEN transaction_type = ''debit'' THEN amount ELSE 0 END)',
    measure_definition = '{"sourceTable":"analytics.fact_banking_transactions","targetColumn":null,"aggregation":null,"rawExpression":"SUM(CASE WHEN transaction_type = ''debit'' THEN amount ELSE 0 END)","mode":"raw","rawSql":"SUM(CASE WHEN transaction_type = ''debit'' THEN amount ELSE 0 END)","table":"analytics.fact_banking_transactions"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R7' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(principal_amount)',
    measure_definition = '{"sourceTable":"analytics.fact_loans","targetColumn":"principal_amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_loans"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R9' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(principal_amount)',
    measure_definition = '{"sourceTable":"analytics.fact_loans","targetColumn":"principal_amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_loans"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R10' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(amount)',
    measure_definition = '{"sourceTable":"analytics.fact_sales","targetColumn":"amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_sales"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R12' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(principal_amount)',
    measure_definition = '{"sourceTable":"analytics.fact_loans","targetColumn":"principal_amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_loans"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R13' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(amount)',
    measure_definition = '{"sourceTable":"analytics.fact_sales","targetColumn":"amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_sales"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R15' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(principal_amount)',
    measure_definition = '{"sourceTable":"analytics.fact_loans","targetColumn":"principal_amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_loans"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R16' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(amount)',
    measure_definition = '{"sourceTable":"analytics.fact_sales","targetColumn":"amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_sales"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R18' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(amount)',
    measure_definition = '{"sourceTable":"analytics.fact_sales","targetColumn":"amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_sales"}'
WHERE report_id = 'SEGMENTED_FINANCIAL_KPI' AND row_id = 'R19' AND version = 1;

-- 4. FINANCIAL_360_KPI
UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(amount)',
    measure_definition = '{"sourceTable":"analytics.fact_sales","targetColumn":"amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_sales"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R2' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(quantity)',
    measure_definition = '{"sourceTable":"analytics.fact_sales","targetColumn":"quantity","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_sales"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R3' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'AVG(amount)',
    measure_definition = '{"sourceTable":"analytics.fact_sales","targetColumn":"amount","aggregation":"AVG","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_sales"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R5' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(CASE WHEN transaction_type = ''credit'' THEN amount ELSE 0 END)',
    measure_definition = '{"sourceTable":"analytics.fact_banking_transactions","targetColumn":null,"aggregation":null,"rawExpression":"SUM(CASE WHEN transaction_type = ''credit'' THEN amount ELSE 0 END)","mode":"raw","rawSql":"SUM(CASE WHEN transaction_type = ''credit'' THEN amount ELSE 0 END)","table":"analytics.fact_banking_transactions"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R10' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(CASE WHEN transaction_type = ''debit'' THEN amount ELSE 0 END)',
    measure_definition = '{"sourceTable":"analytics.fact_banking_transactions","targetColumn":null,"aggregation":null,"rawExpression":"SUM(CASE WHEN transaction_type = ''debit'' THEN amount ELSE 0 END)","mode":"raw","rawSql":"SUM(CASE WHEN transaction_type = ''debit'' THEN amount ELSE 0 END)","table":"analytics.fact_banking_transactions"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R11' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(CASE WHEN transaction_type = ''credit'' THEN amount ELSE -amount END)',
    measure_definition = '{"sourceTable":"analytics.fact_banking_transactions","targetColumn":null,"aggregation":null,"rawExpression":"SUM(CASE WHEN transaction_type = ''credit'' THEN amount ELSE -amount END)","mode":"raw","rawSql":"SUM(CASE WHEN transaction_type = ''credit'' THEN amount ELSE -amount END)","table":"analytics.fact_banking_transactions"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R12' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'COUNT(id)',
    measure_definition = '{"sourceTable":"analytics.fact_banking_transactions","targetColumn":"id","aggregation":"COUNT","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_banking_transactions"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R13' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'AVG(amount)',
    measure_definition = '{"sourceTable":"analytics.fact_banking_transactions","targetColumn":"amount","aggregation":"AVG","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_banking_transactions"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R14' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(principal_amount)',
    measure_definition = '{"sourceTable":"analytics.fact_loans","targetColumn":"principal_amount","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_loans"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R20' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'COUNT(id)',
    measure_definition = '{"sourceTable":"analytics.fact_loans","targetColumn":"id","aggregation":"COUNT","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_loans"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R21' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'AVG(interest_rate)',
    measure_definition = '{"sourceTable":"analytics.fact_loans","targetColumn":"interest_rate","aggregation":"AVG","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_loans"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R22' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'SUM(current_value)',
    measure_definition = '{"sourceTable":"analytics.fact_investments","targetColumn":"current_value","aggregation":"SUM","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_investments"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R23' AND version = 1;

UPDATE reporting.rpt_row_metric
SET sql_expr = 'COUNT(id)',
    measure_definition = '{"sourceTable":"analytics.fact_investments","targetColumn":"id","aggregation":"COUNT","rawExpression":null,"mode":"visual","rawSql":null,"table":"analytics.fact_investments"}'
WHERE report_id = 'FINANCIAL_360_KPI' AND row_id = 'R24' AND version = 1;
