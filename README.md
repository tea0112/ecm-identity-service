# ECM Identity Service

A comprehensive Enterprise Identity and Access Management (IAM) service built with Spring Boot, implementing advanced security features and compliance requirements.

## Overview

The ECM Identity Service provides a complete IAM solution with the following capabilities:

### 🔐 Authentication & Sessions
- **Multi-factor Authentication (MFA)**: TOTP, WebAuthn/FIDO2, SMS, Email
- **Passwordless Authentication**: WebAuthn, Magic Links
- **Risk-based Authentication**: Contextual security with step-up authentication
- **Session Management**: Device tracking, concurrent session control, proactive invalidation
- **Account Security**: Secure password policies with Argon2 hashing

### 🛡️ Authorization & Access Control
- **Policy Engine**: ABAC (Attribute-Based) and ReBAC (Relationship-Based) access control
- **Fine-grained Permissions**: Role-based access with time-bound and conditional grants
- **Break-glass Access**: Emergency access procedures with dual approval
- **Delegation**: Secure permission delegation with audit trails

### 🏢 Multi-tenancy & Federation
- **Tenant Isolation**: Complete data separation with tenant-specific policies
- **Enterprise SSO**: SAML 2.0 and OpenID Connect federation
- **SCIM Protocol**: Automated user provisioning and lifecycle management
- **Account Linking**: Secure identity merging with reversible operations

### 📊 Compliance & Audit
- **Immutable Audit Logs**: Cryptographically chained audit trail
- **Data Privacy**: GDPR/CCPA compliance with right to erasure
- **Forensic Capabilities**: Security timeline reconstruction
- **Retention Policies**: Configurable data retention with legal hold support

### 🔧 Developer Experience
- **OAuth2/OIDC Provider**: Standards-compliant authorization server
- **RESTful APIs**: Comprehensive API coverage with OpenAPI documentation
- **Admin Console**: Web-based management interface
- **Self-service Portal**: User account management and security settings

## Architecture

### Technology Stack
- **Framework**: Spring Boot 3.5.6
- **Security**: Spring Security 6.x with OAuth2 Authorization Server
- **Database**: PostgreSQL with JPA/Hibernate
- **Caching**: Redis for session and permission caching
- **Messaging**: RabbitMQ and Apache Kafka for event streaming
- **Authentication**: WebAuthn/FIDO2, TOTP, Argon2 password hashing
- **Monitoring**: Micrometer with Prometheus metrics

### Key Components

```
┌─────────────────────────────────────────────────────────────┐
│                    ECM Identity Service                     │
├─────────────────────────────────────────────────────────────┤
│  Authentication APIs  │  Authorization Engine  │  Admin UI  │
├─────────────────────────────────────────────────────────────┤
│     OAuth2/OIDC       │    Policy Engine       │   Audit    │
│   Authorization       │    (ABAC/ReBAC)        │  Logging   │
│      Server           │                        │            │
├─────────────────────────────────────────────────────────────┤
│                  Multi-tenant Core                          │
├─────────────────────────────────────────────────────────────┤
│   PostgreSQL    │      Redis Cache     │    Message Queue   │
│   (Primary DB)  │   (Sessions/Perms)   │   (Events/Audit)   │
└─────────────────────────────────────────────────────────────┘
```

## Prerequisites

### Required Software
- **Java Development Kit (JDK) 21 or higher**
  ```bash
  # Install OpenJDK 21 on Fedora/RHEL
  sudo dnf install java-21-openjdk-devel
  
  # Install OpenJDK 21 on Ubuntu/Debian
  sudo apt install openjdk-21-jdk
  
  # Verify installation
  javac -version
  ```

- **PostgreSQL 15+**
  ```bash
  # Install PostgreSQL
  sudo dnf install postgresql postgresql-server postgresql-contrib
  
  # Initialize and start
  sudo postgresql-setup --initdb
  sudo systemctl enable postgresql
  sudo systemctl start postgresql
  
  # Create database and user
  sudo -u postgres createdb ecm_identity
  sudo -u postgres createuser ecm_user
  sudo -u postgres psql -c "ALTER USER ecm_user WITH PASSWORD 'ecm_password';"
  sudo -u postgres psql -c "GRANT ALL PRIVILEGES ON DATABASE ecm_identity TO ecm_user;"
  ```

- **Redis 6+**
  ```bash
  # Install Redis
  sudo dnf install redis
  sudo systemctl enable redis
  sudo systemctl start redis
  ```

### Optional Components
- **RabbitMQ** (for advanced messaging)
- **Apache Kafka** (for event streaming)
- **Prometheus** (for metrics collection)

## Quick Start

### 1. Clone and Build
```bash
git clone <repository-url>
cd ecm-identity-service

# Build the project
./gradlew build

# Run tests
./gradlew test
```

### 2. Database Setup
```bash
# Create database (if not already created)
createdb ecm_identity

# The application will automatically run Flyway migrations on startup
```

### 3. Configuration
Copy and customize the application properties:
```bash
cp src/main/resources/application.properties src/main/resources/application-local.properties
```

Edit the local configuration:
```properties
# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/ecm_identity
spring.datasource.username=ecm_user
spring.datasource.password=ecm_password

# Security Configuration
ecm.security.jwt.secret=your-secure-256-bit-jwt-secret-key-here

# WebAuthn Configuration
ecm.webauthn.rp-id=localhost
ecm.webauthn.rp-name=ECM Identity Service
ecm.webauthn.rp-origin=http://localhost:8080
```

### 4. Run the Application
```bash
# Run with local profile
./gradlew bootRun --args='--spring.profiles.active=local'

# Or run the JAR
java -jar build/libs/ecm-identity-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

The application will be available at: http://localhost:8080

### 5. Default Access
- **Admin Console**: http://localhost:8080/admin
- **Default Admin**: admin@ecm.local / admin123!
- **API Documentation**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health

## API Endpoints

### OAuth2/OIDC Endpoints
- `GET /oauth2/authorize` - Authorization endpoint
- `POST /oauth2/token` - Token endpoint
- `GET /oauth2/jwks` - JWK Set endpoint
- `GET /.well-known/openid_configuration` - Discovery endpoint
- `GET /userinfo` - UserInfo endpoint

### Authentication APIs
- `POST /api/auth/login` - Username/password login
- `POST /api/auth/webauthn/register` - WebAuthn registration
- `POST /api/auth/webauthn/authenticate` - WebAuthn authentication
- `POST /api/auth/mfa/setup` - MFA setup
- `POST /api/auth/magic-link` - Magic link authentication

### User Management APIs
- `GET /api/users` - List users (admin)
- `POST /api/users` - Create user
- `GET /api/users/{id}` - Get user details
- `PUT /api/users/{id}` - Update user
- `DELETE /api/users/{id}` - Delete user

### Session Management APIs
- `GET /api/sessions` - List user sessions
- `DELETE /api/sessions/{id}` - Terminate session
- `GET /api/devices` - List user devices
- `DELETE /api/devices/{id}` - Remove device

## Security Features

### Password Security
- **Argon2** hashing with configurable parameters
- **Password policies** with complexity requirements
- **Breach detection** integration with HaveIBeenPwned
- **Secure recovery** with rate limiting

### Multi-Factor Authentication
- **TOTP** (Time-based One-Time Passwords)
- **WebAuthn/FIDO2** for hardware security keys
- **SMS** and **Email** verification
- **Backup codes** for account recovery

### Session Security
- **Device fingerprinting** and trust scoring
- **Impossible travel** detection
- **Concurrent session** management
- **Risk-based** authentication triggers

### Audit and Compliance
- **Immutable audit logs** with cryptographic integrity
- **PII redaction** capabilities
- **Data retention** policies
- **Legal hold** functionality

## Configuration Reference

### Security Settings
```yaml
ecm:
  security:
    jwt:
      secret: ${JWT_SECRET}
      access-token-expiration: 900000  # 15 minutes
      refresh-token-expiration: 604800000  # 7 days
    argon2:
      memory: 65536
      iterations: 3
      parallelism: 4
    rate-limiting:
      enabled: true
```

### Multi-tenancy Settings
```yaml
ecm:
  multitenancy:
    enabled: true
    default-tenant: default
    header-name: X-Tenant-ID
```

### WebAuthn Settings
```yaml
ecm:
  webauthn:
    rp-id: localhost
    rp-name: ECM Identity Service
    rp-origin: http://localhost:8080
```

## Development

### Project Structure
```
src/main/java/com/ecm/security/identity/
├── config/           # Configuration classes
├── domain/           # Entity models
├── repository/       # Data access layer
├── service/          # Business logic
├── controller/       # REST controllers
├── security/         # Security components
└── audit/           # Audit logging
```

### Testing
```bash
# Run all tests
./gradlew test

# Run integration tests
./gradlew integrationTest

# Run with coverage
./gradlew test jacocoTestReport
```

### Database Migrations
Flyway migrations are located in `src/main/resources/db/migration/`:
- `V1__Create_core_tables.sql` - Initial schema
- `V2__Insert_default_data.sql` - Default tenant and admin user

### Monitoring
The application exposes Prometheus metrics at `/actuator/prometheus`:
- Authentication success/failure rates
- Session creation and termination
- Authorization decisions
- API response times

## Production Deployment

### Environment Variables
```bash
# Required
JWT_SECRET=your-production-jwt-secret-key-256-bits
DATABASE_URL=jdbc:postgresql://prod-db:5432/ecm_identity
DATABASE_USERNAME=ecm_user
DATABASE_PASSWORD=secure-password

# Optional
REDIS_URL=redis://prod-redis:6379
RABBITMQ_URL=amqp://prod-rabbitmq:5672
```

### Docker Deployment
```dockerfile
FROM openjdk:21-jdk-slim
COPY build/libs/ecm-identity-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### Security Considerations
- Use a **hardware security module (HSM)** for JWT signing keys
- Enable **TLS/SSL** with proper certificates
- Configure **firewall rules** to restrict access
- Set up **log aggregation** for security monitoring
- Implement **backup and disaster recovery** procedures

## Contributing

### Code Style
- Follow Java coding conventions
- Use meaningful variable and method names
- Add comprehensive JavaDoc comments
- Write unit tests for all new functionality

### Security Guidelines
- Never log sensitive information (passwords, tokens)
- Validate all input parameters
- Use parameterized queries to prevent SQL injection
- Implement proper error handling without information disclosure

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Support

For support and questions:
- **Documentation**: [Wiki](https://github.com/ecm/identity-service/wiki)
- **Issues**: [GitHub Issues](https://github.com/ecm/identity-service/issues)
- **Discussions**: [GitHub Discussions](https://github.com/ecm/identity-service/discussions)

---

**⚠️ Security Notice**: This is a security-critical application. Please follow secure development practices and conduct thorough security reviews before deploying to production environments.
