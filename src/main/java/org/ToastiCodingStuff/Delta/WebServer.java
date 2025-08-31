package org.ToastiCodingStuff.Delta;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/**
 * Simple HTTP server to serve the web dashboard for the Discord bot
 */
public class WebServer {
    private final HttpServer server;
    private final DatabaseHandler databaseHandler;
    private final int port;

    public WebServer(int port, DatabaseHandler databaseHandler) throws IOException {
        this.port = port;
        this.databaseHandler = databaseHandler;
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        setupRoutes();
        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    private void setupRoutes() {
        // Serve static files (HTML, CSS, JS)
        server.createContext("/", new StaticFileHandler());
        
        // API endpoints
        server.createContext("/api/statistics", new StatisticsHandler());
        server.createContext("/api/guilds", new GuildsHandler());
    }

    public void start() {
        server.start();
        System.out.println("Web dashboard started on http://localhost:" + port);
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

    // Handler for statistics API
    private class StatisticsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, 0);
                return;
            }
            
            // For demo purposes, we'll use a placeholder guild ID
            // In a real implementation, this would come from authentication/session
            String guildId = exchange.getRequestURI().getQuery();
            if (guildId != null && guildId.startsWith("guildId=")) {
                guildId = guildId.substring(8);
            } else {
                guildId = "demo"; // Placeholder
            }
            
            // Get statistics (this will return formatted text, we'll convert to JSON-like format)
            String todayStats = databaseHandler.getTodaysStatistics(guildId);
            String weeklyStats = databaseHandler.getWeeklyStatistics(guildId);
            
            // Simple JSON response
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

    // Handler for guilds API
    private class GuildsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, 0);
                return;
            }
            
            // Simple response for now - in real implementation would list actual guilds
            String response = "{\"guilds\":[{\"id\":\"demo\",\"name\":\"Demo Server\"}]}";
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}