# Liquibase Multi-Environment Configuration

## Schema Configuration

Each environment uses its own database schema to isolate data:

| Environment | Schema Name | Database |
|-------------|-------------|----------|
| **Development** | `dev_ecm` | `ecm_identity_dev` |
| **UAT** | `uat_ecm` | `ecm_identity_uat` |
| **Production** | `prod_ecm` | `ecm_identity_prod` |

## Running Migrations Per Environment

### Development
```bash
./gradlew update -PrunList=dev
# or simply (dev is default)
./gradlew update
```

### UAT
```bash
export UAT_DB_PASSWORD=your_uat_password
./gradlew update -PrunList=uat
```

### Production
```bash
export PROD_DB_PASSWORD=your_prod_password
./gradlew update -PrunList=prod
```

## Configuration Details

### Development (`build.gradle`)
```gradle
dev {
    defaultSchemaName 'dev_ecm'           // All tables created in dev_ecm schema
    liquibaseSchemaName 'dev_ecm'         // Liquibase tracking tables in dev_ecm
    contexts 'dev'                        // Runs dev-specific seed data
}
```

### UAT
```gradle
uat {
    defaultSchemaName 'uat_ecm'
    liquibaseSchemaName 'uat_ecm'
    contexts 'uat'
    password System.getenv('UAT_DB_PASSWORD')  // Password from environment variable
}
```

### Production
```gradle
prod {
    defaultSchemaName 'prod_ecm'
    liquibaseSchemaName 'prod_ecm'
    contexts 'prod'
    password System.getenv('PROD_DB_PASSWORD')  // Password from environment variable
}
```

## Common Commands

### Check Migration Status
```bash
# Dev
./gradlew status

# UAT
./gradlew status -PrunList=uat

# Prod
./gradlew status -PrunList=prod
```

### Preview SQL Changes (Without Executing)
```bash
./gradlew updateSQL
# Output: build/liquibase-update.sql
```

### Validate Changelog
```bash
./gradlew validate
```

### Rollback Last Changeset
```bash
./gradlew rollbackCount -PliquibaseCommandValue=1
```

## Schema Benefits

✅ **Isolation** - Each environment's data is separated by schema
✅ **Security** - Database users can be restricted to specific schemas
✅ **Clarity** - Easy to identify which schema belongs to which environment
✅ **Cleanup** - Drop entire environment schema without affecting others
✅ **Multiple environments** - Can run dev/uat/staging in same database server

## Initial Setup for New Environment

When setting up a new environment (UAT/Prod):

1. **Create the schema:**
```sql
CREATE SCHEMA IF NOT EXISTS uat_ecm;
GRANT ALL ON SCHEMA uat_ecm TO uat_ecm;
```

2. **Update database credentials** in environment variables:
```bash
export UAT_DB_PASSWORD="secure_password"
```

3. **Run migrations:**
```bash
./gradlew update -PrunList=uat
```

## Troubleshooting

### Schema Not Found
If you see "schema does not exist":
```sql
CREATE SCHEMA IF NOT EXISTS dev_ecm;
```

### Permission Denied
Ensure the database user has schema permissions:
```sql
GRANT ALL ON SCHEMA dev_ecm TO dev_ecm;
GRANT ALL ON ALL TABLES IN SCHEMA dev_ecm TO dev_ecm;
```

### Wrong Schema Used
Check `build.gradle` has correct `defaultSchemaName` and `liquibaseSchemaName` settings.

## Schema Verification

Check which schema your tables are in:
```sql
SELECT table_schema, table_name 
FROM information_schema.tables 
WHERE table_schema IN ('dev_ecm', 'uat_ecm', 'prod_ecm')
ORDER BY table_schema, table_name;
```
