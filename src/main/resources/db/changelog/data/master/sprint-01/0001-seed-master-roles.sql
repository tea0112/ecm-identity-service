--liquibase formatted sql

--changeset tea0112:v1.0-0001-seed-master-roles
--comment: Insert standard roles for all environments - master/reference data
INSERT INTO sample_roles (name, description) VALUES 
    ('ROLE_ADMIN', 'Administrator with full access'),
    ('ROLE_USER', 'Standard user with limited access'),
    ('ROLE_MANAGER', 'Manager with team management permissions'),
    ('ROLE_DEVELOPER', 'Developer with extended permissions for testing')
ON CONFLICT (name) DO NOTHING;

--rollback DELETE FROM sample_roles WHERE name IN ('ROLE_ADMIN', 'ROLE_USER', 'ROLE_MANAGER', 'ROLE_DEVELOPER');
