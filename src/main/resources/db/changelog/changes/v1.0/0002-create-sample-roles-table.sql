--liquibase formatted sql

--changeset tea0112:v1.0-0002-create-sample-roles-table
--comment: Create sample_roles table for RBAC
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='sample_roles'

CREATE TABLE sample_roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sample_role_name_format CHECK (name ~ '^ROLE_[A-Z_]+$')
);

-- Create index
CREATE INDEX idx_sample_roles_name ON sample_roles(name);

-- Add comment to table
COMMENT ON TABLE sample_roles IS 'Sample roles table for testing - defines user roles for RBAC';

--rollback DROP TABLE IF EXISTS sample_roles CASCADE;