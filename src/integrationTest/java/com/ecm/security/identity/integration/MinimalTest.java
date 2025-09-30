package com.ecm.security.identity.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal database connectivity test using @DataJpaTest with H2.
 * This test focuses on basic database connectivity without complex entities.
 */
@DataJpaTest
@TestPropertySource(properties = {
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=false",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false"
})
class MinimalTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TestEntityManager entityManager;

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
    @DisplayName("Entity manager works")
    void testEntityManager() {
        // Test that entity manager is available
        assertNotNull(entityManager);
    }
}
