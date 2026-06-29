-- Migration: Add hierarchical column properties to rpt_column_def
-- Adds tier_level (default 'L1') and parent_id (default NULL) columns to support nested horizontal headers.

ALTER TABLE reporting.rpt_column_def
ADD COLUMN tier_level VARCHAR(10) DEFAULT 'L1' NOT NULL,
ADD COLUMN parent_id VARCHAR(50) DEFAULT NULL;
