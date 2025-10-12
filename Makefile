# Makefile for ECM Identity Service - Liquibase Operations

.PHONY: help build test clean run-dev run-uat run-prod
.PHONY: db-validate db-status db-update db-rollback db-rollback-count
.PHONY: db-clear db-drop-all db-generate-sql db-diff docker-up docker-down

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

run-dev: ## Run application with dev profile
	@echo "$(GREEN)Starting application with dev profile...$(NC)"
	./gradlew bootRun --args='--spring.profiles.active=dev'

run-uat: ## Run application with UAT profile
	@echo "$(GREEN)Starting application with UAT profile...$(NC)"
	@if [ -z "$$UAT_DB_PASSWORD" ]; then \
		echo "$(RED)Error: UAT_DB_PASSWORD environment variable not set$(NC)"; \
		exit 1; \
	fi
	./gradlew bootRun --args='--spring.profiles.active=uat'

run-prod: ## Run application with prod profile
	@echo "$(GREEN)Starting application with prod profile...$(NC)"
	@if [ -z "$$PROD_DB_PASSWORD" ]; then \
		echo "$(RED)Error: PROD_DB_PASSWORD environment variable not set$(NC)"; \
		exit 1; \
	fi
	java -jar build/libs/*.jar --spring.profiles.active=prod

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
docker-up: ## Start PostgreSQL 18 in Docker for local dev
	@echo "$(GREEN)Starting PostgreSQL 18 container...$(NC)"
	docker-compose up -d postgres
	@echo "$(GREEN)Waiting for PostgreSQL to be ready...$(NC)"
	@sleep 5
	@echo "$(GREEN)PostgreSQL is ready on localhost:5432$(NC)"
	@echo "  Database: ecm_identity_dev"
	@echo "  Username: dev_ecm"
	@echo "  Password: dev_ecm!23456"

docker-down: ## Stop Docker containers
	@echo "$(YELLOW)Stopping Docker containers...$(NC)"
	docker-compose down

docker-logs: ## Show Docker container logs
	docker-compose logs -f postgres

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
	@make build
	@echo "3. Running with dev profile..."
	@make run-dev