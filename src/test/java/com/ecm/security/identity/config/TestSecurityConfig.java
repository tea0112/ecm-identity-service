package com.ecm.security.identity.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Test security configuration for integration tests.
 * Provides test-specific security beans and configurations.
 */
@TestConfiguration
@Profile("test")
public class TestSecurityConfig {

    /**
     * Provide a simple password encoder for tests.
     */
    @Bean
    @Primary
    public PasswordEncoder testPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
