--liquibase formatted sql

--changeset tea0112:20251012103007-seed-prod-initial-data context:prod
--comment: Insert initial data for production environment only
-- This file can be used for prod-specific initial admin users if needed
-- Currently empty as standard roles are in master data

-- Example: Create initial production admin user
-- INSERT INTO sample_users (username, email, password_hash, first_name, last_name) VALUES 
--     ('prod_admin', 'admin@company.com', '$2a$10$...', 'System', 'Administrator')
-- ON CONFLICT (username) DO NOTHING;

--rollback -- No data to rollback
