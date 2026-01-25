# Local Startup Plan: ecm-identity-service

This plan outlines the steps to start the `ecm-identity-service` server locally using Gradle and Docker.

## Analysis of "Fit"
1. **Docker Compose**: 
   - ✅ Provides Postgres (v18), Redis (v7), and RabbitMQ (v3).
   - ⚠️ **Missing Kafka**: The application has `spring-kafka` dependency, but Kafka is not in `docker-compose.yml`.
2. **Makefile**:
   - ⚠️ **Profile Mismatch**: `make run-dev` forces `--spring.profiles.active=dev`, but `application-dev.properties` points to `dev-server`. Local dev should likely use `local`.
3. **Configuration**:
   - ✅ `.env` is set to `local`.
   - ✅ `application-local.properties` matches the `docker-compose.yml` credentials.

## PHASE 1: Infrastructure Setup
1. **Start Services**: 
   - Command: `make docker-up`
   - This starts Postgres, Redis, and RabbitMQ.

## PHASE 2: Application Execution
1. **Run Locally**:
   - Use the `local` profile to match Docker services.
   - Command: `./gradlew :identity-app:bootRun --args='--spring.profiles.active=local'`
   - *Note: I will update the Makefile to support a `make run-local` or fix the default.*

## PHASE 3: Verification
1. **Check Logs**: Ensure it connects to DB, Redis, and MQ.
2. **Health Check**: `http://localhost:8080/actuator/health`

---
**Status**: Awaiting User Approval to proceed with execution steps and minor Makefile/Plan improvements.
