package com.ecm.security.identity.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * Test data source configuration for database connectivity tests.
 * This provides a fallback data source when Testcontainers is not available.
 */
@TestConfiguration
@Profile("integration-test")
public class TestDataSourceConfig {

    /**
     * Provides a fallback H2 data source for tests.
     * This is used when Testcontainers PostgreSQL is not available.
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("testdb")
                .build();
    }
}
