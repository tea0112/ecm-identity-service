# Liquibase Gradle Plugin Configuration

## What Was Added to build.gradle

### 1. Liquibase Gradle Plugin

```gradle
plugins {
    id 'org.liquibase.gradle' version '2.2.2'
}
```

This plugin provides Gradle tasks for Liquibase operations.

### 2. Liquibase Configuration Block

```gradle
liquibase {
    activities {
        // Main activity - uses local/dev database
        main {
            changelogFile 'src/main/resources/db/changelog/db.changelog-master.sql'
            url 'jdbc:postgresql://localhost:5432/ecm_identity_dev'
            username 'dev_ecm'
            password 'dev_ecm!23456'
            driver 'org.postgresql.Driver'
            contexts 'dev'
            logLevel 'info'
        }
        
        // Dry-run activity - generates SQL without executing
        dryRun {
            changelogFile 'src/main/resources/db/changelog/db.changelog-master.sql'
            url 'jdbc:postgresql://localhost:5432/ecm_identity_dev'
            username 'dev_ecm'
            password 'dev_ecm!23456'
            driver 'org.postgresql.Driver'
            contexts 'dev'
            outputFile 'build/liquibase-update.sql'
        }
    }
    
    // Run main activity by default
    runList = 'main'
}
```

### 3. Liquibase Runtime Dependencies

```gradle
dependencies {
    // Liquibase - Database Migration
    implementation 'org.liquibase:liquibase-core'
    liquibaseRuntime 'org.liquibase:liquibase-core'
    liquibaseRuntime 'org.postgresql:postgresql'
    liquibaseRuntime 'info.picocli:picocli:4.7.5'
    liquibaseRuntime 'ch.qos.logback:logback-classic:1.4.14'
}
```

These dependencies allow Liquibase Gradle tasks to run independently of the application.

---

## Available Gradle Commands

### Built-in Liquibase Plugin Tasks

```bash
# Update database to latest version
./gradlew update

# Show status of unrun changesets
./gradlew status

# Rollback last changeset
./gradlew rollbackCount -PliquibaseCommandValue=1

# Rollback to a specific tag
./gradlew rollback -PliquibaseCommandValue=v1.0

# Generate SQL for pending changes (without executing)
./gradlew updateSQL

# Validate changelog
./gradlew validate

# Clear checksums (if validation fails)
./gradlew clearChecksums

# Drop all database objects (DANGEROUS!)
./gradlew dropAll

# Mark next changeset as executed without running it
./gradlew changelogSync

# Generate a diff between database and changelog
./gradlew diff

# Show history of executed changesets
./gradlew history

# Generate documentation
./gradlew dbDoc
```

### Custom Tasks (Added)

```bash
# Validate changelog syntax
./gradlew liquibaseValidate

# Generate SQL without executing (dry-run)
./gradlew liquibaseDryRun

# Show detailed migration status
./gradlew liquibaseStatusReport

# Generate diff changelog (guide)
./gradlew liquibaseDiffChangelog
```

---

## Usage Examples

### 1. Check Pending Changes

```bash
./gradlew status
```

**Output:**
```
3 change sets have not been applied to dev_ecm@jdbc:postgresql://localhost:5432/ecm_identity_dev
     db/changelog/changes/v1.0/0001-create-sample-users-table.sql::v1.0-0001-create-sample-users-table::tea0112
     db/changelog/changes/v1.0/0002-create-sample-roles-table.sql::v1.0-0002-create-sample-roles-table::tea0112
     db/changelog/changes/v1.0/0003-create-sample-user-roles-table.sql::v1.0-0003-create-sample-user-roles-table::tea0112
```

### 2. Preview SQL Before Applying

```bash
./gradlew updateSQL
```

Generates SQL in `build/generated-sql/update.sql` showing what will be executed.

### 3. Apply Changes Manually

```bash
./gradlew update
```

Executes all pending changesets against the configured database.

### 4. Rollback Last Change

```bash
# Rollback last 1 changeset
./gradlew rollbackCount -PliquibaseCommandValue=1

# Rollback last 3 changesets
./gradlew rollbackCount -PliquibaseCommandValue=3
```

### 5. Rollback to Specific Tag

First, add a tag in your changelog:
```sql
--liquibase formatted sql
--changeset tea0112:v1.0-0005-add-feature
-- ... changes ...
--rollback ...

--changeset tea0112:v1.0-tag tagDatabase:v1.0-release
```

Then rollback:
```bash
./gradlew rollback -PliquibaseCommandValue=v1.0-release
```

### 6. Validate Changelogs

```bash
./gradlew validate
```

Checks for:
- Syntax errors
- Missing files
- Invalid references

### 7. Generate Documentation

```bash
./gradlew dbDoc
```

Generates HTML documentation of your database schema in `build/database/docs/`.

---

## Using Different Environments

### Option 1: Modify liquibase block temporarily

Edit `build.gradle` and change the URL/credentials:

```gradle
liquibase {
    activities {
        main {
            url 'jdbc:postgresql://uat-db-server:5432/ecm_identity_uat'
            username 'uat_ecm'
            password System.getenv('UAT_DB_PASSWORD')
            contexts 'uat'
        }
    }
}
```

### Option 2: Use Gradle properties

Create `gradle.properties`:
```properties
liquibaseUrl=jdbc:postgresql://localhost:5432/ecm_identity_dev
liquibaseUsername=dev_ecm
liquibasePassword=dev_ecm!23456
liquibaseContexts=dev
```

Then update `build.gradle`:
```gradle
liquibase {
    activities {
        main {
            url project.findProperty('liquibaseUrl') ?: 'jdbc:postgresql://localhost:5432/ecm_identity_dev'
            username project.findProperty('liquibaseUsername') ?: 'dev_ecm'
            password project.findProperty('liquibasePassword') ?: 'dev_ecm!23456'
            contexts project.findProperty('liquibaseContexts') ?: 'dev'
        }
    }
}
```

Then override via command line:
```bash
./gradlew update -PliquibaseUrl=jdbc:postgresql://uat-server:5432/db -PliquibaseContexts=uat
```

### Option 3: Multiple Activities (Recommended)

Add multiple activities in `build.gradle`:

```gradle
liquibase {
    activities {
        dev {
            changelogFile 'src/main/resources/db/changelog/db.changelog-master.sql'
            url 'jdbc:postgresql://localhost:5432/ecm_identity_dev'
            username 'dev_ecm'
            password 'dev_ecm!23456'
            contexts 'dev'
        }
        
        uat {
            changelogFile 'src/main/resources/db/changelog/db.changelog-master.sql'
            url 'jdbc:postgresql://uat-server:5432/ecm_identity_uat'
            username 'uat_ecm'
            password System.getenv('UAT_DB_PASSWORD')
            contexts 'uat'
        }
        
        prod {
            changelogFile 'src/main/resources/db/changelog/db.changelog-master.sql'
            url 'jdbc:postgresql://prod-server:5432/ecm_identity_prod'
            username 'prod_ecm'
            password System.getenv('PROD_DB_PASSWORD')
            contexts 'prod'
        }
    }
    
    runList = 'dev'  // default
}
```

Then specify which activity to run:
```bash
# Run against dev
./gradlew update -PrunList=dev

# Run against UAT
export UAT_DB_PASSWORD=secret
./gradlew update -PrunList=uat

# Run against production
export PROD_DB_PASSWORD=topsecret
./gradlew update -PrunList=prod
```

---

## Integration with Makefile

Your existing Makefile can now use these Gradle tasks:

```makefile
# Using Gradle commands instead of bootRun
db-update-gradle: ## Apply Liquibase changes using Gradle
	@echo "$(GREEN)Applying Liquibase changes via Gradle...$(NC)"
	./gradlew update

db-status-gradle: ## Check Liquibase status using Gradle
	@echo "$(GREEN)Checking Liquibase status via Gradle...$(NC)"
	./gradlew status

db-validate-gradle: ## Validate changelogs using Gradle
	@echo "$(GREEN)Validating Liquibase changelogs...$(NC)"
	./gradlew validate

db-rollback-gradle: ## Rollback last changeset using Gradle
	@echo "$(RED)Rolling back last changeset...$(NC)"
	./gradlew rollbackCount -PliquibaseCommandValue=1

db-generate-sql-gradle: ## Generate SQL without executing
	@echo "$(GREEN)Generating SQL...$(NC)"
	./gradlew updateSQL
	@echo "Output: build/generated-sql/update.sql"
```

---

## Comparison: Spring Boot vs Gradle Plugin

| Feature | Spring Boot (bootRun) | Gradle Plugin |
|---------|----------------------|---------------|
| **Auto-migration on startup** | ✅ Yes | ❌ No (manual) |
| **Runs with application** | ✅ Yes | ❌ Standalone |
| **Preview SQL before applying** | ❌ No | ✅ Yes (updateSQL) |
| **Manual control** | ❌ Limited | ✅ Full control |
| **Rollback support** | ❌ Requires code | ✅ Built-in |
| **Generate documentation** | ❌ No | ✅ Yes (dbDoc) |
| **Diff generation** | ❌ No | ✅ Yes (diff) |
| **Best for** | Development, CI/CD | Production, Manual ops |

### When to Use Each?

**Use Spring Boot integration (`bootRun`):**
- ✅ Local development (automatic migrations)
- ✅ CI/CD pipelines (auto-deploy)
- ✅ When you want "just run and it works"
- ✅ Integration with Spring application context

**Use Gradle plugin:**
- ✅ Production deployments (controlled)
- ✅ Preview changes before applying
- ✅ Manual database operations
- ✅ Rollback operations
- ✅ Generate database documentation
- ✅ When app is not running

**Best Practice:**
- **Development:** Use Spring Boot (`make run-dev`)
- **Production:** Use Gradle plugin (`./gradlew update -PrunList=prod`)
- **Preview:** Use Gradle plugin (`./gradlew updateSQL`)
- **Rollback:** Use Gradle plugin (`./gradlew rollbackCount`)

---

## Troubleshooting

### Issue: "Could not find method liquibaseRuntime()"

**Solution:** Make sure Liquibase plugin is in plugins block:
```gradle
plugins {
    id 'org.liquibase.gradle' version '2.2.2'
}
```

### Issue: "No suitable driver found"

**Solution:** Add PostgreSQL driver to liquibaseRuntime:
```gradle
dependencies {
    liquibaseRuntime 'org.postgresql:postgresql'
}
```

### Issue: "Connection refused"

**Check:**
1. Database is running: `docker ps | grep postgres`
2. Correct URL in liquibase block
3. Correct credentials

### Issue: Gradle tasks not showing

**Run:**
```bash
./gradlew tasks --group liquibase
```

If empty, the plugin may not be loaded correctly.

---

## Quick Reference

```bash
# Common operations
./gradlew status              # Check what will run
./gradlew updateSQL           # Preview SQL
./gradlew update              # Apply changes
./gradlew rollbackCount -PliquibaseCommandValue=1  # Rollback last

# Validation
./gradlew validate            # Validate changelog
./gradlew clearChecksums      # Clear checksums if needed

# Documentation
./gradlew dbDoc               # Generate docs

# Advanced
./gradlew diff                # Compare DB to changelog
./gradlew changelogSync       # Mark as executed without running
```

---

## Resources

- [Liquibase Gradle Plugin Docs](https://github.com/liquibase/liquibase-gradle-plugin)
- [Liquibase Command Line](https://docs.liquibase.com/commands/home.html)
- [Gradle Properties](https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties)

---

*Last Updated: 2025-10-12*
