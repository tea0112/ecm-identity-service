package com.ecm.security.identity;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Java 25 compatibility test to verify the application can run with Java 25.
 */
@SpringBootTest(classes = EcmIdentityServiceApplication.class)
@ActiveProfiles("test")
class Java25CompatibilityTest {

    @Test
    void contextLoadsWithJava25() {
        // This test will pass if the application context loads successfully with Java 25
        System.out.println("✅ Java 25 compatibility test passed!");
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("Java vendor: " + System.getProperty("java.vendor"));
    }

    @Test
    void javaVersionTest() {
        String javaVersion = System.getProperty("java.version");
        System.out.println("Running on Java: " + javaVersion);
        
        // Verify we're running on Java 25
        assert javaVersion.startsWith("25") : "Expected Java 25, but got: " + javaVersion;
    }
}
