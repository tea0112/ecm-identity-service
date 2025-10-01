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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple integration test to verify basic Spring Boot context loading and database connectivity.
 * This test avoids complex service dependencies that cause circular dependency issues.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, 
                classes = {TestDataJpaConfig.class})
@Testcontainers
@Transactional
@org.springframework.test.context.ActiveProfiles("integration-test")
class SimpleIntegrationTest {

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
        registry.add("spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation", () -> "true");
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
                .build();
        testTenant = tenantRepository.save(testTenant);
    }

    @Test
    @DisplayName("Basic Spring Boot context loads successfully")
    void testSpringContextLoads() {
        // This test verifies that the Spring Boot application context can be created
        // without circular dependency issues
        assertNotNull(userRepository);
        assertNotNull(tenantRepository);
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
}
