# ECM Identity Service - Development Guide

## 🚀 Quick Start

### Prerequisites
- Java 21 or higher
- Docker and Docker Compose
- Make (for convenient commands)

### Setup Development Environment
```bash
# Setup everything (Java check + database services)
make dev-setup

# Check environment status
make dev-status

# Start the application
make run
```

## 📋 Available Make Commands

The project includes a comprehensive Makefile with commands for all development tasks. Use `make help` to see all available commands.

### Development Commands
```bash
make help              # Show all available commands
make check-java        # Check Java version
make clean             # Clean build artifacts
make build             # Build the application
make compile           # Compile source code only
make format            # Format code using Google Java Format
make lint              # Run static code analysis
```

### Testing Commands
```bash
make test              # Run unit tests
make test-integration  # Run integration tests with Testcontainers
make test-all          # Run all tests (unit + integration)
make test-coverage     # Generate test coverage report
make test-watch        # Run tests in watch mode (continuous testing)
```

### Database Commands
```bash
make db-start          # Start PostgreSQL and Redis using Docker Compose
make db-stop           # Stop database services
make db-migrate        # Run database migrations
make db-reset          # Reset database (clean + migrate)
make db-info           # Show database migration info
```

### Application Commands
```bash
make run               # Run the application in development mode
make run-prod          # Run the application in production mode
make run-debug         # Run the application with debug enabled
make jar               # Build executable JAR
make run-jar           # Run the application from JAR
```

### Docker Commands
```bash
make docker-build      # Build Docker image
make docker-run        # Run application in Docker container
make docker-compose-up # Start all services using Docker Compose
make docker-compose-down # Stop all services using Docker Compose
make docker-logs       # Show Docker Compose logs
```

### Quality Assurance Commands
```bash
make verify            # Run full verification (lint + tests + coverage)
make security-scan     # Run security vulnerability scan
make performance-test  # Run performance tests
```

### Environment Management
```bash
make dev-setup         # Setup development environment
make dev-reset         # Reset development environment
make dev-status        # Check development environment status
make env-check         # Check environment configuration
```

### Documentation Commands
```bash
make docs              # Generate API documentation
make docs-serve        # Serve documentation locally on port 8000
```

### Utility Commands
```bash
make logs              # Show application logs
make health            # Check application health
make metrics           # Show application metrics
make clean-all         # Clean everything (build + stop services)
```

## 🔧 Configuration

### Environment Variables
Copy `env.example` to `.env` and configure your environment:

```bash
cp env.example .env
# Edit .env with your configuration
```

Key configuration options:
- `SPRING_PROFILES_ACTIVE`: Active Spring profiles (dev, test, prod)
- `DATABASE_URL`: PostgreSQL connection URL
- `REDIS_HOST`: Redis server hostname
- `ECM_SECURITY_JWT_SECRET`: JWT signing secret (256-bit)

### Database Setup
The application uses PostgreSQL with Flyway migrations:

```bash
# Start database with Docker Compose
make db-start

# Run migrations
make db-migrate

# Check migration status
make db-info
```

### Redis Setup
Redis is used for session storage and caching:

```bash
# Redis is included in db-start command
make db-start

# Check Redis status in docker-compose.yml
```

## 🧪 Testing

### Unit Tests
Fast, isolated tests using mocks:
```bash
make test
```

### Integration Tests
Full integration tests with Testcontainers (requires Docker):
```bash
make test-integration
```

The integration tests include:
- **AuthenticationIntegrationTest**: FR1 Authentication & Sessions
- **AuthorizationIntegrationTest**: FR3 Authorization & Access Control
- **ComplianceIntegrationTest**: NFR2 Observability & Compliance
- **MultiTenancyIntegrationTest**: FR4 Multi-Tenancy
- **AcceptanceIntegrationTest**: Key Acceptance Scenarios

### Test Coverage
Generate coverage reports:
```bash
make test-coverage
# Report available at: build/reports/jacoco/test/html/index.html
```

### Continuous Testing
Run tests automatically on code changes:
```bash
make test-watch
```

## 🏗️ Build and Deployment

### Local Development
```bash
# Run with auto-reload
make run

# Run with debug port 5005
make run-debug
```

### Production Build
```bash
# Build production JAR
make jar

# Run production build
make run-jar
```

### Docker Deployment
```bash
# Build Docker image
make docker-build

# Run with Docker Compose (includes database)
make docker-compose-up

# Check logs
make docker-logs

# Stop services
make docker-compose-down
```

## 🔍 Quality Assurance

### Code Quality
The project includes comprehensive quality checks:
- **Checkstyle**: Code style compliance
- **SpotBugs**: Static analysis for bugs
- **Jacoco**: Test coverage analysis

```bash
# Run all quality checks
make verify

# Individual checks
make lint              # Static analysis
make test-coverage     # Coverage analysis
make security-scan     # Security vulnerability scan
```

### Security Scanning
```bash
make security-scan
# Report available at: build/reports/dependency-check-report.html
```

## 📊 Monitoring and Health Checks

### Application Health
```bash
# Check application health
make health

# View metrics
make metrics

# View logs
make logs
```

### Health Endpoints
When running, the application exposes:
- Health: http://localhost:8080/actuator/health
- Metrics: http://localhost:8080/actuator/metrics
- Prometheus: http://localhost:8080/actuator/prometheus

### Monitoring with Grafana
```bash
# Start full monitoring stack
make docker-compose-up

# Access Grafana at http://localhost:3000 (admin/admin)
# Access Prometheus at http://localhost:9090
```

## 🚨 Troubleshooting

### Common Issues

#### Java Version Issues
```bash
# Check Java version
make check-java

# Ensure Java 21+ is installed and JAVA_HOME is set
java -version
echo $JAVA_HOME
```

#### Database Connection Issues
```bash
# Check database status
make dev-status

# Restart database services
make db-stop
make db-start

# Check logs
make docker-logs
```

#### Docker Issues
```bash
# Clean Docker system
docker system prune -f

# Restart Docker services
make docker-compose-down
make docker-compose-up
```

#### Test Failures
```bash
# Clean and rebuild
make clean
make build

# Run specific test type
make test-unit         # Unit tests only
make test-integration  # Integration tests only
```

### Performance Issues
```bash
# Check application metrics
make metrics

# Run performance tests
make performance-test

# Monitor with Grafana
# Access http://localhost:3000 after running docker-compose-up
```

## 🔧 Development Workflow

### Typical Development Cycle
1. **Setup**: `make dev-setup`
2. **Code**: Make your changes
3. **Test**: `make test` (fast unit tests)
4. **Verify**: `make verify` (full quality checks)
5. **Integration**: `make test-integration` (when needed)
6. **Run**: `make run` (test locally)

### Before Committing
```bash
# Run full verification
make verify

# Ensure integration tests pass
make test-integration

# Check formatting
make format
```

### CI/CD Integration
The Makefile supports CI/CD workflows:
```bash
make ci-build          # CI build pipeline
make ci-test           # CI test pipeline
make ci-security       # CI security pipeline
make ci-publish        # CI publish pipeline
```

## 📚 Additional Resources

- [Requirements Documentation](requirements.md)
- [API Documentation](http://localhost:8080/swagger-ui.html) (when running)
- [Test Coverage Report](build/reports/jacoco/test/html/index.html)
- [Security Scan Report](build/reports/dependency-check-report.html)

## 🆘 Getting Help

1. **Check Status**: `make dev-status`
2. **View Logs**: `make logs`
3. **Health Check**: `make health`
4. **Environment**: `make env-check`
5. **Clean Reset**: `make dev-reset`

For more detailed help on any command, use `make help` or refer to the Makefile comments.
