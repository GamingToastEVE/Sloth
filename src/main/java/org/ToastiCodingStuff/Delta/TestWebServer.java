package org.ToastiCodingStuff.Delta;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Simple test web server to demonstrate the OAuth2 UI without Discord API
 */
public class TestWebServer {
    private final HttpServer server;
    private final int port;

    public TestWebServer(int port) throws IOException {
        this.port = port;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        setupRoutes();
        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    private void setupRoutes() {
        // Serve static files (HTML, CSS, JS)
        server.createContext("/", new StaticFileHandler());
        
        // Mock API endpoints
        server.createContext("/api/user", new MockUserHandler());
        server.createContext("/api/guilds", new MockGuildsHandler());
        server.createContext("/api/statistics", new MockStatisticsHandler());
        server.createContext("/auth/login", new MockLoginHandler());
        server.createContext("/auth/logout", new MockLogoutHandler());
    }

    public void start() {
        server.start();
        System.out.println("Test web server started on http://localhost:" + port);
        System.out.println("This demonstrates the Discord SSO UI without requiring Discord API access");
    }

    public void stop() {
        server.stop(0);
    }

    // Handler for static files
    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            
            // Default to index.html for root path
            if (path.equals("/")) {
                path = "/index.html";
            }
            
            // Load resource from classpath
            String resourcePath = "/web" + path;
            InputStream resourceStream = getClass().getResourceAsStream(resourcePath);
            
            if (resourceStream == null) {
                // Return 404 for missing files
                String response = "404 Not Found";
                exchange.sendResponseHeaders(404, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }
            
            // Determine content type
            String contentType = getContentType(path);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            
            // Send file content
            byte[] content = resourceStream.readAllBytes();
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(content);
            }
            resourceStream.close();
        }
        
        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".json")) return "application/json";
            return "text/plain";
        }
    }

    // Mock handlers to demonstrate functionality
    private class MockUserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Simulate authenticated user response
            String response = "{\"id\":\"123456789\",\"username\":\"TestUser\",\"discriminator\":\"0001\",\"avatar\":\"https://cdn.discordapp.com/embed/avatars/0.png\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private class MockGuildsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Simulate guilds where user has moderate permissions
            String response = "{\"guilds\":[" +
                "{\"id\":\"guild1\",\"name\":\"Test Server #1\",\"icon\":\"https://cdn.discordapp.com/embed/avatars/1.png\"}," +
                "{\"id\":\"guild2\",\"name\":\"Awesome Community\",\"icon\":\"https://cdn.discordapp.com/embed/avatars/2.png\"}," +
                "{\"id\":\"guild3\",\"name\":\"Gaming Hub\",\"icon\":\"https://cdn.discordapp.com/embed/avatars/3.png\"}" +
                "]}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private class MockStatisticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String guildId = "unknown";
            if (query != null && query.startsWith("guildId=")) {
                guildId = query.substring(8);
            }
            
            String guildName;
            if ("guild1".equals(guildId)) {
                guildName = "Test Server #1";
            } else if ("guild2".equals(guildId)) {
                guildName = "Awesome Community";
            } else if ("guild3".equals(guildId)) {
                guildName = "Gaming Hub";
            } else {
                guildName = "Unknown Server";
            }
            
            // Simulate realistic statistics data
            String todayStats = "**Statistics for " + java.time.LocalDate.now() + ":**\\n" +
                "🔸 Warnings Issued: 3\\n" +
                "🦶 Kicks Performed: 1\\n" +
                "🔨 Bans Performed: 0\\n" +
                "🎫 Tickets Created: 5\\n" +
                "✅ Tickets Closed: 4\\n" +
                "🤖 Automod Actions: 12";
                
            String weeklyStats = "**Weekly Statistics (Last 7 Days):**\\n" +
                "🔸 Total Warnings: 18\\n" +
                "🦶 Total Kicks: 7\\n" +
                "🔨 Total Bans: 2\\n" +
                "🎫 Total Tickets Created: 25\\n" +
                "✅ Total Tickets Closed: 23\\n" +
                "🤖 Total Automod Actions: 89\\n\\n" +
                "**Daily Breakdown:**\\n" +
                "2024-08-31: 3 warnings, 1 kick\\n" +
                "2024-08-30: 2 warnings, 2 kicks\\n" +
                "2024-08-29: 4 warnings, 1 ban\\n" +
                "2024-08-28: 3 warnings, 3 kicks\\n" +
                "2024-08-27: 2 warnings, 0 kicks\\n" +
                "2024-08-26: 2 warnings, 0 kicks\\n" +
                "2024-08-25: 2 warnings, 1 ban";
            
            String response = String.format("{\"today\":\"%s\",\"weekly\":\"%s\"}", 
                escapeJson(todayStats), escapeJson(weeklyStats));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
        
        private String escapeJson(String str) {
            return str.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        }
    }

    private class MockLoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "Mock login - in real implementation this would redirect to Discord OAuth2";
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }

    private class MockLogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(302, 0);
        }
    }

    public static void main(String[] args) {
        try {
            TestWebServer server = new TestWebServer(8081);
            server.start();
            
            System.out.println("\n=== Discord SSO Test Server ===");
            System.out.println("Visit http://localhost:8081 to see the dashboard");
            System.out.println("This demonstrates:");
            System.out.println("- Discord SSO login UI");
            System.out.println("- Guild permission filtering");
            System.out.println("- Authenticated user interface");
            System.out.println("\nPress Ctrl+C to stop");
            
            // Keep server running
            Thread.currentThread().join();
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}