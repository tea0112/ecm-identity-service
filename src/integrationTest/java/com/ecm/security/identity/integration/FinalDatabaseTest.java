package com.ecm.security.identity.integration;

import com.ecm.security.identity.domain.Tenant;
import com.ecm.security.identity.domain.User;
import com.ecm.security.identity.repository.TenantRepository;
import com.ecm.security.identity.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Final working database connectivity test using @SpringBootTest with Testcontainers.
 * This test uses a minimal Spring Boot context that only loads necessary components.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, 
                classes = {TestDataJpaConfig.class})
@Testcontainers
@org.springframework.test.context.ActiveProfiles("integration-test")
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration,org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration,org.springframework.boot.autoconfigure.actuator.ActuatorAutoConfiguration",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    "spring.flyway.enabled=false",
    "ecm.audit.enabled=false",
    "ecm.multitenancy.enabled=false",
    "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop"
})
@Transactional
class FinalDatabaseTest {

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
        registry.add("spring.jpa.properties.hibernate.hbm2ddl.auto", () -> "create-drop");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    @DisplayName("Database connectivity works with Testcontainers")
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
                .build();
        
        Tenant savedTenant = tenantRepository.save(tenant);
        
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
        
        tenantRepository.save(tenant);
        
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
