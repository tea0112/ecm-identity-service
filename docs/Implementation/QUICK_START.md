# Quick Start: Domain-Entity Pattern Implementation

## Implementation Checklist

Follow this checklist in order:

### Phase 1: Foundation (Start Here)

- [ ] **1.1** Create `entity/RoleEntity.java`
  - Map to `roles` table
  - Add JPA annotations
  - Add lifecycle callbacks (`@PrePersist`, `@PreUpdate`)

- [ ] **1.2** Create `domain/Role.java`
  - No JPA annotations
  - Simple domain model
  - Use Lombok annotations

- [ ] **1.3** Create `mapper/RoleMapper.java`
  - `toDomain(RoleEntity)` method
  - `toEntity(Role)` method
  - Null safety checks

- [ ] **1.4** Create `repository/RoleRepository.java`
  - Extend `JpaRepository<RoleEntity, UUID>`
  - Add `findByName()` method
  - Add `existsByName()` method

- [ ] **1.5** Test Phase 1
  - Verify RoleEntity maps to database
  - Test RoleMapper conversions
  - Test RoleRepository queries

### Phase 2: User Implementation

- [ ] **2.1** Create `entity/UserEntity.java`
  - Map to `users` table
  - Add `@ManyToMany` relationship to `RoleEntity`
  - Add all fields from database schema

- [ ] **2.2** Create `domain/User.java`
  - Implement `UserDetails` interface
  - Add business logic methods (`getAuthorities()`, etc.)
  - Include `Set<Role>` relationship

- [ ] **2.3** Create `mapper/UserMapper.java`
  - Inject `RoleMapper` dependency
  - Handle `Set<RoleEntity>` ↔ `Set<Role>` conversion
  - Map all fields

- [ ] **2.4** Create `repository/UserRepository.java`
  - Extend `JpaRepository<UserEntity, UUID>`
  - Add `findByUsername()`, `findByEmail()`
  - Add `findByUsernameWithRoles()` with `JOIN FETCH`

- [ ] **2.5** Test Phase 2
  - Verify UserEntity maps correctly
  - Test UserMapper with relationships
  - Test UserRepository queries

### Phase 3: Business Logic

- [ ] **3.1** Create `dto/UserResponse.java`
  - Fields for API response
  - Exclude sensitive data (passwordHash)

- [ ] **3.2** Create `service/RoleService.java`
  - Inject `RoleRepository` and `RoleMapper`
  - Add `getAllRoles()` method
  - Add `createRole()` method
  - Add `@Transactional` and `@PreAuthorize`

- [ ] **3.3** Create `service/UserService.java`
  - Inject `UserRepository`, `RoleRepository`, `UserMapper`, `RoleMapper`
  - Add `getAllUsers()` method
  - Add `getUserByUsername()` method
  - Add `createUser()` method
  - Add `assignRoleToUser()` method
  - Add `removeRoleFromUser()` method
  - Convert Domain → DTO in service

- [ ] **3.4** Test Phase 3
  - Test RoleService methods
  - Test UserService methods
  - Verify transactions work

### Phase 4: Security Integration

- [ ] **4.1** Create `security/CustomUserDetailsService.java`
  - Implement `UserDetailsService`
  - Use `UserRepository.findByUsernameWithRoles()`
  - Use `UserMapper.toDomain()`
  - Return `User` domain object (implements `UserDetails`)

- [ ] **4.2** Test Security
  - Verify authentication works
  - Test role-based authorization

### Build Helper: Static Metamodel

- Run `./gradlew clean compileJava` to generate Criteria API metamodels under `build/generated/sources/annotationProcessor/java/main`.
- Use `UserEntity_`, `RoleEntity_`, etc., in Criteria queries to avoid string field names.

### Phase 5: API Layer

- [ ] **5.1** Create `controller/AdminController.java`
  - Add `@RestController` and `@RequestMapping("/api/admin")`
  - Inject `UserService` and `RoleService`
  - Create endpoints:
    - `GET /api/admin/users`
    - `GET /api/admin/users/{username}`
    - `POST /api/admin/users`
    - `POST /api/admin/users/{username}/roles`
    - `DELETE /api/admin/users/{username}/roles/{roleName}`
    - `GET /api/admin/roles`
    - `POST /api/admin/roles`

- [ ] **5.2** Test API
  - Test all endpoints
  - Verify security works
  - Test error handling

## Code Templates

### Entity Template

```java
package com.ecm.security.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "table_name")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntityName {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Add fields matching database columns
    @Column(name = "column_name", nullable = false)
    private String fieldName;
    
    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

### Domain Template

```java
package com.ecm.security.identity.domain;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainName {
    private Long id;
    private String fieldName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Add business logic methods here
}
```

### Mapper Template

```java
package com.ecm.security.identity.mapper;

import com.ecm.security.identity.domain.DomainName;
import com.ecm.security.identity.entity.EntityName;
import org.springframework.stereotype.Component;

@Component
public class DomainNameMapper {
    
    public DomainName toDomain(EntityName entity) {
        if (entity == null) {
            return null;
        }
        
        return DomainName.builder()
            .id(entity.getId())
            .fieldName(entity.getFieldName())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
    
    public EntityName toEntity(DomainName domain) {
        if (domain == null) {
            return null;
        }
        
        return EntityName.builder()
            .id(domain.getId())
            .fieldName(domain.getFieldName())
            .createdAt(domain.getCreatedAt())
            .updatedAt(domain.getUpdatedAt())
            .build();
    }
}
```

### Repository Template

```java
package com.ecm.security.identity.repository;

import com.ecm.security.identity.entity.EntityName;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface EntityNameRepository extends JpaRepository<EntityName, Long> {
    Optional<EntityName> findByFieldName(String fieldName);
    boolean existsByFieldName(String fieldName);
}
```

### Service Template

```java
package com.ecm.security.identity.service;

import com.ecm.security.identity.domain.DomainName;
import com.ecm.security.identity.entity.EntityName;
import com.ecm.security.identity.mapper.DomainNameMapper;
import com.ecm.security.identity.repository.EntityNameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DomainNameService {
    
    private final EntityNameRepository repository;
    private final DomainNameMapper mapper;
    
    @PreAuthorize("hasRole('ADMIN')")
    public List<DomainName> getAll() {
        return repository.findAll().stream()
            .map(mapper::toDomain)
            .collect(Collectors.toList());
    }
    
    public DomainName create(DomainName domain) {
        EntityName entity = mapper.toEntity(domain);
        EntityName saved = repository.save(entity);
        return mapper.toDomain(saved);
    }
}
```

## Common Mistakes to Avoid

1. **❌ Using Entity in Service directly**
   ```java
   // WRONG
   public UserEntity getUser(String username) {
       return userRepository.findByUsername(username);
   }
   ```

   ```java
   // CORRECT
   public User getUser(String username) {
       return userRepository.findByUsername(username)
           .map(userMapper::toDomain)
           .orElse(null);
   }
   ```

2. **❌ Adding JPA annotations to Domain**
   ```java
   // WRONG
   @Entity
   public class User {
       // ...
   }
   ```

   ```java
   // CORRECT
   public class User {
       // No JPA annotations
   }
   ```

3. **❌ Forgetting null checks in Mappers**
   ```java
   // WRONG
   public User toDomain(UserEntity entity) {
       return User.builder()
           .id(entity.getId())  // NPE if entity is null!
           .build();
   }
   ```

   ```java
   // CORRECT
   public User toDomain(UserEntity entity) {
       if (entity == null) {
           return null;
       }
       return User.builder()
           .id(entity.getId())
           .build();
   }
   ```

4. **❌ Not handling relationships in Mappers**
   ```java
   // WRONG - roles will be null
   public User toDomain(UserEntity entity) {
       return User.builder()
           .roles(entity.getRoles())  // Type mismatch!
           .build();
   }
   ```

   ```java
   // CORRECT
   public User toDomain(UserEntity entity) {
       Set<Role> roles = entity.getRoles().stream()
           .map(roleMapper::toDomain)
           .collect(Collectors.toSet());
       return User.builder()
           .roles(roles)
           .build();
   }
   ```

## Testing Checklist

After each phase, verify:

- [ ] Code compiles without errors
- [ ] No circular dependencies
- [ ] Mappers handle null correctly
- [ ] Relationships are converted properly
- [ ] Services use domain models (not entities)
- [ ] Repositories work with entities
- [ ] Security annotations work
- [ ] Transactions work correctly

## Next Steps

Once all phases are complete:

1. Add proper exception handling
2. Add validation
3. Add logging
4. Write unit tests
5. Write integration tests
6. Add API documentation (Swagger/OpenAPI)
