--liquibase formatted sql
--changeset team:20260107121003-seed-uat-users context:uat
--comment: Seed UAT admin user (password hash should be replaced before production use)

INSERT INTO users (username, email, password_hash, enabled, created_by, updated_by)
SELECT 'admin', 'admin@uat.local', '$2a$10$replace_me_with_bcrypt_hash', true, 'system', 'system'
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
