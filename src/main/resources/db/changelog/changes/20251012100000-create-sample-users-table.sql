--liquibase formatted sql

--changeset tea0112:20251012100000-create-sample-users-table
--comment: Create sample_users table with basic authentication fields
CREATE TABLE sample_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT true,
    account_locked BOOLEAN NOT NULL DEFAULT false,
    account_expired BOOLEAN NOT NULL DEFAULT false,
    credentials_expired BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100) DEFAULT 'system'
);

-- Create indexes for performance
CREATE INDEX idx_sample_users_username ON sample_users(username);
CREATE INDEX idx_sample_users_email ON sample_users(email);
CREATE INDEX idx_sample_users_enabled ON sample_users(enabled);
CREATE INDEX idx_sample_users_created_at ON sample_users(created_at);

-- Add comment to table
COMMENT ON TABLE sample_users IS 'Sample users table for testing - contains user authentication and profile data';

--rollback DROP TABLE IF EXISTS sample_users CASCADE;
