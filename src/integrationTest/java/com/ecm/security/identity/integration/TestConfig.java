package com.ecm.security.identity.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

/**
 * Test configuration that provides minimal setup for database connectivity tests.
 */
@TestConfiguration
public class TestConfig {
    
    /**
     * Provides a primary DataSource that will be used by @DataJpaTest.
     * This overrides the default H2 configuration.
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        // This will be overridden by the DynamicPropertySource in the test
        return new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();
    }
}
