package org.ToastiCodingStuff.Sloth;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Simple web dashboard for the Sloth Discord bot.
 * Provides a basic HTTP interface to view bot statistics and status.
 * 
 * Note: Uses com.sun.net.httpserver which is included in the JDK since Java 6.
 * While technically an internal API, it's widely used and stable for simple HTTP servers.
 */
public class WebDashboard {
    
    private final HttpServer server;
    private final JDA jda;
    private final int port;
    
    /**
     * Creates a new WebDashboard instance.
     * The server binds to localhost only for security.
     * 
     * @param jda The JDA instance for accessing Discord data
     * @param databaseHandler The database handler (reserved for future use)
     * @param port The port to run the dashboard on
     * @throws IOException If the server cannot be created
     */
    public WebDashboard(JDA jda, DatabaseHandler databaseHandler, int port) throws IOException {
        this.jda = jda;
        this.port = port;
        // Bind to localhost only for security - prevents external access
        this.server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        
        setupRoutes();
    }
    
    /**
     * Sets up HTTP routes for the dashboard.
     */
    private void setupRoutes() {
        server.createContext("/", new DashboardHandler());
        server.createContext("/api/stats", new StatsApiHandler());
        server.createContext("/api/guilds", new GuildsApiHandler());
    }
    
    /**
     * Starts the web dashboard server.
     */
    public void start() {
        server.setExecutor(null); // Use default executor
        server.start();
        System.out.println("Web Dashboard started on port " + port);
        System.out.println("Access the dashboard at http://localhost:" + port);
    }
    
    /**
     * Stops the web dashboard server.
     */
    public void stop() {
        server.stop(0);
        System.out.println("Web Dashboard stopped");
    }
    
    /**
     * Handler for the main dashboard page.
     */
    private class DashboardHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }
            
            String html = generateDashboardHtml();
            sendHtmlResponse(exchange, 200, html);
        }
    }
    
    /**
     * Handler for the stats API endpoint.
     */
    private class StatsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }
            
            String json = generateStatsJson();
            sendJsonResponse(exchange, 200, json);
        }
    }
    
    /**
     * Handler for the guilds API endpoint.
     */
    private class GuildsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equals(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed");
                return;
            }
            
            String json = generateGuildsJson();
            sendJsonResponse(exchange, 200, json);
        }
    }
    
    /**
     * Generates the main dashboard HTML page.
     */
    private String generateDashboardHtml() {
        List<Guild> guilds = jda.getGuilds();
        int totalMembers = guilds.stream().mapToInt(Guild::getMemberCount).sum();
        
        StringBuilder guildRows = new StringBuilder();
        for (Guild guild : guilds) {
            guildRows.append(String.format(
                "<tr><td>%s</td><td>%s</td><td>%d</td></tr>",
                escapeHtml(guild.getName()),
                guild.getId(),
                guild.getMemberCount()
            ));
        }
        
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html lang=\"en\">\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Sloth Bot Dashboard</title>\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("        body {\n");
        html.append("            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif;\n");
        html.append("            background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);\n");
        html.append("            color: #e0e0e0;\n");
        html.append("            min-height: 100vh;\n");
        html.append("            padding: 2rem;\n");
        html.append("        }\n");
        html.append("        .container { max-width: 1200px; margin: 0 auto; }\n");
        html.append("        header { text-align: center; margin-bottom: 2rem; }\n");
        html.append("        h1 { font-size: 2.5rem; color: #7289da; margin-bottom: 0.5rem; }\n");
        html.append("        .subtitle { color: #888; font-size: 1.1rem; }\n");
        html.append("        .stats-grid {\n");
        html.append("            display: grid;\n");
        html.append("            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));\n");
        html.append("            gap: 1.5rem;\n");
        html.append("            margin-bottom: 2rem;\n");
        html.append("        }\n");
        html.append("        .stat-card {\n");
        html.append("            background: rgba(255, 255, 255, 0.05);\n");
        html.append("            border-radius: 12px;\n");
        html.append("            padding: 1.5rem;\n");
        html.append("            text-align: center;\n");
        html.append("            border: 1px solid rgba(255, 255, 255, 0.1);\n");
        html.append("            transition: transform 0.2s, box-shadow 0.2s;\n");
        html.append("        }\n");
        html.append("        .stat-card:hover {\n");
        html.append("            transform: translateY(-5px);\n");
        html.append("            box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);\n");
        html.append("        }\n");
        html.append("        .stat-value { font-size: 2.5rem; font-weight: bold; color: #7289da; }\n");
        html.append("        .stat-label {\n");
        html.append("            color: #888;\n");
        html.append("            margin-top: 0.5rem;\n");
        html.append("            font-size: 0.9rem;\n");
        html.append("            text-transform: uppercase;\n");
        html.append("            letter-spacing: 1px;\n");
        html.append("        }\n");
        html.append("        .section {\n");
        html.append("            background: rgba(255, 255, 255, 0.05);\n");
        html.append("            border-radius: 12px;\n");
        html.append("            padding: 1.5rem;\n");
        html.append("            margin-bottom: 2rem;\n");
        html.append("            border: 1px solid rgba(255, 255, 255, 0.1);\n");
        html.append("        }\n");
        html.append("        .section h2 { color: #7289da; margin-bottom: 1rem; font-size: 1.3rem; }\n");
        html.append("        table { width: 100%; border-collapse: collapse; }\n");
        html.append("        th, td {\n");
        html.append("            padding: 0.75rem 1rem;\n");
        html.append("            text-align: left;\n");
        html.append("            border-bottom: 1px solid rgba(255, 255, 255, 0.1);\n");
        html.append("        }\n");
        html.append("        th {\n");
        html.append("            color: #7289da;\n");
        html.append("            font-weight: 600;\n");
        html.append("            text-transform: uppercase;\n");
        html.append("            font-size: 0.85rem;\n");
        html.append("            letter-spacing: 1px;\n");
        html.append("        }\n");
        html.append("        tr:hover { background: rgba(255, 255, 255, 0.05); }\n");
        html.append("        .status-online {\n");
        html.append("            display: inline-flex;\n");
        html.append("            align-items: center;\n");
        html.append("            gap: 0.5rem;\n");
        html.append("            color: #43b581;\n");
        html.append("        }\n");
        html.append("        .status-dot {\n");
        html.append("            width: 10px;\n");
        html.append("            height: 10px;\n");
        html.append("            background: #43b581;\n");
        html.append("            border-radius: 50%;\n");
        html.append("            animation: pulse 2s infinite;\n");
        html.append("        }\n");
        html.append("        @keyframes pulse {\n");
        html.append("            0%, 100% { opacity: 1; }\n");
        html.append("            50% { opacity: 0.5; }\n");
        html.append("        }\n");
        html.append("        footer {\n");
        html.append("            text-align: center;\n");
        html.append("            color: #666;\n");
        html.append("            margin-top: 2rem;\n");
        html.append("            padding-top: 1rem;\n");
        html.append("            border-top: 1px solid rgba(255, 255, 255, 0.1);\n");
        html.append("        }\n");
        html.append("        .emoji { font-size: 1.5rem; margin-bottom: 0.5rem; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"container\">\n");
        html.append("        <header>\n");
        html.append("            <h1>&#129445; Sloth Bot Dashboard</h1>\n");
        html.append("            <p class=\"subtitle\">Discord Moderation &amp; Management Bot</p>\n");
        html.append("        </header>\n");
        html.append("        \n");
        html.append("        <div class=\"stats-grid\">\n");
        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"emoji\">&#127760;</div>\n");
        html.append(String.format("                <div class=\"stat-value\">%d</div>\n", guilds.size()));
        html.append("                <div class=\"stat-label\">Servers</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"emoji\">&#128101;</div>\n");
        html.append(String.format("                <div class=\"stat-value\">%d</div>\n", totalMembers));
        html.append("                <div class=\"stat-label\">Total Members</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"emoji\">&#128225;</div>\n");
        html.append("                <div class=\"stat-value status-online\"><span class=\"status-dot\"></span> Online</div>\n");
        html.append("                <div class=\"stat-label\">Bot Status</div>\n");
        html.append("            </div>\n");
        html.append("            <div class=\"stat-card\">\n");
        html.append("                <div class=\"emoji\">&#9201;</div>\n");
        html.append(String.format("                <div class=\"stat-value\">%d ms</div>\n", jda.getGatewayPing()));
        html.append("                <div class=\"stat-label\">Gateway Ping</div>\n");
        html.append("            </div>\n");
        html.append("        </div>\n");
        html.append("        \n");
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>&#128203; Connected Servers</h2>\n");
        html.append("            <table>\n");
        html.append("                <thead>\n");
        html.append("                    <tr>\n");
        html.append("                        <th>Server Name</th>\n");
        html.append("                        <th>Server ID</th>\n");
        html.append("                        <th>Members</th>\n");
        html.append("                    </tr>\n");
        html.append("                </thead>\n");
        html.append("                <tbody>\n");
        html.append("                    ").append(guildRows.toString()).append("\n");
        html.append("                </tbody>\n");
        html.append("            </table>\n");
        html.append("        </div>\n");
        html.append("        \n");
        html.append("        <div class=\"section\">\n");
        html.append("            <h2>&#128279; API Endpoints</h2>\n");
        html.append("            <table>\n");
        html.append("                <thead>\n");
        html.append("                    <tr>\n");
        html.append("                        <th>Endpoint</th>\n");
        html.append("                        <th>Description</th>\n");
        html.append("                    </tr>\n");
        html.append("                </thead>\n");
        html.append("                <tbody>\n");
        html.append("                    <tr><td><code>/</code></td><td>This dashboard page</td></tr>\n");
        html.append("                    <tr><td><code>/api/stats</code></td><td>Bot statistics in JSON format</td></tr>\n");
        html.append("                    <tr><td><code>/api/guilds</code></td><td>List of connected guilds in JSON format</td></tr>\n");
        html.append("                </tbody>\n");
        html.append("            </table>\n");
        html.append("        </div>\n");
        html.append("        \n");
        html.append("        <footer>\n");
        html.append("            <p>Sloth Bot Dashboard &copy; 2024 | Made with &#10084;</p>\n");
        html.append("        </footer>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    /**
     * Generates JSON statistics for the API endpoint.
     */
    private String generateStatsJson() {
        List<Guild> guilds = jda.getGuilds();
        int totalMembers = guilds.stream().mapToInt(Guild::getMemberCount).sum();
        
        return String.format(
            "{\"guilds\":%d,\"totalMembers\":%d,\"gatewayPing\":%d,\"status\":\"online\"}",
            guilds.size(),
            totalMembers,
            jda.getGatewayPing()
        );
    }
    
    /**
     * Generates JSON list of guilds for the API endpoint.
     */
    private String generateGuildsJson() {
        List<Guild> guilds = jda.getGuilds();
        StringBuilder json = new StringBuilder("[");
        
        for (int i = 0; i < guilds.size(); i++) {
            Guild guild = guilds.get(i);
            json.append(String.format(
                "{\"name\":\"%s\",\"id\":\"%s\",\"memberCount\":%d}",
                escapeJson(guild.getName()),
                guild.getId(),
                guild.getMemberCount()
            ));
            if (i < guilds.size() - 1) {
                json.append(",");
            }
        }
        
        json.append("]");
        return json.toString();
    }
    
    /**
     * Sends an HTML response.
     */
    private void sendHtmlResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        sendResponse(exchange, statusCode, response);
    }
    
    /**
     * Sends a JSON response.
     */
    private void sendJsonResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        sendResponse(exchange, statusCode, response);
    }
    
    /**
     * Sends an HTTP response.
     */
    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, responseBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(responseBytes);
        }
    }
    
    /**
     * Escapes HTML special characters to prevent XSS.
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
    
    /**
     * Escapes JSON special characters.
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
