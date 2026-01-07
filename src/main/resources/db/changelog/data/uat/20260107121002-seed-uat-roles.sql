--liquibase formatted sql
--changeset team:20260107121002-seed-uat-roles context:uat
--comment: Seed base roles for UAT

INSERT INTO roles (name, description)
SELECT 'ADMIN', 'Administrator with full access'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ADMIN');

INSERT INTO roles (name, description)
SELECT 'USER', 'Standard user'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'USER');
