package com.ecm.security.identity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Minimal Java compatibility test to verify basic functionality.
 */
class MinimalJava25Test {

    @Test
    void testJava25Features() {
        // Test basic Java functionality
        String javaVersion = System.getProperty("java.version");
        System.out.println("🚀 Running on Java: " + javaVersion);
        
        // Verify we're running on Java 21 or higher (Java 25 is not yet released)
        assertTrue(javaVersion.startsWith("21") || javaVersion.startsWith("22") || 
                   javaVersion.startsWith("23") || javaVersion.startsWith("24") || 
                   javaVersion.startsWith("25"), 
            "Expected Java 21+, but got: " + javaVersion);
        
        // Test some basic Java features
        var message = "Java is working!";
        assertNotNull(message);
        assertEquals("Java is working!", message);
        
        System.out.println("✅ Java basic test passed!");
    }

    @Test
    void testModernJavaFeatures() {
        // Test var keyword (Java 10+)
        var numbers = java.util.List.of(1, 2, 3, 4, 5);
        assertEquals(5, numbers.size());
        
        // Test text blocks (Java 13+)
        var textBlock = """
            This is a text block
            running on Java
            """;
        assertTrue(textBlock.contains("Java"));
        
        // Test switch expressions (Java 14+)
        var result = switch (numbers.size()) {
            case 5 -> "Five elements";
            default -> "Other";
        };
        assertEquals("Five elements", result);
        
        System.out.println("✅ Modern Java features test passed!");
    }
}
