--liquibase formatted sql

--changeset tea0112:v1.0-0001-seed-uat-roles context:uat
--comment: Insert default roles for UAT environment (sample_ prefix)
INSERT INTO sample_roles (name, description) VALUES 
    ('ROLE_ADMIN', 'Administrator with full access'),
    ('ROLE_USER', 'Standard user with limited access'),
    ('ROLE_TESTER', 'QA tester with testing permissions')
ON CONFLICT (name) DO NOTHING;

--rollback DELETE FROM sample_roles WHERE name IN ('ROLE_ADMIN', 'ROLE_USER', 'ROLE_TESTER');