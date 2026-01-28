# Auth Module Implementation Practice Requirements

> **Purpose**: Learn Java and Spring Boot by implementing a complete authentication module
> **Difficulty**: Intermediate
> **Estimated Time**: 20-30 hours of focused practice
> **Prerequisites**: Basic Java knowledge, understanding of REST APIs

---

## 🎯 Learning Objectives

After completing this practice, you will understand:

| Category | Skills You'll Learn |
|----------|---------------------|
| **Java Core** | Records, Generics, Streams, Optional, Functional interfaces |
| **Spring Boot** | Dependency Injection, Configuration, Profiles, Auto-configuration |
| **Spring Security** | Authentication, Authorization, JWT, Password encoding |
| **Spring Data JPA** | Entities, Repositories, Queries, Relationships |
| **Spring Web** | REST Controllers, Request/Response handling, Validation |
| **Best Practices** | Clean Code, Error handling, Logging, Testing |

---

## 📚 Before You Start

### Required Knowledge
- [ ] Java basics (classes, interfaces, inheritance)
- [ ] HTTP methods (GET, POST, PUT, DELETE)
- [ ] JSON format
- [ ] Basic SQL

### Setup Checklist
- [ ] JDK 21+ installed
- [ ] Gradle or Maven understanding
- [ ] IDE (IntelliJ IDEA recommended)
- [ ] PostgreSQL running (use Docker)
- [ ] Postman or similar API testing tool

### Reference Documentation
- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/)

---

## 🏗️ Project Structure to Create

```
identity-auth/
├── build.gradle
└── src/
    ├── main/java/com/ecm/security/identity/auth/
    │   ├── controller/
    │   │   └── AuthController.java
    │   ├── service/
    │   │   ├── AuthService.java
    │   │   ├── TokenService.java
    │   │   ├── RefreshTokenService.java
    │   │   ├── EmailVerificationService.java
    │   │   ├── PasswordResetService.java
    │   │   └── SessionService.java
    │   ├── dto/
    │   │   ├── request/
    │   │   │   ├── SignUpRequest.java
    │   │   │   ├── SignInRequest.java
    │   │   │   └── ... (more DTOs)
    │   │   └── response/
    │   │       ├── AuthResponse.java
    │   │       └── ... (more DTOs)
    │   ├── entity/
    │   │   ├── RefreshTokenEntity.java
    │   │   └── VerificationTokenEntity.java
    │   ├── repository/
    │   │   ├── RefreshTokenRepository.java
    │   │   └── VerificationTokenRepository.java
    │   ├── config/
    │   │   └── JwtConfig.java
    │   ├── exception/
    │   │   └── ... (custom exceptions)
    │   └── security/
    │       └── JwtAuthenticationFilter.java
    └── test/java/com/ecm/security/identity/auth/
        └── ... (test classes)
```

---

# Exercise 1: Module Setup & Dependencies

## 📋 Requirements

**REQ-1.1**: Create a new Gradle submodule named `identity-auth`

**REQ-1.2**: Configure `build.gradle` with these dependencies:
- Spring Boot Starter Web
- Spring Boot Starter Security
- Spring Boot Starter Data JPA
- Spring Boot Starter Validation
- Spring Boot Starter Mail
- JJWT (JWT library): `io.jsonwebtoken:jjwt-api:0.12.5`
- Lombok
- Project dependencies: `:identity-shared`, `:identity-user`

**REQ-1.3**: Register the module in `settings.gradle`

## 🎓 Spring Concepts to Learn

| Concept | Description | Where to Use |
|---------|-------------|--------------|
| **Gradle Multi-Project** | How to structure multi-module Gradle projects | `settings.gradle`, `build.gradle` |
| **Dependency Management** | Spring Boot BOM, version management | `build.gradle` |
| **Dependency Injection** | `@Autowired`, constructor injection | All classes |

## ✅ Verification

```bash
./gradlew :identity-auth:compileJava
# Should compile without errors
```

## 💡 Hints

<details>
<summary>Click to see build.gradle template</summary>

```groovy
plugins {
    id 'java-library'
}

dependencies {
    implementation project(':identity-shared')
    implementation project(':identity-user')
    
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-mail'
    
    // JWT
    implementation 'io.jsonwebtoken:jjwt-api:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.5'
    
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
}
```

</details>

---

# Exercise 2: Create DTOs (Data Transfer Objects)

## 📋 Requirements

### Request DTOs

**REQ-2.1**: Create `SignUpRequest` record with:
- `username`: 3-50 chars, alphanumeric + underscore only
- `email`: valid email format
- `password`: 8-128 chars, must have uppercase, lowercase, digit, special char
- `firstName`: optional, max 100 chars
- `lastName`: optional, max 100 chars
- `tenantId`: optional (for multi-tenant)

**REQ-2.2**: Create `SignInRequest` record with:
- `usernameOrEmail`: not blank (accepts both username or email)
- `password`: not blank
- `deviceInfo`: optional (e.g., "Chrome on Windows")

**REQ-2.3**: Create `RefreshTokenRequest` record with:
- `refreshToken`: not blank

**REQ-2.4**: Create `ForgotPasswordRequest` record with:
- `email`: valid email format

**REQ-2.5**: Create `ResetPasswordRequest` record with:
- `token`: not blank
- `newPassword`: 8-128 chars with complexity rules
- `confirmPassword`: not blank

**REQ-2.6**: Create `VerifyEmailRequest` record with:
- `token`: not blank

### Response DTOs

**REQ-2.7**: Create `AuthResponse` record with:
- `accessToken`: the JWT token
- `refreshToken`: the refresh token UUID
- `tokenType`: always "Bearer"
- `expiresIn`: seconds until expiration
- `user`: nested `UserInfo` record containing id, username, email, roles

**REQ-2.8**: Create `TokenResponse` record with:
- `accessToken`: the new JWT token
- `tokenType`: "Bearer"
- `expiresIn`: seconds

**REQ-2.9**: Create `SignUpResponse` record with:
- `message`: success message
- `userId`: created user ID
- `verificationEmailSent`: boolean

**REQ-2.10**: Create `SessionResponse` record with:
- `id`: session UUID
- `deviceInfo`: device description
- `ipAddress`: client IP
- `createdAt`: when session started
- `lastUsedAt`: last activity
- `current`: is this the current session?

**REQ-2.11**: Create `MessageResponse` record for generic responses:
- `message`: the message
- `timestamp`: when the response was created

## 🎓 Java Concepts to Learn

| Concept | Description | Example |
|---------|-------------|---------|
| **Java Records** | Immutable data carriers (Java 16+) | `public record SignUpRequest(...) {}` |
| **Validation Annotations** | Bean Validation (JSR-380) | `@NotBlank`, `@Email`, `@Size` |
| **Pattern Matching** | Regex validation | `@Pattern(regexp = "...")` |
| **Nested Records** | Records inside records | `UserInfo` inside `AuthResponse` |

## ✅ Verification

```java
// Test your validation works
SignUpRequest request = new SignUpRequest(
    "ab",  // Too short - should fail
    "invalid-email",  // Invalid - should fail
    "weak",  // Too weak - should fail
    null, null, null
);
// Should have validation errors
```

## 💡 Hints

<details>
<summary>Click to see SignUpRequest example</summary>

```java
package com.ecm.security.identity.auth.dto.request;

import jakarta.validation.constraints.*;

public record SignUpRequest(
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", 
             message = "Username can only contain letters, numbers, and underscores")
    String username,
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,
    
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
             message = "Password must contain uppercase, lowercase, number, and special character")
    String password,
    
    @Size(max = 100) 
    String firstName,
    
    @Size(max = 100) 
    String lastName,
    
    String tenantId
) {}
```

</details>

<details>
<summary>Click to see AuthResponse example</summary>

```java
package com.ecm.security.identity.auth.dto.response;

import java.util.Set;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
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
    
    // Factory method for convenience
    public static AuthResponse of(String accessToken, String refreshToken, 
                                   long expiresIn, UserInfo user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
```

</details>

---

# Exercise 3: Create Entities

## 📋 Requirements

**REQ-3.1**: Create `RefreshTokenEntity` with:
- `id`: UUID, auto-generated
- `user`: ManyToOne relationship to UserEntity (LAZY fetch)
- `tokenHash`: VARCHAR(255), unique, not null - stores SHA-256 hash
- `deviceInfo`: VARCHAR(500), nullable
- `ipAddress`: VARCHAR(45), nullable (supports IPv6)
- `expiresAt`: Instant, not null
- `createdAt`: Instant, not null, auto-set on create
- `lastUsedAt`: Instant, nullable
- `revokedAt`: Instant, nullable

**REQ-3.2**: Add business methods to `RefreshTokenEntity`:
- `isExpired()`: returns true if current time > expiresAt
- `isRevoked()`: returns true if revokedAt is not null
- `isValid()`: returns true if not expired AND not revoked
- `markAsUsed()`: updates lastUsedAt to current time
- `revoke()`: sets revokedAt to current time

**REQ-3.3**: Create `VerificationTokenEntity` with:
- `id`: UUID, auto-generated
- `user`: ManyToOne relationship to UserEntity (LAZY fetch)
- `tokenHash`: VARCHAR(255), unique, not null
- `tokenType`: Enum (EMAIL_VERIFICATION, PASSWORD_RESET)
- `expiresAt`: Instant, not null
- `createdAt`: Instant, not null, auto-set on create
- `usedAt`: Instant, nullable

**REQ-3.4**: Add business methods to `VerificationTokenEntity`:
- `isExpired()`: returns true if current time > expiresAt
- `isUsed()`: returns true if usedAt is not null
- `isValid()`: returns true if not expired AND not used
- `markAsUsed()`: sets usedAt to current time

**REQ-3.5**: Create `TokenType` enum:
```java
public enum TokenType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
}
```

## 🎓 Spring/JPA Concepts to Learn

| Concept | Description | Annotation |
|---------|-------------|------------|
| **Entity** | Maps class to database table | `@Entity` |
| **Table** | Custom table name | `@Table(name = "...")` |
| **Id** | Primary key | `@Id` |
| **GeneratedValue** | Auto-generate ID | `@GeneratedValue(strategy = ...)` |
| **Column** | Column mapping | `@Column(name = "...", nullable = ...)` |
| **ManyToOne** | Relationship to parent | `@ManyToOne(fetch = FetchType.LAZY)` |
| **JoinColumn** | Foreign key column | `@JoinColumn(name = "user_id")` |
| **Enumerated** | Store enum as string | `@Enumerated(EnumType.STRING)` |
| **PrePersist** | Callback before insert | `@PrePersist` |

## ✅ Verification

```java
RefreshTokenEntity token = new RefreshTokenEntity();
token.setExpiresAt(Instant.now().plusSeconds(3600));
assert !token.isExpired();
assert !token.isRevoked();
assert token.isValid();

token.revoke();
assert token.isRevoked();
assert !token.isValid();
```

## 💡 Hints

<details>
<summary>Click to see RefreshTokenEntity example</summary>

```java
package com.ecm.security.identity.auth.entity;

import com.ecm.security.identity.entity.UserEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_token_hash", columnList = "token_hash"),
    @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
    @Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshTokenEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;
    
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;
    
    @Column(name = "device_info", length = 500)
    private String deviceInfo;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
    
    @Column(name = "revoked_at")
    private Instant revokedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
    
    // Business methods
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    public boolean isRevoked() {
        return revokedAt != null;
    }
    
    public boolean isValid() {
        return !isExpired() && !isRevoked();
    }
    
    public void markAsUsed() {
        this.lastUsedAt = Instant.now();
    }
    
    public void revoke() {
        this.revokedAt = Instant.now();
    }
}
```

</details>

---

# Exercise 4: Create Repositories

## 📋 Requirements

**REQ-4.1**: Create `RefreshTokenRepository` interface extending `JpaRepository<RefreshTokenEntity, UUID>`:
- `findByTokenHash(String tokenHash)`: returns Optional
- `findAllByUserIdAndRevokedAtIsNull(Long userId)`: returns List (active sessions)
- `findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId)`: sorted sessions
- `countByUserIdAndRevokedAtIsNull(Long userId)`: count active sessions

**REQ-4.2**: Add custom queries to `RefreshTokenRepository`:
- Delete expired tokens: `deleteAllByExpiresAtBefore(Instant before)`
- Revoke all user tokens: custom `@Modifying` query

**REQ-4.3**: Create `VerificationTokenRepository` interface extending `JpaRepository<VerificationTokenEntity, UUID>`:
- `findByTokenHash(String tokenHash)`: returns Optional
- `findByTokenHashAndTokenType(String tokenHash, TokenType type)`: returns Optional
- `findByUserIdAndTokenTypeAndUsedAtIsNull(Long userId, TokenType type)`: active token
- `deleteAllByExpiresAtBefore(Instant before)`: cleanup

**REQ-4.4**: Add `@Modifying` query to revoke all user tokens:
```java
@Modifying
@Query("UPDATE RefreshTokenEntity r SET r.revokedAt = :now WHERE r.user.id = :userId AND r.revokedAt IS NULL")
int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
```

## 🎓 Spring Data JPA Concepts to Learn

| Concept | Description | Example |
|---------|-------------|---------|
| **JpaRepository** | CRUD + JPA features | `extends JpaRepository<Entity, ID>` |
| **Query Methods** | Method name to query | `findByUsername(String username)` |
| **@Query** | Custom JPQL/SQL | `@Query("SELECT u FROM User u WHERE ...")` |
| **@Modifying** | UPDATE/DELETE queries | `@Modifying @Query("UPDATE ...")` |
| **@Param** | Named parameters | `@Param("userId")` |
| **Optional return** | Null-safe returns | `Optional<Entity>` |

## ✅ Verification

```java
// These should work after implementation
repository.findByTokenHash("abc123");
repository.findAllByUserIdAndRevokedAtIsNull(1L);
repository.revokeAllByUserId(1L, Instant.now());
```

## 💡 Hints

<details>
<summary>Click to see RefreshTokenRepository example</summary>

```java
package com.ecm.security.identity.auth.repository;

import com.ecm.security.identity.auth.entity.RefreshTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
    
    Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);
    
    List<RefreshTokenEntity> findAllByUserIdAndRevokedAtIsNull(Long userId);
    
    List<RefreshTokenEntity> findAllByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(Long userId);
    
    long countByUserIdAndRevokedAtIsNull(Long userId);
    
    void deleteAllByExpiresAtBefore(Instant before);
    
    @Modifying
    @Query("UPDATE RefreshTokenEntity r SET r.revokedAt = :now " +
           "WHERE r.user.id = :userId AND r.revokedAt IS NULL")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);
    
    @Modifying
    @Query("DELETE FROM RefreshTokenEntity r WHERE r.expiresAt < :before AND r.revokedAt IS NOT NULL")
    int deleteExpiredAndRevokedTokens(@Param("before") Instant before);
}
```

</details>

---

# Exercise 5: Implement TokenService (JWT)

## 📋 Requirements

**REQ-5.1**: Create `JwtConfig` configuration class:
- Read `jwt.secret` from properties (minimum 256 bits / 32 characters)
- Read `jwt.access-token.expiration` (default: 900000ms = 15 min)
- Read `jwt.refresh-token.expiration` (default: 604800000ms = 7 days)
- Read `jwt.issuer` (default: "ecm-identity-service")
- Create `SecretKey` bean from the secret

**REQ-5.2**: Create `TokenService` with these methods:

`generateAccessToken(User user)`:
- Create JWT with claims:
  - `sub`: userId as string
  - `username`: user's username
  - `email`: user's email
  - `roles`: list of role names (e.g., ["ROLE_USER", "ROLE_ADMIN"])
  - `iat`: issued at (current timestamp)
  - `exp`: expiration (iat + 15 minutes)
  - `iss`: issuer name
- Sign with HS256 algorithm
- Return the JWT string

`parseAccessToken(String token)`:
- Parse and validate the JWT
- Verify signature matches
- Verify not expired
- Verify issuer matches
- Return Claims object
- Throw `InvalidTokenException` if invalid
- Throw `TokenExpiredException` if expired

`isValidAccessToken(String token)`:
- Return true if token is valid
- Return false if invalid (don't throw)

`getUserIdFromToken(String token)`:
- Extract and return the user ID from subject claim

`getRolesFromToken(String token)`:
- Extract and return the roles list from claims

## 🎓 Concepts to Learn

| Concept | Description | Library |
|---------|-------------|---------|
| **JWT Structure** | Header.Payload.Signature | JJWT |
| **Claims** | Data stored in JWT | `Jwts.claims()` |
| **Signing** | Creating signature | `signWith(key)` |
| **Parsing** | Validating & reading | `Jwts.parser()` |
| **@ConfigurationProperties** | Type-safe config | Spring Boot |
| **SecretKey** | Cryptographic key | `Keys.hmacShaKeyFor()` |

## ✅ Verification

```java
// Test token generation and parsing
User user = User.builder()
    .id(1L)
    .username("testuser")
    .email("test@example.com")
    .roles(Set.of(Role.builder().name("USER").build()))
    .build();

String token = tokenService.generateAccessToken(user);
assert token != null;
assert token.split("\\.").length == 3; // JWT has 3 parts

Claims claims = tokenService.parseAccessToken(token);
assert claims.getSubject().equals("1");
assert claims.get("username").equals("testuser");

// Test expiration
Thread.sleep(16 * 60 * 1000); // Wait 16 minutes
assert !tokenService.isValidAccessToken(token); // Should be expired
```

## 💡 Hints

<details>
<summary>Click to see JwtConfig example</summary>

```java
package com.ecm.security.identity.auth.config;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    
    private String secret;
    private long accessTokenExpiration = 900000; // 15 minutes
    private long refreshTokenExpiration = 604800000; // 7 days
    private String issuer = "ecm-identity-service";
    
    // Getters and setters...
    
    @Bean
    public SecretKey jwtSecretKey() {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 characters");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
```

</details>

<details>
<summary>Click to see TokenService example</summary>

```java
package com.ecm.security.identity.auth.service;

import com.ecm.security.identity.auth.config.JwtConfig;
import com.ecm.security.identity.auth.exception.InvalidTokenException;
import com.ecm.security.identity.auth.exception.TokenExpiredException;
import com.ecm.security.identity.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {
    
    private final JwtConfig jwtConfig;
    private final SecretKey secretKey;
    
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getAccessTokenExpiration());
        
        List<String> roles = user.getRoles().stream()
            .map(role -> "ROLE_" + role.getName())
            .collect(Collectors.toList());
        
        return Jwts.builder()
            .subject(String.valueOf(user.getId()))
            .claim("username", user.getUsername())
            .claim("email", user.getEmail())
            .claim("roles", roles)
            .issuer(jwtConfig.getIssuer())
            .issuedAt(now)
            .expiration(expiry)
            .signWith(secretKey)
            .compact();
    }
    
    public Claims parseAccessToken(String token) {
        try {
            return Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(jwtConfig.getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException("Access token has expired");
        } catch (SignatureException | MalformedJwtException e) {
            throw new InvalidTokenException("Invalid access token");
        }
    }
    
    public boolean isValidAccessToken(String token) {
        try {
            parseAccessToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public Long getUserIdFromToken(String token) {
        Claims claims = parseAccessToken(token);
        return Long.parseLong(claims.getSubject());
    }
    
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Claims claims = parseAccessToken(token);
        return (List<String>) claims.get("roles");
    }
}
```

</details>

---

# Exercise 6: Implement RefreshTokenService

## 📋 Requirements

**REQ-6.1**: Create helper method `hashToken(String token)`:
- Use SHA-256 to hash the token
- Return hex-encoded hash string
- This prevents token leakage if database is compromised

**REQ-6.2**: Implement `createRefreshToken(User user, String deviceInfo, String ipAddress)`:
- Generate a random UUID as the token
- Hash the token with SHA-256
- Create RefreshTokenEntity with:
  - user reference
  - hashed token
  - device info and IP
  - expiration (current time + 7 days)
- Save to database
- Return the **plain UUID** (not the hash) to send to client

**REQ-6.3**: Implement `refreshAccessToken(String refreshToken)`:
- Hash the incoming token
- Find by hash in database
- Validate: not expired, not revoked
- Update `lastUsedAt` timestamp
- Load the user
- Generate new access token
- Return TokenResponse

**REQ-6.4**: Implement `revokeToken(String refreshToken)`:
- Hash the incoming token
- Find by hash
- Set `revokedAt` to current time
- Save

**REQ-6.5**: Implement `revokeAllUserTokens(Long userId)`:
- Use the `@Modifying` query from repository
- Revoke all active tokens for user

**REQ-6.6**: Implement `getUserSessions(Long userId)`:
- Find all non-revoked tokens for user
- Map to SessionResponse DTOs
- Sort by createdAt descending

**REQ-6.7**: Implement `revokeSession(Long userId, UUID sessionId)`:
- Find the token by ID
- Verify it belongs to the user (security!)
- Revoke it

## 🎓 Concepts to Learn

| Concept | Description | Use Case |
|---------|-------------|----------|
| **SHA-256 Hashing** | One-way hash function | Secure token storage |
| **UUID Generation** | Random unique identifiers | Token generation |
| **@Transactional** | Database transaction management | Write operations |
| **Stream API** | Functional data processing | Mapping entities to DTOs |
| **Authorization Check** | Verify ownership | `revokeSession` |

## ✅ Verification

```java
// Create token
String token = service.createRefreshToken(user, "Chrome", "192.168.1.1");
assert token != null;
assert UUID.fromString(token) != null; // Valid UUID

// Refresh
TokenResponse response = service.refreshAccessToken(token);
assert response.accessToken() != null;

// Revoke
service.revokeToken(token);

// Should fail now
assertThrows(TokenRevokedException.class, () -> {
    service.refreshAccessToken(token);
});
```

## 💡 Hints

<details>
<summary>Click to see hash function example</summary>

```java
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

private String hashToken(String token) {
    try {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
        throw new RuntimeException("SHA-256 not available", e);
    }
}
```

</details>

---

# Exercise 7: Implement AuthService

## 📋 Requirements

**REQ-7.1**: Implement `signUp(SignUpRequest request)`:
- Check if username exists (case-insensitive) → throw `UsernameAlreadyExistsException`
- Check if email exists (case-insensitive) → throw `EmailAlreadyExistsException`
- Hash password with BCrypt (cost 12)
- Create user with enabled=true, emailVerified=false
- Assign default role "USER"
- Save user
- Trigger email verification (async)
- Return SignUpResponse

**REQ-7.2**: Implement `signIn(SignInRequest request)`:
- Find user by username OR email (try both)
- Check if account is locked (lockedUntil > now) → throw `AccountLockedException`
- Check if account is enabled → throw `AccountDisabledException`
- Verify password with BCrypt
- On failure: increment failedLoginAttempts, lock after 5 attempts
- On success: reset failedLoginAttempts, update lastLoginAt
- Create refresh token
- Generate access token
- Return AuthResponse with tokens and user info

**REQ-7.3**: Implement `signOut(String refreshToken, Long userId)`:
- Verify the refresh token belongs to the user
- Revoke the token
- Log the logout event

**REQ-7.4**: Implement `signOutAll(Long userId)`:
- Revoke all refresh tokens for user
- Log the event

**REQ-7.5**: Create custom exceptions:
- `UsernameAlreadyExistsException`
- `EmailAlreadyExistsException`
- `InvalidCredentialsException`
- `AccountLockedException`
- `AccountDisabledException`

## 🎓 Concepts to Learn

| Concept | Description | Spring Feature |
|---------|-------------|----------------|
| **Password Encoding** | BCrypt hashing | `BCryptPasswordEncoder` |
| **Transaction** | Atomic operations | `@Transactional` |
| **Exception Handling** | Business exceptions | Custom exceptions |
| **Async Processing** | Non-blocking email | `@Async` |
| **Event Logging** | Audit trail | SLF4J logging |

## ✅ Verification

```java
// Sign up
SignUpRequest signUp = new SignUpRequest("newuser", "new@example.com", 
    "SecurePass123!", "John", "Doe", null);
SignUpResponse response = authService.signUp(signUp);
assert response.userId() != null;

// Sign in
SignInRequest signIn = new SignInRequest("newuser", "SecurePass123!", null);
AuthResponse auth = authService.signIn(signIn);
assert auth.accessToken() != null;
assert auth.refreshToken() != null;
assert auth.user().username().equals("newuser");

// Sign out
authService.signOut(auth.refreshToken(), auth.user().id());
```

## 💡 Hints

<details>
<summary>Click to see BCrypt usage</summary>

```java
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // Cost factor 12
    }
}

// In AuthService
@RequiredArgsConstructor
public class AuthService {
    private final PasswordEncoder passwordEncoder;
    
    public SignUpResponse signUp(SignUpRequest request) {
        String hashedPassword = passwordEncoder.encode(request.password());
        // ...
    }
    
    public AuthResponse signIn(SignInRequest request) {
        User user = findUser(request.usernameOrEmail());
        
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            handleFailedLogin(user);
            throw new InvalidCredentialsException("Invalid credentials");
        }
        // ...
    }
}
```

</details>

---

# Exercise 8: Implement AuthController

## 📋 Requirements

**REQ-8.1**: Create `AuthController` with base path `/api/auth`

**REQ-8.2**: Implement endpoints:

| Method | Path | Auth | Request | Response | Status |
|--------|------|------|---------|----------|--------|
| POST | `/signup` | Public | SignUpRequest | SignUpResponse | 201 |
| POST | `/login` | Public | SignInRequest | AuthResponse | 200 |
| POST | `/logout` | Bearer | - (token in header) | MessageResponse | 200 |
| POST | `/refresh` | Public | RefreshTokenRequest | TokenResponse | 200 |
| POST | `/forgot-password` | Public | ForgotPasswordRequest | MessageResponse | 200 |
| POST | `/reset-password` | Public | ResetPasswordRequest | MessageResponse | 200 |
| POST | `/verify-email` | Public | VerifyEmailRequest | MessageResponse | 200 |
| GET | `/sessions` | Bearer | - | SessionListResponse | 200 |
| DELETE | `/sessions/{id}` | Bearer | - | MessageResponse | 200 |

**REQ-8.3**: Use `@Valid` annotation for request body validation

**REQ-8.4**: Use `@AuthenticationPrincipal` to get current user for protected endpoints

**REQ-8.5**: Extract device info from User-Agent header

**REQ-8.6**: Extract client IP from request (consider X-Forwarded-For for proxies)

**REQ-8.7**: Return proper HTTP status codes:
- 200: Success
- 201: Created (signup)
- 400: Validation error
- 401: Authentication failed
- 403: Forbidden (locked, disabled)
- 404: Not found
- 409: Conflict (duplicate username/email)

## 🎓 Concepts to Learn

| Concept | Description | Annotation |
|---------|-------------|------------|
| **RestController** | REST API controller | `@RestController` |
| **RequestMapping** | Base URL path | `@RequestMapping("/api/auth")` |
| **PostMapping** | POST endpoint | `@PostMapping("/login")` |
| **RequestBody** | JSON body parsing | `@RequestBody` |
| **Valid** | Trigger validation | `@Valid` |
| **ResponseStatus** | Set HTTP status | `@ResponseStatus(HttpStatus.CREATED)` |
| **AuthenticationPrincipal** | Get logged-in user | `@AuthenticationPrincipal` |

## ✅ Verification

```bash
# Test signup
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@test.com","password":"Test123!@"}'

# Test login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"test","password":"Test123!@"}'

# Test protected endpoint
curl -X GET http://localhost:8080/api/auth/sessions \
  -H "Authorization: Bearer <your-access-token>"
```

## 💡 Hints

<details>
<summary>Click to see AuthController example</summary>

```java
package com.ecm.security.identity.auth.controller;

import com.ecm.security.identity.auth.dto.request.*;
import com.ecm.security.identity.auth.dto.response.*;
import com.ecm.security.identity.auth.service.AuthService;
import com.ecm.security.identity.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public SignUpResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return authService.signUp(request);
    }
    
    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody SignInRequest request,
            HttpServletRequest httpRequest) {
        
        String deviceInfo = httpRequest.getHeader("User-Agent");
        String ipAddress = getClientIp(httpRequest);
        
        return authService.signIn(request.withDeviceInfo(deviceInfo, ipAddress));
    }
    
    @PostMapping("/logout")
    public MessageResponse logout(
            @AuthenticationPrincipal User user,
            @RequestHeader("Authorization") String authHeader) {
        
        // Extract refresh token from request body or header
        authService.signOut(user.getId());
        return new MessageResponse("Logged out successfully", Instant.now());
    }
    
    @PostMapping("/refresh")
    public TokenResponse refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refreshToken(request.refreshToken());
    }
    
    @GetMapping("/sessions")
    public SessionListResponse getSessions(@AuthenticationPrincipal User user) {
        return authService.getUserSessions(user.getId());
    }
    
    @DeleteMapping("/sessions/{id}")
    public MessageResponse revokeSession(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        
        authService.revokeSession(user.getId(), id);
        return new MessageResponse("Session revoked", Instant.now());
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

</details>

---

# Exercise 9: Implement JWT Authentication Filter

## 📋 Requirements

**REQ-9.1**: Create `JwtAuthenticationFilter` extending `OncePerRequestFilter`

**REQ-9.2**: Filter logic:
1. Extract JWT from `Authorization: Bearer <token>` header
2. If no token, continue filter chain (public endpoints)
3. If token exists:
   - Validate the token
   - Extract user ID and roles
   - Create `UsernamePasswordAuthenticationToken`
   - Set in `SecurityContextHolder`
4. Continue filter chain

**REQ-9.3**: Handle exceptions gracefully:
- Invalid token → Clear context, continue (will get 401 from security)
- Expired token → Clear context, continue

**REQ-9.4**: Create `SecurityConfig` to configure:
- Disable CSRF (stateless API)
- Configure stateless session management
- Add JWT filter before `UsernamePasswordAuthenticationFilter`
- Configure public endpoints: `/api/auth/signup`, `/api/auth/login`, `/api/auth/refresh`, etc.
- Require authentication for other endpoints

## 🎓 Concepts to Learn

| Concept | Description | Class/Annotation |
|---------|-------------|------------------|
| **OncePerRequestFilter** | Filter runs once per request | Spring Web |
| **SecurityFilterChain** | Security configuration | `@Bean` |
| **AuthenticationToken** | User authentication object | `UsernamePasswordAuthenticationToken` |
| **SecurityContextHolder** | Thread-local auth storage | Static access |
| **GrantedAuthority** | User permissions/roles | `SimpleGrantedAuthority` |

## ✅ Verification

```bash
# Without token - should work for public endpoints
curl http://localhost:8080/api/auth/login ...

# Without token - should fail for protected endpoints
curl http://localhost:8080/api/auth/sessions
# Returns 401 Unauthorized

# With valid token - should work
curl -H "Authorization: Bearer <valid-token>" http://localhost:8080/api/auth/sessions
# Returns 200 with sessions

# With invalid token - should fail
curl -H "Authorization: Bearer invalid-token" http://localhost:8080/api/auth/sessions
# Returns 401 Unauthorized
```

## 💡 Hints

<details>
<summary>Click to see SecurityConfig example</summary>

```java
package com.ecm.security.identity.config;

import com.ecm.security.identity.auth.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtFilter;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/auth/signup",
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/forgot-password",
                    "/api/auth/reset-password",
                    "/api/auth/verify-email"
                ).permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
```

</details>

---

# Exercise 10: Implement Exception Handling

## 📋 Requirements

**REQ-10.1**: Create base exception `AuthException` extending `RuntimeException`:
- Include error code field
- Include HTTP status field

**REQ-10.2**: Create specific exceptions:
- `InvalidCredentialsException` (401)
- `InvalidTokenException` (401)
- `TokenExpiredException` (401)
- `TokenRevokedException` (401)
- `AccountLockedException` (403)
- `AccountDisabledException` (403)
- `EmailNotVerifiedException` (403)
- `UsernameAlreadyExistsException` (409)
- `EmailAlreadyExistsException` (409)
- `RateLimitExceededException` (429)

**REQ-10.3**: Create `GlobalExceptionHandler` with `@RestControllerAdvice`:
- Handle each auth exception
- Handle validation exceptions (`MethodArgumentNotValidException`)
- Handle generic exceptions
- Return consistent `ErrorResponse` format

**REQ-10.4**: `ErrorResponse` should include:
- `timestamp`: when error occurred
- `status`: HTTP status code
- `error`: error code string
- `message`: human-readable message
- `path`: request path
- `validationErrors`: map of field → message (for validation errors)

## 🎓 Concepts to Learn

| Concept | Description | Annotation |
|---------|-------------|------------|
| **@RestControllerAdvice** | Global exception handling | Class annotation |
| **@ExceptionHandler** | Handle specific exception | Method annotation |
| **ResponseEntity** | Full HTTP response control | Return type |
| **BindingResult** | Validation errors | Method parameter |

## ✅ Verification

```java
// Invalid login should return proper error
// POST /api/auth/login with wrong password

// Response:
{
    "timestamp": "2026-01-28T10:00:00Z",
    "status": 401,
    "error": "INVALID_CREDENTIALS",
    "message": "Invalid username or password",
    "path": "/api/auth/login"
}

// Validation error should return field errors
// POST /api/auth/signup with invalid data

// Response:
{
    "timestamp": "2026-01-28T10:00:00Z",
    "status": 400,
    "error": "VALIDATION_ERROR",
    "message": "Validation failed",
    "path": "/api/auth/signup",
    "validationErrors": {
        "username": "Username must be 3-50 characters",
        "password": "Password must contain uppercase, lowercase, number, and special character"
    }
}
```

---

# Exercise 11: Write Unit Tests

## 📋 Requirements

**REQ-11.1**: Write unit tests for `TokenService`:
- Test token generation contains correct claims
- Test token parsing extracts correct data
- Test expired token throws exception
- Test invalid token throws exception
- Test extraction of user ID and roles

**REQ-11.2**: Write unit tests for `RefreshTokenService`:
- Test token creation and hashing
- Test token refresh flow
- Test token revocation
- Test revoke all user tokens

**REQ-11.3**: Write unit tests for `AuthService`:
- Test sign up success
- Test sign up duplicate username
- Test sign up duplicate email
- Test sign in success
- Test sign in wrong password
- Test sign in locked account
- Test sign out

**REQ-11.4**: Use Mockito to mock dependencies:
- Mock repositories
- Mock password encoder
- Mock token service

**REQ-11.5**: Follow AAA pattern:
- **Arrange**: Set up test data and mocks
- **Act**: Call the method being tested
- **Assert**: Verify the results

## 🎓 Concepts to Learn

| Concept | Description | Annotation/Class |
|---------|-------------|------------------|
| **@ExtendWith** | JUnit 5 extension | `MockitoExtension.class` |
| **@Mock** | Create mock object | Mockito |
| **@InjectMocks** | Inject mocks into class | Mockito |
| **when/thenReturn** | Stub mock behavior | Mockito |
| **verify** | Verify mock calls | Mockito |
| **assertThrows** | Expect exception | JUnit 5 |

## ✅ Verification

```bash
./gradlew :identity-auth:test
# All tests should pass
# Coverage > 80%
```

## 💡 Hints

<details>
<summary>Click to see test example</summary>

```java
package com.ecm.security.identity.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    private TokenService tokenService;
    
    @Mock
    private RefreshTokenService refreshTokenService;
    
    @InjectMocks
    private AuthService authService;
    
    @Test
    void signUp_Success() {
        // Arrange
        SignUpRequest request = new SignUpRequest(
            "testuser", "test@example.com", "Test123!@", "Test", "User", null
        );
        
        when(userRepository.existsByUsernameIgnoreCase("testuser")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Test123!@")).thenReturn("hashed-password");
        when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> {
            UserEntity user = inv.getArgument(0);
            user.setId(1L);
            return user;
        });
        
        // Act
        SignUpResponse response = authService.signUp(request);
        
        // Assert
        assertNotNull(response);
        assertEquals(1L, response.userId());
        assertTrue(response.verificationEmailSent());
        
        verify(userRepository).save(any(UserEntity.class));
    }
    
    @Test
    void signUp_DuplicateUsername_ThrowsException() {
        // Arrange
        SignUpRequest request = new SignUpRequest(
            "existinguser", "new@example.com", "Test123!@", null, null, null
        );
        
        when(userRepository.existsByUsernameIgnoreCase("existinguser")).thenReturn(true);
        
        // Act & Assert
        assertThrows(UsernameAlreadyExistsException.class, () -> {
            authService.signUp(request);
        });
        
        verify(userRepository, never()).save(any());
    }
    
    @Test
    void signIn_Success() {
        // Arrange
        SignInRequest request = new SignInRequest("testuser", "correctpassword", null);
        
        UserEntity userEntity = UserEntity.builder()
            .id(1L)
            .username("testuser")
            .passwordHash("hashed-password")
            .enabled(true)
            .accountLocked(false)
            .build();
        
        when(userRepository.findByUsernameOrEmail("testuser", "testuser"))
            .thenReturn(Optional.of(userEntity));
        when(passwordEncoder.matches("correctpassword", "hashed-password"))
            .thenReturn(true);
        when(tokenService.generateAccessToken(any())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(), any(), any()))
            .thenReturn("refresh-token");
        
        // Act
        AuthResponse response = authService.signIn(request);
        
        // Assert
        assertNotNull(response);
        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
    }
}
```

</details>

---

# Exercise 12: Integration Tests

## 📋 Requirements

**REQ-12.1**: Create integration tests using `@SpringBootTest`

**REQ-12.2**: Use Testcontainers for PostgreSQL

**REQ-12.3**: Test complete flows:
- Full signup → verify email → login → access protected resource → logout
- Login → refresh token → use new token
- Login → revoke session → token no longer works
- Forgot password → reset password → login with new password

**REQ-12.4**: Use `MockMvc` to test HTTP layer

**REQ-12.5**: Use `@Transactional` to rollback after each test

## 🎓 Concepts to Learn

| Concept | Description | Annotation |
|---------|-------------|------------|
| **@SpringBootTest** | Full app context | Test class |
| **@Testcontainers** | Docker containers for tests | Test class |
| **@AutoConfigureMockMvc** | Enable MockMvc | Test class |
| **MockMvc** | HTTP testing | Injected bean |
| **@Transactional** | Rollback after test | Test class/method |

---

# 🏆 Completion Checklist

## Phase 1: Foundation
- [ ] Project structure created
- [ ] Dependencies configured
- [ ] DTOs created with validation

## Phase 2: Data Layer
- [ ] Entities created with JPA annotations
- [ ] Repositories with custom queries
- [ ] Database migration (Liquibase)

## Phase 3: Business Logic
- [ ] TokenService (JWT)
- [ ] RefreshTokenService
- [ ] AuthService
- [ ] EmailVerificationService
- [ ] PasswordResetService
- [ ] SessionService

## Phase 4: API Layer
- [ ] AuthController with all endpoints
- [ ] JwtAuthenticationFilter
- [ ] SecurityConfig
- [ ] GlobalExceptionHandler

## Phase 5: Testing
- [ ] Unit tests (>80% coverage)
- [ ] Integration tests
- [ ] Manual API testing with Postman

## Phase 6: Polish
- [ ] Logging and audit trail
- [ ] API documentation (Swagger)
- [ ] Performance optimization

---

# 📖 Additional Learning Resources

## Spring Security Deep Dive
- [Baeldung Spring Security](https://www.baeldung.com/security-spring)
- [Spring Security Architecture](https://spring.io/guides/topicals/spring-security-architecture)

## JWT Best Practices
- [JWT.io Introduction](https://jwt.io/introduction)
- [OWASP JWT Security](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)

## Testing
- [Baeldung Testing Tutorial](https://www.baeldung.com/spring-boot-testing)
- [Testcontainers Guide](https://www.testcontainers.org/)

---

> **Good luck with your practice!** 🚀
> 
> Remember: The best way to learn is by doing. Don't just read the hints - try to implement each exercise yourself first!
