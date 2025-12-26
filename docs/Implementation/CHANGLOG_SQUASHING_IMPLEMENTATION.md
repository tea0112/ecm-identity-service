# Liquibase Changelog Squashing Implementation Guide

## Overview

This document provides a comprehensive guide for implementing a squashing strategy to maintain the readability and performance of your Liquibase changelog as it grows over time. This approach preserves your current architecture while preventing the master file from becoming unwieldy.

## Problem Statement

As your application evolves, the `db.changelog-master.xml` file will accumulate numerous includes, leading to:
- Reduced readability and maintainability
- Slower deployment times
- Difficulty in understanding the overall database structure
- Increased risk of merge conflicts in version control

## Solution: Incremental Squashing Strategy

This strategy maintains your current architecture while implementing periodic squashing to keep the master file manageable.

## Architecture Preservation

Your current structure will be preserved:
```
src/main/resources/db/changelog/
├── db.changelog-master.xml (main entry point)
├── changes/ (schema changes)
├── data/ (environment-specific data)
│   ├── dev/
│   ├── uat/
│   └── prod/
└── squash/ (new directory for squashed changes)
```

## Squashing Triggers

Implement squashing when any of these conditions are met:

### 1. File Size Threshold
- Master file exceeds 500 lines
- Master file exceeds 50KB in size

### 2. Include Count Threshold
- More than 30-40 individual includes in master file
- More than 50 total changesets

### 3. Time-Based Triggers
- Every 6 months
- After each major/minor version release
- Before major refactoring efforts

### 4. Performance-Based Triggers
- Deployment time increases significantly
- Liquibase processing becomes slow

## Implementation Steps

### Step 1: Create Squash Directory Structure

```bash
mkdir -p src/main/resources/db/changelog/squash/v1.0.0/changes
mkdir -p src/main/resources/db/changelog/squash/v1.0.0/data/dev
mkdir -p src/main/resources/db/changelog/squash/v1.0.0/data/uat
mkdir -p src/main/resources/db/changelog/squash/v1.0.0/data/prod
```

### Step 2: Create Squash Version Structure

```
squash/v1.0.0/
├── schema-squash.sql          # Combined schema changes
├── data-squash.sql           # Combined data changes
├── squash-changelog.xml      # Liquibase changelog for squash
├── README.md                 # Squash documentation
├── changes/                  # Archived original changes
│   ├── 20251012100000-create-users.sql
│   ├── 20251012100001-create-roles.sql
│   └── ...
└── data/                     # Archived original data
    ├── dev/20251012103000-seed-roles.sql
    └── ...
```

### Step 3: Generate Optimized Squash Scripts

#### Schema Squash Script (`schema-squash.sql`)

```sql
--liquibase formatted sql

--changeset system:v1.0.0-schema-squash
--comment: Squashed schema changes for version 1.0.0
--rollback: Individual rollback scripts available in squash/v1.0.0/changes/

-- Create sample_users table with basic authentication fields
CREATE TABLE sample_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT true,
    account_locked BOOLEAN NOT NULL DEFAULT false,
    account_expired BOOLEAN NOT NULL DEFAULT false,
    credentials_expired BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100) DEFAULT 'system'
);

-- Create indexes for performance
CREATE INDEX idx_sample_users_username ON sample_users(username);
CREATE INDEX idx_sample_users_email ON sample_users(email);
CREATE INDEX idx_sample_users_enabled ON sample_users(enabled);
CREATE INDEX idx_sample_users_created_at ON sample_users(created_at);

-- Create sample_roles table for role-based access control
CREATE TABLE sample_roles (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) DEFAULT 'system',
    updated_by VARCHAR(100) DEFAULT 'system'
);

-- Create index for performance
CREATE INDEX idx_sample_roles_name ON sample_roles(name);

-- Create sample_user_roles junction table for many-to-many relationship
CREATE TABLE sample_user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    granted_by VARCHAR(100) DEFAULT 'system',
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES sample_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES sample_roles(id) ON DELETE CASCADE
);

-- Create indexes for foreign keys
CREATE INDEX idx_sample_user_roles_user_id ON sample_user_roles(user_id);
CREATE INDEX idx_sample_user_roles_role_id ON sample_user_roles(role_id);
```

#### Data Squash Script (`data-squash.sql`)

```sql
--liquibase formatted sql

--changeset system:v1.0.0-data-squash
--comment: Squashed data changes for version 1.0.0
--rollback: Individual rollback scripts available in squash/v1.0.0/data/

-- Insert standard roles for all environments - master/reference data
-- Only insert if the role doesn't already exist
INSERT INTO sample_roles (name, description) 
SELECT 'ROLE_ADMIN', 'Administrator with full access'
WHERE NOT EXISTS (SELECT 1 FROM sample_roles WHERE name = 'ROLE_ADMIN');

INSERT INTO sample_roles (name, description) 
SELECT 'ROLE_USER', 'Standard user with limited access'
WHERE NOT EXISTS (SELECT 1 FROM sample_roles WHERE name = 'ROLE_USER');

INSERT INTO sample_roles (name, description) 
SELECT 'ROLE_MANAGER', 'Manager with team management permissions'
WHERE NOT EXISTS (SELECT 1 FROM sample_roles WHERE name = 'ROLE_MANAGER');

INSERT INTO sample_roles (name, description) 
SELECT 'ROLE_DEVELOPER', 'Developer with extended permissions for testing'
WHERE NOT EXISTS (SELECT 1 FROM sample_roles WHERE name = 'ROLE_DEVELOPER');
```

### Step 4: Update Master File

Replace individual includes with squash includes:

```xml
<--from git+https://github.com/oraios/serena Before squash (many lines) -->
<include file="changes/20251012100000-create-users.sql"/>
<include file="changes/20251012100001-create-roles.sql"/>
<include file="changes/20251012100002-create-user-roles.sql"/>
<include file="data/dev/20251012103000-seed-roles.sql"/>
<include file="data/dev/20251012103001-seed-dev-test-data.sql"/>
<include file="data/dev/20251012103002-seed-dev-users.sql"/>
<--from git+https://github.com/oraios/serena ... many more includes ... -->

<--from git+https://github.com/oraios/serena After squash (few lines) -->
<include file="squash/v1.0.0/schema-squash.sql"/>
<include file="squash/v1.0.0/data-squash.sql"/>
<include file="changes/20251201100000-new-feature.sql"/>
<include file="changes/20251202100001-another-feature.sql"/>
<--from git+https://github.com/oraios/serena ... new changes continue ... -->
```

### Step 5: Archive Original Changes

Move original change files to squash directory:

```bash
# Archive schema changes
mv src/main/resources/db/changelog/changes/20251012100000-create-users.sql src/main/resources/db/changelog/squash/v1.0.0/changes/
mv src/main/resources/db/changelog/changes/20251012100001-create-roles.sql src/main/resources/db/changelog/squash/v1.0.0/changes/
mv src/main/resources/db/changelog/changes/20251012100002-create-user-roles.sql src/main/resources/db/changelog/squash/v1.0.0/changes/

# Archive data changes
mv src/main/resources/db/changelog/data/dev/20251012103000-seed-roles.sql src/main/resources/db/changelog/squash/v1.0.0/data/dev/
mv src/main/resources/db/changelog/data/dev/20251012103001-seed-dev-test-data.sql src/main/resources/db/changelog/squash/v1.0.0/data/dev/
mv src/main/resources/db/changelog/data/dev/20251012103002-seed-dev-users.sql src/main/resources/db/changelog/squash/v1.0.0/data/dev/
# ... move other environment data files ...
```

## Automation Scripts

### Squash Script (`scripts/squash-changelog.sh`)

```bash
#!/bin/bash

# Liquibase Changelog Squashing Script
# Usage: ./scripts/squash-changelog.sh <version>

set -e

VERSION=${1:-"v1.0.0"}
CHANGLOG_DIR="src/main/resources/db/changelog"
SQUASH_DIR="$CHANGLOG_DIR/squash/$VERSION"
TEMP_DIR="/tmp/liquibase-squash"

echo "Starting changelog squash for version $VERSION..."

# Create directories
mkdir -p "$SQUASH_DIR/changes"
mkdir -p "$SQUASH_DIR/data/dev"
mkdir -p "$SQUASH_DIR/data/uat"
mkdir -p "$SQUASH_DIR/data/prod"
mkdir -p "$TEMP_DIR"

# Function to combine SQL files
combine_sql_files() {
    local source_dir="$1"
    local output_file="$2"
    local comment="$3"
    
    echo "--liquibase formatted sql" > "$output_file"
    echo "" >> "$output_file"
    echo "--changeset system:${VERSION}-$(basename "$output_file" .sql)" >> "$output_file"
    echo "--comment: $comment" >> "$output_file"
    echo "--rollback: Individual rollback scripts available in squash/$VERSION/changes/" >> "$output_file"
    echo "" >> "$output_file"
    
    for file in "$source_dir"/*.sql; do
        if [ -f "$file" ]; then
            echo "-- From: $(basename "$file")" >> "$output_file"
            # Remove Liquibase headers from individual files
            sed -e '/^--liquibase formatted sql$/d' \
                -e '/^--changeset /d' \
                -e '/^--comment:/d' \
                -e '/^--rollback:/d' \
                "$file" >> "$output_file"
            echo "" >> "$output_file"
        fi
    done
}

# Combine schema changes
echo "Combining schema changes..."
combine_sql_files "$CHANGLOG_DIR/changes" "$TEMP_DIR/schema-squash.sql" "Squashed schema changes for version $VERSION"

# Combine data changes
echo "Combining data changes..."
combine_sql_files "$CHANGLOG_DIR/data/dev" "$TEMP_DIR/data-squash.sql" "Squashed data changes for version $VERSION"

# Move combined files to squash directory
mv "$TEMP_DIR/schema-squash.sql" "$SQUASH_DIR/"
mv "$TEMP_DIR/data-squash.sql" "$SQUASH_DIR/"

# Archive original files
echo "Archiving original files..."
mv "$CHANGLOG_DIR/changes"/* "$SQUASH_DIR/changes/" 2>/dev/null || true
mv "$CHANGLOG_DIR/data/dev"/* "$SQUASH_DIR/data/dev/" 2>/dev/null || true
mv "$CHANGLOG_DIR/data/uat"/* "$SQUASH_DIR/data/uat/" 2>/dev/null || true
mv "$CHANGLOG_DIR/data/prod"/* "$SQUASH_DIR/data/prod/" 2>/dev/null || true

# Update master file
echo "Updating master changelog file..."
cat > "$TEMP_DIR/new-master.xml" << 'MASTEREOF'
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <--from git+https://github.com/oraios/serena Squashed changes for version VERSION_PLACEHOLDER -->
    <include file="squash/VERSION_PLACEHOLDER/schema-squash.sql"/>
    <include file="squash/VERSION_PLACEHOLDER/data-squash.sql"/>

    <--from git+https://github.com/oraios/serena Future changes -->
    <--from git+https://github.com/oraios/serena <include file="changes/YYYYMMDDHHMMSS-next-change.sql"/> -->
    <--from git+https://github.com/oraios/serena <include file="data/dev/YYYYMMDDHHMMSS-next-dev-data.sql"/> -->
    <--from git+https://github.com/oraios/serena <include file="data/uat/YYYYMMDDHHMMSS-next-uat-data.sql"/> -->
    <--from git+https://github.com/oraios/serena <include file="data/prod/YYYYMMDDHHMMSS-next-prod-data.sql"/> -->

</databaseChangeLog>
MASTEREOF

# Replace placeholder with actual version
sed "s/VERSION_PLACEHOLDER/$VERSION/g" "$TEMP_DIR/new-master.xml" > "$CHANGLOG_DIR/db.changelog-master.xml"

# Create README
cat > "$SQUASH_DIR/README.md" << EOF
# Liquibase Changelog Squash - Version $VERSION

This directory contains squashed changes for version $VERSION.

## Contents

- `schema-squash.sql` - Combined schema changes
- `data-squash.sql` - Combined data changes  
- `changes/` - Archived original schema change files
- `data/` - Archived original data change files

## Squashed Changes

The following changes were combined into this squash:

### Schema Changes
$(ls -la "$SQUASH_DIR/changes/" | grep "\.sql$" | awk '{print "- " $9}')

### Data Changes
$(ls -la "$SQUASH_DIR/data/dev/" | grep "\.sql$" | awk '{print "- dev/" $9}')
$(ls -la "$SQUASH_DIR/data/uat/" | grep "\.sql$" | awk '{print "- uat/" $9}')
$(ls -la "$SQUASH_DIR/data/prod/" | grep "\.sql$" | awk '{print "- prod/" $9}')

## Rollback

If rollback is needed, individual rollback scripts are available in the archived files.

## Notes

- This squash was created on $(date)
- Original master file was updated to reference this squash
- New changes should be added to the master file after the squash includes
