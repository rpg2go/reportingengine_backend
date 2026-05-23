-- =============================================================================
-- Migration 006: Seed Analytical Data (Data Warehouse)
-- Schema: analytics
-- Description: Generates realistic mock data for all fact tables for 2024-2026.
--              Dense data for late 2025 ensures showcasing is populated.
-- Date: 2026-04-05
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Helper Function: Random ID from Dimension
-- -----------------------------------------------------------------------------
-- This ensures we always point to valid dimension records.
CREATE OR REPLACE FUNCTION analytics.get_random_id(tbl_name text) RETURNS integer AS $$
DECLARE
    ret_id integer;
BEGIN
    EXECUTE format('SELECT id FROM analytics.%I ORDER BY random() LIMIT 1', tbl_name) INTO ret_id;
    RETURN ret_id;
END;
$$ LANGUAGE plpgsql;

-- -----------------------------------------------------------------------------
-- 2. Clear Existing Facts (Optional but recommended for a clean showcase)
-- -----------------------------------------------------------------------------
TRUNCATE TABLE analytics.fact_sales RESTART IDENTITY;
TRUNCATE TABLE analytics.fact_banking_transactions RESTART IDENTITY;
TRUNCATE TABLE analytics.fact_loans RESTART IDENTITY;
TRUNCATE TABLE analytics.fact_investments RESTART IDENTITY;
TRUNCATE TABLE analytics.fact_department_performance RESTART IDENTITY;

-- -----------------------------------------------------------------------------
-- 3. Seed: fact_sales (Daily sales for all of 2024, 2025, 2026)
--    ~3,000 rows
-- -----------------------------------------------------------------------------
INSERT INTO analytics.fact_sales (reporting_date, product_id, customer_id, location_id, rm_id, quantity, amount)
SELECT 
    d::date as reporting_date,
    analytics.get_random_id('dim_products'),
    analytics.get_random_id('dim_customers'),
    analytics.get_random_id('dim_location'),
    analytics.get_random_id('dim_relationship_manager'),
    (random()*10 + 1)::int as quantity,
    (random()*500 + 50)::numeric(18,2) as amount
FROM generate_series('2024-01-01'::date, '2026-12-31'::date, '1 day'::interval) d
CROSS JOIN generate_series(1, 3); -- 3 sales per day

-- -----------------------------------------------------------------------------
-- 4. Seed: fact_banking_transactions (High volume for accounts)
--    ~5,000 rows
-- -----------------------------------------------------------------------------
INSERT INTO analytics.fact_banking_transactions (reporting_date, account_id, location_id, rm_id, transaction_type, amount)
SELECT 
    d::date as reporting_date,
    analytics.get_random_id('dim_accounts'),
    analytics.get_random_id('dim_location'),
    analytics.get_random_id('dim_relationship_manager'),
    CASE WHEN random() > 0.4 THEN 'debit' ELSE 'credit' END as transaction_type,
    (random()*2000 + 10)::numeric(18,2) as amount
FROM generate_series('2024-01-01'::date, '2026-12-31'::date, '1 day'::interval) d
CROSS JOIN generate_series(1, 5); -- 5 transactions per day

-- -----------------------------------------------------------------------------
-- 5. Seed: fact_loans (Monthly snapshots)
--    ~1,500 rows
-- -----------------------------------------------------------------------------
INSERT INTO analytics.fact_loans (customer_id, reporting_date, location_id, rm_id, loan_type, principal_amount, interest_rate)
SELECT 
    analytics.get_random_id('dim_customers'),
    d::date as reporting_date,
    analytics.get_random_id('dim_location'),
    analytics.get_random_id('dim_relationship_manager'),
    CASE WHEN random() > 0.5 THEN 'Mortgage' ELSE 'Personal' END as loan_type,
    (random()*500000 + 10000)::numeric(18,2) as principal_amount,
    (random()*5 + 2)::numeric(5,2) as interest_rate
FROM generate_series('2024-01-01'::date, '2026-12-31'::date, '1 month'::interval) d
CROSS JOIN generate_series(1, 40); -- 40 loans tracked per month

-- -----------------------------------------------------------------------------
-- 6. Seed: fact_investments (Weekly value snapshots)
--    ~2,000 rows
-- -----------------------------------------------------------------------------
INSERT INTO analytics.fact_investments (customer_id, reporting_date, hier_id, location_id, rm_id, ticker_symbol, quantity, current_value)
SELECT 
    analytics.get_random_id('dim_customers'),
    d::date as reporting_date,
    analytics.get_random_id('dim_investment_hierarchy'),
    analytics.get_random_id('dim_location'),
    analytics.get_random_id('dim_relationship_manager'),
    CASE (random()*5)::int 
        WHEN 0 THEN 'AAPL' WHEN 1 THEN 'MSFT' WHEN 2 THEN 'GOOGL' 
        WHEN 3 THEN 'AMZN' WHEN 4 THEN 'TSLA' ELSE 'META' END as ticker_symbol,
    (random()*100 + 1)::numeric(18,4) as quantity,
    (random()*1000 + 100)::numeric(18,2) as current_value
FROM generate_series('2024-01-01'::date, '2026-12-31'::date, '1 week'::interval) d
CROSS JOIN generate_series(1, 15); -- 15 investment records per week

-- -----------------------------------------------------------------------------
-- 7. Seed: fact_department_performance (Monthly cost tracking)
--    ~1,000 rows
-- -----------------------------------------------------------------------------
INSERT INTO analytics.fact_department_performance (reporting_date, location_id, rm_id, department, cost, budget)
SELECT 
    d::date as reporting_date,
    analytics.get_random_id('dim_location'),
    analytics.get_random_id('dim_relationship_manager'),
    dept as department,
    (random()*50000 + 5000)::numeric(18,2) as cost,
    (random()*50000 + 6000)::numeric(18,2) as budget
FROM generate_series('2024-01-01'::date, '2026-12-31'::date, '1 month'::interval) d
CROSS JOIN (SELECT unnest(ARRAY['Sales','Wealth Management','IT','Operations','Risk']) as dept) depts;

-- -----------------------------------------------------------------------------
-- 8. Clean Up
-- -----------------------------------------------------------------------------
DROP FUNCTION analytics.get_random_id(text);

COMMIT;
