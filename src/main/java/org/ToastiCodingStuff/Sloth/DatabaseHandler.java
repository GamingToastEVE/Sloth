package org.ToastiCodingStuff.Sloth;

import java.awt.Color;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.json.JSONArray;
import org.json.JSONObject;

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
            Dotenv dotenv = Dotenv.load();
            String host = dotenv.get("DB_HOST", "localhost");
            String port = dotenv.get("DB_PORT", "3306");
            String database = dotenv.get("DB_NAME", "sloth");
            String user = dotenv.get("DB_USER", "root");
            String password = dotenv.get("DB_PASSWORD", "admin");
            
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
                    "users", "warnings", "moderation_actions", "tickets",
                    "guild_settings", "role_permissions", "statistics", "guilds", "guild_systems", "rules_embeds_channel", "just_verify_button", "user_statistics", "role_select", "role_select_embeds", "role_select_groups", "active_timers", "role_events", "custom_embeds", "member_roles"
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
                        case "role_select_groups":
                            createSelectRolesGroupsTable();
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
                        case "member_roles":
                            createMemberRolesTable();
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

            // Remove tables that no longer belong to any feature
            dropObsoleteTables();

            // Validate the final schema
            migrationManager.validateDatabaseSchema();

        } catch (SQLException e) {
            System.err.println("Error initializing database tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Tables that used to belong to removed features.
     * <p>
     * ticket_messages was created for the ticket transcript feature. It was never written
     * to and the feature is gone, but a column literally named "content" in a schema
     * contradicts the privacy policy's statement that no message content is stored.
     */
    private static final String[] OBSOLETE_TABLES = {"ticket_messages"};

    /**
     * Drop obsolete tables, but only when they are empty. A non-empty table is left in
     * place with a warning rather than silently destroying data that was not expected
     * to be there.
     */
    private void dropObsoleteTables() {
        for (String table : OBSOLETE_TABLES) {
            try (Connection connection = getConnection()) {
                int rows;
                try (ResultSet rs = connection.createStatement().executeQuery("SELECT COUNT(*) FROM " + table)) {
                    rows = rs.next() ? rs.getInt(1) : 0;
                }

                if (rows > 0) {
                    System.err.println("Obsolete table '" + table + "' still holds " + rows
                            + " row(s) - leaving it in place. Remove it manually once the data is handled.");
                    continue;
                }

                connection.createStatement().execute("DROP TABLE " + table);
                System.out.println("Dropped obsolete empty table '" + table + "'");
            } catch (SQLException e) {
                // Table does not exist (already dropped, or a fresh database) - nothing to do
            }
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
            "group_id INT DEFAULT NULL, " +
            "position INT DEFAULT 0, " +
            "label VARCHAR(64), " +
            "description VARCHAR(255), " +
            "emoji_id VARCHAR(64), " +
            "created_at DATETIME DEFAULT CURRENT_TIMESTAMP)";
        try (Connection connection = getConnection();
             Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }

    private void createSelectRolesGroupsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS role_select_groups (" +
            "id INT PRIMARY KEY AUTO_INCREMENT, " +
            "guild_id VARCHAR(32) NOT NULL, " +
            "name VARCHAR(64) NOT NULL, " +
            "position INT DEFAULT 0, " +
            "title VARCHAR(255), " +
            "description TEXT, " +
            "footer TEXT, " +
            "color VARCHAR(32) DEFAULT '#3498db', " +
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
            "group_id INT DEFAULT NULL, " +
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
            "active TINYINT(1) DEFAULT 1, " +
            "left_at DATETIME NULL)";
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

    // ==================== MEMBER ROLES SNAPSHOT TABLE ====================

    private void createMemberRolesTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS member_roles (" +
                "id INT PRIMARY KEY AUTO_INCREMENT, " +
                "guild_id VARCHAR(32) NOT NULL, " +
                "user_id VARCHAR(32) NOT NULL, " +
                "role_ids TEXT NOT NULL DEFAULT '', " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE(guild_id, user_id))";
        try (Connection connection = getConnection(); Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
            System.out.println("Table 'member_roles' created successfully.");
        }
    }

    /**
     * Returns the stored role IDs for a guild member.
     * Returns an empty list if no snapshot exists yet.
     */
    public List<String> getMemberRoles(String guildId, String userId) {
        String query = "SELECT role_ids FROM member_roles WHERE guild_id = ? AND user_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String raw = rs.getString("role_ids");
                    if (raw == null || raw.isBlank()) return new ArrayList<>();
                    return new ArrayList<>(Arrays.asList(raw.split(",")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching member roles snapshot: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * Upserts the role snapshot for a guild member.
     *
     * @param guildId  the guild ID
     * @param userId   the user ID
     * @param roleIds  current list of role IDs (excluding @everyone)
     */
    public void setMemberRoles(String guildId, String userId, List<String> roleIds) {
        String csv = String.join(",", roleIds);
        String query = "INSERT INTO member_roles (guild_id, user_id, role_ids, updated_at) VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON DUPLICATE KEY UPDATE role_ids = VALUES(role_ids), updated_at = CURRENT_TIMESTAMP";
        try (Connection connection = getConnection();
             PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, userId);
            pstmt.setString(3, csv);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error updating member roles snapshot: " + e.getMessage());
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
            migrationManager.detectAndApplyMissingTables();
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
        public final String severity;
        public final String evidence;
        public final String expiresAt;

        public WarningData(int id, String reason, String moderatorId, String date, String severity, String evidence, String expiresAt) {
            this.id = id;
            this.reason = reason;
            this.moderatorId = moderatorId;
            this.date = date;
            this.severity = severity;
            this.evidence = evidence;
            this.expiresAt = expiresAt;
        }
    }

    public void removeInactiveWarnings() {
        String query = "UPDATE warnings SET active = 0 WHERE expires_at IS NOT NULL AND expires_at <= CURRENT_TIMESTAMP AND (active = 1 OR active IS NULL)";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            int rowsDeleted = stmt.executeUpdate();
            System.out.println("Removed " + rowsDeleted + " inactive warnings from the database.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 2. Methode zum Abrufen der aktiven Warns eines Users
    public List<WarningData> getUserActiveWarnings(String guildId, String userId) {
        List<WarningData> warnings = new ArrayList<>();
        // Wir holen nur aktive Warns
        String query = "SELECT id, reason, moderator_id, created_at, severity, evidence, expires_at FROM warnings WHERE guild_id = ? AND user_id = ? AND (active = 1 OR active IS NULL) ORDER BY created_at ASC LIMIT 25";

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
                            rs.getString("created_at"),
                            rs.getString("severity"),
                            rs.getString("evidence"),
                            rs.getString("expires_at")));
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

    public int insertWarning(String guildId, String userId, String moderatorId, String reason, String severity, String expiresAt, String evidence) {
        try (Connection connection = getConnection()) {
            String insertWarning = "INSERT INTO warnings (guild_id, user_id, moderator_id, reason, severity, active, expires_at, created_at, evidence) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertWarning, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.setString(3, moderatorId);
            stmt.setString(4, reason);
            stmt.setString(5, severity);
            stmt.setInt(6, 1);
            stmt.setString(7, expiresAt);
            stmt.setString(8, evidence);
            
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
                    "VALUES (?, ?, '!', 'en', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1) " +
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
     * Alias for getGuildLanguage - used by LanguageManager
     */
    public String getGuildLanguageSetting(String guildId) {
        return getGuildLanguage(guildId);
    }

    /**
     * Alias for updateGuildLanguage - used by LanguageManager
     */
    public boolean updateGuildLanguageSetting(String guildId, String language) {
        return updateGuildLanguage(guildId, language);
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

                    // INSERT ... ON DUPLICATE KEY UPDATE verwenden statt Trigger.
                    // active/left_at werden mit zurueckgesetzt: wird der Bot einem Server
                    // wieder hinzugefuegt waehrend er offline ist, gibt es kein GuildJoinEvent,
                    // und ohne das Zuruecksetzen wuerde die Aufbewahrungsfrist weiterlaufen
                    // und die Daten eines aktiven Servers loeschen.
                    String upsertQuery = "INSERT INTO guilds (id, name) VALUES (?, ?) " +
                            "ON DUPLICATE KEY UPDATE name = ?, active = 1, left_at = NULL";
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
     * Get the ticket ID for a channel, or null if the channel is not a ticket.
     * Preferred over parsing the string from {@link #getTicketByChannelId(String)}.
     */
    public Integer getTicketIdByChannelId(String channelId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT id FROM tickets WHERE channel_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, channelId);
            ResultSet rs = stmt.executeQuery();

            return rs.next() ? rs.getInt("id") : null;
        } catch (SQLException e) {
            System.err.println("Error getting ticket id by channel: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get the panel a ticket channel belongs to, or null for tickets created before
     * panels existed.
     */
    public TicketPanelData getTicketPanelByChannelId(String channelId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT panel_id FROM tickets WHERE channel_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, channelId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int panelId = rs.getInt("panel_id");
                if (!rs.wasNull() && panelId > 0) {
                    return getTicketPanel(panelId);
                }
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error getting ticket panel by channel: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Support role responsible for a ticket channel: the role of the panel the ticket was
     * opened from, falling back to the guild-wide legacy setting for tickets that predate
     * panels or panels without their own role.
     */
    public String resolveTicketSupportRole(String guildId, String channelId) {
        TicketPanelData panel = getTicketPanelByChannelId(channelId);
        if (panel != null && panel.supportRoleId != null && !panel.supportRoleId.isBlank()) {
            return panel.supportRoleId;
        }
        return getTicketRole(guildId);
    }

    /**
     * Discord category a ticket channel lives in, resolved the same way as the support
     * role: panel first, legacy guild setting second.
     */
    public String resolveTicketDiscordCategory(String guildId, String channelId) {
        TicketPanelData panel = getTicketPanelByChannelId(channelId);
        if (panel != null && panel.categoryId != null && !panel.categoryId.isBlank()) {
            return panel.categoryId;
        }
        return getTicketCategory(guildId);
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

    // ==================== TICKET PANELS (MULTIPLE SYSTEMS) ====================

    /**
     * Data class to hold ticket panel information
     */
    public static class TicketPanelData {
        public final int id;
        public final String guildId;
        public final String name;
        public final String title;
        public final String description;
        public final String buttonLabel;
        public final String buttonEmoji;
        public final String buttonColor;
        public final String categoryId;
        public final String channelId;
        public final String supportRoleId;
        public final String pingRoleId;
        public final String welcomeMessage;
        public final String embedColor;
        public final String embedFooter;
        public final String embedThumbnail;
        public final int position;
        public final int maxTicketsPerUser;
        public final boolean requireSubject;
        public final boolean requireDescription;
        public final String panelMessageId;

        public TicketPanelData(int id, String guildId, String name, String title, String description,
                               String buttonLabel, String buttonEmoji, String buttonColor,
                               String categoryId, String channelId, String supportRoleId, String pingRoleId,
                               String welcomeMessage, String embedColor, String embedFooter, String embedThumbnail,
                               int position, int maxTicketsPerUser, boolean requireSubject, boolean requireDescription,
                               String panelMessageId) {
            this.id = id;
            this.guildId = guildId;
            this.name = name;
            this.title = title != null ? title : "🎫 Create a Ticket";
            this.description = description != null ? description : "Click the button below to create a support ticket.";
            this.buttonLabel = buttonLabel != null ? buttonLabel : "📩 Create Ticket";
            this.buttonEmoji = buttonEmoji;
            this.buttonColor = buttonColor != null ? buttonColor : "PRIMARY";
            this.categoryId = categoryId;
            this.channelId = channelId;
            this.supportRoleId = supportRoleId;
            this.pingRoleId = pingRoleId;
            this.welcomeMessage = welcomeMessage != null ? welcomeMessage : "Welcome to your support ticket! A staff member will assist you shortly.";
            this.embedColor = embedColor != null ? embedColor : "#5865F2";
            this.embedFooter = embedFooter;
            this.embedThumbnail = embedThumbnail;
            this.position = position;
            this.maxTicketsPerUser = maxTicketsPerUser;
            this.requireSubject = requireSubject;
            this.requireDescription = requireDescription;
            this.panelMessageId = panelMessageId;
        }
    }

    /**
     * Create a new ticket panel for a guild
     */
    public int createTicketPanel(String guildId, String name) {
        try (Connection connection = getConnection()) {
            // Get next position
            String posQuery = "SELECT COALESCE(MAX(position), -1) + 1 AS next_pos FROM ticket_panels WHERE guild_id = ?";
            PreparedStatement posStmt = connection.prepareStatement(posQuery);
            posStmt.setString(1, guildId);
            ResultSet posRs = posStmt.executeQuery();
            int nextPosition = posRs.next() ? posRs.getInt("next_pos") : 0;

            String insertQuery = "INSERT INTO ticket_panels (guild_id, name, position) VALUES (?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, guildId);
            stmt.setString(2, name);
            stmt.setInt(3, nextPosition);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("Error creating ticket panel: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Get all ticket panels for a guild
     */
    public List<TicketPanelData> getTicketPanels(String guildId) {
        List<TicketPanelData> panels = new ArrayList<>();
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM ticket_panels WHERE guild_id = ? ORDER BY position ASC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                panels.add(mapResultSetToTicketPanel(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error getting ticket panels: " + e.getMessage());
            e.printStackTrace();
        }
        return panels;
    }

    /**
     * Get a specific ticket panel by ID
     */
    public TicketPanelData getTicketPanel(int panelId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM ticket_panels WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setInt(1, panelId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToTicketPanel(rs);
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error getting ticket panel: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get a ticket panel by guild and name
     */
    public TicketPanelData getTicketPanelByName(String guildId, String name) {
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM ticket_panels WHERE guild_id = ? AND name = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            stmt.setString(2, name);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToTicketPanel(rs);
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error getting ticket panel by name: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private TicketPanelData mapResultSetToTicketPanel(ResultSet rs) throws SQLException {
        return new TicketPanelData(
            rs.getInt("id"),
            rs.getString("guild_id"),
            rs.getString("name"),
            rs.getString("title"),
            rs.getString("description"),
            rs.getString("button_label"),
            rs.getString("button_emoji"),
            rs.getString("button_color"),
            rs.getString("category_id"),
            rs.getString("channel_id"),
            rs.getString("support_role_id"),
            rs.getString("ping_role_id"),
            rs.getString("welcome_message"),
            rs.getString("embed_color"),
            rs.getString("embed_footer"),
            rs.getString("embed_thumbnail"),
            rs.getInt("position"),
            rs.getInt("max_tickets_per_user"),
            rs.getInt("require_subject") == 1,
            rs.getInt("require_description") == 1,
            rs.getString("panel_message_id")
        );
    }

    /**
     * Update a ticket panel's basic settings
     */
    public boolean updateTicketPanel(int panelId, String name, String title, String description,
                                      String buttonLabel, String buttonEmoji, String buttonColor) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE ticket_panels SET name = ?, title = ?, description = ?, " +
                    "button_label = ?, button_emoji = ?, button_color = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(updateQuery);
            stmt.setString(1, name);
            stmt.setString(2, title);
            stmt.setString(3, description);
            stmt.setString(4, buttonLabel);
            stmt.setString(5, buttonEmoji);
            stmt.setString(6, buttonColor);
            stmt.setInt(7, panelId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating ticket panel: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update a ticket panel's channel settings.
     * Partial update: a null argument leaves the corresponding column unchanged,
     * so callers can set a single value without having to re-supply the others.
     */
    public boolean updateTicketPanelChannels(int panelId, String categoryId, String channelId,
                                              String supportRoleId, String pingRoleId) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE ticket_panels SET category_id = COALESCE(?, category_id), " +
                    "channel_id = COALESCE(?, channel_id), " +
                    "support_role_id = COALESCE(?, support_role_id), " +
                    "ping_role_id = COALESCE(?, ping_role_id), " +
                    "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(updateQuery);
            stmt.setString(1, categoryId);
            stmt.setString(2, channelId);
            stmt.setString(3, supportRoleId);
            stmt.setString(4, pingRoleId);
            stmt.setInt(5, panelId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating ticket panel channels: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update a ticket panel's appearance settings
     */
    public boolean updateTicketPanelAppearance(int panelId, String embedColor, String embedFooter,
                                                String embedThumbnail, String welcomeMessage) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE ticket_panels SET embed_color = ?, embed_footer = ?, " +
                    "embed_thumbnail = ?, welcome_message = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(updateQuery);
            stmt.setString(1, embedColor);
            stmt.setString(2, embedFooter);
            stmt.setString(3, embedThumbnail);
            stmt.setString(4, welcomeMessage);
            stmt.setInt(5, panelId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating ticket panel appearance: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update only a ticket panel's max tickets per user, leaving the subject and
     * description requirements untouched. 0 means unlimited.
     */
    public boolean updateTicketPanelMaxTickets(int panelId, int maxTicketsPerUser) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE ticket_panels SET max_tickets_per_user = ?, " +
                    "updated_at = CURRENT_TIMESTAMP WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(updateQuery);
            stmt.setInt(1, maxTicketsPerUser);
            stmt.setInt(2, panelId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating ticket panel max tickets: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update ticket panel settings (max tickets, requirements)
     */
    public boolean updateTicketPanelSettings(int panelId, int maxTicketsPerUser,
                                              boolean requireSubject, boolean requireDescription) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE ticket_panels SET max_tickets_per_user = ?, require_subject = ?, " +
                    "require_description = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(updateQuery);
            stmt.setInt(1, maxTicketsPerUser);
            stmt.setInt(2, requireSubject ? 1 : 0);
            stmt.setInt(3, requireDescription ? 1 : 0);
            stmt.setInt(4, panelId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating ticket panel settings: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update ticket panel's sent message ID
     */
    public boolean updateTicketPanelMessageId(int panelId, String messageId) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE ticket_panels SET panel_message_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(updateQuery);
            stmt.setString(1, messageId);
            stmt.setInt(2, panelId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating ticket panel message ID: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete a ticket panel
     */
    public boolean deleteTicketPanel(int panelId) {
        try (Connection connection = getConnection()) {
            // Get panel info first for position reordering
            TicketPanelData panel = getTicketPanel(panelId);
            if (panel == null) return false;

            String deleteQuery = "DELETE FROM ticket_panels WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(deleteQuery);
            stmt.setInt(1, panelId);

            boolean deleted = stmt.executeUpdate() > 0;

            // Reorder remaining panels
            if (deleted) {
                String reorderQuery = "UPDATE ticket_panels SET position = position - 1 WHERE guild_id = ? AND position > ?";
                PreparedStatement reorderStmt = connection.prepareStatement(reorderQuery);
                reorderStmt.setString(1, panel.guildId);
                reorderStmt.setInt(2, panel.position);
                reorderStmt.executeUpdate();
            }

            return deleted;
        } catch (SQLException e) {
            System.err.println("Error deleting ticket panel: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Move a ticket panel up in order
     */
    public boolean moveTicketPanelUp(String guildId, int panelId) {
        return swapTicketPanelPosition(guildId, panelId, -1);
    }

    /**
     * Move a ticket panel down in order
     */
    public boolean moveTicketPanelDown(String guildId, int panelId) {
        return swapTicketPanelPosition(guildId, panelId, 1);
    }

    private boolean swapTicketPanelPosition(String guildId, int panelId, int direction) {
        try (Connection connection = getConnection()) {
            TicketPanelData panel = getTicketPanel(panelId);
            if (panel == null) return false;

            int newPosition = panel.position + direction;
            if (newPosition < 0) return false;

            // Find panel at target position
            String findQuery = "SELECT id FROM ticket_panels WHERE guild_id = ? AND position = ?";
            PreparedStatement findStmt = connection.prepareStatement(findQuery);
            findStmt.setString(1, guildId);
            findStmt.setInt(2, newPosition);
            ResultSet rs = findStmt.executeQuery();

            if (!rs.next()) return false; // No panel at target position
            int otherPanelId = rs.getInt("id");

            // Swap positions
            String updateQuery = "UPDATE ticket_panels SET position = ? WHERE id = ?";
            PreparedStatement updateStmt = connection.prepareStatement(updateQuery);

            updateStmt.setInt(1, newPosition);
            updateStmt.setInt(2, panelId);
            updateStmt.executeUpdate();

            updateStmt.setInt(1, panel.position);
            updateStmt.setInt(2, otherPanelId);
            updateStmt.executeUpdate();

            return true;
        } catch (SQLException e) {
            System.err.println("Error swapping ticket panel position: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get the count of open tickets for a user in a specific panel
     */
    public int getUserOpenTicketCount(String guildId, String userId, int panelId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT COUNT(*) FROM tickets WHERE guild_id = ? AND user_id = ? AND panel_id = ? AND status != 'CLOSED'";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.setInt(3, panelId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("Error getting user open ticket count: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Create a new ticket with panel ID
     */
    public int createTicketWithPanel(String guildId, String userId, String channelId, int panelId,
                                      String subject, String priority, String username, String discriminator, String avatarUrl) {
        try (Connection connection = getConnection()) {
            insertOrUpdateUser(userId, username, discriminator, avatarUrl);

            String insertTicket = "INSERT INTO tickets (guild_id, user_id, channel_id, panel_id, subject, priority, status) VALUES (?, ?, ?, ?, ?, ?, 'OPEN')";
            PreparedStatement stmt = connection.prepareStatement(insertTicket, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.setString(3, channelId);
            stmt.setInt(4, panelId);
            stmt.setString(5, subject);
            stmt.setString(6, priority != null ? priority : "MEDIUM");

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("Error creating ticket with panel: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Get ticket panel ID by channel ID (for existing tickets)
     */
    public Integer getTicketPanelIdByChannel(String channelId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT panel_id FROM tickets WHERE channel_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, channelId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int panelId = rs.getInt("panel_id");
                return rs.wasNull() ? null : panelId;
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error getting ticket panel ID by channel: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // ==================== END TICKET PANELS ====================

    // ==================== TICKET CATEGORIES ====================

    /**
     * Data class to hold ticket category information
     */
    public static class TicketCategoryData {
        public final int id;
        public final int panelId;
        public final String name;
        public final String description;
        public final String buttonLabel;
        public final String buttonEmoji;
        public final String buttonColor;
        public final String categoryId;
        public final String welcomeMessage;
        public final int position;

        public TicketCategoryData(int id, int panelId, String name, String description,
                                   String buttonLabel, String buttonEmoji, String buttonColor,
                                   String categoryId, String welcomeMessage, int position) {
            this.id = id;
            this.panelId = panelId;
            this.name = name;
            this.description = description;
            this.buttonLabel = buttonLabel != null ? buttonLabel : name;
            this.buttonEmoji = buttonEmoji;
            this.buttonColor = buttonColor != null ? buttonColor : "PRIMARY";
            this.categoryId = categoryId;
            this.welcomeMessage = welcomeMessage;
            this.position = position;
        }
    }

    /**
     * Create a new ticket category
     */
    public int createTicketCategory(int panelId, String name, String buttonLabel) {
        try (Connection connection = getConnection()) {
            String posQuery = "SELECT COALESCE(MAX(position), -1) + 1 AS next_pos FROM ticket_categories WHERE panel_id = ?";
            PreparedStatement posStmt = connection.prepareStatement(posQuery);
            posStmt.setInt(1, panelId);
            ResultSet posRs = posStmt.executeQuery();
            int nextPosition = posRs.next() ? posRs.getInt("next_pos") : 0;

            String insertQuery = "INSERT INTO ticket_categories (panel_id, name, button_label, position) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, panelId);
            stmt.setString(2, name);
            stmt.setString(3, buttonLabel);
            stmt.setInt(4, nextPosition);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("Error creating ticket category: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Get all ticket categories for a panel
     */
    public List<TicketCategoryData> getTicketCategories(int panelId) {
        List<TicketCategoryData> categories = new ArrayList<>();
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM ticket_categories WHERE panel_id = ? ORDER BY position ASC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setInt(1, panelId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                categories.add(mapResultSetToTicketCategory(rs));
            }
        } catch (SQLException e) {
            System.err.println("Error getting ticket categories: " + e.getMessage());
            e.printStackTrace();
        }
        return categories;
    }

    /**
     * Get a specific ticket category
     */
    public TicketCategoryData getTicketCategory(int categoryId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM ticket_categories WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setInt(1, categoryId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToTicketCategory(rs);
            }
        } catch (SQLException e) {
            System.err.println("Error getting ticket category: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private TicketCategoryData mapResultSetToTicketCategory(ResultSet rs) throws SQLException {
        return new TicketCategoryData(
            rs.getInt("id"),
            rs.getInt("panel_id"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("button_label"),
            rs.getString("button_emoji"),
            rs.getString("button_color"),
            rs.getString("category_id"),
            rs.getString("welcome_message"),
            rs.getInt("position")
        );
    }

    /**
     * Update ticket category
     */
    public boolean updateTicketCategory(int categoryId, String name, String description,
                                         String buttonLabel, String buttonEmoji, String buttonColor,
                                         String discordCategoryId, String welcomeMessage) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE ticket_categories SET name = ?, description = ?, button_label = ?, " +
                "button_emoji = ?, button_color = ?, category_id = ?, welcome_message = ? WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(updateQuery);
            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setString(3, buttonLabel);
            stmt.setString(4, buttonEmoji);
            stmt.setString(5, buttonColor);
            stmt.setString(6, discordCategoryId);
            stmt.setString(7, welcomeMessage);
            stmt.setInt(8, categoryId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating ticket category: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete a ticket category
     */
    public boolean deleteTicketCategory(int categoryId) {
        try (Connection connection = getConnection()) {
            // First delete associated form fields
            String deleteFieldsQuery = "DELETE FROM ticket_form_fields WHERE category_id = ?";
            PreparedStatement deleteFieldsStmt = connection.prepareStatement(deleteFieldsQuery);
            deleteFieldsStmt.setInt(1, categoryId);
            deleteFieldsStmt.executeUpdate();

            // Get category info for reordering
            TicketCategoryData category = getTicketCategory(categoryId);

            // Delete the category
            String deleteQuery = "DELETE FROM ticket_categories WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(deleteQuery);
            stmt.setInt(1, categoryId);
            int result = stmt.executeUpdate();

            // Reorder remaining categories
            if (result > 0 && category != null) {
                String reorderQuery = "UPDATE ticket_categories SET position = position - 1 WHERE panel_id = ? AND position > ?";
                PreparedStatement reorderStmt = connection.prepareStatement(reorderQuery);
                reorderStmt.setInt(1, category.panelId);
                reorderStmt.setInt(2, category.position);
                reorderStmt.executeUpdate();
            }

            return result > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting ticket category: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Move ticket category up
     */
    public boolean moveTicketCategoryUp(int categoryId) {
        TicketCategoryData category = getTicketCategory(categoryId);
        if (category == null || category.position == 0) return false;
        return swapTicketCategoryPositions(category.panelId, category.position, category.position - 1);
    }

    /**
     * Move ticket category down
     */
    public boolean moveTicketCategoryDown(int categoryId) {
        TicketCategoryData category = getTicketCategory(categoryId);
        if (category == null) return false;
        List<TicketCategoryData> categories = getTicketCategories(category.panelId);
        if (category.position >= categories.size() - 1) return false;
        return swapTicketCategoryPositions(category.panelId, category.position, category.position + 1);
    }

    private boolean swapTicketCategoryPositions(int panelId, int pos1, int pos2) {
        try (Connection connection = getConnection()) {
            String findQuery = "SELECT id FROM ticket_categories WHERE panel_id = ? AND position = ?";
            PreparedStatement findStmt = connection.prepareStatement(findQuery);
            findStmt.setInt(1, panelId);
            findStmt.setInt(2, pos2);
            ResultSet rs = findStmt.executeQuery();
            if (!rs.next()) return false;
            int otherId = rs.getInt("id");

            String updateQuery = "UPDATE ticket_categories SET position = ? WHERE id = ?";
            PreparedStatement updateStmt = connection.prepareStatement(updateQuery);

            // Find category with pos1
            findStmt.setInt(2, pos1);
            ResultSet rs1 = findStmt.executeQuery();
            if (!rs1.next()) return false;
            int id1 = rs1.getInt("id");

            updateStmt.setInt(1, pos2);
            updateStmt.setInt(2, id1);
            updateStmt.executeUpdate();

            updateStmt.setInt(1, pos1);
            updateStmt.setInt(2, otherId);
            updateStmt.executeUpdate();

            return true;
        } catch (SQLException e) {
            System.err.println("Error swapping ticket category positions: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ==================== TICKET FORMS ====================

    /**
     * Data class to hold ticket form information
     */
    public static class TicketFormData {
        public final int id;
        public final int categoryId;
        public final String name;
        public final String description;
        public final int position;

        public TicketFormData(int id, int categoryId, String name, String description, int position) {
            this.id = id;
            this.categoryId = categoryId;
            this.name = name;
            this.description = description;
            this.position = position;
        }
    }

    /**
     * Create a new form for a category
     */
    public int createTicketForm(int categoryId, String name, String description) {
        try (Connection connection = getConnection()) {
            String posQuery = "SELECT COALESCE(MAX(position), -1) + 1 AS next_pos FROM ticket_forms WHERE category_id = ?";
            PreparedStatement posStmt = connection.prepareStatement(posQuery);
            posStmt.setInt(1, categoryId);
            ResultSet posRs = posStmt.executeQuery();
            int nextPosition = posRs.next() ? posRs.getInt("next_pos") : 0;

            String insertQuery = "INSERT INTO ticket_forms (category_id, name, description, position) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, categoryId);
            stmt.setString(2, name);
            stmt.setString(3, description);
            stmt.setInt(4, nextPosition);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("Error creating ticket form: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Get all forms for a category
     */
    public List<TicketFormData> getTicketForms(int categoryId) {
        List<TicketFormData> forms = new ArrayList<>();
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM ticket_forms WHERE category_id = ? ORDER BY position ASC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setInt(1, categoryId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                forms.add(new TicketFormData(
                    rs.getInt("id"),
                    rs.getInt("category_id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getInt("position")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting ticket forms: " + e.getMessage());
            e.printStackTrace();
        }
        return forms;
    }

    /**
     * Get a specific form
     */
    public TicketFormData getTicketForm(int formId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM ticket_forms WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setInt(1, formId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new TicketFormData(
                    rs.getInt("id"),
                    rs.getInt("category_id"),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getInt("position")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error getting ticket form: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Update a form
     */
    public boolean updateTicketForm(int formId, String name, String description) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE ticket_forms SET name = ?, description = ? WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(updateQuery);
            stmt.setString(1, name);
            stmt.setString(2, description);
            stmt.setInt(3, formId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating ticket form: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete a form and all its fields
     */
    public boolean deleteTicketForm(int formId) {
        try (Connection connection = getConnection()) {
            TicketFormData form = getTicketForm(formId);

            // Delete all fields for this form
            String deleteFieldsQuery = "DELETE FROM ticket_form_fields WHERE form_id = ?";
            PreparedStatement deleteFieldsStmt = connection.prepareStatement(deleteFieldsQuery);
            deleteFieldsStmt.setInt(1, formId);
            deleteFieldsStmt.executeUpdate();

            // Delete the form
            String deleteQuery = "DELETE FROM ticket_forms WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(deleteQuery);
            stmt.setInt(1, formId);
            int result = stmt.executeUpdate();

            if (result > 0 && form != null) {
                String reorderQuery = "UPDATE ticket_forms SET position = position - 1 WHERE category_id = ? AND position > ?";
                PreparedStatement reorderStmt = connection.prepareStatement(reorderQuery);
                reorderStmt.setInt(1, form.categoryId);
                reorderStmt.setInt(2, form.position);
                reorderStmt.executeUpdate();
            }

            return result > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting ticket form: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Move ticket form to a specific position
     */
    public boolean moveTicketFormToPosition(int formId, int newPosition) {
        TicketFormData form = getTicketForm(formId);
        if (form == null) return false;

        int currentPosition = form.position;
        if (currentPosition == newPosition) return true;

        try (Connection connection = getConnection()) {
            if (newPosition < currentPosition) {
                String shiftQuery = "UPDATE ticket_forms SET position = position + 1 WHERE category_id = ? AND position >= ? AND position < ?";
                PreparedStatement shiftStmt = connection.prepareStatement(shiftQuery);
                shiftStmt.setInt(1, form.categoryId);
                shiftStmt.setInt(2, newPosition);
                shiftStmt.setInt(3, currentPosition);
                shiftStmt.executeUpdate();
            } else {
                String shiftQuery = "UPDATE ticket_forms SET position = position - 1 WHERE category_id = ? AND position > ? AND position <= ?";
                PreparedStatement shiftStmt = connection.prepareStatement(shiftQuery);
                shiftStmt.setInt(1, form.categoryId);
                shiftStmt.setInt(2, currentPosition);
                shiftStmt.setInt(3, newPosition);
                shiftStmt.executeUpdate();
            }

            String updateQuery = "UPDATE ticket_forms SET position = ? WHERE id = ?";
            PreparedStatement updateStmt = connection.prepareStatement(updateQuery);
            updateStmt.setInt(1, newPosition);
            updateStmt.setInt(2, formId);
            return updateStmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error moving ticket form to position: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get form fields by form ID
     */
    public List<TicketFormFieldData> getTicketFormFieldsByFormId(int formId) {
        List<TicketFormFieldData> fields = new ArrayList<>();
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM ticket_form_fields WHERE form_id = ? ORDER BY position ASC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setInt(1, formId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                fields.add(new TicketFormFieldData(
                    rs.getInt("id"),
                    rs.getInt("category_id"),
                    rs.getInt("form_id"),
                    rs.getString("label"),
                    rs.getString("placeholder"),
                    rs.getString("field_type"),
                    rs.getInt("min_length"),
                    rs.getInt("max_length"),
                    rs.getInt("required") == 1,
                    rs.getInt("position")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting ticket form fields by form ID: " + e.getMessage());
            e.printStackTrace();
        }
        return fields;
    }

    /**
     * Create a new form field for a form
     */
    public int createTicketFormFieldForForm(int categoryId, int formId, String label, String placeholder,
                                             String fieldType, int minLength, int maxLength, boolean required) {
        try (Connection connection = getConnection()) {
            String posQuery = "SELECT COALESCE(MAX(position), -1) + 1 AS next_pos FROM ticket_form_fields WHERE form_id = ?";
            PreparedStatement posStmt = connection.prepareStatement(posQuery);
            posStmt.setInt(1, formId);
            ResultSet posRs = posStmt.executeQuery();
            int nextPosition = posRs.next() ? posRs.getInt("next_pos") : 0;

            String insertQuery = "INSERT INTO ticket_form_fields (category_id, form_id, label, placeholder, field_type, min_length, max_length, required, position) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, categoryId);
            stmt.setInt(2, formId);
            stmt.setString(3, label);
            stmt.setString(4, placeholder);
            stmt.setString(5, fieldType);
            stmt.setInt(6, minLength);
            stmt.setInt(7, maxLength);
            stmt.setInt(8, required ? 1 : 0);
            stmt.setInt(9, nextPosition);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("Error creating ticket form field for form: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    // ==================== TICKET FORM FIELDS ====================

    /**
     * Data class to hold ticket form field information
     */
    public static class TicketFormFieldData {
        public final int id;
        public final int categoryId;
        public final int formId;
        public final String label;
        public final String placeholder;
        public final String fieldType; // SHORT or PARAGRAPH
        public final int minLength;
        public final int maxLength;
        public final boolean required;
        public final int position;

        public TicketFormFieldData(int id, int categoryId, String label, String placeholder,
                                    String fieldType, int minLength, int maxLength,
                                    boolean required, int position) {
            this(id, categoryId, 0, label, placeholder, fieldType, minLength, maxLength, required, position);
        }

        public TicketFormFieldData(int id, int categoryId, int formId, String label, String placeholder,
                                    String fieldType, int minLength, int maxLength,
                                    boolean required, int position) {
            this.id = id;
            this.categoryId = categoryId;
            this.formId = formId;
            this.label = label;
            this.placeholder = placeholder;
            this.fieldType = fieldType != null ? fieldType : "SHORT";
            this.minLength = minLength;
            this.maxLength = maxLength;
            this.required = required;
            this.position = position;
        }
    }

    /**
     * Create a new form field for a category
     */
    public int createTicketFormField(int categoryId, String label, String placeholder,
                                      String fieldType, int minLength, int maxLength, boolean required) {
        try (Connection connection = getConnection()) {
            String posQuery = "SELECT COALESCE(MAX(position), -1) + 1 AS next_pos FROM ticket_form_fields WHERE category_id = ?";
            PreparedStatement posStmt = connection.prepareStatement(posQuery);
            posStmt.setInt(1, categoryId);
            ResultSet posRs = posStmt.executeQuery();
            int nextPosition = posRs.next() ? posRs.getInt("next_pos") : 0;

            String insertQuery = "INSERT INTO ticket_form_fields (category_id, label, placeholder, field_type, min_length, max_length, required, position) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS);
            stmt.setInt(1, categoryId);
            stmt.setString(2, label);
            stmt.setString(3, placeholder);
            stmt.setString(4, fieldType);
            stmt.setInt(5, minLength);
            stmt.setInt(6, maxLength);
            stmt.setInt(7, required ? 1 : 0);
            stmt.setInt(8, nextPosition);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("Error creating ticket form field: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Get all form fields for a category
     */
    public List<TicketFormFieldData> getTicketFormFields(int categoryId) {
        List<TicketFormFieldData> fields = new ArrayList<>();
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM ticket_form_fields WHERE category_id = ? ORDER BY position ASC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setInt(1, categoryId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                int formId = 0;
                try { formId = rs.getInt("form_id"); } catch (SQLException ignored) {}
                fields.add(new TicketFormFieldData(
                    rs.getInt("id"),
                    rs.getInt("category_id"),
                    formId,
                    rs.getString("label"),
                    rs.getString("placeholder"),
                    rs.getString("field_type"),
                    rs.getInt("min_length"),
                    rs.getInt("max_length"),
                    rs.getInt("required") == 1,
                    rs.getInt("position")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting ticket form fields: " + e.getMessage());
            e.printStackTrace();
        }
        return fields;
    }

    /**
     * Get a specific form field
     */
    public TicketFormFieldData getTicketFormField(int fieldId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM ticket_form_fields WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setInt(1, fieldId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int formId = 0;
                try { formId = rs.getInt("form_id"); } catch (SQLException ignored) {}
                return new TicketFormFieldData(
                    rs.getInt("id"),
                    rs.getInt("category_id"),
                    formId,
                    rs.getString("label"),
                    rs.getString("placeholder"),
                    rs.getString("field_type"),
                    rs.getInt("min_length"),
                    rs.getInt("max_length"),
                    rs.getInt("required") == 1,
                    rs.getInt("position")
                );
            }
        } catch (SQLException e) {
            System.err.println("Error getting ticket form field: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Update a form field
     */
    public boolean updateTicketFormField(int fieldId, String label, String placeholder,
                                          String fieldType, int minLength, int maxLength, boolean required) {
        try (Connection connection = getConnection()) {
            String updateQuery = "UPDATE ticket_form_fields SET label = ?, placeholder = ?, field_type = ?, " +
                "min_length = ?, max_length = ?, required = ? WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(updateQuery);
            stmt.setString(1, label);
            stmt.setString(2, placeholder);
            stmt.setString(3, fieldType);
            stmt.setInt(4, minLength);
            stmt.setInt(5, maxLength);
            stmt.setInt(6, required ? 1 : 0);
            stmt.setInt(7, fieldId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error updating ticket form field: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete a form field
     */
    public boolean deleteTicketFormField(int fieldId) {
        try (Connection connection = getConnection()) {
            TicketFormFieldData field = getTicketFormField(fieldId);

            String deleteQuery = "DELETE FROM ticket_form_fields WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(deleteQuery);
            stmt.setInt(1, fieldId);
            int result = stmt.executeUpdate();

            if (result > 0 && field != null) {
                String reorderQuery = "UPDATE ticket_form_fields SET position = position - 1 WHERE category_id = ? AND position > ?";
                PreparedStatement reorderStmt = connection.prepareStatement(reorderQuery);
                reorderStmt.setInt(1, field.categoryId);
                reorderStmt.setInt(2, field.position);
                reorderStmt.executeUpdate();
            }

            return result > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting ticket form field: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Move ticket form field up
     */
    public boolean moveTicketFormFieldUp(int fieldId) {
        TicketFormFieldData field = getTicketFormField(fieldId);
        if (field == null || field.position == 0) return false;
        return swapTicketFormFieldPositions(field.categoryId, field.position, field.position - 1);
    }

    /**
     * Move ticket form field down
     */
    public boolean moveTicketFormFieldDown(int fieldId) {
        TicketFormFieldData field = getTicketFormField(fieldId);
        if (field == null) return false;
        List<TicketFormFieldData> fields = getTicketFormFields(field.categoryId);
        if (field.position >= fields.size() - 1) return false;
        return swapTicketFormFieldPositions(field.categoryId, field.position, field.position + 1);
    }

    /**
     * Move ticket form field to a specific position
     */
    public boolean moveTicketFormFieldToPosition(int fieldId, int newPosition) {
        TicketFormFieldData field = getTicketFormField(fieldId);
        if (field == null) return false;

        int currentPosition = field.position;
        if (currentPosition == newPosition) return true;

        try (Connection connection = getConnection()) {
            if (newPosition < currentPosition) {
                // Moving up - shift others down
                String shiftQuery = "UPDATE ticket_form_fields SET position = position + 1 WHERE category_id = ? AND position >= ? AND position < ?";
                PreparedStatement shiftStmt = connection.prepareStatement(shiftQuery);
                shiftStmt.setInt(1, field.categoryId);
                shiftStmt.setInt(2, newPosition);
                shiftStmt.setInt(3, currentPosition);
                shiftStmt.executeUpdate();
            } else {
                // Moving down - shift others up
                String shiftQuery = "UPDATE ticket_form_fields SET position = position - 1 WHERE category_id = ? AND position > ? AND position <= ?";
                PreparedStatement shiftStmt = connection.prepareStatement(shiftQuery);
                shiftStmt.setInt(1, field.categoryId);
                shiftStmt.setInt(2, currentPosition);
                shiftStmt.setInt(3, newPosition);
                shiftStmt.executeUpdate();
            }

            // Update the field's position
            String updateQuery = "UPDATE ticket_form_fields SET position = ? WHERE id = ?";
            PreparedStatement updateStmt = connection.prepareStatement(updateQuery);
            updateStmt.setInt(1, newPosition);
            updateStmt.setInt(2, fieldId);
            return updateStmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error moving ticket form field to position: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private boolean swapTicketFormFieldPositions(int categoryId, int pos1, int pos2) {
        try (Connection connection = getConnection()) {
            String findQuery = "SELECT id FROM ticket_form_fields WHERE category_id = ? AND position = ?";
            PreparedStatement findStmt = connection.prepareStatement(findQuery);
            findStmt.setInt(1, categoryId);
            findStmt.setInt(2, pos2);
            ResultSet rs = findStmt.executeQuery();
            if (!rs.next()) return false;
            int otherId = rs.getInt("id");

            String updateQuery = "UPDATE ticket_form_fields SET position = ? WHERE id = ?";
            PreparedStatement updateStmt = connection.prepareStatement(updateQuery);

            findStmt.setInt(2, pos1);
            ResultSet rs1 = findStmt.executeQuery();
            if (!rs1.next()) return false;
            int id1 = rs1.getInt("id");

            updateStmt.setInt(1, pos2);
            updateStmt.setInt(2, id1);
            updateStmt.executeUpdate();

            updateStmt.setInt(1, pos1);
            updateStmt.setInt(2, otherId);
            updateStmt.executeUpdate();

            return true;
        } catch (SQLException e) {
            System.err.println("Error swapping ticket form field positions: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ==================== TICKET FORM RESPONSES ====================

    /**
     * Data class to hold ticket form response information
     */
    public static class TicketFormResponseData {
        public final int id;
        public final int ticketId;
        public final int fieldId;
        public final String fieldLabel;
        public final String response;
        public final String createdAt;

        public TicketFormResponseData(int id, int ticketId, int fieldId, String fieldLabel, String response, String createdAt) {
            this.id = id;
            this.ticketId = ticketId;
            this.fieldId = fieldId;
            this.fieldLabel = fieldLabel;
            this.response = response;
            this.createdAt = createdAt;
        }
    }

    /**
     * Save a form response for a ticket
     */
    public boolean saveTicketFormResponse(int ticketId, int fieldId, String fieldLabel, String response) {
        try (Connection connection = getConnection()) {
            String insertQuery = "INSERT INTO ticket_form_responses (ticket_id, field_id, field_label, response) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertQuery);
            stmt.setInt(1, ticketId);
            stmt.setInt(2, fieldId);
            stmt.setString(3, fieldLabel);
            stmt.setString(4, response);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Error saving ticket form response: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Save multiple form responses for a ticket
     */
    public boolean saveTicketFormResponses(int ticketId, List<TicketFormFieldData> fields, java.util.Map<Integer, String> responses) {
        try (Connection connection = getConnection()) {
            String insertQuery = "INSERT INTO ticket_form_responses (ticket_id, field_id, field_label, response) VALUES (?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertQuery);

            for (TicketFormFieldData field : fields) {
                String response = responses.get(field.id);
                if (response != null && !response.isBlank()) {
                    stmt.setInt(1, ticketId);
                    stmt.setInt(2, field.id);
                    stmt.setString(3, field.label);
                    stmt.setString(4, response);
                    stmt.addBatch();
                }
            }

            stmt.executeBatch();
            return true;
        } catch (SQLException e) {
            System.err.println("Error saving ticket form responses: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get all form responses for a ticket
     */
    public List<TicketFormResponseData> getTicketFormResponses(int ticketId) {
        List<TicketFormResponseData> responses = new ArrayList<>();
        try (Connection connection = getConnection()) {
            String query = "SELECT * FROM ticket_form_responses WHERE ticket_id = ? ORDER BY id ASC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setInt(1, ticketId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                responses.add(new TicketFormResponseData(
                    rs.getInt("id"),
                    rs.getInt("ticket_id"),
                    rs.getInt("field_id"),
                    rs.getString("field_label"),
                    rs.getString("response"),
                    rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error getting ticket form responses: " + e.getMessage());
            e.printStackTrace();
        }
        return responses;
    }

    /**
     * Delete all form responses for a ticket (when ticket is deleted)
     */
    public boolean deleteTicketFormResponses(int ticketId) {
        try (Connection connection = getConnection()) {
            String deleteQuery = "DELETE FROM ticket_form_responses WHERE ticket_id = ?";
            PreparedStatement stmt = connection.prepareStatement(deleteQuery);
            stmt.setInt(1, ticketId);
            return stmt.executeUpdate() >= 0;
        } catch (SQLException e) {
            System.err.println("Error deleting ticket form responses: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // ==================== END TICKET CATEGORIES ====================

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
            pstmt.setString(2, userId);
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
    public void sendAuditLogEntry(Guild guild, String actionType, String targetName, Member targetMember, Member moderatorName, String reason) {
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

                    EmbedBuilder embed;

                    if (moderatorName == null) {
                        embed = new EmbedBuilder()
                                .setTitle(emoji + " " + actionType)
                                .setDescription(emoji + " " + (!Objects.equals(targetName, null) ? targetName : "Unknown User"))
                                .addField("Moderator", "No User found", true)
                                .addField("Reason", reason, true)
                                .setColor(embedColor)
                                .setTimestamp(java.time.Instant.now());
                    } else {
                        embed = new EmbedBuilder()
                                .setTitle(emoji + " " + actionType)
                                .setDescription(emoji + " " + (!Objects.equals(targetName, null) ? targetName : "Unknown User"))
                                .addField("Moderator", moderatorName.getAsMention(), true)
                                .addField("Reason", reason, true)
                                .setColor(embedColor)
                                .setTimestamp(java.time.Instant.now());
                    }
                    


                    if (actionType.equals("WARN")) {
                        List<DatabaseHandler.WarningData> warningDataList = getUserActiveWarnings(guildId, targetMember.getId());
                        DatabaseHandler.WarningData warningData = warningDataList.get(warningDataList.size() - 1);
                        if (warningData != null) {
                            if (warningData.evidence != null && !warningData.evidence.isEmpty()) {
                                embed.addField("Evidence", "", false);
                                embed.setImage(warningData.evidence);
                            } else {
                                embed.addField("Evidence", "No evidence provided for this warning.", false);
                            }
                        } else {
                            embed.addField("Evidence", "No evidence found for this warning.", false);
                        }
                        embed.setFooter("Use /warnings list {user} to view all user warnings.");
                    }

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
            pstmt.setString(4, description);
            pstmt.setString(5, "Select your role"); // Default title
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
        return addEmbedToDatabase(guildId, channelId, messageId, null, displayType, title, description, footer, color);
    }

    public boolean addEmbedToDatabase (String guildId, String channelId, String messageId, Integer groupId, String displayType, String title, String description, String footer, String color) {
        if (displayType.equalsIgnoreCase("BUTTON")) {
            displayType = "REACTION";
        } else if (displayType.equalsIgnoreCase("SELECT_MENU")) {
            displayType = "SELECT-MENU";
        } else {
            displayType = "BUTTON"; // Default to BUTTON if invalid
        }
        String query = "INSERT INTO role_select_embeds (guild_id, channel_id, message_id, group_id, display_type, title, description, footer, color) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection(); PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, guildId);
            pstmt.setString(2, channelId);
            pstmt.setString(3, messageId);
            if (groupId != null) {
                pstmt.setInt(4, groupId);
            } else {
                pstmt.setNull(4, java.sql.Types.INTEGER);
            }
            pstmt.setString(5, displayType);
            pstmt.setString(6, title);
            pstmt.setString(7, description);
            pstmt.setString(8, footer);
            pstmt.setString(9, color);
            pstmt.executeUpdate();
            System.out.println("Role Select Embed added for guild " + guildId + (groupId != null ? " (group: " + groupId + ")" : ""));
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

    // ==================== ROLE SELECT GROUPS ====================

    /**
     * Data class to hold role select group information
     */
    public static class RoleSelectGroupData {
        public final int id;
        public final String guildId;
        public final String name;
        public final int position;
        public final String title;
        public final String description;
        public final String footer;
        public final String color;

        public RoleSelectGroupData(int id, String guildId, String name, int position, String title, String description, String footer, String color) {
            this.id = id;
            this.guildId = guildId;
            this.name = name;
            this.position = position;
            this.title = title != null ? title : "Select Your Roles";
            this.description = description != null ? description : "Choose from the roles below:";
            this.footer = footer;
            this.color = color != null ? color : "#3498db";
        }
    }

    /**
     * Create a new role select group
     */
    public int createRoleSelectGroup(String guildId, String name) {
        // Get next position
        int nextPosition = getNextGroupPosition(guildId);

        String query = "INSERT INTO role_select_groups (guild_id, name, position) VALUES (?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, guildId);
            stmt.setString(2, name);
            stmt.setInt(3, nextPosition);
            stmt.executeUpdate();

            ResultSet rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                int groupId = rs.getInt(1);
                System.out.println("Created role select group '" + name + "' with ID " + groupId + " for guild " + guildId);
                return groupId;
            }
        } catch (SQLException e) {
            System.err.println("Error creating role select group: " + e.getMessage());
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Get next available position for a group
     */
    private int getNextGroupPosition(String guildId) {
        String query = "SELECT MAX(position) as max_pos FROM role_select_groups WHERE guild_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("max_pos") + 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Get all role select groups for a guild
     */
    public List<RoleSelectGroupData> getRoleSelectGroups(String guildId) {
        List<RoleSelectGroupData> groups = new ArrayList<>();
        String query = "SELECT * FROM role_select_groups WHERE guild_id = ? ORDER BY position ASC";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                groups.add(new RoleSelectGroupData(
                    rs.getInt("id"),
                    rs.getString("guild_id"),
                    rs.getString("name"),
                    rs.getInt("position"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("footer"),
                    rs.getString("color")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return groups;
    }

    /**
     * Get a specific role select group by ID
     */
    public RoleSelectGroupData getRoleSelectGroup(String guildId, int groupId) {
        String query = "SELECT * FROM role_select_groups WHERE guild_id = ? AND id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setInt(2, groupId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new RoleSelectGroupData(
                    rs.getInt("id"),
                    rs.getString("guild_id"),
                    rs.getString("name"),
                    rs.getInt("position"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("footer"),
                    rs.getString("color")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Get a role select group by name
     */
    public RoleSelectGroupData getRoleSelectGroupByName(String guildId, String name) {
        String query = "SELECT * FROM role_select_groups WHERE guild_id = ? AND name = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new RoleSelectGroupData(
                    rs.getInt("id"),
                    rs.getString("guild_id"),
                    rs.getString("name"),
                    rs.getInt("position"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("footer"),
                    rs.getString("color")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Update role select group settings
     */
    public boolean updateRoleSelectGroup(int groupId, String guildId, String title, String description, String footer, String color) {
        String query = "UPDATE role_select_groups SET title = ?, description = ?, footer = ?, color = ? WHERE id = ? AND guild_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, title);
            stmt.setString(2, description);
            stmt.setString(3, footer);
            stmt.setString(4, color);
            stmt.setInt(5, groupId);
            stmt.setString(6, guildId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete a role select group and all its roles
     */
    public boolean deleteRoleSelectGroup(String guildId, int groupId) {
        try (Connection connection = getConnection()) {
            // First, remove group_id from all roles in this group
            String updateRoles = "UPDATE role_select SET group_id = NULL WHERE guild_id = ? AND group_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(updateRoles)) {
                stmt.setString(1, guildId);
                stmt.setInt(2, groupId);
                stmt.executeUpdate();
            }

            // Then delete the group
            String deleteGroup = "DELETE FROM role_select_groups WHERE id = ? AND guild_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(deleteGroup)) {
                stmt.setInt(1, groupId);
                stmt.setString(2, guildId);
                return stmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Swap positions of two groups
     */
    public boolean swapGroupPositions(String guildId, int groupId1, int groupId2) {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Get current positions
                int pos1 = -1, pos2 = -1;
                String query = "SELECT id, position FROM role_select_groups WHERE guild_id = ? AND id IN (?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, guildId);
                    stmt.setInt(2, groupId1);
                    stmt.setInt(3, groupId2);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        if (rs.getInt("id") == groupId1) {
                            pos1 = rs.getInt("position");
                        } else {
                            pos2 = rs.getInt("position");
                        }
                    }
                }

                if (pos1 == -1 || pos2 == -1) {
                    connection.rollback();
                    return false;
                }

                // Swap positions
                String update = "UPDATE role_select_groups SET position = ? WHERE id = ? AND guild_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(update)) {
                    stmt.setInt(1, pos2);
                    stmt.setInt(2, groupId1);
                    stmt.setString(3, guildId);
                    stmt.executeUpdate();

                    stmt.setInt(1, pos1);
                    stmt.setInt(2, groupId2);
                    stmt.setString(3, guildId);
                    stmt.executeUpdate();
                }

                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Move a group up in position
     */
    public boolean moveGroupUp(String guildId, int groupId) {
        RoleSelectGroupData group = getRoleSelectGroup(guildId, groupId);
        if (group == null || group.position <= 0) return false;

        // Find the group with position - 1
        String query = "SELECT id FROM role_select_groups WHERE guild_id = ? AND position = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setInt(2, group.position - 1);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return swapGroupPositions(guildId, groupId, rs.getInt("id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Move a group down in position
     */
    public boolean moveGroupDown(String guildId, int groupId) {
        RoleSelectGroupData group = getRoleSelectGroup(guildId, groupId);
        if (group == null) return false;

        // Find the group with position + 1
        String query = "SELECT id FROM role_select_groups WHERE guild_id = ? AND position = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setInt(2, group.position + 1);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return swapGroupPositions(guildId, groupId, rs.getInt("id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ==================== ROLE SELECT WITH GROUPS ====================

    /**
     * Add a role to a specific group
     */
    public boolean addRoleSelectToGroup(String guildId, String roleId, int groupId, String description, String emojiId) {
        if (isRoleAlreadyAdded(guildId, roleId)) {
            // Update existing role to assign to group
            String query = "UPDATE role_select SET group_id = ?, description = ?, emoji_id = ? WHERE guild_id = ? AND role_id = ?";
            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setInt(1, groupId);
                stmt.setString(2, description);
                stmt.setString(3, emojiId);
                stmt.setString(4, guildId);
                stmt.setString(5, roleId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            // Get next position in group
            int nextPosition = getNextRolePositionInGroup(guildId, groupId);

            String query = "INSERT INTO role_select (guild_id, role_id, group_id, position, description, emoji_id) VALUES (?, ?, ?, ?, ?, ?)";
            try (Connection connection = getConnection();
                 PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, guildId);
                stmt.setString(2, roleId);
                stmt.setInt(3, groupId);
                stmt.setInt(4, nextPosition);
                stmt.setString(5, description);
                stmt.setString(6, emojiId);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    /**
     * Get next available position for a role in a group
     */
    private int getNextRolePositionInGroup(String guildId, int groupId) {
        String query = "SELECT MAX(position) as max_pos FROM role_select WHERE guild_id = ? AND group_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setInt(2, groupId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("max_pos") + 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Get all roles in a specific group
     */
    public List<String> getRolesInGroup(String guildId, int groupId) {
        List<String> roleIds = new ArrayList<>();
        String query = "SELECT role_id FROM role_select WHERE guild_id = ? AND group_id = ? ORDER BY position ASC";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setInt(2, groupId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                roleIds.add(rs.getString("role_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return roleIds;
    }

    /**
     * Move a role up in position within a group
     */
    public boolean moveRoleUpInGroup(String guildId, int groupId, String roleId) {
        List<String> roles = getRolesInGroup(guildId, groupId);
        int currentIndex = roles.indexOf(roleId);
        if (currentIndex <= 0) {
            return false; // Already at top or not found
        }
        String roleAbove = roles.get(currentIndex - 1);
        return swapRolePositionsInGroup(guildId, groupId, roleId, roleAbove);
    }

    /**
     * Move a role down in position within a group
     */
    public boolean moveRoleDownInGroup(String guildId, int groupId, String roleId) {
        List<String> roles = getRolesInGroup(guildId, groupId);
        int currentIndex = roles.indexOf(roleId);
        if (currentIndex < 0 || currentIndex >= roles.size() - 1) {
            return false; // Already at bottom or not found
        }
        String roleBelow = roles.get(currentIndex + 1);
        return swapRolePositionsInGroup(guildId, groupId, roleId, roleBelow);
    }

    /**
     * Get all ungrouped roles (roles without a group)
     */
    public List<String> getUngroupedRoles(String guildId) {
        List<String> roleIds = new ArrayList<>();
        String query = "SELECT role_id FROM role_select WHERE guild_id = ? AND (group_id IS NULL OR group_id = 0) ORDER BY position ASC";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                roleIds.add(rs.getString("role_id"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return roleIds;
    }

    /**
     * Remove a role from its group (set group_id to NULL)
     */
    public boolean removeRoleFromGroup(String guildId, String roleId) {
        String query = "UPDATE role_select SET group_id = NULL WHERE guild_id = ? AND role_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, roleId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Swap positions of two roles within a group
     */
    public boolean swapRolePositionsInGroup(String guildId, int groupId, String roleId1, String roleId2) {
        try (Connection connection = getConnection()) {
            connection.setAutoCommit(false);
            try {
                // Get current positions
                int pos1 = -1, pos2 = -1;
                String query = "SELECT role_id, position FROM role_select WHERE guild_id = ? AND group_id = ? AND role_id IN (?, ?)";
                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, guildId);
                    stmt.setInt(2, groupId);
                    stmt.setString(3, roleId1);
                    stmt.setString(4, roleId2);
                    ResultSet rs = stmt.executeQuery();
                    while (rs.next()) {
                        if (rs.getString("role_id").equals(roleId1)) {
                            pos1 = rs.getInt("position");
                        } else {
                            pos2 = rs.getInt("position");
                        }
                    }
                }

                if (pos1 == -1 || pos2 == -1) {
                    connection.rollback();
                    return false;
                }

                // Swap positions
                String update = "UPDATE role_select SET position = ? WHERE guild_id = ? AND role_id = ?";
                try (PreparedStatement stmt = connection.prepareStatement(update)) {
                    stmt.setInt(1, pos2);
                    stmt.setString(2, guildId);
                    stmt.setString(3, roleId1);
                    stmt.executeUpdate();

                    stmt.setInt(1, pos1);
                    stmt.setString(2, guildId);
                    stmt.setString(3, roleId2);
                    stmt.executeUpdate();
                }

                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get the group ID for a role
     */
    public int getRoleGroupId(String guildId, String roleId) {
        String query = "SELECT group_id FROM role_select WHERE guild_id = ? AND role_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, roleId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("group_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    // ==================== ROLE SELECT EMBEDS DATA ====================

    /**
     * Data class to hold role select embed information
     */
    public static class RoleSelectEmbedData {
        public final int id;
        public final String guildId;
        public final String channelId;
        public final String messageId;
        public final Integer groupId;
        public final String displayType;
        public final String title;
        public final String description;
        public final String footer;
        public final String color;

        public RoleSelectEmbedData(int id, String guildId, String channelId, String messageId, Integer groupId,
                                   String displayType, String title, String description, String footer, String color) {
            this.id = id;
            this.guildId = guildId;
            this.channelId = channelId;
            this.messageId = messageId;
            this.groupId = groupId;
            this.displayType = displayType;
            this.title = title;
            this.description = description;
            this.footer = footer;
            this.color = color;
        }
    }

    /**
     * Get all role select embeds for a guild
     */
    public List<RoleSelectEmbedData> getRoleSelectEmbeds(String guildId) {
        List<RoleSelectEmbedData> embeds = new ArrayList<>();
        String query = "SELECT * FROM role_select_embeds WHERE guild_id = ? ORDER BY id ASC";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Integer groupId = rs.getObject("group_id") != null ? rs.getInt("group_id") : null;
                embeds.add(new RoleSelectEmbedData(
                    rs.getInt("id"),
                    rs.getString("guild_id"),
                    rs.getString("channel_id"),
                    rs.getString("message_id"),
                    groupId,
                    rs.getString("display_type"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("footer"),
                    rs.getString("color")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return embeds;
    }

    /**
     * Get role select embeds by group ID
     */
    public List<RoleSelectEmbedData> getRoleSelectEmbedsByGroup(String guildId, Integer groupId) {
        List<RoleSelectEmbedData> embeds = new ArrayList<>();
        String query;
        if (groupId == null || groupId == 0) {
            query = "SELECT * FROM role_select_embeds WHERE guild_id = ? AND (group_id IS NULL OR group_id = 0) ORDER BY id ASC";
        } else {
            query = "SELECT * FROM role_select_embeds WHERE guild_id = ? AND group_id = ? ORDER BY id ASC";
        }
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            if (groupId != null && groupId != 0) {
                stmt.setInt(2, groupId);
            }
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Integer grpId = rs.getObject("group_id") != null ? rs.getInt("group_id") : null;
                embeds.add(new RoleSelectEmbedData(
                    rs.getInt("id"),
                    rs.getString("guild_id"),
                    rs.getString("channel_id"),
                    rs.getString("message_id"),
                    grpId,
                    rs.getString("display_type"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("footer"),
                    rs.getString("color")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return embeds;
    }

    /**
     * Get a role select embed by message ID
     */
    public RoleSelectEmbedData getRoleSelectEmbedByMessageId(String guildId, String messageId) {
        String query = "SELECT * FROM role_select_embeds WHERE guild_id = ? AND message_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, messageId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Integer groupId = rs.getObject("group_id") != null ? rs.getInt("group_id") : null;
                return new RoleSelectEmbedData(
                    rs.getInt("id"),
                    rs.getString("guild_id"),
                    rs.getString("channel_id"),
                    rs.getString("message_id"),
                    groupId,
                    rs.getString("display_type"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("footer"),
                    rs.getString("color")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
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
            e.printStackTrace();
        }
        return userTimers;
    }

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
            "embed", "reminders", "leveling"
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

    // ==================== DATA RETENTION / ERASURE ====================

    /**
     * How long data of a guild is kept after the bot was removed from it.
     * Must match the retention period stated in the privacy policy.
     */
    public static final int GUILD_DATA_RETENTION_DAYS = 30;

    /**
     * Tables holding rows about a single user, mapped to the column carrying the user id.
     * The users table keys the id directly, everything else uses user_id.
     */
    private static final Map<String, String> USER_DATA_TABLES = createUserDataTables();

    private static Map<String, String> createUserDataTables() {
        Map<String, String> tables = new LinkedHashMap<>();
        tables.put("warnings", "user_id");
        tables.put("moderation_actions", "user_id");
        tables.put("tickets", "user_id");
        tables.put("member_roles", "user_id");
        tables.put("active_timers", "user_id");
        tables.put("reminders", "user_id");
        tables.put("user_levels", "user_id");
        tables.put("user_statistics", "user_id");
        tables.put("temporary_data", "user_id");
        tables.put("bot_logs", "user_id");
        tables.put("users", "id");
        return tables;
    }

    /**
     * Guild-scoped tables, all keyed by guild_id. Ticket categories, forms and fields are
     * not listed here because they hang off a panel rather than a guild - they are removed
     * by {@link #deleteGuildData(String)} through their parent panel.
     */
    private static final String[] GUILD_DATA_TABLES = {
            "warnings", "moderation_actions", "tickets", "ticket_panels",
            "guild_settings", "guild_systems", "statistics", "user_statistics",
            "rules_embeds_channel", "log_channels", "warn_system_settings",
            "just_verify_button", "custom_embeds", "role_events", "active_timers",
            "role_permissions", "role_select", "role_select_embeds", "role_select_groups",
            "member_roles", "user_levels", "level_settings", "reminders",
            "temporary_data", "bot_logs"
    };

    /**
     * Delete everything stored about a single user, across all guilds.
     * <p>
     * Each table is deleted in its own statement and its own try/catch, so a table that
     * does not exist in a given deployment cannot abort the rest of the erasure.
     *
     * @return rows deleted per table
     */
    public Map<String, Integer> deleteUserData(String userId) {
        Map<String, Integer> deleted = new LinkedHashMap<>();

        try (Connection connection = getConnection()) {
            for (Map.Entry<String, String> entry : USER_DATA_TABLES.entrySet()) {
                String table = entry.getKey();
                String column = entry.getValue();
                String query = "DELETE FROM " + table + " WHERE " + column + " = ?";

                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, userId);
                    int rows = stmt.executeUpdate();
                    deleted.put(table, rows);
                } catch (SQLException e) {
                    // Missing table or column in this deployment - keep going
                    System.err.println("Skipping " + table + " while deleting user " + userId + ": " + e.getMessage());
                }
            }
        } catch (SQLException e) {
            System.err.println("Error deleting user data for user " + userId + ": " + e.getMessage());
            e.printStackTrace();
        }

        int total = deleted.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("Deleted " + total + " rows for user " + userId + " across " + deleted.size() + " tables");
        return deleted;
    }

    /**
     * Delete everything stored about a guild, including the ticket categories, forms and
     * form responses that are only reachable through the guild's panels and tickets.
     *
     * @return rows deleted per table
     */
    public Map<String, Integer> deleteGuildData(String guildId) {
        Map<String, Integer> deleted = new LinkedHashMap<>();

        try (Connection connection = getConnection()) {
            // Ticket tree first: children before their parents, so nothing is orphaned if
            // a later statement fails
            exec(connection, deleted, "ticket_form_responses",
                    "DELETE FROM ticket_form_responses WHERE ticket_id IN (SELECT id FROM tickets WHERE guild_id = ?)", guildId);
            exec(connection, deleted, "ticket_form_fields",
                    "DELETE FROM ticket_form_fields WHERE category_id IN (SELECT c.id FROM ticket_categories c " +
                    "JOIN ticket_panels p ON c.panel_id = p.id WHERE p.guild_id = ?)", guildId);
            exec(connection, deleted, "ticket_form_fields_by_form",
                    "DELETE FROM ticket_form_fields WHERE form_id IN (SELECT f.id FROM ticket_forms f " +
                    "JOIN ticket_categories c ON f.category_id = c.id " +
                    "JOIN ticket_panels p ON c.panel_id = p.id WHERE p.guild_id = ?)", guildId);
            exec(connection, deleted, "ticket_forms",
                    "DELETE FROM ticket_forms WHERE category_id IN (SELECT c.id FROM ticket_categories c " +
                    "JOIN ticket_panels p ON c.panel_id = p.id WHERE p.guild_id = ?)", guildId);
            exec(connection, deleted, "ticket_categories",
                    "DELETE FROM ticket_categories WHERE panel_id IN (SELECT id FROM ticket_panels WHERE guild_id = ?)", guildId);

            for (String table : GUILD_DATA_TABLES) {
                exec(connection, deleted, table, "DELETE FROM " + table + " WHERE guild_id = ?", guildId);
            }

            exec(connection, deleted, "guilds", "DELETE FROM guilds WHERE id = ?", guildId);
        } catch (SQLException e) {
            System.err.println("Error deleting guild data for guild " + guildId + ": " + e.getMessage());
            e.printStackTrace();
        }

        int total = deleted.values().stream().mapToInt(Integer::intValue).sum();
        System.out.println("Deleted " + total + " rows for guild " + guildId + " across " + deleted.size() + " statements");
        return deleted;
    }

    /** Run one delete statement, recording the row count and swallowing a missing table. */
    private void exec(Connection connection, Map<String, Integer> deleted, String label, String sql, String parameter) {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, parameter);
            deleted.put(label, stmt.executeUpdate());
        } catch (SQLException e) {
            System.err.println("Skipping " + label + ": " + e.getMessage());
        }
    }

    /**
     * Mark a guild as left, starting the retention clock.
     * {@link #purgeExpiredGuildData()} deletes it once the window has passed.
     */
    public void markGuildLeft(String guildId) {
        try (Connection connection = getConnection()) {
            String query = "UPDATE guilds SET active = 0, left_at = CURRENT_TIMESTAMP WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, guildId);
                stmt.executeUpdate();
            }
            System.out.println("Marked guild " + guildId + " as left; data is deleted after "
                    + GUILD_DATA_RETENTION_DAYS + " days");
        } catch (SQLException e) {
            System.err.println("Error marking guild as left: " + e.getMessage());
        }
    }

    /** Clear the retention clock when the bot is added back before the window expires. */
    public void clearGuildLeftMarker(String guildId) {
        try (Connection connection = getConnection()) {
            String query = "UPDATE guilds SET active = 1, left_at = NULL WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, guildId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error clearing guild left marker: " + e.getMessage());
        }
    }

    /**
     * Delete the data of every guild the bot has been removed from for longer than the
     * retention period. Run periodically; safe to call when there is nothing to do.
     *
     * @return the guild ids whose data was deleted
     */
    public List<String> purgeExpiredGuildData() {
        List<String> purged = new ArrayList<>();

        try (Connection connection = getConnection()) {
            String query = "SELECT id FROM guilds WHERE active = 0 AND left_at IS NOT NULL " +
                    "AND left_at < (NOW() - INTERVAL ? DAY)";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setInt(1, GUILD_DATA_RETENTION_DAYS);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    purged.add(rs.getString("id"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error looking up expired guild data: " + e.getMessage());
            return purged;
        }

        for (String guildId : purged) {
            System.out.println("Retention period expired for guild " + guildId + " - deleting data");
            deleteGuildData(guildId);
        }
        return purged;
    }

    /**
     * Start the retention clock for guilds that were left before the clock existed.
     * <p>
     * Without this, every guild the bot was removed from before this feature was added
     * keeps its data forever, because purgeExpiredGuildData only considers rows that have
     * a left_at. Their window starts now rather than retroactively, so nothing is deleted
     * immediately. Call after syncGuilds, so guilds the bot is currently in are excluded.
     *
     * @return number of guilds whose clock was started
     */
    public int backfillGuildLeftTimestamps() {
        try (Connection connection = getConnection()) {
            String query = "UPDATE guilds SET left_at = CURRENT_TIMESTAMP WHERE active = 0 AND left_at IS NULL";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    System.out.println("Started the retention clock for " + rows
                            + " previously left guild(s); their data is deleted in "
                            + GUILD_DATA_RETENTION_DAYS + " days");
                }
                return rows;
            }
        } catch (SQLException e) {
            System.err.println("Error backfilling guild left timestamps: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Delete the stored role snapshot of a member who left a guild.
     * <p>
     * The snapshot exists only to detect role changes while someone is on the server, so
     * once they leave it is personal data without a purpose.
     */
    public void deleteMemberRoleSnapshot(String guildId, String userId) {
        try (Connection connection = getConnection()) {
            String query = "DELETE FROM member_roles WHERE guild_id = ? AND user_id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, guildId);
                stmt.setString(2, userId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error deleting member role snapshot: " + e.getMessage());
        }
    }

    /**
     * Delete profile rows in the users table that nothing references any more.
     * <p>
     * The users table is global rather than per guild, so a profile survives the deletion
     * of every guild that user appeared in. Such a row is a username and avatar URL kept
     * for no remaining purpose, which is exactly what data minimisation forbids.
     *
     * @return number of profiles removed
     */
    public int purgeOrphanedUsers() {
        String query =
                "DELETE FROM users WHERE NOT EXISTS (SELECT 1 FROM warnings w WHERE w.user_id = users.id) " +
                "AND NOT EXISTS (SELECT 1 FROM moderation_actions m WHERE m.user_id = users.id) " +
                "AND NOT EXISTS (SELECT 1 FROM tickets t WHERE t.user_id = users.id) " +
                "AND NOT EXISTS (SELECT 1 FROM member_roles r WHERE r.user_id = users.id) " +
                "AND NOT EXISTS (SELECT 1 FROM active_timers ti WHERE ti.user_id = users.id) " +
                "AND NOT EXISTS (SELECT 1 FROM reminders re WHERE re.user_id = users.id) " +
                "AND NOT EXISTS (SELECT 1 FROM user_levels l WHERE l.user_id = users.id) " +
                "AND NOT EXISTS (SELECT 1 FROM user_statistics s WHERE s.user_id = users.id) " +
                // statistics holds guild-level daily aggregates and its user_id is
                // currently never written, but a profile must not be removed while
                // anything at all still points at it
                "AND NOT EXISTS (SELECT 1 FROM statistics st WHERE st.user_id = users.id)";

        try (Connection connection = getConnection()) {
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    System.out.println("Removed " + rows + " orphaned user profile(s)");
                }
                return rows;
            }
        } catch (SQLException e) {
            System.err.println("Error purging orphaned users: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Count what is stored about a user, per category, for the /data info command.
     * Categories with nothing stored are included with a count of 0 so the answer is
     * explicit rather than silently omitting them.
     */
    public Map<String, Integer> getUserDataSummary(String userId) {
        Map<String, Integer> summary = new LinkedHashMap<>();

        Map<String, String> counts = new LinkedHashMap<>();
        counts.put("profile", "SELECT COUNT(*) FROM users WHERE id = ?");
        counts.put("warnings", "SELECT COUNT(*) FROM warnings WHERE user_id = ?");
        counts.put("moderation_actions", "SELECT COUNT(*) FROM moderation_actions WHERE user_id = ?");
        counts.put("tickets", "SELECT COUNT(*) FROM tickets WHERE user_id = ?");
        counts.put("role_snapshots", "SELECT COUNT(*) FROM member_roles WHERE user_id = ?");
        counts.put("active_timers", "SELECT COUNT(*) FROM active_timers WHERE user_id = ?");
        counts.put("reminders", "SELECT COUNT(*) FROM reminders WHERE user_id = ?");
        counts.put("levels", "SELECT COUNT(*) FROM user_levels WHERE user_id = ?");
        counts.put("statistics", "SELECT COUNT(*) FROM user_statistics WHERE user_id = ?");

        try (Connection connection = getConnection()) {
            for (Map.Entry<String, String> entry : counts.entrySet()) {
                try (PreparedStatement stmt = connection.prepareStatement(entry.getValue())) {
                    stmt.setString(1, userId);
                    ResultSet rs = stmt.executeQuery();
                    summary.put(entry.getKey(), rs.next() ? rs.getInt(1) : 0);
                } catch (SQLException e) {
                    System.err.println("Skipping " + entry.getKey() + " in data summary: " + e.getMessage());
                    summary.put(entry.getKey(), 0);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error building user data summary: " + e.getMessage());
        }
        return summary;
    }

    /**
     * The profile row stored for a user: username, discriminator, avatar and when it was
     * first seen. Returns null when nothing is stored.
     */
    public String[] getStoredUserProfile(String userId) {
        try (Connection connection = getConnection()) {
            String query = "SELECT username, discriminator, avatar, created_at, updated_at FROM users WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, userId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return new String[]{
                            rs.getString("username"), rs.getString("discriminator"),
                            rs.getString("avatar"), rs.getString("created_at"), rs.getString("updated_at")
                    };
                }
            }
        } catch (SQLException e) {
            System.err.println("Error reading stored user profile: " + e.getMessage());
        }
        return null;
    }

    public static class ReminderData {
        public final int id;
        public final String userId;
        public final String guildId;
        public final String channelId;
        public final String title;
        public final String message;
        public final boolean dm;
        public final Timestamp remindAt;

        public ReminderData(int id, String userId, String guildId, String channelId, String title, String message, boolean dm, Timestamp remindAt) {
            this.id = id;
            this.userId = userId;
            this.guildId = guildId;
            this.channelId = channelId;
            this.title = title;
            this.message = message;
            this.dm = dm;
            this.remindAt = remindAt;
        }
    }

    public void addReminder(String userId, String guildId, String channelId, String title, String message, boolean dm, Timestamp remindAt) {
        String query = "INSERT INTO reminders (user_id, guild_id, channel_id, title, message, dm, remind_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, userId);
            stmt.setString(2, guildId);
            stmt.setString(3, channelId);
            stmt.setString(4, title);
            stmt.setString(5, message);
            stmt.setInt(6, dm ? 1 : 0);
            stmt.setTimestamp(7, remindAt);
            stmt.executeUpdate();
            System.out.println("Reminder added for user " + userId);
        } catch (SQLException e) {
            System.err.println("Error adding reminder: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<ReminderData> getUserReminders(String userId) {
        List<ReminderData> list = new ArrayList<>();
        String query = "SELECT * FROM reminders WHERE user_id = ? ORDER BY remind_at ASC";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, userId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                list.add(new ReminderData(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getString("guild_id"),
                        rs.getString("channel_id"),
                        rs.getString("title"),
                        rs.getString("message"),
                        rs.getInt("dm") == 1,
                        rs.getTimestamp("remind_at")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<ReminderData> getDueReminders() {
        List<ReminderData> list = new ArrayList<>();
        String query = "SELECT * FROM reminders WHERE remind_at <= CURRENT_TIMESTAMP";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                list.add(new ReminderData(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getString("guild_id"),
                        rs.getString("channel_id"),
                        rs.getString("title"),
                        rs.getString("message"),
                        rs.getInt("dm") == 1,
                        rs.getTimestamp("remind_at")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean deleteReminder(int id) {
        String query = "DELETE FROM reminders WHERE id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, id);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean deleteReminder(int id, String userId) {
        String query = "DELETE FROM reminders WHERE id = ? AND user_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, id);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public ReminderData getReminder(int id) {
        String query = "SELECT * FROM reminders WHERE id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new ReminderData(
                        rs.getInt("id"),
                        rs.getString("user_id"),
                        rs.getString("guild_id"),
                        rs.getString("channel_id"),
                        rs.getString("title"),
                        rs.getString("message"),
                        rs.getInt("dm") == 1,
                        rs.getTimestamp("remind_at")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ==================== LEVELING SYSTEM SETTINGS ====================

    /**
     * Data class to hold leveling system settings
     */
    public static class LevelSettingsData {
        public final String guildId;
        public final boolean enabled;

        // Formula Settings
        public final String xpCurve;
        public final double xpMultiplier;
        public final int maxLevel;

        // Message XP
        public final boolean messageXpEnabled;
        public final String messageXpMode;
        public final int xpMin;
        public final int xpMax;
        public final int cooldownSeconds;
        public final int minMessageLength;

        // Voice XP
        public final boolean voiceXpEnabled;
        public final int voiceXpMin;
        public final int voiceXpMax;
        public final int voiceXpAmount; // Legacy
        public final int voiceXpCooldown;
        public final int voiceXpMinMembers;
        public final boolean voiceXpAntiAfk;

        // Reaction XP
        public final boolean reactionXpEnabled;
        public final String reactionXpAwards;
        public final int reactionXpMin;
        public final int reactionXpMax;
        public final int reactionXpCooldown;

        // Notifications
        public final String levelupChannelId;
        public final String levelupMessages;
        public final boolean levelupDm;

        // Roles
        public final boolean stackRewards;
        public final boolean applyRoleRewardsOnAddRoleReward;
        public final String rewards;

        // Exceptions
        public final String ignoredChannels;
        public final String ignoredRoles;
        public final boolean resetOnLeave;

        public LevelSettingsData(String guildId, boolean enabled,
                                 String xpCurve, double xpMultiplier, int maxLevel,
                                 boolean messageXpEnabled, String messageXpMode,
                                 int xpMin, int xpMax, int cooldownSeconds, int minMessageLength,
                                 boolean voiceXpEnabled, int voiceXpMin, int voiceXpMax,
                                 int voiceXpAmount, int voiceXpCooldown, int voiceXpMinMembers, boolean voiceXpAntiAfk,
                                 boolean reactionXpEnabled, String reactionXpAwards,
                                 int reactionXpMin, int reactionXpMax, int reactionXpCooldown,
                                 String levelupChannelId, String levelupMessages, boolean levelupDm,
                                 boolean stackRewards, boolean applyRoleRewardsOnAddRoleReward, String rewards,
                                 String ignoredChannels, String ignoredRoles, boolean resetOnLeave) {
            this.guildId = guildId;
            this.enabled = enabled;
            this.xpCurve = xpCurve != null ? xpCurve : "linear";
            this.xpMultiplier = xpMultiplier;
            this.maxLevel = maxLevel;
            this.messageXpEnabled = messageXpEnabled;
            this.messageXpMode = messageXpMode != null ? messageXpMode : "random";
            this.xpMin = xpMin;
            this.xpMax = xpMax;
            this.cooldownSeconds = cooldownSeconds;
            this.minMessageLength = minMessageLength;
            this.voiceXpEnabled = voiceXpEnabled;
            this.voiceXpMin = voiceXpMin;
            this.voiceXpMax = voiceXpMax;
            this.voiceXpAmount = voiceXpAmount;
            this.voiceXpCooldown = voiceXpCooldown;
            this.voiceXpMinMembers = voiceXpMinMembers;
            this.voiceXpAntiAfk = voiceXpAntiAfk;
            this.reactionXpEnabled = reactionXpEnabled;
            this.reactionXpAwards = reactionXpAwards != null ? reactionXpAwards : "both";
            this.reactionXpMin = reactionXpMin;
            this.reactionXpMax = reactionXpMax;
            this.reactionXpCooldown = reactionXpCooldown;
            this.levelupChannelId = levelupChannelId;
            this.levelupMessages = levelupMessages;
            this.levelupDm = levelupDm;
            this.stackRewards = stackRewards;
            this.applyRoleRewardsOnAddRoleReward = applyRoleRewardsOnAddRoleReward;
            this.rewards = rewards;
            this.ignoredChannels = ignoredChannels;
            this.ignoredRoles = ignoredRoles;
            this.resetOnLeave = resetOnLeave;
        }
    }

    /**
     * Get leveling settings for a guild. Creates default settings if none exist.
     */
    public LevelSettingsData getLevelSettings(String guildId) {
        String query = "SELECT * FROM level_settings WHERE guild_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new LevelSettingsData(
                        rs.getString("guild_id"),
                        rs.getInt("enabled") == 1,
                        // Formula
                        rs.getString("xp_curve"),
                        rs.getDouble("xp_multiplier"),
                        rs.getInt("max_level"),
                        // Message XP
                        rs.getInt("message_xp_enabled") == 1,
                        rs.getString("message_xp_mode"),
                        rs.getInt("xp_min"),
                        rs.getInt("xp_max"),
                        rs.getInt("cooldown_seconds"),
                        rs.getInt("min_message_length"),
                        // Voice XP
                        rs.getInt("voice_xp_enabled") == 1,
                        rs.getInt("voice_xp_min"),
                        rs.getInt("voice_xp_max"),
                        rs.getInt("voice_xp_amount"),
                        rs.getInt("voice_xp_cooldown"),
                        rs.getInt("voice_xp_min_members"),
                        rs.getInt("voice_xp_anti_afk") == 1,
                        // Reaction XP
                        rs.getInt("reaction_xp_enabled") == 1,
                        rs.getString("reaction_xp_awards"),
                        rs.getInt("reaction_xp_min"),
                        rs.getInt("reaction_xp_max"),
                        rs.getInt("reaction_xp_cooldown"),
                        // Notifications
                        rs.getString("levelup_channel_id"),
                        rs.getString("levelup_messages"),
                        rs.getInt("levelup_dm") == 1,
                        // Roles
                        rs.getInt("stack_rewards") == 1,
                        rs.getInt("apply_role_rewards") == 1,
                        rs.getString("rewards"),
                        // Exceptions
                        rs.getString("ignored_channels"),
                        rs.getString("ignored_roles"),
                        rs.getInt("reset_on_leave") == 1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Create default settings if none exist
        createDefaultLevelSettings(guildId);
        return getLevelSettings(guildId);
    }

    /**
     * Create default leveling settings for a guild
     */
    public void createDefaultLevelSettings(String guildId) {
        String query = "INSERT IGNORE INTO level_settings (guild_id) VALUES (?)";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Update a single leveling setting
     */
    public boolean updateLevelSetting(String guildId, String column, Object value) {
        String query = "UPDATE level_settings SET " + column + " = ?, updated_at = CURRENT_TIMESTAMP WHERE guild_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setObject(1, value);
            stmt.setString(2, guildId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Toggle a boolean leveling setting
     */
    public boolean toggleLevelSetting(String guildId, String column) {
        String query = "UPDATE level_settings SET " + column + " = NOT " + column + ", updated_at = CURRENT_TIMESTAMP WHERE guild_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get the current value of a boolean leveling setting after toggle
     */
    public boolean getLevelSettingBoolean(String guildId, String column) {
        String query = "SELECT " + column + " FROM level_settings WHERE guild_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) == 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getHighestLevelInRewards(String guildId) {
        LevelSettingsData settings = getLevelSettings(guildId);
        String rewardsStr = settings.rewards;
        if (rewardsStr == null || rewardsStr.isEmpty()) {
            return 0;
        }

        String[] rewardsArray = rewardsStr.split(";");
        int highestLevel = 0;

        for (String reward : rewardsArray) {
            String[] parts = reward.split(":");
            if (parts.length == 2) {
                try {
                    int level = Integer.parseInt(parts[0]);
                    if (level > highestLevel) {
                        highestLevel = level;
                    }
                } catch (NumberFormatException e) {
                    // Ignore invalid entries
                }
            }
        }

        return highestLevel;
    }

    public int getHighestRoleRewardLevel(String guildId, String userId, List<String> roleIds) {
        LevelSettingsData settings = getLevelSettings(guildId);

        // 1. Sicherheitscheck: Ist das Feld leer?
        if (settings.rewards == null || settings.rewards.isEmpty()) {
            return 0;
        }

        int highestLevel = 0;

        try {
            // 2. String aus der DB in ein JSONArray umwandeln
            JSONArray rewardsJson = new JSONArray(settings.rewards);

            // 3. Klassische Schleife nutzen (Wichtig bei org.json!)
            for (int i = 0; i < rewardsJson.length(); i++) {
                JSONObject reward = rewardsJson.getJSONObject(i);

                // Daten aus dem JSON holen (optString verhindert Absturz bei fehlenden Keys)
                String jsonRoleId = reward.optString("role_id");
                int level = reward.optInt("level", 0);

                // 4. Prüfen: Hat der User diese Rolle?
                if (roleIds.contains(jsonRoleId)) {
                    // Wenn ja, prüfen ob das Level höher ist als das bisher höchste
                    if (level > highestLevel) {
                        highestLevel = level;
                    }
                }
            }
        } catch (Exception e) {
            // Fehler fangen, falls das JSON in der DB kaputt ist
            System.err.println("Error parsing rewards for guild " + guildId + ": " + e.getMessage());
        }

        return highestLevel;
    }

    // ==================== USER LEVELS ====================

    /**
     * Data class to hold user level information
     */
    public static class UserLevelData {
        public final int id;
        public final String guildId;
        public final String userId;
        public final long xp;
        public final int level;
        public final long totalXp;
        public final int messagesCount;
        public final int voiceMinutes;
        public final java.time.LocalDateTime lastXpTime;
        public final java.time.LocalDateTime lastVoiceXpTime;

        public UserLevelData(int id, String guildId, String userId, long xp, int level, long totalXp,
                             int messagesCount, int voiceMinutes,
                             java.time.LocalDateTime lastXpTime, java.time.LocalDateTime lastVoiceXpTime) {
            this.id = id;
            this.guildId = guildId;
            this.userId = userId;
            this.xp = xp;
            this.level = level;
            this.totalXp = totalXp;
            this.messagesCount = messagesCount;
            this.voiceMinutes = voiceMinutes;
            this.lastXpTime = lastXpTime;
            this.lastVoiceXpTime = lastVoiceXpTime;
        }

        /**
         * Calculate XP required for a specific level using a scaling formula
         */
        public static long getXpForLevel(int level) {
            if (level <= 0) return 0;
            // Formula: 5 * (level^2) + 50 * level + 100
            return (long) (5 * Math.pow(level, 2) + 50 * level + 100);
        }

        /**
         * Calculate total XP required to reach a specific level
         */
        public static long getTotalXpForLevel(int level) {
            long total = 0;
            for (int i = 1; i <= level; i++) {
                total += getXpForLevel(i);
            }
            return total;
        }

        /**
         * Calculate XP needed for next level
         */
        public long getXpForNextLevel() {
            return getXpForLevel(level + 1);
        }

        /**
         * Calculate progress percentage to next level
         */
        public double getProgressPercent() {
            long needed = getXpForNextLevel();
            if (needed == 0) return 100.0;
            return (double) xp / needed * 100.0;
        }
    }

    /**
     * Get user level data for a specific user in a guild
     */
    public UserLevelData getUserLevel(String guildId, String userId) {
        String query = "SELECT * FROM user_levels WHERE guild_id = ? AND user_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return extractUserLevelData(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        // Return default data if not found
        return new UserLevelData(0, guildId, userId, 0, 0, 0, 0, 0, null, null);
    }

    /**
     * Create or get user level entry
     */
    public UserLevelData getOrCreateUserLevel(String guildId, String userId) {
        UserLevelData existing = getUserLevel(guildId, userId);
        if (existing.id != 0) {
            return existing;
        }

        String query = "INSERT INTO user_levels (guild_id, user_id) VALUES (?, ?)";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return getUserLevel(guildId, userId);
    }

    /**
     * Add XP to a user and handle level-ups
     * @return The new level if leveled up, -1 if no level up, or the current level
     */
    public int addXpToUser(String guildId, String userId, int xpAmount) {
        UserLevelData userData = getOrCreateUserLevel(guildId, userId);

        long newXp = userData.xp + xpAmount;
        long newTotalXp = userData.totalXp + xpAmount;
        int newLevel = userData.level;
        int newMessagesCount = userData.messagesCount + 1;

        // Check for level up(s)
        while (newXp >= UserLevelData.getXpForLevel(newLevel + 1)) {
            newXp -= UserLevelData.getXpForLevel(newLevel + 1);
            newLevel++;
        }

        String query = "UPDATE user_levels SET xp = ?, level = ?, total_xp = ?, messages_count = ?, " +
                       "last_xp_time = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                       "WHERE guild_id = ? AND user_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, newXp);
            stmt.setInt(2, newLevel);
            stmt.setLong(3, newTotalXp);
            stmt.setInt(4, newMessagesCount);
            stmt.setString(5, guildId);
            stmt.setString(6, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        // Return new level if leveled up
        return newLevel > userData.level ? newLevel : -1;
    }

    /**
     * Add voice XP to a user
     */
    public int addVoiceXpToUser(String guildId, String userId, int xpAmount, int minutesInVoice) {
        UserLevelData userData = getOrCreateUserLevel(guildId, userId);

        long newXp = userData.xp + xpAmount;
        long newTotalXp = userData.totalXp + xpAmount;
        int newLevel = userData.level;
        int newVoiceMinutes = userData.voiceMinutes + minutesInVoice;

        // Check for level up(s)
        while (newXp >= UserLevelData.getXpForLevel(newLevel + 1)) {
            newXp -= UserLevelData.getXpForLevel(newLevel + 1);
            newLevel++;
        }

        String query = "UPDATE user_levels SET xp = ?, level = ?, total_xp = ?, voice_minutes = ?, " +
                       "last_voice_xp_time = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP " +
                       "WHERE guild_id = ? AND user_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, newXp);
            stmt.setInt(2, newLevel);
            stmt.setLong(3, newTotalXp);
            stmt.setInt(4, newVoiceMinutes);
            stmt.setString(5, guildId);
            stmt.setString(6, userId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return newLevel > userData.level ? newLevel : -1;
    }

    /**
     * Check if user is on XP cooldown
     */
    public boolean isUserOnXpCooldown(String guildId, String userId, int cooldownSeconds) {
        String query = "SELECT last_xp_time FROM user_levels WHERE guild_id = ? AND user_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Timestamp lastXpTime = rs.getTimestamp("last_xp_time");
                if (lastXpTime == null) return false;

                long elapsed = System.currentTimeMillis() - lastXpTime.getTime();
                return elapsed < (cooldownSeconds * 1000L);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Check if user is on voice XP cooldown
     */
    public boolean isUserOnVoiceXpCooldown(String guildId, String userId, int cooldownSeconds) {
        String query = "SELECT last_voice_xp_time FROM user_levels WHERE guild_id = ? AND user_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                Timestamp lastVoiceXpTime = rs.getTimestamp("last_voice_xp_time");
                if (lastVoiceXpTime == null) return false;

                long elapsed = System.currentTimeMillis() - lastVoiceXpTime.getTime();
                return elapsed < (cooldownSeconds * 1000L);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Check if user is on reaction XP cooldown
     */
    private final Map<String, Long> reactionCooldowns = new ConcurrentHashMap<>();

    public boolean isUserOnReactionXpCooldown(String guildId, String userId, String cooldownType, int cooldownSeconds) {
        String key = guildId + ":" + userId + ":" + cooldownType;
        Long lastTime = reactionCooldowns.get(key);

        if (lastTime == null) {
            reactionCooldowns.put(key, System.currentTimeMillis());
            return false;
        }

        long elapsed = System.currentTimeMillis() - lastTime;
        if (elapsed >= (cooldownSeconds * 1000L)) {
            reactionCooldowns.put(key, System.currentTimeMillis());
            return false;
        }

        return true;
    }

    /**
     * Set user level directly (for admin commands)
     */
    public boolean setUserLevel(String guildId, String userId, int level) {
        getOrCreateUserLevel(guildId, userId);

        String query = "UPDATE user_levels SET level = ?, xp = 0, updated_at = CURRENT_TIMESTAMP " +
                       "WHERE guild_id = ? AND user_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, level);
            stmt.setString(2, guildId);
            stmt.setString(3, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Set user XP directly (for admin commands)
     */
    public boolean setUserXp(String guildId, String userId, long xp) {
        getOrCreateUserLevel(guildId, userId);

        // Calculate level from XP
        int level = 0;
        long remainingXp = xp;
        while (remainingXp >= UserLevelData.getXpForLevel(level + 1)) {
            remainingXp -= UserLevelData.getXpForLevel(level + 1);
            level++;
        }

        String query = "UPDATE user_levels SET xp = ?, level = ?, total_xp = ?, updated_at = CURRENT_TIMESTAMP " +
                       "WHERE guild_id = ? AND user_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, remainingXp);
            stmt.setInt(2, level);
            stmt.setLong(3, xp);
            stmt.setString(4, guildId);
            stmt.setString(5, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Add XP to user (for admin commands)
     */
    public int addUserXp(String guildId, String userId, long xpToAdd) {
        return addXpToUser(guildId, userId, (int) xpToAdd);
    }

    public int calculateLevelFromXp(String guildId, long totalXp) {
        // Calculate level from total XP
        int level = 0;
        long remainingXp = totalXp;
        while (remainingXp >= UserLevelData.getXpForLevel(level + 1)) {
            remainingXp -= UserLevelData.getXpForLevel(level + 1);
            level++;
        }
        return level;
    }

    /**
     * Remove XP from user
     */
    public boolean removeUserXp(String guildId, String userId, long xpToRemove) {
        UserLevelData userData = getOrCreateUserLevel(guildId, userId);

        long newTotalXp = Math.max(0, userData.totalXp - xpToRemove);

        // Recalculate level from total XP
        int level = 0;
        long remainingXp = newTotalXp;
        while (remainingXp >= UserLevelData.getXpForLevel(level + 1)) {
            remainingXp -= UserLevelData.getXpForLevel(level + 1);
            level++;
        }

        String query = "UPDATE user_levels SET xp = ?, level = ?, total_xp = ?, updated_at = CURRENT_TIMESTAMP " +
                       "WHERE guild_id = ? AND user_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, remainingXp);
            stmt.setInt(2, level);
            stmt.setLong(3, newTotalXp);
            stmt.setString(4, guildId);
            stmt.setString(5, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Reset user level data (for admin commands)
     */
    public boolean resetUserLevel(String guildId, String userId) {
        String query = "UPDATE user_levels SET xp = 0, level = 0, total_xp = 0, messages_count = 0, " +
                       "voice_minutes = 0, last_xp_time = NULL, last_voice_xp_time = NULL, " +
                       "updated_at = CURRENT_TIMESTAMP WHERE guild_id = ? AND user_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete user level data (for when user leaves and reset_on_leave is true)
     */
    public boolean deleteUserLevel(String guildId, String userId) {
        String query = "DELETE FROM user_levels WHERE guild_id = ? AND user_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get leaderboard for a guild
     */
    public List<UserLevelData> getLeaderboard(String guildId, int limit, int offset) {
        List<UserLevelData> leaderboard = new ArrayList<>();
        String query = "SELECT * FROM user_levels WHERE guild_id = ? ORDER BY total_xp DESC LIMIT ? OFFSET ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setInt(2, limit);
            stmt.setInt(3, offset);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                leaderboard.add(extractUserLevelData(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return leaderboard;
    }

    /**
     * Get user rank in the guild
     */
    public int getUserRank(String guildId, String userId) {
        String query = "SELECT COUNT(*) + 1 AS rank FROM user_levels " +
                       "WHERE guild_id = ? AND total_xp > (SELECT COALESCE(total_xp, 0) FROM user_levels WHERE guild_id = ? AND user_id = ?)";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setString(2, guildId);
            stmt.setString(3, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("rank");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Get total user count with levels in a guild
     */
    public int getTotalLeveledUsers(String guildId) {
        String query = "SELECT COUNT(*) FROM user_levels WHERE guild_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Get users at a specific level
     */
    public List<UserLevelData> getUsersAtLevel(String guildId, int level) {
        List<UserLevelData> users = new ArrayList<>();
        String query = "SELECT * FROM user_levels WHERE guild_id = ? AND level = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.setInt(2, level);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(extractUserLevelData(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return users;
    }

    /**
     * Reset all levels for a guild
     */
    public boolean resetGuildLevels(String guildId) {
        String query = "DELETE FROM user_levels WHERE guild_id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Helper method to extract UserLevelData from ResultSet
     */
    private UserLevelData extractUserLevelData(ResultSet rs) throws SQLException {
        Timestamp lastXpTimestamp = rs.getTimestamp("last_xp_time");
        Timestamp lastVoiceXpTimestamp = rs.getTimestamp("last_voice_xp_time");

        return new UserLevelData(
                rs.getInt("id"),
                rs.getString("guild_id"),
                rs.getString("user_id"),
                rs.getLong("xp"),
                rs.getInt("level"),
                rs.getLong("total_xp"),
                rs.getInt("messages_count"),
                rs.getInt("voice_minutes"),
                lastXpTimestamp != null ? lastXpTimestamp.toLocalDateTime() : null,
                lastVoiceXpTimestamp != null ? lastVoiceXpTimestamp.toLocalDateTime() : null
        );
    }

    // ==================== SETUP WIZARD HELPER METHODS ====================

    /**
     * Get the default ticket category for setup wizard
     */
    public String getDefaultTicketCategory(String guildId) {
        String query = "SELECT setup_ticket_category FROM guilds WHERE id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("setup_ticket_category");
            }
        } catch (SQLException e) {
            // Column might not exist yet, ignore
        }
        return null;
    }

    /**
     * Set the default ticket category for setup wizard
     */
    public void setDefaultTicketCategory(String guildId, String categoryId) {
        // First ensure the column exists
        try (Connection connection = getConnection()) {
            String alterQuery = "ALTER TABLE guilds ADD COLUMN IF NOT EXISTS setup_ticket_category VARCHAR(32)";
            PreparedStatement alterStmt = connection.prepareStatement(alterQuery);
            alterStmt.executeUpdate();
        } catch (SQLException e) {
            // Column might already exist
        }

        String query = "UPDATE guilds SET setup_ticket_category = ? WHERE id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, categoryId);
            stmt.setString(2, guildId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the default ticket support role for setup wizard
     */
    public String getDefaultTicketSupportRole(String guildId) {
        String query = "SELECT setup_ticket_support_role FROM guilds WHERE id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("setup_ticket_support_role");
            }
        } catch (SQLException e) {
            // Column might not exist yet, ignore
        }
        return null;
    }

    /**
     * Set the default ticket support role for setup wizard
     */
    public void setDefaultTicketSupportRole(String guildId, String roleId) {
        // First ensure the column exists
        try (Connection connection = getConnection()) {
            String alterQuery = "ALTER TABLE guilds ADD COLUMN IF NOT EXISTS setup_ticket_support_role VARCHAR(32)";
            PreparedStatement alterStmt = connection.prepareStatement(alterQuery);
            alterStmt.executeUpdate();
        } catch (SQLException e) {
            // Column might already exist
        }

        String query = "UPDATE guilds SET setup_ticket_support_role = ? WHERE id = ?";
        try (Connection connection = getConnection();
             PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, roleId);
            stmt.setString(2, guildId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
