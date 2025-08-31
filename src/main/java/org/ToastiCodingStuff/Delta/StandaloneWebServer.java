package org.ToastiCodingStuff.Delta;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 * Standalone web server for testing the dashboard
 */
public class StandaloneWebServer {
    public static void main(String[] args) {
        try {
            // Create a test database handler
            DatabaseHandler handler = new DatabaseHandler();
            
            // Initialize JDA for Discord integration
            Dotenv dotenv = Dotenv.load();
            JDA api = JDABuilder.createDefault(dotenv.get("TOKEN"))
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                    .build();
            api.awaitReady();
            
            // Start web server on port 8080
            WebServer webServer = new WebServer(8080, handler, api);
            webServer.start();
            
            System.out.println("Standalone web server started successfully!");
            System.out.println("Visit http://localhost:8080 to view the dashboard");
            System.out.println("The server will run until you press Ctrl+C");
            
            // Add shutdown hook to gracefully stop the server
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down web server...");
                webServer.stop();
                api.shutdownNow();
            }));
            
            // Keep the server running indefinitely
            while (true) {
                Thread.sleep(1000);
            }
            
        } catch (Exception e) {
            System.err.println("Error starting web server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}