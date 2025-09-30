package com.ecm.security.identity.integration;

import com.ecm.security.identity.domain.Tenant;
import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.repository.TenantRepository;
import com.ecm.security.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixed database connectivity test using @DataJpaTest with Testcontainers.
 * This test focuses on basic database connectivity and entity operations.
 */
@DataJpaTest
@Testcontainers
class FixedDatabaseTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("ecm_identity_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Database connectivity works with Testcontainers")
    void testDatabaseConnectivity() {
        // Test that repositories are available
        assertNotNull(userRepository);
        assertNotNull(tenantRepository);
        assertNotNull(entityManager);
    }

    @Test
    @DisplayName("Basic tenant operations work")
    void testBasicTenantOperations() {
        // Create a simple tenant without complex relationships
        Tenant tenant = new Tenant();
        tenant.setTenantCode("test-tenant");
        tenant.setName("Test Tenant");
        tenant.setDomain("test.example.com");
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        
        // Save the tenant
        Tenant savedTenant = tenantRepository.save(tenant);
        entityManager.flush();
        entityManager.clear();
        
        // Verify the tenant was saved
        assertNotNull(savedTenant.getId());
        assertEquals("test-tenant", savedTenant.getTenantCode());
        assertEquals("Test Tenant", savedTenant.getName());
        
        // Test retrieval
        Optional<Tenant> retrievedTenant = tenantRepository.findById(savedTenant.getId());
        assertTrue(retrievedTenant.isPresent());
        assertEquals("test-tenant", retrievedTenant.get().getTenantCode());
    }

    @Test
    @DisplayName("Basic user operations work")
    void testBasicUserOperations() {
        // First create a tenant
        Tenant tenant = new Tenant();
        tenant.setTenantCode("test-tenant");
        tenant.setName("Test Tenant");
        tenant.setDomain("test.example.com");
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        
        Tenant savedTenant = tenantRepository.save(tenant);
        entityManager.flush();
        entityManager.clear();
        
        // Create a simple user
        User user = new User();
        user.setEmail("test@example.com");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setTenant(savedTenant);
        user.setStatus(User.UserStatus.ACTIVE);
        
        // Save the user
        User savedUser = userRepository.save(user);
        entityManager.flush();
        entityManager.clear();
        
        // Verify the user was saved
        assertNotNull(savedUser.getId());
        assertEquals("test@example.com", savedUser.getEmail());
        assertEquals("Test", savedUser.getFirstName());
        assertEquals("User", savedUser.getLastName());
        
        // Test retrieval
        Optional<User> retrievedUser = userRepository.findById(savedUser.getId());
        assertTrue(retrievedUser.isPresent());
        assertEquals("test@example.com", retrievedUser.get().getEmail());
    }

    @Test
    @DisplayName("Repository query methods work")
    void testRepositoryQueryMethods() {
        // Create a tenant
        Tenant tenant = new Tenant();
        tenant.setTenantCode("test-tenant");
        tenant.setName("Test Tenant");
        tenant.setDomain("test.example.com");
        tenant.setStatus(Tenant.TenantStatus.ACTIVE);
        
        Tenant savedTenant = tenantRepository.save(tenant);
        entityManager.flush();
        entityManager.clear();
        
        // Test tenant repository methods
        assertTrue(tenantRepository.existsByTenantCodeAndDeletedAtIsNull("test-tenant"));
        assertFalse(tenantRepository.existsByTenantCodeAndDeletedAtIsNull("non-existent-tenant"));
        
        // Test tenant retrieval by code
        Optional<Tenant> foundTenant = tenantRepository.findByTenantCodeAndStatusNot(
            "test-tenant", Tenant.TenantStatus.ARCHIVED);
        assertTrue(foundTenant.isPresent());
        assertEquals("test-tenant", foundTenant.get().getTenantCode());
    }
}
