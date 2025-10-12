# Database Migration Structure (Sprint-Based)

## Directory Organization

```
/db/changelog/
├── changes/                    # Schema changes (environment-agnostic)
│   ├── sprint-01/             # Sprint 01 schema changes
│   │   ├── 0001-create-sample-users-table.sql
│   │   ├── 0002-create-sample-roles-table.sql
│   │   └── 0003-create-sample-user-roles-table.sql
│   └── sprint-02/             # Sprint 02 schema changes (future)
│
├── data/
│   ├── master/                 # Master/Reference data (runs in ALL environments)
│   │   ├── sprint-01/
│   │   │   └── 0001-seed-master-roles.sql
│   │   └── sprint-02/         # Future sprints
│   │
│   ├── dev/                    # Dev-specific test data (context:dev)
│   │   ├── sprint-01/
│   │   │   ├── 0001-seed-dev-test-data.sql
│   │   │   └── 0002-seed-dev-users.sql
│   │   └── sprint-02/         # Future sprints
│   │
│   ├── uat/                    # UAT-specific test data (context:uat)
│   │   ├── sprint-01/
│   │   │   ├── 0001-seed-uat-test-data.sql
│   │   │   └── 0002-seed-uat-users.sql
│   │   └── sprint-02/         # Future sprints
│   │
│   └── prod/                   # Prod-specific initial data (context:prod)
│       ├── sprint-01/
│       │   └── 0001-seed-prod-initial-data.sql
│       └── sprint-02/         # Future sprints
│
├── versions/                   # Sprint-specific changelog files
│   ├── db.changelog-sprint-01.xml
│   └── db.changelog-sprint-02.xml  # Future sprints
│
└── db.changelog-master.xml     # Master changelog (includes all sprints)
```

## Execution Order

1. **Schema Changes** - Creates tables, indexes, constraints
2. **Master Data** - Inserts reference data (no context = runs everywhere)
3. **Environment-Specific Data** - Inserts test/initial data (filtered by context)

## Execution Example

```bash
./gradlew update -Pcontexts=dev
```

**What runs:**
- ✅ `0001-create-sample-users-table.sql` (no context)
- ✅ `0002-create-sample-roles-table.sql` (no context)
- ✅ `0003-create-sample-user-roles-table.sql` (no context)
- ✅ `0001-seed-master-roles.sql` (no context = runs in all envs)
- ✅ `0001-seed-dev-test-data.sql` (context:dev matches)
- ✅ `0002-seed-dev-users.sql` (context:dev matches)
- ❌ `0001-seed-uat-test-data.sql` (context:uat doesn't match)
- ❌ `0002-seed-uat-users.sql` (context:uat doesn't match)
- ❌ `0001-seed-prod-initial-data.sql` (context:prod doesn't match)

## Data Types

| Type | Location | Context | Description |
|------|----------|---------|-------------|
| **Schema** | `/changes/` | None | Table structures, same everywhere |
| **Master Data** | `/data/master/` | None | Reference data, same everywhere (e.g., standard roles, countries) |
| **Seed Data** | `/data/{env}/` | `context:dev/uat/prod` | Environment-specific test/initial data |

## Master Data Results

After running `./gradlew update`, the following master roles are available in **all environments**:

- `ROLE_ADMIN` - Administrator with full access
- `ROLE_USER` - Standard user with limited access
- `ROLE_MANAGER` - Manager with team management permissions
- `ROLE_DEVELOPER` - Developer with extended permissions for testing

## Adding New Master Data

1. Create file in `/data/master/sprint-XX/` (where XX is current sprint number)
2. **Do NOT add context** to the changeset
3. Add to `db.changelog-sprint-XX.xml` after schema changes
4. Run `./gradlew update` - will execute in all environments

Example:
```sql
--liquibase formatted sql

--changeset tea0112:sprint-02-0002-seed-countries
--comment: Insert country master data for all environments
INSERT INTO countries (code, name) VALUES 
    ('US', 'United States'),
    ('VN', 'Vietnam')
ON CONFLICT (code) DO NOTHING;
```

## Sprint-Based Development Workflow

### Adding Changes for a New Sprint

When starting Sprint 02, create the following structure:

```bash
# 1. Create sprint directories
mkdir -p db/changelog/changes/sprint-02
mkdir -p db/changelog/data/master/sprint-02
mkdir -p db/changelog/data/dev/sprint-02
mkdir -p db/changelog/data/uat/sprint-02
mkdir -p db/changelog/data/prod/sprint-02

# 2. Add your migration files
# db/changelog/changes/sprint-02/0001-add-email-verification.sql
# db/changelog/data/master/sprint-02/0001-seed-verification-statuses.sql

# 3. Create sprint changelog
# db/changelog/versions/db.changelog-sprint-02.xml

# 4. Include in master changelog
# Uncomment the sprint-02 include in db.changelog-master.xml
```

### Sprint Changelog Example (db.changelog-sprint-02.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
        http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.20.xsd">

    <!-- Sprint 02: Email Verification Feature -->
    <include file="db/changelog/changes/sprint-02/0001-add-email-verification.sql"/>
    <include file="db/changelog/data/master/sprint-02/0001-seed-verification-statuses.sql"/>
    <include file="db/changelog/data/dev/sprint-02/0001-seed-dev-test-accounts.sql"/>
</databaseChangeLog>
```

## Progressive Deployment (Dev → UAT → Prod)

The structure supports progressive deployment across environments:

```bash
# Sprint 02: Deploy to Dev
cd dev-environment
./gradlew update -Pcontexts=dev
# ✅ Sprint 01 changes applied (if not already)
# ✅ Sprint 02 schema changes applied
# ✅ Sprint 02 master data applied
# ✅ Sprint 02 dev test data applied

# After testing → Deploy to UAT
cd uat-environment
./gradlew update -Pcontexts=uat
# ✅ Same sprint 02 schema changes applied
# ✅ Same sprint 02 master data applied
# ✅ Sprint 02 UAT test data applied

# After UAT approval → Deploy to Prod
cd prod-environment
./gradlew update -Pcontexts=prod
# ✅ Same sprint 02 schema changes applied
# ✅ Same sprint 02 master data applied
# ✅ Sprint 02 prod initial data applied
```

**Key Point:** The **same migration files** flow through all environments. You don't create separate files per environment - the progressive deployment happens naturally as you run migrations in each environment sequentially.

## Decision Guide: Where to Put Data Changes?

### ✅ Put in `/data/master/` when:
Data **MUST be identical** across all environments (dev, uat, prod)

**Examples:**
- Standard roles (ROLE_ADMIN, ROLE_USER, ROLE_MANAGER)
- Country/currency codes
- System status codes (ACTIVE, INACTIVE, PENDING)
- Product categories
- Application configuration reference data
- Lookup table values

**Rule:** No `context` attribute - runs in ALL environments

```sql
--changeset tea0112:sprint-05-0001-seed-status-codes
-- No context = runs everywhere
INSERT INTO status_codes (code, name) VALUES 
    ('ACTIVE', 'Active'),
    ('INACTIVE', 'Inactive');
```

### ❌ Put in `/data/{env}/` when:
Data is **environment-specific** and should differ

**Examples:**
- Test users (dev/uat only)
- Sample transactions for testing
- Environment-specific admin users
- Test API keys/credentials
- Mock data for development

**Rule:** Has `context:dev/uat/prod` - runs only in matching environment

```sql
--changeset tea0112:sprint-01-0001-seed-dev-users context:dev
-- Only runs in dev
INSERT INTO users (username, email) VALUES 
    ('test_user', 'test@example.com');
```

### Decision Tree

```
Is this data identical in all environments?
│
├─ YES → /data/master/sprint-XX/
│         (Countries, roles, status codes, etc.)
│
└─ NO → /data/{env}/sprint-XX/
         (Test users, sample data, etc.)
```

## Key Benefits

✅ **Sprint traceability** - Clear visibility of which DB changes belong to each sprint  
✅ **Schema consistency** - All environments have identical structure  
✅ **Master data consistency** - Reference data same everywhere  
✅ **Environment flexibility** - Each environment can have custom test data  
✅ **Context filtering** - Liquibase automatically runs correct data per environment  
✅ **Progressive deployment** - Same migration files flow dev → uat → prod  
✅ **Reduced merge conflicts** - Each sprint works in isolated folders  
✅ **Sprint rollback** - Can identify and rollback entire sprint if needed  
✅ **Clear organization** - Easy to understand what runs where and when  
✅ **Agile-friendly** - Aligns perfectly with sprint-based development workflow

## Sprint-Based Benefits for Scrum Teams

| Benefit | Description |
|---------|-------------|
| **Sprint Planning** | Easy to estimate DB changes - see all changes in sprint folder |
| **Code Reviews** | Group migrations by sprint - easier to review related changes |
| **Release Management** | Bundle sprint folders for release - clear what's included |
| **Sprint Retrospective** | Review DB changes made during sprint |
| **Rollback Strategy** | If sprint needs rollback, all migrations are in one folder |
| **Team Collaboration** | Different developers work in different sprint folders = less conflicts |
| **Documentation** | Sprint folders serve as historical record of DB evolution |
