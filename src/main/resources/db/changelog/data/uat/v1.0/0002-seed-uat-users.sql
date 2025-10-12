--liquibase formatted sql

--changeset tea0112:v1.0-0002-seed-uat-users context:uat
--comment: Insert UAT test users (sample_ prefix)
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM sample_users WHERE username='uat_admin'

-- Use strong passwords for UAT
INSERT INTO sample_users (username, email, password_hash, first_name, last_name, enabled) VALUES 
    ('uat_admin', 'admin@uat.ecm.local', '$2a$10$N9qo8uLOickgx2ZMRZoMye1IlJ8XQ2bqHJ8tIg3XF6p3L5FUt8zWm', 'UAT', 'Admin', true),
    ('uat_tester', 'tester@uat.ecm.local', '$2a$10$N9qo8uLOickgx2ZMRZoMye1IlJ8XQ2bqHJ8tIg3XF6p3L5FUt8zWm', 'UAT', 'Tester', true);

-- Assign roles
INSERT INTO sample_user_roles (user_id, role_id) 
SELECT u.id, r.id 
FROM sample_users u, sample_roles r 
WHERE u.username = 'uat_admin' AND r.name = 'ROLE_ADMIN';

INSERT INTO sample_user_roles (user_id, role_id) 
SELECT u.id, r.id 
FROM sample_users u, sample_roles r 
WHERE u.username = 'uat_tester' AND r.name = 'ROLE_TESTER';

--rollback DELETE FROM sample_user_roles WHERE user_id IN (SELECT id FROM sample_users WHERE username IN ('uat_admin', 'uat_tester'));
--rollback DELETE FROM sample_users WHERE username IN ('uat_admin', 'uat_tester');