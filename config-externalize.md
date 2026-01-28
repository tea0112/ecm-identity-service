# Config Externalization Plan

> **Project Type:** BACKEND (Spring Boot Microservice)  
> **Created:** 2026-01-28  
> **Updated:** 2026-01-28  
> **Status:** ✅ COMPLETE

---

## Overview

Refactor `ecm-identity-service` from multi-file environment-specific properties (`application-{env}.properties`) to modern externalized configuration pattern. This enables:

- **One artifact, many environments** - Same JAR deployed everywhere
- **Secrets never in git** - All sensitive data externalized
- **Kubernetes-native** - ConfigMaps + Secrets integration
- **12-Factor App compliance** - Configuration via environment variables

---

## Success Criteria

| Criteria                           | Metric                                          |
| ---------------------------------- | ----------------------------------------------- |
| ✅ Single `application.properties` | All hardcoded values removed                    |
| ✅ Secrets externalized            | Zero passwords/keys in git                      |
| ✅ Local dev works                 | `docker-compose up` + `make run-local` succeeds |
| ✅ K8s ready                       | ConfigMap + Secret manifests created            |
| ✅ No breaking changes             | Existing functionality preserved                |

---

## Tech Stack Decision

### Configuration Management: **Kubernetes ConfigMaps + Secrets**

| Option                       | Pros                                    | Cons                          | Decision            |
| ---------------------------- | --------------------------------------- | ----------------------------- | ------------------- |
| **K8s ConfigMaps + Secrets** | Native, no extra infra, GitOps friendly | Manual rotation               | ✅ **SELECTED**     |
| Spring Cloud Config Server   | Centralized, versioned                  | Extra service to maintain     | ❌ Overkill for now |
| HashiCorp Vault              | Enterprise-grade, dynamic secrets       | Complex setup, learning curve | ❌ Future option    |
| AWS Parameter Store          | Managed, versioned                      | AWS lock-in                   | ❌ Cloud-specific   |

**Rationale:** For a new microservice, K8s-native ConfigMaps + Secrets provide the best balance of simplicity and capability. Can migrate to Vault later if needed.

---

## Current State Analysis

### Files to Modify

| File                           | Current State          | Target State                          |
| ------------------------------ | ---------------------- | ------------------------------------- |
| `application.properties`       | Shared defaults        | All externalized placeholders         |
| `application-local.properties` | Full config + password | Minimal, for Docker Compose local dev |
| `application-dev.properties`   | Hardcoded dev-server   | **DELETE** → K8s ConfigMap            |
| `application-uat.properties`   | Hardcoded uat-server   | **DELETE** → K8s ConfigMap            |
| `application-prod.properties`  | Hardcoded prod-server  | **DELETE** → K8s ConfigMap            |
| `docker-compose.yml`           | Hardcoded passwords    | Reference `.env` file                 |
| `.env.example`                 | Does not exist         | **CREATE** → Template for local dev   |
| `.gitignore`                   | Standard               | Add `.env`, `*-secret.yaml`           |

### Configuration Categories

| Category           | Examples                              | Externalization Method           |
| ------------------ | ------------------------------------- | -------------------------------- |
| **Secrets**        | DB password, admin password, API keys | K8s Secret / `.env`              |
| **Infrastructure** | DB host, port, Redis host             | K8s ConfigMap / `.env`           |
| **Behavior**       | Log level, SQL logging, contexts      | Spring Profile OR ConfigMap      |
| **Static**         | Driver class, dialect                 | Keep in `application.properties` |

---

## Target Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         RUNTIME                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │   LOCAL      │    │   DEV/STG    │    │   PROD       │      │
│  │              │    │              │    │              │      │
│  │ .env file    │    │ K8s ConfigMap│    │ K8s ConfigMap│      │
│  │ + Docker     │    │ + K8s Secret │    │ + K8s Secret │      │
│  │   Compose    │    │              │    │ + Sealed     │      │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
│         │                   │                   │               │
│         └───────────────────┼───────────────────┘               │
│                             ▼                                   │
│                   ┌──────────────────┐                         │
│                   │ application.     │                         │
│                   │ properties       │                         │
│                   │ (placeholders)   │                         │
│                   │                  │                         │
│                   │ ${DB_URL}        │                         │
│                   │ ${DB_PASSWORD}   │                         │
│                   │ ${LOG_LEVEL}     │                         │
│                   └──────────────────┘                         │
│                             │                                   │
│                             ▼                                   │
│                   ┌──────────────────┐                         │
│                   │ Spring Boot App  │                         │
│                   │ (Same JAR)       │                         │
│                   └──────────────────┘                         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## Environment Variables Specification

### Required Variables (All Environments)

| Variable                 | Description              | Example                          | Secret? |
| ------------------------ | ------------------------ | -------------------------------- | ------- |
| `DB_URL`                 | JDBC connection URL      | `jdbc:postgresql://host:5432/db` | No      |
| `DB_USERNAME`            | Database username        | `app_user`                       | No      |
| `DB_PASSWORD`            | Database password        | `***`                            | **YES** |
| `DB_SCHEMA`              | Database schema          | `identity`                       | No      |
| `LIQUIBASE_CONTEXTS`     | Liquibase contexts       | `dev` or `prod`                  | No      |
| `LOG_LEVEL_ROOT`         | Root log level           | `INFO`                           | No      |
| `LOG_LEVEL_APP`          | App log level            | `DEBUG`                          | No      |
| `LOG_LEVEL_LIQUIBASE`    | Liquibase log level      | `WARN`                           | No      |
| `JPA_SHOW_SQL`           | Show SQL in logs         | `true` / `false`                 | No      |
| `ADMIN_INITIAL_PASSWORD` | Bootstrap admin password | `***`                            | **YES** |
| `ADMIN_EMAIL`            | Admin email              | `admin@company.com`              | No      |

### Optional Variables (Feature Flags)

| Variable            | Description       | Default     |
| ------------------- | ----------------- | ----------- |
| `SERVER_PORT`       | HTTP port         | `8080`      |
| `REDIS_HOST`        | Redis hostname    | `localhost` |
| `REDIS_PORT`        | Redis port        | `6379`      |
| `RABBITMQ_HOST`     | RabbitMQ hostname | `localhost` |
| `RABBITMQ_PORT`     | RabbitMQ port     | `5672`      |
| `RABBITMQ_USERNAME` | RabbitMQ user     | `guest`     |
| `RABBITMQ_PASSWORD` | RabbitMQ password | **YES**     |

---

## File Structure (After Refactoring)

```
ecm-identity-service/
├── identity-app/
│   └── src/main/resources/
│       ├── application.properties          # All externalized (${VAR})
│       └── application-local.properties    # Minimal local overrides
│
├── k8s/                                    # NEW: Kubernetes manifests
│   ├── base/
│   │   ├── kustomization.yaml
│   │   ├── configmap.yaml                  # Non-sensitive config
│   │   └── secret.yaml                     # Template (not real values)
│   │
│   ├── overlays/
│   │   ├── dev/
│   │   │   ├── kustomization.yaml
│   │   │   ├── configmap-patch.yaml
│   │   │   └── secret.yaml                 # Encrypted/sealed
│   │   ├── staging/
│   │   │   └── ...
│   │   ├── uat/
│   │   │   └── ...
│   │   └── prod/
│   │       └── ...
│
├── docker-compose.yml                      # References .env
├── .env.example                            # NEW: Template for local dev
├── .env                                    # GITIGNORED: Actual local values
└── .gitignore                              # Updated with .env patterns

DELETED:
├── application-dev.properties              ❌ REMOVED
├── application-uat.properties              ❌ REMOVED
├── application-prod.properties             ❌ REMOVED
```

---

## Task Breakdown

### Phase 1: Foundation (No Breaking Changes)

#### Task 1.1: Create Environment Variable Template

| Field            | Value                                      |
| ---------------- | ------------------------------------------ |
| **Agent**        | `backend-specialist`                       |
| **Skill**        | `clean-code`                               |
| **Priority**     | P0                                         |
| **Dependencies** | None                                       |
| **INPUT**        | Current property files                     |
| **OUTPUT**       | `.env.example` with all required variables |
| **VERIFY**       | File exists, all variables documented      |

- [x] Create `.env.example` with all required environment variables ✅
- [x] Add descriptions for each variable ✅
- [x] Mark which are secrets vs config ✅

#### Task 1.2: Update .gitignore

| Field            | Value                               |
| ---------------- | ----------------------------------- |
| **Agent**        | `backend-specialist`                |
| **Priority**     | P0                                  |
| **Dependencies** | None                                |
| **INPUT**        | Current `.gitignore`                |
| **OUTPUT**       | Updated `.gitignore`                |
| **VERIFY**       | `.env` and secret files are ignored |

- [x] Add `.env` to gitignore ✅ (already present)
- [x] Add `*-secret.yaml` patterns ✅ (already present)
- [x] Add any IDE-specific secret files ✅

#### Task 1.3: Create Local .env File

| Field            | Value                           |
| ---------------- | ------------------------------- |
| **Agent**        | `backend-specialist`            |
| **Priority**     | P0                              |
| **Dependencies** | Task 1.1                        |
| **INPUT**        | `.env.example`                  |
| **OUTPUT**       | `.env` (gitignored)             |
| **VERIFY**       | File exists, not tracked by git |

- [x] Copy `.env.example` to `.env` ✅
- [x] Fill in local development values ✅
- [x] Verify file is gitignored ✅

---

### Phase 2: Externalize Application Properties

#### Task 2.1: Refactor application.properties

| Field            | Value                                             |
| ---------------- | ------------------------------------------------- |
| **Agent**        | `backend-specialist`                              |
| **Skill**        | `clean-code`                                      |
| **Priority**     | P1                                                |
| **Dependencies** | Task 1.1                                          |
| **INPUT**        | `application.properties` + all env-specific files |
| **OUTPUT**       | Single externalized `application.properties`      |
| **VERIFY**       | No hardcoded values, all use `${VAR}` syntax      |

- [x] Replace all hardcoded values with `${VAR:default}` placeholders ✅
- [x] Add sensible defaults where safe (non-secrets) ✅
- [x] No defaults for secrets (force explicit configuration) ✅
- [x] Document each variable with comments ✅

#### Task 2.2: Minimize application-local.properties

| Field            | Value                            |
| ---------------- | -------------------------------- |
| **Agent**        | `backend-specialist`             |
| **Priority**     | P1                               |
| **Dependencies** | Task 2.1                         |
| **INPUT**        | `application-local.properties`   |
| **OUTPUT**       | Minimal local profile            |
| **VERIFY**       | Only contains behavior overrides |

- [x] Remove all connection strings (use .env) ✅
- [x] Keep only behavior toggles (show-sql, log levels) ✅
- [x] Keep Liquibase contexts for local seeding ✅

#### Task 2.3: Delete Environment-Specific Files

| Field            | Value                                                                                     |
| ---------------- | ----------------------------------------------------------------------------------------- |
| **Agent**        | `backend-specialist`                                                                      |
| **Priority**     | P1                                                                                        |
| **Dependencies** | Task 2.1, Task 3.1 (K8s manifests ready)                                                  |
| **INPUT**        | `application-dev.properties`, `application-uat.properties`, `application-prod.properties` |
| **OUTPUT**       | Files deleted                                                                             |
| **VERIFY**       | Files removed, git history preserved                                                      |

- [x] Ensure K8s manifests contain equivalent config ✅
- [x] Delete `application-dev.properties` ✅
- [x] Delete `application-uat.properties` ✅
- [x] Delete `application-prod.properties` ✅

---

### Phase 3: Docker Compose Update

#### Task 3.1: Update docker-compose.yml

| Field            | Value                                         |
| ---------------- | --------------------------------------------- |
| **Agent**        | `backend-specialist`                          |
| **Skill**        | `deployment-procedures`                       |
| **Priority**     | P1                                            |
| **Dependencies** | Task 1.3                                      |
| **INPUT**        | `docker-compose.yml`                          |
| **OUTPUT**       | Updated compose file using `.env`             |
| **VERIFY**       | `docker-compose config` shows resolved values |

- [x] Add `env_file: .env` to services ✅
- [x] Replace hardcoded values with `${VAR}` references ✅
- [x] Keep healthcheck commands unchanged ✅

---

### Phase 4: Kubernetes Manifests

#### Task 4.1: Create Kustomize Base Structure

| Field            | Value                                 |
| ---------------- | ------------------------------------- |
| **Agent**        | `backend-specialist`                  |
| **Skill**        | `deployment-procedures`               |
| **Priority**     | P2                                    |
| **Dependencies** | Task 2.1                              |
| **INPUT**        | Environment variable spec             |
| **OUTPUT**       | `k8s/base/` directory with manifests  |
| **VERIFY**       | `kubectl kustomize k8s/base` succeeds |

- [x] Create `k8s/base/kustomization.yaml` ✅
- [x] Create `k8s/base/configmap.yaml` with non-sensitive config ✅
- [x] Create `k8s/base/secret.yaml` as template (no real values) ✅

#### Task 4.2: Create Environment Overlays

| Field            | Value                                         |
| ---------------- | --------------------------------------------- |
| **Agent**        | `backend-specialist`                          |
| **Skill**        | `deployment-procedures`                       |
| **Priority**     | P2                                            |
| **Dependencies** | Task 4.1                                      |
| **INPUT**        | Base manifests                                |
| **OUTPUT**       | `k8s/overlays/{dev,staging,uat,prod}/`        |
| **VERIFY**       | `kubectl kustomize k8s/overlays/dev` succeeds |

- [x] Create dev overlay with dev-specific values ✅
- [x] Create staging overlay ✅
- [x] Create uat overlay ✅
- [x] Create prod overlay with production hardening ✅

#### Task 4.3: Document Secret Management

| Field            | Value                                  |
| ---------------- | -------------------------------------- |
| **Agent**        | `backend-specialist`                   |
| **Skill**        | `documentation-templates`              |
| **Priority**     | P2                                     |
| **Dependencies** | Task 4.2                               |
| **INPUT**        | K8s manifests                          |
| **OUTPUT**       | `k8s/README.md`                        |
| **VERIFY**       | Clear instructions for secret creation |

- [x] Document how to create secrets in each environment ✅
- [x] Document Sealed Secrets option for GitOps ✅
- [x] Include rollback procedures ✅

---

### Phase 5: Update Build & Makefile

#### Task 5.1: Update Makefile Targets

| Field            | Value                                  |
| ---------------- | -------------------------------------- |
| **Agent**        | `backend-specialist`                   |
| **Priority**     | P1                                     |
| **Dependencies** | Task 2.1, Task 3.1                     |
| **INPUT**        | `Makefile`                             |
| **OUTPUT**       | Updated Makefile                       |
| **VERIFY**       | `make run-local` works with new config |

- [x] Update `run-local` to use `local` profile + `.env` ✅
- [x] Remove or update `run-dev`, `run-uat`, `run-prod` targets ✅
- [x] Add `make k8s-dev`, `make k8s-prod` for K8s deploys ✅

---

### Phase X: Verification

#### Checklist

- [x] **Local Dev Test:** `docker-compose up -d && make run-local` succeeds ✅
- [x] **No Secrets in Git:** `git grep -i password` returns only placeholders ✅
- [x] **K8s Structure:** All manifests created with Kustomize overlays ✅
- [x] **Existing Functionality:** API endpoints still work ✅
- [x] **Documentation:** `k8s/README.md` created with instructions ✅

#### Scripts to Run

```bash
# Security scan (no secrets in code)
python .agent/skills/vulnerability-scanner/scripts/security_scan.py .

# Validate K8s manifests
kubectl kustomize k8s/overlays/dev --enable-helm | kubectl apply --dry-run=client -f -
```

---

## Risk Assessment

| Risk                           | Likelihood | Impact   | Mitigation                              |
| ------------------------------ | ---------- | -------- | --------------------------------------- |
| Missing env var breaks startup | Medium     | High     | Add validation at startup, fail fast    |
| Wrong config in wrong env      | Low        | Critical | Use Kustomize overlays, review process  |
| Secrets leaked in logs         | Low        | Critical | Never log config values, mask in errors |
| Local dev friction             | Medium     | Medium   | Provide `.env.example`, clear docs      |

---

## Timeline Estimate

| Phase                 | Tasks        | Estimated Time |
| --------------------- | ------------ | -------------- |
| Phase 1: Foundation   | 3 tasks      | 30 min         |
| Phase 2: Properties   | 3 tasks      | 45 min         |
| Phase 3: Docker       | 1 task       | 15 min         |
| Phase 4: Kubernetes   | 3 tasks      | 1 hour         |
| Phase 5: Makefile     | 1 task       | 15 min         |
| Phase X: Verification | -            | 30 min         |
| **Total**             | **11 tasks** | **~3 hours**   |

---

## Next Steps

After plan approval:

1. Run `/create` or start implementation manually
2. Begin with Phase 1 (no breaking changes)
3. Test locally before proceeding to K8s manifests
