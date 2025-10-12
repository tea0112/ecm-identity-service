# Liquibase Setup & Best Practices Guide

## Table of Contents
- [Overview](#overview)
- [Project Structure](#project-structure)
- [Configuration](#configuration)
- [Best Practices](#best-practices)
- [Naming Conventions](#naming-conventions)
- [Handling Large Seed Data](#handling-large-seed-data)
- [Makefile Commands](#makefile-commands)
- [First Run Guide](#first-run-guide)
- [Troubleshooting](#troubleshooting)

---

## Overview

This project uses **Liquibase** with **SQL format** for database schema versioning and migrations across multiple environments (local, dev, uat, prod).

### Key Features
- ✅ SQL-based changelogs (native PostgreSQL syntax)
- ✅ Version-organized structure (v1.0, v2.0, v3.0)
- ✅ Environment-specific seed data using contexts
- ✅ Scalable master changelog design
- ✅ Table prefix `sample_` for testing
- ✅ PostgreSQL 18 compatibility

---

## Project Structure

```
src/main/resources/db/changelog/
├── db.changelog-master.sql              # Main entry point (stays small)
├── db.changelog-v1.0.sql                # Version 1.0 master
├── db.changelog-v2.0.sql                # Version 2.0 master (future)
├── changes/
│   ├── v1.0/
│   │   ├── 0001-create-sample-users-table.sql
│   │   ├── 0002-create-sample-roles-table.sql
│   │   └── 0003-create-sample-user-roles-table.sql
│   └── v2.0/
│       ├── 0001-add-email-verification.sql
│       └── 0002-add-2fa-support.sql
└── data/
    ├── dev/
    │   ├── v1.0/
    │   │   ├── 0001-seed-dev-roles.sql
    │   │   └── 0002-seed-dev-users.sql
    │   └── v2.0/
    │       └── 0001-seed-dev-v2-features.sql
    ├── uat/
    │   └── v1.0/
    │       ├── 0001-seed-uat-roles.sql
    │       └── 0002-seed-uat-users.sql
    └── prod/
        └── v1.0/
            └── 0001-seed-prod-roles.sql
```

### Why This Structure?

**Version-Based Organization:**
- Each version (v1.0, v2.0) has its own master file
- Main master stays tiny (just includes version masters)
- Easy to locate all changes for a specific release

**Environment Isolation:**
- Separate seed data per environment (dev, uat, prod)
- Controlled by Liquibase contexts
- Production has minimal seed data

**Scalability:**
- Adding v3.0? Just create new master file and add 1 line to main master
- Large seed data? Split by entity or use CSV import
- Master changelog never grows beyond ~20 lines

---

## Configuration

### application.properties (Base)

```properties
spring.application.name=ecm-identity-service

# Liquibase configuration
spring.liquibase.enabled=true
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.sql
spring.liquibase.default-schema=public

# JPA configuration
spring.jpa.hibernate.ddl-auto=none
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

### application-local.properties

```properties
# Database configuration - PostgreSQL 18
spring.datasource.url=jdbc:postgresql://localhost:5432/ecm_identity_dev
spring.datasource.username=dev_ecm
spring.datasource.password=dev_ecm!23456
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true

# Liquibase contexts - include dev data seeding
spring.liquibase.contexts=local,dev

# JPA
spring.jpa.show-sql=true

# Other dev settings
logging.level.liquibase=DEBUG
logging.level.com.ecm.security=DEBUG
```

### application-dev.properties

```properties
# Database configuration - PostgreSQL 18
spring.datasource.url=jdbc:postgresql://dev-db-server:5432/ecm_identity_dev
spring.datasource.username=dev_ecm
spring.datasource.password=${DEV_DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true

# Liquibase contexts - include dev data seeding
spring.liquibase.contexts=dev

# JPA
spring.jpa.show-sql=true

# Other dev settings
logging.level.liquibase=DEBUG
logging.level.com.ecm.security=DEBUG
```

### application-uat.properties

```properties
# Database configuration - PostgreSQL 18
spring.datasource.url=jdbc:postgresql://uat-db-server:5432/ecm_identity_uat
spring.datasource.username=uat_ecm
spring.datasource.password=${UAT_DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

# Liquibase contexts - include UAT data
spring.liquibase.contexts=uat

# JPA
spring.jpa.show-sql=false

# Logging
logging.level.liquibase=INFO
logging.level.com.ecm.security=INFO
```

### application-prod.properties

```properties
# Database configuration - PostgreSQL 18
spring.datasource.url=jdbc:postgresql://prod-db-server:5432/ecm_identity_prod
spring.datasource.username=prod_ecm
spring.datasource.password=${PROD_DB_PASSWORD}
spring.datasource.driver-class-name=org.postgresql.Driver

# Liquibase contexts - production only
spring.liquibase.contexts=prod

# JPA
spring.jpa.show-sql=false

# Logging
logging.level.liquibase=WARN
logging.level.com.ecm.security=INFO

# Production-specific settings
server.error.include-message=never
server.error.include-stacktrace=never
```

---

## Best Practices

### 1. Naming Conventions

**File Naming:**
```
db/changelog/changes/v1.0/0001-create-sample-users-table.sql
db/changelog/changes/v2.0/0001-add-email-verification.sql
```
- Use 4 digits: `0001`, `0002`, ..., `9999`
- Group by version folder: `v1.0`, `v2.0`, `v3.0`
- Descriptive names after number

**Changeset ID Format:**
```sql
--changeset author:version-number-descriptive-name

-- Examples:
--changeset tea0112:v1.0-0001-create-users-table
--changeset tea0112:v1.0-0002-create-roles-table
--changeset tea0112:v2.0-0001-add-2fa-support
--changeset tea0112:v2.0-0002-add-email-verification
```

**Why include version in changeset ID?**
- ✅ Ensures global uniqueness across all versions
- ✅ Clear tracking in `DATABASECHANGELOG` table
- ✅ Easy to identify which release a change belongs to
- ✅ Prevents conflicts when resetting numbering in new versions

### 2. Always Include

- `--comment:` Explain what and why
- `--rollback` statement for every changeset
- Preconditions for dependent changes
- Context for environment-specific data

### 3. Precondition Options

```sql
--preconditions onFail:HALT|CONTINUE|MARK_RAN|WARN onError:HALT|CONTINUE|MARK_RAN|WARN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM table_name
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_name='users'
```

### 4. Transaction Control

```sql
--changeset author:id runInTransaction:false
-- Use for DDL that can't run in transaction (like CREATE INDEX CONCURRENTLY)
CREATE INDEX CONCURRENTLY idx_users_email ON users(email);
```

### 5. Idempotency

```sql
-- Always use IF EXISTS / IF NOT EXISTS
CREATE TABLE IF NOT EXISTS users (...);
DROP TABLE IF EXISTS old_table CASCADE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS new_column VARCHAR(100);
```

### 6. Rollback Strategies

Every changeset should have a rollback strategy:

```sql
--changeset tea0112:v1.0-0001-create-users-table
CREATE TABLE sample_users (...);

--rollback DROP TABLE IF EXISTS sample_users CASCADE;
```

For complex changes:
```sql
--changeset tea0112:v1.0-0005-add-audit-columns
ALTER TABLE sample_users ADD COLUMN created_by VARCHAR(100) DEFAULT 'system';
ALTER TABLE sample_users ADD COLUMN updated_by VARCHAR(100) DEFAULT 'system';

--rollback ALTER TABLE sample_users DROP COLUMN IF EXISTS created_by;
--rollback ALTER TABLE sample_users DROP COLUMN IF EXISTS updated_by;
```

---

## Handling Large Seed Data

### Problem
Single seed file becomes too large (1000+ rows) and unmaintainable.

### Solution A: Split by Entity (100-1000 rows)

```
db/changelog/data/dev/v1.0/
├── 0001-seed-dev-roles.sql         (50 rows)
├── 0002-seed-dev-users.sql         (500 rows)
├── 0003-seed-dev-organizations.sql (200 rows)
└── 0004-seed-dev-permissions.sql   (1000 rows)
```

### Solution B: Use CSV for Bulk Data (10,000+ rows)

**Create XML changeset for CSV loading:**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-latest.xsd">

    <changeSet id="v1.0-seed-bulk-users" author="tea0112" context="dev">
        <comment>Bulk load 10,000 users from CSV</comment>
        <loadData tableName="sample_users" 
                  file="db/changelog/data/dev/csv/users.csv"
                  separator=",">
            <column name="username" type="STRING"/>
            <column name="email" type="STRING"/>
            <column name="password_hash" type="STRING"/>
            <column name="first_name" type="STRING"/>
            <column name="last_name" type="STRING"/>
            <column name="enabled" type="BOOLEAN"/>
        </loadData>
    </changeSet>
</databaseChangeLog>
```

**Or use PostgreSQL COPY in SQL:**

```sql
--changeset tea0112:v1.0-seed-bulk-users context:dev
--comment: Bulk load 10,000 users from CSV
COPY sample_users (username, email, password_hash, first_name, last_name, enabled)
FROM '/path/to/users.csv'
WITH (FORMAT csv, HEADER true);

--rollback DELETE FROM sample_users WHERE created_by='csv_import';
```

### Solution C: External Script (100,000+ rows)

For very large datasets, don't put in Liquibase:

```bash
#!/bin/bash
# scripts/import-large-dataset.sh
ENV=$1
PGPASSWORD=dev_ecm!23456 psql -h localhost -p 5432 -U dev_ecm -d ecm_identity_dev \
  -c "\COPY sample_users FROM 'data/users-${ENV}.csv' CSV HEADER"
```

### Best Practice Summary

- **Small data (< 100 rows)**: Single SQL file ✅
- **Medium data (100-1000 rows)**: Split by entity ✅
- **Large data (1000-10000 rows)**: CSV import ✅
- **Very large data (10000+ rows)**: External script ✅

---

## Makefile Commands

### Initial Setup (First Time)
```bash
make docker-up          # Start PostgreSQL 18
make build-skip-tests   # Build without tests
make run-dev            # Run with dev profile (migrates v1.0)
```

### Daily Development
```bash
make docker-up          # Start database if not running
make run-dev            # Run application
```

### Database Management
```bash
make db-connect-dev     # Connect to database with psql
make docker-logs        # View PostgreSQL logs
make docker-down        # Stop all containers
```

### Liquibase Operations
```bash
make db-validate                              # Validate changelog syntax
PROFILE=dev make db-status                    # Check pending changes
PROFILE=dev make db-update                    # Apply changes manually
PROFILE=dev ROLLBACK_COUNT=1 make db-rollback-count  # Rollback last changeset
```

### Utility Commands
```bash
make help               # Show all available commands
make info               # Show configuration
make version            # Show app version
```

---

## First Run Guide

### Step 1: Prerequisites Check

```bash
# Check Docker is running
docker --version
docker ps

# Check Java
java -version  # Should be Java 21

# Check Gradle wrapper
ls -la gradlew
```

### Step 2: Start PostgreSQL 18

```bash
make docker-up
```

**Expected output:**
```
Starting PostgreSQL 18 container...
PostgreSQL is ready on localhost:5432
  Database: ecm_identity_dev
  Username: dev_ecm
  Password: dev_ecm!23456
```

### Step 3: Build the Application

```bash
# If all test infrastructure is ready
make build

# If RabbitMQ/Kafka not running yet
make build-skip-tests
```

### Step 4: Run Application (First Migration)

```bash
# Option A: Use local profile
./gradlew bootRun --args='--spring.profiles.active=local'

# Option B: Use dev profile  
make run-dev
```

**What happens on first run:**

1. ✅ Spring Boot starts
2. ✅ Liquibase detects empty database
3. ✅ Creates `DATABASECHANGELOG` tracking table
4. ✅ Executes v1.0 changesets in order:
   - Creates `sample_users` table
   - Creates `sample_roles` table
   - Creates `sample_user_roles` table
   - Seeds dev roles (3 roles)
   - Seeds dev users (3 users with role assignments)
5. ✅ Application starts successfully

**Expected console output:**
```
INFO  liquibase.lockservice : Successfully acquired change log lock
INFO  liquibase.changelog : Creating database history table [public].[databasechangelog]
INFO  liquibase.changelog : Reading from public.databasechangelog
INFO  liquibase.changelog : Running Changeset: db/changelog/changes/v1.0/0001-create-sample-users-table.sql::v1.0-0001-create-sample-users-table::tea0112
INFO  liquibase.changelog : Running Changeset: db/changelog/changes/v1.0/0002-create-sample-roles-table.sql::v1.0-0002-create-sample-roles-table::tea0112
INFO  liquibase.changelog : Running Changeset: db/changelog/changes/v1.0/0003-create-sample-user-roles-table.sql::v1.0-0003-create-sample-user-roles-table::tea0112
INFO  liquibase.changelog : Running Changeset: db/changelog/data/dev/v1.0/0001-seed-dev-roles.sql::v1.0-0001-seed-dev-roles::tea0112
INFO  liquibase.changelog : Running Changeset: db/changelog/data/dev/v1.0/0002-seed-dev-users.sql::v1.0-0002-seed-dev-users::tea0112
INFO  liquibase.lockservice : Successfully released change log lock
INFO  o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port 8080
```

### Step 5: Verify Database

```bash
# Connect to database
make db-connect-dev
```

**Run verification queries:**

```sql
-- Check Liquibase tracking table
SELECT id, author, filename, orderexecuted, exectype 
FROM databasechangelog 
ORDER BY orderexecuted;

-- Check tables were created
\dt

-- Expected output:
-- public | databasechangelog      | table | dev_ecm
-- public | databasechangeloglock  | table | dev_ecm
-- public | sample_roles           | table | dev_ecm
-- public | sample_user_roles      | table | dev_ecm
-- public | sample_users           | table | dev_ecm

-- Check data was seeded (dev context)
SELECT * FROM sample_roles;
-- Expected: 3 rows (ROLE_ADMIN, ROLE_USER, ROLE_DEVELOPER)

SELECT * FROM sample_users;
-- Expected: 3 rows (dev_admin, dev_user, test_user)

SELECT u.username, r.name 
FROM sample_users u
JOIN sample_user_roles ur ON u.id = ur.user_id
JOIN sample_roles r ON r.id = ur.role_id;
-- Expected: 3 rows showing user-role assignments

-- Exit psql
\q
```

---

## Profile Selection Guide

| Profile | Use Case | Database | Seed Data | Command |
|---------|----------|----------|-----------|---------|
| **local** | Your laptop | localhost:5432 | dev + local | `./gradlew bootRun --args='--spring.profiles.active=local'` |
| **dev** | Shared dev server | dev-db-server:5432 | dev only | `make run-dev` |
| **uat** | UAT testing | uat-db-server:5432 | uat only | `make run-uat` (needs UAT_DB_PASSWORD) |
| **prod** | Production | prod-db-server:5432 | prod only | `make run-prod` (needs PROD_DB_PASSWORD) |

---

## Context Behavior

With `spring.liquibase.contexts=local,dev`:

- ✅ **Runs changesets with:** `context:dev`
- ✅ **Runs changesets with:** `context:local`
- ✅ **Runs changesets with:** NO context (always runs)
- ❌ **Skips changesets with:** `context:uat`
- ❌ **Skips changesets with:** `context:prod`

**Your seed data execution:**
- Schema changes (no context) → ✅ **Always run**
- Dev seed data (`context:dev`) → ✅ **Runs on local/dev profiles**
- UAT seed data (`context:uat`) → ❌ **Skipped on local/dev**
- Prod seed data (`context:prod`) → ❌ **Skipped on local/dev**

---

## Understanding Execution Order

**Important:** Liquibase determines execution order by following `--include` statements sequentially, **NOT by file names or changeset IDs**.

### How Liquibase Processes Changelogs

```
1. Read db.changelog-master.sql
2. Find first --include: db.changelog-v1.0.sql
3. Open db.changelog-v1.0.sql
4. Process includes top-to-bottom:
   → changes/v1.0/0001-create-sample-users-table.sql
   → changes/v1.0/0002-create-sample-roles-table.sql
   → changes/v1.0/0003-create-sample-user-roles-table.sql
   → data/dev/v1.0/0001-seed-dev-roles.sql
   → data/dev/v1.0/0002-seed-dev-users.sql
   → ... etc
5. When done with v1.0, return to master
6. Find next --include (v2.0 if uncommented)
7. Repeat process...
```

### What Determines Order?

✅ **Order IS determined by:**
- Sequential position of `--include` statements (top to bottom)
- Nesting level (follows includes recursively)

❌ **Order is NOT determined by:**
- File names (`0001`, `0002` are for human readability)
- Changeset IDs (`v1.0-0001` is for uniqueness, not order)
- Alphabetical sorting
- File modification dates

### Example

Even if you wrote this (wrong order):

```sql
-- db.changelog-v1.0.sql (WRONG!)
--include file:db/changelog/changes/v1.0/0003-create-sample-user-roles-table.sql
--include file:db/changelog/changes/v1.0/0001-create-sample-users-table.sql
```

Liquibase would try to create `sample_user_roles` first and **fail** because the referenced tables don't exist yet.

**Remember:** File numbering helps YOU keep includes in correct order, but Liquibase only follows the sequence you write.

---

## Troubleshooting

### Issue: "Table already exists" error

**Cause:** Running migrations twice or DDL auto-create enabled

**Solution:**
```properties
# Make sure in application.properties:
spring.jpa.hibernate.ddl-auto=none
```

### Issue: Checksum validation failed

**Cause:** Modified an already-executed changeset

**Solutions:**

1. **Never modify executed changesets!** Create a new one instead.
2. If absolutely necessary (dev only):
```bash
make db-clear  # Clear checksums
```

### Issue: Wrong execution order

**Cause:** `--include` statements in wrong order

**Solution:** Reorder includes in master changelog files to match dependencies

### Issue: Context not working (data not seeded)

**Cause:** Wrong profile or context configuration

**Check:**
```properties
# In application-local.properties
spring.liquibase.contexts=local,dev
```

**Verify:**
```sql
SELECT * FROM databasechangelog WHERE contexts IS NOT NULL;
```

### Issue: Docker database not accessible

**Check:**
```bash
docker ps | grep postgres  # Should show running container
make docker-logs           # Check for errors
```

**Restart:**
```bash
make docker-down
make docker-up
```

### Issue: Liquibase not running

**Check application startup logs for:**
```
INFO liquibase.changelog : Reading from public.databasechangelog
```

If missing, verify:
```properties
spring.liquibase.enabled=true
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.sql
```

---

## Adding a New Version (v2.0)

### Step 1: Create version directory structure

```bash
mkdir -p src/main/resources/db/changelog/changes/v2.0
mkdir -p src/main/resources/db/changelog/data/{dev,uat,prod}/v2.0
```

### Step 2: Create changesets

```sql
-- changes/v2.0/0001-add-email-verification.sql
--liquibase formatted sql

--changeset tea0112:v2.0-0001-add-email-verification
--comment: Add email verification functionality
ALTER TABLE sample_users 
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS email_verification_token VARCHAR(255),
    ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP;

--rollback ALTER TABLE sample_users DROP COLUMN IF EXISTS email_verified, DROP COLUMN IF EXISTS email_verification_token, DROP COLUMN IF EXISTS email_verified_at;
```

### Step 3: Create v2.0 master

```sql
-- db.changelog-v2.0.sql
--liquibase formatted sql

--changeset system:v2.0-master runOnChange:false
--comment: Version 2.0 master - email verification and 2FA

-- Schema changes
--include file:db/changelog/changes/v2.0/0001-add-email-verification.sql
--include file:db/changelog/changes/v2.0/0002-add-2fa-support.sql

-- Seed data
--include file:db/changelog/data/dev/v2.0/0001-seed-dev-v2-data.sql
```

### Step 4: Update main master (just 1 line!)

```sql
-- db.changelog-master.sql

-- Version 2.0 Release
--include file:db/changelog/db.changelog-v2.0.sql
```

**Done!** v2.0 is now ready to deploy.

---

## Sample Changelog Files

### Schema Change Example

```sql
--liquibase formatted sql

--changeset tea0112:v1.0-0001-create-sample-users-table
--comment: Create sample_users table with basic authentication fields
CREATE TABLE sample_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT true,
    account_locked BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100) DEFAULT 'system'
);

-- Create indexes for performance
CREATE INDEX idx_sample_users_username ON sample_users(username);
CREATE INDEX idx_sample_users_email ON sample_users(email);
CREATE INDEX idx_sample_users_enabled ON sample_users(enabled);

-- Add comment to table
COMMENT ON TABLE sample_users IS 'Sample users table for testing - contains user authentication and profile data';

--rollback DROP TABLE IF EXISTS sample_users CASCADE;
```

### Seed Data Example

```sql
--liquibase formatted sql

--changeset tea0112:v1.0-0001-seed-dev-roles context:dev
--comment: Insert default roles for development environment
INSERT INTO sample_roles (name, description) VALUES 
    ('ROLE_ADMIN', 'Administrator with full access'),
    ('ROLE_USER', 'Standard user with limited access'),
    ('ROLE_DEVELOPER', 'Developer with extended permissions for testing')
ON CONFLICT (name) DO NOTHING;

--rollback DELETE FROM sample_roles WHERE name IN ('ROLE_ADMIN', 'ROLE_USER', 'ROLE_DEVELOPER');
```

### Complex Change with Preconditions

```sql
--liquibase formatted sql

--changeset tea0112:v1.0-0003-create-sample-user-roles-table
--comment: Create sample_user_roles junction table for many-to-many relationship
--preconditions onFail:HALT onError:HALT
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='sample_users'
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='sample_roles'

CREATE TABLE sample_user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    assigned_by VARCHAR(100) DEFAULT 'system',
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_sample_user_roles_user FOREIGN KEY (user_id) 
        REFERENCES sample_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_sample_user_roles_role FOREIGN KEY (role_id) 
        REFERENCES sample_roles(id) ON DELETE CASCADE
);

-- Create indexes for foreign keys
CREATE INDEX idx_sample_user_roles_user_id ON sample_user_roles(user_id);
CREATE INDEX idx_sample_user_roles_role_id ON sample_user_roles(role_id);

--rollback DROP TABLE IF EXISTS sample_user_roles CASCADE;
```

---

## Resources

- [Liquibase Documentation](https://docs.liquibase.com/)
- [Liquibase SQL Format](https://docs.liquibase.com/concepts/changelogs/sql-format.html)
- [Spring Boot Liquibase Integration](https://docs.spring.io/spring-boot/docs/current/reference/html/howto.html#howto.data-initialization.migration-tool.liquibase)
- [PostgreSQL 18 Documentation](https://www.postgresql.org/docs/18/index.html)

---

## Quick Reference Card

```bash
# Development Workflow
make docker-up              # Start DB
make build-skip-tests       # Build app
make run-dev                # Run app (auto-migrate)
make db-connect-dev         # Check database

# Liquibase Commands
make db-validate            # Validate changelogs
PROFILE=dev make db-status  # Check pending changes

# Cleanup
make docker-down            # Stop containers
make clean                  # Clean build

# Help
make help                   # Show all commands
```

**Default Credentials (Local):**
- **DB Host:** localhost:5432
- **Database:** ecm_identity_dev
- **Username:** dev_ecm
- **Password:** dev_ecm!23456

**Test Users (after v1.0 migration):**
- **Admin:** dev_admin / password
- **User:** dev_user / password
- **Tester:** test_user / password

---

*Last Updated: 2025-10-12*
*Version: 1.0*
