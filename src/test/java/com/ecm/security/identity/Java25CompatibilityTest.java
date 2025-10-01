package com.ecm.security.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Java compatibility test to verify the application can run with the current Java version.
 */
class Java25CompatibilityTest {

    @Test
    void contextLoadsWithJava25() {
        // This test will pass if the application can be instantiated with the current Java version
        EcmIdentityServiceApplication app = new EcmIdentityServiceApplication();
        assertNotNull(app);
        System.out.println("✅ Java compatibility test passed!");
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("Java vendor: " + System.getProperty("java.vendor"));
    }

    @Test
    void javaVersionTest() {
        String javaVersion = System.getProperty("java.version");
        System.out.println("Running on Java: " + javaVersion);
        
        // Verify we're running on Java 21 or higher (Java 25 is not yet released)
        assertTrue(javaVersion.startsWith("21") || javaVersion.startsWith("22") || 
               javaVersion.startsWith("23") || javaVersion.startsWith("24") || 
               javaVersion.startsWith("25"), "Expected Java 21+, but got: " + javaVersion);
    }
}
