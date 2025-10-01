package com.ecm.security.identity.integration;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Minimal test configuration for database connectivity tests.
 * This configuration only loads the necessary components for JPA testing.
 */
@TestConfiguration
@Profile("integration-test")
@EnableAutoConfiguration(exclude = {
    SecurityAutoConfiguration.class,
    RedisAutoConfiguration.class,
    MailSenderAutoConfiguration.class,
    WebMvcAutoConfiguration.class
})
@ComponentScan(basePackages = {
    "com.ecm.security.identity.domain",
    "com.ecm.security.identity.repository"
})
@EnableJpaRepositories(basePackages = "com.ecm.security.identity.repository")
public class DatabaseTestConfiguration {
    // Minimal configuration for database connectivity tests
}
