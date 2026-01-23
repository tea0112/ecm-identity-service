--liquibase formatted sql
--changeset team:20260107121004-seed-prod-roles context:prod
--comment: Seed base roles for prod

INSERT INTO roles (name, description)
SELECT 'ADMIN', 'Administrator with full access'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

INSERT INTO roles (name, description)
SELECT 'USER', 'Standard user'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'USER');
