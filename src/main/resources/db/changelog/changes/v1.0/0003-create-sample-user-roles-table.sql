--liquibase formatted sql

--changeset tea0112:v1.0-0003-create-sample-user-roles-table
--comment: Create sample_user_roles junction table for many-to-many relationship
CREATE TABLE sample_user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(100) DEFAULT 'system',
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES sample_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES sample_roles(id) ON DELETE CASCADE
);

-- Create indexes for foreign keys
CREATE INDEX idx_sample_user_roles_user_id ON sample_user_roles(user_id);
CREATE INDEX idx_sample_user_roles_role_id ON sample_user_roles(role_id);

-- Add comment to table
COMMENT ON TABLE sample_user_roles IS 'Sample user-role junction table for testing - maps users to their assigned roles';

--rollback DROP TABLE IF EXISTS sample_user_roles CASCADE;
