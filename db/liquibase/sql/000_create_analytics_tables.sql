--liquibase formatted sql
--changeset devops:000_create_analytics_tables endDelimiter:;

CREATE SCHEMA IF NOT EXISTS analytics;

-- -----------------------------------------------------------------------------
-- dim_date
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_date (
    date_key DATE PRIMARY KEY,
    year INTEGER,
    quarter INTEGER,
    month INTEGER,
    month_name VARCHAR(20),
    day_of_week VARCHAR(20),
    is_weekend BOOLEAN
);

-- -----------------------------------------------------------------------------
-- dim_location
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_location (
    id SERIAL PRIMARY KEY,
    country_name VARCHAR(100),
    region VARCHAR(50)
);

-- -----------------------------------------------------------------------------
-- dim_relationship_manager
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_relationship_manager (
    id SERIAL PRIMARY KEY,
    rm_code VARCHAR(20) UNIQUE,
    rm_name VARCHAR(255)
);

-- -----------------------------------------------------------------------------
-- dim_investment_hierarchy
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_investment_hierarchy (
    id SERIAL PRIMARY KEY,
    asset_class VARCHAR(100),
    product_type VARCHAR(100),
    investment_strategy VARCHAR(100)
);

-- -----------------------------------------------------------------------------
-- dim_products
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    category VARCHAR(100),
    unit_price DECIMAL(10, 2)
);

-- -----------------------------------------------------------------------------
-- dim_customers
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_customers (
    id SERIAL PRIMARY KEY,
    customer_no INTEGER UNIQUE,
    national_id VARCHAR(50) UNIQUE,
    name VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    segment VARCHAR(50),
    signup_date DATE,
    country_code VARCHAR(10)
);

-- -----------------------------------------------------------------------------
-- dim_countries
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_countries (
    id SERIAL PRIMARY KEY,
    country_name VARCHAR(255),
    iso_code VARCHAR(10) UNIQUE,
    calling_code VARCHAR(20)
);

-- -----------------------------------------------------------------------------
-- dim_accounts
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.dim_accounts (
    id SERIAL PRIMARY KEY,
    customer_id INTEGER REFERENCES analytics.dim_customers(id),
    account_type VARCHAR(100),
    open_date DATE,
    status VARCHAR(50)
);

-- -----------------------------------------------------------------------------
-- fact_sales
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_sales (
    id SERIAL PRIMARY KEY,
    reporting_date DATE REFERENCES analytics.dim_date(date_key),
    product_id INTEGER REFERENCES analytics.dim_products(id),
    customer_id INTEGER REFERENCES analytics.dim_customers(id),
    location_id INTEGER REFERENCES analytics.dim_location(id),
    rm_id INTEGER REFERENCES analytics.dim_relationship_manager(id),
    quantity INTEGER,
    amount DECIMAL(15, 2)
);

-- -----------------------------------------------------------------------------
-- fact_banking_transactions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_banking_transactions (
    id SERIAL PRIMARY KEY,
    reporting_date DATE REFERENCES analytics.dim_date(date_key),
    account_id INTEGER REFERENCES analytics.dim_accounts(id),
    location_id INTEGER REFERENCES analytics.dim_location(id),
    rm_id INTEGER REFERENCES analytics.dim_relationship_manager(id),
    transaction_type VARCHAR(50),
    amount DECIMAL(15, 2)
);

-- -----------------------------------------------------------------------------
-- fact_loans
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_loans (
    id SERIAL PRIMARY KEY,
    customer_id INTEGER REFERENCES analytics.dim_customers(id),
    reporting_date DATE REFERENCES analytics.dim_date(date_key),
    location_id INTEGER REFERENCES analytics.dim_location(id),
    rm_id INTEGER REFERENCES analytics.dim_relationship_manager(id),
    loan_type VARCHAR(50),
    principal_amount DECIMAL(15, 2),
    interest_rate DECIMAL(5, 2)
);

-- -----------------------------------------------------------------------------
-- fact_investments
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_investments (
    id SERIAL PRIMARY KEY,
    customer_id INTEGER REFERENCES analytics.dim_customers(id),
    reporting_date DATE REFERENCES analytics.dim_date(date_key),
    hier_id INTEGER REFERENCES analytics.dim_investment_hierarchy(id),
    location_id INTEGER REFERENCES analytics.dim_location(id),
    rm_id INTEGER REFERENCES analytics.dim_relationship_manager(id),
    ticker_symbol VARCHAR(20),
    quantity INTEGER,
    current_value DECIMAL(15, 2)
);

-- -----------------------------------------------------------------------------
-- fact_department_performance
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_department_performance (
    id SERIAL PRIMARY KEY,
    reporting_date DATE REFERENCES analytics.dim_date(date_key),
    location_id INTEGER REFERENCES analytics.dim_location(id),
    rm_id INTEGER REFERENCES analytics.dim_relationship_manager(id),
    department VARCHAR(100),
    cost DECIMAL(15, 2),
    budget DECIMAL(15, 2)
);
