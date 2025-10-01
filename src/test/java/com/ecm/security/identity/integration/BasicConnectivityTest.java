package com.ecm.security.identity.integration;

import com.ecm.security.identity.domain.Tenant;
import com.ecm.security.identity.repository.TenantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Very basic connectivity test to verify Testcontainers and database setup.
 */
@DataJpaTest
@Testcontainers
class BasicConnectivityTest {

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
    @DisplayName("Basic repository injection works")
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
                .build();
        
        Tenant savedTenant = tenantRepository.save(tenant);
        assertNotNull(savedTenant.getId());
        assertEquals("test-tenant", savedTenant.getTenantCode());
    }
}
