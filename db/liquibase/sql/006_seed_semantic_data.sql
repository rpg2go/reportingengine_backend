--liquibase formatted sql
--changeset devops:006_seed_semantic_data endDelimiter:; runOnChange:true

-- =============================================================================
-- sem_view — one row per logical view (fact + dimension tables)
-- =============================================================================
INSERT INTO reporting.sem_view (name, label, table_ref, view_type, primary_key, time_key, description) VALUES
    ('fact_sales',                  'Sales',                        'analytics.fact_sales',                 'fact',      'id',  'reporting_date', 'Daily sales transactions'),
    ('fact_loans',                  'Loans',                        'analytics.fact_loans',                 'fact',      'id',  'reporting_date', 'Loan portfolio records'),
    ('fact_investments',            'Investments',                   'analytics.fact_investments',           'fact',      'id',  'reporting_date', 'Investment portfolio positions'),
    ('fact_banking_transactions',   'Banking Transactions',          'analytics.fact_banking_transactions',  'fact',      'id',  'reporting_date', 'Banking transactions'),
    ('fact_department_performance', 'Department Performance',        'analytics.fact_department_performance','fact',      'id',  'reporting_date', 'Department cost vs. budget'),
    ('dim_date',                    'Date',                         'analytics.dim_date',                   'dimension', 'date_key', 'date_key', 'Calendar dimension'),
    ('dim_customers',               'Customers',                    'analytics.dim_customers',              'dimension', 'id',  NULL,             'Customer master data'),
    ('dim_accounts',                'Accounts',                     'analytics.dim_accounts',               'dimension', 'id',  NULL,             'Account master data'),
    ('dim_location',                'Location',                     'analytics.dim_location',               'dimension', 'id',  NULL,             'Country / region dimension'),
    ('dim_relationship_manager',    'Relationship Manager',          'analytics.dim_relationship_manager',   'dimension', 'id',  NULL,             'RM master data'),
    ('dim_investment_hierarchy',    'Investment Hierarchy',          'analytics.dim_investment_hierarchy',   'dimension', 'id',  NULL,             'Asset class / product type hierarchy'),
    ('dim_products',                'Products',                     'analytics.dim_products',               'dimension', 'id',  NULL,             'Product catalogue'),
    ('dim_countries',               'Countries',                    'analytics.dim_countries',              'dimension', 'id',  NULL,             'ISO country codes')
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- sem_explore — one explore per fact table
-- =============================================================================
INSERT INTO reporting.sem_explore (name, label, fact_view_id)
SELECT 'explore_sales', 'Sales Explore', view_id FROM reporting.sem_view WHERE name = 'fact_sales'
ON CONFLICT (name) DO NOTHING;

INSERT INTO reporting.sem_explore (name, label, fact_view_id)
SELECT 'explore_loans', 'Loans Explore', view_id FROM reporting.sem_view WHERE name = 'fact_loans'
ON CONFLICT (name) DO NOTHING;

INSERT INTO reporting.sem_explore (name, label, fact_view_id)
SELECT 'explore_investments', 'Investments Explore', view_id FROM reporting.sem_view WHERE name = 'fact_investments'
ON CONFLICT (name) DO NOTHING;

INSERT INTO reporting.sem_explore (name, label, fact_view_id)
SELECT 'explore_banking_transactions', 'Banking Transactions Explore', view_id FROM reporting.sem_view WHERE name = 'fact_banking_transactions'
ON CONFLICT (name) DO NOTHING;

INSERT INTO reporting.sem_explore (name, label, fact_view_id)
SELECT 'explore_department_performance', 'Department Performance Explore', view_id FROM reporting.sem_view WHERE name = 'fact_department_performance'
ON CONFLICT (name) DO NOTHING;

-- =============================================================================
-- sem_join — dimension joins per explore
-- =============================================================================
-- explore_sales joins
INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_date ON analytics.dim_date.date_key = analytics.fact_sales.reporting_date'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_sales'
JOIN reporting.sem_view tv ON tv.name = 'dim_date'
WHERE e.name = 'explore_sales'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_customers ON analytics.dim_customers.id = analytics.fact_sales.customer_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_sales'
JOIN reporting.sem_view tv ON tv.name = 'dim_customers'
WHERE e.name = 'explore_sales'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_products ON analytics.dim_products.id = analytics.fact_sales.product_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_sales'
JOIN reporting.sem_view tv ON tv.name = 'dim_products'
WHERE e.name = 'explore_sales'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_location ON analytics.dim_location.id = analytics.fact_sales.location_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_sales'
JOIN reporting.sem_view tv ON tv.name = 'dim_location'
WHERE e.name = 'explore_sales'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_relationship_manager ON analytics.dim_relationship_manager.id = analytics.fact_sales.rm_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_sales'
JOIN reporting.sem_view tv ON tv.name = 'dim_relationship_manager'
WHERE e.name = 'explore_sales'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

-- explore_loans joins
INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_date ON analytics.dim_date.date_key = analytics.fact_loans.reporting_date'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_loans'
JOIN reporting.sem_view tv ON tv.name = 'dim_date'
WHERE e.name = 'explore_loans'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_customers ON analytics.dim_customers.id = analytics.fact_loans.customer_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_loans'
JOIN reporting.sem_view tv ON tv.name = 'dim_customers'
WHERE e.name = 'explore_loans'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_location ON analytics.dim_location.id = analytics.fact_loans.location_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_loans'
JOIN reporting.sem_view tv ON tv.name = 'dim_location'
WHERE e.name = 'explore_loans'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_relationship_manager ON analytics.dim_relationship_manager.id = analytics.fact_loans.rm_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_loans'
JOIN reporting.sem_view tv ON tv.name = 'dim_relationship_manager'
WHERE e.name = 'explore_loans'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

-- explore_investments joins
INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_date ON analytics.dim_date.date_key = analytics.fact_investments.reporting_date'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_investments'
JOIN reporting.sem_view tv ON tv.name = 'dim_date'
WHERE e.name = 'explore_investments'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_customers ON analytics.dim_customers.id = analytics.fact_investments.customer_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_investments'
JOIN reporting.sem_view tv ON tv.name = 'dim_customers'
WHERE e.name = 'explore_investments'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_investment_hierarchy ON analytics.dim_investment_hierarchy.id = analytics.fact_investments.hier_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_investments'
JOIN reporting.sem_view tv ON tv.name = 'dim_investment_hierarchy'
WHERE e.name = 'explore_investments'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_location ON analytics.dim_location.id = analytics.fact_investments.location_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_investments'
JOIN reporting.sem_view tv ON tv.name = 'dim_location'
WHERE e.name = 'explore_investments'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_relationship_manager ON analytics.dim_relationship_manager.id = analytics.fact_investments.rm_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_investments'
JOIN reporting.sem_view tv ON tv.name = 'dim_relationship_manager'
WHERE e.name = 'explore_investments'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

-- explore_banking_transactions joins
INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_date ON analytics.dim_date.date_key = analytics.fact_banking_transactions.reporting_date'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_banking_transactions'
JOIN reporting.sem_view tv ON tv.name = 'dim_date'
WHERE e.name = 'explore_banking_transactions'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_accounts ON analytics.dim_accounts.id = analytics.fact_banking_transactions.account_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_banking_transactions'
JOIN reporting.sem_view tv ON tv.name = 'dim_accounts'
WHERE e.name = 'explore_banking_transactions'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_location ON analytics.dim_location.id = analytics.fact_banking_transactions.location_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_banking_transactions'
JOIN reporting.sem_view tv ON tv.name = 'dim_location'
WHERE e.name = 'explore_banking_transactions'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_relationship_manager ON analytics.dim_relationship_manager.id = analytics.fact_banking_transactions.rm_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_banking_transactions'
JOIN reporting.sem_view tv ON tv.name = 'dim_relationship_manager'
WHERE e.name = 'explore_banking_transactions'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

-- explore_department_performance joins
INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_date ON analytics.dim_date.date_key = analytics.fact_department_performance.reporting_date'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_department_performance'
JOIN reporting.sem_view tv ON tv.name = 'dim_date'
WHERE e.name = 'explore_department_performance'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_location ON analytics.dim_location.id = analytics.fact_department_performance.location_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_department_performance'
JOIN reporting.sem_view tv ON tv.name = 'dim_location'
WHERE e.name = 'explore_department_performance'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

INSERT INTO reporting.sem_join (explore_id, from_view_id, to_view_id, join_type, join_sql)
SELECT e.explore_id, fv.view_id, tv.view_id, 'LEFT',
       'analytics.dim_relationship_manager ON analytics.dim_relationship_manager.id = analytics.fact_department_performance.rm_id'
FROM reporting.sem_explore e
JOIN reporting.sem_view fv ON fv.name = 'fact_department_performance'
JOIN reporting.sem_view tv ON tv.name = 'dim_relationship_manager'
WHERE e.name = 'explore_department_performance'
ON CONFLICT (explore_id, from_view_id, to_view_id) DO NOTHING;

-- =============================================================================
-- sem_dimension — key filterable columns per view
-- =============================================================================
-- dim_date
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'date_key', 'Date', 'analytics.dim_date.date_key', 'date' FROM reporting.sem_view WHERE name = 'dim_date' ON CONFLICT (view_id, name) DO NOTHING;
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'year', 'Year', 'analytics.dim_date.year', 'integer' FROM reporting.sem_view WHERE name = 'dim_date' ON CONFLICT (view_id, name) DO NOTHING;
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'quarter', 'Quarter', 'analytics.dim_date.quarter', 'integer' FROM reporting.sem_view WHERE name = 'dim_date' ON CONFLICT (view_id, name) DO NOTHING;
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'month', 'Month', 'analytics.dim_date.month', 'integer' FROM reporting.sem_view WHERE name = 'dim_date' ON CONFLICT (view_id, name) DO NOTHING;
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'month_name', 'Month Name', 'analytics.dim_date.month_name', 'varchar' FROM reporting.sem_view WHERE name = 'dim_date' ON CONFLICT (view_id, name) DO NOTHING;
-- dim_customers
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'segment', 'Customer Segment', 'analytics.dim_customers.segment', 'varchar' FROM reporting.sem_view WHERE name = 'dim_customers' ON CONFLICT (view_id, name) DO NOTHING;
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'country_code', 'Country Code', 'analytics.dim_customers.country_code', 'varchar' FROM reporting.sem_view WHERE name = 'dim_customers' ON CONFLICT (view_id, name) DO NOTHING;
-- dim_location
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'country_name', 'Country', 'analytics.dim_location.country_name', 'varchar' FROM reporting.sem_view WHERE name = 'dim_location' ON CONFLICT (view_id, name) DO NOTHING;
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'region', 'Region', 'analytics.dim_location.region', 'varchar' FROM reporting.sem_view WHERE name = 'dim_location' ON CONFLICT (view_id, name) DO NOTHING;
-- dim_accounts
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'account_type', 'Account Type', 'analytics.dim_accounts.account_type', 'varchar' FROM reporting.sem_view WHERE name = 'dim_accounts' ON CONFLICT (view_id, name) DO NOTHING;
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'status', 'Account Status', 'analytics.dim_accounts.status', 'varchar' FROM reporting.sem_view WHERE name = 'dim_accounts' ON CONFLICT (view_id, name) DO NOTHING;
-- dim_investment_hierarchy
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'asset_class', 'Asset Class', 'analytics.dim_investment_hierarchy.asset_class', 'varchar' FROM reporting.sem_view WHERE name = 'dim_investment_hierarchy' ON CONFLICT (view_id, name) DO NOTHING;
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'product_type', 'Product Type', 'analytics.dim_investment_hierarchy.product_type', 'varchar' FROM reporting.sem_view WHERE name = 'dim_investment_hierarchy' ON CONFLICT (view_id, name) DO NOTHING;
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'investment_strategy', 'Investment Strategy', 'analytics.dim_investment_hierarchy.investment_strategy', 'varchar' FROM reporting.sem_view WHERE name = 'dim_investment_hierarchy' ON CONFLICT (view_id, name) DO NOTHING;
-- dim_products
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'category', 'Category', 'analytics.dim_products.category', 'varchar' FROM reporting.sem_view WHERE name = 'dim_products' ON CONFLICT (view_id, name) DO NOTHING;
-- fact_banking_transactions
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'transaction_type', 'Transaction Type', 'analytics.fact_banking_transactions.transaction_type', 'varchar' FROM reporting.sem_view WHERE name = 'fact_banking_transactions' ON CONFLICT (view_id, name) DO NOTHING;
-- fact_loans
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'loan_type', 'Loan Type', 'analytics.fact_loans.loan_type', 'varchar' FROM reporting.sem_view WHERE name = 'fact_loans' ON CONFLICT (view_id, name) DO NOTHING;
-- fact_department_performance
INSERT INTO reporting.sem_dimension (view_id, name, label, column_ref, data_type) SELECT view_id, 'department', 'Department', 'analytics.fact_department_performance.department', 'varchar' FROM reporting.sem_view WHERE name = 'fact_department_performance' ON CONFLICT (view_id, name) DO NOTHING;


