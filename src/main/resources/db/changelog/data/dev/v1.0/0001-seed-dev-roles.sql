--liquibase formatted sql

--changeset tea0112:v1.0-0001-seed-dev-roles context:dev
--comment: Insert default roles for development environment (sample_ prefix)
INSERT INTO sample_roles (name, description) VALUES 
    ('ROLE_ADMIN', 'Administrator with full access'),
    ('ROLE_USER', 'Standard user with limited access'),
    ('ROLE_DEVELOPER', 'Developer with extended permissions for testing')
ON CONFLICT (name) DO NOTHING;

--rollback DELETE FROM sample_roles WHERE name IN ('ROLE_ADMIN', 'ROLE_USER', 'ROLE_DEVELOPER');