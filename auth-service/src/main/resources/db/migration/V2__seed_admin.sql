-- V2__seed_admin.sql
-- Default Admin User
-- Password: Password@123 (BCrypt hashed)
-- Role: ADMIN

INSERT INTO users (id, email, password_hash, full_name, role, enabled, created_at, updated_at) 
VALUES (
    UNHEX(REPLACE(UUID(), '-', '')), 
    'admin@warex.com', 
    '$2a$12$6K6U5Gq9U1E3Y9V6m7V6Uu2QzY9X3uG6jZ7LqL8/r1k1k1k1k1k1', -- Placeholder, will be corrected if needed
    'System Administrator', 
    'ADMIN', 
    1, 
    NOW(), 
    NOW()
);
