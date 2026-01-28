# ECM Identity Service - Kubernetes Configuration

This directory contains Kubernetes manifests for deploying the ECM Identity Service using [Kustomize](https://kustomize.io/).

## Structure

```
k8s/
├── base/                    # Shared base configuration
│   ├── kustomization.yaml   # Base kustomization
│   ├── configmap.yaml       # Non-sensitive configuration
│   ├── secret.yaml          # Secret template (no real values)
│   └── deployment.yaml      # Application deployment
│
└── overlays/                # Environment-specific overrides
    ├── dev/                 # Development environment
    ├── staging/             # Staging environment
    ├── uat/                 # UAT environment
    └── prod/                # Production environment
```

## Quick Start

### Preview Configuration

```bash
# Preview dev environment
kubectl kustomize k8s/overlays/dev

# Preview prod environment
kubectl kustomize k8s/overlays/prod
```

### Deploy to Cluster

```bash
# Deploy to dev namespace
kubectl apply -k k8s/overlays/dev

# Deploy to prod namespace
kubectl apply -k k8s/overlays/prod
```

## Secret Management

### Option 1: Manual Secret Creation (Recommended for initial setup)

Before deploying, create secrets manually in each namespace:

```bash
# Dev environment
kubectl create namespace ecm-identity-dev
kubectl -n ecm-identity-dev create secret generic ecm-identity-secrets \
  --from-literal=DB_PASSWORD='your-dev-password' \
  --from-literal=ADMIN_INITIAL_PASSWORD='Admin123!'

# Production environment  
kubectl create namespace ecm-identity-prod
kubectl -n ecm-identity-prod create secret generic ecm-identity-secrets \
  --from-literal=DB_PASSWORD='your-prod-password' \
  --from-literal=ADMIN_INITIAL_PASSWORD=''
```

### Option 2: Sealed Secrets (Recommended for GitOps)

For GitOps workflows, use [Sealed Secrets](https://github.com/bitnami-labs/sealed-secrets):

```bash
# Install kubeseal CLI
brew install kubeseal  # macOS
# or
wget https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.24.0/kubeseal-0.24.0-linux-amd64.tar.gz

# Seal a secret
kubectl -n ecm-identity-dev create secret generic ecm-identity-secrets \
  --from-literal=DB_PASSWORD='your-password' \
  --dry-run=client -o yaml | kubeseal --format yaml > k8s/overlays/dev/sealed-secret.yaml
```

### Option 3: External Secrets Operator

For cloud-native secret management, integrate with:
- AWS Secrets Manager
- GCP Secret Manager
- HashiCorp Vault

## Environment Variables Reference

### ConfigMap Values (Non-Sensitive)

| Variable | Description | Dev Default | Prod Default |
|----------|-------------|-------------|--------------|
| `DB_URL` | JDBC URL | `jdbc:postgresql://dev-db:5432/ecm_identity_dev` | `jdbc:postgresql://prod-db:5432/ecm_identity_prod` |
| `DB_USERNAME` | DB user | `dev_ecm` | `prod_ecm` |
| `DB_SCHEMA` | DB schema | `dev_ecm` | `prod_ecm` |
| `LIQUIBASE_CONTEXTS` | Liquibase contexts | `dev` | `prod` |
| `LOG_LEVEL_ROOT` | Root log level | `INFO` | `WARN` |
| `LOG_LEVEL_APP` | App log level | `DEBUG` | `INFO` |
| `JPA_SHOW_SQL` | Show SQL | `true` | `false` |
| `SERVER_PORT` | HTTP port | `8080` | `8080` |

### Secret Values (Sensitive)

| Variable | Description | Notes |
|----------|-------------|-------|
| `DB_PASSWORD` | Database password | **REQUIRED** |
| `ADMIN_INITIAL_PASSWORD` | Initial admin password | Empty in prod (skip creation) |

## Deployment Verification

```bash
# Check deployment status
kubectl -n ecm-identity-dev get pods
kubectl -n ecm-identity-dev get svc

# View logs
kubectl -n ecm-identity-dev logs -f deployment/ecm-identity-service

# Check config
kubectl -n ecm-identity-dev get configmap ecm-identity-config -o yaml
```

## Rollback

```bash
# Rollback to previous revision
kubectl -n ecm-identity-dev rollout undo deployment/ecm-identity-service

# Rollback to specific revision
kubectl -n ecm-identity-dev rollout undo deployment/ecm-identity-service --to-revision=2

# View rollout history
kubectl -n ecm-identity-dev rollout history deployment/ecm-identity-service
```

## Troubleshooting

### Pod not starting

```bash
# Check pod events
kubectl -n ecm-identity-dev describe pod -l app=ecm-identity-service

# Check if secrets exist
kubectl -n ecm-identity-dev get secrets

# Verify environment variables
kubectl -n ecm-identity-dev exec -it deployment/ecm-identity-service -- env | grep DB_
```

### Database connection issues

1. Verify `DB_URL` in ConfigMap
2. Check if Secret `DB_PASSWORD` exists
3. Ensure network policy allows DB connection
4. Check DB service DNS resolution
