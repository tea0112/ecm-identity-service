--liquibase formatted sql

--changeset tea0112:v1.0-0001-seed-dev-test-data context:dev
--comment: Insert additional test data for development environment only
-- This file can be used for dev-specific test roles if needed
-- Currently empty as standard roles are in master data

-- Example: Add dev-specific test role
-- INSERT INTO sample_roles (name, description) VALUES 
--     ('ROLE_TESTER', 'Special test role for development only')
-- ON CONFLICT (name) DO NOTHING;

--rollback -- No data to rollback
