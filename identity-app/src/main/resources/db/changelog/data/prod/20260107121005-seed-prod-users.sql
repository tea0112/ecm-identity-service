--liquibase formatted sql
--changeset team:20260107121005-seed-prod-users context:prod
--comment: Seed prod admin user (ensure secure hash before use)

-- Replace with a real encoded password hash before running in production
INSERT INTO users (username, email, password_hash, enabled, created_by, updated_by)
SELECT 'admin', 'admin@company.com', '$2a$10$replace_me_with_secure_hash', true, 'system', 'system'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
CROSS JOIN roles r
WHERE u.username = 'admin'
  AND r.name = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
