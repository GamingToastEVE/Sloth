package org.ToastiCodingStuff.Delta;

import org.ToastiCodingStuff.Delta.web.WebApplication;
import org.springframework.boot.SpringApplication;

/**
 * Combined launcher that starts both the Discord bot and web application
 */
public class CombinedApplication {
    
    public static void main(String[] args) {
        // Start the Discord bot in a separate thread
        Thread botThread = new Thread(() -> {
            try {
                System.out.println("Starting Discord bot...");
                Delta.main(args);
            } catch (Exception e) {
                System.err.println("Error starting Discord bot: " + e.getMessage());
                e.printStackTrace();
            }
        });
        botThread.setDaemon(false);
        botThread.start();
        
        // Start the web application
        System.out.println("Starting web application...");
        SpringApplication.run(WebApplication.class, args);
    }
}