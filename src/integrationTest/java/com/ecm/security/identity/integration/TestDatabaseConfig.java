package com.ecm.security.identity.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration for database connectivity tests.
 * This configuration provides minimal setup for database-only tests.
 */
@TestConfiguration
@Profile("integration-test")
public class TestDatabaseConfig {
    // Minimal configuration for database connectivity tests
    // Complex services are disabled via application properties
}
