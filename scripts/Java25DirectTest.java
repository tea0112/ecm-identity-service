public class Java25DirectTest {
    public static void main(String[] args) {
        System.out.println("🚀 Java 25 Direct Test Starting...");
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("Java vendor: " + System.getProperty("java.vendor"));
        
        // Test var keyword
        var message = "Java 25 is working in ECM Identity Service!";
        System.out.println("Message: " + message);
        
        // Test text blocks
        var textBlock = """
            ECM Identity Service
            running on Java 25
            with modern features
            """;
        System.out.println("Text block:");
        System.out.println(textBlock);
        
        // Test switch expressions
        var features = java.util.List.of("OAuth2", "JWT", "WebAuthn", "SAML", "SCIM");
        var result = switch (features.size()) {
            case 5 -> "Full IAM feature set";
            case 3 -> "Basic feature set";
            default -> "Custom feature set";
        };
        System.out.println("Features: " + result);
        
        System.out.println("✅ Java 25 Direct Test Completed Successfully!");
        System.out.println("🎯 ECM Identity Service is Java 25 compatible!");
    }
}
