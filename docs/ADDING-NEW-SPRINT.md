# Quick Guide: Adding Database Changes for a New Sprint

This guide shows how to add database migrations for a new sprint using the sprint-based structure.

## Step 1: Create Sprint Directories

Replace `XX` with your sprint number (e.g., `02`, `03`, etc.)

```bash
# From project root
cd src/main/resources/db/changelog

# Create all directories at once
mkdir -p changes/sprint-XX \
         data/master/sprint-XX \
         data/dev/sprint-XX \
         data/uat/sprint-XX \
         data/prod/sprint-XX
```

## Step 2: Add Your Migration Files

### Schema Changes (goes in `changes/sprint-XX/`)

Create files like: `0001-add-user-profile-table.sql`

```sql
--liquibase formatted sql

--changeset yourname:sprint-XX-0001-add-user-profile-table
--comment: Add user_profile table for Sprint XX feature
CREATE TABLE user_profile (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES sample_users(id),
    bio TEXT,
    avatar_url VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_user_profile_user_id ON user_profile(user_id);

--rollback DROP TABLE IF EXISTS user_profile CASCADE;
```

### Master Data (goes in `data/master/sprint-XX/`)

Only if you have reference data that MUST be the same in all environments:

```sql
--liquibase formatted sql

--changeset yourname:sprint-XX-0001-seed-profile-types
--comment: Insert profile type reference data
INSERT INTO profile_types (code, name) VALUES 
    ('PERSONAL', 'Personal Profile'),
    ('BUSINESS', 'Business Profile')
ON CONFLICT (code) DO NOTHING;

--rollback DELETE FROM profile_types WHERE code IN ('PERSONAL', 'BUSINESS');
```

### Environment-Specific Data (goes in `data/{env}/sprint-XX/`)

For dev/uat/prod test data:

```sql
--liquibase formatted sql

--changeset yourname:sprint-XX-0001-seed-dev-profiles context:dev
--comment: Insert test profiles for development
INSERT INTO user_profile (user_id, bio) VALUES 
    (1, 'Test user bio for development')
ON CONFLICT DO NOTHING;

--rollback DELETE FROM user_profile WHERE user_id = 1;
```

## Step 3: Create Sprint Changelog XML

Create `versions/db.changelog-sprint-XX.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <!-- ============================================ -->
    <!-- SPRINT XX CHANGELOG -->
    <!-- ============================================ -->
    <!-- Feature: User Profile Management -->
    <!-- Sprint Date: YYYY-MM-DD -->

    <!-- Schema Changes - Sprint XX -->
    <include file="db/changelog/changes/sprint-XX/0001-add-user-profile-table.sql"/>
    <include file="db/changelog/changes/sprint-XX/0002-add-profile-settings-table.sql"/>

    <!-- Master/Reference Data - Sprint XX (runs in ALL environments) -->
    <include file="db/changelog/data/master/sprint-XX/0001-seed-profile-types.sql"/>

    <!-- Environment-Specific Seed Data - Sprint XX -->
    <include file="db/changelog/data/dev/sprint-XX/0001-seed-dev-profiles.sql"/>
    <include file="db/changelog/data/uat/sprint-XX/0001-seed-uat-profiles.sql"/>
    <include file="db/changelog/data/prod/sprint-XX/0001-seed-prod-initial-profiles.sql"/>

</databaseChangeLog>
```

## Step 4: Update Master Changelog

Edit `db.changelog-master.xml` and uncomment/add your sprint:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
        xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <!-- Sprint 01 -->
    <include file="db/changelog/versions/db.changelog-sprint-01.xml"/>

    <!-- Sprint 02 - UNCOMMENT THIS LINE -->
    <include file="db/changelog/versions/db.changelog-sprint-02.xml"/>

    <!-- Future sprints -->
    <!-- <include file="db/changelog/versions/db.changelog-sprint-03.xml"/> -->

</databaseChangeLog>
```

## Step 5: Test Your Migration

```bash
# Test in local dev environment
./gradlew update -Pcontexts=dev

# Verify the changes
./gradlew status
```

## File Naming Convention

Use leading zeros for sequential numbering:

```
✅ GOOD:
0001-create-table.sql
0002-add-column.sql
0010-add-index.sql

❌ BAD:
1-create-table.sql
2-add-column.sql
10-add-index.sql
```

## Changeset ID Convention

Format: `yourname:sprint-XX-NNNN-description`

Examples:
- `tea0112:sprint-02-0001-create-profile-table`
- `john:sprint-03-0005-add-email-verification`
- `sarah:sprint-05-0002-seed-countries`

## Quick Checklist

Before committing your migration:

- [ ] Sprint directories created
- [ ] Migration files numbered sequentially (0001, 0002, etc.)
- [ ] Each changeset has unique ID with sprint number
- [ ] Master data has NO context attribute
- [ ] Environment data has correct context (dev/uat/prod)
- [ ] Rollback statements included
- [ ] Sprint changelog XML created
- [ ] Master changelog updated
- [ ] Tested with `./gradlew update -Pcontexts=dev`
- [ ] No syntax errors in SQL
- [ ] Comments explain the "why" not just the "what"

## Common Mistakes to Avoid

❌ **Don't** put environment-specific data in `/data/master/`  
✅ **Do** use `/data/{env}/` with proper context

❌ **Don't** forget the context attribute for env-specific data  
✅ **Do** add `context:dev`, `context:uat`, or `context:prod`

❌ **Don't** reuse changeset IDs from other files  
✅ **Do** make each changeset ID unique across all files

❌ **Don't** skip rollback statements  
✅ **Do** provide meaningful rollback SQL

❌ **Don't** put multiple sprints in same file  
✅ **Do** create separate sprint folder for each sprint

## Progressive Deployment Workflow

```bash
# Sprint XX completed → Deploy to DEV
./gradlew update -Pcontexts=dev
# Test in dev...

# After dev testing → Deploy to UAT
./gradlew update -Pcontexts=uat
# UAT testing...

# After UAT approval → Deploy to PROD
./gradlew update -Pcontexts=prod
```

## Need Help?

See the full documentation:
- [DATABASE-MIGRATION-STRUCTURE.md](./DATABASE-MIGRATION-STRUCTURE.md)
- [LIQUIBASE-GRADLE.md](./LIQUIBASE-GRADLE.md)
- [LIQUIBASE-ENVIRONMENTS.md](./LIQUIBASE-ENVIRONMENTS.md)
