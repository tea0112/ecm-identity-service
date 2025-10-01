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
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Working database connectivity test using @SpringBootTest with Testcontainers.
 * This test uses a minimal Spring Boot context that only loads necessary components.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, 
                classes = {TestDataJpaConfig.class})
@Testcontainers
@org.springframework.test.context.ActiveProfiles("integration-test")
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration,org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
    "spring.flyway.enabled=false",
    "ecm.audit.enabled=false",
    "ecm.multitenancy.enabled=false"
})
@Transactional
class WorkingDatabaseTest {

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
                .build();
        
        userRepository.save(testUser);
        
        // Verify user was saved
        Optional<User> foundUser = userRepository.findByEmailAndTenant("test2@example.com", testTenant);
        assertTrue(foundUser.isPresent());
        assertEquals("Test2", foundUser.get().getFirstName());
    }
}
