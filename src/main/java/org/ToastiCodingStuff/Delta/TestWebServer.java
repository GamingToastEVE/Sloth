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