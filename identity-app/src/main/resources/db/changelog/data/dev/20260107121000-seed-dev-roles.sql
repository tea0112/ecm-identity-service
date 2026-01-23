--liquibase formatted sql
--changeset team:20260107121000-seed-dev-roles context:dev
--comment: Seed base roles for dev

INSERT INTO roles (name, description)
SELECT 'ADMIN', 'Administrator with full access'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

INSERT INTO roles (name, description)
SELECT 'USER', 'Standard user'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'USER');
