--liquibase formatted sql
--changeset team:20260107120000-create-users-table
--comment: Create users table

CREATE TABLE users (
    id                   BIGSERIAL PRIMARY KEY,
    username             VARCHAR(100) NOT NULL UNIQUE,
    email                VARCHAR(255) NOT NULL UNIQUE,
    password_hash        VARCHAR(255) NOT NULL,
    first_name           VARCHAR(100),
    last_name            VARCHAR(100),
    enabled              BOOLEAN NOT NULL DEFAULT true,
    account_locked       BOOLEAN NOT NULL DEFAULT false,
    account_expired      BOOLEAN NOT NULL DEFAULT false,
    credentials_expired  BOOLEAN NOT NULL DEFAULT false,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           VARCHAR(100) DEFAULT 'system',
    updated_by           VARCHAR(100) DEFAULT 'system'
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_enabled ON users(enabled);
CREATE INDEX idx_users_created_at ON users(created_at);

--rollback DROP TABLE IF EXISTS users CASCADE;
