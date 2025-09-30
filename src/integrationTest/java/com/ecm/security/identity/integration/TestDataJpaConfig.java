package com.ecm.security.identity.integration;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ActiveProfiles;

/**
 * Test configuration for JPA-only tests that avoids loading problematic services.
 */
@Configuration
@EnableAutoConfiguration(exclude = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
})
@EntityScan("com.ecm.security.identity.domain")
@EnableJpaRepositories("com.ecm.security.identity.repository")
@ActiveProfiles("integration-test")
public class TestDataJpaConfig {
}
