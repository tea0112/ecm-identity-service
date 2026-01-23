--liquibase formatted sql
--changeset team:20260107120001-create-roles-table
--comment: Create roles table

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT uuidv7(),
    name        VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by  VARCHAR(100) DEFAULT 'system',
    updated_by  VARCHAR(100) DEFAULT 'system'
);

CREATE INDEX idx_roles_name ON roles(name);

--rollback DROP TABLE IF EXISTS roles CASCADE;