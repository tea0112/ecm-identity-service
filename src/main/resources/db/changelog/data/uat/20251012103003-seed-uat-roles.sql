--liquibase formatted sql

--changeset tea0112:20251012103003-seed-uat-roles
--comment: Insert standard roles for all environments - master/reference data
-- Only insert if the role doesn't already exist
INSERT INTO sample_roles (name, description) 
SELECT 'ROLE_ADMIN', 'Administrator with full access'
WHERE NOT EXISTS (SELECT 1 FROM sample_roles WHERE name = 'ROLE_ADMIN');

INSERT INTO sample_roles (name, description) 
SELECT 'ROLE_USER', 'Standard user with limited access'
WHERE NOT EXISTS (SELECT 1 FROM sample_roles WHERE name = 'ROLE_USER');

INSERT INTO sample_roles (name, description) 
SELECT 'ROLE_MANAGER', 'Manager with team management permissions'
WHERE NOT EXISTS (SELECT 1 FROM sample_roles WHERE name = 'ROLE_MANAGER');

INSERT INTO sample_roles (name, description) 
SELECT 'ROLE_DEVELOPER', 'Developer with extended permissions for testing'
WHERE NOT EXISTS (SELECT 1 FROM sample_roles WHERE name = 'ROLE_DEVELOPER');

--rollback DELETE FROM sample_roles WHERE name IN ('ROLE_ADMIN', 'ROLE_USER', 'ROLE_MANAGER', 'ROLE_DEVELOPER');
