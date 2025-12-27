# ECM Identity Service - Project Brief

## Overview
**Project Name**: ECM Identity Service  
**Purpose**: Identity and Access Management (IAM) service for the ECM platform  
**Technology Stack**: Java 21, Spring Boot 3.5.6, PostgreSQL, Liquibase, Redis, RabbitMQ, Kafka  
**Architecture**: Microservice-based Spring Boot application

## Core Components

### 1. Authentication & Authorization
- **Spring Security**: Provides comprehensive security framework
- **Multi-environment Support**: Dev, UAT, Production configurations
- **Database-driven**: User and role management via PostgreSQL

### 2. Database Management
- **Liquibase**: Database schema migration and version control
- **PostgreSQL 18**: Primary database with schema-per-environment
- **Multi-schema Design**: Separate schemas for dev_ecm, uat_ecm, prod_ecm
- **Data Seeding**: Environment-specific test data and initial configurations

### 3. Messaging & Integration
- **RabbitMQ**: AMQP messaging for service communication
- **Kafka**: Event streaming and data pipelines
- **Redis**: Caching and session management

### 4. Development Infrastructure
- **Gradle Build System**: With comprehensive Liquibase integration
- **Docker Support**: Containerized development environment
- **TestContainers**: Integration testing with real database instances
- **Multi-profile Configuration**: Environment-specific application properties

## Project Structure

```
src/main/java/com/ecm/security/identity/
├── EcmIdentityServiceApplication.java    # Main Spring Boot application
└── [domain packages]                     # Business logic packages

src/main/resources/
├── application.properties               # Base configuration
├── application-{env}.properties         # Environment-specific configs
└── db/changelog/                      # Liquibase migration scripts
    ├── db.changelog-master.xml         # Main changelog
    ├── changes/                        # Schema changes
    └── data/{env}/                     # Environment-specific data seeding

src/test/java/com/ecm/security/identity/
└── EcmIdentityServiceApplicationTests.java
```

## Key Features

### Database Schema Management
- **Version Control**: All database changes tracked via Liquibase
- **Environment Isolation**: Separate schemas prevent cross-environment conflicts
- **Rollback Support**: Full rollback capabilities for safe deployments
- **Validation**: Automated changelog validation and dry-run capabilities

### Development Workflow
- **Local Development**: Docker-based local environment setup
- **Multi-environment**: Seamless deployment across dev, uat, prod
- **CI/CD Ready**: Gradle tasks for automated database operations
- **Testing**: Comprehensive test suite with TestContainers

### Security & Best Practices
- **Spring Security**: Enterprise-grade authentication and authorization
- **Configuration Management**: Environment variables and secure credential handling
- **Code Quality**: Lombok for boilerplate reduction, comprehensive logging
- **Documentation**: Detailed implementation guides and best practices

## Development Commands

### Environment Management
```bash
make docker-up          # Start local Docker environment
make quickstart         # Initialize development setup
make run-dev           # Start development profile
make run-uat           # Start UAT profile
```

### Database Operations
```bash
make db-validate       # Validate Liquibase changelogs
make db-update         # Apply database changes
make db-rollback-count # Rollback specific changesets
```

### Build & Test
```bash
./gradlew build        # Build the application
./gradlew test         # Run test suite
./gradlew liquibaseValidate  # Validate database migrations
```

## Environment Configuration

### Local Development
- **Database**: PostgreSQL on localhost:5432
- **Schema**: dev_ecm
- **Credentials**: dev_ecm / dev_ecm!23456
- **Features**: Full data seeding and debugging enabled

### Production Deployment
- **Database**: Production PostgreSQL cluster
- **Schema**: prod_ecm
- **Security**: Production-grade authentication and monitoring
- **Performance**: Optimized configurations and caching

## Technology Decisions

### Why This Stack?
- **Java 21**: Latest LTS with performance improvements and modern features
- **Spring Boot 3.5.6**: Mature, enterprise-ready framework with excellent ecosystem
- **PostgreSQL**: Reliable, feature-rich relational database
- **Liquibase**: Industry-standard database migration tool
- **Redis**: High-performance caching and session storage
- **RabbitMQ/Kafka**: Robust messaging for microservice communication

### Architecture Benefits
- **Scalability**: Microservice design allows independent scaling
- **Maintainability**: Clear separation of concerns and comprehensive testing
- **Reliability**: Database versioning and rollback capabilities
- **Security**: Enterprise-grade authentication and authorization

## Next Steps
1. Review implementation documentation in `/docs/Implementation/`
2. Set up local development environment using provided Makefile commands
3. Explore database schema and migration patterns
4. Understand security configuration and authentication flows
5. Familiarize with testing strategy and CI/CD pipeline

## Contact & Support
- **Documentation**: `/docs/` directory contains detailed implementation guides
- **Configuration**: Environment-specific settings in `application-{env}.properties`
- **Build Scripts**: Makefile and Gradle build configuration for automation