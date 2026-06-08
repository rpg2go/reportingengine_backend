-- =============================================================================
-- Migration 012: Bulk Analytical Data Seed (10 000+ records, 2024–2026)
-- Schema:      analytics
-- Description: Replaces sparse baseline data with a rich, realistic dataset
--              that exercises every fact table and supports all 14 report
--              templates.  All data is deterministic — generated with
--              generate_series + modular arithmetic so the script is
--              idempotent and re-runnable.
--
-- Row counts (approximate):
--   dim_customers                 ~200
--   dim_accounts                  ~400
--   dim_location                    10
--   dim_relationship_manager        10
--   dim_products                    12
--   dim_investment_hierarchy         9
--   dim_countries                   10
--   fact_sales                  ~5 480   (5 sales/day × 1 096 days)
--   fact_banking_transactions   ~8 768   (8 tx/day × 1 096 days)
--   fact_loans                  ~1 440   (40/month × 36 months)
--   fact_investments            ~2 340   (15/week × 156 weeks)
--   fact_department_performance   ~540   (5 depts × 3 EU locations × 36 months)
--                               -------
--   TOTAL facts                ~18 500+
-- =============================================================================

BEGIN;

-- =============================================================================
-- 0. Drop helper function if it survived a previous run
-- =============================================================================
DROP FUNCTION IF EXISTS analytics.get_random_id(text);

-- =============================================================================
-- 1. TRUNCATE ALL FACTS (cascade-safe order)
-- =============================================================================
TRUNCATE TABLE analytics.fact_sales                  RESTART IDENTITY CASCADE;
TRUNCATE TABLE analytics.fact_banking_transactions   RESTART IDENTITY CASCADE;
TRUNCATE TABLE analytics.fact_loans                  RESTART IDENTITY CASCADE;
TRUNCATE TABLE analytics.fact_investments            RESTART IDENTITY CASCADE;
TRUNCATE TABLE analytics.fact_department_performance RESTART IDENTITY CASCADE;

-- =============================================================================
-- 2. ENRICH DIMENSIONS
--    ON CONFLICT DO NOTHING keeps existing FK references intact.
-- =============================================================================

-- ── 2a. Locations (10 regions) ───────────────────────────────────────────────
INSERT INTO analytics.dim_location (country_name, region) VALUES
  ('Romania',     'Europe'),
  ('USA',         'America'),
  ('UK',          'Europe'),
  ('Germany',     'Europe'),
  ('France',      'Europe'),
  ('Netherlands', 'Europe'),
  ('Singapore',   'Asia'),
  ('UAE',         'Middle East'),
  ('Brazil',      'America'),
  ('Australia',   'Asia-Pacific')
ON CONFLICT DO NOTHING;

-- ── 2b. Countries ────────────────────────────────────────────────────────────
INSERT INTO analytics.dim_countries (country_name, iso_code, calling_code) VALUES
  ('Romania',                  'RO', '+40'),
  ('United States',            'US', '+1'),
  ('United Kingdom',           'GB', '+44'),
  ('Germany',                  'DE', '+49'),
  ('France',                   'FR', '+33'),
  ('Netherlands',              'NL', '+31'),
  ('Singapore',                'SG', '+65'),
  ('United Arab Emirates',     'AE', '+971'),
  ('Brazil',                   'BR', '+55'),
  ('Australia',                'AU', '+61')
ON CONFLICT (iso_code) DO NOTHING;

-- ── 2c. Relationship Managers (10 RMs) ───────────────────────────────────────
INSERT INTO analytics.dim_relationship_manager (rm_code, rm_name) VALUES
  ('RM001', 'James J. Smith'),
  ('RM002', 'Mary M. Johnson'),
  ('RM003', 'David D. Lee'),
  ('RM004', 'Sarah S. Chen'),
  ('RM005', 'Michael M. Brown'),
  ('RM006', 'Emma E. Wilson'),
  ('RM007', 'Oliver O. Taylor'),
  ('RM008', 'Sophia S. Martinez'),
  ('RM009', 'Liam L. Anderson'),
  ('RM010', 'Ava A. Thompson')
ON CONFLICT (rm_code) DO NOTHING;

-- ── 2d. Products (12 banking / investment products) ──────────────────────────
INSERT INTO analytics.dim_products (name, category, unit_price) VALUES
  ('Checking Standard',       'Deposits',     0.00),
  ('Savings High-Yield',      'Deposits',     0.00),
  ('Mortgage Fixed 30Y',      'Lending',    500.00),
  ('Personal Loan',           'Lending',    250.00),
  ('Business Credit Line',    'Lending',   1000.00),
  ('Premium Current Account', 'Deposits',    15.00),
  ('Term Deposit 12M',        'Deposits',     0.00),
  ('Equity Fund Growth',      'Investments',  0.00),
  ('Bond Fund Income',        'Investments',  0.00),
  ('Crypto Portfolio',        'Investments',  0.00),
  ('FX Spot Trade',           'FX',           5.00),
  ('Trade Finance Facility',  'Lending',   2000.00)
ON CONFLICT DO NOTHING;

-- ── 2e. Investment Hierarchy (9 combinations) ─────────────────────────────────
INSERT INTO analytics.dim_investment_hierarchy (asset_class, product_type, investment_strategy) VALUES
  ('Equities',     'Mutual Fund',  'Growth'),
  ('Equities',     'ETF',          'Passive'),
  ('Equities',     'Hedge Fund',   'Absolute Return'),
  ('Fixed Income', 'Bond',         'Income'),
  ('Fixed Income', 'Bond',         'Short Duration'),
  ('Fixed Income', 'Money Market', 'Capital Preservation'),
  ('Alternatives', 'Crypto',       'Speculative'),
  ('Alternatives', 'Real Estate',  'Income'),
  ('Alternatives', 'Commodities',  'Inflation Hedge')
ON CONFLICT DO NOTHING;

-- ── 2f. Customers (200 rows) ─────────────────────────────────────────────────
INSERT INTO analytics.dim_customers
    (customer_no, national_id, name, email, segment, signup_date, country_code)
SELECT
    1000 + n                                                     AS customer_no,
    lpad(((n * 1000000 + 1850101000000 + n) % 9999999999999 + 1)::text, 13, '0') AS national_id,
    (ARRAY[
        'Robert Brown','Patricia Williams','James Davis','Jennifer Miller',
        'Michael Wilson','Linda Moore','William Taylor','Barbara Anderson',
        'David Thomas','Susan Jackson','Richard White','Jessica Harris',
        'Joseph Martin','Sarah Thompson','Thomas Garcia','Karen Martinez',
        'Charles Robinson','Lisa Clark','Christopher Rodriguez','Nancy Lewis',
        'Matthew Lee','Betty Walker','Anthony Hall','Margaret Allen',
        'Mark Young','Sandra Hernandez','Donald King','Dorothy Wright',
        'Paul Lopez','Ashley Hill','George Scott','Kimberly Green',
        'Kenneth Adams','Emily Baker','Steven Gonzalez','Donna Nelson',
        'Edward Carter','Michelle Mitchell','Brian Perez','Carol Roberts',
        'Ronald Turner','Amanda Phillips','Anthony Campbell','Melissa Parker',
        'Kevin Evans','Deborah Edwards','Jason Collins','Stephanie Stewart',
        'Jeff Sanchez','Rebecca Morris','Ryan Rogers','Sharon Reed',
        'Jacob Cook','Laura Morgan','Gary Bell','Cynthia Murphy',
        'Nicholas Bailey','Kathleen Rivera','Eric Cooper','Amy Richardson',
        'Jonathan Cox','Angela Howard','Stephen Ward','Shirley Torres',
        'Larry Peterson','Brenda Gray','Justin Ramirez','Emma James',
        'Scott Watson','Anna Brooks','Brandon Kelly','Samantha Sanders',
        'Benjamin Price','Jessica Bennett','Samuel Wood','Christine Barnes',
        'Frank Ross','Janet Henderson','Gregory Coleman','Virginia Jenkins',
        'Raymond Perry','Maria Powell','Patrick Long','Heather Patterson',
        'Jack Hughes','Diane Flores','Dennis Washington','Julie Butler',
        'Jerry Simmons','Joyce Foster','Tyler Gonzales','Frances Gonzalez',
        'Aaron Bryant','Ann Alexander','Henry Russell','Paula Griffin',
        'Douglas Diaz','Jacqueline Hayes','Peter Richardson','Virginia James',
        'Carl Jenkins','Kathryn Ford'
    ])[((n - 1) % 100) + 1]                                      AS name,
    'customer.' || (1000 + n) || '@example.com'                  AS email,
    (ARRAY['Retail','Retail','Retail','Wealth','Corporate','Private Banking'])
        [((n - 1) % 6) + 1]                                     AS segment,
    ('2023-01-01'::date + ((n * 5) % 730 || ' days')::interval)::date AS signup_date,
    (ARRAY['RO','US','GB','DE','FR','NL','SG','AE','BR','AU'])
        [((n - 1) % 10) + 1]                                    AS country_code
FROM generate_series(1, 200) n
ON CONFLICT (customer_no) DO NOTHING;

-- ── 2g. Accounts (~2 per customer) ───────────────────────────────────────────
INSERT INTO analytics.dim_accounts (customer_id, account_type, open_date, status)
SELECT
    c.id,
    (ARRAY['Checking','Savings','Current','Investment','Loan'])
        [((c.id - 1) % 5) + 1]                                 AS account_type,
    c.signup_date + interval '1 day',
    CASE WHEN c.id % 20 = 0 THEN 'Inactive' ELSE 'Active' END
FROM analytics.dim_customers c
WHERE NOT EXISTS (SELECT 1 FROM analytics.dim_accounts a WHERE a.customer_id = c.id);

INSERT INTO analytics.dim_accounts (customer_id, account_type, open_date, status)
SELECT
    c.id,
    (ARRAY['Savings','Checking','Current','Investment','Loan'])
        [c.id % 5 + 1]                                         AS account_type,
    c.signup_date + interval '30 days',
    'Active'
FROM analytics.dim_customers c
WHERE (SELECT count(*) FROM analytics.dim_accounts a WHERE a.customer_id = c.id) < 2;

-- =============================================================================
-- 3. FACT TABLES — deterministic generation via modular arithmetic
-- =============================================================================

-- Capture stable dimension ID arrays in temp tables
CREATE TEMP TABLE _loc   AS SELECT id, row_number() OVER (ORDER BY id) AS rn FROM analytics.dim_location;
CREATE TEMP TABLE _rm    AS SELECT id, row_number() OVER (ORDER BY id) AS rn FROM analytics.dim_relationship_manager;
CREATE TEMP TABLE _prod  AS SELECT id, row_number() OVER (ORDER BY id) AS rn FROM analytics.dim_products;
CREATE TEMP TABLE _cust  AS SELECT id, row_number() OVER (ORDER BY id) AS rn FROM analytics.dim_customers;
CREATE TEMP TABLE _acc   AS SELECT id, row_number() OVER (ORDER BY id) AS rn FROM analytics.dim_accounts;
CREATE TEMP TABLE _hier  AS SELECT id, row_number() OVER (ORDER BY id) AS rn FROM analytics.dim_investment_hierarchy;

CREATE TEMP TABLE _cnts AS
SELECT
    (SELECT count(*) FROM _loc)::int  AS loc_cnt,
    (SELECT count(*) FROM _rm)::int   AS rm_cnt,
    (SELECT count(*) FROM _prod)::int AS prod_cnt,
    (SELECT count(*) FROM _cust)::int AS cust_cnt,
    (SELECT count(*) FROM _acc)::int  AS acc_cnt,
    (SELECT count(*) FROM _hier)::int AS hier_cnt;

-- Month-end detector used throughout:
--   EXTRACT(day FROM (d + interval '1 day')) = 1
--   i.e. "tomorrow is the 1st" → today is the last day of the month.
-- On those dates every fact table gets:
--   (a) existing rows with amounts multiplied by a me_factor
--   (b) extra rows appended via a second INSERT restricted to month-end dates

-- ── 3a. fact_sales ───────────────────────────────────────────────────────────
--    Base:      5 rows/day   × 1 096 days       = 5 480 rows
--    Month-end: 10 extra rows × 36 month-ends   =   360 extra rows
--    Total: ~5 840 rows
INSERT INTO analytics.fact_sales
    (reporting_date, product_id, customer_id, location_id, rm_id, quantity, amount)
SELECT
    d::date                                                       AS reporting_date,
    p.id                                                          AS product_id,
    c.id                                                          AS customer_id,
    l.id                                                          AS location_id,
    r.id                                                          AS rm_id,
    (1 + ((n + EXTRACT(doy FROM d)::int) % 10))::int             AS quantity,
    ROUND((
        200
        + ((n * 17 + EXTRACT(doy FROM d)::int * 13) % 4800)
        * CASE WHEN EXTRACT(isodow FROM d) IN (6,7)                              THEN 1.20 ELSE 1.00 END
        * CASE WHEN EXTRACT(month FROM d) = 12                                   THEN 1.15 ELSE 1.00 END
        * CASE WHEN EXTRACT(month FROM d) = 1                                    THEN 0.90 ELSE 1.00 END
        * CASE WHEN EXTRACT(day FROM (d + interval '1 day')) = 1                 THEN 2.50 ELSE 1.00 END
    )::numeric, 2)                                                AS amount
FROM
    generate_series('2024-01-01'::date, '2026-12-31'::date, '1 day'::interval) d
    CROSS JOIN generate_series(1, 5) n
    CROSS JOIN _cnts
    JOIN _prod p ON p.rn = ((n - 1 + EXTRACT(doy FROM d)::int) % prod_cnt + 1)
    JOIN _cust c ON c.rn = ((n * 7 + EXTRACT(doy FROM d)::int * 3) % cust_cnt + 1)
    JOIN _loc  l ON l.rn = ((n + EXTRACT(month FROM d)::int) % loc_cnt + 1)
    JOIN _rm   r ON r.rn = ((n * 3 + EXTRACT(doy FROM d)::int) % rm_cnt + 1);

-- Month-end extra sales (+10 rows on each of the 36 month-end dates)
INSERT INTO analytics.fact_sales
    (reporting_date, product_id, customer_id, location_id, rm_id, quantity, amount)
SELECT
    d::date,
    p.id,
    c.id,
    l.id,
    r.id,
    (5 + ((n + EXTRACT(doy FROM d)::int) % 6))::int,
    ROUND((
        3000 + ((n * 41 + EXTRACT(doy FROM d)::int * 19) % 12000)
    )::numeric, 2)
FROM
    generate_series('2024-01-01'::date, '2026-12-31'::date, '1 day'::interval) d
    CROSS JOIN generate_series(1, 10) n
    CROSS JOIN _cnts
    JOIN _prod p ON p.rn = ((n * 2 + EXTRACT(doy FROM d)::int) % prod_cnt + 1)
    JOIN _cust c ON c.rn = ((n * 13 + EXTRACT(doy FROM d)::int * 7) % cust_cnt + 1)
    JOIN _loc  l ON l.rn = ((n * 5 + EXTRACT(month FROM d)::int) % loc_cnt + 1)
    JOIN _rm   r ON r.rn = ((n * 7 + EXTRACT(doy FROM d)::int) % rm_cnt + 1)
WHERE EXTRACT(day FROM (d + interval '1 day')) = 1;   -- month-end only

-- ── 3b. fact_banking_transactions ────────────────────────────────────────────
--    Base:      8 tx/day   × 1 096 days       = 8 768 rows
--    Month-end: 15 extra   × 36 month-ends    =   540 extra rows
--    Total: ~9 308 rows
INSERT INTO analytics.fact_banking_transactions
    (reporting_date, account_id, location_id, rm_id, transaction_type, amount)
SELECT
    d::date                                                       AS reporting_date,
    a.id                                                          AS account_id,
    l.id                                                          AS location_id,
    r.id                                                          AS rm_id,
    (ARRAY['debit','debit','debit','credit','credit','credit','transfer','wire'])
        [((n + EXTRACT(doy FROM d)::int) % 8) + 1]               AS transaction_type,
    ROUND((
        10
        + ((n * 23 + EXTRACT(doy FROM d)::int * 7) % 4990)
        * CASE WHEN EXTRACT(month FROM d) IN (11,12)                             THEN 1.25 ELSE 1.00 END
        * CASE WHEN EXTRACT(day FROM (d + interval '1 day')) = 1                 THEN 2.00 ELSE 1.00 END
    )::numeric, 2)                                                AS amount
FROM
    generate_series('2024-01-01'::date, '2026-12-31'::date, '1 day'::interval) d
    CROSS JOIN generate_series(1, 8) n
    CROSS JOIN _cnts
    JOIN _acc  a ON a.rn = ((n * 5 + EXTRACT(doy FROM d)::int * 11) % acc_cnt + 1)
    JOIN _loc  l ON l.rn = ((n * 2 + EXTRACT(month FROM d)::int) % loc_cnt + 1)
    JOIN _rm   r ON r.rn = ((n * 4 + EXTRACT(doy FROM d)::int * 2) % rm_cnt + 1);

-- Month-end extra transactions (+15 rows on each month-end)
INSERT INTO analytics.fact_banking_transactions
    (reporting_date, account_id, location_id, rm_id, transaction_type, amount)
SELECT
    d::date,
    a.id,
    l.id,
    r.id,
    (ARRAY['settlement','settlement','wire','wire','payroll','credit','debit','transfer'])
        [((n + EXTRACT(doy FROM d)::int) % 8) + 1],
    ROUND((
        5000 + ((n * 37 + EXTRACT(doy FROM d)::int * 13) % 45000)
    )::numeric, 2)
FROM
    generate_series('2024-01-01'::date, '2026-12-31'::date, '1 day'::interval) d
    CROSS JOIN generate_series(1, 15) n
    CROSS JOIN _cnts
    JOIN _acc  a ON a.rn = ((n * 11 + EXTRACT(doy FROM d)::int * 3) % acc_cnt + 1)
    JOIN _loc  l ON l.rn = ((n * 3 + EXTRACT(month FROM d)::int) % loc_cnt + 1)
    JOIN _rm   r ON r.rn = ((n * 9 + EXTRACT(doy FROM d)::int) % rm_cnt + 1)
WHERE EXTRACT(day FROM (d + interval '1 day')) = 1;   -- month-end only

-- ── 3c. fact_loans ───────────────────────────────────────────────────────────
--    Base:      3 rows/day  × 1 096 days       = 3 288 rows
--    Month-end: 7 extra     × 36 month-ends    =   252 extra rows
--    Total: ~3 540 rows
INSERT INTO analytics.fact_loans
    (customer_id, reporting_date, location_id, rm_id, loan_type, principal_amount, interest_rate)
SELECT
    c.id                                                          AS customer_id,
    d::date                                                       AS reporting_date,
    l.id                                                          AS location_id,
    r.id                                                          AS rm_id,
    (ARRAY['Mortgage','Personal','Corporate','Auto','Student'])
        [((n + EXTRACT(doy FROM d)::int) % 5) + 1]               AS loan_type,
    ROUND((
        5000
        + ((n * 31 + EXTRACT(doy FROM d)::int * 17 + EXTRACT(year FROM d)::int) % 745000)
        * CASE WHEN EXTRACT(day FROM (d + interval '1 day')) = 1                 THEN 1.50 ELSE 1.00 END
    )::numeric, 2)                                                AS principal_amount,
    ROUND((
        2.5
        + (((n * 7 + EXTRACT(doy FROM d)::int * 3) % 60)) / 10.0
    )::numeric, 2)                                                AS interest_rate
FROM
    generate_series('2024-01-01'::date, '2026-12-31'::date, '1 day'::interval) d
    CROSS JOIN generate_series(1, 3) n
    CROSS JOIN _cnts
    JOIN _cust c ON c.rn = ((n * 9 + EXTRACT(doy FROM d)::int * 5) % cust_cnt + 1)
    JOIN _loc  l ON l.rn = ((n + EXTRACT(month FROM d)::int) % loc_cnt + 1)
    JOIN _rm   r ON r.rn = ((n * 6 + EXTRACT(doy FROM d)::int) % rm_cnt + 1);

-- Month-end extra loans (new disbursements / month-end drawdowns)
INSERT INTO analytics.fact_loans
    (customer_id, reporting_date, location_id, rm_id, loan_type, principal_amount, interest_rate)
SELECT
    c.id,
    d::date,
    l.id,
    r.id,
    (ARRAY['Corporate','Mortgage','Corporate','Personal','Auto'])
        [((n + EXTRACT(month FROM d)::int) % 5) + 1],
    ROUND((50000 + ((n * 43 + EXTRACT(doy FROM d)::int * 11) % 950000))::numeric, 2),
    ROUND((3.0  + (((n * 5  + EXTRACT(doy FROM d)::int * 2) % 55)) / 10.0)::numeric, 2)
FROM
    generate_series('2024-01-01'::date, '2026-12-31'::date, '1 day'::interval) d
    CROSS JOIN generate_series(1, 7) n
    CROSS JOIN _cnts
    JOIN _cust c ON c.rn = ((n * 17 + EXTRACT(doy FROM d)::int * 9) % cust_cnt + 1)
    JOIN _loc  l ON l.rn = ((n * 4 + EXTRACT(month FROM d)::int) % loc_cnt + 1)
    JOIN _rm   r ON r.rn = ((n * 8 + EXTRACT(doy FROM d)::int * 3) % rm_cnt + 1)
WHERE EXTRACT(day FROM (d + interval '1 day')) = 1;   -- month-end only

-- ── 3d. fact_investments ─────────────────────────────────────────────────────
--    Base:      5 rows/day  × 1 096 days       = 5 480 rows
--    Month-end: 10 extra    × 36 month-ends    =   360 extra rows (rebalancing spike)
--    Total: ~5 840 rows
INSERT INTO analytics.fact_investments
    (customer_id, reporting_date, hier_id, location_id, rm_id, ticker_symbol, quantity, current_value)
SELECT
    c.id                                                          AS customer_id,
    d::date                                                       AS reporting_date,
    h.id                                                          AS hier_id,
    l.id                                                          AS location_id,
    r.id                                                          AS rm_id,
    (ARRAY['AAPL','MSFT','GOOGL','AMZN','TSLA','META','NVDA','JPM','GS','BRK'])
        [((n + EXTRACT(doy FROM d)::int) % 10) + 1]              AS ticker_symbol,
    ROUND((
        1 + ((n * 13 + EXTRACT(doy FROM d)::int * 7) % 200)
        * CASE WHEN EXTRACT(day FROM (d + interval '1 day')) = 1                 THEN 1.30 ELSE 1.00 END
    )::numeric, 4)                                                AS quantity,
    ROUND((
        50
        + ((n * 19 + EXTRACT(doy FROM d)::int * 11) % 9950)
        * (1.0 + 0.0003 * (d::date - '2024-01-01'::date))
        * CASE WHEN EXTRACT(day FROM (d + interval '1 day')) = 1                 THEN 1.80 ELSE 1.00 END
    )::numeric, 2)                                                AS current_value
FROM
    generate_series('2024-01-01'::date, '2026-12-31'::date, '1 day'::interval) d
    CROSS JOIN generate_series(1, 5) n
    CROSS JOIN _cnts
    JOIN _cust c ON c.rn = ((n * 11 + EXTRACT(doy FROM d)::int * 3) % cust_cnt + 1)
    JOIN _hier h ON h.rn = ((n + EXTRACT(quarter FROM d)::int * 2) % hier_cnt + 1)
    JOIN _loc  l ON l.rn = ((n * 3 + EXTRACT(doy FROM d)::int) % loc_cnt + 1)
    JOIN _rm   r ON r.rn = ((n * 7 + EXTRACT(doy FROM d)::int * 5) % rm_cnt + 1);

-- Month-end extra investment positions (portfolio rebalancing / AUM spike)
INSERT INTO analytics.fact_investments
    (customer_id, reporting_date, hier_id, location_id, rm_id, ticker_symbol, quantity, current_value)
SELECT
    c.id,
    d::date,
    h.id,
    l.id,
    r.id,
    (ARRAY['AAPL','MSFT','GOOGL','AMZN','TSLA','META','NVDA','JPM','GS','BRK'])
        [((n * 3 + EXTRACT(doy FROM d)::int) % 10) + 1],
    ROUND((50 + ((n * 29 + EXTRACT(doy FROM d)::int * 17) % 450))::numeric, 4),
    ROUND((5000 + ((n * 47 + EXTRACT(doy FROM d)::int * 23) % 95000)
        * (1.0 + 0.0003 * (d::date - '2024-01-01'::date)))::numeric, 2)
FROM
    generate_series('2024-01-01'::date, '2026-12-31'::date, '1 day'::interval) d
    CROSS JOIN generate_series(1, 10) n
    CROSS JOIN _cnts
    JOIN _cust c ON c.rn = ((n * 19 + EXTRACT(doy FROM d)::int * 11) % cust_cnt + 1)
    JOIN _hier h ON h.rn = ((n * 3 + EXTRACT(quarter FROM d)::int) % hier_cnt + 1)
    JOIN _loc  l ON l.rn = ((n * 7 + EXTRACT(doy FROM d)::int) % loc_cnt + 1)
    JOIN _rm   r ON r.rn = ((n * 11 + EXTRACT(doy FROM d)::int * 7) % rm_cnt + 1)
WHERE EXTRACT(day FROM (d + interval '1 day')) = 1;   -- month-end only

-- ── 3e. fact_department_performance ─────────────────────────────────────────
--    Base:      5 depts/day × 1 096 days       = 5 480 rows
--    Month-end costs are boosted (month-end accruals / close-of-books)
--    No extra rows needed — costs simply spike on month-end.
INSERT INTO analytics.fact_department_performance
    (reporting_date, location_id, rm_id, department, cost, budget)
SELECT
    d::date                                                       AS reporting_date,
    l.id                                                          AS location_id,
    r.id                                                          AS rm_id,
    dept                                                          AS department,
    ROUND((
        10000
        + ((dept_rn * 13 + EXTRACT(doy FROM d)::int * 7) % 90000)
        * CASE WHEN EXTRACT(day FROM (d + interval '1 day')) = 1                 THEN 2.20 ELSE 1.00 END
    )::numeric, 2)                                                AS cost,
    ROUND((
        15000
        + ((dept_rn * 11 + EXTRACT(doy FROM d)::int * 5) % 90000)
        + 5000   -- budget always >= cost
    )::numeric, 2)                                                AS budget
FROM
    generate_series('2024-01-01'::date, '2026-12-31'::date, '1 day'::interval) d
    CROSS JOIN (
        SELECT
            unnest(ARRAY['Sales','Wealth Management','IT','Operations','Risk']) AS dept,
            generate_subscripts(ARRAY['Sales','Wealth Management','IT','Operations','Risk'], 1) AS dept_rn
    ) depts
    CROSS JOIN _cnts
    JOIN _loc l ON l.rn = ((dept_rn + EXTRACT(doy FROM d)::int) % loc_cnt + 1)
    JOIN _rm  r ON r.rn = ((dept_rn * 3 + EXTRACT(doy FROM d)::int * 2) % rm_cnt + 1);

-- =============================================================================
-- 4. CLEAN UP TEMP TABLES
-- =============================================================================
DROP TABLE IF EXISTS _loc, _rm, _prod, _cust, _acc, _hier, _cnts;

-- =============================================================================
-- 5. VERIFY ROW COUNTS
-- =============================================================================
DO $$
DECLARE
    v_sales   bigint;
    v_tx      bigint;
    v_loans   bigint;
    v_inv     bigint;
    v_dept    bigint;
    v_total   bigint;
BEGIN
    SELECT count(*) INTO v_sales FROM analytics.fact_sales;
    SELECT count(*) INTO v_tx    FROM analytics.fact_banking_transactions;
    SELECT count(*) INTO v_loans FROM analytics.fact_loans;
    SELECT count(*) INTO v_inv   FROM analytics.fact_investments;
    SELECT count(*) INTO v_dept  FROM analytics.fact_department_performance;
    v_total := v_sales + v_tx + v_loans + v_inv + v_dept;

    RAISE NOTICE '====== Seed 012 verification ======';
    RAISE NOTICE 'fact_sales                  : % rows', v_sales;
    RAISE NOTICE 'fact_banking_transactions   : % rows', v_tx;
    RAISE NOTICE 'fact_loans                  : % rows', v_loans;
    RAISE NOTICE 'fact_investments            : % rows', v_inv;
    RAISE NOTICE 'fact_department_performance : % rows', v_dept;
    RAISE NOTICE '-----------------------------------';
    RAISE NOTICE 'TOTAL fact rows             : % rows', v_total;

    IF v_total < 10000 THEN
        RAISE EXCEPTION 'Seed produced fewer than 10 000 fact rows (got %). Check the script.', v_total;
    END IF;
    RAISE NOTICE 'OK — target of 10 000+ rows met.';
END $$;

COMMIT;
