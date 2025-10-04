package org.ToastiCodingStuff.Sloth;

import java.awt.Color;
import java.sql.*;
import java.util.*;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

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

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
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

    private final Connection connection;
    private final DatabaseMigrationManager migrationManager;
    
    public DatabaseHandler() {
        Connection connection1;
        try {
            // Connect to MariaDB on localhost
            // Use environment variables for configuration, with defaults
            String host = System.getenv().getOrDefault("DB_HOST", "localhost");
            String port = System.getenv().getOrDefault("DB_PORT", "3306");
            String database = System.getenv().getOrDefault("DB_NAME", "sloth");
            String user = System.getenv().getOrDefault("DB_USER", "root");
            String password = System.getenv().getOrDefault("DB_PASSWORD", "admin");
            
            String url = String.format("jdbc:mariadb://%s:%s/%s", host, port, database);
            System.out.println("Connecting to MariaDB database: " + url);
            connection1 = DriverManager.getConnection(url, user, password);
            System.out.println("Successfully connected to MariaDB database");
            connection1.setAutoCommit(true);
            if (connection1.getAutoCommit() == true) {
                System.out.println("Auto-commit is enabled");
            } else {
                System.out.println("Auto-commit is disabled");
            }
            // Initialize database tables

        } catch (SQLException e) {
            connection1 = null;
            System.err.println("Database connection error: " + e.getMessage());
        }
        this.connection = connection1;
        this.migrationManager = new DatabaseMigrationManager(connection1);
        initializeTables();
    }

    /**
     * Check if database tables already exist
     */
    private boolean tablesAlreadyExist() {
        try {
            DatabaseMetaData meta = connection.getMetaData();
            String[] tableNames = {
                "users", "warnings", "moderation_actions", "tickets", "ticket_messages",
                "guild_settings", "role_permissions", "bot_logs", "statistics",
                "temporary_data", "guilds", "guild_systems", "rules_embeds_channel, just_verify_button", "user_statistics"
            };
            for (String tableName : tableNames) {
                ResultSet rs = meta.getTables(null, null, tableName, null);
                if (!rs.next()) {
                    return false; // If any table does not exist, return false
                }
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
    private void initializeTables() {
        try {
            // MariaDB has foreign keys enabled by default
            Statement stmt = connection.createStatement();

            // Check for every table if already exist, if so apply migrations instead of full initialization
            if (tablesAlreadyExist()) {
                System.out.println("Database tables already exist. Running comprehensive migration check...");
                
                // Run the comprehensive migration system to detect and add missing columns
                migrationManager.detectAndApplyMissingColumns();
                
                // Apply any missing indexes for existing tables
                applyMissingIndexes();
                
                // Validate the final schema
                migrationManager.validateDatabaseSchema();
                
                return;
            }

            // If tables don't exist, create them from scratch
            System.out.println("Creating database tables from scratch...");
            createUsersTable();
            createGuildsTable();
            createWarningsTable();
            createModerationActionsTable();
            createTicketsTable();
            createTicketMessagesTable();
            createGuildSettingsTable();
            createRolePermissionsTable();
            createBotLogsTable();
            createStatisticsTable();
            createUserStatisticsTable();
            createTemporaryDataTable();
            createGuildSystemsTable();
            createRulesEmbedsChannel();
            createJustVerifyButtonTable();
            createSelectRolesMessagesTable();
            createSelectRolesOptionsTable();
            
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
            "button_emoji_id VARCHAR(64), " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        java.sql.Statement stmt = connection.createStatement();
        stmt.execute(createTable);
    }
    private void createGuildsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS guilds (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "discord_id VARCHAR(32) UNIQUE NOT NULL, " +
            "name VARCHAR(255) NOT NULL, " +
            "prefix VARCHAR(16) DEFAULT '!', " +
            "language VARCHAR(8) DEFAULT 'de', " +
            "created_at DATETIME, " +
            "updated_at DATETIME, " +
            "active TINYINT(1) DEFAULT 1" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
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
            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
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
                "severity ENUM('LOW', 'MEDIUM', 'HIGH', 'SEVERE') DEFAULT 'MEDIUM', " +
                "active TINYINT(1) DEFAULT 1, " +
                "expires_at DATETIME, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (guild_id) REFERENCES guilds(discord_id) ON DELETE CASCADE, " +
                "FOREIGN KEY (user_id) REFERENCES users(user_id), " +
                "FOREIGN KEY (moderator_id) REFERENCES users(user_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);

        // Indizes erstellen
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_warnings_guild_user ON warnings(guild_id, user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_warnings_active ON warnings(active)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_warnings_expires ON warnings(expires_at)");
    }

    private void createJustVerifyButtonTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS just_verify_button (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "guild_id VARCHAR(32) UNIQUE NOT NULL, " +
            "role_to_give_id VARCHAR(32) NOT NULL, " +
            "role_to_remove_id VARCHAR(32), " +
            "button_label VARCHAR(64) DEFAULT 'Verify', " +
            "button_emoji_id VARCHAR(64), " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
    }
    
    private void createSelectRolesMessagesTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS select_roles_messages (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "guild_id VARCHAR(32) NOT NULL, " +
            "message_id VARCHAR(32) NOT NULL, " +
            "channel_id VARCHAR(32) NOT NULL, " +
            "type VARCHAR(16) NOT NULL CHECK(type IN ('reactions', 'dropdown', 'buttons')), " +
            "title TEXT, " +
            "description TEXT, " +
            "ephemeral TINYINT(1) DEFAULT 0, " +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE, " +
            "INDEX idx_select_roles_guild (guild_id), " +
            "INDEX idx_select_roles_message (message_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
    }
    
    private void createSelectRolesOptionsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS select_roles_options (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "message_id VARCHAR(32) NOT NULL, " +
            "role_id VARCHAR(32) NOT NULL, " +
            "label VARCHAR(128), " +
            "description VARCHAR(256), " +
            "emoji VARCHAR(64), " +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
            "INDEX idx_select_roles_options_message (message_id)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
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
                "action_type ENUM('KICK', 'BAN', 'TEMP_BAN', 'UNBAN', 'WARN', 'TIMEOUT', 'UNTIMEOUT') NOT NULL, " +
                "reason TEXT NOT NULL, " +
                "duration INT, " +
                "expires_at DATETIME, " +
                "active TINYINT(1) DEFAULT 1, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (guild_id) REFERENCES guilds(discord_id) ON DELETE CASCADE, " +
                "FOREIGN KEY (user_id) REFERENCES users(user_id), " +
                "FOREIGN KEY (moderator_id) REFERENCES users(user_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
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
                    "status ENUM('OPEN', 'IN_PROGRESS', 'WAITING', 'CLOSED') DEFAULT 'OPEN', " +
                    "priority ENUM('LOW', 'MEDIUM', 'HIGH', 'URGENT') DEFAULT 'MEDIUM', " +
                    "assigned_to INT, " +
                    "closed_by INT, " +
                    "closed_reason TEXT, " +
                    "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                    "closed_at DATETIME, " +
                    "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE, " +
                    "FOREIGN KEY (user_id) REFERENCES users(id), " +
                    "FOREIGN KEY (assigned_to) REFERENCES users(id), " +
                    "FOREIGN KEY (closed_by) REFERENCES users(id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
                Statement stmt = connection.createStatement();
                stmt.execute(createTable);
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
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (ticket_id) REFERENCES tickets(id) ON DELETE CASCADE, " +
            "FOREIGN KEY (user_id) REFERENCES users(id)" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
    }

    public boolean removeRulesEmbedFromDatabase(String guildId, String embedId) {
        String deleteQuery = "DELETE FROM rules_embeds_channel WHERE guild_id = ? AND id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(deleteQuery)) {
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
            "updated_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
    }

    /**
     * Create role_permissions table
     */
    private void createRolePermissionsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS role_permissions (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "guild_id VARCHAR(32) NOT NULL, " +
            "role_id VARCHAR(32) NOT NULL, " +
            "permission TEXT NOT NULL, " +
            "allowed TINYINT(1) DEFAULT 1, " +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE, " +
            "UNIQUE(guild_id, role_id, permission)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);

        // Index erstellen
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_role_permissions_guild_role ON role_permissions(guild_id, role_id)");
    }

    /**
     * Create bot_logs table
     */
    private void createBotLogsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS bot_logs (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "guild_id VARCHAR(32), " +
            "user_id VARCHAR(32), " +
            "event_type VARCHAR(64) NOT NULL, " +
            "message TEXT NOT NULL, " +
            "data TEXT, " +
            "level ENUM('DEBUG', 'INFO', 'WARN', 'ERROR') DEFAULT 'INFO', " +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
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
            "FOREIGN KEY (guild_id) REFERENCES guilds(discord_id) ON DELETE CASCADE, " +
            "FOREIGN KEY (user_id) REFERENCES users(user_id), " +
            "UNIQUE(guild_id, user_id, date)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
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
            "FOREIGN KEY (guild_id) REFERENCES guilds(discord_id) ON DELETE CASCADE, " +
            "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE, " +
            "UNIQUE(guild_id, user_id, date)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
    }

    /**
     * Migrate existing statistics table to add timeout and verification columns
     * Note: This method is kept for backward compatibility. 
     * New migrations should use updateTableColumns() for a more generic approach.
     */
    private void migrateStatisticsTable() throws SQLException {
        Statement stmt = connection.createStatement();

        // Prüfe, ob die Spalten bereits existieren
        String checkColumns = "SHOW COLUMNS FROM statistics";
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

        Statement stmt = connection.createStatement();
        boolean columnsAdded = false;

        try {
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

        } finally {
            stmt.close();
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
     * Create temporary_data table
     */
    private void createTemporaryDataTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS temporary_data (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "guild_id VARCHAR(32) NOT NULL, " +
            "user_id VARCHAR(32) NOT NULL, " +
            "data_type VARCHAR(64) NOT NULL, " +
            "data TEXT NOT NULL, " +
            "expires_at DATETIME NOT NULL, " +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(discord_id) ON DELETE CASCADE, " +
            "FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
    }

    /**
     * Create guild_systems table
     */
    private void createGuildSystemsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS guild_systems (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "guild_id VARCHAR(32) NOT NULL, " +
            "system_type VARCHAR(64) NOT NULL, " +
            "active TINYINT(1) DEFAULT 1, " +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(discord_id) ON DELETE CASCADE, " +
            "UNIQUE(guild_id, system_type)" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);

        // Index erstellen
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_guild_systems_guild_active ON guild_systems(guild_id, active)");
    }

    /**
     * Create legacy tables for backward compatibility
     */
    private void createLegacyTables() throws SQLException {
        // Erstelle log_channels Tabelle (MariaDB-Syntax, VARCHAR für IDs)
        String logChannelsTable = "CREATE TABLE IF NOT EXISTS log_channels (" +
            "guildid VARCHAR(32) PRIMARY KEY, " +
            "channelid VARCHAR(32) NOT NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        // Erstelle warn_system_settings Tabelle (MariaDB-Syntax, VARCHAR für IDs)
        String warnSystemTable = "CREATE TABLE IF NOT EXISTS warn_system_settings (" +
            "guild_id VARCHAR(32) PRIMARY KEY, " +
            "max_warns INT NOT NULL, " +
            "minutes_muted INT NOT NULL, " +
            "role_id VARCHAR(32) NOT NULL, " +
            "warn_time_hours INT NOT NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        Statement stmt = connection.createStatement();
        stmt.execute(logChannelsTable);
        stmt.execute(warnSystemTable);
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
        try {
            if (connection != null) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    //add Embed to Database
    public Boolean addRulesEmbedToDatabase(String guildID, String title, String description, String footer, String color, String roleId, String buttonLabel, String buttonEmoji) {
        try {
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
        try {
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
        try {
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
        try {
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
        try {
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
       try {
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
        try {
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
        try {
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
        try {
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

    public int getTimeMuted(String guildID) {
        try {
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
        try {
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
        try {
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
        try {
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
        try {
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
        try {
            String query = "SELECT COUNT(*) as count FROM warnings WHERE guild_id = ? AND user_id = ? AND active = 1 AND (expires_at IS NULL OR expires_at > NOW())";
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
        try {
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
        try {
            String insertWarning = "INSERT INTO warnings (guild_id, user_id, moderator_id, reason, severity, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertWarning, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.setString(3, moderatorId);
            stmt.setString(4, reason);
            stmt.setString(5, severity);
            stmt.setString(6, expiresAt);
            
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

    public void insertOrUpdateUser(String userId, String effectiveName, String discriminator, String avatarUrl) {
        try {
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
        try {
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
        try {
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
        try {
            // MariaDB-Syntax: IDs als VARCHAR(32)
            String query = "SELECT prefix FROM guilds WHERE discord_id = ? AND active = 1";
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
        try {
            // MariaDB-Syntax: IDs als VARCHAR(32)
            String query = "SELECT language FROM guilds WHERE discord_id = ? AND active = 1";
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
        try {
            // MariaDB-Syntax: guildId ist VARCHAR(32)
            String updatePrefix = "UPDATE guilds SET prefix = ?, updated_at = CURRENT_TIMESTAMP WHERE discord_id = ? AND active = 1";
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
        try {
            // MariaDB-Syntax: guildId ist VARCHAR(32)
            String updateLanguage = "UPDATE guilds SET language = ?, updated_at = CURRENT_TIMESTAMP WHERE discord_id = ? AND active = 1";
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
        try {
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
        try {
            // Transaktion starten
            connection.setAutoCommit(false);

            for (Guild guild : currentGuilds) {
                String guildId = guild.getId();
                String guildName = guild.getName();

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
            connection.setAutoCommit(true);

        } catch (SQLException e) {
            try {
                // Bei Fehler: Rollback
                connection.rollback();
                connection.setAutoCommit(true);
            } catch (SQLException ex) {
                System.err.println("Error during rollback: " + ex.getMessage());
            }
            System.err.println("Error syncing guilds: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isTicketSystem(String guildId) {
        try {
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
        try {
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
        try {
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
        try {
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
        try {
            // Sicherstellen, dass die Guild in der Tabelle existiert (MariaDB: discord_id als VARCHAR(32))
            String checkGuildQuery = "SELECT discord_id FROM guilds WHERE discord_id = ?";
            PreparedStatement checkGuildStmt = connection.prepareStatement(checkGuildQuery);
            checkGuildStmt.setString(1, guildId);
            ResultSet guildResult = checkGuildStmt.executeQuery();

            if (!guildResult.next()) {
                String insertGuildQuery = "INSERT INTO guilds (discord_id) VALUES (?)";
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
        try {
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
        try {
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
        try {
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
        try {
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
        try {
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
     * Ticket-Priorität aktualisieren (MariaDB-Syntax)
     */
    public boolean updateTicketPriority(int ticketId, String priority) {
        try {
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
        try {
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
    private String getCurrentDate() {
        return java.time.LocalDate.now().toString();
    }

    /**
     * Update statistics for a guild and specific action type
     */
    private void updateStatistics(String guildId, String actionType) {
        try {
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
        try {
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

    // USER STATISTICS FUNCTIONS

    /**
     * Update statistics for a user and specific action type
     */
    private void updateUserStatistics(String guildId, String userId, String actionType) {
        try {
            String currentDate = getCurrentDate();

            // Nur erlaubte Spaltennamen zulassen (SQL-Injection vermeiden)
            java.util.Set<String> allowedActions = java.util.Set.of(
                    "messages_sent", "commands_used", "timeouts_performed", "untimeouts_performed",
                    "verifications_performed", "untimeouts_received", "timeouts_received",
                    "bans_performed", "bans_received", "kicks_performed", "kicks_received",
                    "warnings_issued", "warnings_received", "tickets_created", "tickets_closed"
            );

            if (!allowedActions.contains(actionType)) {
                throw new IllegalArgumentException("Ungültiger Spaltenname für Statistik: " + actionType);
            }

            // UPDATE versuchen
            String updateQuery = null;
            if (userExistsInUserStatistics(guildId, userId)) {
            }updateQuery = "UPDATE user_statistics SET " + actionType + " = " + actionType +
                    " + ? WHERE guild_id = ? AND user_id = ? AND date = ?";
            PreparedStatement updateStmt = connection.prepareStatement(updateQuery);
            updateStmt.setInt(1, 1);
            updateStmt.setString(2, guildId);
            updateStmt.setString(3, userId);
            updateStmt.setString(4, currentDate);

            int rowsUpdated = updateStmt.executeUpdate();

            // Wenn kein Datensatz existiert, neuen einfügen
            if (rowsUpdated == 0) {
                // ID wird durch AUTO_INCREMENT in der Datenbank generiert
                String insertQuery = "INSERT INTO user_statistics (guild_id, user_id, date, " +
                        actionType + ") VALUES (?, ?, ?, ?)";
                PreparedStatement insertStmt = connection.prepareStatement(insertQuery);
                insertStmt.setString(1, guildId);
                insertStmt.setString(2, userId);
                insertStmt.setString(3, currentDate);
                insertStmt.setInt(4, 1);
                insertStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error updating user statistics: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean userExistsInUserStatistics(String guildId, String userId) {
        String currentDate = getCurrentDate();
        String checkQuery = "SELECT id FROM user_statistics WHERE guild_id = ? AND user_id = ? AND date = ?";
        try {
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
        try {
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
        try {
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
        try {
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
        try {
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

        try {
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

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
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

        try {
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

    public String getJustVerifyButtonRoleToGiveID(String guildId) {
        String query = "SELECT role_to_give_id FROM just_verify_button WHERE guild_id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
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
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
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
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
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
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
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
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
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
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
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
            try (PreparedStatement pstmt = connection.prepareStatement(query)) {
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
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
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
    
    // ==================== Select Roles Methods ====================
    
    /**
     * Create a new select roles message
     */
    public String createSelectRolesMessage(String guildId, String messageId, String channelId, 
                                          String type, String title, String description, boolean ephemeral) {
        try {
            String query = "INSERT INTO select_roles_messages (guild_id, message_id, channel_id, type, title, description, ephemeral) " +
                          "VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            stmt.setString(2, messageId);
            stmt.setString(3, channelId);
            stmt.setString(4, type);
            stmt.setString(5, title);
            stmt.setString(6, description);
            stmt.setInt(7, ephemeral ? 1 : 0);
            stmt.executeUpdate();
            return messageId;
        } catch (SQLException e) {
            System.err.println("Error creating select roles message: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Add a role option to a select roles message
     */
    public boolean addSelectRoleOption(String messageId, String roleId, String label, 
                                      String description, String emoji) {
        try {
            String query = "INSERT INTO select_roles_options (message_id, role_id, label, description, emoji) " +
                          "VALUES (?, ?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, messageId);
            stmt.setString(2, roleId);
            stmt.setString(3, label);
            stmt.setString(4, description);
            stmt.setString(5, emoji);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error adding select role option: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Remove a role option from a select roles message
     */
    public boolean removeSelectRoleOption(String messageId, String roleId) {
        try {
            String query = "DELETE FROM select_roles_options WHERE message_id = ? AND role_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, messageId);
            stmt.setString(2, roleId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error removing select role option: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Delete a select roles message and all its options
     */
    public boolean deleteSelectRolesMessage(String messageId) {
        try {
            // First delete all options
            String deleteOptions = "DELETE FROM select_roles_options WHERE message_id = ?";
            PreparedStatement stmt1 = connection.prepareStatement(deleteOptions);
            stmt1.setString(1, messageId);
            stmt1.executeUpdate();
            
            // Then delete the message
            String deleteMessage = "DELETE FROM select_roles_messages WHERE message_id = ?";
            PreparedStatement stmt2 = connection.prepareStatement(deleteMessage);
            stmt2.setString(1, messageId);
            int rowsAffected = stmt2.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting select roles message: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get select roles message data
     */
    public SelectRolesMessageData getSelectRolesMessage(String messageId) {
        try {
            String query = "SELECT * FROM select_roles_messages WHERE message_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, messageId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return new SelectRolesMessageData(
                    rs.getString("guild_id"),
                    rs.getString("message_id"),
                    rs.getString("channel_id"),
                    rs.getString("type"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getInt("ephemeral") == 1
                );
            }
        } catch (SQLException e) {
            System.err.println("Error getting select roles message: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Get all role options for a select roles message
     */
    public List<SelectRoleOption> getSelectRoleOptions(String messageId) {
        List<SelectRoleOption> options = new ArrayList<>();
        try {
            String query = "SELECT * FROM select_roles_options WHERE message_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, messageId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                options.add(new SelectRoleOption(
                    rs.getString("role_id"),
                    rs.getString("label"),
                    rs.getString("description"),
                    rs.getString("emoji")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting select role options: " + e.getMessage());
            e.printStackTrace();
        }
        return options;
    }
    
    /**
     * Check if a select roles message exists
     */
    public boolean selectRolesMessageExists(String messageId) {
        try {
            String query = "SELECT COUNT(*) FROM select_roles_messages WHERE message_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, messageId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("Error checking if select roles message exists: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Data class for select roles message
     */
    public static class SelectRolesMessageData {
        public final String guildId;
        public final String messageId;
        public final String channelId;
        public final String type;
        public final String title;
        public final String description;
        public final boolean ephemeral;
        
        public SelectRolesMessageData(String guildId, String messageId, String channelId, 
                                     String type, String title, String description, boolean ephemeral) {
            this.guildId = guildId;
            this.messageId = messageId;
            this.channelId = channelId;
            this.type = type;
            this.title = title;
            this.description = description;
            this.ephemeral = ephemeral;
        }
    }
    
    /**
     * Data class for select role option
     */
    public static class SelectRoleOption {
        public final String roleId;
        public final String label;
        public final String description;
        public final String emoji;
        
        public SelectRoleOption(String roleId, String label, String description, String emoji) {
            this.roleId = roleId;
            this.label = label;
            this.description = description;
            this.emoji = emoji;
        }
    }
}
