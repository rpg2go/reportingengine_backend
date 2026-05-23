-- =============================================================================
-- Migration 005: Seed Semantic Data (from semantic_model_banking.yaml)
-- Schema: reporting
-- Description: Populates sem_model, sem_view, sem_explore, sem_dimension,
--              sem_measure, sem_join, and sem_derived_metric with the complete
--              banking data warehouse semantic layer.
--              Source: report_template/sample/semantic_model_banking.yaml
--
-- Date: 2026-04-05
-- Depends on: 001_create_semantic_tables.sql
-- =============================================================================

-- =============================================================================
-- 1. MODEL
-- =============================================================================
INSERT INTO reporting.sem_model (name, label, description, version, is_active)
VALUES (
    'banking_analytics',
    'Banking & Financial Analytics',
    'Unified semantic model over a star-schema banking data warehouse. Covers Sales, Banking Transactions, Loans, Investments, and Department Performance — all anchored to a shared reporting_date via dim_date.',
    1,
    TRUE
);

-- =============================================================================
-- 2. VIEWS (dimensions first, then facts)
--    table_ref uses the analytics.* schema prefix matching the live DB.
-- =============================================================================

-- ---- DIMENSIONS ----
INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'dim_date', 'Date', 'analytics.dim_date', 'dimension', 'date_key', NULL,
       'Standard calendar dimension for time-series analysis (YTD, MTD, WTD).'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'dim_customers', 'Customers', 'analytics.dim_customers', 'dimension', 'id', NULL,
       'Profile and demographic data for all clients.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'dim_products', 'Products', 'analytics.dim_products', 'dimension', 'id', NULL,
       'Product catalog including hierarchies and pricing.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'dim_location', 'Location', 'analytics.dim_location', 'dimension', 'id', NULL,
       'Standardized geographic dimension covering 36 strategic global markets.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'dim_countries', 'Countries', 'analytics.dim_countries', 'dimension', 'id', NULL,
       'Reference table for all global countries including ISO codes.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'dim_relationship_manager', 'Relationship Manager', 'analytics.dim_relationship_manager', 'dimension', 'id', NULL,
       'Attribution table for employees managing customer relationships.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'dim_investment_hierarchy', 'Investment Hierarchy', 'analytics.dim_investment_hierarchy', 'dimension', 'id', NULL,
       'Hierarchical categorization for investment products and strategies.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'dim_accounts', 'Accounts', 'analytics.dim_accounts', 'dimension', 'id', NULL,
       'Analytical attributes for banking accounts.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

-- ---- FACTS ----
INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'fact_sales', 'Sales', 'analytics.fact_sales', 'fact', 'id', 'reporting_date',
       'Sales transactions. Key metrics: revenue, quantity sold, average order value.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'fact_banking_transactions', 'Banking Transactions', 'analytics.fact_banking_transactions', 'fact', 'id', 'reporting_date',
       'All banking transactions. Key metrics: transaction volume, net flow, credits vs debits.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'fact_loans', 'Loans', 'analytics.fact_loans', 'fact', 'id', 'reporting_date',
       'Loan portfolio data. Key metrics: outstanding principal, interest income, loan count.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'fact_investments', 'Investments', 'analytics.fact_investments', 'fact', 'id', 'reporting_date',
       'Investment portfolio holdings. Key metrics: AUM, portfolio count, average position value.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_view (model_id, name, label, table_ref, view_type, primary_key, time_key, description)
SELECT model_id, 'fact_department_performance', 'Department Performance', 'analytics.fact_department_performance', 'fact', 'id', 'reporting_date',
       'Department-level cost vs budget tracking. Key metrics: actual cost, budget, variance.'
FROM reporting.sem_model WHERE name = 'banking_analytics';


-- =============================================================================
-- 3. EXPLORES (one per fact table)
-- =============================================================================
INSERT INTO reporting.sem_explore (model_id, fact_view_id, name, label)
SELECT m.model_id, v.view_id, 'explore_sales', 'Sales Explorer'
FROM reporting.sem_model m JOIN reporting.sem_view v ON v.model_id = m.model_id
WHERE m.name = 'banking_analytics' AND v.name = 'fact_sales';

INSERT INTO reporting.sem_explore (model_id, fact_view_id, name, label)
SELECT m.model_id, v.view_id, 'explore_banking_transactions', 'Banking Transactions Explorer'
FROM reporting.sem_model m JOIN reporting.sem_view v ON v.model_id = m.model_id
WHERE m.name = 'banking_analytics' AND v.name = 'fact_banking_transactions';

INSERT INTO reporting.sem_explore (model_id, fact_view_id, name, label)
SELECT m.model_id, v.view_id, 'explore_loans', 'Loans Explorer'
FROM reporting.sem_model m JOIN reporting.sem_view v ON v.model_id = m.model_id
WHERE m.name = 'banking_analytics' AND v.name = 'fact_loans';

INSERT INTO reporting.sem_explore (model_id, fact_view_id, name, label)
SELECT m.model_id, v.view_id, 'explore_investments', 'Investments Explorer'
FROM reporting.sem_model m JOIN reporting.sem_view v ON v.model_id = m.model_id
WHERE m.name = 'banking_analytics' AND v.name = 'fact_investments';

INSERT INTO reporting.sem_explore (model_id, fact_view_id, name, label)
SELECT m.model_id, v.view_id, 'explore_department_performance', 'Department Performance Explorer'
FROM reporting.sem_model m JOIN reporting.sem_view v ON v.model_id = m.model_id
WHERE m.name = 'banking_analytics' AND v.name = 'fact_department_performance';


-- =============================================================================
-- 4. DIMENSIONS (attributes on each view)
-- =============================================================================

-- dim_date attributes
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type, description)
SELECT view_id, 'year',         'Year',         'year',         'integer', 'Calendar year'              FROM reporting.sem_view WHERE name = 'dim_date';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type, description)
SELECT view_id, 'quarter',      'Quarter',      'quarter',      'integer', 'Calendar quarter (1-4)'     FROM reporting.sem_view WHERE name = 'dim_date';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type, description)
SELECT view_id, 'month',        'Month Number', 'month',        'integer', 'Calendar month number'      FROM reporting.sem_view WHERE name = 'dim_date';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type, description)
SELECT view_id, 'month_name',   'Month Name',   'month_name',   'string',  'Calendar month name'        FROM reporting.sem_view WHERE name = 'dim_date';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type, description)
SELECT view_id, 'week_of_year', 'Week of Year', 'week_of_year', 'integer', 'ISO week number'            FROM reporting.sem_view WHERE name = 'dim_date';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type, description)
SELECT view_id, 'day_of_week',  'Day of Week',  'day_of_week',  'integer', 'Day of week (1=Mon-7=Sun)' FROM reporting.sem_view WHERE name = 'dim_date';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type, description)
SELECT view_id, 'is_weekend',   'Is Weekend',   'is_weekend',   'boolean', 'True if Saturday or Sunday' FROM reporting.sem_view WHERE name = 'dim_date';

-- dim_customers attributes
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'customer_no',  'Customer Number', 'customer_no',  'integer' FROM reporting.sem_view WHERE name = 'dim_customers';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'name',         'Customer Name',   'name',         'string'  FROM reporting.sem_view WHERE name = 'dim_customers';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'email',        'Email',           'email',        'string'  FROM reporting.sem_view WHERE name = 'dim_customers';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'signup_date',  'Signup Date',     'signup_date',  'date'    FROM reporting.sem_view WHERE name = 'dim_customers';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'segment',      'Customer Segment','segment',      'string'  FROM reporting.sem_view WHERE name = 'dim_customers';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'country_code', 'Country Code',    'country_code', 'string'  FROM reporting.sem_view WHERE name = 'dim_customers';

-- dim_products attributes
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'name',       'Product Name',     'name',       'string'  FROM reporting.sem_view WHERE name = 'dim_products';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'category',   'Product Category', 'category',   'string'  FROM reporting.sem_view WHERE name = 'dim_products';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'unit_price', 'Unit Price',       'unit_price', 'numeric' FROM reporting.sem_view WHERE name = 'dim_products';

-- dim_location attributes
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'country_name', 'Country', 'country_name', 'string' FROM reporting.sem_view WHERE name = 'dim_location';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'region',       'Region',  'region',       'string' FROM reporting.sem_view WHERE name = 'dim_location';

-- dim_countries attributes
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'country_name', 'Country Name', 'country_name', 'string' FROM reporting.sem_view WHERE name = 'dim_countries';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'iso_code',     'ISO Code',     'iso_code',     'string' FROM reporting.sem_view WHERE name = 'dim_countries';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'calling_code', 'Calling Code', 'calling_code', 'string' FROM reporting.sem_view WHERE name = 'dim_countries';

-- dim_relationship_manager attributes
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'rm_code', 'RM Code', 'rm_code', 'string' FROM reporting.sem_view WHERE name = 'dim_relationship_manager';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'rm_name', 'RM Name', 'rm_name', 'string' FROM reporting.sem_view WHERE name = 'dim_relationship_manager';

-- dim_investment_hierarchy attributes
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'asset_class',          'Asset Class',         'asset_class',          'string' FROM reporting.sem_view WHERE name = 'dim_investment_hierarchy';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'product_type',         'Product Type',        'product_type',         'string' FROM reporting.sem_view WHERE name = 'dim_investment_hierarchy';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'investment_strategy',  'Investment Strategy', 'investment_strategy',  'string' FROM reporting.sem_view WHERE name = 'dim_investment_hierarchy';

-- dim_accounts attributes
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'account_type', 'Account Type',   'account_type', 'string' FROM reporting.sem_view WHERE name = 'dim_accounts';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'open_date',    'Open Date',       'open_date',    'date'   FROM reporting.sem_view WHERE name = 'dim_accounts';
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type)
SELECT view_id, 'status',       'Account Status',  'status',       'string' FROM reporting.sem_view WHERE name = 'dim_accounts';


-- =============================================================================
-- 5. MEASURES (metrics per fact view)
-- =============================================================================

-- ---- fact_sales measures ----
INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'total_revenue',     'Total Revenue',        'SUM(analytics.fact_sales.amount)',                                                                                   'SUM',     'currency', 'Sum of all sales amounts'
FROM reporting.sem_view WHERE name = 'fact_sales';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'total_quantity_sold','Total Quantity Sold',  'SUM(analytics.fact_sales.quantity)',                                                                                  'SUM',     'integer',  'Sum of all units sold'
FROM reporting.sem_view WHERE name = 'fact_sales';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'transaction_count', 'Transaction Count',    'COUNT(DISTINCT analytics.fact_sales.id)',                                                                             'COUNT',   'integer',  'Number of distinct sales transactions'
FROM reporting.sem_view WHERE name = 'fact_sales';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'avg_order_value',   'Average Order Value',  'AVG(analytics.fact_sales.amount)',                                                                                    'AVG',     'currency', 'Average revenue per sales transaction'
FROM reporting.sem_view WHERE name = 'fact_sales';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'avg_unit_price',    'Average Unit Price',   'CASE WHEN SUM(analytics.fact_sales.quantity) = 0 THEN NULL ELSE SUM(analytics.fact_sales.amount) / NULLIF(SUM(analytics.fact_sales.quantity), 0) END', 'FORMULA',  'currency', 'Revenue / quantity; null-safe'
FROM reporting.sem_view WHERE name = 'fact_sales';

-- ---- fact_banking_transactions measures ----
INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'total_transaction_amount', 'Total Transaction Amount', 'SUM(analytics.fact_banking_transactions.amount)',                                                                                                                        'SUM',   'currency', 'Sum of all transaction amounts'
FROM reporting.sem_view WHERE name = 'fact_banking_transactions';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'transaction_count',        'Transaction Count',        'COUNT(DISTINCT analytics.fact_banking_transactions.id)',                                                                                                                  'COUNT', 'integer',  'Number of distinct banking transactions'
FROM reporting.sem_view WHERE name = 'fact_banking_transactions';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'total_credits',            'Total Credits',            'SUM(CASE WHEN analytics.fact_banking_transactions.transaction_type = ''credit'' THEN analytics.fact_banking_transactions.amount ELSE 0 END)',                            'SUM',   'currency', 'Sum of inbound/credit transactions'
FROM reporting.sem_view WHERE name = 'fact_banking_transactions';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'total_debits',             'Total Debits',             'SUM(CASE WHEN analytics.fact_banking_transactions.transaction_type = ''debit'' THEN analytics.fact_banking_transactions.amount ELSE 0 END)',                             'SUM',   'currency', 'Sum of outbound/debit transactions'
FROM reporting.sem_view WHERE name = 'fact_banking_transactions';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'net_cash_flow',            'Net Cash Flow',            'SUM(CASE WHEN analytics.fact_banking_transactions.transaction_type = ''credit'' THEN analytics.fact_banking_transactions.amount ELSE -analytics.fact_banking_transactions.amount END)', 'SUM', 'currency', 'Credits minus debits'
FROM reporting.sem_view WHERE name = 'fact_banking_transactions';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'avg_transaction_amount',   'Average Transaction Amount','AVG(analytics.fact_banking_transactions.amount)',                                                                                                                        'AVG',   'currency', 'Average value per banking transaction'
FROM reporting.sem_view WHERE name = 'fact_banking_transactions';

-- ---- fact_loans measures ----
INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'total_principal',           'Total Principal Amount',   'SUM(analytics.fact_loans.principal_amount)',                                                              'SUM',   'currency', 'Sum of all outstanding loan principal amounts'
FROM reporting.sem_view WHERE name = 'fact_loans';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'loan_count',                'Loan Count',               'COUNT(DISTINCT analytics.fact_loans.id)',                                                                 'COUNT', 'integer',  'Number of distinct loans'
FROM reporting.sem_view WHERE name = 'fact_loans';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'avg_interest_rate',         'Average Interest Rate',    'AVG(analytics.fact_loans.interest_rate)',                                                                 'AVG',   'percent',  'Weighted average interest rate across the loan portfolio'
FROM reporting.sem_view WHERE name = 'fact_loans';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'avg_loan_size',             'Average Loan Size',        'AVG(analytics.fact_loans.principal_amount)',                                                              'AVG',   'currency', 'Average principal amount per loan'
FROM reporting.sem_view WHERE name = 'fact_loans';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'estimated_interest_income', 'Estimated Interest Income','SUM(analytics.fact_loans.principal_amount * analytics.fact_loans.interest_rate)',                        'SUM',   'currency', 'Approximate interest income: principal × interest_rate'
FROM reporting.sem_view WHERE name = 'fact_loans';

-- ---- fact_investments measures ----
INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'total_aum',          'Total AUM',               'SUM(analytics.fact_investments.current_value)',        'SUM',   'currency', 'Assets Under Management: sum of current market value'
FROM reporting.sem_view WHERE name = 'fact_investments';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'total_quantity',     'Total Quantity',          'SUM(analytics.fact_investments.quantity)',              'SUM',   'numeric',  'Total number of investment units/shares held'
FROM reporting.sem_view WHERE name = 'fact_investments';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'investment_count',   'Investment Count',        'COUNT(DISTINCT analytics.fact_investments.id)',         'COUNT', 'integer',  'Number of distinct investment positions'
FROM reporting.sem_view WHERE name = 'fact_investments';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'avg_position_value', 'Average Position Value',  'AVG(analytics.fact_investments.current_value)',         'AVG',   'currency', 'Average current value per investment position'
FROM reporting.sem_view WHERE name = 'fact_investments';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'unique_tickers',     'Unique Tickers',          'COUNT(DISTINCT analytics.fact_investments.ticker_symbol)', 'COUNT', 'integer', 'Number of distinct securities held'
FROM reporting.sem_view WHERE name = 'fact_investments';

-- ---- fact_department_performance measures ----
INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'total_cost',               'Total Cost',              'SUM(analytics.fact_department_performance.cost)',                                                                                                                                    'SUM',     'currency', 'Sum of all department costs'
FROM reporting.sem_view WHERE name = 'fact_department_performance';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'total_budget',             'Total Budget',            'SUM(analytics.fact_department_performance.budget)',                                                                                                                                  'SUM',     'currency', 'Sum of all department budgets'
FROM reporting.sem_view WHERE name = 'fact_department_performance';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'budget_variance',          'Budget Variance',         'SUM(analytics.fact_department_performance.budget - analytics.fact_department_performance.cost)',                                                                                     'SUM',     'currency', 'Budget minus actual cost (positive = under budget)'
FROM reporting.sem_view WHERE name = 'fact_department_performance';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'budget_utilization_rate',  'Budget Utilization Rate', 'CASE WHEN SUM(analytics.fact_department_performance.budget) = 0 THEN NULL ELSE SUM(analytics.fact_department_performance.cost) / NULLIF(SUM(analytics.fact_department_performance.budget), 0) END', 'FORMULA', 'percent',  'Actual cost as a percentage of budget'
FROM reporting.sem_view WHERE name = 'fact_department_performance';

INSERT INTO reporting.sem_measure (view_id, name, label, sql_expr, agg_type, data_type, description)
SELECT view_id, 'department_count',         'Department Count',        'COUNT(DISTINCT analytics.fact_department_performance.department)',                                                                                                                    'COUNT',   'integer',  'Number of distinct departments reported'
FROM reporting.sem_view WHERE name = 'fact_department_performance';


-- =============================================================================
-- 6. JOINS
--    Links each explore's fact view to its applicable dimension views.
-- =============================================================================

-- ---- explore_sales joins ----
INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_sales.reporting_date = analytics.dim_date.date_key', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_sales'
JOIN reporting.sem_view dv ON dv.name = 'dim_date'
WHERE e.name = 'explore_sales';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_sales.product_id = analytics.dim_products.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_sales'
JOIN reporting.sem_view dv ON dv.name = 'dim_products'
WHERE e.name = 'explore_sales';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_sales.customer_id = analytics.dim_customers.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_sales'
JOIN reporting.sem_view dv ON dv.name = 'dim_customers'
WHERE e.name = 'explore_sales';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_sales.location_id = analytics.dim_location.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_sales'
JOIN reporting.sem_view dv ON dv.name = 'dim_location'
WHERE e.name = 'explore_sales';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_sales.rm_id = analytics.dim_relationship_manager.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_sales'
JOIN reporting.sem_view dv ON dv.name = 'dim_relationship_manager'
WHERE e.name = 'explore_sales';

-- ---- explore_banking_transactions joins ----
INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_banking_transactions.reporting_date = analytics.dim_date.date_key', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_banking_transactions'
JOIN reporting.sem_view dv ON dv.name = 'dim_date'
WHERE e.name = 'explore_banking_transactions';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_banking_transactions.account_id = analytics.dim_accounts.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_banking_transactions'
JOIN reporting.sem_view dv ON dv.name = 'dim_accounts'
WHERE e.name = 'explore_banking_transactions';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_banking_transactions.location_id = analytics.dim_location.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_banking_transactions'
JOIN reporting.sem_view dv ON dv.name = 'dim_location'
WHERE e.name = 'explore_banking_transactions';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_banking_transactions.rm_id = analytics.dim_relationship_manager.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_banking_transactions'
JOIN reporting.sem_view dv ON dv.name = 'dim_relationship_manager'
WHERE e.name = 'explore_banking_transactions';

-- ---- explore_loans joins ----
INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_loans.reporting_date = analytics.dim_date.date_key', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_loans'
JOIN reporting.sem_view dv ON dv.name = 'dim_date'
WHERE e.name = 'explore_loans';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_loans.customer_id = analytics.dim_customers.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_loans'
JOIN reporting.sem_view dv ON dv.name = 'dim_customers'
WHERE e.name = 'explore_loans';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_loans.location_id = analytics.dim_location.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_loans'
JOIN reporting.sem_view dv ON dv.name = 'dim_location'
WHERE e.name = 'explore_loans';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_loans.rm_id = analytics.dim_relationship_manager.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_loans'
JOIN reporting.sem_view dv ON dv.name = 'dim_relationship_manager'
WHERE e.name = 'explore_loans';

-- ---- explore_investments joins ----
INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_investments.reporting_date = analytics.dim_date.date_key', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_investments'
JOIN reporting.sem_view dv ON dv.name = 'dim_date'
WHERE e.name = 'explore_investments';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_investments.customer_id = analytics.dim_customers.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_investments'
JOIN reporting.sem_view dv ON dv.name = 'dim_customers'
WHERE e.name = 'explore_investments';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_investments.hier_id = analytics.dim_investment_hierarchy.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_investments'
JOIN reporting.sem_view dv ON dv.name = 'dim_investment_hierarchy'
WHERE e.name = 'explore_investments';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_investments.location_id = analytics.dim_location.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_investments'
JOIN reporting.sem_view dv ON dv.name = 'dim_location'
WHERE e.name = 'explore_investments';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_investments.rm_id = analytics.dim_relationship_manager.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_investments'
JOIN reporting.sem_view dv ON dv.name = 'dim_relationship_manager'
WHERE e.name = 'explore_investments';

-- ---- explore_department_performance joins ----
INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_department_performance.reporting_date = analytics.dim_date.date_key', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_department_performance'
JOIN reporting.sem_view dv ON dv.name = 'dim_date'
WHERE e.name = 'explore_department_performance';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_department_performance.location_id = analytics.dim_location.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_department_performance'
JOIN reporting.sem_view dv ON dv.name = 'dim_location'
WHERE e.name = 'explore_department_performance';

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_sql, join_type)
SELECT e.explore_id, fv.view_id, dv.view_id,
       'analytics.fact_department_performance.rm_id = analytics.dim_relationship_manager.id', 'LEFT'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_department_performance'
JOIN reporting.sem_view dv ON dv.name = 'dim_relationship_manager'
WHERE e.name = 'explore_department_performance';


-- =============================================================================
-- 7. DERIVED METRICS (post-processor / cross-fact formulas)
-- =============================================================================
INSERT INTO reporting.sem_derived_metric (model_id, name, label, formula_expr, depends_on, description)
SELECT model_id,
       'revenue_per_transaction',
       'Revenue per Transaction',
       'total_revenue / transaction_count',
       '["total_revenue", "transaction_count"]'::jsonb,
       'Average revenue generated per banking transaction.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_derived_metric (model_id, name, label, formula_expr, depends_on, description)
SELECT model_id,
       'loan_to_aum_ratio',
       'Loan to AUM Ratio',
       'total_principal / total_aum',
       '["total_principal", "total_aum"]'::jsonb,
       'Total loan principal relative to total assets under management.'
FROM reporting.sem_model WHERE name = 'banking_analytics';

INSERT INTO reporting.sem_derived_metric (model_id, name, label, formula_expr, depends_on, description)
SELECT model_id,
       'cost_income_ratio',
       'Cost-Income Ratio',
       'total_cost / total_revenue',
       '["total_cost", "total_revenue"]'::jsonb,
       'Total department cost relative to total revenue (efficiency metric).'
FROM reporting.sem_model WHERE name = 'banking_analytics';


-- =============================================================================
-- 8. STANDARD STYLES (shared across all reports)
-- =============================================================================
INSERT INTO reporting.rpt_style (name, font_size, is_bold, border_top, border_bottom, alignment, color_hex, bg_color_hex) VALUES
    ('header',  12, TRUE,  FALSE, TRUE,  'left',   '#FFFFFF', '#1B4F72'),
    ('section', 11, TRUE,  TRUE,  FALSE, 'left',   '#1B4F72', '#D6EAF8'),
    ('total',   11, TRUE,  TRUE,  TRUE,  'left',   '#1B4F72', '#EBF5FB'),
    ('normal',  10, FALSE, FALSE, FALSE, 'left',   '#2C3E50', NULL),
    ('blank',   10, FALSE, FALSE, FALSE, 'left',   NULL,      NULL);


-- =============================================================================
-- 9. VERIFICATION QUERIES (run manually to confirm seed data is correct)
-- =============================================================================
-- SELECT count(*) FROM reporting.sem_view;           -- expected: 13 (8 dims + 5 facts)
-- SELECT count(*) FROM reporting.sem_measure;        -- expected: 26
-- SELECT count(*) FROM reporting.sem_join;           -- expected: 21
-- SELECT count(*) FROM reporting.sem_derived_metric; -- expected: 3
-- SELECT count(*) FROM reporting.rpt_style;          -- expected: 5
