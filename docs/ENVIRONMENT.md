# Environment Variables Configuration

## Overview

The application uses environment variables for all configuration to:
- ✅ Keep sensitive data out of source control
- ✅ Support different environments (dev/uat/prod)
- ✅ Follow 12-factor app principles
- ✅ Enable containerization (Docker/Kubernetes)

## Environment Variable Standard

**All environments use the SAME variable names:**

```bash
DB_URL=jdbc:postgresql://...
DB_USERNAME=...
DB_PASSWORD=...
DB_SCHEMA=...
SPRING_PROFILES_ACTIVE=local|dev|uat|prod
```

### Configuration Files Per Environment

The application uses **separate .env files** for each environment:

| File | Purpose | Values |
|------|---------|--------|
| `.env.local` | Localhost development | `DB_URL=jdbc:postgresql://localhost:5432/...` |
| `.env.dev` | Shared dev server | `DB_URL=jdbc:postgresql://dev-server:5432/...` |
| `.env.uat` | UAT environment | `DB_URL=jdbc:postgresql://uat-server:5432/...` |
| `.env.prod` | Production | `DB_URL=jdbc:postgresql://prod-server:5432/...` |

### Example: .env.local
```bash
DB_URL=jdbc:postgresql://localhost:5432/ecm_identity_dev
DB_USERNAME=dev_ecm
DB_PASSWORD=dev_ecm!23456
DB_SCHEMA=dev_ecm
SPRING_PROFILES_ACTIVE=local
```

### Example: .env.prod
```bash
DB_URL=jdbc:postgresql://prod-server:5432/ecm_identity_prod
DB_USERNAME=prod_ecm
DB_PASSWORD=secure_prod_password
DB_SCHEMA=prod_ecm
SPRING_PROFILES_ACTIVE=prod
```

## Setup Methods

### Method 1: Using Environment-Specific .env Files (Recommended)

1. **For local development:**
```bash
# Use the pre-configured local file
export $(cat .env.local | xargs)
./gradlew bootRun
```

2. **For dev server:**
```bash
# Edit password first
nano .env.dev
export $(cat .env.dev | xargs)
./gradlew update -PrunList=dev
```

3. **For UAT/Prod:**
```bash
# Edit with actual credentials
nano .env.uat
export $(cat .env.uat | xargs)
./gradlew update -PrunList=uat
```

### Method 2: System Environment Variables

```bash
# Add to ~/.bashrc or ~/.zshrc
export DB_URL="jdbc:postgresql://localhost:5432/ecm_identity_dev"
export DB_USERNAME="dev_ecm"
export DB_PASSWORD="dev_ecm!23456"
export DB_SCHEMA="dev_ecm"

# Reload
source ~/.bashrc
```

### Method 3: IDE Configuration

**IntelliJ IDEA:**
1. Run → Edit Configurations
2. Select your application
3. Environment Variables → Add
4. Enter: `DB_PASSWORD=your_password;DB_SCHEMA=dev_ecm;SPRING_PROFILES_ACTIVE=local`

**VS Code:**
1. Create `.vscode/launch.json`:
```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Spring Boot App - Local",
      "request": "launch",
      "mainClass": "com.ecm.security.EcmIdentityServiceApplication",
      "env": {
        "DB_URL": "jdbc:postgresql://localhost:5432/ecm_identity_dev",
        "DB_USERNAME": "dev_ecm",
        "DB_PASSWORD": "dev_ecm!23456",
        "DB_SCHEMA": "dev_ecm",
        "SPRING_PROFILES_ACTIVE": "local"
      }
    }
  ]
}
```

### Method 4: Docker/Kubernetes

**Docker Compose:**
```yaml
services:
  app:
    image: ecm-identity-service
    environment:
      - DB_URL=jdbc:postgresql://postgres:5432/ecm_identity_dev
      - DB_USERNAME=dev_ecm
      - DB_PASSWORD=${DB_PASSWORD}
      - DB_SCHEMA=dev_ecm
      - SPRING_PROFILES_ACTIVE=local
    env_file:
      - .env.local  # or .env.prod for production
```

**Kubernetes Secret:**
```bash
# Create secret from .env file
kubectl create secret generic db-config \
  --from-env-file=.env.prod

# Or create manually
kubectl create secret generic db-config \
  --from-literal=DB_PASSWORD=secure_password \
  --from-literal=DB_USERNAME=prod_ecm \
  --from-literal=DB_URL=jdbc:postgresql://prod-db:5432/ecm_identity_prod \
  --from-literal=DB_SCHEMA=prod_ecm

# In deployment.yaml
env:
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: db-config
        key: DB_PASSWORD
  - name: DB_USERNAME
    valueFrom:
      secretKeyRef:
        name: db-config
        key: DB_USERNAME
  - name: DB_URL
    valueFrom:
      secretKeyRef:
        name: db-config
        key: DB_URL
  - name: DB_SCHEMA
    valueFrom:
      secretKeyRef:
        name: db-config
        key: DB_SCHEMA
  - name: SPRING_PROFILES_ACTIVE
    value: "prod"
```

## Usage

### Running Liquibase Migrations

```bash
# Local development (localhost)
export $(cat .env.local | xargs)
./gradlew update
# or
./gradlew update -PrunList=local

# Shared dev server
export $(cat .env.dev | xargs)
./gradlew update -PrunList=dev

# UAT
export $(cat .env.uat | xargs)
./gradlew update -PrunList=uat

# Production
export $(cat .env.prod | xargs)
./gradlew update -PrunList=prod
```

### Running Spring Boot Application

```bash
# Local development (localhost)
export $(cat .env.local | xargs)
./gradlew bootRun

# Shared dev server
export $(cat .env.dev | xargs)
./gradlew bootRun

# UAT
export $(cat .env.uat | xargs)
./gradlew bootRun

# Production
export $(cat .env.prod | xargs)
java -jar build/libs/ecm-identity-service.jar
```

## Environment Profile Structure

| Profile | Purpose | Database Location | When to Use |
|---------|---------|-------------------|-------------|
| **local** | Individual coding/testing | localhost | Daily development on your machine |
| **dev** | Team integration testing | Shared dev server | Testing integration with team |
| **uat** | Client/QA testing | UAT server | Pre-production validation |
| **prod** | Production | Production server | Live deployment |

## Default Values

The application provides sensible defaults for local development:

| Variable | Default (Local) | Default (Dev) | Default (UAT) | Default (Prod) |
|----------|-----------------|---------------|---------------|----------------|
| `DB_URL` | `localhost:5432/ecm_identity_dev` | `dev-server:5432/...` | `uat-server:5432/...` | `prod-server:5432/...` |
| `DB_USERNAME` | `dev_ecm` | `dev_ecm` | `uat_ecm` | `prod_ecm` |
| `DB_PASSWORD` | `dev_ecm!23456` (fallback) | **Required** | **Required** | **Required** |
| `DB_SCHEMA` | `dev_ecm` | `dev_ecm` | `uat_ecm` | `prod_ecm` |

**Security Note:** 
- **Local**: Has default password for convenience (localhost only)
- **Dev/UAT/Prod**: Password MUST be explicitly set via environment variables
- Each environment uses `.env.{environment}` file with consistent variable names

## Verification

Check if variables are loaded:
```bash
echo $DEV_DB_URL
echo $DEV_DB_PASSWORD
echo $DEV_DB_SCHEMA
```

Test database connection:
```bash
psql "$DEV_DB_URL" -U $DEV_DB_USERNAME -c "SELECT current_schema();"
```

## Security Best Practices

✅ **DO:**
- Use environment variables for all sensitive data
- Use different passwords per environment
- Rotate passwords regularly
- Use secrets management (Vault, AWS Secrets Manager)
- Add `.env` to `.gitignore`

❌ **DON'T:**
- Commit `.env` file to version control
- Share passwords in Slack/email
- Use same password across environments
- Hardcode credentials in code
- Log password values

## Troubleshooting

### Variable Not Found
```bash
# Check if variable is set
env | grep DB_

# Set temporarily
export DEV_DB_PASSWORD="your_password"

# Check application can see it
./gradlew bootRun --debug | grep DB_
```

## Troubleshooting

### Variable Not Found
```bash
# Check if variable is set
env | grep DB_

# Load from .env file
export $(cat .env.local | xargs)

# Check application can see it
./gradlew bootRun --debug | grep DB_
```

### Wrong Schema Used
```bash
# Verify schema variable
echo $DB_SCHEMA

# Check current schema in database
psql -U $DB_USERNAME -h $DB_HOST -c "SELECT current_schema();"
```

### Connection Refused
```bash
# Test connection manually
psql "$DB_URL" -U $DB_USERNAME

# Check if PostgreSQL is running
docker ps | grep postgres
# or
systemctl status postgresql
```

### Wrong Environment Profile
```bash
# Check which profile is active
echo $SPRING_PROFILES_ACTIVE

# Verify correct .env file was loaded
cat .env.local | grep SPRING_PROFILES_ACTIVE
```

## CI/CD Integration

### GitHub Actions
```yaml
- name: Run Liquibase Migrations
  env:
    UAT_DB_PASSWORD: ${{ secrets.UAT_DB_PASSWORD }}
    UAT_DB_URL: ${{ secrets.UAT_DB_URL }}
  run: ./gradlew update -PrunList=uat
```

### GitLab CI
```yaml
deploy:uat:
  variables:
    UAT_DB_PASSWORD: $UAT_DB_PASSWORD
    UAT_DB_URL: $UAT_DB_URL
  script:
    - ./gradlew update -PrunList=uat
```

### Jenkins
```groovy
withCredentials([
  string(credentialsId: 'uat-db-password', variable: 'UAT_DB_PASSWORD')
]) {
  sh './gradlew update -PrunList=uat'
}
```
