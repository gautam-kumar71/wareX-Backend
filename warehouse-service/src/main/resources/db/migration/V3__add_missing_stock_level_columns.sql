-- V3__add_missing_stock_level_columns.sql
-- Adding missing columns to stock_levels table in warehouse-service.

ALTER TABLE stock_levels
ADD COLUMN product_name VARCHAR(255) AFTER product_id,
ADD COLUMN sku VARCHAR(100) AFTER product_name,
ADD COLUMN aisle VARCHAR(50) AFTER sku,
ADD COLUMN rack VARCHAR(50) AFTER aisle,
ADD COLUMN bin VARCHAR(50) AFTER rack,
ADD COLUMN batch_number VARCHAR(100) AFTER bin,
ADD COLUMN expiry_date TIMESTAMP NULL AFTER batch_number;
