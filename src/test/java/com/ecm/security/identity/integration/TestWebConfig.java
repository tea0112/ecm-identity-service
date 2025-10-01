package com.ecm.security.identity.integration;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test configuration for web integration tests that need REST endpoints
 * but want to avoid bean definition conflicts with the main application.
 */
@Configuration
@EnableAutoConfiguration(exclude = {
    org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class,
    org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@ComponentScan({"com.ecm.security.identity.controller", "com.ecm.security.identity.service"})
@EntityScan("com.ecm.security.identity.domain")
@EnableJpaRepositories("com.ecm.security.identity.repository")
@EnableJpaAuditing
@EnableWebSecurity
@ActiveProfiles("integration-test")
public class TestWebConfig {

    /**
     * Minimal security configuration for tests - allows all requests
     */
    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.disable());
        
        return http.build();
    }

    /**
     * Simple password encoder for tests
     */
    @Bean
    public PasswordEncoder testPasswordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }
}
