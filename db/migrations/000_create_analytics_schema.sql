-- Phase 119: Pure SQL Seeding (Analytics Schema)
-- 2026-03-23

-- 1. Setup Schema
CREATE SCHEMA IF NOT EXISTS analytics;

-- 2. DDL: Dimensions
DROP TABLE IF EXISTS analytics.dim_date CASCADE;
CREATE TABLE analytics.dim_date (
    date_key DATE PRIMARY KEY,
    year INTEGER,
    quarter INTEGER,
    month INTEGER,
    month_name VARCHAR(20),
    day_of_week VARCHAR(20),
    is_weekend BOOLEAN
);

DROP TABLE IF EXISTS analytics.dim_location CASCADE;
CREATE TABLE analytics.dim_location (
    id SERIAL PRIMARY KEY,
    country_name VARCHAR(100),
    region VARCHAR(50)
);

DROP TABLE IF EXISTS analytics.dim_relationship_manager CASCADE;
CREATE TABLE analytics.dim_relationship_manager (
    id SERIAL PRIMARY KEY,
    rm_code VARCHAR(20) UNIQUE,
    rm_name VARCHAR(255)
);

DROP TABLE IF EXISTS analytics.dim_investment_hierarchy CASCADE;
CREATE TABLE analytics.dim_investment_hierarchy (
    id SERIAL PRIMARY KEY,
    asset_class VARCHAR(100),
    product_type VARCHAR(100),
    investment_strategy VARCHAR(100)
);

DROP TABLE IF EXISTS analytics.dim_products CASCADE;
CREATE TABLE analytics.dim_products (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    category VARCHAR(100),
    unit_price DECIMAL(10, 2)
);

DROP TABLE IF EXISTS analytics.dim_customers CASCADE;
CREATE TABLE analytics.dim_customers (
    id SERIAL PRIMARY KEY,
    customer_no INTEGER UNIQUE,
    national_id VARCHAR(50) UNIQUE,
    name VARCHAR(255),
    email VARCHAR(255) UNIQUE,
    segment VARCHAR(50),
    signup_date DATE,
    country_code VARCHAR(10)
);

DROP TABLE IF EXISTS analytics.dim_countries CASCADE;
CREATE TABLE analytics.dim_countries (
    id SERIAL PRIMARY KEY,
    country_name VARCHAR(255),
    iso_code VARCHAR(10) UNIQUE,
    calling_code VARCHAR(20)
);

DROP TABLE IF EXISTS analytics.dim_accounts CASCADE;
CREATE TABLE analytics.dim_accounts (
    id SERIAL PRIMARY KEY,
    customer_id INTEGER REFERENCES analytics.dim_customers(id),
    account_type VARCHAR(100),
    open_date DATE,
    status VARCHAR(50)
);

-- 3. DDL: Fact Tables
DROP TABLE IF EXISTS analytics.fact_sales CASCADE;
CREATE TABLE analytics.fact_sales (
    id SERIAL PRIMARY KEY,
    reporting_date DATE REFERENCES analytics.dim_date(date_key),
    product_id INTEGER REFERENCES analytics.dim_products(id),
    customer_id INTEGER REFERENCES analytics.dim_customers(id),
    location_id INTEGER REFERENCES analytics.dim_location(id),
    rm_id INTEGER REFERENCES analytics.dim_relationship_manager(id),
    quantity INTEGER,
    amount DECIMAL(15, 2)
);

DROP TABLE IF EXISTS analytics.fact_banking_transactions CASCADE;
CREATE TABLE analytics.fact_banking_transactions (
    id SERIAL PRIMARY KEY,
    reporting_date DATE REFERENCES analytics.dim_date(date_key),
    account_id INTEGER REFERENCES analytics.dim_accounts(id),
    location_id INTEGER REFERENCES analytics.dim_location(id),
    rm_id INTEGER REFERENCES analytics.dim_relationship_manager(id),
    transaction_type VARCHAR(50),
    amount DECIMAL(15, 2)
);

DROP TABLE IF EXISTS analytics.fact_loans CASCADE;
CREATE TABLE analytics.fact_loans (
    id SERIAL PRIMARY KEY,
    customer_id INTEGER REFERENCES analytics.dim_customers(id),
    reporting_date DATE REFERENCES analytics.dim_date(date_key),
    location_id INTEGER REFERENCES analytics.dim_location(id),
    rm_id INTEGER REFERENCES analytics.dim_relationship_manager(id),
    loan_type VARCHAR(50),
    principal_amount DECIMAL(15, 2),
    interest_rate DECIMAL(5, 2)
);

DROP TABLE IF EXISTS analytics.fact_investments CASCADE;
CREATE TABLE analytics.fact_investments (
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

DROP TABLE IF EXISTS analytics.fact_department_performance CASCADE;
CREATE TABLE analytics.fact_department_performance (
    id SERIAL PRIMARY KEY,
    reporting_date DATE REFERENCES analytics.dim_date(date_key),
    location_id INTEGER REFERENCES analytics.dim_location(id),
    rm_id INTEGER REFERENCES analytics.dim_relationship_manager(id),
    department VARCHAR(100),
    cost DECIMAL(15, 2),
    budget DECIMAL(15, 2)
);

-- 4. SEED DATA: Dimensions
INSERT INTO analytics.dim_date (date_key, year, quarter, month, month_name, day_of_week, is_weekend)
SELECT 
    d::date as date_key,
    EXTRACT(year FROM d)::int as year,
    EXTRACT(quarter FROM d)::int as quarter,
    EXTRACT(month FROM d)::int as month,
    to_char(d, 'Month') as month_name,
    to_char(d, 'Day') as day_of_week,
    CASE WHEN EXTRACT(isodow FROM d) IN (6, 7) THEN true ELSE false END as is_weekend
FROM generate_series('2024-01-01'::date, '2026-12-31'::date, '1 day'::interval) d
ON CONFLICT (date_key) DO NOTHING;

INSERT INTO analytics.dim_investment_hierarchy (asset_class, product_type, investment_strategy) VALUES
('Equities', 'Mutual Fund', 'Growth'),
('Fixed Income', 'Bond', 'Income'),
('Alternatives', 'Crypto', 'Speculative');

INSERT INTO analytics.dim_location (country_name, region) VALUES
('Romania', 'Europe'), ('USA', 'America'), ('UK', 'Europe'), ('Germany', 'Europe');

INSERT INTO analytics.dim_relationship_manager (rm_code, rm_name) VALUES
('RM001', 'James J. Smith'), ('RM002', 'Mary M. Johnson');

INSERT INTO analytics.dim_products (name, category, unit_price) VALUES
('Checking Standard', 'Deposits', 0.00), ('Savings High-Yield', 'Deposits', 0.00), ('Mortgage Fixed 30Y', 'Lending', 500.00);

INSERT INTO analytics.dim_customers (customer_no, national_id, name, email, segment, signup_date, country_code) VALUES
(1000, '1850101123456', 'Robert R. Brown', 'robert.brown.0@example.com', 'Retail', '2024-01-01', 'RO'),
(1001, '2900202654321', 'Patricia P. Williams', 'patricia.williams.1@example.com', 'Wealth', '2024-01-02', 'US');

INSERT INTO analytics.dim_countries (country_name, iso_code, calling_code) VALUES
('Romania', 'RO', '+40'), ('United States', 'US', '+1');

INSERT INTO analytics.dim_accounts (customer_id, account_type, open_date, status) VALUES
(1, 'Checking', '2024-01-01', 'Active'), (2, 'Savings', '2024-01-02', 'Active');

-- 5. SEED DATA: Facts
INSERT INTO analytics.fact_sales (reporting_date, product_id, customer_id, location_id, rm_id, quantity, amount) VALUES
('2024-01-01', 1, 1, 1, 1, 1, 100.00),
('2025-01-01', 3, 2, 2, 2, 1, 500.00);

-- (In a real scenario, this would have 1000s of lines. For this seed file, we provide a baseline).
