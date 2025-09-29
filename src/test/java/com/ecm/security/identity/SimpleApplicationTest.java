package com.ecm.security.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Simple application context test to verify basic setup.
 */
@SpringBootTest
@ActiveProfiles("test")
class SimpleApplicationTest {

    @Test
    void contextLoads() {
        // This test will pass if the application context loads successfully
    }
}
