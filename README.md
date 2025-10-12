# ecm-identity-service

# Run commands:
```bash
# Start local environment
make docker-up
make quickstart

# Work with different profiles
make run-dev                     # Local development
make run-uat                      # UAT environment
PROFILE=uat make db-status       # Check UAT status

# Liquibase operations
make db-validate                  # Validate changelogs
PROFILE=dev make db-update       # Apply changes to dev
PROFILE=dev ROLLBACK_COUNT=1 make db-rollback-count  # Rollback last changeset
```

# Liquibase - Best Practices
##### 1. Naming Conventions
```sql
--changeset author:sequential-number-descriptive-name
-- Examples:
--changeset tea0112:0001-create-users-table
--changeset tea0112:0002-add-email-column
--changeset tea0112:v2.0-0001-add-2fa-support
```
##### 2. Always Include
- `--comment` Explain what and why
- `--rollback` statement for every changeset
Preconditions for dependent changes
Context for environment-specific data
##### 3. Precondition Options
```sql
--preconditions onFail:HALT|CONTINUE|MARK_RAN|WARN onError:HALT|CONTINUE|MARK_RAN|WARN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM table_name
--precondition-sql-check expectedResult:0 SELECT COUNT(*) FROM information_schema.tables WHERE table_name='users'
```
##### 4. Transaction Control
```sql
--changeset author:id runInTransaction:false
-- Use for DDL that can't run in transaction (like CREATE INDEX CONCURRENTLY)
CREATE INDEX CONCURRENTLY idx_users_email ON users(email);
```
##### 5. Idempotency
```sql
-- Always use IF EXISTS / IF NOT EXISTS
CREATE TABLE IF NOT EXISTS users (...);
DROP TABLE IF EXISTS old_table CASCADE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS new_column VARCHAR(100);
```