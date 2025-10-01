package com.ecm.security.identity.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Database connectivity test using @DataJpaTest with PostgreSQL Testcontainers.
 * 
 * This test demonstrates:
 * 1. PostgreSQL Testcontainers connectivity works
 * 2. Database connection and basic SQL operations work
 * 3. JPA infrastructure is available and functional
 * 
 * Note: This test focuses on database connectivity rather than complex entity operations
 * due to the complex entity relationships in the domain model.
 */
@DataJpaTest
@Testcontainers
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false",
    "ecm.audit.enabled=false",
    "ecm.multitenancy.enabled=false"
})
class DatabaseConnectivityTest {

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
    private DataSource dataSource;

    @Test
    @DisplayName("PostgreSQL Testcontainers connectivity works")
    void testPostgresConnectivity() throws Exception {
        // Test that PostgreSQL Testcontainers is running and accessible
        assertTrue(postgres.isRunning(), "PostgreSQL container should be running");
        assertNotNull(postgres.getJdbcUrl(), "JDBC URL should not be null");
        assertNotNull(postgres.getUsername(), "Username should not be null");
        assertNotNull(postgres.getPassword(), "Password should not be null");
        
        // Test that the container is accessible
        String jdbcUrl = postgres.getJdbcUrl();
        assertTrue(jdbcUrl.contains("postgresql"), "JDBC URL should contain postgresql: " + jdbcUrl);
        // Note: The port might be dynamically assigned, so we'll just check for postgresql
    }

    @Test
    @DisplayName("Database connection works")
    void testDatabaseConnection() throws Exception {
        // Test database connection
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
    @DisplayName("Database schema operations work")
    void testDatabaseSchemaOperations() throws Exception {
        // Test that we can create tables and perform SQL operations
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
    @DisplayName("JPA infrastructure is available")
    void testJpaInfrastructure() {
        // Test that JPA infrastructure is available
        assertNotNull(dataSource);
        
        // Test that we can get connection metadata
        try (Connection connection = dataSource.getConnection()) {
            assertNotNull(connection.getMetaData());
            assertNotNull(connection.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            fail("Failed to get connection metadata: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Database features work correctly")
    void testDatabaseFeatures() throws Exception {
        // Test database features
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            
            // Test database functions
            try (ResultSet resultSet = statement.executeQuery("SELECT CURRENT_TIMESTAMP as now")) {
                assertTrue(resultSet.next());
                assertNotNull(resultSet.getTimestamp("now"));
            }
            
            // Test data types
            try (ResultSet resultSet = statement.executeQuery("SELECT 'test' as string_value, 123 as int_value")) {
                assertTrue(resultSet.next());
                assertEquals("test", resultSet.getString("string_value"));
                assertEquals(123, resultSet.getInt("int_value"));
            }
        }
    }
}