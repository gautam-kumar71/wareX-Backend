-- V2__add_category_to_suppliers.sql
ALTER TABLE suppliers ADD COLUMN category VARCHAR(50) NULL AFTER notes;
