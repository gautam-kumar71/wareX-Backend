-- V2__add_product_and_warehouse_names.sql
-- Adding denormalized fields for better reporting performance without joining.

ALTER TABLE stock_movements
ADD COLUMN product_name VARCHAR(255) AFTER product_id,
ADD COLUMN warehouse_name VARCHAR(255) AFTER warehouse_id;
