package com.ecm.security.identity.integration;

import com.ecm.security.identity.domain.Tenant;
import com.ecm.security.identity.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Very simple test that only uses basic JPA operations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ContextConfiguration(classes = TestDataJpaConfig.class)
@Testcontainers
class SimpleTenantTest {

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
    @DisplayName("Basic tenant save and find works")
    void testBasicTenantOperations() {
        // Create tenant
        Tenant tenant = Tenant.builder()
                .tenantCode("simple-test")
                .name("Simple Test Tenant")
                .domain("simple.example.com")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        
        // Save tenant
        Tenant savedTenant = tenantRepository.save(tenant);
        assertNotNull(savedTenant.getId());
        assertEquals("simple-test", savedTenant.getTenantCode());
        
        // Find all tenants
        List<Tenant> allTenants = tenantRepository.findAll();
        assertTrue(allTenants.size() >= 1);
        
        // Find by ID
        Tenant foundTenant = tenantRepository.findById(savedTenant.getId()).orElse(null);
        assertNotNull(foundTenant);
        assertEquals("simple-test", foundTenant.getTenantCode());
    }
}
