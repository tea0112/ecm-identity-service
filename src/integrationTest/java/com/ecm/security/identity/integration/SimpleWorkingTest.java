package com.ecm.security.identity.integration;

import com.ecm.security.identity.domain.Tenant;
import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.repository.TenantRepository;
import com.ecm.security.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple working database connectivity test using @SpringBootTest with PostgreSQL Testcontainers.
 * This test focuses on basic database connectivity and entity operations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
                classes = {TestDataJpaConfig.class})
@Testcontainers
@ActiveProfiles("integration-test")
@Transactional
class SimpleWorkingTest {

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
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("ecm.audit.enabled", () -> "false");
        registry.add("ecm.multitenancy.enabled", () -> "false");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    @DisplayName("Database connectivity works")
    void testDatabaseConnectivity() {
        // Test that repositories are available
        assertNotNull(userRepository);
        assertNotNull(tenantRepository);
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
                .settings("{}") // Valid JSON for jsonb column
                .build();
        
        // Save the tenant
        Tenant savedTenant = tenantRepository.save(tenant);
        
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
                .settings("{}") // Valid JSON for jsonb column
                .build();
        
        Tenant savedTenant = tenantRepository.save(tenant);
        
        // Create a simple user
        User user = User.builder()
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .tenant(savedTenant)
                .status(User.UserStatus.ACTIVE)
                .metadata("{}") // Valid JSON for jsonb column
                .build();
        
        // Save the user
        User savedUser = userRepository.save(user);
        
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
                .settings("{}") // Valid JSON for jsonb column
                .build();
        
        Tenant savedTenant = tenantRepository.save(tenant);
        
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
