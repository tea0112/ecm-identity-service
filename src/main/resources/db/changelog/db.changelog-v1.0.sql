--liquibase formatted sql

-- ============================================
-- VERSION 1.0 CHANGELOG
-- ============================================
-- All schema and data changes for version 1.0
-- Release Date: 2025-10-12

--changeset system:v1.0-master runOnChange:false
--comment: Version 1.0 master - includes all v1.0 schema and data changes

-- ============================================
-- Schema Changes - v1.0
-- ============================================
--include file:db/changelog/changes/v1.0/0001-create-sample-users-table.sql
--include file:db/changelog/changes/v1.0/0002-create-sample-roles-table.sql
--include file:db/changelog/changes/v1.0/0003-create-sample-user-roles-table.sql

-- ============================================
-- Seed Data - v1.0 - DEV Environment
-- ============================================
--include file:db/changelog/data/dev/v1.0/0001-seed-dev-roles.sql
--include file:db/changelog/data/dev/v1.0/0002-seed-dev-users.sql

-- ============================================
-- Seed Data - v1.0 - UAT Environment
-- ============================================
--include file:db/changelog/data/uat/v1.0/0001-seed-uat-roles.sql
--include file:db/changelog/data/uat/v1.0/0002-seed-uat-users.sql

-- ============================================
-- Seed Data - v1.0 - PROD Environment
-- ============================================
--include file:db/changelog/data/prod/v1.0/0001-seed-prod-roles.sql