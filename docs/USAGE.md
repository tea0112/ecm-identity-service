# ECM Identity Service - Usage Guide

## Table of Contents
- [Overview](#overview)
- [Quick Start](#quick-start)
- [Authentication Flows](#authentication-flows)
- [API Integration](#api-integration)
- [Multi-Tenancy](#multi-tenancy)
- [Admin Console](#admin-console)
- [Security Features](#security-features)
- [Compliance & Audit](#compliance--audit)
- [Troubleshooting](#troubleshooting)

## Overview

The ECM Identity Service is a comprehensive Enterprise Identity and Access Management (IAM) solution that provides:

- **Multi-Factor Authentication** (MFA) with WebAuthn, TOTP, and magic links
- **Advanced Authorization** with ABAC/ReBAC policy engine
- **Multi-Tenancy** with complete data isolation
- **OAuth2/OIDC Provider** for application integration
- **Enterprise Federation** with SAML 2.0 and SCIM
- **Compliance Features** for GDPR, CCPA, and audit requirements

## Quick Start

### 1. Service Setup

```bash
# Start the service with Docker Compose
make docker-compose-up

# Or run locally
make run
```

The service will be available at:
- **Main Service**: http://localhost:8080
- **Admin Console**: http://localhost:8080/admin
- **API Documentation**: http://localhost:8080/swagger-ui.html

### 2. Default Credentials

```
Admin User: admin@ecm.local
Password: admin123!
```

### 3. Health Check

```bash
curl http://localhost:8080/actuator/health
```

## Authentication Flows

### 1. Password Authentication

#### Login Request
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user@example.com",
    "password": "securePassword123!",
    "tenantId": "default"
  }'
```

#### Response
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "user-123",
    "email": "user@example.com",
    "firstName": "John",
    "lastName": "Doe"
  }
}
```

### 2. WebAuthn (Passwordless) Authentication

#### Registration
```javascript
// Client-side WebAuthn registration
const credential = await navigator.credentials.create({
  publicKey: {
    challenge: new Uint8Array(32),
    rp: {
      name: "ECM Identity Service",
      id: "localhost"
    },
    user: {
      id: new TextEncoder().encode("user-123"),
      name: "user@example.com",
      displayName: "John Doe"
    },
    pubKeyCredParams: [
      { type: "public-key", alg: -7 },
      { type: "public-key", alg: -257 }
    ],
    authenticatorSelection: {
      authenticatorAttachment: "platform"
    }
  }
});

// Send to server
fetch('/api/auth/webauthn/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    credential: credential,
    userId: 'user-123'
  })
});
```

#### Authentication
```javascript
// Client-side WebAuthn authentication
const assertion = await navigator.credentials.get({
  publicKey: {
    challenge: new Uint8Array(32),
    allowCredentials: [{
      type: 'public-key',
      id: credentialId,
      transports: ['internal']
    }]
  }
});

// Send to server
fetch('/api/auth/webauthn/authenticate', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    assertion: assertion,
    userId: 'user-123'
  })
});
```

### 3. Multi-Factor Authentication (MFA)

#### Setup TOTP
```bash
curl -X POST http://localhost:8080/api/auth/mfa/setup \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "TOTP"
  }'
```

#### Response
```json
{
  "secret": "JBSWY3DPEHPK3PXP",
  "qrCode": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
  "backupCodes": [
    "12345678",
    "87654321",
    "11223344"
  ]
}
```

#### Verify MFA
```bash
curl -X POST http://localhost:8080/api/auth/mfa/verify \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "type": "TOTP",
    "code": "123456"
  }'
```

### 4. Magic Link Authentication

#### Request Magic Link
```bash
curl -X POST http://localhost:8080/api/auth/magic-link \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "tenantId": "default"
  }'
```

#### Verify Magic Link
```bash
curl -X POST http://localhost:8080/api/auth/magic-link/verify \
  -H "Content-Type: application/json" \
  -d '{
    "token": "magic-link-token-from-email"
  }'
```

## Authorization & Access Control

The ECM Identity Service implements a sophisticated ABAC/ReBAC policy engine for fine-grained authorization decisions.

### Policy Engine Overview

The authorization system supports:
- **ABAC (Attribute-Based Access Control)**: Decisions based on user attributes, resource attributes, and environmental context
- **ReBAC (Relationship-Based Access Control)**: Decisions based on relationships between users, resources, and organizational structures
- **Policy Precedence**: Explicit DENY policies always override ALLOW policies
- **Contextual Authorization**: Time, location, device, and risk-based access decisions
- **Batch Authorization**: Efficient evaluation of multiple authorization requests

### Authorization API Endpoints

#### Single Authorization Check
```bash
curl -X POST http://localhost:8080/api/authz/check \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "subject": "user:123",
    "resource": "document:456",
    "action": "read",
    "context": {
      "department": "engineering",
      "clearanceLevel": "confidential",
      "clientIp": "192.168.1.100",
      "timeOfDay": "09:30"
    }
  }'
```

#### Response
```json
{
  "decision": "ALLOW",
  "reason": "Allowed by policy",
  "timestamp": "2024-01-15T10:30:00Z",
  "evaluations": [
    {
      "policyId": "policy-123",
      "policyName": "Engineering Document Access",
      "decision": "ALLOW",
      "reason": "Policy allows engineering department to read confidential documents"
    }
  ]
}
```

#### Batch Authorization Check
```bash
curl -X POST http://localhost:8080/api/authz/batch \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '[
    {
      "subject": "user:123",
      "resource": "document:456",
      "action": "read"
    },
    {
      "subject": "user:123", 
      "resource": "document:456",
      "action": "write"
    },
    {
      "subject": "user:123",
      "resource": "document:456", 
      "action": "delete"
    }
  ]'
```

### Policy Management

#### Create ABAC Policy (Attribute-Based)
```bash
curl -X POST http://localhost:8080/api/authz/policies \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Engineering Document Access",
    "description": "Allow engineering department to access confidential documents",
    "policyType": "AUTHORIZATION",
    "effect": "ALLOW",
    "priority": 100,
    "subjects": ["role:engineer", "department:engineering"],
    "resources": ["document:confidential:*"],
    "actions": ["read", "write"],
    "conditions": "{\"department\":\"engineering\",\"clearanceLevel\":\"confidential\"}",
    "mfaRequired": true,
    "auditLevel": "DETAILED"
  }'
```

#### Create ReBAC Policy (Relationship-Based)
```bash
curl -X POST http://localhost:8080/api/authz/policies \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Project Member Access",
    "description": "Allow project members to access project resources",
    "policyType": "AUTHORIZATION", 
    "effect": "ALLOW",
    "priority": 200,
    "subjects": ["role:project_member"],
    "resources": ["project:alpha:*"],
    "actions": ["read", "write"],
    "conditions": "{\"member_of\":\"project:alpha\"}",
    "consentRequired": true
  }'
```

#### Create Explicit Deny Policy
```bash
curl -X POST http://localhost:8080/api/authz/policies \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Deny Sensitive Data Access",
    "description": "Explicitly deny access to sensitive data for non-privileged users",
    "policyType": "AUTHORIZATION",
    "effect": "DENY", 
    "priority": 50,
    "subjects": ["role:intern", "role:contractor"],
    "resources": ["document:sensitive:*", "data:financial:*"],
    "actions": ["*"],
    "breakGlassEligible": true,
    "auditLevel": "CRITICAL"
  }'
```

#### Create Time-Restricted Policy
```bash
curl -X POST http://localhost:8080/api/authz/policies \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Business Hours Access",
    "description": "Restrict access to business hours only",
    "policyType": "AUTHORIZATION",
    "effect": "ALLOW",
    "priority": 300,
    "subjects": ["role:employee"],
    "resources": ["system:*"],
    "actions": ["*"],
    "timeRestrictions": "{\"start\":\"09:00\",\"end\":\"17:00\",\"timezone\":\"UTC\",\"weekdays\":[\"monday\",\"tuesday\",\"wednesday\",\"thursday\",\"friday\"]}"
  }'
```

#### Create IP-Restricted Policy
```bash
curl -X POST http://localhost:8080/api/authz/policies \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Corporate Network Access",
    "description": "Allow access only from corporate network",
    "policyType": "AUTHORIZATION",
    "effect": "ALLOW",
    "priority": 150,
    "subjects": ["role:employee"],
    "resources": ["*"],
    "actions": ["*"],
    "ipRestrictions": ["192.168.1.0/24", "10.0.0.0/8"],
    "deviceRestrictions": "{\"trustedDevice\":true,\"deviceType\":[\"laptop\",\"desktop\"]}"
  }'
```

### Advanced Authorization Features

#### Break-Glass Emergency Access
```bash
# Create break-glass policy
curl -X POST http://localhost:8080/api/authz/policies \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Emergency System Access",
    "description": "Break-glass access for critical system failures",
    "policyType": "AUTHORIZATION",
    "effect": "ALLOW",
    "priority": 10,
    "subjects": ["role:admin", "role:emergency_responder"],
    "resources": ["*"],
    "actions": ["*"],
    "breakGlassEligible": true,
    "emergencyOverride": true,
    "conditions": "{\"emergency\":true,\"approval\":\"dual\"}",
    "auditLevel": "CRITICAL"
  }'

# Request emergency access
curl -X POST http://localhost:8080/api/emergency/request \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "justification": "Critical system failure - database unavailable",
    "approvers": ["admin1@company.com", "admin2@company.com"],
    "duration": 1800,
    "resources": ["database:*", "system:*"]
  }'
```

#### Risk-Based Authorization
```bash
# Create risk-based policy
curl -X POST http://localhost:8080/api/authz/policies \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "High-Risk Access Control",
    "description": "Require additional verification for high-risk operations",
    "policyType": "AUTHORIZATION",
    "effect": "ALLOW",
    "priority": 75,
    "subjects": ["role:admin"],
    "resources": ["user:*", "system:*"],
    "actions": ["delete", "modify"],
    "riskLevelMax": "medium",
    "stepUpRequired": true,
    "mfaRequired": true,
    "conditions": "{\"riskScore\":\"<50\",\"deviceTrust\":\"high\"}"
  }'
```

#### Delegation Policies
```bash
# Create delegation policy
curl -X POST http://localhost:8080/api/authz/policies \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Delegated Project Access",
    "description": "Allow delegated access to project resources",
    "policyType": "AUTHORIZATION",
    "effect": "ALLOW",
    "priority": 250,
    "subjects": ["delegate:*"],
    "resources": ["project:*"],
    "actions": ["read", "write"],
    "conditions": "{\"delegated_by\":\"user:manager\",\"delegation_active\":true,\"delegation_expires\":\">now\"}",
    "consentRequired": true
  }'
```

### Policy Testing and Validation

#### Test Policy with Sample Data
```bash
curl -X POST http://localhost:8080/api/authz/policies/{policyId}/test \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "testCases": [
      {
        "name": "Valid Engineering Access",
        "subject": "user:engineer-123",
        "resource": "document:confidential:design",
        "action": "read",
        "expectedDecision": "ALLOW",
        "context": {
          "department": "engineering",
          "clearanceLevel": "confidential"
        }
      },
      {
        "name": "Invalid Department Access",
        "subject": "user:marketing-456", 
        "resource": "document:confidential:design",
        "action": "read",
        "expectedDecision": "DENY",
        "context": {
          "department": "marketing",
          "clearanceLevel": "public"
        }
      }
    ]
  }'
```

#### Policy Simulation (Dry Run)
```bash
curl -X POST http://localhost:8080/api/authz/simulate \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "policies": [
      {
        "name": "New Policy",
        "effect": "ALLOW",
        "subjects": ["role:user"],
        "resources": ["document:*"],
        "actions": ["read"]
      }
    ],
    "testRequests": [
      {
        "subject": "user:123",
        "resource": "document:456", 
        "action": "read"
      }
    ]
  }'
```

### Continuous Authorization

#### Long-Lived Connection Authorization
```bash
# Check authorization for WebSocket connection
curl -X POST http://localhost:8080/api/authz/continuous \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "connectionId": "ws-connection-123",
    "connectionType": "websocket",
    "resource": "realtime:notifications",
    "action": "subscribe",
    "context": {
      "clientIp": "192.168.1.100",
      "userAgent": "Mozilla/5.0...",
      "riskScore": 25
    }
  }'
```

#### Revoke Long-Lived Connection
```bash
curl -X DELETE http://localhost:8080/api/authz/continuous/ws-connection-123 \
  -H "Authorization: Bearer <admin_token>"
```

### Policy Management Operations

#### List All Policies
```bash
curl -X GET http://localhost:8080/api/authz/policies \
  -H "Authorization: Bearer <admin_token>"
```

#### Get Policy Details
```bash
curl -X GET http://localhost:8080/api/authz/policies/{policyId} \
  -H "Authorization: Bearer <admin_token>"
```

#### Update Policy
```bash
curl -X PUT http://localhost:8080/api/authz/policies/{policyId} \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Updated Policy Name",
    "priority": 75,
    "conditions": "{\"department\":\"engineering\",\"clearanceLevel\":\"secret\"}"
  }'
```

#### Activate/Deactivate Policy
```bash
# Activate policy
curl -X POST http://localhost:8080/api/authz/policies/{policyId}/activate \
  -H "Authorization: Bearer <admin_token>"

# Deactivate policy
curl -X POST http://localhost:8080/api/authz/policies/{policyId}/deactivate \
  -H "Authorization: Bearer <admin_token>"
```

#### Delete Policy
```bash
curl -X DELETE http://localhost:8080/api/authz/policies/{policyId} \
  -H "Authorization: Bearer <admin_token>"
```

### Authorization Context Examples

#### User Attributes Context
```json
{
  "subject": "user:123",
  "resource": "document:456",
  "action": "read",
  "context": {
    "user": {
      "id": "user-123",
      "roles": ["engineer", "project_lead"],
      "department": "engineering",
      "clearanceLevel": "confidential",
      "location": "US",
      "employmentStatus": "active"
    },
    "resource": {
      "id": "document-456",
      "type": "design_document",
      "classification": "confidential",
      "owner": "user-789",
      "project": "alpha"
    },
    "environment": {
      "time": "2024-01-15T10:30:00Z",
      "clientIp": "192.168.1.100",
      "userAgent": "Mozilla/5.0...",
      "deviceTrust": "high",
      "riskScore": 25
    }
  }
}
```

#### Relationship Context
```json
{
  "subject": "user:123",
  "resource": "project:alpha:documents",
  "action": "write",
  "context": {
    "relationships": {
      "member_of": ["project:alpha", "team:engineering"],
      "reports_to": "user:456",
      "manages": ["user:789", "user:101"],
      "collaborates_with": ["user:202", "user:303"]
    },
    "project": {
      "id": "alpha",
      "status": "active",
      "visibility": "internal",
      "members": ["user:123", "user:456", "user:789"]
    }
  }
}
```

### Authorization Best Practices

#### 1. Policy Design
- **Use explicit DENY policies** for critical resources with high priority
- **Implement least privilege** - grant minimum necessary permissions
- **Use ABAC for dynamic decisions** based on user attributes and context
- **Use ReBAC for organizational relationships** and project-based access
- **Test policies thoroughly** before deployment

#### 2. Performance Optimization
- **Use batch authorization** for multiple requests
- **Cache policy decisions** for frequently accessed resources
- **Optimize policy queries** with proper indexing
- **Monitor authorization performance** and adjust caching strategies

#### 3. Security Considerations
- **Audit all authorization decisions** for compliance
- **Implement break-glass procedures** for emergency access
- **Use risk-based authorization** for sensitive operations
- **Regular policy reviews** and cleanup of unused policies

## API Integration

### OAuth2/OIDC Provider

The service acts as a standards-compliant OAuth2 Authorization Server and OpenID Connect Provider.

#### 1. Client Registration

```bash
curl -X POST http://localhost:8080/api/oauth2/clients \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "clientId": "my-app",
    "clientName": "My Application",
    "clientType": "public",
    "redirectUris": ["https://myapp.com/callback"],
    "scopes": ["openid", "profile", "email"],
    "grantTypes": ["authorization_code", "refresh_token"]
  }'
```

#### 2. Authorization Code Flow

**Step 1: Redirect to Authorization Endpoint**
```
https://localhost:8080/oauth2/authorize?
  response_type=code&
  client_id=my-app&
  redirect_uri=https://myapp.com/callback&
  scope=openid profile email&
  state=random-state-value&
  code_challenge=code-challenge&
  code_challenge_method=S256
```

**Step 2: Exchange Code for Token**
```bash
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=authorization_code&
       code=authorization-code&
       redirect_uri=https://myapp.com/callback&
       client_id=my-app&
       code_verifier=code-verifier'
```

#### 3. Token Refresh
```bash
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=refresh_token&
       refresh_token=refresh-token&
       client_id=my-app'
```

#### 4. UserInfo Endpoint
```bash
curl -X GET http://localhost:8080/userinfo \
  -H "Authorization: Bearer <access_token>"
```

### REST API Integration

#### User Management

**Create User**
```bash
curl -X POST http://localhost:8080/api/users \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "newuser@example.com",
    "firstName": "Jane",
    "lastName": "Smith",
    "password": "SecurePass123!",
    "tenantId": "default",
    "roles": ["USER"]
  }'
```

**Get User Details**
```bash
curl -X GET http://localhost:8080/api/users/user-123 \
  -H "Authorization: Bearer <access_token>"
```

**Update User**
```bash
curl -X PUT http://localhost:8080/api/users/user-123 \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Jane",
    "lastName": "Smith-Updated",
    "email": "jane.smith@example.com"
  }'
```

#### Session Management

**List User Sessions**
```bash
curl -X GET http://localhost:8080/api/sessions \
  -H "Authorization: Bearer <access_token>"
```

**Terminate Session**
```bash
curl -X DELETE http://localhost:8080/api/sessions/session-123 \
  -H "Authorization: Bearer <access_token>"
```

**List User Devices**
```bash
curl -X GET http://localhost:8080/api/devices \
  -H "Authorization: Bearer <access_token>"
```

## Multi-Tenancy

### Tenant Configuration

#### Create Tenant
```bash
curl -X POST http://localhost:8080/api/tenants \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Acme Corporation",
    "domain": "acme.com",
    "settings": {
      "passwordPolicy": {
        "minLength": 12,
        "requireUppercase": true,
        "requireNumbers": true,
        "requireSpecialChars": true
      },
      "mfaRequired": true,
      "sessionTimeout": 3600
    }
  }'
```

#### Tenant-Specific Authentication
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: acme-corp" \
  -d '{
    "username": "user@acme.com",
    "password": "SecurePass123!"
  }'
```

### Cross-Tenant Collaboration

#### Invite Guest User
```bash
curl -X POST http://localhost:8080/api/tenants/acme-corp/guests \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "email": "guest@external.com",
    "permissions": ["read:documents", "write:comments"],
    "expiresAt": "2024-12-31T23:59:59Z"
  }'
```

## Admin Console

### Access Admin Console
Navigate to: http://localhost:8080/admin

### Key Features

#### 1. User Management
- Create, update, and delete users
- Assign roles and permissions
- View user activity and security timeline
- Manage user sessions and devices

#### 2. Policy Management
- Create and manage ABAC/ReBAC policies
- Set up break-glass access procedures
- Configure delegation rules
- Manage consent requirements

#### 3. Tenant Administration
- Create and configure tenants
- Set tenant-specific security policies
- Manage cross-tenant collaborations
- Monitor tenant usage and compliance

#### 4. Audit and Compliance
- View comprehensive audit logs
- Generate compliance reports
- Manage data retention policies
- Handle data subject requests (GDPR)

### Admin Impersonation

#### Login as User
```bash
curl -X POST http://localhost:8080/api/admin/impersonate \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "justification": "Customer support request #12345",
    "duration": 3600
  }'
```

## Security Features

### Risk-Based Authentication

#### Device Trust Management
```bash
# Register trusted device
curl -X POST http://localhost:8080/api/devices/trust \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "deviceId": "device-123",
    "deviceName": "John's iPhone",
    "trustLevel": "high"
  }'
```

#### Risk Assessment
```bash
# Check risk score
curl -X GET http://localhost:8080/api/security/risk-score \
  -H "Authorization: Bearer <access_token>"
```

### Break-Glass Access

#### Emergency Access Request
```bash
curl -X POST http://localhost:8080/api/emergency/request \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "justification": "Critical system failure - need immediate access",
    "approvers": ["admin1@company.com", "admin2@company.com"],
    "duration": 1800
  }'
```

### Delegation

#### Delegate Permissions
```bash
curl -X POST http://localhost:8080/api/delegation \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "delegateTo": "user-456",
    "permissions": ["read:documents", "write:comments"],
    "expiresAt": "2024-12-31T23:59:59Z",
    "conditions": {
      "ipWhitelist": ["192.168.1.0/24"],
      "timeRestrictions": {
        "start": "09:00",
        "end": "17:00"
      }
    }
  }'
```

## Compliance & Audit

### Audit Log Access

#### Query Audit Events
```bash
curl -X GET "http://localhost:8080/api/audit/events?userId=user-123&startDate=2024-01-01&endDate=2024-01-31" \
  -H "Authorization: Bearer <admin_token>"
```

#### Export Audit Data
```bash
curl -X POST http://localhost:8080/api/audit/export \
  -H "Authorization: Bearer <admin_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "startDate": "2024-01-01",
    "endDate": "2024-01-31",
    "format": "json",
    "includePII": false
  }'
```

### Data Privacy (GDPR/CCPA)

#### Data Subject Request
```bash
# Request data export
curl -X POST http://localhost:8080/api/privacy/export \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "format": "json",
    "excludeFields": ["internalNotes", "systemLogs"]
  }'
```

#### Right to Erasure
```bash
# Request account deletion
curl -X POST http://localhost:8080/api/privacy/delete \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-123",
    "reason": "User requested account deletion",
    "retentionOverride": false
  }'
```

### Consent Management

#### Grant Consent
```bash
curl -X POST http://localhost:8080/api/consent/grant \
  -H "Authorization: Bearer <access_token>" \
  -H "Content-Type: application/json" \
  -d '{
    "applicationId": "app-123",
    "permissions": ["read:profile", "write:preferences"],
    "version": "1.0"
  }'
```

#### Revoke Consent
```bash
curl -X DELETE http://localhost:8080/api/consent/consent-123 \
  -H "Authorization: Bearer <access_token>"
```

## Enterprise Federation

### SAML 2.0 Integration

#### SAML Metadata
```bash
curl -X GET http://localhost:8080/saml/metadata
```

#### SAML SSO Configuration
```xml
<!-- Service Provider Configuration -->
<EntityDescriptor entityID="https://myapp.com">
  <SPSSODescriptor protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
    <AssertionConsumerService 
      index="0" 
      Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
      Location="https://localhost:8080/saml/sso"
      isDefault="true"/>
  </SPSSODescriptor>
</EntityDescriptor>
```

### SCIM User Provisioning

#### Create User via SCIM
```bash
curl -X POST http://localhost:8080/scim/v2/Users \
  -H "Authorization: Bearer <scim_token>" \
  -H "Content-Type: application/scim+json" \
  -d '{
    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "userName": "john.doe@company.com",
    "name": {
      "givenName": "John",
      "familyName": "Doe"
    },
    "emails": [{
      "value": "john.doe@company.com",
      "primary": true
    }],
    "active": true
  }'
```

#### Update User via SCIM
```bash
curl -X PATCH http://localhost:8080/scim/v2/Users/user-123 \
  -H "Authorization: Bearer <scim_token>" \
  -H "Content-Type: application/scim+json" \
  -d '{
    "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
    "Operations": [{
      "op": "replace",
      "path": "active",
      "value": false
    }]
  }'
```

## Troubleshooting

### Common Issues

#### 1. Authentication Failures

**Issue**: "Invalid credentials" error
```bash
# Check user status
curl -X GET http://localhost:8080/api/users/user-123 \
  -H "Authorization: Bearer <admin_token>"

# Check account lockout
curl -X GET http://localhost:8080/api/security/account-status/user-123 \
  -H "Authorization: Bearer <admin_token>"
```

**Solution**: Verify user exists, account is active, and not locked out.

#### 2. Token Expiration

**Issue**: "Token expired" error
```bash
# Refresh token
curl -X POST http://localhost:8080/oauth2/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=refresh_token&refresh_token=<refresh_token>&client_id=<client_id>'
```

#### 3. Multi-Tenancy Issues

**Issue**: "Tenant not found" error
```bash
# Check tenant configuration
curl -X GET http://localhost:8080/api/tenants \
  -H "Authorization: Bearer <admin_token>"

# Verify tenant header
curl -X GET http://localhost:8080/api/users \
  -H "Authorization: Bearer <access_token>" \
  -H "X-Tenant-ID: correct-tenant-id"
```

#### 4. MFA Setup Issues

**Issue**: TOTP code not working
```bash
# Verify time synchronization
curl -X GET http://localhost:8080/api/auth/mfa/status \
  -H "Authorization: Bearer <access_token>"

# Reset MFA if needed
curl -X DELETE http://localhost:8080/api/auth/mfa/totp \
  -H "Authorization: Bearer <access_token>"
```

### Debugging Commands

#### Check Service Health
```bash
# Application health
curl http://localhost:8080/actuator/health

# Database connectivity
curl http://localhost:8080/actuator/health/db

# Redis connectivity
curl http://localhost:8080/actuator/health/redis
```

#### View Logs
```bash
# Application logs
make logs

# Docker logs
make docker-logs

# Specific service logs
docker logs ecm-identity-service
```

#### Performance Monitoring
```bash
# Metrics endpoint
curl http://localhost:8080/actuator/metrics

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

### Security Considerations

#### 1. Token Security
- Always use HTTPS in production
- Implement proper token storage (secure cookies, not localStorage)
- Use short-lived access tokens with refresh token rotation
- Implement token binding to prevent token theft

#### 2. API Security
- Validate all input parameters
- Implement rate limiting
- Use proper authentication headers
- Log security events for monitoring

#### 3. Multi-Tenancy Security
- Always include tenant context in requests
- Validate tenant isolation in queries
- Use tenant-specific encryption keys
- Monitor cross-tenant access attempts

## Best Practices

### 1. Authentication
- Implement MFA for all admin accounts
- Use WebAuthn for passwordless authentication
- Implement risk-based authentication
- Regular security audits and penetration testing

### 2. Authorization
- Follow principle of least privilege
- Use ABAC/ReBAC for fine-grained access control
- Implement break-glass procedures for emergencies
- Regular access reviews and cleanup

### 3. Compliance
- Maintain comprehensive audit logs
- Implement data retention policies
- Regular compliance assessments
- User consent management

### 4. Monitoring
- Set up security monitoring and alerting
- Monitor authentication failures and anomalies
- Track privilege escalations
- Regular security metrics review

---

For additional support and documentation:
- **API Documentation**: http://localhost:8080/swagger-ui.html
- **Health Checks**: http://localhost:8080/actuator/health
- **Metrics**: http://localhost:8080/actuator/prometheus
- **Admin Console**: http://localhost:8080/admin
