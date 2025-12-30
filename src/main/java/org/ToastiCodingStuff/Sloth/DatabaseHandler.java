package org.ToastiCodingStuff.Sloth;

import java.awt.Color;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;

public class DatabaseHandler {

    public EmbedBuilder getLifetimeModerationStatisticsEmbed(String guildId) {
        String query = "SELECT " +
                "SUM(warnings_issued) AS total_warnings, " +
                "SUM(kicks_performed) AS total_kicks, " +
                "SUM(bans_performed) AS total_bans, " +
                "SUM(timeouts_performed) AS total_timeouts, " +
                "SUM(untimeouts_performed) AS total_untimeouts, " +
                "SUM(tickets_created) AS total_tickets_created, " +
                "SUM(tickets_closed) AS total_tickets_closed, " +
                "SUM(verifications_performed) AS total_verifications " +
                "FROM statistics WHERE guild_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int totalWarnings = rs.getInt("total_warnings");
                int totalKicks = rs.getInt("total_kicks");
                int totalBans = rs.getInt("total_bans");
                int totalTimeouts = rs.getInt("total_timeouts");
                int totalUntimeouts = rs.getInt("total_untimeouts");
                int totalTicketsCreated = rs.getInt("total_tickets_created");
                int totalTicketsClosed = rs.getInt("total_tickets_closed");
                int totalVerifications = rs.getInt("total_verifications");

                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("Lifetime Moderation Statistics");
                embed.setColor(Color.BLUE);
                embed.addField("Total Warnings Issued", String.valueOf(totalWarnings), true);
                embed.addField("Total Kicks Performed", String.valueOf(totalKicks), true);
                embed.addField("Total Bans Performed", String.valueOf(totalBans), true);
                embed.addField("Total Timeouts Performed", String.valueOf(totalTimeouts), true);
                embed.addField("Total Untimeouts Performed", String.valueOf(totalUntimeouts), true);
                embed.addField("Total Tickets Created", String.valueOf(totalTicketsCreated), true);
                embed.addField("Total Tickets Closed", String.valueOf(totalTicketsClosed), true);
                embed.addField("Total Verifications Performed", String.valueOf(totalVerifications), true);

                return embed;
            } else {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("Lifetime Moderation Statistics");
                embed.setColor(Color.BLUE);
                embed.setDescription("No statistics available for this guild.");
                return embed;
            }
        }
        catch (SQLException e) {
            System.err.println("Error fetching lifetime moderation statistics: " + e.getMessage());
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("Lifetime Moderation Statistics");
            embed.setColor(Color.RED);
            embed.setDescription("An error occurred while fetching statistics.");
            return embed;
        }
    }

    /**
     * Data class to hold complete rules embed information
     */
    public static class RulesEmbedData {
        public final int id;
        public final String title;
        public final String description;
        public final String footer;
        public final String color;
        public final String roleId;
        public final String buttonLabel;
        public final String buttonEmoji;
        
        public RulesEmbedData(int id, String title, String description, String footer, String color, 
                             String roleId, String buttonLabel, String buttonEmoji) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.footer = footer;
            this.color = color;
            this.roleId = roleId;
            this.buttonLabel = buttonLabel;
            this.buttonEmoji = buttonEmoji;
        }
        
        public EmbedBuilder toEmbedBuilder() {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle(title);
            embed.setDescription(processLinebreaks(description));
            if (footer != null && !footer.isEmpty()) {
                embed.setFooter(processLinebreaks(footer));
            }
            if (color != null && !color.isEmpty()) {
                try {
                    // Handle hex colors (e.g., "#FF0000") and named colors
                    if (color.startsWith("#")) {
                        Color parsedColor = Color.decode(color);
                        embed.setColor(parsedColor);
                    } else {
                        // Handle named colors
                        switch (color.toLowerCase()) {
                            case "red": embed.setColor(Color.RED); break;
                            case "blue": embed.setColor(Color.BLUE); break;
                            case "green": embed.setColor(Color.GREEN); break;
                            case "yellow": embed.setColor(Color.YELLOW); break;
                            case "orange": embed.setColor(Color.ORANGE); break;
                            case "pink": embed.setColor(Color.PINK); break;
                            case "cyan": embed.setColor(Color.CYAN); break;
                            case "magenta": embed.setColor(Color.MAGENTA); break;
                            case "white": embed.setColor(Color.WHITE); break;
                            case "black": embed.setColor(Color.BLACK); break;
                            case "gray": case "grey": embed.setColor(Color.GRAY); break;
                            default: embed.setColor(Color.GREEN); break;
                        }
                    }
                } catch (NumberFormatException e) {
                    // If color is not valid, use default green
                    embed.setColor(Color.GREEN);
                }
            } else {
                embed.setColor(Color.GREEN);
            }
            return embed;
        }
        
        /**
         * Process literal linebreak characters in text to actual newlines for Discord
         * @param text The text to process
         * @return The text with linebreaks converted
         */
        private String processLinebreaks(String text) {
            if (text == null) return null;
            
            // Convert literal \n, \r\n, and \r to actual newlines
            return text.replace("\\n", "\n")
                      .replace("\\r\\n", "\n")  // Windows style
                      .replace("\\r", "\n");    // Mac style
        }
    }

    private final HikariDataSource dataSource;
    private final DatabaseMigrationManager migrationManager;
    
    public DatabaseHandler() {
        HikariDataSource ds = null;
        try {
            // Configure HikariCP connection pool
            String host = System.getenv().getOrDefault("DB_HOST", "localhost");
            String port = System.getenv().getOrDefault("DB_PORT", "3306");
            String database = System.getenv().getOrDefault("DB_NAME", "sloth");
            String user = System.getenv().getOrDefault("DB_USER", "root");
            String password = System.getenv().getOrDefault("DB_PASSWORD", "admin");
            
            String url = String.format("jdbc:mariadb://%s:%s/%s", host, port, database);
            System.out.println("Configuring HikariCP connection pool for MariaDB: " + url);
            
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword(password);
            config.setMaximumPoolSize(25);
            config.setMinimumIdle(2);
            config.setIdleTimeout(300000); // 5 minutes
            config.setConnectionTimeout(30000); // 30 seconds
            config.setMaxLifetime(1800000); // 30 minutes
            config.setAutoCommit(true);
            config.setPoolName("SlothDBPool");
            
            // MariaDB specific optimizations
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            
            ds = new HikariDataSource(config);
            System.out.println("Successfully created HikariCP connection pool");
            
        } catch (Exception e) {
            System.err.println("Database connection pool error: " + e.getMessage());
        }
        this.dataSource = ds;
        this.migrationManager = new DatabaseMigrationManager(this);
        initializeTables();
    }
    
    /**
     * Get a connection from the pool. Callers should use try-with-resources to ensure proper release.
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("DataSource is not initialized");
        }
        return dataSource.getConnection();
    }

    /**
     * Check if database tables already exist
     */
    private boolean tableAlreadyExist (String tableName) {
        try (Connection connection = getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            ResultSet rs = meta.getTables(null, null, tableName, null);
            if (!rs.next()) {
                return false; // If any table does not exist, return false
            }
            return true; // All tables exist
        } catch (SQLException e) {
            System.err.println("Error checking database tables: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Initialize all database tables if they don't exist and run comprehensive migrations.
     * 
     * This method implements a comprehensive database migration system that:
     * 1. Creates tables from scratch if the database is new
     * 2. Automatically detects missing columns in existing tables
     * 3. Adds missing columns while preserving existing data
     * 4. Applies indexes that may be missing
     * 5. Validates the final schema against expected definitions
     * 6. Tracks all migrations for audit purposes
     * 
     * The migration system handles:
     * - Schema evolution across software versions
     * - SQLite constraints (e.g., CURRENT_TIMESTAMP defaults)
     * - Data preservation during migrations
     * - Rollback safety (additive changes only)
     * - Migration history and performance tracking
     */
    protected void initializeTables() {
        try (Connection connection = getConnection()) {

            // Check for every table if already exist, if so apply migrations instead of full initialization
            String[] tableNames = {
                    "users", "warnings", "moderation_actions", "tickets", "ticket_messages",
                    "guild_settings", "role_permissions", "statistics", "guilds", "guild_systems", "rules_embeds_channel", "just_verify_button", "user_statistics", "role_select", "role_select_embeds", "active_timers", "role_events", "custom_embeds"
            };
            for (String tableName : tableNames) {
                if (!tableAlreadyExist(tableName)) {
                    System.out.println("Table '" + tableName + "' does not exist. Proceeding with full initialization.");
                    switch (tableName) {
                        case "users":
                            createUsersTable();
                            break;
                        case "guilds":
                            createGuildsTable();
                            break;
                        case "warnings":
                            createWarningsTable();
                            break;
                        case "moderation_actions":
                            createModerationActionsTable();
                            break;
                        case "tickets":
                            createTicketsTable();
                            break;
                        case "ticket_messages":
                            createTicketMessagesTable();
                            break;
                        case "guild_settings":
                            createGuildSettingsTable();
                            break;
                        case "statistics":
                            createStatisticsTable();
                            break;
                        case "user_statistics":
                            createUserStatisticsTable();
                            break;
                        case "rules_embeds_channel":
                            createRulesEmbedsChannel();
                            break;
                        case "just_verify_button":
                            createJustVerifyButtonTable();
                            break;
                        case "role_select":
                            createSelectRolesTable();
                            break;
                        case "role_select_embeds":
                            createSelectRolesEmbedsTable();
                            break;
                        case "active_timers":
                            createActiveTimersTable();
                            break;
                        case "role_events":
                            createRoleEventsTable();
                            break;
                        case "custom_embeds":
                            createCustomEmbedsTable();
                            break;
                    }
                    return;
                }
            }
            
            // Handle statistics table migrations (legacy compatibility)
            migrateStatisticsTable();
            
            // Create legacy tables for backward compatibility
            createLegacyTables();
            
            // Run migration check to ensure everything is up to date
            migrationManager.detectAndApplyMissingColumns();
            
            // Validate the final schema
            migrationManager.validateDatabaseSchema();
            
        } catch (SQLException e) {
            System.err.println("Error initializing database tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Apply any missing indexes for existing tables using the migration manager
     */
    private void applyMissingIndexes() {
        try {
            Map<String, DatabaseMigrationManager.TableSchema> expectedSchemas = migrationManager.getExpectedSchemas();
            
            for (Map.Entry<String, DatabaseMigrationManager.TableSchema> entry : expectedSchemas.entrySet()) {
                String tableName = entry.getKey();
                DatabaseMigrationManager.TableSchema schema = entry.getValue();
                
                try {
                    migrationManager.applyIndexes(tableName, schema);
                } catch (SQLException e) {
                    System.err.println("Error applying indexes for table '" + tableName + "': " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error applying missing indexes: " + e.getMessage());
        }
    }

    private void createSelectRolesTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS role_select (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "guild_id VARCHAR(32) NOT NULL, " +
            "role_id VARCHAR(32) NOT NULL, " +
            "label VARCHAR(64), " +
            "description VARCHAR(255), " +
            "emoji_id VARCHAR(64), " +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)";
        try (Connection connection = getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    private void createSelectRolesEmbedsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS role_select_embeds (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "guild_id VARCHAR(32) NOT NULL, " +
            "channel_id VARCHAR(32) NOT NULL, " +
            "message_id VARCHAR(32) NOT NULL, " +
            "display_type VARCHAR(32) NOT NULL DEFAULT 'BUTTON', " +
            "title VARCHAR(255) NOT NULL, " +
            "description TEXT NOT NULL, " +
            "footer TEXT, " +
            "color VARCHAR(32) DEFAULT 'blue', " +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)";
        try (Connection connection = getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    /**
     * Create guilds table - main table for Discord servers/guilds
     */
    private void createRulesEmbedsChannel() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS rules_embeds_channel (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "guild_id VARCHAR(32), " +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
            "title VARCHAR(255) NOT NULL, " +
            "description TEXT NOT NULL, " +
            "footer TEXT, " +
            "color VARCHAR(32) DEFAULT 'green', " +
            "role_id VARCHAR(32), " +
            "button_label VARCHAR(64), " +
            "button_emoji_id VARCHAR(64))";

        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }
    
    private void createGuildsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS guilds (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "name VARCHAR(255) NOT NULL, " +
            "prefix VARCHAR(16) DEFAULT '!', " +
            "language VARCHAR(8) DEFAULT 'de', " +
            "created_at DATETIME, " +
            "updated_at DATETIME, " +
            "active TINYINT(1) DEFAULT 1)";
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    /**
     * Create users table
     */
    private void createUsersTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS users (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "user_id VARCHAR(32) UNIQUE NOT NULL, " +
            "username VARCHAR(255) NOT NULL, " +
            "discriminator VARCHAR(8), " +
            "avatar VARCHAR(512), " +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP)";
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    /**
     * Create warnings table
     */
    private void createWarningsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS warnings (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "guild_id VARCHAR(32) NOT NULL, " +
                "user_id VARCHAR(32) NOT NULL, " +
                "moderator_id VARCHAR(32) NOT NULL, " +
                "reason TEXT NOT NULL, " +
                "severity VARCHAR(255), " +
                "active TINYINT(1) DEFAULT 1, " +
                "expires_at DATETIME, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)";
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    private void createJustVerifyButtonTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS just_verify_button (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "guild_id VARCHAR(32) UNIQUE NOT NULL, " +
            "role_to_give_id VARCHAR(32) NOT NULL, " +
            "role_to_remove_id VARCHAR(32), " +
            "button_label VARCHAR(64) DEFAULT 'Verify', " +
            "button_emoji_id VARCHAR(64))";
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    /**
     * Create moderation_actions table
     */
    private void createModerationActionsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS moderation_actions (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "guild_id VARCHAR(32) NOT NULL, " +
                "user_id VARCHAR(32) NOT NULL, " +
                "moderator_id VARCHAR(32) NOT NULL, " +
                "action_type VARCHAR(255)," +
                "reason TEXT NOT NULL, " +
                "duration INT, " +
                "expires_at DATETIME, " +
                "active TINYINT(1) DEFAULT 1, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)";
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }
    /**
     * Create tickets table
     */
    private void createTicketsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS tickets (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "guild_id INT NOT NULL, " +
            "user_id INT NOT NULL, " +
            "channel_id BIGINT UNIQUE, " +
            "category VARCHAR(64) DEFAULT 'general', " +
            "subject VARCHAR(255), " +
            "status VARCHAR(255) DEFAULT 'OPEN', " +
            "priority VARCHAR(255) DEFAULT 'MEDIUM', " +
            "assigned_to BIGINT(20), " +
            "closed_by BIGINT(20), " +
            "closed_reason TEXT, " +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
            "closed_at DATETIME)";
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    /**
     * Create ticket_messages table
     */
    private void createTicketMessagesTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS ticket_messages (" +
            "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
            "ticket_id INTEGER NOT NULL, " +
            "user_id INTEGER NOT NULL, " +
            "message_id INTEGER NOT NULL, " +
            "content TEXT NOT NULL, " +
            "attachments TEXT, " +
            "is_staff INTEGER DEFAULT 0, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP)";
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    /**
     * Create guild_settings table
     */
    private void createGuildSettingsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS guild_settings (" +
                "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
                "guild_id VARCHAR(32) NOT NULL UNIQUE, " +
                "modlog_channel VARCHAR(32), " +
                "warn_threshold_kick INTEGER DEFAULT 5, " +
                "warn_threshold_ban INTEGER DEFAULT 8, " +
                "warn_expire_days INTEGER DEFAULT 30, " +
                "ticket_category VARCHAR(32), " +
                "ticket_channel VARCHAR(32), " +
                "ticket_role VARCHAR(32), " +
                "ticket_transcript INTEGER DEFAULT 1, " +
                "join_role VARCHAR(32), " +
                "mute_role VARCHAR(32), " +
                "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TEXT DEFAULT CURRENT_TIMESTAMP)";
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    /**
     * Create statistics table
     */
    private void createStatisticsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS statistics (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "guild_id VARCHAR(32) NOT NULL, " +
                "user_id VARCHAR(32), " +
                "date DATE NOT NULL, " +
                "warnings_issued INT DEFAULT 0, " +
                "kicks_performed INT DEFAULT 0, " +
                "bans_performed INT DEFAULT 0, " +
                "timeouts_performed INT DEFAULT 0, " +
                "untimeouts_performed INT DEFAULT 0, " +
                "tickets_created INT DEFAULT 0, " +
                "tickets_closed INT DEFAULT 0, " +
                "verifications_performed INT DEFAULT 0, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(guild_id, user_id, date))";
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    /**
     * Create user_statistics table
     */
    private void createUserStatisticsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS user_statistics (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "guild_id VARCHAR(32) NOT NULL, " +
                "user_id VARCHAR(32) NOT NULL, " +
                "date DATE NOT NULL, " +
                "warnings_received INT DEFAULT 0, " +
                "warnings_issued INT DEFAULT 0, " +
                "kicks_received INT DEFAULT 0, " +
                "kicks_performed INT DEFAULT 0, " +
                "bans_received INT DEFAULT 0, " +
                "bans_performed INT DEFAULT 0, " +
                "timeouts_received INT DEFAULT 0, " +
                "timeouts_performed INT DEFAULT 0, " +
                "untimeouts_received INT DEFAULT 0, " +
                "untimeouts_performed INT DEFAULT 0, " +
                "tickets_created INT DEFAULT 0, " +
                "tickets_closed INT DEFAULT 0, " +
                "verifications_performed INT DEFAULT 0, " +
                "messages_sent INT DEFAULT 0, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(guild_id, user_id))";
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    /**
     * Create role_events table for the Timed Roles feature
     */
    private void createRoleEventsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS role_events (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "guild_id VARCHAR(32) NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "event_type VARCHAR(32) NOT NULL, " +
                "role_id VARCHAR(32) NOT NULL, " +
                "action_type VARCHAR(16) DEFAULT 'ADD', " +
                "duration_seconds BIGINT DEFAULT 0, " +
                "stack_type VARCHAR(16) DEFAULT 'REFRESH', " +
                "trigger_data TEXT, " +
                "active TINYINT(1) DEFAULT 1, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)";

        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    private void createActiveTimersTable () throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS active_timers (" +
                "    id INTEGER PRIMARY KEY AUTO_INCREMENT," +
                "    guild_id VARCHAR(32) NOT NULL," +
                "    user_id VARCHAR(32) NOT NULL," +
                "    role_id VARCHAR(32) NOT NULL," +
                "    expires_at DATETIME NOT NULL," +
                "    source_event_id INTEGER," +
                "    created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ");";
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    private void createCustomEmbedsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS custom_embeds (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "guild_id VARCHAR(32) NOT NULL, " +
                "name VARCHAR(100) NOT NULL, " +
                "data TEXT NOT NULL, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(guild_id, name))"; // Verhindert doppelte Namen pro Server

        try (Connection connection = getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
            System.out.println("Table 'custom_embeds' created successfully.");
        }
    }

    public boolean removeRulesEmbedFromDatabase(String guildId, String embedId) {
        String deleteQuery = "DELETE FROM rules_embeds_channel WHERE guild_id = ? AND id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(deleteQuery)) {
            pstmt.setString(1, guildId);
            pstmt.setInt(2, Integer.parseInt(embedId));
            int affectedRows = pstmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            System.err.println("Error removing rules embed from database: " + e.getMessage());
            return false;
        }
    }

    /**
     * Migrate existing statistics table to add timeout and verification columns
     * Note: This method is kept for backward compatibility. 
     * New migrations should use updateTableColumns() for a more generic approach.
     */
    private void migrateStatisticsTable() throws SQLException {
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            // Prüfe, ob die Spalten bereits existieren
            String checkColumns = "SHOW COLUMNS FROM statistics FROM sloth";
            ResultSet rs = stmt.executeQuery(checkColumns);
            boolean hasTimeouts = false;
            boolean hasUntimeouts = false;
            boolean hasVerifications = false;

            while (rs.next()) {
                String columnName = rs.getString("Field");
                if ("timeouts_performed".equals(columnName)) {
                    hasTimeouts = true;
                } else if ("untimeouts_performed".equals(columnName)) {
                    hasUntimeouts = true;
                } else if ("verifications_performed".equals(columnName)) {
                    hasVerifications = true;
                }
            }

            // Fehlende Spalten hinzufügen (MariaDB-Syntax)
            if (!hasTimeouts) {
                stmt.execute("ALTER TABLE statistics ADD COLUMN timeouts_performed INT DEFAULT 0");
            }
            if (!hasUntimeouts) {
                stmt.execute("ALTER TABLE statistics ADD COLUMN untimeouts_performed INT DEFAULT 0");
            }
            if (!hasVerifications) {
                stmt.execute("ALTER TABLE statistics ADD COLUMN verifications_performed INT DEFAULT 0");
            }
        }
    }

    /**
     * Example method demonstrating how to add a new column to an existing table.
     * This shows how developers can easily extend the database schema in future updates.
     * 
     * Usage example:
     * 
     * // To add a new feature that requires a new column:
     * // 1. Add the column to the schema definition in DatabaseMigrationManager
     * // 2. The migration system will automatically detect and add it on next startup
     * // 3. Optionally call this method to add columns manually during runtime
     */
    public boolean addNewFeatureColumn(String tableName, String columnName, String columnDefinition) {
        try {
            return addColumnIfNotExists(tableName, columnName, columnDefinition);
        } catch (SQLException e) {
            System.err.println("Error adding new feature column: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Example of adding multiple columns for a new feature.
     * This demonstrates the power of the new migration system.
     */
    public void addNewFeatureColumns() {
        System.out.println("Adding columns for new Discord bot features...");
        
        // Example: Adding user preference columns
        java.util.Map<String, String> userPreferenceColumns = new java.util.HashMap<>();
        userPreferenceColumns.put("timezone", "TEXT DEFAULT 'UTC'");
        userPreferenceColumns.put("notification_preferences", "TEXT DEFAULT 'all'");
        userPreferenceColumns.put("last_activity", "TEXT");
        
        try {
            boolean added = updateTableColumns("users", userPreferenceColumns);
            if (added) {
                System.out.println("Successfully added user preference columns!");
            } else {
                System.out.println("User preference columns already exist or no changes needed.");
            }
        } catch (SQLException e) {
            System.err.println("Error adding user preference columns: " + e.getMessage());
        }
    }
    public boolean updateTableColumns(String tableName, java.util.Map<String, String> columns) throws SQLException {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        if (columns == null || columns.isEmpty()) {
            return false; // Nichts zu aktualisieren
        }

        boolean columnsAdded = false;
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            // Prüfe existierende Spalten in der Tabelle
            String checkColumns = "SHOW COLUMNS FROM `" + tableName + "`";
            ResultSet rs = stmt.executeQuery(checkColumns);

            // Sammle existierende Spaltennamen
            java.util.Set<String> existingColumns = new java.util.HashSet<>();
            while (rs.next()) {
                existingColumns.add(rs.getString("Field")); // MariaDB verwendet "Field"
            }
            rs.close();

            // Fehlende Spalten hinzufügen
            for (java.util.Map.Entry<String, String> column : columns.entrySet()) {
                String columnName = column.getKey();
                String columnDefinition = column.getValue();

                // IDs als VARCHAR(32) oder TEXT behandeln
                if (columnName != null && (columnName.endsWith("_id") || columnName.equalsIgnoreCase("user_id") || columnName.equalsIgnoreCase("guild_id"))) {
                    if (!columnDefinition.toLowerCase().contains("varchar") && !columnDefinition.toLowerCase().contains("text")) {
                        columnDefinition = "VARCHAR(32)" + (columnDefinition.contains("DEFAULT") ? " " + columnDefinition.substring(columnDefinition.indexOf("DEFAULT")) : "");
                    }
                }

                // Spaltenname und Definition validieren
                if (columnName == null || columnName.trim().isEmpty()) {
                    throw new IllegalArgumentException("Column name cannot be null or empty");
                }
                if (columnDefinition == null || columnDefinition.trim().isEmpty()) {
                    throw new IllegalArgumentException("Column definition cannot be null or empty for column: " + columnName);
                }

                if (!existingColumns.contains(columnName)) {
                    try {
                        String alterQuery = "ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` " + columnDefinition;
                        stmt.execute(alterQuery);
                        System.out.println("Added column '" + columnName + "' to table '" + tableName + "'");
                        columnsAdded = true;
                    } catch (SQLException e) {
                        System.err.println("Error adding column '" + columnName + "' to table '" + tableName + "': " + e.getMessage());
                        throw new SQLException("Failed to add column '" + columnName + "' to table '" + tableName + "'", e);
                    }
                }
            }
        }

        return columnsAdded;
    }

    /**
     * Convenience method to add a single column to a table if it doesn't exist.
     * 
     * @param tableName The name of the table to update
     * @param columnName The name of the column to add
     * @param columnDefinition The SQL definition of the column (e.g., "INTEGER DEFAULT 0", "TEXT NOT NULL")
     * @return true if the column was added, false if it already existed
     * @throws SQLException if there's an error checking or updating the table
     */
    public boolean addColumnIfNotExists(String tableName, String columnName, String columnDefinition) throws SQLException {
        java.util.Map<String, String> columns = new java.util.HashMap<>();
        columns.put(columnName, columnDefinition);
        return updateTableColumns(tableName, columns);
    }

    /**
     * Example of how to use updateTableColumns() for statistics table migration.
     * This method demonstrates the new generic approach and can be used as a reference.
     * 
     * Additional examples:
     * 
     * // Example 1: Add multiple columns to user table
     * Map<String, String> userColumns = new HashMap<>();
     * userColumns.put("last_login", "TEXT");
     * userColumns.put("login_count", "INTEGER DEFAULT 0");
     * userColumns.put("email_verified", "INTEGER DEFAULT 0");
     * updateTableColumns("users", userColumns);
     * 
     * // Example 2: Add single column to tickets table
     * addColumnIfNotExists("tickets", "priority", "TEXT DEFAULT 'medium'");
     * 
     * // Example 3: Add multiple columns with different types
     * Map<String, String> guildColumns = new HashMap<>();
     * guildColumns.put("premium_until", "TEXT");
     * guildColumns.put("feature_flags", "INTEGER DEFAULT 0");
     * guildColumns.put("max_members", "INTEGER DEFAULT 100");
     * updateTableColumns("guilds", guildColumns);
     */
    private void migrateStatisticsTableUsingGenericFunction() throws SQLException {
        java.util.Map<String, String> columnsToAdd = new java.util.HashMap<>();
        columnsToAdd.put("timeouts_performed", "INT DEFAULT 0");
        columnsToAdd.put("untimeouts_performed", "INT DEFAULT 0");
        columnsToAdd.put("verifications_performed", "INT DEFAULT 0");

        updateTableColumns("statistics", columnsToAdd);
    }

    /**
     * Create legacy tables for backward compatibility
     */
    private void createLegacyTables() throws SQLException {
        // Erstelle log_channels Tabelle (MariaDB-Syntax, VARCHAR für IDs)
        String logChannelsTable = "CREATE TABLE IF NOT EXISTS log_channels (" +
            "guildid VARCHAR(32) PRIMARY KEY, " +
            "channelid VARCHAR(32) NOT NULL)";

        // Erstelle warn_system_settings Tabelle (MariaDB-Syntax, VARCHAR für IDs)
        String warnSystemTable = "CREATE TABLE IF NOT EXISTS warn_system_settings (" +
            "guild_id VARCHAR(32) PRIMARY KEY, " +
            "max_warns INT NOT NULL, " +
            "minutes_muted INT NOT NULL, " +
            "role_id VARCHAR(32) NOT NULL, " +
            "warn_time_hours INT NOT NULL)";

        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(logChannelsTable);
            stmt.execute(warnSystemTable);
        }
    }

    /**
     * Get the migration manager instance for advanced migration operations
     */
    public DatabaseMigrationManager getMigrationManager() {
        return migrationManager;
    }

    /**
     * Manually trigger the comprehensive migration check
     * This can be called to check for and apply any missing columns
     */
    public void runMigrationCheck() {
        try {
            System.out.println("Manually triggering migration check...");
            initializeTables();
            migrationManager.detectAndApplyMissingColumns();
            applyMissingIndexes();
            migrationManager.validateDatabaseSchema();
        } catch (SQLException e) {
            System.err.println("Error during manual migration check: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get migration history for debugging and monitoring
     */
    public java.util.List<java.util.Map<String, Object>> getMigrationHistory() {
        try {
            return migrationManager.getMigrationHistory();
        } catch (SQLException e) {
            System.err.println("Error getting migration history: " + e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    /**
     * Validate the current database schema against expected schemas
     */
    public boolean validateDatabaseSchema() {
        try {
            return migrationManager.validateDatabaseSchema();
        } catch (SQLException e) {
            System.err.println("Error validating database schema: " + e.getMessage());
            return false;
        }
    }

    public void closeConnection() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    //add Embed to Database
    public Boolean addRulesEmbedToDatabase(String guildID, String title, String description, String footer, String color, String roleId, String buttonLabel, String buttonEmoji) {
        try (Connection connection = getConnection()) {
            if (getNumberOfEmbedsInDataBase(guildID) >= 3) {
                System.out.println("Guild already has maximum rules embeds (3) in the database.");
                return false;
            }
            // MariaDB-Syntax: IDs als VARCHAR(32)
            String insertEmbed = "INSERT INTO rules_embeds_channel (guild_id, title, description, footer, color, role_id, button_label, button_emoji_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement pstmt = connection.prepareStatement(insertEmbed);
            pstmt.setString(1, guildID);
            pstmt.setString(2, title);
            pstmt.setString(3, description);
            pstmt.setString(4, footer);
            pstmt.setString(5, color);
            pstmt.setString(6, roleId);
            pstmt.setString(7, buttonLabel);
            pstmt.setString(8, buttonEmoji);
            pstmt.executeUpdate();
            System.out.println("Successfully added rules embed to database for guild: " + guildID);
        } catch (SQLException e) {
            System.err.println("Error adding rules embed to database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public int getNumberOfEmbedsInDataBase(String guildID) {
        try (Connection connection = getConnection()) {
            String query = "SELECT COUNT(*) AS count FROM rules_embeds_channel WHERE guild_id = ?";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setString(1, guildID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Abrufen der Anzahl der Embeds in der Datenbank: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    public ArrayList<RulesEmbedData> getAllRulesEmbedDataFromDatabase(String guildID) {
        try (Connection connection = getConnection()) {
            ArrayList<RulesEmbedData> embedDataList = new ArrayList<>();
            // MariaDB-Syntax: IDs als VARCHAR(32) oder TEXT
            String query = "SELECT * FROM rules_embeds_channel WHERE guild_id = ?";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setString(1, guildID);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                int id = rs.getInt("id");
                String title = rs.getString("title");
                String description = rs.getString("description");
                String footer = rs.getString("footer");
                String color = rs.getString("color");
                String roleId = rs.getString("role_id");
                String buttonLabel = rs.getString("button_label");
                String buttonEmoji = rs.getString("button_emoji_id");

                RulesEmbedData embedData = new RulesEmbedData(id, title, processLinebreaks(description), footer, color, roleId, buttonLabel, buttonEmoji);
                embedDataList.add(embedData);
            }
            return embedDataList;
        } catch (SQLException e) {
            System.err.println("Fehler beim Abrufen der Rules-Embed-Daten aus der Datenbank: " + e.getMessage());
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public String getRoleIDFromRulesEmbed(String guildID) {
        String roleId = "0";
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: IDs als VARCHAR(32) oder TEXT
            String query = "SELECT role_id FROM rules_embeds_channel WHERE guild_id = ?";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setString(1, guildID);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                roleId = rs.getString("role_id");
                if (!roleId.equals("0")) {
                    break;
                }
            }
            return roleId;
        } catch (SQLException e) {
            System.err.println("Error getting role ID from rules embed: " + e.getMessage());
            e.printStackTrace();
        }
        return roleId;
    }

    //Log Channel Databasekram

    public String getLogChannelID(String guildID) {
        try (Connection connection = getConnection()) {
            String query = "SELECT channelid FROM log_channels WHERE guildid = ?";
            PreparedStatement pstmt = connection.prepareStatement(query);
            pstmt.setString(1, guildID);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String channelid = rs.getString("channelid");
                if (channelid == null || channelid.equals("0")) {
                    return "Couldnt find a Log Channel";
                }
                return channelid;
            } else {
                return "Couldnt find a Log Channel";
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error: " + e;
        }
    }

   public boolean hasLogChannel(String guildID) {
       try (Connection connection = getConnection()) {
           String query = "SELECT channelid FROM log_channels WHERE guildid = ?";
           PreparedStatement pstmt = connection.prepareStatement(query);
           pstmt.setString(1, guildID);
           ResultSet rs = pstmt.executeQuery();
           if (rs.next()) {
               String channelid = rs.getString("channelid");
               return channelid != null && !channelid.equals("0");
           }
           return false;
       } catch (SQLException e) {
           e.printStackTrace();
           return false;
       }
   }

    public String setLogChannel (String guildID, String channelID) {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            if (hasLogChannel(guildID)) {
                String setLogChannel = "UPDATE log_channels SET channelid=? WHERE guildid=?";
                PreparedStatement updateLogChannel = connection.prepareStatement(setLogChannel);
                updateLogChannel.setString(1, channelID);
                updateLogChannel.setString(2, guildID);
                updateLogChannel.executeUpdate();
                connection.commit();
                return channelID;
            } else {
                String setLogChannel = "INSERT INTO log_channels(guildid, channelid) VALUES(?,?)";
                PreparedStatement updateLogChannel = connection.prepareStatement(setLogChannel);
                updateLogChannel.setString(1, guildID);
                updateLogChannel.setString(2, channelID);
                updateLogChannel.executeUpdate();
                connection.commit();
                return channelID;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error";
        }
    }


    //Warn System Kram
    public boolean hasWarnSystemSettings(String guildID) {
        try (Connection connection = getConnection()) {
            String query = "SELECT max_warns, minutes_muted, role_id, warn_time_hours FROM warn_system_settings WHERE guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildID);
            ResultSet rs = stmt.executeQuery();
            boolean exists = rs.next();
            rs.close();
            stmt.close();
            return exists;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getMaxWarns(String guildID) {
        try (Connection connection = getConnection()) {
            String query = "SELECT max_warns FROM warn_system_settings WHERE guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildID);
            ResultSet rs = stmt.executeQuery();
            int maxWarns = 0;
            if (rs.next()) {
                maxWarns = rs.getInt("max_warns");
            }
            rs.close();
            stmt.close();
            return maxWarns;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // Innerhalb von DatabaseHandler.java

    // 1. Eine kleine Helper-Klasse für die Daten
    public static class WarningData {
        public final int id;
        public final String reason;
        public final String moderatorId;
        public final String date;

        public WarningData(int id, String reason, String moderatorId, String date) {
            this.id = id;
            this.reason = reason;
            this.moderatorId = moderatorId;
            this.date = date;
        }
    }

    // 2. Methode zum Abrufen der aktiven Warns eines Users
    public List<WarningData> getUserActiveWarnings(String guildId, String userId) {
        List<WarningData> warnings = new ArrayList<>();
        // Wir holen nur aktive Warns
        String query = "SELECT id, reason, moderator_id, created_at FROM warnings WHERE guild_id = ? AND user_id = ? AND (active = 1 OR active IS NULL) ORDER BY created_at ASC LIMIT 25";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setString(1, guildId);
            stmt.setString(2, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    warnings.add(new WarningData(
                            rs.getInt("id"),
                            rs.getString("reason"),
                            rs.getString("moderator_id"),
                            rs.getString("created_at")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return warnings;
    }

    // 3. Methode zum Deaktivieren (Löschen) eines Warns
    public boolean deactivateWarning(int warningId, String guildId) {
        // Sicherheitscheck: guild_id prüfen, damit man keine Warns von anderen Servern löscht
        String query = "UPDATE warnings SET active = 0 WHERE id = ? AND guild_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setInt(1, warningId);
            stmt.setString(2, guildId);

            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public int getTimeMuted(String guildID) {
        try (Connection connection = getConnection()) {
            String getMinutesMutedString = "SELECT minutes_muted FROM warn_system_settings WHERE guild_id = ?";
            PreparedStatement getMinutesMutedStatement = connection.prepareStatement(getMinutesMutedString);
            getMinutesMutedStatement.setString(1, guildID);
            ResultSet rs = getMinutesMutedStatement.executeQuery();
            if (rs.next()) {
                return rs.getInt("minutes_muted");
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getWarnTimeHours(String guildID) {
        try (Connection connection = getConnection()) {
            String getWarnTimeHoursString = "SELECT warn_time_hours FROM warn_system_settings WHERE guild_id = ?";
            PreparedStatement getWarnTimeHoursStatement = connection.prepareStatement(getWarnTimeHoursString);
            getWarnTimeHoursStatement.setString(1, guildID);
            ResultSet rs = getWarnTimeHoursStatement.executeQuery();
            if (rs.next()) {
                return rs.getInt("warn_time_hours");
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getWarnRoleID(String guildID) {
        try (Connection connection = getConnection()) {
            String getRoleIDString = "SELECT role_id FROM warn_system_settings WHERE guild_id = ?";
            PreparedStatement getRoleIDStatement = connection.prepareStatement(getRoleIDString);
            getRoleIDStatement.setString(1, guildID);
            ResultSet rs = getRoleIDStatement.executeQuery();
            if (rs.next()) {
                return rs.getString("role_id");
            }
            return "0";
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setWarnSettings(String guildID, int maxWarns, int minutesMuted, String roleID, int warnTimeHours) {
        try (Connection connection = getConnection()) {
            if (hasWarnSystemSettings(guildID)) {
                String setWarnSettings1 = "UPDATE warn_system_settings SET max_warns=?, minutes_muted=?, role_id=?, warn_time_hours=? WHERE guild_id=?";
                PreparedStatement setWarnSettings2 = connection.prepareStatement(setWarnSettings1);
                setWarnSettings2.setInt(1, maxWarns);
                setWarnSettings2.setInt(2, minutesMuted);
                if (roleID != null) {
                    setWarnSettings2.setString(3, roleID);
                } else {
                    setWarnSettings2.setNull(3, java.sql.Types.VARCHAR);
                }
                setWarnSettings2.setInt(4, warnTimeHours);
                setWarnSettings2.setString(5, guildID);
                setWarnSettings2.execute();
                return;
            }
            // MariaDB-Syntax: IDs als VARCHAR(32)
            String setWarnSettings3 = "INSERT INTO warn_system_settings (guild_id, max_warns, minutes_muted, role_id, warn_time_hours) VALUES (?, ?, ?, ?, ?)";
            PreparedStatement setWarnSettings4 = connection.prepareStatement(setWarnSettings3);
            setWarnSettings4.setString(1, guildID);
            setWarnSettings4.setInt(2, maxWarns);
            setWarnSettings4.setInt(3, minutesMuted);
            if (roleID != null) {
                setWarnSettings4.setString(4, roleID);
            } else {
                setWarnSettings4.setNull(4, java.sql.Types.VARCHAR);
            }
            setWarnSettings4.setInt(5, warnTimeHours);
            setWarnSettings4.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean userInWarnTable(String guildID, String userID) {
        try (Connection connection = getConnection()) {
            String checkIfUserIsInGuildTable = "SELECT user_id FROM warnings WHERE guild_id = ? AND user_id = ?";
            PreparedStatement stmt = connection.prepareStatement(checkIfUserIsInGuildTable);
            stmt.setString(1, guildID);
            stmt.setString(2, userID);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getActiveWarningsCount(String guildID, String userID) {
        try (Connection connection = getConnection()) {
            String query = "SELECT COUNT(*) as count FROM warnings WHERE guild_id = ? AND user_id = ? AND active = 1 AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildID);
            stmt.setString(2, userID);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
            return 0;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void insertModerationAction(String guildId, String userId, String moderatorId, String actionType, String reason, Object duration, String expiresAt) {
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: IDs als VARCHAR(32) oder TEXT
            String insertAction = "INSERT INTO moderation_actions (guild_id, user_id, moderator_id, action_type, reason, duration, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertAction);
            stmt.setString(1, guildId); // VARCHAR(32) für guild_id
            stmt.setString(2, userId);  // VARCHAR(32) für user_id
            stmt.setString(3, moderatorId); // VARCHAR(32) für moderator_id
            stmt.setString(4, actionType);
            stmt.setString(5, reason);
            if (duration != null) {
                stmt.setObject(6, duration);
            } else {
                stmt.setNull(6, Types.INTEGER);
            }
            stmt.setString(7, expiresAt);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting moderation action: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public int insertWarning(String guildId, String userId, String moderatorId, String reason, String severity, String expiresAt) {
        try (Connection connection = getConnection()) {
            String insertWarning = "INSERT INTO warnings (guild_id, user_id, moderator_id, reason, severity, active, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertWarning, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.setString(3, moderatorId);
            stmt.setString(4, reason);
            stmt.setString(5, severity);
            stmt.setInt(6, 1);
            stmt.setString(7, expiresAt);
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("Error inserting warning: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    public void removeWarningTimer() {
        try (Connection connection = getConnection()) {
            String deleteTimers = "UPDATE warnings SET active = 0 WHERE expires_at <= CURRENT_TIMESTAMP AND active = 1 OR active IS NULL";
            PreparedStatement stmt = connection.prepareStatement(deleteTimers);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error removing warning timers: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void insertOrUpdateUser(String userId, String effectiveName, String discriminator, String avatarUrl) {
        try (Connection connection = getConnection()) {
            if (isUserInDatabase(userId)) {
                String updateUser = "UPDATE users SET username = ?, discriminator = ?, avatar = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                PreparedStatement updateStmt = connection.prepareStatement(updateUser);
                updateStmt.setString(1, effectiveName);
                updateStmt.setString(2, discriminator);
                updateStmt.setString(3, avatarUrl);
                updateStmt.setString(4, userId);
                updateStmt.executeUpdate();
            } else {
                String upsertUser = "INSERT INTO users (id, username, discriminator, avatar, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "username = VALUES(username), " +
                        "discriminator = VALUES(discriminator), " +
                        "avatar = VALUES(avatar), " +
                        "updated_at = CURRENT_TIMESTAMP";
                PreparedStatement stmt = connection.prepareStatement(upsertUser);
                stmt.setString(1, userId);
                stmt.setString(2, effectiveName);
                stmt.setString(3, discriminator);
                stmt.setString(4, avatarUrl);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error inserting/updating user: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean isUserInDatabase(String userId) {
        String checkUser = "SELECT id FROM users WHERE id = ?";
        try (Connection connection = getConnection()) {
            PreparedStatement stmt = connection.prepareStatement(checkUser);
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Error checking if user exists: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Insert or update guild data in the guilds table
     */
    public void insertOrUpdateGuild(String guildId, String guildName) {
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: IDs als VARCHAR(32)
            String upsertGuild = "INSERT INTO guilds (id, name, prefix, language, created_at, updated_at, active) " +
                    "VALUES (?, ?, '!', 'de', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "name = VALUES(name), " +
                    "updated_at = CURRENT_TIMESTAMP, " +
                    "active = 1";
            PreparedStatement stmt = connection.prepareStatement(upsertGuild);
            stmt.setString(1, guildId); // discord_id ist VARCHAR(32)
            stmt.setString(2, guildName);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting/updating guild: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public String getGuildPrefix(String guildId) {
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: IDs als VARCHAR(32)
            String query = "SELECT prefix FROM guilds WHERE id = ? AND active = 1";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String prefix = rs.getString("prefix");
                return prefix != null ? prefix : "!";
            }
            return "!"; // Standard-Prefix
        } catch (SQLException e) {
            System.err.println("Error getting guild prefix: " + e.getMessage());
            e.printStackTrace();
            return "!"; // Standard-Prefix bei Fehler
        }
    }

    /**
     * Sprache der Guild aus der Datenbank holen
     */
    public String getGuildLanguage(String guildId) {
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: IDs als VARCHAR(32)
            String query = "SELECT language FROM guilds WHERE id = ? AND active = 1";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String language = rs.getString("language");
                return language != null ? language : "de";
            }
            return "de"; // Standard-Sprache
        } catch (SQLException e) {
            System.err.println("Error getting guild language: " + e.getMessage());
            e.printStackTrace();
            return "de"; // Standard-Sprache bei Fehler
        }
    }

    /**
     * Update guild prefix
     */
    public boolean updateGuildPrefix(String guildId, String prefix) {
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: guildId ist VARCHAR(32)
            String updatePrefix = "UPDATE guilds SET prefix = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND active = 1";
            PreparedStatement stmt = connection.prepareStatement(updatePrefix);
            stmt.setString(1, prefix != null ? prefix : "!");
            stmt.setString(2, guildId);

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException e) {
            System.err.println("Error updating guild prefix: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update guild language
     */
    public boolean updateGuildLanguage(String guildId, String language) {
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: guildId ist VARCHAR(32)
            String updateLanguage = "UPDATE guilds SET language = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND active = 1";
            PreparedStatement stmt = connection.prepareStatement(updateLanguage);
            stmt.setString(1, language != null ? language : "de");
            stmt.setString(2, guildId);

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException e) {
            System.err.println("Error updating guild language: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Deactivate a guild when the bot leaves it
     */
    public void deactivateGuild(String guildId) {
        try (Connection connection = getConnection()) {
            String query = "UPDATE guilds SET active = 0 WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            stmt.executeUpdate();
            System.out.println("Deactivated guild: " + guildId);
        } catch (SQLException e) {
            System.err.println("Error deactivating guild: " + e.getMessage());
        }
    }

    /**
     * Sync all guilds that the bot is currently in - called on startup
     */
    public void syncGuilds(java.util.List<Guild> currentGuilds) {
        try (Connection connection = getConnection()) {
            // Transaktion starten
            connection.setAutoCommit(false);

            try {
                for (Guild guild : currentGuilds) {
                    String guildId = guild.getId();
                    String guildName = guild.getName();
                    System.out.println("Syncing guild: " + guildName + " (" + guildId + ")");

                    // INSERT ... ON DUPLICATE KEY UPDATE verwenden statt Trigger
                    String upsertQuery = "INSERT INTO guilds (id, name) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE name = ?";
                    PreparedStatement stmt = connection.prepareStatement(upsertQuery);
                    stmt.setString(1, guildId);
                    stmt.setString(2, guildName);
                    stmt.setString(3, guildName);
                    stmt.executeUpdate();
                }

                // Transaktion bestätigen
                connection.commit();
            } catch (SQLException e) {
                // Bei Fehler: Rollback
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }

        } catch (SQLException e) {
            System.err.println("Error syncing guilds: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isTicketSystem(String guildId) {
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: IDs als VARCHAR(32) oder TEXT
            String query = "SELECT ticket_category, ticket_channel FROM guild_settings WHERE guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                // Prüfe, ob ticket_category oder ticket_channel gesetzt ist (nicht null und nicht leer/"0")
                String ticketCategory = rs.getString("ticket_category");
                boolean hasCategorySet = ticketCategory != null && !ticketCategory.equals("0") && !ticketCategory.isEmpty();

                String ticketChannel = rs.getString("ticket_channel");
                boolean hasChannelSet = ticketChannel != null && !ticketChannel.equals("0") && !ticketChannel.isEmpty();

                return hasCategorySet || hasChannelSet;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Error checking ticket system status: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get ticket category ID for a guild
     */
    public String getTicketCategory(String guildId) {
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: IDs als VARCHAR(32) oder TEXT
            String query = "SELECT ticket_category FROM guild_settings WHERE guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String categoryId = rs.getString("ticket_category");
                if (categoryId != null && !categoryId.equals("0") && !categoryId.isEmpty()) {
                    return categoryId;
                }
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error getting ticket category: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get ticket channel ID for a guild
     */
    public String getTicketChannel(String guildId) {
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: IDs als VARCHAR(32) oder TEXT
            String query = "SELECT ticket_channel FROM guild_settings WHERE guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String channelId = rs.getString("ticket_channel");
                if (channelId != null && !channelId.equals("0") && !channelId.isEmpty()) {
                    return channelId;
                }
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error getting ticket channel: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Set ticket system settings for a guild
     */
    public boolean setTicketSettings(String guildId, String categoryId, String channelId, String roleId, boolean transcriptEnabled) {
        try (Connection connection = getConnection()) {
            // Zuerst prüfen, ob Einstellungen für die Guild existieren
            String checkQuery = "SELECT id FROM guild_settings WHERE guild_id = ?";
            PreparedStatement checkStmt = connection.prepareStatement(checkQuery);
            checkStmt.setString(1, guildId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                // Update bestehender Einstellungen
                String updateQuery = "UPDATE guild_settings SET ticket_category = ?, ticket_channel = ?, ticket_role = ?, ticket_transcript = ? WHERE guild_id = ?";
                PreparedStatement updateStmt = connection.prepareStatement(updateQuery);

                // IDs als VARCHAR(32) oder TEXT behandeln
                if (categoryId != null && !categoryId.isEmpty()) {
                    updateStmt.setString(1, categoryId);
                } else {
                    updateStmt.setNull(1, Types.VARCHAR);
                }

                if (channelId != null && !channelId.isEmpty()) {
                    updateStmt.setString(2, channelId);
                } else {
                    updateStmt.setNull(2, Types.VARCHAR);
                }

                if (roleId != null && !roleId.isEmpty()) {
                    updateStmt.setString(3, roleId);
                } else {
                    updateStmt.setNull(3, Types.VARCHAR);
                }

                updateStmt.setInt(4, transcriptEnabled ? 1 : 0);
                updateStmt.setString(5, guildId);

                int rowsUpdated = updateStmt.executeUpdate();
                return rowsUpdated > 0;
            } else {
                // Neue Einstellungen einfügen
                String insertQuery = "INSERT INTO guild_settings (guild_id, ticket_category, ticket_channel, ticket_role, ticket_transcript) VALUES (?, ?, ?, ?, ?)";
                PreparedStatement insertStmt = connection.prepareStatement(insertQuery);
                insertStmt.setString(1, guildId);

                if (categoryId != null && !categoryId.isEmpty()) {
                    insertStmt.setString(2, categoryId);
                } else {
                    insertStmt.setNull(2, Types.VARCHAR);
                }

                if (channelId != null && !channelId.isEmpty()) {
                    insertStmt.setString(3, channelId);
                } else {
                    insertStmt.setNull(3, Types.VARCHAR);
                }

                if (roleId != null && !roleId.isEmpty()) {
                    insertStmt.setString(4, roleId);
                } else {
                    insertStmt.setNull(4, Types.VARCHAR);
                }

                insertStmt.setInt(5, transcriptEnabled ? 1 : 0);

                int rowsInserted = insertStmt.executeUpdate();
                return rowsInserted > 0;
            }
        } catch (SQLException e) {
            System.err.println("Fehler beim Setzen der Ticket-Einstellungen: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Create a new ticket
     */
    public int createTicket(String guildId, String userId, String channelId, String category, String subject, String priority, String username, String discriminator, String avatarUrl) {
        try (Connection connection = getConnection()) {
            // Sicherstellen, dass die Guild in der Tabelle existiert (MariaDB: discord_id als VARCHAR(32))
            String checkGuildQuery = "SELECT id FROM guilds WHERE id = ?";
            PreparedStatement checkGuildStmt = connection.prepareStatement(checkGuildQuery);
            checkGuildStmt.setString(1, guildId);
            ResultSet guildResult = checkGuildStmt.executeQuery();

            if (!guildResult.next()) {
                String insertGuildQuery = "INSERT INTO guilds (id) VALUES (?)";
                PreparedStatement insertGuildStmt = connection.prepareStatement(insertGuildQuery);
                insertGuildStmt.setString(1, guildId);
                insertGuildStmt.executeUpdate();
            }

            // Sicherstellen, dass der User in der Tabelle existiert (MariaDB: id als VARCHAR(32))
            insertOrUpdateUser(userId, username, discriminator, avatarUrl);

            // Ticket einfügen (guild_id, user_id, channel_id als VARCHAR(32))
            String insertTicket = "INSERT INTO tickets (guild_id, user_id, channel_id, category, subject, priority, status) VALUES (?, ?, ?, ?, ?, ?, 'OPEN')";
            PreparedStatement stmt = connection.prepareStatement(insertTicket, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.setString(3, channelId);
            stmt.setString(4, category != null ? category : "general");
            stmt.setString(5, subject);
            stmt.setString(6, priority != null ? priority : "MEDIUM");

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1); // Gibt die generierte Ticket-ID zurück
                }
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("Error creating ticket: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Close a ticket
     */
    public boolean closeTicket(int ticketId, String closedById, String reason) {
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: IDs als VARCHAR(32) oder TEXT behandeln
            String closeTicket = "UPDATE tickets SET status = 'CLOSED', closed_by = ?, closed_reason = ?, closed_at = CURRENT_TIMESTAMP WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(closeTicket);
            stmt.setString(1, closedById); // VARCHAR(32) statt Long
            stmt.setString(2, reason);
            stmt.setInt(3, ticketId);

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException e) {
            System.err.println("Error closing ticket: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get ticket by channel ID
     */
    public String getTicketByChannelId(String channelId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT id, user_id, category, subject, status, priority, assigned_to, created_at FROM tickets WHERE channel_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, channelId); // VARCHAR(32) für channel_id (MariaDB)
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return String.format("ID: %d | User: <@%s> | Category: %s | Subject: %s | Status: %s | Priority: %s | Created: %s",
                    rs.getInt("id"), rs.getString("user_id"), rs.getString("category"),
                    rs.getString("subject"), rs.getString("status"), rs.getString("priority"),
                    rs.getString("created_at"));
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error getting ticket by channel: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get ticket role ID for a guild
     */
    public String getTicketRole(String guildId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT ticket_role FROM guild_settings WHERE guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String roleId = rs.getString("ticket_role"); // VARCHAR(32) oder TEXT für IDs
                if (roleId != null && !roleId.equals("0") && !roleId.isEmpty()) {
                    return roleId;
                }
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error getting ticket role: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public boolean assignTicket(int ticketId, String assignedToId) {
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: assigned_to als VARCHAR(32) oder TEXT behandeln
            String assignTicket = "UPDATE tickets SET assigned_to = ?, status = 'IN_PROGRESS' WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(assignTicket);
            stmt.setString(1, assignedToId);
            stmt.setInt(2, ticketId);

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException e) {
            System.err.println("Error assigning ticket: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Prüft, ob Transkripte für eine Guild aktiviert sind (MariaDB-Syntax)
     */
    public boolean areTranscriptsEnabled(String guildId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT ticket_transcript FROM guild_settings WHERE guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int transcriptEnabled = rs.getInt("ticket_transcript");
                return !rs.wasNull() && transcriptEnabled == 1;
            }
            return false; // Standard: deaktiviert, falls keine Einstellung gefunden
        } catch (SQLException e) {
            System.err.println("Error checking transcript settings: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get ticket panel title for a guild
     */
    public String getTicketTitle(String guildId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT ticket_title FROM guild_settings WHERE guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String title = rs.getString("ticket_title");
                if (title != null && !title.isEmpty()) {
                    return title;
                }
            }
            return "🎫 Create a Ticket"; // Default title
        } catch (SQLException e) {
            System.err.println("Error getting ticket title: " + e.getMessage());
            e.printStackTrace();
            return "🎫 Create a Ticket"; // Default title on error
        }
    }

    /**
     * Get ticket panel description for a guild
     */
    public String getTicketDescription(String guildId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT ticket_description FROM guild_settings WHERE guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String description = rs.getString("ticket_description");
                if (description != null && !description.isEmpty()) {
                    return description;
                }
            }
            return "Need help or have a question? Click the button below to create a ticket!\n\nOur support team will assist you as soon as possible."; // Default description
        } catch (SQLException e) {
            System.err.println("Error getting ticket description: " + e.getMessage());
            e.printStackTrace();
            return "Need help or have a question? Click the button below to create a ticket!\n\nOur support team will assist you as soon as possible."; // Default description on error
        }
    }

    /**
     * Set ticket panel title and description for a guild
     */
    public boolean setTicketConfig(String guildId, String title, String description) {
        try (Connection connection = getConnection()) {
            // Check if settings exist for the guild
            String checkQuery = "SELECT id FROM guild_settings WHERE guild_id = ?";
            PreparedStatement checkStmt = connection.prepareStatement(checkQuery);
            checkStmt.setString(1, guildId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next()) {
                // Update existing settings
                String updateQuery = "UPDATE guild_settings SET ticket_title = ?, ticket_description = ? WHERE guild_id = ?";
                PreparedStatement updateStmt = connection.prepareStatement(updateQuery);
                updateStmt.setString(1, title);
                updateStmt.setString(2, description);
                updateStmt.setString(3, guildId);

                int rowsUpdated = updateStmt.executeUpdate();
                return rowsUpdated > 0;
            } else {
                // Insert new settings
                String insertQuery = "INSERT INTO guild_settings (guild_id, ticket_title, ticket_description) VALUES (?, ?, ?)";
                PreparedStatement insertStmt = connection.prepareStatement(insertQuery);
                insertStmt.setString(1, guildId);
                insertStmt.setString(2, title);
                insertStmt.setString(3, description);

                int rowsInserted = insertStmt.executeUpdate();
                return rowsInserted > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error setting ticket config: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Ticket-Priorität aktualisieren (MariaDB-Syntax)
     */
    public boolean updateTicketPriority(int ticketId, String priority) {
        try (Connection connection = getConnection()) {
            String updatePriority = "UPDATE tickets SET priority = ? WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(updatePriority);
            stmt.setString(1, priority);
            stmt.setInt(2, ticketId);

            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException e) {
            System.err.println("Error updating ticket priority: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get tickets by guild with channel ID and priority for sorting
     */
    public java.util.List<java.util.Map<String, String>> getTicketsByGuildWithPriority(String guildId) {
        java.util.List<java.util.Map<String, String>> tickets = new java.util.ArrayList<>();
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: IDs als VARCHAR(32) oder TEXT behandeln
            String query = "SELECT channel_id, priority FROM tickets WHERE guild_id = ? AND status IN ('OPEN', 'IN_PROGRESS') ORDER BY " +
                    "CASE priority " +
                    "WHEN 'URGENT' THEN 1 " +
                    "WHEN 'HIGH' THEN 2 " +
                    "WHEN 'MEDIUM' THEN 3 " +
                    "WHEN 'LOW' THEN 4 " +
                    "ELSE 5 END";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                java.util.Map<String, String> ticket = new java.util.HashMap<>();
                // channel_id als VARCHAR(32) oder TEXT behandeln
                ticket.put("channel_id", rs.getString("channel_id"));
                ticket.put("priority", rs.getString("priority"));
                tickets.add(ticket);
            }
        } catch (SQLException e) {
            System.err.println("Error getting tickets by guild: " + e.getMessage());
            e.printStackTrace();
        }
        return tickets;
    }

    // Statistics management methods

    /**
     * Get current date in YYYY-MM-DD format for statistics
     */
    public String getCurrentDate() {
        return java.time.LocalDate.now().toString();
    }

    /**
     * Update statistics for a guild and specific action type
     */
    private void updateStatistics(String guildId, String actionType) {
        try (Connection connection = getConnection()) {
            String currentDate = getCurrentDate();

            // Spaltennamen validieren (nur erlaubte Aktionen zulassen)
            java.util.Set<String> allowedActions = java.util.Set.of(
                "messages_sent", "commands_used", "timeouts_performed", "untimeouts_performed", "verifications_performed", "untimeouts_received", "timeouts_received",
                "bans_performed", "bans_received", "kicks_performed", "kicks_received", "warnings_issued", "warnings_received",
                "tickets_created", "tickets_closed"
            );
            if (!allowedActions.contains(actionType)) {
                throw new IllegalArgumentException("Ungültiger Spaltenname für Statistik: " + actionType);
            }

            // MariaDB-Syntax: guild_id als VARCHAR(32)
            if (guildExistsInStatisticsTable(guildId, currentDate)) {
                String insertGuildQuery = "UPDATE statistics SET " + actionType + " = " + actionType + " WHERE guild_id = ? AND date = ?";
                PreparedStatement insertGuildStmt = connection.prepareStatement(insertGuildQuery);
                insertGuildStmt.setString(1, guildId);
                insertGuildStmt.setString(2, currentDate);
                insertGuildStmt.executeUpdate();
                return;
            }

            // Wenn kein Datensatz existiert, neuen einfügen
            String insertQuery = "INSERT INTO statistics (guild_id, date, " + actionType + ") VALUES (?, ?, ?)";
            PreparedStatement insertStmt = connection.prepareStatement(insertQuery);
            insertStmt.setString(1, guildId);
            insertStmt.setString(2, currentDate);
            insertStmt.setInt(3, 1);
            insertStmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating statistics: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean guildExistsInStatisticsTable(String guildId, String currentDate) {
        try (Connection connection = getConnection()) {
            String checkQuery = "SELECT guild_id FROM statistics WHERE guild_id = ? AND date = ?";
            PreparedStatement checkStmt = connection.prepareStatement(checkQuery);
            checkStmt.setString(1, guildId);
            checkStmt.setString(2, currentDate);
            ResultSet rs = checkStmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Error checking guild in statistics table: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Increment warnings issued count for a guild
     */
    public void incrementWarningsIssued(String guildId) {
        updateStatistics(guildId, "warnings_issued");
    }

    /**
     * Increment bans performed count for a guild
     */
    public void incrementBansPerformed(String guildId) {
        updateStatistics(guildId, "bans_performed");
    }

    /**
     * Increment kicks performed count for a guild
     */
    public void incrementKicksPerformed(String guildId) {
        updateStatistics(guildId, "kicks_performed");
    }

    /**
     * Increment timeouts performed count for a guild
     */
    public void incrementTimeoutsPerformed(String guildId) {
        updateStatistics(guildId, "timeouts_performed");
    }

    /**
     * Increment untimeouts performed count for a guild
     */
    public void incrementUntimeoutsPerformed(String guildId) {
        updateStatistics(guildId, "untimeouts_performed");
    }

    /**
     * Increment tickets created count for a guild
     */
    public void incrementTicketsCreated(String guildId) {
        updateStatistics(guildId, "tickets_created");
    }

    /**
     * Increment tickets closed count for a guild
     */
    public void incrementTicketsClosed(String guildId) {
        updateStatistics(guildId, "tickets_closed");
    }

    /**
     * Increment verifications performed count for a guild
     */
    public void incrementVerificationsPerformed(String guildId) {
        updateStatistics(guildId, "verifications_performed");
    }

    // GLOBAL STATISTICS FUNCTIONS

    /**
     * Insert or update global command statistics
     * This method tracks how many times each command has been used globally
     */
    public void insertOrUpdateGlobalStatistic(String command) {
        try (Connection connection = getConnection()) {
            // Try to update existing record
            String updateQuery = "UPDATE global_statistics SET number = number + 1, last_used = ? WHERE command = ?";
            PreparedStatement updateStmt = connection.prepareStatement(updateQuery);
            updateStmt.setString(1, getCurrentDate());
            updateStmt.setString(2, command);
            
            int rowsUpdated = updateStmt.executeUpdate();

            // If no record exists, insert new one
            if (rowsUpdated == 0) {
                String insertQuery = "INSERT INTO global_statistics (command, number, last_used) VALUES (?, ?, ?)";
                PreparedStatement insertStmt = connection.prepareStatement(insertQuery);
                insertStmt.setString(1, command);
                insertStmt.setInt(2, 1);
                insertStmt.setString(3, getCurrentDate());
                insertStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error updating global statistics: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // USER STATISTICS FUNCTIONS

    /**
     * Update statistics for a user and specific action type
     */
    private void updateUserStatistics(String guildId, String userId, String actionType) {
        try (Connection connection = getConnection()) {
            String currentDate = getCurrentDate();

            java.util.Set<String> allowedActions = java.util.Set.of(
                    "messages_sent", "commands_used", "timeouts_performed", "untimeouts_performed",
                    "verifications_performed", "untimeouts_received", "timeouts_received",
                    "bans_performed", "bans_received", "kicks_performed", "kicks_received",
                    "warnings_issued", "warnings_received", "tickets_created", "tickets_closed"
            );

            if (!allowedActions.contains(actionType)) {
                throw new IllegalArgumentException("Ungültiger Spaltenname für Statistik: " + actionType);
            }

            int currentNumber = 0;
            String selectQuery = "SELECT " + actionType + " FROM user_statistics WHERE guild_id = ? AND user_id = ? AND date = ?";
            PreparedStatement selectStmt = connection.prepareStatement(selectQuery);
            selectStmt.setString(1, guildId);
            selectStmt.setString(2, userId);
            selectStmt.setString(3, currentDate);
            ResultSet rs = selectStmt.executeQuery();
            if (rs.next()) {
                currentNumber = rs.getInt(actionType);
            }

            // UPDATE versuchen
            String updateQuery = null;
            if (userExistsInUserStatistics(guildId, userId)) {
                updateQuery = "UPDATE user_statistics SET " + actionType + " = " + currentNumber +
                        " + ? WHERE guild_id = ? AND user_id = ? AND date = ?";
            } else {
                updateQuery = "INSERT INTO user_statistics (" + actionType + ", guild_id, user_id, date) VALUES (?, ?, ?, ?)";
            }
            PreparedStatement updateStmt = connection.prepareStatement(updateQuery);
            updateStmt.setInt(1, 1);
            updateStmt.setString(2, guildId);
            updateStmt.setString(3, userId);
            updateStmt.setString(4, currentDate);

            int rowsUpdated = updateStmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating user statistics: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean userExistsInUserStatistics(String guildId, String userId) {
        String currentDate = getCurrentDate();
        String checkQuery = "SELECT id FROM user_statistics WHERE guild_id = ? AND user_id = ? AND date = ?";
        try (Connection connection = getConnection()) {
            PreparedStatement checkStmt = connection.prepareStatement(checkQuery);
            checkStmt.setString(1, guildId);
            checkStmt.setString(2, userId);
            checkStmt.setString(3, currentDate);
            ResultSet rs = checkStmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Error checking user in user_statistics table: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Increment warnings received count for a user
     */
    public void incrementUserWarningsReceived(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "warnings_received");
    }

    /**
     * Increment warnings issued count for a user
     */
    public void incrementUserWarningsIssued(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "warnings_issued");
    }

    /**
     * Increment kicks received count for a user
     */
    public void incrementUserKicksReceived(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "kicks_received");
    }

    /**
     * Increment kicks performed count for a user
     */
    public void incrementUserKicksPerformed(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "kicks_performed");
    }

    /**
     * Increment bans received count for a user
     */
    public void incrementUserBansReceived(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "bans_received");
    }

    /**
     * Increment bans performed count for a user
     */
    public void incrementUserBansPerformed(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "bans_performed");
    }

    /**
     * Increment timeouts received count for a user
     */
    public void incrementUserTimeoutsReceived(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "timeouts_received");
    }

    /**
     * Increment timeouts performed count for a user
     */
    public void incrementUserTimeoutsPerformed(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "timeouts_performed");
    }

    /**
     * Increment untimeouts received count for a user
     */
    public void incrementUserUntimeoutsReceived(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "untimeouts_received");
    }

    /**
     * Increment untimeouts performed count for a user
     */
    public void incrementUserUntimeoutsPerformed(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "untimeouts_performed");
    }

    /**
     * Increment tickets created count for a user
     */
    public void incrementUserTicketsCreated(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "tickets_created");
    }

    /**
     * Increment tickets closed count for a user
     */
    public void incrementUserTicketsClosed(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "tickets_closed");
    }

    /**
     * Increment verifications performed count for a user
     */
    public void incrementUserVerificationsPerformed(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "verifications_performed");
    }

    /**
     * Increment messages sent count for a user
     */
    public void incrementUserMessagesSent(String guildId, String userId) {
        updateUserStatistics(guildId, userId, "messages_sent");
    }

    /**
     * Get user information and statistics embed
     */
    public EmbedBuilder getUserInfoEmbed(String guildId, String userId) {
        try (Connection connection = getConnection()) {
            // Hole Benutzerinformationen (MariaDB: id als VARCHAR(32))
            String userQuery = "SELECT username, discriminator, avatar, created_at FROM users WHERE id = ?";
            PreparedStatement userStmt = connection.prepareStatement(userQuery);
            userStmt.setString(1, userId);
            ResultSet userRs = userStmt.executeQuery();

            if (!userRs.next()) {
                return new EmbedBuilder()
                    .setTitle("❌ User Not Found")
                    .setDescription("User information not available in database.")
                    .setColor(Color.RED);
            }

            String username = userRs.getString("username");
            String discriminator = userRs.getString("discriminator");
            String avatar = userRs.getString("avatar");
            String createdAt = userRs.getString("created_at");

            // Erstelle Embed mit Benutzerinfo
            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👤 User Information: " + username + (discriminator != null ? "#" + discriminator : ""))
                .setColor(Color.BLUE)
                .setTimestamp(java.time.Instant.now());

            if (avatar != null && !avatar.isEmpty()) {
                embed.setThumbnail(avatar);
            }

            // Füge Basisinfos hinzu
            embed.addField("📅 Joined Database", createdAt != null ? createdAt : "Unknown", true);
            embed.addField("🆔 User ID", userId, true);

            // Hole Benutzerstatistiken (MariaDB: guild_id, user_id als VARCHAR(32))
            String statsQuery = "SELECT " +
                "SUM(warnings_received) as total_warnings_received, " +
                "SUM(warnings_issued) as total_warnings_issued, " +
                "SUM(kicks_received) as total_kicks_received, " +
                "SUM(kicks_performed) as total_kicks_performed, " +
                "SUM(bans_received) as total_bans_received, " +
                "SUM(bans_performed) as total_bans_performed, " +
                "SUM(timeouts_received) as total_timeouts_received, " +
                "SUM(timeouts_performed) as total_timeouts_performed, " +
                "SUM(untimeouts_received) as total_untimeouts_received, " +
                "SUM(untimeouts_performed) as total_untimeouts_performed, " +
                "SUM(tickets_created) as total_tickets_created, " +
                "SUM(tickets_closed) as total_tickets_closed, " +
                "SUM(verifications_performed) as total_verifications_performed, " +
                "SUM(messages_sent) as total_messages_sent " +
                "FROM user_statistics WHERE guild_id = ? AND user_id = ?";
            PreparedStatement statsStmt = connection.prepareStatement(statsQuery);
            statsStmt.setString(1, guildId);
            statsStmt.setString(2, userId);
            ResultSet statsRs = statsStmt.executeQuery();

            if (statsRs.next()) {
                StringBuilder moderationStats = new StringBuilder();
                StringBuilder activityStats = new StringBuilder();

                // Moderation erhalten
                int warningsReceived = statsRs.getInt("total_warnings_received");
                int kicksReceived = statsRs.getInt("total_kicks_received");
                int bansReceived = statsRs.getInt("total_bans_received");
                int timeoutsReceived = statsRs.getInt("total_timeouts_received");
                int untimeoutsReceived = statsRs.getInt("total_untimeouts_received");

                if (warningsReceived > 0 || kicksReceived > 0 || bansReceived > 0 || timeoutsReceived > 0 || untimeoutsReceived > 0) {
                    moderationStats.append("**Moderation Received:**\n");
                    if (warningsReceived > 0) moderationStats.append("⚠️ Warnings: ").append(warningsReceived).append("\n");
                    if (kicksReceived > 0) moderationStats.append("🦶 Kicks: ").append(kicksReceived).append("\n");
                    if (bansReceived > 0) moderationStats.append("🔨 Bans: ").append(bansReceived).append("\n");
                    if (timeoutsReceived > 0) moderationStats.append("⏱️ Timeouts: ").append(timeoutsReceived).append("\n");
                    if (untimeoutsReceived > 0) moderationStats.append("⏰ Untimeouts: ").append(untimeoutsReceived).append("\n");
                }

                // Moderation durchgeführt
                int warningsIssued = statsRs.getInt("total_warnings_issued");
                int kicksPerformed = statsRs.getInt("total_kicks_performed");
                int bansPerformed = statsRs.getInt("total_bans_performed");
                int timeoutsPerformed = statsRs.getInt("total_timeouts_performed");
                int untimeoutsPerformed = statsRs.getInt("total_untimeouts_performed");

                if (warningsIssued > 0 || kicksPerformed > 0 || bansPerformed > 0 || timeoutsPerformed > 0 || untimeoutsPerformed > 0) {
                    if (moderationStats.length() > 0) moderationStats.append("\n");
                    moderationStats.append("**Moderation Performed:**\n");
                    if (warningsIssued > 0) moderationStats.append("⚠️ Warnings Issued: ").append(warningsIssued).append("\n");
                    if (kicksPerformed > 0) moderationStats.append("🦶 Kicks Performed: ").append(kicksPerformed).append("\n");
                    if (bansPerformed > 0) moderationStats.append("🔨 Bans Performed: ").append(bansPerformed).append("\n");
                    if (timeoutsPerformed > 0) moderationStats.append("⏱️ Timeouts Performed: ").append(timeoutsPerformed).append("\n");
                    if (untimeoutsPerformed > 0) moderationStats.append("⏰ Untimeouts Performed: ").append(untimeoutsPerformed).append("\n");
                }

                // Aktivitätsstatistiken
                int ticketsCreated = statsRs.getInt("total_tickets_created");
                int ticketsClosed = statsRs.getInt("total_tickets_closed");
                int verificationsPerformed = statsRs.getInt("total_verifications_performed");
                int messagesSent = statsRs.getInt("total_messages_sent");

                if (ticketsCreated > 0 || ticketsClosed > 0 || verificationsPerformed > 0 || messagesSent > 0) {
                    activityStats.append("**Activity Stats:**\n");
                    if (ticketsCreated > 0) activityStats.append("🎫 Tickets Created: ").append(ticketsCreated).append("\n");
                    if (ticketsClosed > 0) activityStats.append("✅ Tickets Closed: ").append(ticketsClosed).append("\n");
                    if (verificationsPerformed > 0) activityStats.append("✅ Verifications: ").append(verificationsPerformed).append("\n");
                    if (messagesSent > 0) activityStats.append("💬 Messages Sent: ").append(messagesSent).append("\n");
                }

                // Felder zum Embed hinzufügen
                if (moderationStats.length() > 0) {
                    embed.addField("📊 Moderation Statistics", moderationStats.toString(), false);
                }
                if (activityStats.length() > 0) {
                    embed.addField("📈 Activity Statistics", activityStats.toString(), false);
                }

                if (moderationStats.length() == 0 && activityStats.length() == 0) {
                    embed.addField("📊 Statistics", "No activity recorded yet.", false);
                }
            } else {
                embed.addField("📊 Statistics", "No activity recorded yet.", false);
            }

            return embed;

        } catch (SQLException e) {
            System.err.println("Error getting user info: " + e.getMessage());
            e.printStackTrace();
            return new EmbedBuilder()
                .setTitle("❌ Error")
                .setDescription("Failed to retrieve user information.")
                .setColor(Color.RED);
        }
    }

    /**
     * Get user statistics for a specific date
     */
    public EmbedBuilder getUserStatisticsForDateEmbed(String guildId, String userId, String date) {
        try (Connection connection = getConnection()) {
            // Hole Benutzerinformationen (MariaDB: id als VARCHAR(32) oder TEXT)
            String userQuery = "SELECT username, discriminator FROM users WHERE id = ?";
            PreparedStatement userStmt = connection.prepareStatement(userQuery);
            userStmt.setString(1, userId);
            ResultSet userRs = userStmt.executeQuery();

            String displayName = "Unknown User";
            if (userRs.next()) {
                String username = userRs.getString("username");
                String discriminator = userRs.getString("discriminator");
                displayName = username + (discriminator != null ? "#" + discriminator : "");
            }

            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 User Statistics for " + date)
                .setDescription("Statistics for " + displayName)
                .setColor(Color.BLUE)
                .setTimestamp(java.time.Instant.now());

            // Hole Statistiken für das spezifische Datum (MariaDB: guild_id, user_id als VARCHAR(32) oder TEXT)
            String statsQuery = "SELECT * FROM user_statistics WHERE guild_id = ? AND user_id = ? AND date = ?";
            PreparedStatement statsStmt = connection.prepareStatement(statsQuery);
            statsStmt.setString(1, guildId);
            statsStmt.setString(2, userId);
            statsStmt.setString(3, date);
            ResultSet statsRs = statsStmt.executeQuery();

            if (statsRs.next()) {
                StringBuilder stats = new StringBuilder();

                // Prüfe jede Statistik und füge sie hinzu, wenn > 0
                if (statsRs.getInt("warnings_received") > 0) stats.append("⚠️ Warnings Received: ").append(statsRs.getInt("warnings_received")).append("\n");
                if (statsRs.getInt("warnings_issued") > 0) stats.append("⚠️ Warnings Issued: ").append(statsRs.getInt("warnings_issued")).append("\n");
                if (statsRs.getInt("kicks_received") > 0) stats.append("🦶 Kicks Received: ").append(statsRs.getInt("kicks_received")).append("\n");
                if (statsRs.getInt("kicks_performed") > 0) stats.append("🦶 Kicks Performed: ").append(statsRs.getInt("kicks_performed")).append("\n");
                if (statsRs.getInt("bans_received") > 0) stats.append("🔨 Bans Received: ").append(statsRs.getInt("bans_received")).append("\n");
                if (statsRs.getInt("bans_performed") > 0) stats.append("🔨 Bans Performed: ").append(statsRs.getInt("bans_performed")).append("\n");
                if (statsRs.getInt("timeouts_received") > 0) stats.append("⏱️ Timeouts Received: ").append(statsRs.getInt("timeouts_received")).append("\n");
                if (statsRs.getInt("timeouts_performed") > 0) stats.append("⏱️ Timeouts Performed: ").append(statsRs.getInt("timeouts_performed")).append("\n");
                if (statsRs.getInt("untimeouts_received") > 0) stats.append("⏰ Untimeouts Received: ").append(statsRs.getInt("untimeouts_received")).append("\n");
                if (statsRs.getInt("untimeouts_performed") > 0) stats.append("⏰ Untimeouts Performed: ").append(statsRs.getInt("untimeouts_performed")).append("\n");
                if (statsRs.getInt("tickets_created") > 0) stats.append("🎫 Tickets Created: ").append(statsRs.getInt("tickets_created")).append("\n");
                if (statsRs.getInt("tickets_closed") > 0) stats.append("✅ Tickets Closed: ").append(statsRs.getInt("tickets_closed")).append("\n");
                if (statsRs.getInt("verifications_performed") > 0) stats.append("✅ Verifications: ").append(statsRs.getInt("verifications_performed")).append("\n");
                if (statsRs.getInt("messages_sent") > 0) stats.append("💬 Messages Sent: ").append(statsRs.getInt("messages_sent")).append("\n");

                if (stats.length() > 0) {
                    embed.addField("📈 Daily Activity", stats.toString(), false);
                } else {
                    embed.addField("📈 Daily Activity", "No activity recorded for this date.", false);
                }
            } else {
                embed.addField("📈 Daily Activity", "No activity recorded for this date.", false);
            }

            return embed;

        } catch (SQLException e) {
            System.err.println("Error getting user statistics for date: " + e.getMessage());
            e.printStackTrace();
            return new EmbedBuilder()
                .setTitle("❌ Error")
                .setDescription("Failed to retrieve user statistics.")
                .setColor(Color.RED);
        }
    }

    /**
     * Get statistics for a guild for a specific date
     */
    public String getStatisticsForDate(String guildId, String date) {
        try (Connection connection = getConnection()) {
            // MariaDB-Syntax: guild_id als VARCHAR(32) oder TEXT behandeln
            String query = "SELECT warnings_issued, kicks_performed, bans_performed, timeouts_performed, untimeouts_performed, tickets_created, tickets_closed " +
                    "FROM statistics WHERE guild_id = ? AND date = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId); // guild_id als VARCHAR(32) oder TEXT
            stmt.setString(2, date);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                StringBuilder result = new StringBuilder();
                result.append("**Statistics for ").append(date).append(":**\n");
                result.append("🔸 Warnings Issued: ").append(rs.getInt("warnings_issued")).append("\n");
                result.append("🦶 Kicks Performed: ").append(rs.getInt("kicks_performed")).append("\n");
                result.append("🔨 Bans Performed: ").append(rs.getInt("bans_performed")).append("\n");
                result.append("⏱️ Timeouts Performed: ").append(rs.getInt("timeouts_performed")).append("\n");
                result.append("⏰ Untimeouts Performed: ").append(rs.getInt("untimeouts_performed")).append("\n");
                result.append("🎫 Tickets Created: ").append(rs.getInt("tickets_created")).append("\n");
                result.append("✅ Tickets Closed: ").append(rs.getInt("tickets_closed"));
                return result.toString();
            } else {
                return "No statistics found for " + date + ".";
            }
        } catch (SQLException e) {
            System.err.println("Error getting statistics: " + e.getMessage());
            e.printStackTrace();
            return "Error retrieving statistics.";
        }
    }

    /**
     * Get statistics for a guild for today
     */
    public String getTodaysStatistics(String guildId) {
        return getStatisticsForDate(guildId, getCurrentDate());
    }

    /**
     * Get statistics for a guild for the last 7 days
     */
    public String getWeeklyStatistics(String guildId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT date, warnings_issued, kicks_performed, bans_performed, timeouts_performed, untimeouts_performed, tickets_created, tickets_closed " +
                    "FROM statistics WHERE guild_id = ? AND date >= date('now', '-7 days') ORDER BY date DESC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            
            StringBuilder result = new StringBuilder();
            result.append("**Weekly Statistics (Last 7 Days):**\n");
            
            int totalWarnings = 0, totalKicks = 0, totalBans = 0, totalTimeouts = 0, totalUntimeouts = 0, totalTicketsCreated = 0, totalTicketsClosed = 0;
            boolean hasData = false;
            
            while (rs.next()) {
                hasData = true;
                String date = rs.getString("date");
                int warnings = rs.getInt("warnings_issued");
                int kicks = rs.getInt("kicks_performed");
                int bans = rs.getInt("bans_performed");
                int timeouts = rs.getInt("timeouts_performed");
                int untimeouts = rs.getInt("untimeouts_performed");
                int ticketsCreated = rs.getInt("tickets_created");
                int ticketsClosed = rs.getInt("tickets_closed");
                
                totalWarnings += warnings;
                totalKicks += kicks;
                totalBans += bans;
                totalTimeouts += timeouts;
                totalUntimeouts += untimeouts;
                totalTicketsCreated += ticketsCreated;
                totalTicketsClosed += ticketsClosed;
                
                result.append("\n**").append(date).append(":**\n");
                result.append("🔸 ").append(warnings).append(" | 🦶 ").append(kicks).append(" | 🔨 ").append(bans);
                result.append(" | ⏱️ ").append(timeouts).append(" | ⏰ ").append(untimeouts);
                result.append(" | 🎫 ").append(ticketsCreated).append(" | ✅ ").append(ticketsClosed);
            }
            
            if (hasData) {
                result.append("\n\n**Weekly Totals:**\n");
                result.append("🔸 Warnings: ").append(totalWarnings).append("\n");
                result.append("🦶 Kicks: ").append(totalKicks).append("\n");
                result.append("🔨 Bans: ").append(totalBans).append("\n");
                result.append("⏱️ Timeouts: ").append(totalTimeouts).append("\n");
                result.append("⏰ Untimeouts: ").append(totalUntimeouts).append("\n");
                result.append("🎫 Tickets Created: ").append(totalTicketsCreated).append("\n");
                result.append("✅ Tickets Closed: ").append(totalTicketsClosed);
                return result.toString();
            } else {
                return "No statistics found for the last 7 days.";
            }
        } catch (SQLException e) {
            System.err.println("Error getting weekly statistics: " + e.getMessage());
            e.printStackTrace();
            return "Error retrieving weekly statistics.";
        }
    }

    public String processLinebreaks(String text) {
        if (text == null) return null;

        // Convert literal \n, \r\n, and \r to actual newlines
        return text.replace("\\n", "\n")
                .replace("\\r\\n", "\n")  // Windows style
                .replace("\\r", "\n");    // Mac style
    }

    /**
     * Get moderation statistics for a guild for a specific date using embeds
     */
    public EmbedBuilder getModerationStatisticsForDateEmbed(String guildId) {
        String date = getCurrentDate();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Moderation Statistics")
                .setDescription("Statistics for " + date)
                .setColor(Color.BLUE)
                .setTimestamp(java.time.Instant.now());

        try (Connection connection = getConnection()) {
            // Get counts from moderation_actions table
            ArrayList<String> actionTypes = new ArrayList<>();
            actionTypes.add("warnings_issued");
            actionTypes.add("kicks_performed");
            actionTypes.add("bans_performed");
            actionTypes.add("timeouts_performed");
            actionTypes.add("untimeouts_performed");
            actionTypes.add("tickets_created");
            actionTypes.add("tickets_closed");
            actionTypes.add("verifications_performed");

            String query = "SELECT warnings_issued, kicks_performed, bans_performed, timeouts_performed, untimeouts_performed, tickets_created, tickets_closed, verifications_performed FROM statistics WHERE guild_id = ? AND date = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            stmt.setString(2, date);
            ResultSet rs = stmt.executeQuery();

            boolean hasData = false;
            StringBuilder moderationStats = new StringBuilder();

            System.out.println("Abfrage von Statistiken für Guild: " + guildId + ", Datum: " + date);

            if (rs.next()) {
                int warnings = rs.getInt("warnings_issued");
                int kicks = rs.getInt("kicks_performed");
                int bans = rs.getInt("bans_performed");
                int timeouts = rs.getInt("timeouts_performed");
                int untimeouts = rs.getInt("untimeouts_performed");
                int ticketsCreated = rs.getInt("tickets_created");
                int ticketsClosed = rs.getInt("tickets_closed");
                int verifications = rs.getInt("verifications_performed");
                if (warnings > 0) {
                    moderationStats.append("⚠️ Warnings Issued: ").append(warnings).append("\n");
                    hasData = true;
                }
                if (kicks > 0) {
                    moderationStats.append("🦶 Kicks Performed: ").append(kicks).append("\n");
                    hasData = true;
                }
                if (bans > 0) {
                    moderationStats.append("🔨 Bans Performed: ").append(bans).append("\n");
                    hasData = true;
                }
                if (timeouts > 0) {
                    moderationStats.append("⏱️ Timeouts Performed: ").append(timeouts).append("\n");
                    hasData = true;
                }
                if (untimeouts > 0) {
                    moderationStats.append("⏰ Untimeouts Performed: ").append(untimeouts).append("\n");
                    hasData = true;
                }
                if (ticketsCreated > 0) {
                    moderationStats.append("🎫 Tickets Created: ").append(ticketsCreated).append("\n");
                    hasData = true;
                }
                if (ticketsClosed > 0) {
                    moderationStats.append("✅ Tickets Closed: ").append(ticketsClosed).append("\n");
                    hasData = true;
                }
                if (verifications > 0) {
                    moderationStats.append("✅ Verifications Performed: ").append(verifications).append("\n");
                    hasData = true;
                }
            }

            System.out.println("Moderation Aktionen gefunden: " + (hasData ? "Ja" : "Nein"));

            if (hasData) {
                embed.addField("Moderation Actions", moderationStats.toString(), false);
            } else {
                embed.addField("No Activity", "No moderation actions or ticket activity found for " + date, false);
            }

        } catch (SQLException e) {
            System.err.println("Error getting moderation statistics: " + e.getMessage());
            e.printStackTrace();
            embed.addField("Error", "Failed to retrieve statistics for " + date, false);
        }

        return embed;
    }

    /**
     * Get moderation statistics for today using embeds
     */
    public EmbedBuilder getTodaysModerationStatisticsEmbed(String guildId) {
        return getModerationStatisticsForDateEmbed(guildId);
    }

    public EmbedBuilder getUserModerationStatisticsEmbed(String guildId, String userId) {
        // Fetch user statistics from the database
        String query = "SELECT SUM(warnings_issued) AS total_warnings, " +
                "SUM(kicks_performed) AS total_kicks, " +
                "SUM(bans_performed) AS total_bans, " +
                "SUM(timeouts_performed) AS total_timeouts, " +
                "SUM(untimeouts_performed) AS total_untimeouts " +
                "FROM statistics WHERE guild_id = ? AND user_id = ?";

        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                int totalWarnings = rs.getInt("total_warnings");
                int totalKicks = rs.getInt("total_kicks");
                int totalBans = rs.getInt("total_bans");
                int totalTimeouts = rs.getInt("total_timeouts");
                int totalUntimeouts = rs.getInt("total_untimeouts");

                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("User Moderation Statistics");
                embed.setColor(Color.BLUE);
                embed.addField("Total Warnings", String.valueOf(totalWarnings), true);
                embed.addField("Total Kicks", String.valueOf(totalKicks), true);
                embed.addField("Total Bans", String.valueOf(totalBans), true);
                embed.addField("Total Timeouts", String.valueOf(totalTimeouts), true);
                embed.addField("Total Untimeouts", String.valueOf(totalUntimeouts), true);
                return embed;
            } else {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("User Moderation Statistics");
                embed.setColor(Color.RED);
                embed.setDescription("No statistics found for the specified user.");
                return embed;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("User Moderation Statistics");
            embed.setColor(Color.RED);
            embed.setDescription("An error occurred while fetching statistics.");
            return embed;
        }
    }

    /**
     * Get moderation statistics for the last 7 days using embeds
     */
    public EmbedBuilder getWeeklyModerationStatisticsEmbed(String guildId, String afterDate) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Weekly Moderation Statistics")
                .setDescription("Statistics for all days with date \">" + afterDate + "\"")
                .setColor(Color.BLUE)
                .setTimestamp(java.time.Instant.now());

        System.out.println("Hole wöchentliche Moderations-Statistiken für Guild: " + guildId + " ab Datum (exklusiv): " + afterDate);

        StringBuilder dailyBreakdown = new StringBuilder();
        int totalWarnings = 0, totalKicks = 0, totalBans = 0, totalTimeouts = 0, totalUntimeouts = 0, totalTicketsCreated = 0, totalTicketsClosed = 0, totalVerifications = 0;

        try (Connection connection = getConnection()) {
            String query = "SELECT date, warnings_issued, kicks_performed, bans_performed, timeouts_performed, " +
                    "untimeouts_performed, tickets_created, tickets_closed, verifications_performed " +
                    "FROM statistics WHERE guild_id = ? AND date > ? ORDER BY date ASC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            stmt.setString(2, afterDate);
            ResultSet rs = stmt.executeQuery();

            boolean hasData = false;

            while (rs.next()) {
                hasData = true;
                String day = rs.getString("date");
                int warnings = rs.getInt("warnings_issued");
                int kicks = rs.getInt("kicks_performed");
                int bans = rs.getInt("bans_performed");
                int timeouts = rs.getInt("timeouts_performed");
                int untimeouts = rs.getInt("untimeouts_performed");
                int ticketsCreated = rs.getInt("tickets_created");
                int ticketsClosed = rs.getInt("tickets_closed");
                int verifications = rs.getInt("verifications_performed");

                totalWarnings += warnings;
                totalKicks += kicks;
                totalBans += bans;
                totalTimeouts += timeouts;
                totalUntimeouts += untimeouts;
                totalTicketsCreated += ticketsCreated;
                totalTicketsClosed += ticketsClosed;
                totalVerifications += verifications;

                System.out.println("Tag: " + day + ", Warnungen: " + warnings + ", Kicks: " + kicks + ", Bans: " + bans +
                        ", Timeouts: " + timeouts + ", Untimeouts: " + untimeouts +
                        ", Tickets Erstellt: " + ticketsCreated + ", Tickets Geschlossen: " + ticketsClosed +
                        ", Verifizierungen: " + verifications);

                dailyBreakdown.append("**").append(day).append("**\n")
                        .append("⚠️ ").append(warnings)
                        .append(" | 🦶 ").append(kicks)
                        .append(" | 🔨 ").append(bans)
                        .append(" | ⏱️ ").append(timeouts)
                        .append(" | ⏰ ").append(untimeouts)
                        .append(" | 🎫 ").append(ticketsCreated)
                        .append(" | ✅ ").append(ticketsClosed)
                        .append(" | ✔️ ").append(verifications)
                        .append("\n\n");
            }

            if (hasData) {
                String dailyContent = dailyBreakdown.toString();
                if (dailyContent.length() > 1024) {
                    dailyContent = dailyContent.substring(0, 1000) + "...\n*(gekürzt)*";
                }
                embed.addField("Daily Breakdown", dailyContent.isEmpty() ? "Keine Tagesdaten." : dailyContent, false);

                StringBuilder totals = new StringBuilder()
                        .append("⚠️ Warnings: ").append(totalWarnings).append("\n")
                        .append("🦶 Kicks: ").append(totalKicks).append("\n")
                        .append("🔨 Bans: ").append(totalBans).append("\n")
                        .append("⏱️ Timeouts: ").append(totalTimeouts).append("\n")
                        .append("⏰ Untimeouts: ").append(totalUntimeouts).append("\n")
                        .append("🎫 Tickets Created: ").append(totalTicketsCreated).append("\n")
                        .append("✅ Tickets Closed: ").append(totalTicketsClosed).append("\n")
                        .append("✔️ Verifications: ").append(totalVerifications);
                embed.addField("Totals", totals.toString(), true);
            } else {
                embed.addField("No Activity", "Keine Aktionen für Datum > " + afterDate, false);
            }
        } catch (SQLException e) {
            System.err.println("Error getting weekly moderation statistics: " + e.getMessage());
            e.printStackTrace();
            embed.addField("Error", "Failed to retrieve weekly statistics", false);
        }

        return embed;
    }

    /**
     * Get appropriate emoji for moderation action type
     */
    private String getModerationEmoji(String actionType) {
        switch (actionType.toUpperCase()) {
            case "WARN": return "⚠️";
            case "KICK": return "🦶";
            case "BAN": return "🔨";
            case "TEMP_BAN": return "🔨⏰";
            case "UNBAN": return "🔓";
            case "MUTE": return "🔇";
            case "TEMP_MUTE": return "🔇⏰";
            case "UNMUTE": return "🔊";
            case "TIMEOUT": return "⏱️";
            case "UNTIMEOUT": return "⏰";
            default: return "⚖️";
        }
    }

    public EmbedBuilder getGlobalStats () {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🌐 Global Bot Statistics")
                .setColor(Color.MAGENTA)
                .setTimestamp(java.time.Instant.now());

        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM global_statistics";
            PreparedStatement stmt = connection.prepareStatement(query);
            ResultSet rs = stmt.executeQuery();
            StringBuilder description = new StringBuilder();

            while (rs.next()) {
                String command = rs.getString("command");
                int count = rs.getInt("number");
                System.out.println("Anzahl: " + count);
                String lastUsed = rs.getTimestamp("last_used").toString();
                description.append("Command Name: ").append(command).append("; Total Uses: ").append(count).append("\n");
                description.append(lastUsed).append("\n");
            }
            embed.setDescription(description);
        } catch (SQLException e) {
            System.err.println("Error getting global statistics: " + e.getMessage());
            e.printStackTrace();
            embed.setDescription("Error retrieving global statistics.");
        }

        return embed;
    }


    /**
     * Send an audit log entry to the configured log channel
     * @param guild The guild where the action occurred
     * @param actionType The type of action (e.g., "WARN", "KICK", "BAN", etc.)
     * @param targetName The name/identifier of the target
     * @param moderatorName The name of the moderator who performed the action
     * @param reason The reason for the action
     */
    public void sendAuditLogEntry(Guild guild, String actionType, String targetName, String moderatorName, String reason) {
        String guildId = guild.getId();
        
        if (hasLogChannel(guildId)) {
            String logChannelId = getLogChannelID(guildId);
            if (!logChannelId.equals("Couldnt find a Log Channel") && !logChannelId.equals("Error")) {
                TextChannel logChannel = guild.getTextChannelById(logChannelId);
                if (logChannel != null) {
                    String emoji;
                    Color embedColor;
                    switch (actionType) {
                        case "WARN": emoji = "⚠️"; embedColor = Color.YELLOW; break;
                        case "KICK": emoji = "🦶"; embedColor = Color.ORANGE; break;
                        case "BAN": emoji = "🔨"; embedColor = Color.RED; break;
                        case "UNBAN": emoji = "🔓"; embedColor = Color.GREEN; break;
                        case "PURGE": emoji = "🧹"; embedColor = Color.YELLOW; break;
                        case "SLOWMODE": emoji = "🐌"; embedColor = Color.BLUE; break;
                        case "UNTIMEOUT": emoji = "⏰"; embedColor = Color.GREEN; break;
                        case "TICKET_CREATED": emoji = "🎫"; embedColor = Color.CYAN; break;
                        case "TICKET_CLOSED": emoji = "🔒"; embedColor = Color.GRAY; break;
                        default:
                            if (actionType.startsWith("TIMEOUT")) {
                                emoji = "⏱️";
                                embedColor = Color.ORANGE;
                            } else {
                                emoji = "⚖️"; // Default moderation emoji
                                embedColor = Color.GRAY;
                            }
                            break;
                    }
                    
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle(emoji + " " + actionType)
                            .setDescription(emoji + " " + targetName)
                            .addField("Moderator", moderatorName, true)
                            .addField("Reason", reason, true)
                            .setColor(embedColor)
                            .setTimestamp(java.time.Instant.now());
                    
                    logChannel.sendMessageEmbeds(embed.build()).queue();
                }
            }
        }
    }

    /**
     * Data class to hold verify button configuration
     */
    public static class VerifyButtonData {
        public final String roleToGiveId;
        public final String roleToRemoveId;
        public final String buttonLabel;
        public final String buttonEmoji;

        public VerifyButtonData(String roleToGiveId, String roleToRemoveId, String buttonLabel, String buttonEmoji) {
            this.roleToGiveId = roleToGiveId;
            this.roleToRemoveId = roleToRemoveId;
            this.buttonLabel = buttonLabel;
            this.buttonEmoji = buttonEmoji;
        }
    }

    /**
     * Get all verify button configurations for a guild
     */
    public List<VerifyButtonData> getVerifyButtonConfigs(String guildId) {
        List<VerifyButtonData> configs = new ArrayList<>();
        String query = "SELECT role_to_give_id, role_to_remove_id, button_label, button_emoji_id FROM just_verify_button WHERE guild_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(query)) {

            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                configs.add(new VerifyButtonData(
                        rs.getString("role_to_give_id"),
                        rs.getString("role_to_remove_id"),
                        rs.getString("button_label"),
                        rs.getString("button_emoji_id")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching verify button configs: " + e.getMessage());
            e.printStackTrace();
        }
        return configs;
    }

    public String getJustVerifyButtonRoleToGiveID(String guildId) {
        String query = "SELECT role_to_give_id FROM just_verify_button WHERE guild_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String roleId = rs.getString("role_to_give_id");
                System.out.println("Just Verify Button Role ID for guild " + guildId + ": " + roleId);
                return roleId;
            } else {
                System.out.println("No Just Verify Button Role ID found for guild " + guildId);
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error getting Just Verify Button Role ID: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String getJustVerifyButtonRoleToRemoveID(String guildId) {
        String query = "SELECT role_to_remove_id FROM just_verify_button WHERE guild_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String roleId = rs.getString("role_to_remove_id");
                System.out.println("Just Verify Button Role to Remove ID for guild " + guildId + ": " + roleId);
                return roleId;
            } else {
                System.out.println("No Just Verify Button Role to Remove ID found for guild " + guildId);
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error getting Just Verify Button Role to Remove ID: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String getJustVerifyButtonLabel (String guildId) {
        String query = "SELECT button_label FROM just_verify_button WHERE guild_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String label = rs.getString("button_label");
                System.out.println("Just Verify Button Label for guild " + guildId + ": " + label);
                return label != null ? label : "✅ Verify!";
            } else {
                System.out.println("No Just Verify Button Label found for guild " + guildId);
                return "Verify";
            }
        } catch (SQLException e) {
            System.err.println("Error getting Just Verify Button Label: " + e.getMessage());
            e.printStackTrace();
            return "Verify";
        }
    }

    public String getJustVerifyButtonEmojiID (String guildId) {
        String query = "SELECT button_emoji_id FROM just_verify_button WHERE guild_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String emojiId = rs.getString("button_emoji_id");
                System.out.println("Just Verify Button Emoji ID for guild " + guildId + ": " + emojiId);
                return emojiId;
            } else {
                System.out.println("No Just Verify Button Emoji ID found for guild " + guildId);
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error getting Just Verify Button Emoji ID: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public boolean isJustVerifyButton(String guildId) {
        String query = "SELECT * FROM just_verify_button WHERE guild_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                System.out.println("Just Verify Button Enabled for guild " + guildId + ": " + true);
                return true;
            } else {
                System.out.println("No Just Verify Button setting found for guild " + guildId);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error getting Just Verify Button setting: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void setJustVerifyButton(String guildId, String roleToGiveId, String roleToRemoveId, String buttonLabel, String buttonEmojiId) {
        if (!isJustVerifyButton(guildId)) {
            String query = "INSERT INTO just_verify_button (guild_id, role_to_give_id, role_to_remove_id, button_label, button_emoji_id) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE guild_id = ?";
            try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, roleToGiveId);
                pstmt.setString(3, roleToRemoveId);
                pstmt.setString(4, buttonLabel);
                pstmt.setString(5, buttonEmojiId);
                pstmt.setString(6, guildId);
                pstmt.executeUpdate();
                System.out.println("Just Verify Button entry created/updated for guild " + guildId);
            } catch (SQLException e) {
                System.err.println("Error setting Just Verify Button entry: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            String query = "UPDATE just_verify_button SET role_to_give_id = ?, role_to_remove_id = ?, button_label = ?, button_emoji_id = ? WHERE guild_id = ?";
            try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, roleToGiveId);
                pstmt.setString(2, roleToRemoveId);
                pstmt.setString(3, buttonLabel);
                pstmt.setString(4, buttonEmojiId);
                pstmt.setString(5, guildId);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("Just Verify Button entry updated for guild " + guildId);
                } else {
                    System.out.println("No Just Verify Button entry found to update for guild " + guildId);
                }
            } catch (SQLException e) {
                System.err.println("Error updating Just Verify Button entry: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    public void removeJustVerifyButton(String guildId) {
        String query = "DELETE FROM just_verify_button WHERE guild_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Just Verify Button entry removed for guild " + guildId);
            } else {
                System.out.println("No Just Verify Button entry found to remove for guild " + guildId);
            }
        } catch (SQLException e) {
            System.err.println("Error removing Just Verify Button entry: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Button createJustVerifyButton(String roleToGiveID, String roleToRemoveID, String buttonLabel, String buttonEmoji) {
        Button button;
        if (buttonLabel != null) {
            button = Button.primary("just_verify", buttonLabel);
            if (buttonEmoji != null) {
                button = button.withEmoji(Emoji.fromFormatted(buttonEmoji));
            }
        } else {
            // No emoji
            button = Button.primary("just_verify", "✅ Verify!");
        }
        return button;
    }
    public void updateGuildActivityStatus(List<Guild> guilds) {
        String query = "SELECT * FROM guilds";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            ResultSet rs = pstmt.executeQuery();
            Set<String> activeGuildIds = guilds.stream().map(Guild::getId).collect(Collectors.toSet());

            while (rs.next()) {
                String guildId = rs.getString("id");
                boolean isActive = rs.getBoolean("active");

                if (activeGuildIds.contains(guildId) && !isActive) {
                    // Guild is now active but marked as inactive in DB
                    String updateQuery = "UPDATE guilds SET active = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                    try (PreparedStatement updatePstmt = connection.prepareStatement(updateQuery)) {
                        updatePstmt.setString(1, guildId);
                        updatePstmt.executeUpdate();
                        System.out.println("Marked guild " + guildId + " as active.");
                    }
                } else if (!activeGuildIds.contains(guildId) && isActive) {
                    // Guild is no longer active but marked as active in DB
                    String updateQuery = "UPDATE guilds SET active = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
                    try (PreparedStatement updatePstmt = connection.prepareStatement(updateQuery)) {
                        updatePstmt.setString(1, guildId);
                        updatePstmt.executeUpdate();
                        System.out.println("Marked guild " + guildId + " as inactive.");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error updating guild activity status: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isRoleAlreadyAdded (String guildId, String roleSelectId) {
        String query = "SELECT * FROM role_select WHERE guild_id = ? AND role_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, roleSelectId);
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("Error checking if role select is already added: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean addRoleSelectToGuild (String guildId, String roleSelectId, String description, String emojiId) {
        if (!isRoleAlreadyAdded(guildId, roleSelectId)) {
            String query = "INSERT INTO role_select (guild_id, role_id, description,emoji_id) VALUES (?, ?, ?, ?)";
            try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, roleSelectId);
                pstmt.setString(3, description);
                pstmt.setString(4, emojiId);
                pstmt.executeUpdate();
                System.out.println("Role select " + roleSelectId + " added to guild " + guildId);
                return true;

            } catch (SQLException e) {
                System.err.println("Error adding role select to guild: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } else {
            System.out.println("Role select " + roleSelectId + " is already added to guild " + guildId);
            return false;
        }
    }

    public boolean removeRoleSelectFromGuild (String guildId, String roleSelectId) {
        if (isRoleAlreadyAdded(guildId, roleSelectId)) {
            String query = "DELETE FROM role_select WHERE guild_id = ? AND role_id = ?";
            try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, guildId);
                pstmt.setString(2, roleSelectId);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("Role select " + roleSelectId + " removed from guild " + guildId);
                    return true;
                } else {
                    System.out.println("No role select " + roleSelectId + " found to remove from guild " + guildId);
                    return false;
                }
            } catch (SQLException e) {
                System.err.println("Error removing role select from guild: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } else {
            System.out.println("Role select " + roleSelectId + " is not added to guild " + guildId);
            return false;
        }
    }

    public int getRoleSelectID (String guildId, String roleSelectId) {
        String query = "SELECT id FROM role_select WHERE guild_id = ? AND role_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, roleSelectId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                int id = rs.getInt("id");
                System.out.println("Role Select ID for guild " + guildId + " and role select " + roleSelectId + ": " + id);
                return id;
            } else {
                System.out.println("No Role Select ID found for guild " + guildId + " and role select " + roleSelectId);
                return -1;
            }
        } catch (SQLException e) {
            System.err.println("Error getting Role Select ID: " + e.getMessage());
            e.printStackTrace();
            return -1;
        }
    }

    public boolean addSelectRolesEmbed (String guildId, String channelId, String messageId, String description) {
        String query = "INSERT INTO role_select_embeds (guild_id, channel_id, message_id, description, title) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, channelId);
            pstmt.setString(3, messageId);
            pstmt.executeUpdate();
            System.out.println("Select Roles Embed added for guild " + guildId);
            return true;
        } catch (SQLException e) {
            System.err.println("Error adding Select Roles Embed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean addEmbedToDatabase (String guildId, String channelId, String messageId, String displayType, String title, String description, String footer, String color) {
        if (displayType.equalsIgnoreCase("BUTTON")) {
            displayType = "REACTION";
        } else if (displayType.equalsIgnoreCase("SELECT_MENU")) {
            displayType = "SELECT-MENU";
        } else {
            displayType = "BUTTON"; // Default to BUTTON if invalid
        }
        String query = "INSERT INTO role_select_embeds (guild_id, channel_id, message_id, display_type, title, description, footer, color) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, channelId);
            pstmt.setString(3, messageId);
            pstmt.setString(4, displayType);
            pstmt.setString(5, title);
            pstmt.setString(6, description);
            pstmt.setString(7, footer);
            pstmt.setString(8, color);
            pstmt.executeUpdate();
            System.out.println("Role Select Embed added for guild " + guildId);
            return true;
        } catch (SQLException e) {
            System.err.println("Error adding Role Select Embed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean removeEmbedFromDatabase (String guildId, String messageId) {
        String query = "DELETE FROM role_select_embeds WHERE guild_id = ? AND message_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, messageId);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Role Select Embed removed for guild " + guildId);
                return true;
            } else {
                System.out.println("No Role Select Embed found to remove for guild " + guildId);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error removing Role Select Embed: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public List<String> getAllRoleSelectForGuild (String guildId) {
        List<String> embedMessageIds = new ArrayList<>();
        String query = "SELECT role_id FROM role_select WHERE guild_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String messageId = rs.getString("role_id");
                embedMessageIds.add(messageId);
            }
            System.out.println("Fetched Role Select Embeds for guild " + guildId);
        } catch (SQLException e) {
            System.err.println("Error fetching Role Select Embeds: " + e.getMessage());
            e.printStackTrace();
        }
        return embedMessageIds;
    }

    public String getRoleSelectDescription(String id, String id1) {
        String query = "SELECT description FROM role_select WHERE guild_id = ? AND role_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, id);
            pstmt.setString(2, id1);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String description = rs.getString("description");
                System.out.println("Role Select Description for guild " + id + " and role select " + id1 + ": " + description);
                return description;
            } else {
                System.out.println("No Role Select Description found for guild " + id + " and role select " + id1);
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error getting Role Select Description: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String getRoleSelectEmoji(String guildId, String roleSelectId) {
        String query = "SELECT emoji_id FROM role_select WHERE guild_id = ? AND role_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, roleSelectId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String emojiId = rs.getString("emoji_id");
                System.out.println("Role Select Emoji for guild " + guildId + " and role select " + roleSelectId + ": " + emojiId);
                return emojiId;
            } else {
                System.out.println("No Role Select Emoji found for guild " + guildId + " and role select " + roleSelectId);
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error getting Role Select Emoji: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String getRoleSelectRoleIDByEmoji (String guildId, String emoji) {
        String query = "SELECT role_id FROM role_select WHERE guild_id = ? AND emoji_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, emoji);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String roleId = rs.getString("role_id");
                System.out.println("Role Select Role ID for guild " + guildId + " and emoji " + emoji + ": " + roleId);
                return roleId;
            } else {
                System.out.println("No Role Select Role ID found for guild " + guildId + " and emoji " + emoji);
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error getting Role Select Role ID by Emoji: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public EmbedBuilder getGlobalModStats() {
        try (Connection connection = getConnection()) {
            String query = "SELECT " +
                    "SUM(warnings_issued) AS total_warnings, " +
                    "SUM(kicks_performed) AS total_kicks, " +
                    "SUM(bans_performed) AS total_bans, " +
                    "SUM(timeouts_performed) AS total_timeouts, " +
                    "SUM(untimeouts_performed) AS total_untimeouts, " +
                    "SUM(tickets_created) AS total_tickets_created, " +
                    "SUM(tickets_closed) AS total_tickets_closed, " +
                    "SUM(verifications_performed) AS total_verifications " +
                    "FROM statistics";
            PreparedStatement pstmt = connection.prepareStatement(query);
            ResultSet rs = pstmt.executeQuery();

            EmbedBuilder embed = new EmbedBuilder();
            if (rs.next()) {
                int totalWarnings = rs.getInt("total_warnings");
                int totalKicks = rs.getInt("total_kicks");
                int totalBans = rs.getInt("total_bans");
                int totalTimeouts = rs.getInt("total_timeouts");
                int totalUntimeouts = rs.getInt("total_untimeouts");
                int totalTicketsCreated = rs.getInt("total_tickets_created");
                int totalTicketsClosed = rs.getInt("total_tickets_closed");
                int totalVerifications = rs.getInt("total_verifications");


                embed.setTitle("Lifetime Moderation Statistics");
                embed.setColor(Color.BLUE);
                embed.addField("Total Warnings Issued", String.valueOf(totalWarnings), true);
                embed.addField("Total Kicks Performed", String.valueOf(totalKicks), true);
                embed.addField("Total Bans Performed", String.valueOf(totalBans), true);
                embed.addField("Total Timeouts Performed", String.valueOf(totalTimeouts), true);
                embed.addField("Total Untimeouts Performed", String.valueOf(totalUntimeouts), true);
                embed.addField("Total Tickets Created", String.valueOf(totalTicketsCreated), true);
                embed.addField("Total Tickets Closed", String.valueOf(totalTicketsClosed), true);
                embed.addField("Total Verifications Performed", String.valueOf(totalVerifications), true);

                return embed;
            } else {
                embed.setDescription("Nothing.");
                return embed;
            }
        } catch (SQLException e) {
            System.err.println("Error getting global statistics: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public boolean isSelectRoleEmbedExist (String guildId) {
        String query = "SELECT * FROM role_select_embeds WHERE guild_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                System.out.println("Select Role Embed exists for guild " + guildId);
                return true;
            } else {
                System.out.println("No Select Role Embed found for guild " + guildId);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error checking Select Role Embed existence: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void editSelectRoleEmbed (String title, String description, String footer, String color, String guildId) {
        if (isSelectRoleEmbedExist(guildId)) {
            String query = "UPDATE role_select_embeds SET title = ?, description = ?, footer = ?, color = ? WHERE guild_id = ?";
            try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, title);
                pstmt.setString(2, description);
                pstmt.setString(3, footer);
                pstmt.setString(4, color);
                pstmt.setString(5, guildId);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("Select Role Embed updated for guild " + guildId);
                } else {
                    System.out.println("No Select Role Embed found to update for guild " + guildId);
                }
            } catch (SQLException e) {
                System.err.println("Error updating Select Role Embed: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            String query = "INSERT INTO role_select_embeds SET title = ?, description = ?, footer = ?, color = ?, guild_id = ?";
            try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
                pstmt.setString(1, title);
                pstmt.setString(2, description);
                pstmt.setString(3, footer);
                pstmt.setString(4, color);
                pstmt.setString(5, guildId);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    System.out.println("Select Role Embed updated for guild " + guildId);
                } else {
                    System.out.println("No Select Role Embed found to update for guild " + guildId);
                }
            } catch (SQLException e) {
                System.err.println("Error updating Select Role Embed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public String getSelectRoleEmbedTitle (String guildId) {
        String query = "SELECT title FROM role_select_embeds WHERE guild_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String title = rs.getString("title");
                System.out.println("Select Role Embed Title for guild " + guildId + ": " + title);
                return title;
            } else {
                System.out.println("No Select Role Embed Title found for guild " + guildId);
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error getting Select Role Embed Title: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String getSelectRolesDescription (String guildId) {
        String query = "SELECT description FROM role_select_embeds WHERE guild_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String description = rs.getString("description");
                System.out.println("Select Role Embed Description for guild " + guildId + ": " + description);
                return description;
            } else {
                System.out.println("No Select Role Embed Description found for guild " + guildId);
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error getting Select Role Embed Description: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String getSelectRolesFooter (String guildId) {
        String query = "SELECT footer FROM role_select_embeds WHERE guild_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String footer = rs.getString("footer");
                System.out.println("Select Role Embed Footer for guild " + guildId + ": " + footer);
                return footer;
            } else {
                System.out.println("No Select Role Embed Footer found for guild " + guildId);
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error getting Select Role Embed Footer: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public String getSelectRolesColor (String guildId) {
        String query = "SELECT color FROM role_select_embeds WHERE guild_id = ?";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String color = rs.getString("color");
                System.out.println("Select Role Embed Color for guild " + guildId + ": " + color);
                return color;
            } else {
                System.out.println("No Select Role Embed Color found for guild " + guildId);
                return null;
            }
        } catch (SQLException e) {
            System.err.println("Error getting Select Role Embed Color: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Erstellt einen neuen Event-Eintrag in der Datenbank.
     * * @param guildId Die ID des Discord Servers
     * @param name Der Name des Events (z.B. "Newbie Schutz")
     * @param eventType Der Trigger (z.B. "MEMBER_JOIN", "WARN_THRESHOLD")
     * @param roleId Die betroffene Rolle
     * @param actionType "ADD" (geben) oder "REMOVE" (nehmen)
     * @param durationSeconds Wie lange die Rolle bleibt (0 = permanent)
     * @param stackType "REFRESH" (Zeit zurücksetzen) oder "EXTEND" (Zeit addieren)
     * @param triggerData JSON-String für Bedingungen (z.B. "{\"threshold\": 3}")
     */
    public void createRoleEvent(String guildId, String name, String eventType, String roleId, String actionType, long durationSeconds, String stackType, String triggerData) {
        String query = "INSERT INTO role_events (guild_id, name, event_type, role_id, action_type, duration_seconds, stack_type, trigger_data) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setString(1, guildId);
            stmt.setString(2, name);
            stmt.setString(3, eventType);
            stmt.setString(4, roleId);
            stmt.setString(5, actionType != null ? actionType : "ADD");
            stmt.setLong(6, durationSeconds);
            stmt.setString(7, stackType != null ? stackType : "REFRESH");
            stmt.setString(8, triggerData); // Kann null sein

            stmt.executeUpdate();
            System.out.println("Created role event: " + name + " for guild " + guildId);

        } catch (SQLException e) {
            System.err.println("Error creating role event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Löscht ein Role-Event aus der Datenbank.
     * * @param guildId Die ID der Guild (Sicherheitscheck)
     * @param eventId Die ID des zu löschenden Events
     * @return true wenn erfolgreich gelöscht, sonst false
     */
    public boolean deleteRoleEvent(String guildId, int eventId) {
        String query = "DELETE FROM role_events WHERE id = ? AND guild_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setInt(1, eventId);
            stmt.setString(2, guildId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Deleted role event " + eventId + " for guild " + guildId);
                return true;
            } else {
                System.out.println("No role event found with ID " + eventId + " for guild " + guildId);
                return false;
            }

        } catch (SQLException e) {
            System.err.println("Error deleting role event: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Aktualisiert ein bestehendes Role-Event.
     * * @param eventId Die ID des Events, das bearbeitet wird
     * @param guildId Die ID der Guild (Sicherheitscheck)
     * @param name Neuer Name (oder null/alter Wert)
     * @param eventType Neuer Trigger-Typ (z.B. "WARN_THRESHOLD")
     * @param roleId Neue Rolle
     * @param actionType "ADD" oder "REMOVE"
     * @param durationSeconds Neue Dauer
     * @param stackType "REFRESH" oder "EXTEND"
     * @param triggerData Neues JSON für Bedingungen
     * @param active Ob das Event aktiv sein soll (true/false)
     * @return true bei Erfolg
     */
    public boolean updateRoleEvent(int eventId, String guildId, String name, String eventType, String roleId,
                                   String actionType, long durationSeconds, String stackType, String triggerData, boolean active) {

        String query = "UPDATE role_events SET " +
                "name = ?, event_type = ?, role_id = ?, action_type = ?, " +
                "duration_seconds = ?, stack_type = ?, trigger_data = ?, active = ? " +
                "WHERE id = ? AND guild_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setString(1, name);
            stmt.setString(2, eventType);
            stmt.setString(3, roleId);
            stmt.setString(4, actionType);
            stmt.setLong(5, durationSeconds);
            stmt.setString(6, stackType);
            stmt.setString(7, triggerData);
            stmt.setInt(8, active ? 1 : 0); // Boolean zu TinyInt konvertieren

            // WHERE clause
            stmt.setInt(9, eventId);
            stmt.setString(10, guildId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Updated role event " + eventId + " (" + name + ")");
                return true;
            } else {
                System.out.println("Failed to update: Role event " + eventId + " not found for guild " + guildId);
                return false;
            }

        } catch (SQLException e) {
            System.err.println("Error updating role event: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Data class to hold active timer information
     */
    public static class ActiveTimerData {
        public final int id;
        public final String guildId;
        public final String userId;
        public final String roleId;
        public final Timestamp expiresAt;
        public final int sourceEventId;
        public String actionType;

        public ActiveTimerData(int id, String guildId, String userId, String roleId, Timestamp expiresAt, int sourceEventId, String actionType) {
            this.id = id;
            this.guildId = guildId;
            this.userId = userId;
            this.roleId = roleId;
            this.expiresAt = expiresAt;
            this.sourceEventId = sourceEventId;
            this.actionType = actionType;
        }
    }

    /**
     * Schaltet ein Event an oder aus (Toggle).
     */
    public void toggleRoleEventActive(String guildId, int eventId, boolean isActive) {
        String query = "UPDATE role_events SET active = ? WHERE id = ? AND guild_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setInt(1, isActive ? 1 : 0);
            stmt.setInt(2, eventId);
            stmt.setString(3, guildId);

            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error toggling role event status: " + e.getMessage());
        }
    }

    /**
     * Startet einen Timer für eine Rolle.
     * * @param guildId Die ID des Discord Servers
     * @param userId Die ID des Users
     * @param roleId Die ID der Rolle, die entfernt werden muss
     * @param durationSeconds In wie vielen Sekunden der Timer abläuft
     * @param sourceEventId (Optional) Die ID des Events, das diesen Timer ausgelöst hat
     */
    public void addActiveTimer(String guildId, String userId, String roleId, int sourceEventId, long durationSeconds) {
        String query = "INSERT INTO active_timers (guild_id, user_id, role_id, expires_at, source_event_id) VALUES (?, ?, ?, ?, ?)";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            // Ablaufzeitpunkt in Java berechnen (Sicherer als DB-spezifische SQL-Funktionen)
            long expiryMillis = System.currentTimeMillis() + (durationSeconds * 1000);
            Timestamp expiresAt = new Timestamp(expiryMillis);

            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.setString(3, roleId);
            stmt.setTimestamp(4, expiresAt);

            if (sourceEventId > 0) {
                stmt.setInt(5, sourceEventId);
            } else {
                stmt.setNull(5, java.sql.Types.INTEGER);
            }

            stmt.executeUpdate();
            System.out.println("Added active timer for user " + userId + " (Expires: " + expiresAt + ")");

        } catch (SQLException e) {
            System.err.println("Error adding active timer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Holt alle Timer, die abgelaufen sind (expires_at <= JETZT).
     * Wird vom Background-Loop aufgerufen.
     */
    public List<ActiveTimerData> getExpiredTimers() {
        List<ActiveTimerData> expiredTimers = new ArrayList<>();
        String query = "SELECT * FROM active_timers LEFT JOIN role_events ON source_event_id = role_events.id WHERE expires_at <= CURRENT_TIMESTAMP";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                expiredTimers.add(new ActiveTimerData(
                        rs.getInt("id"),
                        rs.getString("guild_id"),
                        rs.getString("user_id"),
                        rs.getString("role_id"),
                        rs.getTimestamp("expires_at"),
                        rs.getInt("source_event_id"),
                        rs.getString("role_events.action_type")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error fetching expired timers: " + e.getMessage());
            e.printStackTrace();
        }
        return expiredTimers;
    }

    /**
     * Löscht einen Timer anhand seiner ID.
     * Aufrufen, NACHDEM die Rolle im Discord entfernt wurde.
     */
    public void removeTimer (int timerId) {
        String query = "DELETE FROM active_timers WHERE id = ?";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setInt(1, timerId);
            stmt.executeUpdate();
            // System.out.println("Removed active timer with ID: " + timerId);

        } catch (SQLException e) {
            System.err.println("Error removing timer " + timerId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Löscht einen Timer basierend auf User und Rolle (z.B. bei manuellem Unmute).
     */
    public boolean removeTimerManual (String guildId, String userId, String roleId) {
        String query = "DELETE FROM active_timers WHERE guild_id = ? AND user_id = ? AND role_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.setString(3, roleId);

            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("Error removing specific timer: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Verlängert einen bestehenden Timer (Stacking / Extend).
     * @param additionalSeconds Sekunden, die auf die aktuelle Ablaufzeit addiert werden.
     */
    public boolean extendTimer (String guildId, String userId, String roleId, long additionalSeconds) {
        String query = "UPDATE active_timers SET expires_at = DATE_ADD(expires_at, INTERVAL ? SECOND) " +
                "WHERE guild_id = ? AND user_id = ? AND role_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setLong(1, additionalSeconds);
            stmt.setString(2, guildId);
            stmt.setString(3, userId);
            stmt.setString(4, roleId);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Error extending timer: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Holt alle aktiven Timer für einen spezifischen User.
     */
    public List<ActiveTimerData> getActiveTimersForUser(String guildId, String userId) {
        List<ActiveTimerData> userTimers = new ArrayList<>();
        String query = "SELECT * FROM active_timers LEFT JOIN role_events ON source_event_id = role_events.id WHERE active_timers.guild_id = ? AND user_id = ? ORDER BY expires_at ASC";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setString(1, guildId);
            stmt.setString(2, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    userTimers.add(new ActiveTimerData(
                            rs.getInt("active_timers.id"),
                            rs.getString("active_timers.guild_id"),
                            rs.getString("active_timers.user_id"),
                            rs.getString("active_timers.role_id"),
                            rs.getTimestamp("active_timers.expires_at"),
                            rs.getInt("active_timers.source_event_id"),
                            rs.getString("role_events.action_type")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching user timers: " + e.getMessage());
            e.printStackTrace();
        }
        return userTimers;
    }

    // In DatabaseHandler.java einfügen:

    public static class RoleEventData {
        public final int id;
        public final String name;
        public final String eventType;
        public final String roleId;
        public final String actionType;
        public final String stackType;
        public final long durationSeconds;
        public final String triggerData;
        public final boolean active;
        public final boolean instant;

        public RoleEventData(int id, String name, String eventType, String roleId, String actionType, long durationSeconds, String triggerData, boolean active, String stackType, boolean instant) {
            this.id = id;
            this.name = name;
            this.eventType = eventType;
            this.roleId = roleId;
            this.actionType = actionType;
            this.durationSeconds = durationSeconds;
            this.triggerData = triggerData;
            this.active = active;
            this.stackType = stackType;
            this.instant = instant;
        }
    }

    public RoleEventData getRoleEvent(int eventId) {
        String query = "SELECT * FROM role_events WHERE id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, eventId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new RoleEventData(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("event_type"),
                        rs.getString("role_id"),
                        rs.getString("action_type"),
                        rs.getLong("duration_seconds"),
                        rs.getString("trigger_data"),
                        rs.getInt("active") == 1,
                        rs.getString("stack_type"),
                        rs.getInt("instant_apply") == 1
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void updateRoleEventInstantApply(String guildId, int eventId, boolean b) {
        String query = "UPDATE role_events SET instant_apply = ? WHERE id = ? AND guild_id = ?";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setInt(1, b ? 1 : 0);
            stmt.setInt(2, eventId);
            stmt.setString(3, guildId);

            stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error updating instant apply for role event: " + e.getMessage());
        }
    }

    /**
     * Holt alle aktiven Events eines bestimmten Typs für eine Guild.
     */
    public List<RoleEventData> getRoleEventsByType(String guildId, RoleEventType type) {
        List<RoleEventData> events = new ArrayList<>();
        String query = "SELECT * FROM role_events WHERE guild_id = ? AND event_type = ? AND active = 1";

        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setString(1, guildId);
            stmt.setString(2, type.toString());

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                events.add(new RoleEventData(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getString("event_type"),
                        rs.getString("role_id"),
                        rs.getString("action_type"),
                        rs.getLong("duration_seconds"),
                        rs.getString("trigger_data"),
                        rs.getInt("active") == 1,
                        rs.getString("stack_type"),
                        rs.getInt("instant_apply") == 1
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return events;
    }

    // In DatabaseHandler.java

    public void saveCustomEmbed(String guildId, String name, String jsonData) {
        String query = "INSERT INTO custom_embeds (guild_id, name, data) VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE data = VALUES(data)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, name);
            stmt.setString(3, jsonData);
            stmt.executeUpdate();
            System.out.println("Saved custom embed '" + name + "' for guild " + guildId);
        } catch (SQLException e) {
            System.err.println("Error saving custom embed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getCustomEmbedData(String guildId, String name) {
        String query = "SELECT data FROM custom_embeds WHERE guild_id = ? AND name = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("data");
            }
        } catch (SQLException e) {
            System.err.println("Error fetching custom embed: " + e.getMessage());
        }
        return null;
    }

    public List<String> getCustomEmbedNames(String guildId) {
        List<String> names = new ArrayList<>();
        String query = "SELECT name FROM custom_embeds WHERE guild_id = ? ORDER BY name ASC";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        } catch (SQLException e) {
            System.err.println("Error listing custom embeds: " + e.getMessage());
        }
        return names;
    }

    public boolean deleteCustomEmbed(String guildId, String name) {
        String query = "DELETE FROM custom_embeds WHERE guild_id = ? AND name = ?";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, name);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting custom embed: " + e.getMessage());
            return false;
        }
    }

    //
    // REMOVED: createGuildSystemsTable() method

    // Constants for all available systems
    private static final String[] ALL_SYSTEMS = {
            "log-channel", "warn", "ticket", "mod", "stats",
            "verify-button", "select-roles", "temprole", "role-event",
            "embed"
    };

    /**
     * Check if a specific system is active for a guild.
     * Uses the active_modules column in the guilds table.
     */
    public boolean isSystemActive(String guildId, String systemName) {
        try (Connection connection = getConnection()) {
            String query = "SELECT active_modules FROM guilds WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String modulesStr = rs.getString("active_modules");
                if (modulesStr == null) return false;

                List<String> modules = Arrays.asList(modulesStr.split(","));
                return modules.contains(systemName);
            }
            return false; // Default to true if guild not found (shouldn't happen)
        } catch (SQLException e) {
            System.err.println("Error checking system status: " + e.getMessage());
            e.printStackTrace();
            return true;
        }
    }

    /**
     * Toggle a system's status for a guild in the guilds table.
     */
    public boolean toggleSystem(String guildId, String systemName) {
        HashMap<String, Boolean> statuses = new HashMap<>();
        List<String> currentModules;
        boolean wasActive = false;

        try (Connection connection = getConnection()) {
            // 1. Get current modules
            String query = "SELECT active_modules FROM guilds WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String modulesStr = rs.getString("active_modules");
                if (modulesStr == null) {
                    // First time setup: Initialize with ALL systems
                    currentModules = new ArrayList<>();
                } else if (modulesStr.isEmpty()) {
                    currentModules = new ArrayList<>();
                } else {
                    currentModules = new ArrayList<>(Arrays.asList(modulesStr.split(",")));
                }
            } else {
                // Guild not found, assume defaults
                currentModules = new ArrayList<>();
            }

            for (String module : ALL_SYSTEMS) {
                if (currentModules.contains(module) && module.equals(systemName)) {
                    statuses.put(module, false);
                } else if (currentModules.contains(module) || (module.equals(systemName) && !currentModules.contains(systemName))) {
                    statuses.put(module, true);
                } else {
                    statuses.put(module, false);
                }
            }

            wasActive = currentModules.contains(systemName);
            boolean newStatus = !wasActive;

            // 2. Modify list
            if (newStatus) {
                if (!currentModules.contains(systemName)) {
                    currentModules.add(systemName);
                }
            } else {
                currentModules.remove(systemName);
            }

            // 3. Save back to database
            System.out.println("Updating active modules for guild " + guildId + ": " + currentModules);
            String newModulesStr = "";
            System.out.println(statuses);
            for (String mod : statuses.keySet()) {
                if (statuses.get(mod) == true) {
                    newModulesStr = newModulesStr + mod + ",";
                }
            }
            if (newModulesStr.endsWith(",")) {
                newModulesStr = newModulesStr.substring(0, newModulesStr.length() - 1);
            }
            System.out.println(newModulesStr);

            String updateQuery = "UPDATE guilds SET active_modules = ? WHERE id = ?";
            PreparedStatement updateStmt = connection.prepareStatement(updateQuery);
            updateStmt.setString(1, newModulesStr);
            updateStmt.setString(2, guildId);
            updateStmt.executeUpdate();

            return newStatus;

        } catch (SQLException e) {
            System.err.println("Error toggling system: " + e.getMessage());
            e.printStackTrace();
            return !wasActive; // Return old status on error
        }
    }

    /**
     * Get all system statuses for a guild from the active_modules column
     */
    public Map<String, Boolean> getGuildSystemsStatus(String guildId) {
        Map<String, Boolean> statuses = new HashMap<>();
        List<String> activeModules = new ArrayList<>();

        try (Connection connection = getConnection()) {
            String query = "SELECT active_modules FROM guilds WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String modulesStr = rs.getString("active_modules");
                if (modulesStr == null) {
                    // Default: All systems disabled
                    activeModules = List.of("");
                } else if (!modulesStr.isEmpty()) {
                    activeModules = Arrays.asList(modulesStr.split(","));
                }
            } else {
                activeModules = List.of("");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            activeModules = List.of("");
        }

        // Populate map
        for (String sys : ALL_SYSTEMS) {
            statuses.put(sys, activeModules.contains(sys));
        }

        return statuses;
    }

    public int getMessagesSentByDate (String guildId, String userId, String date) {
        String query = "SELECT messages_sent FROM user_statistics WHERE guild_id = ? AND user_id = ? AND date >= ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.setString(3, date);
            ResultSet rs = stmt.executeQuery();
            int count = 0;
            while (rs.next()) {
                count += rs.getInt("messages_sent");
            }
            return count;
        } catch (SQLException e) {
            System.err.println("Error fetching messages sent: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    public HashMap<String, Integer> getMessagesSentByDate (String guildId, String date) {
        String query = "SELECT user_id, messages_sent FROM user_statistics WHERE guild_id = ? AND date >= ?";
        HashMap<String, Integer> messagesMap = new HashMap<>();
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, date);
            ResultSet rs = stmt.executeQuery();
            int count = 0;
            String userId = null;
            while (rs.next()) {
                if (userId == null) {
                    userId = rs.getString("user_id");
                }
                if (userId.equals(rs.getString("user_id"))) {
                    int messagesSent = rs.getInt("messages_sent");
                    count += messagesSent;
                } else {
                    messagesMap.put(userId, count);
                }
                userId = rs.getString("user_id");
            }
            return messagesMap;
        } catch (SQLException e) {
            System.err.println("Error fetching messages sent: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    public void toggleMessageCountTracking (String guildId, boolean enable) {
        String query = "UPDATE guilds SET message_count_tracking = ? WHERE id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, enable ? 1 : 0);
            stmt.setString(2, guildId);
            stmt.executeUpdate();
            System.out.println("Message count tracking for guild " + guildId + " set to " + enable);
        } catch (SQLException e) {
            System.err.println("Error toggling message count tracking: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean doesGuildTrackMessages (String guildId) {
        String query = "SELECT message_count_tracking FROM guilds WHERE id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("message_count_tracking") == 1;
            } else {
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error checking message count tracking: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public void deleteUserData(String userId) {
        String[] tables = {
                "users",
                "user_statistics",
                "active_timers",
                "role_events",
                "custom_embeds"
        };

        try (Connection connection = getConnection()) {
            for (String table : tables) {
                String query = "DELETE FROM " + table + " WHERE user_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, userId);
                    int rowsAffected = stmt.executeUpdate();
                    System.out.println("Deleted " + rowsAffected + " rows from " + table + " for user " + userId);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error deleting user data for user " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
