package com.ecm.security.identity.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * Test configuration to provide mock implementations of problematic services
 * to avoid circular dependency issues during integration testing.
 */
@TestConfiguration
@Profile("integration-test")
public class IntegrationTestConfiguration {
    
    /**
     * Mock UserDetailsService to avoid circular dependencies.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean
    public UserDetailsService mockUserDetailsService() {
        return username -> {
            throw new UnsupportedOperationException("Mock UserDetailsService - not implemented for testing");
        };
    }
}
