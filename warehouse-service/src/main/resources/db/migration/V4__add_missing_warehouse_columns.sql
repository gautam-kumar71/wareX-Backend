-- V4__add_missing_warehouse_columns.sql
-- Adding missing columns to warehouses table in warehouse-service.

ALTER TABLE warehouses
ADD COLUMN total_storage_capacity INT NULL AFTER active,
ADD COLUMN current_capacity_utilization INT DEFAULT 0 AFTER total_storage_capacity,
ADD COLUMN manager_name VARCHAR(100) NULL AFTER current_capacity_utilization,
ADD COLUMN contact_phone VARCHAR(50) NULL AFTER manager_name;
