--liquibase formatted sql

--changeset tea0112:20251012100001-create-sample-roles-table
--comment: Create sample_roles table for role-based access control
CREATE TABLE sample_roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100) DEFAULT 'system'
);

-- Create index for performance
CREATE INDEX idx_sample_roles_name ON sample_roles(name);

-- Add comment to table
COMMENT ON TABLE sample_roles IS 'Sample roles table for testing - contains role definitions for RBAC';

--rollback DROP TABLE IF EXISTS sample_roles CASCADE;
