package com.ecm.security.identity.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

/**
 * Test configuration that provides a PostgreSQL DataSource for @DataJpaTest.
 */
@TestConfiguration
public class PostgresTestConfig {
    
    /**
     * Provides a primary DataSource that will be used by @DataJpaTest.
     * This will be overridden by the DynamicPropertySource in the test.
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
        dataSource.setUrl("jdbc:postgresql://localhost:5432/test");
        dataSource.setUsername("test");
        dataSource.setPassword("test");
        return dataSource;
    }
}
