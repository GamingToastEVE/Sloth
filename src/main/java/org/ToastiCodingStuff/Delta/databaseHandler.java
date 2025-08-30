package org.ToastiCodingStuff.Delta;

import java.io.PrintStream;
import java.sql.*;

public class databaseHandler {

    private final Connection connection;
    public databaseHandler() {
        Connection connection1;
        try {
            connection1 = DriverManager.getConnection("jdbc:sqlite:server.db");
        } catch (SQLException e) {
            connection1 = null;
            System.err.println("Database connection error: " + e.getMessage());
        }
        this.connection = connection1;
        
        // Initialize database tables after connection is established
        if (this.connection != null) {
            try {
                initializeTables();
            } catch (Exception e) {
                System.err.println("Error initializing database tables: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Initialize all database tables if they don't exist
     */
    private void initializeTables() {
        try {
            // Enable foreign keys
            Statement stmt = connection.createStatement();
            stmt.execute("PRAGMA foreign_keys = ON;");
            
            createGuildsTable();
            createUsersTable();
            createWarningsTable();
            createModerationActionsTable();
            createTicketsTable();
            createTicketMessagesTable();
            createGuildSettingsTable();
            createAutomodRulesTable();
            createRolePermissionsTable();
            createBotLogsTable();
            createStatisticsTable();
            createTemporaryDataTable();
            
            // Create legacy tables for backward compatibility
            createLegacyTables();
            
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Create guilds table - main table for Discord servers/guilds
     */
    private void createGuildsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS guilds (" +
            "id INTEGER PRIMARY KEY, " +
            "name TEXT NOT NULL, " +
            "prefix TEXT DEFAULT '!', " +
            "language TEXT DEFAULT 'de', " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "active INTEGER DEFAULT 1" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
        
        // Create trigger for updated_at
        String trigger = "CREATE TRIGGER IF NOT EXISTS update_guilds_updated_at " +
            "AFTER UPDATE ON guilds " +
            "FOR EACH ROW " +
            "BEGIN " +
                "UPDATE guilds SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id; " +
            "END";
        stmt.execute(trigger);
    }

    /**
     * Create users table
     */
    private void createUsersTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS users (" +
            "id INTEGER PRIMARY KEY, " +
            "username TEXT NOT NULL, " +
            "discriminator TEXT, " +
            "avatar TEXT, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TEXT DEFAULT CURRENT_TIMESTAMP" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
        
        // Create trigger for updated_at
        String trigger = "CREATE TRIGGER IF NOT EXISTS update_users_updated_at " +
            "AFTER UPDATE ON users " +
            "FOR EACH ROW " +
            "BEGIN " +
                "UPDATE users SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id; " +
            "END";
        stmt.execute(trigger);
    }

    /**
     * Create warnings table
     */
    private void createWarningsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS warnings (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "guild_id INTEGER NOT NULL, " +
            "user_id INTEGER NOT NULL, " +
            "moderator_id INTEGER NOT NULL, " +
            "reason TEXT NOT NULL, " +
            "severity TEXT DEFAULT 'MEDIUM' CHECK(severity IN ('LOW', 'MEDIUM', 'HIGH', 'SEVERE')), " +
            "active INTEGER DEFAULT 1, " +
            "expires_at TEXT, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE, " +
            "FOREIGN KEY (user_id) REFERENCES users(id), " +
            "FOREIGN KEY (moderator_id) REFERENCES users(id)" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
        
        // Create indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_warnings_guild_user ON warnings(guild_id, user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_warnings_active ON warnings(active)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_warnings_expires ON warnings(expires_at)");
    }

    /**
     * Create moderation_actions table
     */
    private void createModerationActionsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS moderation_actions (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "guild_id INTEGER NOT NULL, " +
            "user_id INTEGER NOT NULL, " +
            "moderator_id INTEGER NOT NULL, " +
            "action_type TEXT NOT NULL CHECK(action_type IN ('KICK', 'BAN', 'TEMP_BAN', 'MUTE', 'TEMP_MUTE', 'UNMUTE', 'UNBAN')), " +
            "reason TEXT NOT NULL, " +
            "duration INTEGER, " +
            "expires_at TEXT, " +
            "active INTEGER DEFAULT 1, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE, " +
            "FOREIGN KEY (user_id) REFERENCES users(id), " +
            "FOREIGN KEY (moderator_id) REFERENCES users(id)" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
        
        // Create indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_moderation_guild_user ON moderation_actions(guild_id, user_id)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_moderation_action_type ON moderation_actions(action_type)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_moderation_active_expires ON moderation_actions(active, expires_at)");
    }

    /**
     * Create tickets table
     */
    private void createTicketsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS tickets (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "guild_id INTEGER NOT NULL, " +
            "user_id INTEGER NOT NULL, " +
            "channel_id INTEGER UNIQUE, " +
            "category TEXT DEFAULT 'general', " +
            "subject TEXT, " +
            "status TEXT DEFAULT 'OPEN' CHECK(status IN ('OPEN', 'IN_PROGRESS', 'WAITING', 'CLOSED')), " +
            "priority TEXT DEFAULT 'MEDIUM' CHECK(priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')), " +
            "assigned_to INTEGER, " +
            "closed_by INTEGER, " +
            "closed_reason TEXT, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "closed_at TEXT, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE, " +
            "FOREIGN KEY (user_id) REFERENCES users(id), " +
            "FOREIGN KEY (assigned_to) REFERENCES users(id), " +
            "FOREIGN KEY (closed_by) REFERENCES users(id)" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
        
        // Create indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_guild_status ON tickets(guild_id, status)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_tickets_user ON tickets(user_id)");
        
        // Create trigger for updated_at
        String trigger = "CREATE TRIGGER IF NOT EXISTS update_tickets_updated_at " +
            "AFTER UPDATE ON tickets " +
            "FOR EACH ROW " +
            "BEGIN " +
                "UPDATE tickets SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id; " +
            "END";
        stmt.execute(trigger);
    }

    /**
     * Create ticket_messages table
     */
    private void createTicketMessagesTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS ticket_messages (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
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
        
        // Create indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_ticket_messages_ticket_created ON ticket_messages(ticket_id, created_at)");
    }

    /**
     * Create guild_settings table
     */
    private void createGuildSettingsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS guild_settings (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "guild_id INTEGER NOT NULL UNIQUE, " +
            "modlog_channel INTEGER, " +
            "automod_enabled INTEGER DEFAULT 0, " +
            "warn_threshold_kick INTEGER DEFAULT 5, " +
            "warn_threshold_ban INTEGER DEFAULT 8, " +
            "warn_expire_days INTEGER DEFAULT 30, " +
            "ticket_category INTEGER, " +
            "ticket_channel INTEGER, " +
            "ticket_role INTEGER, " +
            "ticket_transcript INTEGER DEFAULT 1, " +
            "join_role INTEGER, " +
            "mute_role INTEGER, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
        
        // Create trigger for updated_at
        String trigger = "CREATE TRIGGER IF NOT EXISTS update_guild_settings_updated_at " +
            "AFTER UPDATE ON guild_settings " +
            "FOR EACH ROW " +
            "BEGIN " +
                "UPDATE guild_settings SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id; " +
            "END";
        stmt.execute(trigger);
    }

    /**
     * Create automod_rules table
     */
    private void createAutomodRulesTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS automod_rules (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "guild_id INTEGER NOT NULL, " +
            "name TEXT NOT NULL, " +
            "rule_type TEXT NOT NULL CHECK(rule_type IN ('SPAM', 'CAPS', 'LINKS', 'INVITE', 'BADWORDS', 'MENTION_SPAM')), " +
            "enabled INTEGER DEFAULT 1, " +
            "action TEXT DEFAULT 'WARN' CHECK(action IN ('WARN', 'MUTE', 'KICK', 'BAN', 'DELETE')), " +
            "threshold INTEGER DEFAULT 1, " +
            "duration INTEGER, " +
            "whitelist TEXT, " +
            "config TEXT, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
        
        // Create indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_automod_guild_enabled ON automod_rules(guild_id, enabled)");
        
        // Create trigger for updated_at
        String trigger = "CREATE TRIGGER IF NOT EXISTS update_automod_rules_updated_at " +
            "AFTER UPDATE ON automod_rules " +
            "FOR EACH ROW " +
            "BEGIN " +
                "UPDATE automod_rules SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id; " +
            "END";
        stmt.execute(trigger);
    }

    /**
     * Create role_permissions table
     */
    private void createRolePermissionsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS role_permissions (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "guild_id INTEGER NOT NULL, " +
            "role_id INTEGER NOT NULL, " +
            "permission TEXT NOT NULL, " +
            "allowed INTEGER DEFAULT 1, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE, " +
            "UNIQUE(guild_id, role_id, permission)" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
        
        // Create indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_role_permissions_guild_role ON role_permissions(guild_id, role_id)");
    }

    /**
     * Create bot_logs table
     */
    private void createBotLogsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS bot_logs (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "guild_id INTEGER, " +
            "user_id INTEGER, " +
            "event_type TEXT NOT NULL, " +
            "message TEXT NOT NULL, " +
            "data TEXT, " +
            "level TEXT DEFAULT 'INFO' CHECK(level IN ('DEBUG', 'INFO', 'WARN', 'ERROR')), " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
        
        // Create indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_bot_logs_guild_created ON bot_logs(guild_id, created_at)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_bot_logs_level_created ON bot_logs(level, created_at)");
    }

    /**
     * Create statistics table
     */
    private void createStatisticsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS statistics (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "guild_id INTEGER NOT NULL, " +
            "date TEXT NOT NULL, " +
            "warnings_issued INTEGER DEFAULT 0, " +
            "kicks_performed INTEGER DEFAULT 0, " +
            "bans_performed INTEGER DEFAULT 0, " +
            "tickets_created INTEGER DEFAULT 0, " +
            "tickets_closed INTEGER DEFAULT 0, " +
            "automod_actions INTEGER DEFAULT 0, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE, " +
            "UNIQUE(guild_id, date)" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
    }

    /**
     * Create temporary_data table
     */
    private void createTemporaryDataTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS temporary_data (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "guild_id INTEGER NOT NULL, " +
            "user_id INTEGER NOT NULL, " +
            "data_type TEXT NOT NULL, " +
            "data TEXT NOT NULL, " +
            "expires_at TEXT NOT NULL, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
        
        // Create indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_temporary_data_expires ON temporary_data(expires_at)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_temporary_data_guild_user_type ON temporary_data(guild_id, user_id, data_type)");
    }

    /**
     * Create legacy tables for backward compatibility
     */
    private void createLegacyTables() throws SQLException {
        // Create log_channels table (for existing functionality)
        String logChannelsTable = "CREATE TABLE IF NOT EXISTS log_channels (" +
            "guildid TEXT PRIMARY KEY, " +
            "channelid TEXT NOT NULL" +
            ")";
        
        // Create warn_system_settings table (for existing functionality)
        String warnSystemTable = "CREATE TABLE IF NOT EXISTS warn_system_settings (" +
            "guild_id TEXT PRIMARY KEY, " +
            "max_warns INTEGER NOT NULL, " +
            "minutes_muted INTEGER NOT NULL, " +
            "role_id TEXT NOT NULL, " +
            "warn_time_hours INTEGER NOT NULL" +
            ")";
        
        Statement stmt = connection.createStatement();
        stmt.execute(logChannelsTable);
        stmt.execute(warnSystemTable);
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

    //Log Channel Databasekram

    public String getLogChannelID (String guildID) {
        try {
            connection.setAutoCommit(false);
            String hasLogChannel1 = "SELECT channelid FROM log_channels WHERE guildid=?";
            PreparedStatement hasLogChannel2 = connection.prepareStatement(hasLogChannel1);
            hasLogChannel2.setString(1, guildID);
            ResultSet rs = hasLogChannel2.executeQuery();
            String channelid = rs.getString("channelid");
            if (channelid == null || channelid.equals("0")) {
                return "Couldnt find a Log Channel";
            }
            return channelid;
        } catch (SQLException e) {
            e.printStackTrace();
            return "Error: " + e;
        }
    }

    public boolean hasLogChannel (String guildID) {
        try {
            connection.setAutoCommit(false);
            String hasLogChannel1 = "SELECT channelid FROM log_channels WHERE guildid=?";
            PreparedStatement hasLogChannel2 = connection.prepareStatement(hasLogChannel1);
            hasLogChannel2.setString(1, guildID);
            ResultSet rs = hasLogChannel2.executeQuery();
            String channelid = rs.getString("channelid");
            if (channelid == null || channelid.equals("0")) {
                return false;
            }
            return true;
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
    public boolean hasWarnSystemSettings (String guildID) {
        try {
            String hasWarnSystemSettingsAndStuff = "SELECT max_warns, minutes_muted, role_id, warn_time_hours FROM warn_system_settings WHERE guild_id=?";
            PreparedStatement hasWarnSystem = connection.prepareStatement(hasWarnSystemSettingsAndStuff);
            hasWarnSystem.setString(1, guildID);
            ResultSet rs = hasWarnSystem.executeQuery();
            if (rs == null) {
                return false;
            }
            return true;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getMaxWarns (String guildID) {
        try {
            String getMaxWarnsString = "SELECT max_warns FROM warn_system_settings WHERE guild_id=?";
            PreparedStatement getMaxWarnsStatement = connection.prepareStatement(getMaxWarnsString);
            getMaxWarnsStatement.setString(1, guildID);
            ResultSet rs = getMaxWarnsStatement.executeQuery();
            if (rs == null) {
                return 0;
            }
            return rs.getInt("max_warns");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getTimeMuted (String guildID) {
        try {
            String getMinutesMutedString = "SELECT minutes_muted FROM warn_system_settings WHERE guild_id=?";
            PreparedStatement getMinutesMutedStatement = connection.prepareStatement(getMinutesMutedString);
            getMinutesMutedStatement.setString(1, guildID);
            ResultSet rs = getMinutesMutedStatement.executeQuery();
            if (rs == null) {
                return 0;
            }
            return rs.getInt("minutes_muted");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public int getWarnTimeHours (String guildID) {
        try {
            String getWarnTimeHoursString = "SELECT warn_time_hours FROM warn_system_settings WHERE guild_id=?";
            PreparedStatement getWarnTimeHoursStatement = connection.prepareStatement(getWarnTimeHoursString);
            getWarnTimeHoursStatement.setString(1, guildID);
            ResultSet rs = getWarnTimeHoursStatement.executeQuery();
            if (rs == null) {
                return 0;
            }
            return rs.getInt("warn_time_hours");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getWarnRoleID (String guildID) {
        try {
            String getRoleIDString = "SELECT role_id FROM warn_system_settings WHERE guild_id=?";
            PreparedStatement getRoleIDStatement = connection.prepareStatement(getRoleIDString);
            getRoleIDStatement.setString(1, guildID);
            ResultSet rs = getRoleIDStatement.executeQuery();
            if (rs == null) {
                return "0";
            }
            return rs.getString("role_id");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setWarnSettings (String guildID, int maxWarns, int minutesMuted, String roleID, int warnTimeHours) {
        try {
            if (hasWarnSystemSettings(guildID)) {
                String setWarnSettings1 = "UPDATE warn_system_settings SET max_warns=?, minutes_muted=?, role_id=?, warn_time_hours=? WHERE guild_id=?";
                PreparedStatement setWarnSettings2 = connection.prepareStatement(setWarnSettings1);
                setWarnSettings2.setInt(1, maxWarns);
                setWarnSettings2.setInt(2, minutesMuted);
                setWarnSettings2.setString(3, roleID);
                setWarnSettings2.setInt(4, warnTimeHours);
                setWarnSettings2.setString(5, guildID); // Fixed: added guildID parameter
                setWarnSettings2.execute();
                return;
            }
            String setWarnSettings3 = "INSERT INTO warn_system_settings(guild_id, max_warns, minutes_muted, role_id, warn_time_hours) VALUES(?,?,?,?,?)";
            PreparedStatement setWarnSettings4 = connection.prepareStatement(setWarnSettings3);
            setWarnSettings4.setString(1, guildID); // Fixed: added guildID parameter
            setWarnSettings4.setInt(2, maxWarns);
            setWarnSettings4.setInt(3, minutesMuted);
            setWarnSettings4.setString(4, roleID);
            setWarnSettings4.setInt(5, warnTimeHours);
            setWarnSettings4.execute();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean userInWarnTable (String guildID, String userID) {
        try {
            String checkIfUserIsInGuildTable = "SELECT user_id FROM warnings WHERE guild_id=? AND user_id=?";
            PreparedStatement checkIfUserIsInGuildTableStatement = connection.prepareStatement(checkIfUserIsInGuildTable);
            checkIfUserIsInGuildTableStatement.setString(1, guildID);
            checkIfUserIsInGuildTableStatement.setString(2, userID);
            ResultSet rs = checkIfUserIsInGuildTableStatement.executeQuery();
            return rs.next(); // returns true if there's at least one result
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // ============================================================================
    // INSERT METHODS FOR DATABASE TABLES
    // ============================================================================

    /**
     * Insert or update a guild in the guilds table
     */
    public boolean insertOrUpdateGuild(String guildId, String name, String prefix, String language) {
        try {
            connection.setAutoCommit(false);
            
            // Check if guild exists
            String checkSql = "SELECT id FROM guilds WHERE id=?";
            PreparedStatement checkStmt = connection.prepareStatement(checkSql);
            checkStmt.setString(1, guildId);
            ResultSet rs = checkStmt.executeQuery();
            
            if (rs.next()) {
                // Update existing guild
                String updateSql = "UPDATE guilds SET name=?, prefix=?, language=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
                PreparedStatement updateStmt = connection.prepareStatement(updateSql);
                updateStmt.setString(1, name);
                updateStmt.setString(2, prefix != null ? prefix : "!");
                updateStmt.setString(3, language != null ? language : "de");
                updateStmt.setString(4, guildId);
                updateStmt.executeUpdate();
            } else {
                // Insert new guild
                String insertSql = "INSERT INTO guilds(id, name, prefix, language) VALUES(?,?,?,?)";
                PreparedStatement insertStmt = connection.prepareStatement(insertSql);
                insertStmt.setString(1, guildId);
                insertStmt.setString(2, name);
                insertStmt.setString(3, prefix != null ? prefix : "!");
                insertStmt.setString(4, language != null ? language : "de");
                insertStmt.executeUpdate();
            }
            
            connection.commit();
            return true;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Insert or update a user in the users table
     */
    public boolean insertOrUpdateUser(String userId, String username, String discriminator, String avatar) {
        try {
            connection.setAutoCommit(false);
            
            // Check if user exists
            String checkSql = "SELECT id FROM users WHERE id=?";
            PreparedStatement checkStmt = connection.prepareStatement(checkSql);
            checkStmt.setString(1, userId);
            ResultSet rs = checkStmt.executeQuery();
            
            if (rs.next()) {
                // Update existing user
                String updateSql = "UPDATE users SET username=?, discriminator=?, avatar=?, updated_at=CURRENT_TIMESTAMP WHERE id=?";
                PreparedStatement updateStmt = connection.prepareStatement(updateSql);
                updateStmt.setString(1, username);
                updateStmt.setString(2, discriminator);
                updateStmt.setString(3, avatar);
                updateStmt.setString(4, userId);
                updateStmt.executeUpdate();
            } else {
                // Insert new user
                String insertSql = "INSERT INTO users(id, username, discriminator, avatar) VALUES(?,?,?,?)";
                PreparedStatement insertStmt = connection.prepareStatement(insertSql);
                insertStmt.setString(1, userId);
                insertStmt.setString(2, username);
                insertStmt.setString(3, discriminator);
                insertStmt.setString(4, avatar);
                insertStmt.executeUpdate();
            }
            
            connection.commit();
            return true;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Insert a warning into the warnings table
     */
    public int insertWarning(String guildId, String userId, String moderatorId, String reason, String severity, String expiresAt) {
        try {
            connection.setAutoCommit(false);
            
            String insertSql = "INSERT INTO warnings(guild_id, user_id, moderator_id, reason, severity, expires_at) VALUES(?,?,?,?,?,?)";
            PreparedStatement insertStmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setString(1, guildId);
            insertStmt.setString(2, userId);
            insertStmt.setString(3, moderatorId);
            insertStmt.setString(4, reason);
            insertStmt.setString(5, severity != null ? severity : "MEDIUM");
            insertStmt.setString(6, expiresAt);
            
            int affectedRows = insertStmt.executeUpdate();
            if (affectedRows == 0) {
                connection.rollback();
                return -1;
            }
            
            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            int warningId = -1;
            if (generatedKeys.next()) {
                warningId = generatedKeys.getInt(1);
            }
            
            connection.commit();
            return warningId;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Insert a moderation action into the moderation_actions table
     */
    public int insertModerationAction(String guildId, String userId, String moderatorId, String actionType, String reason, Integer duration, String expiresAt) {
        try {
            connection.setAutoCommit(false);
            
            String insertSql = "INSERT INTO moderation_actions(guild_id, user_id, moderator_id, action_type, reason, duration, expires_at) VALUES(?,?,?,?,?,?,?)";
            PreparedStatement insertStmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setString(1, guildId);
            insertStmt.setString(2, userId);
            insertStmt.setString(3, moderatorId);
            insertStmt.setString(4, actionType);
            insertStmt.setString(5, reason);
            if (duration != null) {
                insertStmt.setInt(6, duration);
            } else {
                insertStmt.setNull(6, Types.INTEGER);
            }
            insertStmt.setString(7, expiresAt);
            
            int affectedRows = insertStmt.executeUpdate();
            if (affectedRows == 0) {
                connection.rollback();
                return -1;
            }
            
            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            int actionId = -1;
            if (generatedKeys.next()) {
                actionId = generatedKeys.getInt(1);
            }
            
            connection.commit();
            return actionId;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Insert a ticket into the tickets table
     */
    public int insertTicket(String guildId, String userId, String channelId, String category, String subject, String status, String priority) {
        try {
            connection.setAutoCommit(false);
            
            String insertSql = "INSERT INTO tickets(guild_id, user_id, channel_id, category, subject, status, priority) VALUES(?,?,?,?,?,?,?)";
            PreparedStatement insertStmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setString(1, guildId);
            insertStmt.setString(2, userId);
            insertStmt.setString(3, channelId);
            insertStmt.setString(4, category != null ? category : "general");
            insertStmt.setString(5, subject);
            insertStmt.setString(6, status != null ? status : "OPEN");
            insertStmt.setString(7, priority != null ? priority : "MEDIUM");
            
            int affectedRows = insertStmt.executeUpdate();
            if (affectedRows == 0) {
                connection.rollback();
                return -1;
            }
            
            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            int ticketId = -1;
            if (generatedKeys.next()) {
                ticketId = generatedKeys.getInt(1);
            }
            
            connection.commit();
            return ticketId;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Insert a message into the ticket_messages table
     */
    public int insertTicketMessage(int ticketId, String userId, String messageId, String content, String attachments, boolean isStaff) {
        try {
            connection.setAutoCommit(false);
            
            String insertSql = "INSERT INTO ticket_messages(ticket_id, user_id, message_id, content, attachments, is_staff) VALUES(?,?,?,?,?,?)";
            PreparedStatement insertStmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setInt(1, ticketId);
            insertStmt.setString(2, userId);
            insertStmt.setString(3, messageId);
            insertStmt.setString(4, content);
            insertStmt.setString(5, attachments);
            insertStmt.setInt(6, isStaff ? 1 : 0);
            
            int affectedRows = insertStmt.executeUpdate();
            if (affectedRows == 0) {
                connection.rollback();
                return -1;
            }
            
            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            int messageId_internal = -1;
            if (generatedKeys.next()) {
                messageId_internal = generatedKeys.getInt(1);
            }
            
            connection.commit();
            return messageId_internal;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Insert or update guild settings in the guild_settings table
     */
    public boolean insertOrUpdateGuildSettings(String guildId, String modlogChannel, boolean automodEnabled, 
                                               int warnThresholdKick, int warnThresholdBan, int warnExpireDays,
                                               String ticketCategory, String ticketChannel, String ticketRole, 
                                               boolean ticketTranscript, String joinRole, String muteRole) {
        try {
            connection.setAutoCommit(false);
            
            // Check if settings exist
            String checkSql = "SELECT id FROM guild_settings WHERE guild_id=?";
            PreparedStatement checkStmt = connection.prepareStatement(checkSql);
            checkStmt.setString(1, guildId);
            ResultSet rs = checkStmt.executeQuery();
            
            if (rs.next()) {
                // Update existing settings
                String updateSql = "UPDATE guild_settings SET modlog_channel=?, automod_enabled=?, warn_threshold_kick=?, " +
                                   "warn_threshold_ban=?, warn_expire_days=?, ticket_category=?, ticket_channel=?, ticket_role=?, " +
                                   "ticket_transcript=?, join_role=?, mute_role=?, updated_at=CURRENT_TIMESTAMP WHERE guild_id=?";
                PreparedStatement updateStmt = connection.prepareStatement(updateSql);
                updateStmt.setString(1, modlogChannel);
                updateStmt.setInt(2, automodEnabled ? 1 : 0);
                updateStmt.setInt(3, warnThresholdKick);
                updateStmt.setInt(4, warnThresholdBan);
                updateStmt.setInt(5, warnExpireDays);
                updateStmt.setString(6, ticketCategory);
                updateStmt.setString(7, ticketChannel);
                updateStmt.setString(8, ticketRole);
                updateStmt.setInt(9, ticketTranscript ? 1 : 0);
                updateStmt.setString(10, joinRole);
                updateStmt.setString(11, muteRole);
                updateStmt.setString(12, guildId);
                updateStmt.executeUpdate();
            } else {
                // Insert new settings
                String insertSql = "INSERT INTO guild_settings(guild_id, modlog_channel, automod_enabled, warn_threshold_kick, " +
                                   "warn_threshold_ban, warn_expire_days, ticket_category, ticket_channel, ticket_role, " +
                                   "ticket_transcript, join_role, mute_role) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
                PreparedStatement insertStmt = connection.prepareStatement(insertSql);
                insertStmt.setString(1, guildId);
                insertStmt.setString(2, modlogChannel);
                insertStmt.setInt(3, automodEnabled ? 1 : 0);
                insertStmt.setInt(4, warnThresholdKick);
                insertStmt.setInt(5, warnThresholdBan);
                insertStmt.setInt(6, warnExpireDays);
                insertStmt.setString(7, ticketCategory);
                insertStmt.setString(8, ticketChannel);
                insertStmt.setString(9, ticketRole);
                insertStmt.setInt(10, ticketTranscript ? 1 : 0);
                insertStmt.setString(11, joinRole);
                insertStmt.setString(12, muteRole);
                insertStmt.executeUpdate();
            }
            
            connection.commit();
            return true;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Insert an automod rule into the automod_rules table
     */
    public int insertAutomodRule(String guildId, String name, String ruleType, boolean enabled, String action, 
                                 int threshold, Integer duration, String whitelist, String config) {
        try {
            connection.setAutoCommit(false);
            
            String insertSql = "INSERT INTO automod_rules(guild_id, name, rule_type, enabled, action, threshold, duration, whitelist, config) VALUES(?,?,?,?,?,?,?,?,?)";
            PreparedStatement insertStmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setString(1, guildId);
            insertStmt.setString(2, name);
            insertStmt.setString(3, ruleType);
            insertStmt.setInt(4, enabled ? 1 : 0);
            insertStmt.setString(5, action != null ? action : "WARN");
            insertStmt.setInt(6, threshold);
            if (duration != null) {
                insertStmt.setInt(7, duration);
            } else {
                insertStmt.setNull(7, Types.INTEGER);
            }
            insertStmt.setString(8, whitelist);
            insertStmt.setString(9, config);
            
            int affectedRows = insertStmt.executeUpdate();
            if (affectedRows == 0) {
                connection.rollback();
                return -1;
            }
            
            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            int ruleId = -1;
            if (generatedKeys.next()) {
                ruleId = generatedKeys.getInt(1);
            }
            
            connection.commit();
            return ruleId;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Insert a role permission into the role_permissions table
     */
    public int insertRolePermission(String guildId, String roleId, String permission, boolean allowed) {
        try {
            connection.setAutoCommit(false);
            
            String insertSql = "INSERT OR REPLACE INTO role_permissions(guild_id, role_id, permission, allowed) VALUES(?,?,?,?)";
            PreparedStatement insertStmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setString(1, guildId);
            insertStmt.setString(2, roleId);
            insertStmt.setString(3, permission);
            insertStmt.setInt(4, allowed ? 1 : 0);
            
            int affectedRows = insertStmt.executeUpdate();
            if (affectedRows == 0) {
                connection.rollback();
                return -1;
            }
            
            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            int permissionId = -1;
            if (generatedKeys.next()) {
                permissionId = generatedKeys.getInt(1);
            }
            
            connection.commit();
            return permissionId;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Insert a bot log entry into the bot_logs table
     */
    public int insertBotLog(String guildId, String userId, String eventType, String message, String data, String level) {
        try {
            connection.setAutoCommit(false);
            
            String insertSql = "INSERT INTO bot_logs(guild_id, user_id, event_type, message, data, level) VALUES(?,?,?,?,?,?)";
            PreparedStatement insertStmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setString(1, guildId);
            insertStmt.setString(2, userId);
            insertStmt.setString(3, eventType);
            insertStmt.setString(4, message);
            insertStmt.setString(5, data);
            insertStmt.setString(6, level != null ? level : "INFO");
            
            int affectedRows = insertStmt.executeUpdate();
            if (affectedRows == 0) {
                connection.rollback();
                return -1;
            }
            
            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            int logId = -1;
            if (generatedKeys.next()) {
                logId = generatedKeys.getInt(1);
            }
            
            connection.commit();
            return logId;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Insert or update statistics in the statistics table
     */
    public boolean insertOrUpdateStatistics(String guildId, String date, int warningsIssued, int kicksPerformed, 
                                            int bansPerformed, int ticketsCreated, int ticketsClosed, int automodActions) {
        try {
            connection.setAutoCommit(false);
            
            String insertSql = "INSERT OR REPLACE INTO statistics(guild_id, date, warnings_issued, kicks_performed, bans_performed, tickets_created, tickets_closed, automod_actions) VALUES(?,?,?,?,?,?,?,?)";
            PreparedStatement insertStmt = connection.prepareStatement(insertSql);
            insertStmt.setString(1, guildId);
            insertStmt.setString(2, date);
            insertStmt.setInt(3, warningsIssued);
            insertStmt.setInt(4, kicksPerformed);
            insertStmt.setInt(5, bansPerformed);
            insertStmt.setInt(6, ticketsCreated);
            insertStmt.setInt(7, ticketsClosed);
            insertStmt.setInt(8, automodActions);
            
            int affectedRows = insertStmt.executeUpdate();
            connection.commit();
            return affectedRows > 0;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Insert temporary data into the temporary_data table
     */
    public int insertTemporaryData(String guildId, String userId, String dataType, String data, String expiresAt) {
        try {
            connection.setAutoCommit(false);
            
            String insertSql = "INSERT INTO temporary_data(guild_id, user_id, data_type, data, expires_at) VALUES(?,?,?,?,?)";
            PreparedStatement insertStmt = connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS);
            insertStmt.setString(1, guildId);
            insertStmt.setString(2, userId);
            insertStmt.setString(3, dataType);
            insertStmt.setString(4, data);
            insertStmt.setString(5, expiresAt);
            
            int affectedRows = insertStmt.executeUpdate();
            if (affectedRows == 0) {
                connection.rollback();
                return -1;
            }
            
            ResultSet generatedKeys = insertStmt.getGeneratedKeys();
            int tempDataId = -1;
            if (generatedKeys.next()) {
                tempDataId = generatedKeys.getInt(1);
            }
            
            connection.commit();
            return tempDataId;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * Utility method to clean up expired temporary data
     */
    public int cleanupExpiredTemporaryData() {
        try {
            connection.setAutoCommit(false);
            
            String deleteSql = "DELETE FROM temporary_data WHERE expires_at < datetime('now')";
            PreparedStatement deleteStmt = connection.prepareStatement(deleteSql);
            int deletedRows = deleteStmt.executeUpdate();
            
            connection.commit();
            return deletedRows;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                e.addSuppressed(rollbackEx);
            }
            e.printStackTrace();
            return -1;
        }
    }
}
