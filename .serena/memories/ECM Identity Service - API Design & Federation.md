# ECM Identity Service - API Design & Federation Protocols

## REST API Design

### Authentication Endpoints
```
POST /api/v1/auth/login           # Standard login with credentials
POST /api/v1/auth/mfa/verify      # MFA verification (TOTP/WebAuthn)
POST /api/v1/auth/magic-link      # Magic link authentication
POST /api/v1/auth/device-code     # Device code flow for CLI/headless
POST /api/v1/auth/refresh         # Token refresh
POST /api/v1/auth/logout          # Session termination
```

### User Management Endpoints
```
GET    /api/v1/users              # List users (admin)
POST   /api/v1/users              # Create user
GET    /api/v1/users/{id}         # Get user details
PUT    /api/v1/users/{id}         # Update user
DELETE /api/v1/users/{id}         # Delete user (soft delete)
GET    /api/v1/users/me           # Current user profile
PUT    /api/v1/users/me           # Update current user
```

### Session Management Endpoints
```
GET    /api/v1/sessions           # List user sessions
GET    /api/v1/sessions/{id}      # Get session details
DELETE /api/v1/sessions/{id}      # Terminate session
GET    /api/v1/sessions/me        # Current session info
```

### Authorization Endpoints
```
POST   /api/v1/authz/check        # Single authorization check
POST   /api/v1/authz/batch        # Batch authorization checks
GET    /api/v1/authz/policies     # List policies
POST   /api/v1/authz/policies     # Create policy
PUT    /api/v1/authz/policies/{id} # Update policy
```

### Admin Console Endpoints
```
GET    /api/v1/admin/users        # Admin user management
GET    /api/v1/admin/sessions     # Global session monitoring
GET    /api/v1/admin/audit        # Audit log viewing
GET    /api/v1/admin/policies     # Policy management
GET    /api/v1/admin/tenants      # Tenant management
```

## OAuth2 & OIDC Implementation

### Authorization Server Endpoints
```
GET    /oauth2/authorize          # Authorization endpoint
POST   /oauth2/token              # Token endpoint
POST   /oauth2/introspect         # Token introspection
POST   /oauth2/revoke             # Token revocation
GET    /oauth2/userinfo           # OIDC UserInfo endpoint
GET    /.well-known/openid_configuration # OIDC Discovery
GET    /.well-known/jwks.json     # JSON Web Key Set
```

### Supported Grant Types
- **Authorization Code**: Standard web app flow with PKCE
- **Client Credentials**: Service-to-service authentication
- **Device Code**: For CLI and IoT devices
- **Refresh Token**: Long-lived token rotation

### OIDC Claims
- **Standard Claims**: sub, name, email, email_verified, etc.
- **Custom Claims**: tenant_id, roles, permissions, risk_score
- **Scope-Based Claims**: Granular claim release based on scopes

## SAML 2.0 Implementation

### Service Provider (SP) Configuration
```xml
<EntityDescriptor entityID="https://ecm-identity.example.com">
  <SPSSODescriptor>
    <AssertionConsumerService 
      Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
      Location="https://ecm-identity.example.com/saml/acs"/>
    <SingleLogoutService
      Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
      Location="https://ecm-identity.example.com/saml/slo"/>
  </SPSSODescriptor>
</EntityDescriptor>
```

### SAML Endpoints
```
POST   /saml/acs                  # Assertion Consumer Service
GET    /saml/slo                  # Single Logout Service
GET    /saml/metadata             # SP Metadata
POST   /saml/sso                  # Initiate SSO
```

### Attribute Mapping
- **NameID**: Maps to user email or username
- **Custom Attributes**: Role mapping, tenant assignment
- **Just-In-Time Provisioning**: Automatic user creation

## SCIM 2.0 Protocol Implementation

### SCIM Endpoints
```
GET    /scim/v2/Users             # List users
POST   /scim/v2/Users             # Create user
GET    /scim/v2/Users/{id}        # Get user
PUT    /scim/v2/Users/{id}        # Update user (full)
PATCH  /scim/v2/Users/{id}        # Update user (partial)
DELETE /scim/v2/Users/{id}        # Delete user
GET    /scim/v2/Groups            # List groups/roles
POST   /scim/v2/Groups            # Create group
```

### SCIM Resource Schema
```json
{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "userName": "john.doe@example.com",
  "emails": [{"value": "john.doe@example.com", "primary": true}],
  "active": true,
  "roles": [{"value": "user", "display": "Standard User"}],
  "urn:ietf:params:scim:schemas:extension:enterprise:2.0:User": {
    "employeeNumber": "12345",
    "department": "Engineering"
  }
}
```

### SCIM Filtering & Pagination
- **Filtering**: `filter=userName eq "john.doe@example.com"`
- **Sorting**: `sortBy=userName&sortOrder=ascending`
- **Pagination**: `startIndex=1&count=20`

## WebAuthn/FIDO2 API

### Registration Flow
```
POST   /api/v1/webauthn/register/begin    # Start registration
POST   /api/v1/webauthn/register/finish   # Complete registration
```

### Authentication Flow
```
POST   /api/v1/webauthn/authenticate/begin  # Start authentication
POST   /api/v1/webauthn/authenticate/finish # Complete authentication
```

### Credential Management
```
GET    /api/v1/webauthn/credentials       # List user credentials
DELETE /api/v1/webauthn/credentials/{id}  # Remove credential
PUT    /api/v1/webauthn/credentials/{id}  # Update credential name
```

## Data Export & Privacy APIs

### GDPR Compliance Endpoints
```
GET    /api/v1/privacy/export            # Request data export
DELETE /api/v1/privacy/delete            # Request account deletion
GET    /api/v1/privacy/consent           # View consent status
POST   /api/v1/privacy/consent           # Update consent
```

### Data Export Format
```json
{
  "exportId": "exp_123456",
  "userId": "user_789",
  "exportedAt": "2024-01-15T10:30:00Z",
  "dataIntegrityHash": "sha256_hash_here",
  "digitalSignature": "signature_here",
  "data": {
    "profile": {...},
    "sessions": [...],
    "auditEvents": [...],
    "consents": [...]
  }
}
```

## Monitoring & Health Check APIs

### Health & Status Endpoints
```
GET    /actuator/health              # Application health
GET    /actuator/metrics             # Prometheus metrics
GET    /actuator/info                # Application info
GET    /api/v1/system/status         # System status
```

### Security Monitoring
```
GET    /api/v1/security/incidents    # Security incidents
GET    /api/v1/security/threats      # Threat detection
GET    /api/v1/security/risk-scores  # User risk scores
```

## Error Response Format

### Standard Error Structure
```json
{
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "The provided credentials are invalid",
    "details": "Username or password is incorrect",
    "timestamp": "2024-01-15T10:30:00Z",
    "requestId": "req_123456",
    "documentation": "https://docs.ecm-identity.com/errors/invalid-credentials"
  }
}
```

### Common Error Codes
- **INVALID_CREDENTIALS**: Authentication failure
- **INSUFFICIENT_PERMISSIONS**: Authorization failure
- **MFA_REQUIRED**: Multi-factor authentication needed
- **ACCOUNT_LOCKED**: Account temporarily locked
- **TOKEN_EXPIRED**: Access token has expired
- **TENANT_SUSPENDED**: Tenant account suspended

This API design provides comprehensive coverage for all authentication, authorization, federation, and compliance requirements while maintaining RESTful principles and security best practices.