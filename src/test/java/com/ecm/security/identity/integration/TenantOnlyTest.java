package com.ecm.security.identity.integration;

import com.ecm.security.identity.domain.Tenant;
import com.ecm.security.identity.repository.TenantRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that only uses Tenant entity to avoid complex relationship issues.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
                classes = {TestDataJpaConfig.class})
@Testcontainers
@ActiveProfiles("integration-test")
@Transactional
class TenantOnlyTest {

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
    private TenantRepository tenantRepository;

    @Test
    @DisplayName("Testcontainers PostgreSQL starts successfully")
    void testPostgresContainerStarts() {
        assertTrue(postgres.isRunning());
        assertNotNull(postgres.getJdbcUrl());
    }

    @Test
    @DisplayName("Repository injection works")
    void testRepositoryInjection() {
        assertNotNull(tenantRepository);
    }

    @Test
    @DisplayName("Simple tenant creation and save works")
    void testSimpleTenantCreation() {
        Tenant tenant = Tenant.builder()
                .tenantCode("test-tenant")
                .name("Test Tenant")
                .domain("test.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .settings("{}") // Valid JSON for jsonb column
                .build();
        
        Tenant savedTenant = tenantRepository.save(tenant);
        assertNotNull(savedTenant.getId());
        assertEquals("test-tenant", savedTenant.getTenantCode());
    }

    @Test
    @DisplayName("Tenant retrieval by code works")
    void testTenantRetrievalByCode() {
        // Create tenant
        Tenant tenant = Tenant.builder()
                .tenantCode("retrieval-test")
                .name("Retrieval Test Tenant")
                .domain("retrieval.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .settings("{}") // Valid JSON for jsonb column
                .build();
        
        tenantRepository.save(tenant);
        
        // Retrieve by code
        Optional<Tenant> retrievedTenant = tenantRepository.findByTenantCodeAndStatusNot(
            "retrieval-test", Tenant.TenantStatus.ARCHIVED);
        
        assertTrue(retrievedTenant.isPresent());
        assertEquals("retrieval-test", retrievedTenant.get().getTenantCode());
        assertEquals("Retrieval Test Tenant", retrievedTenant.get().getName());
    }

    @Test
    @DisplayName("Tenant status filtering works")
    void testTenantStatusFiltering() {
        // Create active tenant
        Tenant activeTenant = Tenant.builder()
                .tenantCode("active-tenant")
                .name("Active Tenant")
                .domain("active.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .settings("{}") // Valid JSON for jsonb column
                .build();
        
        // Create archived tenant
        Tenant archivedTenant = Tenant.builder()
                .tenantCode("archived-tenant")
                .name("Archived Tenant")
                .domain("archived.example.com")
                .status(Tenant.TenantStatus.ARCHIVED)
                .settings("{}") // Valid JSON for jsonb column
                .build();
        
        tenantRepository.save(activeTenant);
        tenantRepository.save(archivedTenant);
        
        // Test status filtering
        List<Tenant> activeTenants = tenantRepository.findByStatusAndDeletedAtIsNull(Tenant.TenantStatus.ACTIVE);
        assertTrue(activeTenants.size() >= 1);
        
        // Verify archived tenant is not found when filtering by active status
        Optional<Tenant> foundActive = activeTenants.stream()
                .filter(t -> "active-tenant".equals(t.getTenantCode()))
                .findFirst();
        assertTrue(foundActive.isPresent());
        
        Optional<Tenant> foundArchived = activeTenants.stream()
                .filter(t -> "archived-tenant".equals(t.getTenantCode()))
                .findFirst();
        assertFalse(foundArchived.isPresent());
    }
}
