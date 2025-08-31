package org.ToastiCodingStuff.Delta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Simple HTTP server to serve the web dashboard for the Discord bot
 */
public class WebServer {
    private final HttpServer server;
    private final DatabaseHandler databaseHandler;
    private final JDA jda;
    private final int port;
    private final String clientId;
    private final String clientSecret; 
    private final String redirectUri;
    private final ObjectMapper objectMapper;
    private final Map<String, UserSession> sessions;

    public WebServer(int port, DatabaseHandler databaseHandler, JDA jda) throws IOException {
        this.port = port;
        this.databaseHandler = databaseHandler;
        this.jda = jda;
        this.objectMapper = new ObjectMapper();
        this.sessions = new ConcurrentHashMap<>();
        
        // Load OAuth2 configuration
        Dotenv dotenv = Dotenv.load();
        this.clientId = dotenv.get("DISCORD_CLIENT_ID");
        this.clientSecret = dotenv.get("DISCORD_CLIENT_SECRET");
        this.redirectUri = dotenv.get("DISCORD_REDIRECT_URI", "http://localhost:8080/auth/callback");
        
        // Validate OAuth2 configuration and provide helpful feedback
        validateOAuth2Configuration();
        
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
        server.createContext("/api/user", new UserHandler());
        
        // OAuth2 endpoints
        server.createContext("/auth/login", new LoginHandler());
        server.createContext("/auth/callback", new CallbackHandler());
        server.createContext("/auth/logout", new LogoutHandler());
    }

    public void start() {
        server.start();
        System.out.println("Web dashboard started on http://localhost:" + port);
    }

    public void stop() {
        server.stop(0);
    }
    
    /**
     * Validate OAuth2 configuration and provide helpful feedback
     */
    private void validateOAuth2Configuration() {
        System.out.println("=== Discord OAuth2 Configuration Check ===");
        
        if (clientId == null || clientId.trim().isEmpty()) {
            System.err.println("❌ DISCORD_CLIENT_ID is not set in .env file");
            System.err.println("   → Please add your Discord application's Client ID to .env");
            System.err.println("   → Get it from: https://discord.com/developers/applications");
        } else {
            System.out.println("✅ DISCORD_CLIENT_ID is configured");
        }
        
        if (clientSecret == null || clientSecret.trim().isEmpty()) {
            System.err.println("❌ DISCORD_CLIENT_SECRET is not set in .env file");
            System.err.println("   → Please add your Discord application's Client Secret to .env");
            System.err.println("   → Get it from: https://discord.com/developers/applications");
        } else {
            System.out.println("✅ DISCORD_CLIENT_SECRET is configured");
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
    }
    
    // Utility method for JSON escaping
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t")
                  .replace("\\", "\\\\");
    }
    
    /**
     * Create an HTML error page for better user experience
     */
    private String createErrorPage(String title, String message, String description, String helpUrl) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>")
            .append("<html lang=\"en\">")
            .append("<head>")
            .append("<meta charset=\"UTF-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            .append("<title>").append(escapeHtml(title)).append(" - Delta Bot</title>")
            .append("<style>")
            .append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; margin: 0; padding: 2rem; background: #f7fafc; color: #2d3748; }")
            .append(".container { max-width: 600px; margin: 0 auto; background: white; border-radius: 12px; padding: 2rem; box-shadow: 0 4px 6px rgba(0,0,0,0.1); }")
            .append("h1 { color: #e53e3e; margin: 0 0 1rem 0; font-size: 1.5rem; }")
            .append("p { line-height: 1.6; margin: 1rem 0; }")
            .append(".description { background: #f7fafc; padding: 1rem; border-radius: 6px; margin: 1rem 0; }")
            .append(".help-link { color: #3182ce; text-decoration: none; }")
            .append(".help-link:hover { text-decoration: underline; }")
            .append(".back-button { display: inline-block; background: #3182ce; color: white; padding: 0.75rem 1.5rem; border-radius: 6px; text-decoration: none; margin-top: 1rem; }")
            .append(".back-button:hover { background: #2c5aa0; }")
            .append("</style>")
            .append("</head>")
            .append("<body>")
            .append("<div class=\"container\">")
            .append("<h1>").append(escapeHtml(title)).append("</h1>")
            .append("<p>").append(escapeHtml(message)).append("</p>");
        
        if (description != null) {
            html.append("<div class=\"description\">").append(escapeHtml(description)).append("</div>");
        }
        
        if (helpUrl != null) {
            html.append("<p>For more information, visit: <a href=\"").append(escapeHtml(helpUrl)).append("\" class=\"help-link\" target=\"_blank\">").append(escapeHtml(helpUrl)).append("</a></p>");
        }
        
        html.append("<a href=\"/\" class=\"back-button\">Return to Dashboard</a>")
            .append("</div>")
            .append("</body>")
            .append("</html>");
        
        return html.toString();
    }
    
    /**
     * Escape HTML special characters
     */
    private String escapeHtml(String str) {
        if (str == null) return "";
        return str.replace("&", "&amp;")
                  .replace("<", "&lt;")
                  .replace(">", "&gt;")
                  .replace("\"", "&quot;")
                  .replace("'", "&#39;");
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
                System.out.println("Resource not found: " + resourcePath);
                
                // Return 404 for missing files
                String response = createErrorPage("File Not Found", 
                    "The requested resource was not found on this server.",
                    "Path: " + path,
                    null);
                    
                exchange.getResponseHeaders().set("Content-Type", "text/html");
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
            
            // Check if user is authenticated
            String sessionToken = getSessionToken(exchange);
            UserSession session = sessionToken != null ? sessions.get(sessionToken) : null;
            
            if (session == null) {
                // Return unauthorized if not logged in
                String response = "{\"error\":\"Unauthorized\",\"loginUrl\":\"/auth/login\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }
            
            // Get user's guilds with moderate permissions
            List<GuildInfo> moderableGuilds = getUserModerableGuilds(session.userId);
            
            // Convert to JSON
            StringBuilder json = new StringBuilder("{\"guilds\":[");
            for (int i = 0; i < moderableGuilds.size(); i++) {
                if (i > 0) json.append(",");
                GuildInfo guild = moderableGuilds.get(i);
                json.append("{")
                    .append("\"id\":\"").append(escapeJson(guild.id)).append("\",")
                    .append("\"name\":\"").append(escapeJson(guild.name)).append("\",")
                    .append("\"icon\":\"").append(escapeJson(guild.iconUrl != null ? guild.iconUrl : "")).append("\"")
                    .append("}");
            }
            json.append("]}");
            
            String response = json.toString();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
    
    // Handler for user info API
    private class UserHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, 0);
                return;
            }
            
            // Check if user is authenticated
            String sessionToken = getSessionToken(exchange);
            UserSession session = sessionToken != null ? sessions.get(sessionToken) : null;
            
            if (session == null) {
                String response = "{\"error\":\"Unauthorized\"}";
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }
            
            String response = String.format("{\"id\":\"%s\",\"username\":\"%s\",\"discriminator\":\"%s\",\"avatar\":\"%s\"}", 
                escapeJson(session.userId), escapeJson(session.username), 
                escapeJson(session.discriminator), escapeJson(session.avatarUrl != null ? session.avatarUrl : ""));
            
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
    
    // Handler for Discord OAuth2 login
    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (clientId == null || clientSecret == null || clientId.trim().isEmpty() || clientSecret.trim().isEmpty()) {
                System.err.println("Login attempt failed: OAuth2 not properly configured");
                
                String response = createErrorPage("OAuth2 Not Configured", 
                    "Discord login is not properly configured on this server.",
                    "Please contact the server administrator to configure DISCORD_CLIENT_ID and DISCORD_CLIENT_SECRET in the .env file.",
                    "Configuration Help: https://discord.com/developers/applications");
                
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(500, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
                return;
            }
            
            try {
                // Generate state for CSRF protection
                String state = UUID.randomUUID().toString();
                
                // Build Discord OAuth2 URL
                String discordUrl = "https://discord.com/oauth2/authorize" +
                    "?client_id=" + clientId +
                    "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                    "&response_type=code" +
                    "&scope=" + URLEncoder.encode("identify guilds", StandardCharsets.UTF_8) +
                    "&state=" + state;
                
                // Store state for verification
                sessions.put("state_" + state, new UserSession("", "", "", "", ""));
                
                System.out.println("Redirecting user to Discord OAuth2: " + discordUrl);
                
                // Redirect to Discord
                exchange.getResponseHeaders().set("Location", discordUrl);
                exchange.sendResponseHeaders(302, 0);
                
            } catch (Exception e) {
                System.err.println("Error during login initiation: " + e.getMessage());
                e.printStackTrace();
                
                String response = createErrorPage("Login Error", 
                    "An unexpected error occurred while initiating Discord login.",
                    "Please try again later or contact the server administrator.",
                    null);
                
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(500, response.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            }
        }
    }
    
    // Handler for Discord OAuth2 callback
    private class CallbackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            System.out.println("OAuth2 callback received: " + exchange.getRequestURI());
            
            String query = exchange.getRequestURI().getQuery();
            if (query == null) {
                System.err.println("OAuth2 callback failed: No query parameters received");
                sendErrorRedirect(exchange, "No authorization code received from Discord");
                return;
            }
            
            Map<String, String> params = parseQuery(query);
            String code = params.get("code");
            String state = params.get("state");
            String error = params.get("error");
            
            // Check if Discord returned an error
            if (error != null) {
                String errorDescription = params.get("error_description");
                System.err.println("Discord OAuth2 error: " + error + 
                    (errorDescription != null ? " - " + errorDescription : ""));
                sendErrorRedirect(exchange, "Discord authorization failed: " + error);
                return;
            }
            
            if (code == null || state == null) {
                System.err.println("OAuth2 callback failed: Missing code or state parameter");
                sendErrorRedirect(exchange, "Invalid authorization response from Discord");
                return;
            }
            
            // Verify state
            if (!sessions.containsKey("state_" + state)) {
                System.err.println("OAuth2 callback failed: Invalid or expired state parameter: " + state);
                sendErrorRedirect(exchange, "Invalid or expired authorization request");
                return;
            }
            sessions.remove("state_" + state);
            
            try {
                System.out.println("Exchanging authorization code for access token");
                
                // Exchange code for access token
                String accessToken = exchangeCodeForToken(code);
                if (accessToken == null) {
                    System.err.println("OAuth2 callback failed: Could not exchange code for access token");
                    sendErrorRedirect(exchange, "Failed to obtain access token from Discord");
                    return;
                }
                
                System.out.println("Access token obtained, retrieving user info");
                
                // Get user info
                UserSession userSession = getUserInfo(accessToken);
                if (userSession == null) {
                    System.err.println("OAuth2 callback failed: Could not retrieve user info from Discord");
                    sendErrorRedirect(exchange, "Failed to retrieve user information from Discord");
                    return;
                }
                
                // Create session
                String sessionToken = UUID.randomUUID().toString();
                sessions.put(sessionToken, userSession);
                
                System.out.println("User logged in successfully: " + userSession.username + " (ID: " + userSession.userId + ")");
                
                // Set session cookie and redirect
                exchange.getResponseHeaders().set("Set-Cookie", "session=" + sessionToken + "; HttpOnly; Path=/");
                exchange.getResponseHeaders().set("Location", "/");
                exchange.sendResponseHeaders(302, 0);
                
            } catch (Exception e) {
                System.err.println("OAuth2 callback error: " + e.getMessage());
                e.printStackTrace();
                sendErrorRedirect(exchange, "An unexpected error occurred during login");
            }
        }
        
        private void sendErrorRedirect(HttpExchange exchange, String errorMessage) throws IOException {
            // Redirect to home page with error parameter
            String redirectUrl = "/?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Location", redirectUrl);
            exchange.sendResponseHeaders(302, 0);
        }
    }
    
    // Handler for logout
    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String sessionToken = getSessionToken(exchange);
            if (sessionToken != null) {
                sessions.remove(sessionToken);
            }
            
            // Clear session cookie
            exchange.getResponseHeaders().set("Set-Cookie", "session=; HttpOnly; Path=/; Max-Age=0");
            exchange.getResponseHeaders().set("Location", "/");
            exchange.sendResponseHeaders(302, 0);
        }
    }
    
    // Helper methods
    private String getSessionToken(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        
        for (String cookie : cookieHeader.split(";")) {
            String[] parts = cookie.trim().split("=", 2);
            if (parts.length == 2 && "session".equals(parts[0])) {
                return parts[1];
            }
        }
        return null;
    }
    
    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        for (String param : query.split("&")) {
            String[] parts = param.split("=", 2);
            if (parts.length == 2) {
                try {
                    params.put(parts[0], java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                } catch (Exception e) {
                    // Ignore malformed parameters
                }
            }
        }
        return params;
    }
    
    private String exchangeCodeForToken(String code) throws IOException {
        URL url = new URL("https://discord.com/api/oauth2/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);
        
        String data = "client_id=" + clientId +
                     "&client_secret=" + clientSecret +
                     "&grant_type=authorization_code" +
                     "&code=" + code +
                     "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(data.getBytes());
        }
        
        int responseCode = conn.getResponseCode();
        System.out.println("Token exchange response code: " + responseCode);
        
        if (responseCode != 200) {
            // Read error response for debugging
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                System.err.println("Token exchange failed with response: " + errorResponse.toString());
            } catch (Exception e) {
                System.err.println("Could not read error response: " + e.getMessage());
            }
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            JsonNode json = objectMapper.readTree(response.toString());
            String accessToken = json.get("access_token").asText();
            System.out.println("Successfully obtained access token");
            return accessToken;
        } catch (Exception e) {
            System.err.println("Error parsing token response: " + e.getMessage());
            return null;
        }
    }
    
    private UserSession getUserInfo(String accessToken) throws IOException {
        URL url = new URL("https://discord.com/api/v10/users/@me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        
        int responseCode = conn.getResponseCode();
        System.out.println("User info request response code: " + responseCode);
        
        if (responseCode != 200) {
            // Read error response for debugging
            try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                StringBuilder errorResponse = new StringBuilder();
                String line;
                while ((line = errorReader.readLine()) != null) {
                    errorResponse.append(line);
                }
                System.err.println("User info request failed with response: " + errorResponse.toString());
            } catch (Exception e) {
                System.err.println("Could not read error response: " + e.getMessage());
            }
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            JsonNode json = objectMapper.readTree(response.toString());
            String id = json.get("id").asText();
            String username = json.get("username").asText();
            String discriminator = json.has("discriminator") ? json.get("discriminator").asText() : "0";
            String avatar = json.has("avatar") && !json.get("avatar").isNull() ? json.get("avatar").asText() : null;
            String avatarUrl = avatar != null ? 
                "https://cdn.discordapp.com/avatars/" + id + "/" + avatar + ".png" : null;
                
            System.out.println("Successfully retrieved user info for: " + username);
            return new UserSession(id, username, discriminator, avatarUrl, accessToken);
            
        } catch (Exception e) {
            System.err.println("Error parsing user info response: " + e.getMessage());
            return null;
        }
    }
    
    private List<GuildInfo> getUserModerableGuilds(String userId) {
        List<GuildInfo> moderableGuilds = new ArrayList<>();
        
        // Get all guilds where the bot is present
        for (Guild guild : jda.getGuilds()) {
            try {
                // Check if user is in this guild
                Member member = guild.getMemberById(userId);
                if (member != null) {
                    // Check if user has moderation permissions
                    boolean hasModeratePermission = member.hasPermission(Permission.MODERATE_MEMBERS) ||
                                                  member.hasPermission(Permission.BAN_MEMBERS) ||
                                                  member.hasPermission(Permission.KICK_MEMBERS);
                    
                    if (hasModeratePermission) {
                        String iconUrl = guild.getIconUrl();
                        moderableGuilds.add(new GuildInfo(guild.getId(), guild.getName(), iconUrl));
                    }
                }
            } catch (Exception e) {
                // Skip guilds where we can't check permissions
                System.err.println("Failed to check permissions for guild " + guild.getName() + ": " + e.getMessage());
            }
        }
        
        return moderableGuilds;
    }
    
    // Helper classes
    private static class UserSession {
        final String userId;
        final String username;
        final String discriminator;
        final String avatarUrl;
        final String accessToken;
        
        UserSession(String userId, String username, String discriminator, String avatarUrl, String accessToken) {
            this.userId = userId;
            this.username = username;
            this.discriminator = discriminator;
            this.avatarUrl = avatarUrl;
            this.accessToken = accessToken;
        }
    }
    
    private static class GuildInfo {
        final String id;
        final String name;
        final String iconUrl;
        
        GuildInfo(String id, String name, String iconUrl) {
            this.id = id;
            this.name = name;
            this.iconUrl = iconUrl;
        }
    }
}