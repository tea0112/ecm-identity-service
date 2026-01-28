# Makefile for ECM Identity Service

.PHONY: help build test clean run-local run-dev run-uat run-prod
.PHONY: db-validate db-status db-update db-rollback db-rollback-count
.PHONY: db-clear db-drop-all db-generate-sql db-diff docker-up docker-down
.PHONY: k8s-preview-dev k8s-preview-staging k8s-preview-uat k8s-preview-prod
.PHONY: k8s-apply-dev k8s-apply-staging k8s-apply-uat k8s-apply-prod

# Default profile
PROFILE ?= dev
SPRING_PROFILE = --spring.profiles.active=$(PROFILE)

# Liquibase specific
ROLLBACK_COUNT ?= 1
ROLLBACK_TAG ?= 

# Colors for output
GREEN  := \033[0;32m
YELLOW := \033[0;33m
RED    := \033[0;31m
NC     := \033[0m # No Color

help: ## Show this help message
	@echo '$(GREEN)Available targets:$(NC)'
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  $(YELLOW)%-20s$(NC) %s\n", $$1, $$2}'

# Build & Run targets
build: ## Build the application
	@echo "$(GREEN)Building application...$(NC)"
	./gradlew clean build

build-skip-tests: ## Build without running tests (for initial setup)
	@echo "$(GREEN)Building application (skipping tests)...$(NC)"
	./gradlew clean build -x test

test: ## Run tests with Testcontainers
	@echo "$(GREEN)Running tests...$(NC)"
	./gradlew test

clean: ## Clean build artifacts
	@echo "$(GREEN)Cleaning...$(NC)"
	./gradlew clean

# Load .env file if it exists (for run-local target)
-include .env
export

run-local: ## Run application with local profile (uses .env)
	@echo "$(GREEN)Starting application with local profile...$(NC)"
	@if [ ! -f .env ]; then \
		echo "$(YELLOW)Creating .env from .env.example...$(NC)"; \
		cp .env.example .env; \
		echo "$(YELLOW)Re-run 'make run-local' to load the new .env file$(NC)"; \
		exit 0; \
	fi
	./gradlew :identity-app:bootRun --args='--spring.profiles.active=local'

run-dev: ## Run application for shared dev server (requires env vars)
	@echo "$(GREEN)Starting application for dev environment...$(NC)"
	@if [ -z "$$DB_URL" ]; then \
		echo "$(RED)Error: DB_URL environment variable not set$(NC)"; \
		echo "$(YELLOW)For local development, use: make run-local$(NC)"; \
		exit 1; \
	fi
	./gradlew :identity-app:bootRun

run-uat: ## Run application for UAT (requires env vars)
	@echo "$(GREEN)Starting application for UAT environment...$(NC)"
	@if [ -z "$$DB_URL" ]; then \
		echo "$(RED)Error: DB_URL environment variable not set$(NC)"; \
		exit 1; \
	fi
	./gradlew :identity-app:bootRun

run-prod: ## Run application for production (requires env vars)
	@echo "$(GREEN)Starting application for production...$(NC)"
	@if [ -z "$$DB_URL" ]; then \
		echo "$(RED)Error: DB_URL environment variable not set$(NC)"; \
		exit 1; \
	fi
	java -jar build/libs/*.jar


# Liquibase targets
db-validate: ## Validate Liquibase changelog syntax
	@echo "$(GREEN)Validating Liquibase changelog...$(NC)"
	./gradlew bootRun --args='--spring.profiles.active=$(PROFILE) --spring.liquibase.enabled=false' & \
	sleep 5 && \
	./gradlew liquibaseValidate

db-status: ## Check pending Liquibase changes (PROFILE=dev|uat|prod)
	@echo "$(GREEN)Checking Liquibase status for profile: $(PROFILE)...$(NC)"
	./gradlew bootRun --args='--spring.profiles.active=$(PROFILE) --logging.level.liquibase=INFO' -i | grep -A 50 "Liquibase"

db-update: ## Apply pending Liquibase changes manually (PROFILE=dev|uat|prod)
	@echo "$(GREEN)Applying Liquibase changes for profile: $(PROFILE)...$(NC)"
	@read -p "Are you sure you want to apply changes to $(PROFILE)? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		./gradlew bootRun --args='--spring.profiles.active=$(PROFILE)'; \
	else \
		echo "$(YELLOW)Aborted$(NC)"; \
	fi

db-rollback-count: ## Rollback last N changesets (PROFILE=dev, ROLLBACK_COUNT=1)
	@echo "$(RED)Rolling back $(ROLLBACK_COUNT) changeset(s) for profile: $(PROFILE)...$(NC)"
	@read -p "Are you sure you want to rollback $(ROLLBACK_COUNT) changeset(s) on $(PROFILE)? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		./gradlew liquibaseRollbackCount -PliquibaseCommandValue=$(ROLLBACK_COUNT); \
	else \
		echo "$(YELLOW)Aborted$(NC)"; \
	fi

db-rollback: ## Rollback to specific tag (PROFILE=dev, ROLLBACK_TAG=v1.0)
	@if [ -z "$(ROLLBACK_TAG)" ]; then \
		echo "$(RED)Error: ROLLBACK_TAG is required. Usage: make db-rollback ROLLBACK_TAG=v1.0$(NC)"; \
		exit 1; \
	fi
	@echo "$(RED)Rolling back to tag: $(ROLLBACK_TAG) for profile: $(PROFILE)...$(NC)"
	@read -p "Are you sure you want to rollback to $(ROLLBACK_TAG) on $(PROFILE)? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		./gradlew liquibaseRollback -PliquibaseCommandValue=$(ROLLBACK_TAG); \
	else \
		echo "$(YELLOW)Aborted$(NC)"; \
	fi

db-generate-sql: ## Generate SQL for pending changes without applying
	@echo "$(GREEN)Generating SQL for profile: $(PROFILE)...$(NC)"
	./gradlew liquibaseUpdateSQL -PliquibaseCommandValue=build/liquibase-update-$(PROFILE).sql

db-clear: ## Clear Liquibase checksums (use if changelog validation fails)
	@echo "$(YELLOW)Clearing Liquibase checksums for profile: $(PROFILE)...$(NC)"
	./gradlew liquibaseClearChecksums

db-drop-all: ## Drop all database objects managed by Liquibase (DANGEROUS!)
	@echo "$(RED)WARNING: This will drop ALL database objects!$(NC)"
	@read -p "Are you sure you want to drop all objects on $(PROFILE)? Type 'yes' to confirm: " -r; \
	echo; \
	if [[ $$REPLY == "yes" ]]; then \
		./gradlew liquibaseDropAll; \
	else \
		echo "$(YELLOW)Aborted$(NC)"; \
	fi

# Docker targets (for local development)
docker-up: ## Start all infrastructure containers in Docker for local dev
	@echo "$(GREEN)Starting all infrastructure containers...$(NC)"
	docker compose up -d
	@echo "$(GREEN)Waiting for PostgreSQL to be ready...$(NC)"
	@sleep 5
	@echo "$(GREEN)PostgreSQL is ready on localhost:5432$(NC)"
	@echo "  Database: ecm_identity_dev"
	@echo "  Username: dev_ecm"
	@echo "  Password: dev_ecm!23456"

docker-down: ## Stop Docker containers
	@echo "$(YELLOW)Stopping Docker containers...$(NC)"
	docker compose down

docker-logs: ## Show Docker container logs
	docker compose logs -f postgres

# Database connection shortcuts
db-connect-dev: ## Connect to dev database with psql
	@echo "$(GREEN)Connecting to dev database...$(NC)"
	PGPASSWORD=dev_ecm!23456 psql -h localhost -p 5432 -U dev_ecm -d ecm_identity_dev

db-connect-uat: ## Connect to UAT database with psql
	@echo "$(GREEN)Connecting to UAT database...$(NC)"
	@if [ -z "$$UAT_DB_PASSWORD" ]; then \
		echo "$(RED)Error: UAT_DB_PASSWORD not set$(NC)"; \
		exit 1; \
	fi
	PGPASSWORD=$$UAT_DB_PASSWORD psql -h uat-db-server -p 5432 -U uat_ecm -d ecm_identity_uat

# Utility targets
version: ## Show application version
	@grep "version = " build.gradle | head -1 | awk -F"'" '{print $$2}'

info: ## Show current configuration
	@echo "$(GREEN)Current Configuration:$(NC)"
	@echo "  Profile: $(PROFILE)"
	@echo "  Java Version: $$(java -version 2>&1 | head -1)"
	@echo "  Gradle Version: $$(./gradlew --version | grep Gradle | awk '{print $$2}')"
	@echo "  Spring Boot Version: $$(grep "org.springframework.boot" build.gradle | awk -F"'" '{print $$4}')"

# Quick start for new developers
quickstart: ## Quick setup for new developers
	@echo "$(GREEN)Quick Start Setup$(NC)"
	@echo "1. Starting Docker containers..."
	@make docker-up
	@echo "2. Building application..."
	@make build-skip-tests
	@echo "3. Running with local profile..."
	@make run-local

# ============================================================
# Kubernetes Deployment Targets
# ============================================================

# Preview targets (dry-run)
k8s-preview-dev: ## Preview K8s manifests for dev environment
	@echo "$(GREEN)Previewing dev environment manifests...$(NC)"
	kubectl kustomize k8s/overlays/dev

k8s-preview-staging: ## Preview K8s manifests for staging environment
	@echo "$(GREEN)Previewing staging environment manifests...$(NC)"
	kubectl kustomize k8s/overlays/staging

k8s-preview-uat: ## Preview K8s manifests for UAT environment
	@echo "$(GREEN)Previewing UAT environment manifests...$(NC)"
	kubectl kustomize k8s/overlays/uat

k8s-preview-prod: ## Preview K8s manifests for production environment
	@echo "$(GREEN)Previewing production environment manifests...$(NC)"
	kubectl kustomize k8s/overlays/prod

# Apply targets
k8s-apply-dev: ## Apply K8s manifests to dev namespace
	@echo "$(GREEN)Deploying to dev environment...$(NC)"
	@read -p "Deploy to dev? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		kubectl apply -k k8s/overlays/dev; \
	else \
		echo "$(YELLOW)Aborted$(NC)"; \
	fi

k8s-apply-staging: ## Apply K8s manifests to staging namespace
	@echo "$(GREEN)Deploying to staging environment...$(NC)"
	@read -p "Deploy to staging? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		kubectl apply -k k8s/overlays/staging; \
	else \
		echo "$(YELLOW)Aborted$(NC)"; \
	fi

k8s-apply-uat: ## Apply K8s manifests to UAT namespace
	@echo "$(YELLOW)Deploying to UAT environment...$(NC)"
	@read -p "Deploy to UAT? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		kubectl apply -k k8s/overlays/uat; \
	else \
		echo "$(YELLOW)Aborted$(NC)"; \
	fi

k8s-apply-prod: ## Apply K8s manifests to production namespace (DANGEROUS!)
	@echo "$(RED)WARNING: Deploying to PRODUCTION environment!$(NC)"
	@read -p "Are you SURE you want to deploy to PRODUCTION? Type 'yes' to confirm: " -r; \
	echo; \
	if [[ $$REPLY == "yes" ]]; then \
		kubectl apply -k k8s/overlays/prod; \
	else \
		echo "$(YELLOW)Aborted$(NC)"; \
	fi

# Status targets
k8s-status-dev: ## Check deployment status in dev
	@echo "$(GREEN)Dev environment status:$(NC)"
	kubectl -n ecm-identity-dev get pods,svc,configmap,secret

k8s-status-prod: ## Check deployment status in production
	@echo "$(GREEN)Production environment status:$(NC)"
	kubectl -n ecm-identity-prod get pods,svc,configmap,secret