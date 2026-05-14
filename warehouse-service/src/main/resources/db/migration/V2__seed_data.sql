-- V2__seed_data.sql
-- Default warehouses for development / testing.
-- In production, use the API to create warehouses.

INSERT INTO warehouses (name, location, city, country, active) VALUES
                                                                   ('Main Warehouse',   'Industrial Area, Sector 5', 'Mumbai',    'India', TRUE),
                                                                   ('North Hub',        'NH-44 Logistics Park',      'Delhi',     'India', TRUE),
                                                                   ('South Distribution', 'SIDCO Industrial Estate', 'Chennai',   'India', TRUE);