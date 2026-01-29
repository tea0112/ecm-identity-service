# PLAN: SpringDoc OpenAPI Integration for ECM Identity Service

> **Created:** 2026-01-28  
> **Updated:** 2026-01-29  
> **Status:** ✅ IMPLEMENTED  
> **Project Type:** BACKEND (Java Spring Boot 3.5)

---

## Overview

Integrate **SpringDoc OpenAPI 2.8.x** (latest cutting-edge) into `ecm-identity-service` to provide comprehensive API documentation with:
- **Dual UI Support:** Swagger UI (classic) + Scalar UI (modern 2025)
- **Full OAuth2 Security Documentation:** Authorization/Token URLs, Bearer token, scopes
- **Module-based API Grouping:** Separate docs per module (users, roles, auth)
- **OpenAPI 3.1 Specification:** Latest standard with enhanced JSON Schema support

---

## Success Criteria

| Criteria | Verification |
|----------|--------------|
| Swagger UI accessible at `/swagger-ui.html` | Browser test returns 200 |
| Scalar UI accessible at `/scalar.html` | Browser test returns 200 |
| OpenAPI spec available at `/v3/api-docs` | JSON response with valid OpenAPI 3.1 |
| Module groups working | Dropdown shows: users, roles, auth |
| OAuth2 security documented | `securitySchemes` contains Bearer + OAuth2 |
| All existing endpoints documented | UserController endpoints visible |
| Build passes | `./gradlew build` succeeds |

---

## Tech Stack

| Component | Choice | Rationale |
|-----------|--------|-----------|
| **OpenAPI Library** | SpringDoc OpenAPI 2.8.15 | Latest stable, Spring Boot 3.5 compatible, OpenAPI 3.1 |
| **Swagger UI** | Built-in (springdoc-openapi-starter-webmvc-ui) | Classic, well-supported |
| **Scalar UI** | springdoc-openapi-starter-webmvc-api + Scalar CDN | Modern 2025 interface |
| **Annotations** | OpenAPI 3 (`io.swagger.v3.oas.annotations`) | Standard, IDE support |

---

## File Structure

```
ecm-identity-service/
├── identity-app/
│   ├── build.gradle                          # [MODIFY] Add SpringDoc dependencies
│   └── src/main/
│       ├── java/com/ecm/security/identity/
│       │   └── config/
│       │       ├── OpenApiConfig.java        # [CREATE] Main OpenAPI configuration
│       │       └── ScalarConfig.java         # [CREATE] Scalar UI endpoint
│       └── resources/
│           └── application.properties        # [MODIFY] Add springdoc properties
│
├── identity-user/
│   └── src/main/java/.../controller/
│       └── UserController.java               # [MODIFY] Add OpenAPI annotations
│
├── identity-role/
│   └── src/main/java/.../controller/
│       └── RoleController.java               # [MODIFY] Add OpenAPI annotations (if exists)
│
└── docs/
    └── PLAN-swagger-openapi.md               # This plan file
```

---

## Task Breakdown

### Phase 1: Foundation Setup

#### Task 1.1: Add SpringDoc Dependencies
**Agent:** `backend-specialist`  
**Skill:** `clean-code`  
**Priority:** P0 (Blocker)  
**Dependencies:** None

| INPUT | OUTPUT | VERIFY |
|-------|--------|--------|
| `identity-app/build.gradle` | Updated with SpringDoc 2.8.15 | `./gradlew dependencies` shows springdoc |

**Implementation:**
```groovy
// Add to identity-app/build.gradle dependencies block
implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.15'
```

---

#### Task 1.2: Create OpenAPI Configuration Class
**Agent:** `backend-specialist`  
**Skill:** `api-patterns`  
**Priority:** P0 (Blocker)  
**Dependencies:** Task 1.1

| INPUT | OUTPUT | VERIFY |
|-------|--------|--------|
| Project requirements | `OpenApiConfig.java` | Application starts without errors |

**Implementation Details:**
- Configure API metadata (title, version, description, contact, license)
- Define OAuth2 security scheme with full flow
- Define Bearer token scheme
- Configure module-based grouping with `@GroupedOpenApi`
- Set up server URLs (dev, staging, prod)

**File:** `identity-app/src/main/java/com/ecm/security/identity/config/OpenApiConfig.java`

```java
@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("ECM Identity Service API")
                .version("1.0.0")
                .description("Enterprise Content Management - Identity & Access Management Service")
                .contact(new Contact().name("ECM Team").email("ecm@company.com"))
                .license(new License().name("Proprietary")))
            .components(new Components()
                .addSecuritySchemes("bearer-jwt", bearerScheme())
                .addSecuritySchemes("oauth2", oauth2Scheme()));
    }
    
    @Bean
    public GroupedOpenApi usersApi() {
        return GroupedOpenApi.builder()
            .group("users")
            .displayName("User Management")
            .pathsToMatch("/api/users/**")
            .build();
    }
    
    @Bean
    public GroupedOpenApi rolesApi() {
        return GroupedOpenApi.builder()
            .group("roles")
            .displayName("Role Management")
            .pathsToMatch("/api/roles/**")
            .build();
    }
    
    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
            .group("auth")
            .displayName("Authentication")
            .pathsToMatch("/api/auth/**")
            .build();
    }
}
```

---

#### Task 1.3: Create Scalar UI Configuration
**Agent:** `backend-specialist`  
**Skill:** `api-patterns`  
**Priority:** P1  
**Dependencies:** Task 1.2

| INPUT | OUTPUT | VERIFY |
|-------|--------|--------|
| OpenAPI config | `ScalarConfig.java` | `/scalar.html` returns modern UI |

**Implementation Details:**
- Create controller for Scalar UI HTML page
- Use Scalar CDN for modern UI
- Link to existing OpenAPI spec

**File:** `identity-app/src/main/java/com/ecm/security/identity/config/ScalarConfig.java`

---

#### Task 1.4: Configure Application Properties
**Agent:** `backend-specialist`  
**Skill:** `clean-code`  
**Priority:** P1  
**Dependencies:** Task 1.1

| INPUT | OUTPUT | VERIFY |
|-------|--------|--------|
| Requirements | Updated `application.properties` | Swagger UI loads at correct path |

**Properties to add:**
```properties
# SpringDoc OpenAPI Configuration
springdoc.api-docs.enabled=true
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.enabled=true
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.operationsSorter=method
springdoc.swagger-ui.tagsSorter=alpha
springdoc.swagger-ui.doc-expansion=none
springdoc.swagger-ui.filter=true
springdoc.swagger-ui.show-extensions=true
springdoc.swagger-ui.show-common-extensions=true

# OAuth2 Configuration for Swagger UI
springdoc.swagger-ui.oauth.client-id=${OAUTH2_CLIENT_ID:swagger-ui}
springdoc.swagger-ui.oauth.use-pkce-with-authorization-code-grant=true

# Default group
springdoc.default-group=users
```

---

### Phase 2: Controller Annotations

#### Task 2.1: Annotate UserController
**Agent:** `backend-specialist`  
**Skill:** `api-patterns`  
**Priority:** P1  
**Dependencies:** Task 1.2

| INPUT | OUTPUT | VERIFY |
|-------|--------|--------|
| `UserController.java` | Annotated controller | Swagger UI shows all endpoints with descriptions |

**Annotations to add:**
- `@Tag(name = "Users", description = "User management operations")`
- `@Operation(summary, description, responses)` for each method
- `@Parameter` for path/query parameters
- `@ApiResponse` for success/error responses
- `@SecurityRequirement(name = "bearer-jwt")` for protected endpoints

**Example:**
```java
@Tag(name = "Users", description = "User management operations")
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Operation(
        summary = "List all users",
        description = "Returns a paginated list of all users. Requires ADMIN role.",
        security = @SecurityRequirement(name = "bearer-jwt")
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Users retrieved successfully"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    @GetMapping
    public List<UserResponse> listUsers() { ... }
}
```

---

#### Task 2.2: Annotate DTO Classes
**Agent:** `backend-specialist`  
**Skill:** `api-patterns`  
**Priority:** P2  
**Dependencies:** Task 2.1

| INPUT | OUTPUT | VERIFY |
|-------|--------|--------|
| Request/Response DTOs | Annotated DTOs | Schema section shows field descriptions |

**DTOs to annotate:**
- `CreateUserRequest`
- `UpdateUserRequest`
- `ChangePasswordRequest`
- `RoleChangeRequest`
- `UserResponse`

**Example:**
```java
@Schema(description = "Request to create a new user")
public class CreateUserRequest {
    
    @Schema(description = "Unique username", example = "john.doe", required = true)
    @NotBlank
    private String username;
    
    @Schema(description = "User email address", example = "john@example.com", required = true)
    @Email
    private String email;
}
```

---

#### Task 2.3: Annotate RoleController (if exists)
**Agent:** `backend-specialist`  
**Skill:** `api-patterns`  
**Priority:** P2  
**Dependencies:** Task 2.1

| INPUT | OUTPUT | VERIFY |
|-------|--------|--------|
| `RoleController.java` | Annotated controller | Roles group visible in Swagger |

Same pattern as Task 2.1.

---

### Phase 3: Security Configuration

#### Task 3.1: Configure Security Filter for Doc Endpoints
**Agent:** `backend-specialist` + `security-auditor`  
**Skill:** `vulnerability-scanner`  
**Priority:** P1  
**Dependencies:** Task 1.2

| INPUT | OUTPUT | VERIFY |
|-------|--------|--------|
| Security config | Updated security filter | Swagger accessible without auth |

**Paths to permit:**
```java
.requestMatchers(
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/v3/api-docs/**",
    "/scalar.html",
    "/webjars/**"
).permitAll()
```

**Environment consideration:**
- DEV/LOCAL: Permit all doc endpoints
- STAGING: Permit with basic auth
- PRODUCTION: Disable or secure behind VPN

---

#### Task 3.2: Document OAuth2 Endpoints
**Agent:** `backend-specialist`  
**Skill:** `api-patterns`  
**Priority:** P2  
**Dependencies:** Task 3.1

| INPUT | OUTPUT | VERIFY |
|-------|--------|--------|
| OAuth2 flow requirements | Complete OAuth2 security scheme | "Authorize" button works in Swagger UI |

**OAuth2 Configuration:**
```java
private SecurityScheme oauth2Scheme() {
    return new SecurityScheme()
        .type(SecurityScheme.Type.OAUTH2)
        .description("OAuth2 Authentication")
        .flows(new OAuthFlows()
            .authorizationCode(new OAuthFlow()
                .authorizationUrl("/api/auth/authorize")
                .tokenUrl("/api/auth/token")
                .refreshUrl("/api/auth/refresh")
                .scopes(new Scopes()
                    .addString("read", "Read access")
                    .addString("write", "Write access")
                    .addString("admin", "Admin access"))));
}
```

---

### Phase 4: Testing & Validation

#### Task 4.1: Integration Test for OpenAPI Spec
**Agent:** `backend-specialist`  
**Skill:** `testing-patterns`  
**Priority:** P2  
**Dependencies:** Phase 1, 2, 3

| INPUT | OUTPUT | VERIFY |
|-------|--------|--------|
| OpenAPI config | `OpenApiIntegrationTest.java` | Test passes |

**Test cases:**
- `/v3/api-docs` returns valid JSON
- Schema contains all expected endpoints
- Security schemes are defined
- Groups are correctly configured

---

#### Task 4.2: Manual UI Verification
**Agent:** Manual  
**Priority:** P2  
**Dependencies:** Task 4.1

| Verification | Expected |
|--------------|----------|
| Open `/swagger-ui.html` | Classic Swagger UI loads |
| Open `/scalar.html` | Modern Scalar UI loads |
| Select "users" group | User endpoints displayed |
| Try "Authorize" button | OAuth2 dialog appears |
| Try an endpoint | Request/response works |

---

### Phase X: Final Verification

#### Checklist:

- [x] **Build:** `./gradlew build` passes
- [x] **Swagger UI:** Accessible at `/swagger-ui.html`
- [x] **Scalar UI:** Accessible at `/scalar.html`
- [x] **OpenAPI Spec:** Valid at `/v3/api-docs`
- [x] **Module Groups:** users, roles, auth, system, all groups configured
- [x] **Security:** OAuth2 + Bearer schemes documented
- [x] **Endpoints:** All existing endpoints annotated
- [x] **DTOs:** All request/response schemas documented
- [x] **Security Filter:** Doc endpoints accessible in dev
- [ ] **Production Safety:** Doc endpoints secured/disabled in prod (configure via env vars)

#### Verification Commands:

```bash
# Build verification
./gradlew clean build

# Start application
./gradlew :identity-app:bootRun

# Test endpoints
curl http://localhost:8080/v3/api-docs | jq '.info.title'
curl http://localhost:8080/v3/api-docs/users | jq '.paths | keys'
curl -I http://localhost:8080/swagger-ui.html
curl -I http://localhost:8080/scalar.html
```

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| Security misconfiguration | HIGH | Use environment-based doc access |
| Version incompatibility | MEDIUM | Pin SpringDoc to 2.8.15 |
| Performance overhead | LOW | Lazy initialization, cache specs |
| Auth module not ready | LOW | Create placeholder group |

---

## Dependencies Graph

```
Task 1.1 (Dependencies)
    ↓
Task 1.2 (OpenAPI Config) ──→ Task 1.3 (Scalar Config)
    ↓                              ↓
Task 1.4 (Properties)         Task 3.1 (Security Filter)
    ↓                              ↓
Task 2.1 (UserController) ──→ Task 2.2 (DTOs)
    ↓                              ↓
Task 2.3 (RoleController)     Task 3.2 (OAuth2 Doc)
    ↓                              ↓
    └──────────────────────────────┘
                    ↓
            Task 4.1 (Integration Test)
                    ↓
            Task 4.2 (Manual Verification)
                    ↓
              Phase X (Final)
```

---

## Estimated Timeline

| Phase | Duration | Tasks |
|-------|----------|-------|
| Phase 1 | 45 min | Foundation (4 tasks) |
| Phase 2 | 30 min | Annotations (3 tasks) |
| Phase 3 | 20 min | Security (2 tasks) |
| Phase 4 | 15 min | Testing (2 tasks) |
| **Total** | **~2 hours** | |

---

## Next Steps

After plan approval:
1. Run `/create` or start implementation
2. Begin with Task 1.1 (Add Dependencies)
3. Follow task order per dependency graph

---

> **Plan Status:** ⏳ AWAITING APPROVAL
