package com.ecm.security.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal Java 25 test to verify basic functionality.
 */
class MinimalJava25Test {

    @Test
    void testJava25Features() {
        // Test basic Java 25 functionality
        String javaVersion = System.getProperty("java.version");
        System.out.println("🚀 Running on Java: " + javaVersion);
        
        // Verify we're running on Java 25
        assertTrue(javaVersion.startsWith("25"), 
            "Expected Java 25, but got: " + javaVersion);
        
        // Test some basic Java features
        var message = "Java 25 is working!";
        assertNotNull(message);
        assertEquals("Java 25 is working!", message);
        
        System.out.println("✅ Java 25 basic test passed!");
    }

    @Test
    void testModernJavaFeatures() {
        // Test var keyword (Java 10+)
        var numbers = java.util.List.of(1, 2, 3, 4, 5);
        assertEquals(5, numbers.size());
        
        // Test text blocks (Java 13+)
        var textBlock = """
            This is a text block
            running on Java 25
            """;
        assertTrue(textBlock.contains("Java 25"));
        
        // Test switch expressions (Java 14+)
        var result = switch (numbers.size()) {
            case 5 -> "Five elements";
            default -> "Other";
        };
        assertEquals("Five elements", result);
        
        System.out.println("✅ Modern Java features test passed!");
    }
}
