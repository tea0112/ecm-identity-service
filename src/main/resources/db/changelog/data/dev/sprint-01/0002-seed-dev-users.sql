--liquibase formatted sql

--changeset tea0112:v1.0-0002-seed-dev-users context:dev
--comment: Insert test users for development environment
-- Password for all users: 'password123' (bcrypt hash)
INSERT INTO sample_users (username, email, password_hash, first_name, last_name) VALUES 
    ('admin', 'admin@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Admin', 'User'),
    ('johndoe', 'john.doe@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'John', 'Doe'),
    ('janedoe', 'jane.doe@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Jane', 'Doe')
ON CONFLICT (username) DO NOTHING;

-- Assign roles to users
INSERT INTO sample_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sample_users u, sample_roles r 
WHERE u.username = 'admin' AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO sample_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sample_users u, sample_roles r 
WHERE u.username = 'johndoe' AND r.name = 'ROLE_USER'
ON CONFLICT DO NOTHING;

INSERT INTO sample_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sample_users u, sample_roles r 
WHERE u.username = 'janedoe' AND r.name = 'ROLE_DEVELOPER'
ON CONFLICT DO NOTHING;

--rollback DELETE FROM sample_user_roles WHERE user_id IN (SELECT id FROM sample_users WHERE username IN ('admin', 'johndoe', 'janedoe'));
--rollback DELETE FROM sample_users WHERE username IN ('admin', 'johndoe', 'janedoe');
