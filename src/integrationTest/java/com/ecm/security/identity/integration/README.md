# ECM Identity Service - Integration Tests

This directory contains comprehensive integration tests that validate all requirements from `requirements.md`.

## Test Coverage

### ✅ Functional Requirements (FR)

#### FR1 - Authentication & Sessions
**File:** `FR1AuthenticationSessionsIntegrationTest.java`
- **FR1.1** - Credential Login & Passwordless Authentication
- **FR1.2** - Token Issuance & Refresh with Rotation
- **FR1.3** - Multi-Factor & Step-Up Authentication  
- **FR1.4** - Device & Session Management
- **FR1.5** - Secure Account Recovery & Fallbacks
- **FR1.6** - Account Linking & Merging
- **FR1.7** - Age Gates & Parental Consent
- **FR1.8** - Proactive Session Invalidation based on Risk Signals

#### FR2 - Identity, Federation & Protocols
**File:** `FR2IdentityFederationProtocolsIntegrationTest.java`
- **FR2.1** - User & Identity Lifecycle Management
- **FR2.2** - OAuth2/OIDC Provider Compliance
- **FR2.3** - Enterprise Federation (SSO) - SAML 2.0 and OIDC
- **FR2.4** - Directory Sync (SCIM) Protocol Support
- **FR2.5** - Device & CLI Authentication (Device Code Flow)
- **FR2.6** - Service & Non-Human Principals Management

#### FR3 - Authorization & Access Control
**File:** `FR3AuthorizationAccessControlIntegrationTest.java`
- **FR3.1** - Policy Engine (ABAC/ReBAC) with Precedence Rules
- **FR3.2** - Contextual Authorization API with Batch Decisions and TOCTOU Protection
- **FR3.3** - Continuous Authorization for Long-Lived Connections
- **FR3.4** - Time-Bound, JIT & Emergency Break-Glass Access
- **FR3.5** - Advanced Delegation & Scoped Administration
- **FR3.6** - Granular Consent Management

#### FR4 - Multi-Tenancy
**File:** `FR4MultiTenancyIntegrationTest.java`
- **FR4.1** - Tenant Isolation & Configuration with Per-Tenant Security Policies
- **FR4.2** - Tenant Lifecycle Management - Splitting and Merging
- **FR4.3** - Cross-Tenant Collaboration - Guest Users and Sharing

### ✅ Non-Functional Requirements (NFR)

#### NFR1 - Security Hardening
**File:** `NFR1SecurityHardeningIntegrationTest.java`
- **NFR1.1** - Threat Mitigation: Credential Stuffing, MFA Fatigue, Replay Attacks
- **NFR1.2** - Advanced Token & Session Hygiene
- **NFR1.3** - Rate Limiting & Lockout Protection
- **NFR1.4** - Secrets & Supply-Chain Security
- **NFR1.5** - Backup & Restore Token Safety
- **NFR1.8** - Cryptographic Key Lifecycle Policy
- **NFR1.9** - Cryptographic Agility

### ✅ Key Acceptance & Test Scenarios
**File:** `KeyAcceptanceTestScenariosIntegrationTest.java`
- **Instant De-provisioning** - User deletion must reject session/token within 1 second
- **Admin Impersonation Flow** - Must prompt for justification and display persistent banner
- **Break-Glass Account Access** - Must trigger multi-person approval and high-severity alert
- **Key Compromise Drill** - Must trigger automated workflow and ensure old token rejection
- **Emergency Policy Rollback** - Must verify instant rollback within 5 minutes

## Test Architecture

### Integration Test Framework
- **Spring Boot Test** - Full application context with `@SpringBootTest`
- **Testcontainers** - PostgreSQL database for realistic testing
- **TestRestTemplate** - HTTP client for API testing
- **JUnit 5** - Test framework with comprehensive assertions

### Test Data Management
- **@Transactional** - Automatic rollback after each test
- **@BeforeEach** - Fresh test data setup
- **Repository injection** - Direct database access for verification

### Validation Approach
- **End-to-End API Testing** - Real HTTP requests and responses
- **Database State Verification** - Direct repository queries
- **Audit Event Validation** - Comprehensive audit trail verification
- **Timing Requirements** - Critical performance validations
- **Security Assertions** - Threat mitigation and compliance checks

## Running Integration Tests

### Prerequisites
- Java 25
- Docker (for Testcontainers PostgreSQL)
- Gradle 8.12+

### Commands
```bash
# Run all integration tests
./gradlew integrationTest

# Run specific test class
./gradlew integrationTest --tests "FR1AuthenticationSessionsIntegrationTest"

# Run with detailed logging
./gradlew integrationTest --info

# Run with Java 25 compatibility
JAVA_HOME=/usr/lib/jvm/java-25-openjdk ./gradlew integrationTest
```

### Make Commands
```bash
# Run all tests (unit + integration)
make test-all

# Run integration tests specifically
make test-integration
```

## Test Environment

### Database
- **PostgreSQL 15** via Testcontainers
- **Automatic schema creation** via Flyway migrations
- **Isolated test database** per test execution

### Security Context
- **Mock authentication tokens** for speed
- **Real authorization flows** for accuracy
- **Comprehensive audit logging** validation

### Performance Validation
- **1-second de-provisioning** requirement
- **5-minute policy rollback** requirement
- **Sub-100ms authorization** checks (where applicable)

## Coverage Validation

Each integration test validates:

1. **Functional Behavior** - Feature works as specified
2. **Security Requirements** - Threats are mitigated
3. **Audit Compliance** - All actions are logged
4. **Performance Criteria** - Timing requirements met
5. **Data Integrity** - No data leakage or corruption
6. **Error Handling** - Proper error responses and logging

## Test Maintenance

### Adding New Tests
1. Create test class in appropriate package
2. Follow naming convention: `[Requirement]IntegrationTest.java`
3. Use `@DisplayName` with requirement reference
4. Include comprehensive audit event validation
5. Add timing assertions for critical paths

### Test Data
- Use builder patterns for domain objects
- Create minimal test data in `@BeforeEach`
- Use meaningful test identifiers
- Clean up automatically via `@Transactional`

### Assertions
- Validate HTTP status codes
- Check response body structure
- Verify database state changes
- Confirm audit event creation
- Test timing requirements
- Validate security headers and tokens

## Continuous Integration

These integration tests are designed to run in CI/CD pipelines:
- **Docker-based** - No external dependencies
- **Deterministic** - Consistent results across environments  
- **Comprehensive** - Full requirements coverage
- **Fast** - Parallel execution where possible
- **Reliable** - Proper cleanup and isolation

