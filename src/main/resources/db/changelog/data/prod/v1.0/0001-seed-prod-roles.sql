--liquibase formatted sql

--changeset tea0112:v1.0-0001-seed-prod-roles context:prod
--comment: Insert minimal roles for production environment (sample_ prefix)
INSERT INTO sample_roles (name, description) VALUES 
    ('ROLE_ADMIN', 'Administrator with full access'),
    ('ROLE_USER', 'Standard user with limited access')
ON CONFLICT (name) DO NOTHING;

--rollback DELETE FROM sample_roles WHERE name IN ('ROLE_ADMIN', 'ROLE_USER');

-- Note: No default users in production - they should be created through the application