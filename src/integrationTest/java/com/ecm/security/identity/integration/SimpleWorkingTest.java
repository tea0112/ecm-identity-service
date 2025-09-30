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
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple working database connectivity test using @DataJpaTest with H2.
 * This test focuses on basic database connectivity and entity operations.
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false"
})
class SimpleWorkingTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Database connectivity works")
    void testDatabaseConnectivity() {
        // Test that repositories are available
        assertNotNull(userRepository);
        assertNotNull(tenantRepository);
        assertNotNull(entityManager);
    }

    @Test
    @DisplayName("Basic tenant operations work")
    void testBasicTenantOperations() {
        // Create a simple tenant
        Tenant tenant = Tenant.builder()
                .tenantCode("test-tenant")
                .name("Test Tenant")
                .domain("test.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        
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
        Tenant tenant = Tenant.builder()
                .tenantCode("test-tenant")
                .name("Test Tenant")
                .domain("test.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        
        Tenant savedTenant = tenantRepository.save(tenant);
        entityManager.flush();
        entityManager.clear();
        
        // Create a simple user
        User user = User.builder()
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .tenant(savedTenant)
                .status(User.UserStatus.ACTIVE)
                .build();
        
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
        Tenant tenant = Tenant.builder()
                .tenantCode("test-tenant")
                .name("Test Tenant")
                .domain("test.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        
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
