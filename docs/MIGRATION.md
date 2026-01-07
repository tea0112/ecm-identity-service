# Database Migration Architecture: Timestamp-Based Organization

## Overview

This document provides a comprehensive guide for implementing and maintaining a timestamp-based database changelog structure using Liquibase. This architecture provides improved team collaboration, chronological tracking, and deployment flexibility for database migrations.

## Architecture Design

### Core Principles

1. **Timestamp-Based Organization**: All database changes are organized using YYYYMMDDHHMMSS format for clear chronological ordering
2. **Environment Independence**: Each environment (dev, uat, prod) maintains its own data files for better control and isolation
3. **Direct File Inclusion**: Master changelog includes timestamped files directly without intermediate version files
4. **Cross-Database Compatibility**: SQL syntax designed to work across different database systems

### Directory Structure

```
src/main/resources/db/changelog/
├── db.changelog-master.xml              # Main entry point
├── changes/                              # Schema changes with timestamps
│   ├── 20251012100000-create-sample-users-table.sql
│   ├── 20251012100001-create-sample-roles-table.sql
│   └── 20251012100002-create-sample-user-roles-table.sql
└── data/                                # Environment-specific data
    ├── dev/                             # Development environment
    │   ├── 20251012103000-seed-dev-roles.sql
    │   ├── 20251012103001-seed-dev-test-data.sql
    │   └── 20251012103002-seed-dev-users.sql
    ├── uat/                             # UAT environment
    │   ├── 20251012103003-seed-uat-roles.sql
    │   ├── 20251012103004-seed-uat-test-data.sql
    │   └── 20251012103005-seed-uat-users.sql
    └── prod/                            # Production environment
        ├── 20251012103006-seed-prod-roles.sql
        └── 20251012103007-seed-prod-initial-data.sql
```

## Implementation Flow

### 1. File Naming Convention

**Format**: `YYYYMMDDHHMMSS-description.sql`

**Examples**:
- `20251012100000-create-sample-users-table.sql`
- `20251012100001-create-sample-roles-table.sql`
- `20251012103000-seed-dev-roles.sql`

**Benefits**:
- Natural chronological ordering
- Unique filenames prevent Git conflicts
- Clear identification of when changes were created
- Easy to locate specific changes

### 2. Changeset Identifier Format

**Format**: `author:YYYYMMDDHHMMSS-description`

**Examples**:
- `tea0112:20251012100000-create-sample-users-table`
- `tea0112:20251012103000-seed-dev-roles`

**Benefits**:
- Clear tracking of changes by author and timestamp
- Prevents conflicts between team members
- Easy to identify the source of specific changes

### 3. Master Changelog Structure

The master changelog (`db.changelog-master.xml`) includes files in chronological order:

```xml
<databaseChangeLog>
    <--from git+https://github.com/oraios/serena Schema Changes -->
    <include file="db/changelog/changes/20251012100000-create-sample-users-table.sql"/>
    <include file="db/changelog/changes/20251012100001-create-sample-roles-table.sql"/>
    <include file="db/changelog/changes/20251012100002-create-sample-user-roles-table.sql"/>

    <--from git+https://github.com/oraios/serena Environment-Specific Seed Data -->
    <include file="db/changelog/data/dev/20251012103000-seed-dev-roles.sql"/>
    <include file="db/changelog/data/dev/20251012103001-seed-dev-test-data.sql"/>
    <include file="db/changelog/data/dev/20251012103002-seed-dev-users.sql"/>
    <--from git+https://github.com/oraios/serena ... more environment data files ... -->
</databaseChangeLog>
```

## Environment Strategy

### Environment-Specific Data Organization

Each environment has its own data directory with timestamped files:

**Development Environment** (`data/dev/`):
- Test users and roles for development
- Development-specific test data
- Non-production data that can be safely modified

**UAT Environment** (`data/uat/`):
- UAT-specific test scenarios
- Integration testing data
- Pre-production validation data

**Production Environment** (`data/prod/`):
- Minimal initial production data
- Critical reference data only
- Carefully controlled data changes

### Master Data Strategy

**Environment-Specific Master Data**: Instead of a shared master directory, each environment maintains its own copy of reference data (like roles). This provides:

- **Environment Independence**: Each environment can have different reference data as needed
- **Deployment Control**: Clear control over what data exists in each environment
- **Future Flexibility**: Easy to add environment-specific variations

## SQL Compatibility Standards

### Cross-Database Compatible Syntax

**Preferred Approach** (Cross-database compatible):
```sql
-- Only insert if the role doesn't already exist
INSERT INTO roles (name, description) 
SELECT 'ROLE_ADMIN', 'Administrator with full access'
WHERE NOT EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_ADMIN');
```

**Avoid** (Database-specific):
```sql
-- PostgreSQL-specific syntax
INSERT INTO roles (name, description) VALUES 
    ('ROLE_ADMIN', 'Administrator with full access')
ON CONFLICT (name) DO NOTHING;
```

**Benefits**:
- Works across PostgreSQL, MySQL, Oracle, SQL Server
- No database-specific dependencies
- Easier to switch databases if needed

## Development Workflow

### Creating New Database Changes

1. **Generate Timestamp**: Use current date/time in YYYYMMDDHHMMSS format
2. **Create Schema File**: For structural changes (tables, indexes, constraints)
3. **Create Data Files**: For environment-specific seed data
4. **Update Master Changelog**: Include new files in chronological order
5. **Test Across Environments**: Verify changes work in all environments

### Example: Adding a New Feature

```bash
# Step 1: Generate timestamp (e.g., 20251226150000)
# Step 2: Create schema change
cat > changes/20251226150000-add-new-feature.sql << 'SQL'
--liquibase formatted sql
--changeset tea0112:20251226150000-add-new-feature
--comment: Add new feature table
CREATE TABLE new_feature (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
SQL

# Step 3: Create environment data (if needed)
cat > data/dev/20251226153000-seed-dev-new-feature.sql << 'SQL'
--liquibase formatted sql
--changeset tea0112:20251226153000-seed-dev-new-feature context:dev
--comment: Add development test data for new feature
INSERT INTO new_feature (name) VALUES ('Development Feature');
SQL

# Step 4: Update master changelog
echo "<include file=\"db/changelog/changes/20251226150000-add-new-feature.sql\"/>" >> db.changelog-master.xml
echo "<include file=\"db/changelog/data/dev/20251226153000-seed-dev-new-feature.sql\"/>" >> db.changelog-master.xml
```

## Integration with Build System

### Liquibase Gradle Configuration

The timestamp-based structure integrates seamlessly with the existing Liquibase Gradle configuration:

```gradle
liquibase {
    activities {
        local {
            changelogFile 'db/changelog/db.changelog-master.xml'  // Points to updated master
            url System.getenv('DB_URL') ?: 'jdbc:postgresql://localhost:5432/ecm_identity_dev'
            username System.getenv('DB_USERNAME') ?: 'dev_ecm'
            password System.getenv('DB_PASSWORD') ?: 'dev_ecm!23456'
            driver 'org.postgresql.Driver'
            contexts 'dev'
            // ... other settings
        }
        // ... other environments remain unchanged
    }
}
```

### Environment-Specific Execution

Each environment uses the same master changelog but with different context filtering:

```bash
# Local development
export $(cat .env.local | xargs)
./gradlew update

# Development server
export $(cat .env.dev | xargs)
./gradlew update -PrunList=dev

# UAT
export $(cat .env.uat | xargs)
./gradlew update -PrunList=uat

# Production
export $(cat .env.prod | xargs)
./gradlew update -PrunList=prod
```

## Verification and Testing

### File Structure Validation

```bash
cd src/main/resources/db/changelog
find . -name "*.sql" -o -name "*.xml" | sort
```

Expected output shows all files in chronological order:
```
./changes/20251012100000-create-sample-users-table.sql
./changes/20251012100001-create-sample-roles-table.sql
./changes/20251012100002-create-sample-user-roles-table.sql
./data/dev/20251012103000-seed-dev-roles.sql
./data/dev/20251012103001-seed-dev-test-data.sql
./data/dev/20251012103002-seed-dev-users.sql
./data/prod/20251012103006-seed-prod-roles.sql
./data/prod/20251012103007-seed-prod-initial-data.sql
./data/uat/20251012103003-seed-uat-roles.sql
./data/uat/20251012103004-seed-uat-test-data.sql
./data/uat/20251012103005-seed-uat-users.sql
./db.changelog-master.xml
```

### Changeset Identifier Verification

```bash
head -5 changes/20251012100000-create-sample-users-table.sql
```

Expected output:
```
--liquibase formatted sql

--changeset tea0112:20251012100000-create-sample-users-table
--comment: Create users table with basic authentication fields
CREATE TABLE users (
```

### Liquibase Execution Testing

```bash
# Test with local environment
export $(cat .env.local | xargs)
./gradlew update
```

## Benefits of Timestamp-Based Architecture

### 1. Team Collaboration
- **No Git Conflicts**: Unique timestamps ensure no filename conflicts
- **Parallel Development**: Multiple developers can work on changes simultaneously
- **Clear Attribution**: Changeset identifiers show author and timestamp

### 2. Chronological Tracking
- **Natural Ordering**: Files are naturally ordered by creation time
- **Easy Auditing**: Clear timeline of database changes
- **Deployment Tracking**: Easy to see what changes were deployed when

### 3. Environment Management
- **Independent Control**: Each environment controls its own data
- **Clear Separation**: No confusion about which data belongs where
- **Flexible Deployment**: Different environments can have different data

### 4. Operational Excellence
- **CI/CD Ready**: Simplified automation and deployment
- **Rollback Capability**: Easy to identify and rollback specific changes
- **Monitoring**: Clear metrics on database change frequency and patterns

## Troubleshooting

### Common Issues

1. **Liquibase Execution Fails**
   - Check environment variables are set correctly
   - Verify database connectivity
   - Ensure all required files exist in the correct paths

2. **Changeset Identifier Conflicts**
   - Ensure unique timestamps for each change
   - Check for duplicate changeset identifiers
   - Verify author names are consistent

3. **File Not Found Errors**
   - Verify file paths in master changelog are correct
   - Check file permissions
   - Ensure files are committed to version control

4. **Environment-Specific Issues**
   - Verify context filtering is working correctly
   - Check that environment-specific data files exist
   - Ensure environment variables are properly set

### Getting Help

- **Liquibase Documentation**: https://docs.liquibase.com/
- **Spring Boot Liquibase Guide**: https://docs.spring.io/spring-boot/docs/current/reference/html/data.html\#data.sql-initialization.liquibase
- **Database Compatibility**: Check specific database documentation for syntax requirements

## Maintenance Guidelines

### Regular Tasks

1. **Monitor File Growth**: Keep changelog files manageable in size
2. **Review Timestamps**: Ensure timestamps are being used consistently
3. **Validate Environment Data**: Regularly verify environment-specific data is correct
4. **Test Across Environments**: Regular testing in all environments

### Best Practices

1. **Use Descriptive Names**: File names should clearly describe what the change does
2. **Maintain Chronological Order**: Always add new files in timestamp order
3. **Test Before Committing**: Always test changes in a local environment first
4. **Document Complex Changes**: Use comments to explain complex or non-obvious changes
5. **Plan Rollback Strategy**: Consider how to rollback each change if needed

## Conclusion

The timestamp-based database changelog architecture provides a robust, scalable, and maintainable solution for managing database changes in a multi-environment development workflow. By following the patterns and guidelines outlined in this document, teams can ensure consistent, reliable database migrations that support agile development practices while maintaining data integrity and operational stability.

This architecture is designed to grow with the project, supporting future requirements while maintaining simplicity and clarity in database change management.
