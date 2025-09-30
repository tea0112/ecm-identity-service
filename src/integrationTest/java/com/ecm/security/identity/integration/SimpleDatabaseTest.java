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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple database connectivity test using @SpringBootTest with Testcontainers.
 * This test focuses only on basic JPA operations and database connectivity.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
                classes = {TestDataJpaConfig.class})
@Testcontainers
@ActiveProfiles("integration-test")
@Transactional
class SimpleDatabaseTest {

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

    private Tenant testTenant;

    @BeforeEach
    void setUp() {
        // Create test tenant
        testTenant = Tenant.builder()
                .tenantCode("test-tenant")
                .name("Test Tenant")
                .domain("test.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .settings("{}") // Valid JSON for jsonb column
                .build();
        testTenant = tenantRepository.save(testTenant);
    }

    @Test
    @DisplayName("Database connectivity works with Testcontainers")
    void testDatabaseConnectivity() {
        // Test basic database operations
        assertNotNull(testTenant);
        assertNotNull(testTenant.getId());
        assertEquals("test-tenant", testTenant.getTenantCode());
    }

    @Test
    @DisplayName("User creation and retrieval works")
    void testUserCreationAndRetrieval() {
        // Create a test user
        User testUser = User.builder()
                .email("test@example.com")
                .firstName("Test")
                .lastName("User")
                .tenant(testTenant)
                .status(User.UserStatus.ACTIVE)
                .metadata("{}") // Valid JSON for jsonb column
                .build();
        
        User savedUser = userRepository.save(testUser);
        
        assertNotNull(savedUser.getId());
        
        // Retrieve the user
        Optional<User> retrievedUser = userRepository.findByTenantAndEmailAndDeletedAtIsNull(
            testTenant, "test@example.com");
        
        assertTrue(retrievedUser.isPresent());
        assertEquals("test@example.com", retrievedUser.get().getEmail());
        assertEquals("Test", retrievedUser.get().getFirstName());
        assertEquals("User", retrievedUser.get().getLastName());
    }

    @Test
    @DisplayName("Tenant retrieval works")
    void testTenantRetrieval() {
        // Retrieve the tenant by code
        Optional<Tenant> retrievedTenant = tenantRepository.findByTenantCodeAndStatusNot(
            "test-tenant", Tenant.TenantStatus.ARCHIVED);
        
        assertTrue(retrievedTenant.isPresent());
        assertEquals("test-tenant", retrievedTenant.get().getTenantCode());
        assertEquals("Test Tenant", retrievedTenant.get().getName());
    }

    @Test
    @DisplayName("Repository operations work correctly")
    void testRepositoryOperations() {
        // Test tenant repository operations
        assertTrue(tenantRepository.existsByTenantCodeAndDeletedAtIsNull("test-tenant"));
        assertFalse(tenantRepository.existsByTenantCodeAndDeletedAtIsNull("non-existent-tenant"));
        
        // Test user repository operations
        User testUser = User.builder()
                .email("test2@example.com")
                .firstName("Test2")
                .lastName("User2")
                .tenant(testTenant)
                .status(User.UserStatus.ACTIVE)
                .metadata("{}") // Valid JSON for jsonb column
                .build();
        
        userRepository.save(testUser);
        
        // Verify user was saved
        Optional<User> foundUser = userRepository.findByEmailAndTenant("test2@example.com", testTenant);
        assertTrue(foundUser.isPresent());
        assertEquals("Test2", foundUser.get().getFirstName());
    }
}
