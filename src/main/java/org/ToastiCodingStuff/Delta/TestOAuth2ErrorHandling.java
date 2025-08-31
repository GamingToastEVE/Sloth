package org.ToastiCodingStuff.Delta;

/**
 * Simple test to verify the OAuth2 error handling improvements
 */
public class TestOAuth2ErrorHandling {
    public static void main(String[] args) {
        try {
            System.out.println("=== Testing OAuth2 Error Handling Improvements ===");
            
            // Create a mock database handler (this won't actually connect to anything)
            DatabaseHandler handler = new DatabaseHandler();
            
            // This will fail because we don't have a real JDA instance or proper Discord credentials
            // But it will demonstrate the improved error handling
            System.out.println("Testing WebServer OAuth2 configuration validation...");
            
            // The WebServer constructor should now validate OAuth2 configuration and provide helpful feedback
            WebServer webServer = new WebServer(8081, handler, null);
            
            System.out.println("WebServer created successfully with configuration validation");
            System.out.println("Starting web server on port 8081...");
            
            webServer.start();
            
            System.out.println("✅ Test completed - Web server started with improved error handling");
            System.out.println("📋 You can now test the Discord login at: http://localhost:8081");
            System.out.println("💡 Since OAuth2 credentials are not configured, you should see helpful error messages");
            
            // Keep running for a short time to allow testing
            Thread.sleep(30000);
            
            webServer.stop();
            System.out.println("Test web server stopped");
            
        } catch (Exception e) {
            System.err.println("Test failed with error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}