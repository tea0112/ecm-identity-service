--liquibase formatted sql

--changeset tea0112:v1.0-0002-seed-dev-users context:dev
--comment: Insert test users for development (sample_ prefix)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sample_users WHERE username='dev_admin'

-- Password is 'password' hashed with BCrypt (rounds=10)
INSERT INTO sample_users (username, email, password_hash, first_name, last_name, enabled) VALUES 
    ('dev_admin', 'admin@dev.local', '$2a$10$N9qo8uLOickgx2ZMRZoMye1IlJ8XQ2bqHJ8tIg3XF6p3L5FUt8zWm', 'Dev', 'Admin', true),
    ('dev_user', 'user@dev.local', '$2a$10$N9qo8uLOickgx2ZMRZoMye1IlJ8XQ2bqHJ8tIg3XF6p3L5FUt8zWm', 'Dev', 'User', true),
    ('test_user', 'test@dev.local', '$2a$10$N9qo8uLOickgx2ZMRZoMye1IlJ8XQ2bqHJ8tIg3XF6p3L5FUt8zWm', 'Test', 'User', true);

-- Assign roles to users
INSERT INTO sample_user_roles (user_id, role_id) 
SELECT u.id, r.id 
FROM sample_users u, sample_roles r 
WHERE u.username = 'dev_admin' AND r.name = 'ROLE_ADMIN';

INSERT INTO sample_user_roles (user_id, role_id) 
SELECT u.id, r.id 
FROM sample_users u, sample_roles r 
WHERE u.username = 'dev_user' AND r.name = 'ROLE_USER';

INSERT INTO sample_user_roles (user_id, role_id) 
SELECT u.id, r.id 
FROM sample_users u, sample_roles r 
WHERE u.username = 'test_user' AND r.name = 'ROLE_USER';

--rollback DELETE FROM sample_user_roles WHERE user_id IN (SELECT id FROM sample_users WHERE username IN ('dev_admin', 'dev_user', 'test_user'));
--rollback DELETE FROM sample_users WHERE username IN ('dev_admin', 'dev_user', 'test_user');