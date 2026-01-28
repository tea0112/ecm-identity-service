# Auth Module Plan

> **Created**: 2026-01-28
> **Status**: PLANNING
> **Project Type**: BACKEND (Java/Spring Boot)

---

## Overview

### What

Add an `identity-auth` module to the ECM Identity Service providing complete authentication flows for multi-tenant clients (internal admin + external customers).

### Why

- Enable secure user authentication for web/mobile applications
- Provide foundation for future OAuth2, SSO, MFA features
- Follow existing modular architecture pattern (identity-user, identity-role)
- Ship to market quickly with essential auth features

### Scope

**In Scope (MVP):**

- Sign Up (user registration with email verification)
- Sign In (username/password + email/password)
- Sign Out (session termination)
- Forgot Password (email-based reset)
- Refresh Token (JWT renewal)
- Account Verification (email verification)
- Session Management (list/revoke sessions)

**Out of Scope (Future):**

- OAuth2/Social Login (Google, Facebook, GitHub)
- SSO/SAML/OIDC (enterprise federation)
- Passwordless (magic link, OTP)
- MFA/2FA (TOTP, SMS, Email)

---

## Architecture Decision

### Token Strategy: Hybrid (Best Practice)

```
┌───────────────────────────────────────────────────────────────┐
│                    HYBRID TOKEN ARCHITECTURE                  │
├───────────────────────────────────────────────────────────────┤
│                                                               │
│  ACCESS TOKEN (JWT)              REFRESH TOKEN (Database)     │
│  ┌─────────────────────┐        ┌─────────────────────┐      │
│  │ - Short-lived (15m) │        │ - Long-lived (7d)   │      │
│  │ - Stateless         │        │ - Stateful (stored) │      │
│  │ - No DB lookup      │        │ - Revocable anytime │      │
│  │ - Contains claims:  │        │ - Contains:         │      │
│  │   • userId          │        │   • tokenId (UUID)  │      │
│  │   • username        │        │   • userId          │      │
│  │   • roles           │        │   • expiresAt       │      │
│  │   • tenantId        │        │   • deviceInfo      │      │
│  └─────────────────────┘        │   • createdAt       │      │
│                                 └─────────────────────┘      │
│                                                               │
│  Benefits:                                                    │
│  ✓ Fast API validation (no DB hit for access token)          │
│  ✓ Secure logout (delete refresh token = instant revoke)     │
│  ✓ Session management (view/revoke all devices)              │
│  ✓ Scalable (stateless access tokens work with any instance) │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

### Service Architecture: API Gateway Ready

```
┌─────────────────────────────────────────────────────────────────┐
│                    PRODUCTION ARCHITECTURE                       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│   Clients (Web/Mobile)                                          │
│          │                                                       │
│          ▼                                                       │
│   ┌─────────────────────┐                                       │
│   │    API Gateway      │  Kong / Nginx / AWS ALB               │
│   │  ┌───────────────┐  │                                       │
│   │  │ Rate Limiting │  │  - SSL termination                    │
│   │  │ CORS          │  │  - Request routing                    │
│   │  │ Auth Header   │  │  - JWT validation (optional)          │
│   │  └───────────────┘  │                                       │
│   └──────────┬──────────┘                                       │
│              │                                                   │
│   ┌──────────▼──────────────────────────────────────────────┐   │
│   │              ECM IDENTITY SERVICE                        │   │
│   │                                                          │   │
│   │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐     │   │
│   │  │identity-auth │ │identity-user │ │identity-role │     │   │
│   │  │              │ │              │ │              │     │   │
│   │  │ POST /signup │ │ GET  /users  │ │ GET  /roles  │     │   │
│   │  │ POST /login  │ │ POST /users  │ │ POST /roles  │     │   │
│   │  │ POST /logout │ │ GET  /users/ │ │ ...          │     │   │
│   │  │ POST /refresh│ │ PUT  /users/ │ │              │     │   │
│   │  │ POST /forgot │ │ DELETE /users│ │              │     │   │
│   │  │ POST /verify │ │              │ │              │     │   │
│   │  │ GET /sessions│ │              │ │              │     │   │
│   │  └──────────────┘ └──────────────┘ └──────────────┘     │   │
│   │                                                          │   │
│   │  ┌──────────────┐ ┌──────────────┐                      │   │
│   │  │identity-     │ │identity-app  │                      │   │
│   │  │shared        │ │              │                      │   │
│   │  │              │ │ Bootstrap    │                      │   │
│   │  │ JWT Utils    │ │ Config       │                      │   │
│   │  │ Exceptions   │ │ Security     │                      │   │
│   │  │ Base Classes │ │              │                      │   │
│   │  └──────────────┘ └──────────────┘                      │   │
│   │                                                          │   │
│   └──────────────────────────────────────────────────────────┘   │
│              │                            │                      │
│   ┌──────────▼──────────┐    ┌───────────▼───────────┐          │
│   │     PostgreSQL      │    │   Email Service       │          │
│   │  - users            │    │   (SMTP / SendGrid)   │          │
│   │  - roles            │    │   - Verification      │          │
│   │  - refresh_tokens   │    │   - Password Reset    │          │
│   │  - verification_    │    └───────────────────────┘          │
│   │    tokens           │                                        │
│   └─────────────────────┘                                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Success Criteria

### Functional

- [ ] User can sign up with email and receive verification email
- [ ] User can verify email with token link
- [ ] User can sign in with username/password or email/password
- [ ] User receives JWT access token (15min) + refresh token (7days)
- [ ] User can refresh access token using refresh token
- [ ] User can sign out (invalidates refresh token)
- [ ] User can request password reset via email
- [ ] User can reset password with reset token
- [ ] User can view active sessions
- [ ] User can revoke specific sessions

### Non-Functional

- [ ] API response time < 200ms for auth endpoints
- [ ] Tokens are secure (HS256/RS256, proper expiry)
- [ ] Passwords are securely hashed (BCrypt, min cost 12)
- [ ] Rate limiting on auth endpoints (prevent brute force)
- [ ] All auth events are logged (audit trail)

### Testing

- [ ] Unit tests for all services (>80% coverage)
- [ ] Integration tests for auth flows
- [ ] Security tests (token validation, password policies)

---

## Tech Stack

| Component      | Technology               | Rationale                        |
| -------------- | ------------------------ | -------------------------------- |
| **Framework**  | Spring Boot 3.x          | Already in use, mature ecosystem |
| **Security**   | Spring Security 6.x      | Industry standard for Java auth  |
| **JWT**        | jjwt (io.jsonwebtoken)   | Most popular, well-maintained    |
| **Password**   | BCrypt (Spring Security) | Secure, built-in support         |
| **Database**   | PostgreSQL               | Already in use                   |
| **Email**      | Spring Mail + Thymeleaf  | Simple, templated emails         |
| **Validation** | Jakarta Validation       | Standard, annotation-based       |
| **Testing**    | JUnit 5 + Mockito        | Standard Java testing            |

### Dependencies to Add

```groovy
// identity-auth/build.gradle
dependencies {
    implementation project(':identity-shared')
    implementation project(':identity-user')

    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.5'

    // Email
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    implementation 'org.springframework.boot:spring-boot-starter-thymeleaf'

    // Validation
    implementation 'org.springframework.boot:spring-boot-starter-validation'
}
```

---

## File Structure

```
ecm-identity-service/
├── identity-auth/                          # NEW MODULE
│   ├── build.gradle
│   └── src/main/java/com/ecm/security/identity/auth/
│       ├── controller/
│       │   └── AuthController.java         # REST endpoints
│       ├── service/
│       │   ├── AuthService.java            # Authentication logic
│       │   ├── TokenService.java           # JWT generation/validation
│       │   ├── RefreshTokenService.java    # Refresh token management
│       │   ├── PasswordResetService.java   # Password reset flow
│       │   ├── EmailVerificationService.java # Email verification
│       │   └── SessionService.java         # Session management
│       ├── dto/
│       │   ├── request/
│       │   │   ├── SignUpRequest.java
│       │   │   ├── SignInRequest.java
│       │   │   ├── RefreshTokenRequest.java
│       │   │   ├── ForgotPasswordRequest.java
│       │   │   ├── ResetPasswordRequest.java
│       │   │   └── VerifyEmailRequest.java
│       │   └── response/
│       │       ├── AuthResponse.java       # Tokens + user info
│       │       ├── TokenResponse.java      # Access token only
│       │       └── SessionResponse.java    # Session info
│       ├── entity/
│       │   ├── RefreshTokenEntity.java     # Stored refresh tokens
│       │   └── VerificationTokenEntity.java # Email/password tokens
│       ├── repository/
│       │   ├── RefreshTokenRepository.java
│       │   └── VerificationTokenRepository.java
│       ├── config/
│       │   └── JwtConfig.java              # JWT configuration
│       └── exception/
│           ├── AuthenticationException.java
│           ├── TokenExpiredException.java
│           └── InvalidTokenException.java
│
├── identity-shared/                         # UPDATES
│   └── src/main/java/com/ecm/security/identity/shared/
│       ├── security/
│       │   └── JwtUtils.java               # Shared JWT utilities
│       └── exception/
│           └── BaseException.java          # Base exception class
│
├── identity-app/                            # UPDATES
│   └── src/main/
│       ├── java/com/ecm/security/identity/
│       │   └── config/
│       │       └── SecurityConfig.java     # Updated security config
│       └── resources/
│           ├── application.properties      # JWT + Email config
│           └── templates/
│               └── email/
│                   ├── verification.html   # Email verification template
│                   └── password-reset.html # Password reset template
│
└── identity-user/                           # MINOR UPDATES
    └── src/main/java/.../
        └── entity/
            └── UserEntity.java             # Add emailVerified field
```

### Database Schema (New Tables)

```sql
-- Refresh tokens for session management
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    device_info VARCHAR(500),
    ip_address VARCHAR(45),
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP,

    INDEX idx_refresh_tokens_user_id (user_id),
    INDEX idx_refresh_tokens_token_hash (token_hash),
    INDEX idx_refresh_tokens_expires_at (expires_at)
);

-- Verification tokens (email verification, password reset)
CREATE TABLE verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    token_type VARCHAR(50) NOT NULL, -- 'EMAIL_VERIFICATION', 'PASSWORD_RESET'
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    used_at TIMESTAMP,

    INDEX idx_verification_tokens_user_id (user_id),
    INDEX idx_verification_tokens_token_hash (token_hash),
    INDEX idx_verification_tokens_type (token_type)
);

-- Add to users table
ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP;
ALTER TABLE users ADD COLUMN last_login_at TIMESTAMP;
ALTER TABLE users ADD COLUMN failed_login_attempts INTEGER DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until TIMESTAMP;
```

---

## Detailed Implementation Specifications

### 1. DTOs (Data Transfer Objects)

#### Request DTOs

```java
// SignUpRequest.java
public record SignUpRequest(
    @NotBlank @Size(min = 3, max = 50) 
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, underscores")
    String username,
    
    @NotBlank @Email 
    String email,
    
    @NotBlank @Size(min = 8, max = 128)
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
             message = "Password must contain uppercase, lowercase, number, and special character")
    String password,
    
    @Size(max = 100) String firstName,
    @Size(max = 100) String lastName,
    
    String tenantId  // Optional for multi-tenant
) {}

// SignInRequest.java
public record SignInRequest(
    @NotBlank String usernameOrEmail,  // Accepts both
    @NotBlank String password,
    String deviceInfo,  // Optional: "Chrome on Windows"
    String ipAddress    // Auto-captured from request
) {}

// RefreshTokenRequest.java
public record RefreshTokenRequest(
    @NotBlank String refreshToken
) {}

// ForgotPasswordRequest.java
public record ForgotPasswordRequest(
    @NotBlank @Email String email
) {}

// ResetPasswordRequest.java
public record ResetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8, max = 128) String newPassword,
    @NotBlank String confirmPassword
) {}

// VerifyEmailRequest.java
public record VerifyEmailRequest(
    @NotBlank String token
) {}
```

#### Response DTOs

```java
// AuthResponse.java (Login success)
public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,      // "Bearer"
    long expiresIn,        // seconds until access token expires
    UserInfo user
) {
    public record UserInfo(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        Set<String> roles,
        boolean emailVerified
    ) {}
}

// TokenResponse.java (Refresh success)
public record TokenResponse(
    String accessToken,
    String tokenType,
    long expiresIn
) {}

// SignUpResponse.java
public record SignUpResponse(
    String message,
    Long userId,
    boolean verificationEmailSent
) {}

// SessionResponse.java
public record SessionResponse(
    UUID id,
    String deviceInfo,
    String ipAddress,
    Instant createdAt,
    Instant lastUsedAt,
    boolean current  // Is this the session making the request?
) {}

// SessionListResponse.java
public record SessionListResponse(
    List<SessionResponse> sessions,
    int totalCount
) {}

// MessageResponse.java (Generic success)
public record MessageResponse(
    String message,
    Instant timestamp
) {}
```

---

### 2. Entity Specifications

#### RefreshTokenEntity

```java
@Entity
@Table(name = "refresh_tokens")
public class RefreshTokenEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;  // SHA-256 hash of actual token
    
    @Column(name = "device_info", length = 500)
    private String deviceInfo;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    
    @Column(name = "revoked_at")
    private Instant revokedAt;
    
    // Business methods
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isRevoked() { return revokedAt != null; }
    public boolean isValid() { return !isExpired() && !isRevoked(); }
}
```

#### VerificationTokenEntity

```java
@Entity
@Table(name = "verification_tokens")
public class VerificationTokenEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false)
    private TokenType tokenType;  // EMAIL_VERIFICATION, PASSWORD_RESET
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "used_at")
    private Instant usedAt;
    
    public enum TokenType {
        EMAIL_VERIFICATION,
        PASSWORD_RESET
    }
    
    public boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    public boolean isUsed() { return usedAt != null; }
    public boolean isValid() { return !isExpired() && !isUsed(); }
}
```

---

### 3. Service Method Specifications

#### AuthService

```java
@Service
@Transactional
public class AuthService {
    
    /**
     * Register a new user account.
     * 
     * Business Rules:
     * - Username must be unique (case-insensitive)
     * - Email must be unique (case-insensitive)
     * - Password is hashed with BCrypt (cost 12)
     * - User created with enabled=true, emailVerified=false
     * - Verification email sent asynchronously
     * - Default role: "USER" assigned
     * 
     * @throws UsernameAlreadyExistsException if username taken
     * @throws EmailAlreadyExistsException if email taken
     */
    public SignUpResponse signUp(SignUpRequest request);
    
    /**
     * Authenticate user and issue tokens.
     * 
     * Business Rules:
     * - Accept username OR email for login
     * - Check if account is locked (failed_login_attempts >= 5)
     * - Check if account is enabled
     * - Verify password with BCrypt
     * - Reset failed_login_attempts on success
     * - Update last_login_at timestamp
     * - Generate access token (JWT, 15min)
     * - Generate refresh token (UUID, stored in DB, 7days)
     * - Log login event for audit
     * 
     * @throws AccountLockedException if locked_until > now
     * @throws AccountDisabledException if enabled=false
     * @throws InvalidCredentialsException if password wrong
     */
    public AuthResponse signIn(SignInRequest request);
    
    /**
     * Invalidate refresh token (logout).
     * 
     * Business Rules:
     * - Mark refresh token as revoked (set revoked_at)
     * - Token hash must match stored hash
     * - Already revoked tokens return success (idempotent)
     */
    public void signOut(String refreshToken);
    
    /**
     * Invalidate ALL refresh tokens for user.
     * 
     * Business Rules:
     * - Revoke all active refresh tokens
     * - Used after password reset for security
     */
    public void signOutAll(Long userId);
}
```

#### TokenService

```java
@Service
public class TokenService {
    
    /**
     * Generate JWT access token.
     * 
     * JWT Claims:
     * - sub: userId (string)
     * - username: username
     * - email: user email
     * - roles: ["ROLE_USER", "ROLE_ADMIN"]
     * - tenantId: tenant identifier (if multi-tenant)
     * - iat: issued at (epoch seconds)
     * - exp: expiration (iat + 15 minutes)
     * - iss: "ecm-identity-service"
     * 
     * Algorithm: HS256 (HMAC-SHA256)
     * Secret: 256-bit minimum from config
     */
    public String generateAccessToken(User user);
    
    /**
     * Validate and parse JWT access token.
     * 
     * Validation:
     * - Signature valid with secret
     * - Not expired (exp > now)
     * - Issuer matches ("ecm-identity-service")
     * 
     * @throws InvalidTokenException if validation fails
     * @throws TokenExpiredException if expired
     */
    public Claims parseAccessToken(String token);
    
    /**
     * Check if token is valid (without throwing).
     */
    public boolean isValidAccessToken(String token);
    
    /**
     * Extract user ID from token.
     */
    public Long getUserIdFromToken(String token);
}
```

#### RefreshTokenService

```java
@Service
@Transactional
public class RefreshTokenService {
    
    /**
     * Create new refresh token for user.
     * 
     * Process:
     * 1. Generate secure random UUID
     * 2. Hash with SHA-256 before storing
     * 3. Store in DB with device info, IP, expiry
     * 4. Return plain UUID to client (not hash)
     * 
     * Token Rotation: Each refresh creates new token,
     * old token remains valid until used or revoked.
     */
    public String createRefreshToken(User user, String deviceInfo, String ipAddress);
    
    /**
     * Use refresh token to get new access token.
     * 
     * Process:
     * 1. Hash incoming token
     * 2. Find by hash in DB
     * 3. Validate: not expired, not revoked
     * 4. Update last_used_at
     * 5. Generate new access token
     * 6. Optionally rotate refresh token (configurable)
     * 
     * @throws InvalidTokenException if not found
     * @throws TokenExpiredException if expired
     * @throws TokenRevokedException if revoked
     */
    public TokenResponse refreshAccessToken(String refreshToken);
    
    /**
     * Revoke specific refresh token.
     */
    public void revokeToken(String refreshToken);
    
    /**
     * Revoke all tokens for user.
     */
    public void revokeAllUserTokens(Long userId);
    
    /**
     * Get all active sessions for user.
     */
    public List<SessionResponse> getUserSessions(Long userId);
    
    /**
     * Revoke specific session by ID.
     */
    public void revokeSession(Long userId, UUID sessionId);
}
```

#### EmailVerificationService

```java
@Service
@Transactional
public class EmailVerificationService {
    
    /**
     * Send verification email to user.
     * 
     * Process:
     * 1. Generate secure random token (32 bytes, base64url)
     * 2. Hash and store in verification_tokens
     * 3. Build verification URL: {baseUrl}/verify-email?token={token}
     * 4. Send email with Thymeleaf template
     * 5. Async execution (don't block signup)
     * 
     * Token Expiry: 24 hours
     */
    @Async
    public void sendVerificationEmail(User user);
    
    /**
     * Verify email with token.
     * 
     * Process:
     * 1. Hash incoming token
     * 2. Find by hash and type=EMAIL_VERIFICATION
     * 3. Validate: not expired, not used
     * 4. Mark token as used (set used_at)
     * 5. Update user: emailVerified=true, emailVerifiedAt=now
     * 
     * @throws InvalidTokenException if not found/invalid
     * @throws TokenExpiredException if expired
     * @throws TokenAlreadyUsedException if already used
     */
    public void verifyEmail(String token);
    
    /**
     * Resend verification email.
     * 
     * Rate limit: Max 3 per hour
     * Invalidates previous tokens
     */
    public void resendVerificationEmail(String email);
}
```

#### PasswordResetService

```java
@Service
@Transactional
public class PasswordResetService {
    
    /**
     * Initiate password reset (forgot password).
     * 
     * Process:
     * 1. Find user by email (fail silently if not found - security)
     * 2. Invalidate any existing reset tokens
     * 3. Generate new reset token
     * 4. Send reset email with link: {baseUrl}/reset-password?token={token}
     * 
     * Token Expiry: 1 hour
     * Always returns success (don't reveal if email exists)
     */
    public void sendPasswordResetEmail(String email);
    
    /**
     * Reset password with token.
     * 
     * Process:
     * 1. Hash incoming token
     * 2. Find by hash and type=PASSWORD_RESET
     * 3. Validate: not expired, not used
     * 4. Validate new password meets requirements
     * 5. Update user password (BCrypt hash)
     * 6. Mark token as used
     * 7. Revoke ALL refresh tokens (logout everywhere)
     * 8. Send confirmation email (optional)
     * 
     * @throws InvalidTokenException if not found
     * @throws TokenExpiredException if expired
     * @throws PasswordValidationException if weak password
     */
    public void resetPassword(String token, String newPassword);
}
```

---

### 4. Security Specifications

#### Password Policy

```yaml
Password Requirements:
  - Minimum length: 8 characters
  - Maximum length: 128 characters
  - Must contain:
    - At least 1 uppercase letter (A-Z)
    - At least 1 lowercase letter (a-z)
    - At least 1 digit (0-9)
    - At least 1 special character (@$!%*?&)
  - Cannot contain username
  - Cannot be in common password list (top 10000)
  
Hashing:
  - Algorithm: BCrypt
  - Cost factor: 12 (2^12 = 4096 iterations)
```

#### Rate Limiting

```yaml
Rate Limits (per IP):
  /auth/signup:         5 requests / minute
  /auth/login:          10 requests / minute
  /auth/forgot-password: 3 requests / minute
  /auth/verify-email:   10 requests / minute
  /auth/refresh:        30 requests / minute
  
Account Lockout:
  - Lock after: 5 failed login attempts
  - Lock duration: 15 minutes
  - Exponential backoff: 15min, 30min, 1hr, 24hr
```

#### JWT Security

```yaml
Access Token:
  - Algorithm: HS256 (symmetric) or RS256 (asymmetric)
  - Secret: Minimum 256 bits (32 bytes)
  - Expiry: 15 minutes
  - Claims: userId, username, roles, tenantId
  
Refresh Token:
  - Format: UUID v4 (random)
  - Storage: SHA-256 hash in database
  - Expiry: 7 days
  - One-time use: Optional (token rotation)
```

#### Security Headers

```yaml
Response Headers:
  - X-Content-Type-Options: nosniff
  - X-Frame-Options: DENY
  - X-XSS-Protection: 1; mode=block
  - Strict-Transport-Security: max-age=31536000; includeSubDomains
  - Cache-Control: no-store (for auth endpoints)
```

---

### 5. Error Response Format

```java
// Standard error response
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> validationErrors  // For 400 errors
) {}
```

#### Error Codes

| HTTP Status | Error Code | When |
|-------------|------------|------|
| 400 | `VALIDATION_ERROR` | Invalid request body |
| 400 | `PASSWORD_MISMATCH` | Passwords don't match |
| 401 | `INVALID_CREDENTIALS` | Wrong username/password |
| 401 | `INVALID_TOKEN` | Token malformed/invalid |
| 401 | `TOKEN_EXPIRED` | Token past expiry |
| 403 | `ACCOUNT_LOCKED` | Too many failed attempts |
| 403 | `ACCOUNT_DISABLED` | Account is disabled |
| 403 | `EMAIL_NOT_VERIFIED` | Email verification required |
| 404 | `USER_NOT_FOUND` | User doesn't exist |
| 409 | `USERNAME_EXISTS` | Username already taken |
| 409 | `EMAIL_EXISTS` | Email already registered |
| 429 | `RATE_LIMIT_EXCEEDED` | Too many requests |

---

### 6. Audit Logging

```java
// Events to log
public enum AuthEvent {
    SIGNUP_SUCCESS,
    SIGNUP_FAILED,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    TOKEN_REFRESH,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_SUCCESS,
    EMAIL_VERIFICATION_SUCCESS,
    SESSION_REVOKED,
    ACCOUNT_LOCKED,
    ACCOUNT_UNLOCKED
}

// Log format
{
    "timestamp": "2026-01-28T10:30:00Z",
    "event": "LOGIN_SUCCESS",
    "userId": 123,
    "username": "johndoe",
    "ipAddress": "192.168.1.1",
    "userAgent": "Mozilla/5.0...",
    "details": {
        "deviceInfo": "Chrome on Windows",
        "sessionId": "550e8400-e29b-..."
    }
}
```

---

## Task Breakdown

### Phase 1: Foundation (Database + Shared)

#### Task 1.1: Database Schema Migration

- **Agent**: `backend-specialist`
- **Skills**: `database-design`, `clean-code`
- **Priority**: P0 (Blocker)
- **Dependencies**: None
- **Estimated**: 30 min

**INPUT:**

- Current database schema
- Token requirements (refresh, verification)

**OUTPUT:**

- Liquibase changelog: `db/changelog/0003-auth-tokens.sql`
- Updated users table with email_verified fields

**VERIFY:**

```bash
make db-validate
make db-update PROFILE=local
# Check tables exist: refresh_tokens, verification_tokens
# Check users table has new columns
```

---

#### Task 1.2: Update identity-shared Module

- **Agent**: `backend-specialist`
- **Skills**: `clean-code`
- **Priority**: P0 (Blocker)
- **Dependencies**: None
- **Estimated**: 20 min

**INPUT:**

- JWT requirements
- Exception handling patterns

**OUTPUT:**

- `JwtUtils.java` - Shared JWT utilities
- `BaseException.java` - Base exception class
- Update `build.gradle` with jjwt dependency

**VERIFY:**

```bash
./gradlew :identity-shared:compileJava
# No compile errors
```

---

### Phase 2: Core Auth Module Setup

#### Task 2.1: Create identity-auth Module Structure

- **Agent**: `backend-specialist`
- **Skills**: `clean-code`
- **Priority**: P0 (Blocker)
- **Dependencies**: Task 1.2
- **Estimated**: 15 min

**INPUT:**

- Module structure from File Structure section
- Dependency requirements

**OUTPUT:**

- `identity-auth/build.gradle`
- Package structure created
- Module registered in `settings.gradle`

**VERIFY:**

```bash
./gradlew :identity-auth:compileJava
# Module compiles successfully
```

---

#### Task 2.2: Implement Token Entities & Repositories

- **Agent**: `backend-specialist`
- **Skills**: `database-design`, `clean-code`
- **Priority**: P0 (Blocker)
- **Dependencies**: Task 1.1, Task 2.1
- **Estimated**: 45 min

**INPUT:**

- Database schema from Task 1.1
- Entity pattern from identity-user

**OUTPUT:**

- `RefreshTokenEntity.java`
- `VerificationTokenEntity.java`
- `RefreshTokenRepository.java`
- `VerificationTokenRepository.java`

**VERIFY:**

```bash
./gradlew :identity-auth:compileJava
# Entities match database schema
# Repositories have required query methods
```

---

#### Task 2.3: Implement JWT Token Service

- **Agent**: `backend-specialist`
- **Skills**: `clean-code`
- **Priority**: P0 (Blocker)
- **Dependencies**: Task 2.1
- **Estimated**: 1 hour

**INPUT:**

- JWT requirements (claims, expiry)
- jjwt library documentation

**OUTPUT:**

- `TokenService.java` with methods:
  - `generateAccessToken(User user)`
  - `generateRefreshToken(User user, String deviceInfo)`
  - `validateAccessToken(String token)`
  - `parseAccessToken(String token)`
- `JwtConfig.java` configuration class

**VERIFY:**

```bash
./gradlew :identity-auth:test --tests "*TokenServiceTest*"
# All token generation/validation tests pass
```

---

### Phase 3: Auth Flows Implementation

#### Task 3.1: Implement Sign Up Flow

- **Agent**: `backend-specialist`
- **Skills**: `clean-code`, `api-patterns`
- **Priority**: P1
- **Dependencies**: Task 2.2, Task 2.3
- **Estimated**: 1.5 hours

**INPUT:**

- SignUpRequest DTO requirements
- Email verification flow

**OUTPUT:**

- `SignUpRequest.java` with validation
- `AuthService.signUp()` method
- `EmailVerificationService.sendVerificationEmail()`
- Email template: `verification.html`

**VERIFY:**

```bash
# Unit tests
./gradlew :identity-auth:test --tests "*SignUpTest*"

# Integration test (manual)
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"Test123!"}'
# Returns 201 Created
# Email sent (check mailhog/logs)
```

---

#### Task 3.2: Implement Email Verification Flow

- **Agent**: `backend-specialist`
- **Skills**: `clean-code`
- **Priority**: P1
- **Dependencies**: Task 3.1
- **Estimated**: 45 min

**INPUT:**

- Verification token structure
- User table update requirements

**OUTPUT:**

- `EmailVerificationService.verifyEmail(token)`
- `VerifyEmailRequest.java`
- Update user's `emailVerified` field

**VERIFY:**

```bash
./gradlew :identity-auth:test --tests "*VerificationTest*"
# Token validates correctly
# User emailVerified = true after verification
```

---

#### Task 3.3: Implement Sign In Flow

- **Agent**: `backend-specialist`
- **Skills**: `clean-code`, `api-patterns`
- **Priority**: P0 (Critical)
- **Dependencies**: Task 2.3
- **Estimated**: 1.5 hours

**INPUT:**

- SignInRequest DTO (username/email + password)
- Token generation requirements

**OUTPUT:**

- `SignInRequest.java`
- `AuthResponse.java` (tokens + user info)
- `AuthService.signIn()` method
- `RefreshTokenService.createRefreshToken()`

**VERIFY:**

```bash
./gradlew :identity-auth:test --tests "*SignInTest*"

# Integration test
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin123!"}'
# Returns 200 with accessToken and refreshToken
```

---

#### Task 3.4: Implement Token Refresh Flow

- **Agent**: `backend-specialist`
- **Skills**: `clean-code`
- **Priority**: P0 (Critical)
- **Dependencies**: Task 3.3
- **Estimated**: 45 min

**INPUT:**

- Refresh token validation logic
- New access token generation

**OUTPUT:**

- `RefreshTokenRequest.java`
- `TokenResponse.java`
- `RefreshTokenService.refreshAccessToken()`

**VERIFY:**

```bash
./gradlew :identity-auth:test --tests "*RefreshTokenTest*"

# Integration test
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"<token-from-login>"}'
# Returns new accessToken
```

---

#### Task 3.5: Implement Sign Out Flow

- **Agent**: `backend-specialist`
- **Skills**: `clean-code`
- **Priority**: P1
- **Dependencies**: Task 3.3
- **Estimated**: 30 min

**INPUT:**

- Refresh token revocation logic
- Optional: revoke all sessions

**OUTPUT:**

- `AuthService.signOut(refreshToken)`
- `AuthService.signOutAll(userId)` (revoke all sessions)

**VERIFY:**

```bash
./gradlew :identity-auth:test --tests "*SignOutTest*"

# Integration test
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <access-token>" \
  -d '{"refreshToken":"<refresh-token>"}'
# Returns 200
# Refresh token no longer works
```

---

#### Task 3.6: Implement Forgot Password Flow

- **Agent**: `backend-specialist`
- **Skills**: `clean-code`
- **Priority**: P1
- **Dependencies**: Task 2.2
- **Estimated**: 1 hour

**INPUT:**

- Password reset token requirements
- Email template

**OUTPUT:**

- `ForgotPasswordRequest.java`
- `PasswordResetService.sendResetEmail(email)`
- Email template: `password-reset.html`

**VERIFY:**

```bash
./gradlew :identity-auth:test --tests "*ForgotPasswordTest*"

# Integration test
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -d '{"email":"test@example.com"}'
# Returns 200 (always, for security)
# Email sent if user exists
```

---

#### Task 3.7: Implement Reset Password Flow

- **Agent**: `backend-specialist`
- **Skills**: `clean-code`
- **Priority**: P1
- **Dependencies**: Task 3.6
- **Estimated**: 45 min

**INPUT:**

- Reset token validation
- Password update logic

**OUTPUT:**

- `ResetPasswordRequest.java`
- `PasswordResetService.resetPassword(token, newPassword)`
- Invalidate all existing refresh tokens

**VERIFY:**

```bash
./gradlew :identity-auth:test --tests "*ResetPasswordTest*"
# Token validates
# Password updated
# Old sessions invalidated
```

---

#### Task 3.8: Implement Session Management

- **Agent**: `backend-specialist`
- **Skills**: `clean-code`, `api-patterns`
- **Priority**: P2
- **Dependencies**: Task 3.3
- **Estimated**: 1 hour

**INPUT:**

- Session listing requirements
- Session revocation logic

**OUTPUT:**

- `SessionResponse.java`
- `SessionService.getUserSessions(userId)`
- `SessionService.revokeSession(sessionId)`

**VERIFY:**

```bash
./gradlew :identity-auth:test --tests "*SessionTest*"

# Integration test
curl -X GET http://localhost:8080/api/auth/sessions \
  -H "Authorization: Bearer <access-token>"
# Returns list of active sessions
```

---

### Phase 4: Controller & Security Integration

#### Task 4.1: Implement AuthController

- **Agent**: `backend-specialist`
- **Skills**: `api-patterns`, `clean-code`
- **Priority**: P0 (Critical)
- **Dependencies**: Task 3.1 - 3.8
- **Estimated**: 1 hour

**INPUT:**

- All service methods
- REST API design

**OUTPUT:**

- `AuthController.java` with endpoints:
  - `POST /api/auth/signup`
  - `POST /api/auth/login`
  - `POST /api/auth/logout`
  - `POST /api/auth/refresh`
  - `POST /api/auth/forgot-password`
  - `POST /api/auth/reset-password`
  - `POST /api/auth/verify-email`
  - `GET /api/auth/sessions`
  - `DELETE /api/auth/sessions/{id}`

**VERIFY:**

```bash
./gradlew :identity-auth:compileJava
# All endpoints documented
# Proper HTTP status codes
```

---

#### Task 4.2: Update Security Configuration

- **Agent**: `security-auditor`
- **Skills**: `clean-code`
- **Priority**: P0 (Critical)
- **Dependencies**: Task 4.1
- **Estimated**: 1 hour

**INPUT:**

- Endpoint security requirements
- JWT filter requirements

**OUTPUT:**

- Updated `SecurityConfig.java`:
  - Public endpoints: `/api/auth/**` (except logout, sessions)
  - JWT authentication filter
  - CORS configuration for API Gateway

**VERIFY:**

```bash
./gradlew :identity-app:compileJava
# Security config compiles
# Public endpoints accessible without auth
# Protected endpoints require valid JWT
```

---

### Phase 5: Testing & Documentation

#### Task 5.1: Unit Tests for Auth Services

- **Agent**: `backend-specialist`
- **Skills**: `testing-patterns`, `clean-code`
- **Priority**: P1
- **Dependencies**: Phase 3 complete
- **Estimated**: 2 hours

**INPUT:**

- All service classes
- Edge cases and error scenarios

**OUTPUT:**

- `AuthServiceTest.java`
- `TokenServiceTest.java`
- `RefreshTokenServiceTest.java`
- `EmailVerificationServiceTest.java`
- `PasswordResetServiceTest.java`
- `SessionServiceTest.java`

**VERIFY:**

```bash
./gradlew :identity-auth:test
./gradlew :identity-auth:jacocoTestReport
# Coverage > 80%
```

---

#### Task 5.2: Integration Tests

- **Agent**: `backend-specialist`
- **Skills**: `testing-patterns`
- **Priority**: P1
- **Dependencies**: Task 5.1
- **Estimated**: 2 hours

**INPUT:**

- Auth flow requirements
- Database integration

**OUTPUT:**

- `AuthIntegrationTest.java` - Full auth flows
- Test with real database (Testcontainers)

**VERIFY:**

```bash
./gradlew :identity-auth:testWithContainers
# All integration tests pass
```

---

#### Task 5.3: API Documentation

- **Agent**: `backend-specialist`
- **Skills**: `documentation-templates`
- **Priority**: P2
- **Dependencies**: Task 4.1
- **Estimated**: 1 hour

**INPUT:**

- All API endpoints
- Request/Response examples

**OUTPUT:**

- OpenAPI/Swagger annotations on controller
- Updated `docs/API.md` with auth endpoints

**VERIFY:**

```bash
# Swagger UI accessible at /swagger-ui.html
# All endpoints documented with examples
```

---

## API Reference

### Endpoints Summary

| Method | Endpoint                    | Auth   | Description                    |
| ------ | --------------------------- | ------ | ------------------------------ |
| POST   | `/api/auth/signup`          | Public | Register new user              |
| POST   | `/api/auth/verify-email`    | Public | Verify email with token        |
| POST   | `/api/auth/login`           | Public | Sign in, get tokens            |
| POST   | `/api/auth/refresh`         | Public | Refresh access token           |
| POST   | `/api/auth/logout`          | Bearer | Sign out, revoke refresh token |
| POST   | `/api/auth/forgot-password` | Public | Request password reset         |
| POST   | `/api/auth/reset-password`  | Public | Reset password with token      |
| GET    | `/api/auth/sessions`        | Bearer | List active sessions           |
| DELETE | `/api/auth/sessions/{id}`   | Bearer | Revoke specific session        |

### Request/Response Examples

<details>
<summary>POST /api/auth/signup</summary>

**Request:**

```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "password": "SecurePass123!",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Response (201 Created):**

```json
{
  "message": "Registration successful. Please verify your email.",
  "userId": 123
}
```

</details>

<details>
<summary>POST /api/auth/login</summary>

**Request:**

```json
{
  "username": "johndoe",
  "password": "SecurePass123!"
}
```

**Response (200 OK):**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": 123,
    "username": "johndoe",
    "email": "john@example.com",
    "roles": ["USER"]
  }
}
```

</details>

<details>
<summary>POST /api/auth/refresh</summary>

**Request:**

```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response (200 OK):**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

</details>

<details>
<summary>GET /api/auth/sessions</summary>

**Response (200 OK):**

```json
{
  "sessions": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "deviceInfo": "Chrome on Windows",
      "ipAddress": "192.168.1.1",
      "createdAt": "2026-01-28T10:00:00Z",
      "lastUsedAt": "2026-01-28T17:00:00Z",
      "current": true
    },
    {
      "id": "660e8400-e29b-41d4-a716-446655440001",
      "deviceInfo": "Safari on iPhone",
      "ipAddress": "192.168.1.2",
      "createdAt": "2026-01-27T08:00:00Z",
      "lastUsedAt": "2026-01-27T20:00:00Z",
      "current": false
    }
  ]
}
```

</details>

---

## Configuration

### Application Properties

```properties
# JWT Configuration
jwt.secret=${JWT_SECRET:your-256-bit-secret-key-here}
jwt.access-token.expiration=900000      # 15 minutes in ms
jwt.refresh-token.expiration=604800000  # 7 days in ms
jwt.issuer=ecm-identity-service

# Email Configuration
spring.mail.host=${MAIL_HOST:smtp.gmail.com}
spring.mail.port=${MAIL_PORT:587}
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# Verification
auth.verification.expiration=86400000   # 24 hours in ms
auth.password-reset.expiration=3600000  # 1 hour in ms
auth.verification.base-url=${BASE_URL:http://localhost:3000}
```

---

## Risk Assessment

| Risk                   | Impact | Mitigation                                             |
| ---------------------- | ------ | ------------------------------------------------------ |
| Token secret leaked    | High   | Use strong secret, rotate periodically, store in Vault |
| Brute force login      | Medium | Rate limiting, account lockout after N attempts        |
| Email delivery failure | Medium | Retry logic, fallback provider, monitoring             |
| Token replay attack    | Medium | Short expiry, one-time use for verification tokens     |
| Session hijacking      | High   | HTTPS only, secure cookie flags, IP validation         |

---

## Timeline Estimate

| Phase                 | Tasks         | Estimated Time |
| --------------------- | ------------- | -------------- |
| Phase 1: Foundation   | 1.1, 1.2      | 1 hour         |
| Phase 2: Module Setup | 2.1, 2.2, 2.3 | 2 hours        |
| Phase 3: Auth Flows   | 3.1 - 3.8     | 7 hours        |
| Phase 4: Integration  | 4.1, 4.2      | 2 hours        |
| Phase 5: Testing      | 5.1, 5.2, 5.3 | 5 hours        |

**Total Estimated: ~17 hours (2-3 days)**

---

## Phase X: Verification Checklist

### Pre-Deployment Checks

- [ ] All unit tests pass: `./gradlew :identity-auth:test`
- [ ] All integration tests pass: `./gradlew :identity-auth:testWithContainers`
- [ ] Code coverage > 80%
- [ ] No security vulnerabilities: `./gradlew dependencyCheckAnalyze`
- [ ] Build successful: `./gradlew build`
- [ ] Liquibase migration validated: `make db-validate`

### Functional Testing

- [ ] Sign up flow works end-to-end
- [ ] Email verification arrives and works
- [ ] Sign in returns valid tokens
- [ ] Token refresh works
- [ ] Sign out invalidates refresh token
- [ ] Password reset flow works
- [ ] Session listing shows all devices
- [ ] Session revocation works

### Security Testing

- [ ] Passwords hashed with BCrypt (cost 12+)
- [ ] JWT secret is 256+ bits
- [ ] Tokens expire correctly
- [ ] Invalid tokens rejected
- [ ] Rate limiting works on login
- [ ] SQL injection protected
- [ ] XSS protected in error messages

### API Gateway Readiness

- [ ] CORS configured correctly
- [ ] API documented (Swagger/OpenAPI)
- [ ] Health check endpoint exists
- [ ] Metrics exposed (Prometheus)

---

## Next Steps After Planning

1. **Review this plan** - Check if anything is missing
2. **Run `/create`** - Start implementation
3. **Phase by phase** - Complete each phase before moving to next

---

> **Plan Status**: READY FOR REVIEW
> **Plan File**: `auth-module.md`
