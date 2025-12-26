## **Auto-Setup Admin User Implementation Plan**

### **Overview**
Implement an automatic admin user creation system for the Spring Boot Identity Service that creates an initial admin user on application startup. This provides a secure, flexible solution for different environments with proper credential management.

### **Implementation Approach: Enhanced CommandLineRunner**

#### **1. Configuration Structure**
```properties
# Admin auto-setup configuration (add to each environment file)
app.admin.enabled=true
app.admin.username=admin
app.admin.password=${ADMIN_PASSWORD:Admin!23456}
app.admin.email=${ADMIN_EMAIL:admin@company.com}
app.admin.first-name=System
app.admin.last-name=Administrator
```

#### **2. Implementation Components**

**2.1 AdminInitializer Component**
- **Location**: `src/main/java/com/ecm/security/identity/config/AdminInitializer.java`
- **Interface**: Implements `CommandLineRunner`
- **Functionality**:
  - Runs after application context loads but before serving requests
  - Checks if admin user exists before creating (prevents duplicates)
  - Creates admin user with proper password encoding using Spring Security's `PasswordEncoder`
  - Assigns `ROLE_ADMIN` role to the admin user
  - Comprehensive logging for debugging and monitoring

**2.2 Repository Layer**
- **UserRepository**: `src/main/java/com/ecm/security/identity/repository/UserRepository.java`
  - Extends `JpaRepository<User, Long>`
  - Custom query methods for checking user existence by username
  - Methods for finding users by email and role

- **RoleRepository**: `src/main/java/com/ecm/security/identity/repository/RoleRepository.java`
  - Extends `JpaRepository<Role, Long>`
  - Methods for finding roles by name
  - Support for role creation if needed

**2.3 Configuration Properties**
- **AdminProperties**: `src/main/java/com/ecm/security/identity/config/AdminProperties.java`
  - Type-safe configuration using `@ConfigurationProperties`
  - Environment variable binding with defaults
  - Validation for required fields

**2.4 Entity Classes**
- **User Entity**: Maps to existing `sample_users` table
  - Fields: id, username, email, passwordHash, firstName, lastName, enabled, accountLocked, accountExpired, credentialsExpired, createdAt, updatedAt, createdBy, updatedBy
  - Proper JPA annotations and validation

- **Role Entity**: Maps to existing `sample_roles` table
  - Fields: id, name, description, createdAt, updatedAt, createdBy, updatedBy
  - Proper JPA annotations

- **UserRole Entity**: Maps to existing `sample_user_roles` table
  - Many-to-many relationship mapping
  - Composite primary key support

#### **3. Environment Configuration Updates**

**3.1 Development Environment** (`application-dev.properties`)
```properties
# Admin auto-setup configuration
app.admin.enabled=true
app.admin.username=admin
app.admin.password=${ADMIN_PASSWORD:Admin!23456}
app.admin.email=${ADMIN_EMAIL:admin@dev.company.com}
app.admin.first-name=System
app.admin.last-name=Administrator
```

**3.2 UAT Environment** (`application-uat.properties`)
```properties
# Admin auto-setup configuration
app.admin.enabled=true
app.admin.username=admin
app.admin.password=${ADMIN_PASSWORD:Admin!23456}
app.admin.email=${ADMIN_EMAIL:admin@uat.company.com}
app.admin.first-name=System
app.admin.last-name=Administrator
```

**3.3 Production Environment** (`application-prod.properties`)
```properties
# Admin auto-setup configuration
app.admin.enabled=true
app.admin.username=admin
app.admin.password=${ADMIN_PASSWORD:Admin!23456}
app.admin.email=${ADMIN_EMAIL:admin@company.com}
app.admin.first-name=System
app.admin.last-name=Administrator
```

**3.4 Local Environment** (`application-local.properties`)
```properties
# Admin auto-setup configuration
app.admin.enabled=true
app.admin.username=admin
app.admin.password=${ADMIN_PASSWORD:Admin!23456}
app.admin.email=${ADMIN_EMAIL:admin@localhost.com}
app.admin.first-name=System
app.admin.last-name=Administrator
```

#### **4. Key Features**

✅ **Security**: Uses environment variables, no hardcoded passwords  
✅ **Idempotent**: Won't duplicate users if they already exist  
✅ **Environment-aware**: Different configs per environment  
✅ **Robust**: Handles database errors gracefully  
✅ **Logged**: Clear startup messages for debugging  
✅ **Integrated**: Works with existing schema and Liquibase  

#### **5. Startup Flow**

1. **Application starts** → Spring Boot loads application context
2. **AdminInitializer runs** → After context loads but before serving requests
3. **Check admin exists** → Query database for admin user by username
4. **If not exists** → Create new admin user with encoded password
5. **Assign ROLE_ADMIN** → Create user-role relationship
6. **Log result** → Success/failure messages with clear information
7. **Continue startup** → Application proceeds with normal initialization

#### **6. Error Handling Strategy**

- **Database connection issues**: Log error but don't crash application startup
- **Role creation failures**: Log specific error and skip admin creation
- **Duplicate user attempts**: Gracefully handle and log that user already exists
- **Password encoding errors**: Log error and skip admin creation
- **Configuration validation**: Validate required fields before attempting creation

#### **7. Security Considerations**

- **Password hashing**: Always use Spring Security's `PasswordEncoder` (BCrypt)
- **Environment variables**: Support for secure credential management
- **Audit logging**: Log all admin creation attempts for security monitoring
- **Role validation**: Ensure `ROLE_ADMIN` exists before assigning
- **Input validation**: Validate all configuration parameters

#### **8. Testing Strategy**

- **Unit tests**: Test AdminInitializer logic in isolation
- **Integration tests**: Test with real database and Spring context
- **Environment tests**: Verify different configs work in each environment
- **Error scenario tests**: Test database failures, duplicate users, etc.
- **Security tests**: Verify password encoding and role assignment

#### **9. Dependencies Required**

- Spring Boot Starter Data JPA
- Spring Security (for PasswordEncoder)
- Lombok (for clean entity code)
- Proper logging configuration

#### **10. Implementation Order**

1. Create entity classes (User, Role, UserRole)
2. Create repository interfaces
3. Create configuration properties class
4. Create AdminInitializer component
5. Update environment configuration files
6. Add proper logging and error handling
7. Write tests for all components
8. Test across all environments

This implementation provides a robust, secure, and maintainable solution for automatic admin user creation that integrates seamlessly with the existing Spring Boot application architecture.

#### **11. Complete Code Specifications**

**11.1 User Entity Class**
```java
package com.ecm.security.identity.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "sample_users", indexes = {
    @Index(name = "idx_sample_users_username", columnList = "username"),
    @Index(name = "idx_sample_users_email", columnList = "email"),
    @Index(name = "idx_sample_users_enabled", columnList = "enabled"),
    @Index(name = "idx_sample_users_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = true;

    @Column(name = "account_locked", nullable = false)
    private Boolean accountLocked = false;

    @Column(name = "account_expired", nullable = false)
    private Boolean accountExpired = false;

    @Column(name = "credentials_expired", nullable = false)
    private Boolean credentialsExpired = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (createdBy == null) {
            createdBy = "system";
        }
        if (updatedBy == null) {
            updatedBy = "system";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (updatedBy == null) {
            updatedBy = "system";
        }
    }
}
```

**11.2 Role Entity Class**
```java
package com.ecm.security.identity.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "sample_roles", indexes = {
    @Index(name = "idx_sample_roles_name", columnList = "name")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 50)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (createdBy == null) {
            createdBy = "system";
        }
        if (updatedBy == null) {
            updatedBy = "system";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (updatedBy == null) {
            updatedBy = "system";
        }
    }
}
```

**11.3 UserRole Entity Class**
```java
package com.ecm.security.identity.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "sample_user_roles", indexes = {
    @Index(name = "idx_sample_user_roles_user_id", columnList = "user_id"),
    @Index(name = "idx_sample_user_roles_role_id", columnList = "role_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {
    @EmbeddedId
    private UserRoleId id;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "granted_by", length = 100)
    private String grantedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roleId")
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @PrePersist
    protected void onCreate() {
        grantedAt = LocalDateTime.now();
        if (grantedBy == null) {
            grantedBy = "system";
        }
    }
}

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class UserRoleId implements Serializable {
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "role_id")
    private Long roleId;
}
```

**11.4 UserRepository Interface**
```java
package com.ecm.security.identity.repository;

import com.ecm.security.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    
    @Query("SELECT u FROM User u JOIN FETCH u.userRoles ur JOIN FETCH ur.role WHERE u.username = :username")
    Optional<User> findByUsernameWithRoles(@Param("username") String username);
}
```

**11.5 RoleRepository Interface**
```java
package com.ecm.security.identity.repository;

import com.ecm.security.identity.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByName(String name);
    
    @Query("SELECT r FROM Role r WHERE r.name = :name")
    Optional<Role> findByNameWithDetails(@Param("name") String name);
}
```

**11.6 AdminProperties Configuration**
```java
package com.ecm.security.identity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "app.admin")
@Validated
public record AdminProperties(
    @NotNull boolean enabled,
    @NotBlank String username,
    @NotBlank String password,
    @Email String email,
    @NotBlank String firstName,
    @NotBlank String lastName
) {
    public AdminProperties {
        if (!enabled) {
            return;
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Admin username cannot be null or empty");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Admin password cannot be null or empty");
        }
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Admin email cannot be null or empty");
        }
    }
}
```

**11.7 AdminInitializer Component**
```java
package com.ecm.security.identity.config;

import com.ecm.security.identity.entity.Role;
import com.ecm.security.identity.entity.User;
import com.ecm.security.identity.entity.UserRole;
import com.ecm.security.identity.repository.RoleRepository;
import com.ecm.security.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminInitializer implements CommandLineRunner {

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (!adminProperties.enabled()) {
            log.info("Admin user auto-creation is disabled");
            return;
        }

        try {
            log.info("Checking for existing admin user: {}", adminProperties.username());
            
            if (userRepository.existsByUsername(adminProperties.username())) {
                log.info("Admin user '{}' already exists, skipping creation", adminProperties.username());
                return;
            }

            // Ensure ROLE_ADMIN exists
            Role adminRole = roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> createAdminRole());

            // Create admin user
            User adminUser = User.builder()
                .username(adminProperties.username())
                .email(adminProperties.email())
                .passwordHash(passwordEncoder.encode(adminProperties.password()))
                .firstName(adminProperties.firstName())
                .lastName(adminProperties.lastName())
                .enabled(true)
                .accountLocked(false)
                .accountExpired(false)
                .credentialsExpired(false)
                .createdBy("system")
                .updatedBy("system")
                .build();

            User savedUser = userRepository.save(adminUser);
            
            // Assign ROLE_ADMIN
            UserRole userRole = UserRole.builder()
                .id(new UserRoleId(savedUser.getId(), adminRole.getId()))
                .grantedBy("system")
                .user(savedUser)
                .role(adminRole)
                .build();

            // Save user-role relationship (handled by cascade)
            savedUser.getUserRoles().add(userRole);
            userRepository.save(savedUser);

            log.info("Successfully created admin user: {} with email: {}", 
                adminProperties.username(), adminProperties.email());
            log.info("Admin user created with ROLE_ADMIN role");

        } catch (Exception e) {
            log.error("Failed to create admin user: {}", e.getMessage(), e);
            log.warn("Admin user creation failed but application startup will continue");
        }
    }

    private Role createAdminRole() {
        log.info("Creating ROLE_ADMIN role");
        Role adminRole = Role.builder()
            .name("ROLE_ADMIN")
            .description("Administrator with full access")
            .createdBy("system")
            .updatedBy("system")
            .build();
        
        return roleRepository.save(adminRole);
    }
}
```

**11.8 Required Dependencies**
```gradle
// Add to build.gradle
dependencies {
    // Spring Security for PasswordEncoder
    implementation 'org.springframework.boot:spring-boot-starter-security'
    
    // Lombok for clean code
    compileOnly 'org.projectlombok:lombok'
    annotationProcessor 'org.projectlombok:lombok'
    
    // Validation
    implementation 'org.springframework.boot:spring-boot-starter-validation'
}
```

#### **12. Testing Specifications**

**12.1 Unit Tests**
```java
package com.ecm.security.identity.config;

import com.ecm.security.identity.entity.Role;
import com.ecm.security.identity.entity.User;
import com.ecm.security.identity.repository.RoleRepository;
import com.ecm.security.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

    @Mock
    private AdminProperties adminProperties;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminInitializer adminInitializer;

    @Test
    void shouldNotCreateAdminWhenDisabled() {
        when(adminProperties.enabled()).thenReturn(false);
        
        adminInitializer.run();
        
        verify(userRepository, never()).existsByUsername(any());
    }

    @Test
    void shouldNotCreateAdminWhenAlreadyExists() {
        when(adminProperties.enabled()).thenReturn(true);
        when(adminProperties.username()).thenReturn("admin");
        when(userRepository.existsByUsername("admin")).thenReturn(true);
        
        adminInitializer.run();
        
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldCreateAdminUserWhenNotExists() {
        when(adminProperties.enabled()).thenReturn(true);
        when(adminProperties.username()).thenReturn("admin");
        when(adminProperties.email()).thenReturn("admin@company.com");
        when(adminProperties.password()).thenReturn("Admin!23456");
        when(adminProperties.firstName()).thenReturn("System");
        when(adminProperties.lastName()).thenReturn("Administrator");
        
        when(userRepository.existsByUsername("admin")).thenReturn(false);
        when(roleRepository.findByName("ROLE_ADMIN")).thenReturn(Optional.of(createAdminRole()));
        when(passwordEncoder.encode("Admin!23456")).thenReturn("$2a$10$...");
        
        adminInitializer.run();
        
        verify(userRepository).save(any(User.class));
    }

    private Role createAdminRole() {
        return Role.builder()
            .id(1L)
            .name("ROLE_ADMIN")
            .description("Administrator with full access")
            .build();
    }
}
```

**12.2 Integration Tests**
```java
package com.ecm.security.identity.config;

import com.ecm.security.identity.entity.User;
import com.ecm.security.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class AdminInitializerIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldCreateAdminUserOnStartup() {
        User admin = userRepository.findByUsername("admin")
            .orElseThrow(() -> new AssertionError("Admin user should exist"));
        
        assertNotNull(admin);
        assertEquals("admin", admin.getUsername());
        assertEquals("admin@company.com", admin.getEmail());
        assertTrue(admin.getEnabled());
        assertFalse(admin.getAccountLocked());
    }
}
```

#### **13. Environment-Specific Configuration**

**13.1 Development Environment**
```properties
# application-dev.properties
app.admin.enabled=true
app.admin.username=admin
app.admin.password=${ADMIN_PASSWORD:Admin!23456}
app.admin.email=${ADMIN_EMAIL:admin@dev.company.com}
app.admin.first-name=System
app.admin.last-name=Administrator

# Database and other dev settings remain unchanged
```

**13.2 Production Environment**
```properties
# application-prod.properties
app.admin.enabled=true
app.admin.username=admin
app.admin.password=${ADMIN_PASSWORD:Admin!23456}
app.admin.email=${ADMIN_EMAIL:admin@company.com}
app.admin.first-name=System
app.admin.last-name=Administrator

# Production-specific settings
logging.level.com.ecm.security.identity.config.AdminInitializer=WARN
```

**13.3 Environment Variable Examples**
```bash
# Development
export ADMIN_PASSWORD=DevAdmin!23456
export ADMIN_EMAIL=admin@dev.company.com

# Production
export ADMIN_PASSWORD=ProdAdmin!23456
export ADMIN_EMAIL=admin@company.com
```

#### **14. Security Best Practices**

1. **Never commit passwords to version control**
2. **Use strong, unique passwords per environment**
3. **Rotate admin passwords regularly**
4. **Monitor admin user creation in logs**
5. **Restrict access to environment variables**
6. **Use HSM/KMS for production password storage**
7. **Implement audit logging for admin activities**

#### **15. Troubleshooting Guide**

**Common Issues:**

1. **Admin user not created:**
   - Check `app.admin.enabled=true` in environment config
   - Verify database connection and schema
   - Check logs for error messages

2. **Duplicate key errors:**
   - Ensure unique username and email
   - Check if admin user already exists

3. **Password encoding issues:**
   - Verify Spring Security dependency
   - Check PasswordEncoder bean configuration

4. **Role assignment failures:**
   - Ensure ROLE_ADMIN exists in roles table
   - Check foreign key constraints

**Log Analysis:**
- Look for "Admin user auto-creation is disabled" messages
- Check for "Admin user already exists" messages
- Monitor for any exceptions during startup

This implementation provides a robust, secure, and maintainable solution for automatic admin user creation that integrates seamlessly with the existing Spring Boot application architecture.
