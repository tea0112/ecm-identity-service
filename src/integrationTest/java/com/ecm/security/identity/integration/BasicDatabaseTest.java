package com.ecm.security.identity.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic database connectivity test using @DataJpaTest with Testcontainers.
 * This test focuses on basic database connectivity without complex entity relationships.
 */
@DataJpaTest
@Testcontainers
class BasicDatabaseTest {

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
    private DataSource dataSource;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Database connectivity works with Testcontainers")
    void testDatabaseConnectivity() throws Exception {
        // Test basic database connection
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection);
            assertFalse(connection.isClosed());
            
            // Test basic SQL query
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT 1 as test")) {
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
            try (ResultSet resultSet = statement.executeQuery("SELECT * FROM test_table WHERE name = 'test'")) {
                assertTrue(resultSet.next());
                assertEquals("test", resultSet.getString("name"));
            }
            
            // Clean up
            statement.execute("DROP TABLE test_table");
        }
    }

    @Test
    @DisplayName("PostgreSQL specific features work")
    void testPostgreSQLFeatures() throws Exception {
        // Test PostgreSQL-specific features
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Test UUID support
            try (ResultSet resultSet = statement.executeQuery("SELECT gen_random_uuid() as uuid")) {
                assertTrue(resultSet.next());
                assertNotNull(resultSet.getString("uuid"));
            }
            
            // Test JSON support
            try (ResultSet resultSet = statement.executeQuery("SELECT '{\"test\": \"value\"}'::jsonb as json_data")) {
                assertTrue(resultSet.next());
                assertNotNull(resultSet.getString("json_data"));
            }
        }
    }
}
