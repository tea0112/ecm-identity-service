public class Java25Test {
    public static void main(String[] args) {
        System.out.println("🚀 Java 25 Test Starting...");
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("Java vendor: " + System.getProperty("java.vendor"));
        
        // Test var keyword
        var message = "Java 25 is working!";
        System.out.println("Message: " + message);
        
        // Test text blocks
        var textBlock = """
            This is a text block
            running on Java 25
            with multiple lines
            """;
        System.out.println("Text block:");
        System.out.println(textBlock);
        
        // Test switch expressions
        var numbers = java.util.List.of(1, 2, 3, 4, 5);
        var result = switch (numbers.size()) {
            case 5 -> "Five elements";
            case 3 -> "Three elements";
            default -> "Other count";
        };
        System.out.println("Switch result: " + result);
        
        System.out.println("✅ Java 25 Test Completed Successfully!");
    }
}
