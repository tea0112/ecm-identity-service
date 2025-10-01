package com.ecm.security.identity.integration;

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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal database connectivity test using PostgreSQL with Testcontainers.
 * This test focuses on basic database connectivity without complex service dependencies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, 
                classes = {TestDataJpaConfig.class})
@Testcontainers
@Transactional
@org.springframework.test.context.ActiveProfiles("integration-test")
class MinimalTest {

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
    private DataSource dataSource;

    @Test
    @DisplayName("Testcontainers PostgreSQL starts successfully")
    void testPostgresContainerStarts() {
        assertTrue(postgres.isRunning());
        assertNotNull(postgres.getJdbcUrl());
    }

    @Test
    @DisplayName("Database connectivity works")
    void testDatabaseConnectivity() throws Exception {
        // Test basic database connection
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
            
            // Test basic SQL query
            try (Statement statement = connection.createStatement()) {
                var resultSet = statement.executeQuery("SELECT 1 as test");
                assertTrue(resultSet.next());
                assertEquals(1, resultSet.getInt("test"));
            }
        }
    }

    @Test
    @DisplayName("Database schema can be created")
    void testDatabaseSchemaCreation() throws Exception {
        // Test that we can create a simple table
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Create a simple test table
            statement.execute("CREATE TABLE test_table (id SERIAL PRIMARY KEY, name VARCHAR(100))");
            
            // Insert test data
            statement.execute("INSERT INTO test_table (name) VALUES ('test')");
            
            // Query test data
            var resultSet = statement.executeQuery("SELECT * FROM test_table WHERE name = 'test'");
            assertTrue(resultSet.next());
            assertEquals("test", resultSet.getString("name"));
            
            // Clean up
            statement.execute("DROP TABLE test_table");
        }
    }

    @Test
    @DisplayName("DataSource injection works")
    void testDataSourceInjection() {
        // Test that DataSource is available
        assertNotNull(dataSource);
    }
}
