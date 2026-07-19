-- -----------------------------------------------------------------------------
-- Google BigQuery OLAP Schema DDL for Analytical Datasets
-- -----------------------------------------------------------------------------

-- -----------------------------------------------------------------------------
-- dim_date
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_date (
    date_key DATE,
    year INT64,
    quarter INT64,
    month INT64,
    month_name STRING,
    day_of_week STRING,
    is_weekend BOOL
);

-- -----------------------------------------------------------------------------
-- dim_location
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_location (
    id INT64,
    country_name STRING,
    region STRING
);

-- -----------------------------------------------------------------------------
-- dim_relationship_manager
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_relationship_manager (
    id INT64,
    rm_code STRING,
    rm_name STRING
);

-- -----------------------------------------------------------------------------
-- dim_investment_hierarchy
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_investment_hierarchy (
    id INT64,
    asset_class STRING,
    product_type STRING,
    investment_strategy STRING
);

-- -----------------------------------------------------------------------------
-- dim_products
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_products (
    id INT64,
    name STRING,
    category STRING,
    unit_price NUMERIC
);

-- -----------------------------------------------------------------------------
-- dim_customers
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_customers (
    id INT64,
    customer_no INT64,
    national_id STRING,
    name STRING,
    email STRING,
    segment STRING,
    signup_date DATE,
    country_code STRING
);

-- -----------------------------------------------------------------------------
-- dim_countries
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_countries (
    id INT64,
    country_name STRING,
    iso_code STRING,
    calling_code STRING
);

-- -----------------------------------------------------------------------------
-- dim_accounts
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_accounts (
    id INT64,
    customer_id INT64,
    account_type STRING,
    open_date DATE,
    status STRING
);

-- -----------------------------------------------------------------------------
-- fact_sales
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_sales (
    id INT64,
    reporting_date DATE,
    product_id INT64,
    customer_id INT64,
    location_id INT64,
    rm_id INT64,
    quantity INT64,
    amount NUMERIC
)
PARTITION BY reporting_date;

-- -----------------------------------------------------------------------------
-- fact_banking_transactions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_banking_transactions (
    id INT64,
    reporting_date DATE,
    account_id INT64,
    location_id INT64,
    rm_id INT64,
    transaction_type STRING,
    amount NUMERIC
)
PARTITION BY reporting_date;

-- -----------------------------------------------------------------------------
-- fact_loans
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_loans (
    id INT64,
    customer_id INT64,
    reporting_date DATE,
    location_id INT64,
    rm_id INT64,
    loan_type STRING,
    principal_amount NUMERIC,
    interest_rate NUMERIC
)
PARTITION BY reporting_date;

-- -----------------------------------------------------------------------------
-- fact_investments
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_investments (
    id INT64,
    customer_id INT64,
    reporting_date DATE,
    hier_id INT64,
    location_id INT64,
    rm_id INT64,
    ticker_symbol STRING,
    quantity INT64,
    current_value NUMERIC
)
PARTITION BY reporting_date;

-- -----------------------------------------------------------------------------
-- fact_department_performance
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_department_performance (
    id INT64,
    reporting_date DATE,
    location_id INT64,
    rm_id INT64,
    department STRING,
    cost NUMERIC,
    budget NUMERIC
)
PARTITION BY reporting_date;
