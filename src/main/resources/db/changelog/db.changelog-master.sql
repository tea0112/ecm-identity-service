--liquibase formatted sql

-- ============================================
-- MASTER CHANGELOG - Main Entry Point
-- ============================================
-- This file remains small by delegating to version-specific changelogs
-- Each version has its own master file with schema + data changes

--changeset system:master-changelog-root runOnChange:false
--comment: Root master changelog - includes version-specific masters

-- Version 1.0 Release
--include file:db/changelog/db.changelog-v1.0.sql

-- Version 2.0 Release (future)
-- --include file:db/changelog/db.changelog-v2.0.sql

-- Version 3.0 Release (future)
-- --include file:db/changelog/db.changelog-v3.0.sql

-- ============================================
-- As you add new versions:
-- 1. Create new db.changelog-vX.X.sql file
-- 2. Add one line here: --include file:db/changelog/db.changelog-vX.X.sql
-- 3. This file stays small forever!
-- ============================================