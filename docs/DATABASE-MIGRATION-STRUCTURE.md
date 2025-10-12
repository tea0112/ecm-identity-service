# Database Migration Structure

## Directory Organization

```
/db/changelog/
├── changes/                    # Schema changes (environment-agnostic)
│   └── v1.0/
│       ├── 0001-create-sample-users-table.sql
│       ├── 0002-create-sample-roles-table.sql
│       └── 0003-create-sample-user-roles-table.sql
│
├── data/
│   ├── master/                 # Master/Reference data (runs in ALL environments)
│   │   └── v1.0/
│   │       └── 0001-seed-master-roles.sql
│   │
│   ├── dev/                    # Dev-specific test data (context:dev)
│   │   └── v1.0/
│   │       ├── 0001-seed-dev-test-data.sql
│   │       └── 0002-seed-dev-users.sql
│   │
│   ├── uat/                    # UAT-specific test data (context:uat)
│   │   └── v1.0/
│   │       ├── 0001-seed-uat-test-data.sql
│   │       └── 0002-seed-uat-users.sql
│   │
│   └── prod/                   # Prod-specific initial data (context:prod)
│       └── v1.0/
│           └── 0001-seed-prod-initial-data.sql
│
└── db.changelog-master.xml     # Master changelog (XML format for includes)
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

1. Create file in `/data/master/v1.0/`
2. **Do NOT add context** to the changeset
3. Add to `db.changelog-master.xml` after schema changes
4. Run `./gradlew update` - will execute in all environments

Example:
```sql
--liquibase formatted sql

--changeset tea0112:v1.0-0002-seed-countries
--comment: Insert country master data for all environments
INSERT INTO countries (code, name) VALUES 
    ('US', 'United States'),
    ('VN', 'Vietnam')
ON CONFLICT (code) DO NOTHING;
```

## Progressive Deployment (Dev → UAT → Prod)

The structure supports progressive deployment across environments:

```bash
# Sprint 5: Deploy to Dev
cd dev-environment
./gradlew update -Pcontexts=dev
# ✅ Schema changes applied
# ✅ Master data applied
# ✅ Dev test data applied

# After testing → Deploy to UAT
cd uat-environment
./gradlew update -Pcontexts=uat
# ✅ Same schema changes applied
# ✅ Same master data applied
# ✅ UAT test data applied

# After UAT approval → Deploy to Prod
cd prod-environment
./gradlew update -Pcontexts=prod
# ✅ Same schema changes applied
# ✅ Same master data applied
# ✅ Prod initial data applied
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
--changeset tea0112:v1.5-0001-seed-status-codes
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
--changeset tea0112:v1.0-0001-seed-dev-users context:dev
-- Only runs in dev
INSERT INTO users (username, email) VALUES 
    ('test_user', 'test@example.com');
```

### Decision Tree

```
Is this data identical in all environments?
│
├─ YES → /data/master/v1.X/
│         (Countries, roles, status codes, etc.)
│
└─ NO → /data/{env}/v1.X/
         (Test users, sample data, etc.)
```

## Key Benefits

✅ **Schema consistency** - All environments have identical structure  
✅ **Master data consistency** - Reference data same everywhere  
✅ **Environment flexibility** - Each environment can have custom test data  
✅ **Context filtering** - Liquibase automatically runs correct data per environment  
✅ **Progressive deployment** - Same migration files flow dev → uat → prod  
✅ **Clear organization** - Easy to understand what runs where
