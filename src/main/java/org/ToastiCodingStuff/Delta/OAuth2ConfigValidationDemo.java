package org.ToastiCodingStuff.Delta;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Simple test to demonstrate OAuth2 configuration validation improvements
 */
public class OAuth2ConfigValidationDemo {
    public static void main(String[] args) {
        System.out.println("=== OAuth2 Configuration Validation Demo ===");
        System.out.println();
        
        try {
            // Load environment variables
            Dotenv dotenv = Dotenv.load();
            String clientId = dotenv.get("DISCORD_CLIENT_ID");
            String clientSecret = dotenv.get("DISCORD_CLIENT_SECRET");
            String redirectUri = dotenv.get("DISCORD_REDIRECT_URI", "http://localhost:8080/auth/callback");
            
            // Simulate the validation logic from WebServer
            System.out.println("=== Discord OAuth2 Configuration Check ===");
            
            if (clientId == null || clientId.trim().isEmpty()) {
                System.err.println("❌ DISCORD_CLIENT_ID is not set in .env file");
                System.err.println("   → Please add your Discord application's Client ID to .env");
                System.err.println("   → Get it from: https://discord.com/developers/applications");
            } else {
                System.out.println("✅ DISCORD_CLIENT_ID is configured: " + clientId);
            }
            
            if (clientSecret == null || clientSecret.trim().isEmpty()) {
                System.err.println("❌ DISCORD_CLIENT_SECRET is not set in .env file");
                System.err.println("   → Please add your Discord application's Client Secret to .env");
                System.err.println("   → Get it from: https://discord.com/developers/applications");
            } else {
                System.out.println("✅ DISCORD_CLIENT_SECRET is configured: " + maskSecret(clientSecret));
            }
            
            System.out.println("📋 Redirect URI: " + redirectUri);
            System.out.println("   → Make sure this matches your Discord application's redirect URI");
            
            if (clientId == null || clientSecret == null || clientId.trim().isEmpty() || clientSecret.trim().isEmpty()) {
                System.err.println("\n⚠️  Discord OAuth2 login will not work without proper configuration!");
                System.err.println("   → Copy .env.example to .env and fill in your Discord application credentials");
            } else {
                System.out.println("\n✅ Discord OAuth2 appears to be properly configured");
            }
            System.out.println("===========================================");
            
            System.out.println("\n=== Error Handling Improvements ===");
            System.out.println("✅ Added detailed configuration validation on server startup");
            System.out.println("✅ Enhanced error logging in OAuth2 flow");
            System.out.println("✅ User-friendly error pages for OAuth2 failures");
            System.out.println("✅ Better debugging information in console logs");
            System.out.println("✅ Graceful handling of Discord API errors");
            System.out.println("\n=== What users will now see ===");
            if (clientId == null || clientSecret == null || clientId.trim().isEmpty() || clientSecret.trim().isEmpty()) {
                System.out.println("- Clear error message explaining OAuth2 is not configured");
                System.out.println("- Helpful HTML error page instead of generic 500 error");
                System.out.println("- Direct links to Discord developer portal for setup");
                System.out.println("- Server startup warnings about missing configuration");
            } else {
                System.out.println("- Detailed logging of each step in OAuth2 process");
                System.out.println("- Specific error messages if Discord API calls fail");
                System.out.println("- Better error reporting in case of token exchange failures");
            }
            
        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static String maskSecret(String secret) {
        if (secret == null || secret.length() < 8) return "***";
        return secret.substring(0, 4) + "***" + secret.substring(secret.length() - 4);
    }
}