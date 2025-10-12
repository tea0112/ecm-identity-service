--liquibase formatted sql

--changeset tea0112:v1.0-0002-seed-uat-users context:uat
--comment: Insert test users for UAT environment
-- Password for all users: 'password123' (bcrypt hash)
INSERT INTO sample_users (username, email, password_hash, first_name, last_name) VALUES 
    ('uat_admin', 'uat.admin@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'UAT', 'Admin'),
    ('uat_user', 'uat.user@example.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'UAT', 'User')
ON CONFLICT (username) DO NOTHING;

-- Assign roles to users
INSERT INTO sample_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sample_users u, sample_roles r 
WHERE u.username = 'uat_admin' AND r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO sample_user_roles (user_id, role_id)
SELECT u.id, r.id FROM sample_users u, sample_roles r 
WHERE u.username = 'uat_user' AND r.name = 'ROLE_USER'
ON CONFLICT DO NOTHING;

--rollback DELETE FROM sample_user_roles WHERE user_id IN (SELECT id FROM sample_users WHERE username IN ('uat_admin', 'uat_user'));
--rollback DELETE FROM sample_users WHERE username IN ('uat_admin', 'uat_user');
