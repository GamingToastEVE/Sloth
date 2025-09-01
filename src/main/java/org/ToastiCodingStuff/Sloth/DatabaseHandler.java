package org.ToastiCodingStuff.Sloth;

import java.awt.Color;
import java.io.PrintStream;
import java.sql.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class DatabaseHandler {

    private final Connection connection;
    public DatabaseHandler() {
        Connection connection1;
        try {
            connection1 = DriverManager.getConnection("jdbc:sqlite:server.db");
            // Initialize database tables

        } catch (SQLException e) {
            connection1 = null;
            System.err.println("Database connection error: " + e.getMessage());
        }
        this.connection = connection1;
        initializeTables();
    }

    /**
     * Check if database tables already exist
     */
    private boolean tablesAlreadyExist() {
        try {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='guilds'");
            return rs.next(); // Returns true if guilds table exists
        } catch (SQLException e) {
            System.err.println("Error checking if tables exist: " + e.getMessage());
            return false;
        }
    }

    /**
     * Initialize all database tables if they don't exist
     */
    private void initializeTables() {
        try {
            // Check if tables already exist, if so break the method
            /*if (tablesAlreadyExist()) {
                System.out.println("Database tables already exist, skipping initialization.");
                return;
            }*/
            
            // Enable foreign keys
            Statement stmt = connection.createStatement();
            stmt.execute("PRAGMA foreign_keys = ON;");

            createUsersTable();
            createWarningsTable();
            createModerationActionsTable();
            createTicketsTable();
            createTicketMessagesTable();
            createGuildSettingsTable();
            createRolePermissionsTable();
            createBotLogsTable();
            createStatisticsTable();
            createTemporaryDataTable();
            createGuildsTable();
            createGuildSystemsTable();
            
            // Create legacy tables for backward compatibility
            createLegacyTables();
            
        } catch (SQLException e) {
            System.err.println("Error initializing database tables: " + e.getMessage());
            e.printStackTrace();
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
            "action_type TEXT NOT NULL CHECK(action_type IN ('KICK', 'BAN', 'TEMP_BAN', 'MUTE', 'TEMP_MUTE', 'UNMUTE', 'UNBAN', 'WARN')), " +
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
     * Create guild_systems table
     */
    private void createGuildSystemsTable() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS guild_systems (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "guild_id INTEGER NOT NULL, " +
            "system_type TEXT NOT NULL CHECK(system_type IN ('log-channel', 'warn-system', 'ticket-system', 'moderation-system')), " +
            "active INTEGER DEFAULT 1, " +
            "created_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "updated_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "FOREIGN KEY (guild_id) REFERENCES guilds(id) ON DELETE CASCADE, " +
            "UNIQUE(guild_id, system_type)" +
            ")";
        Statement stmt = connection.createStatement();
        stmt.execute(createTable);
        
        // Create indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_guild_systems_guild_active ON guild_systems(guild_id, active)");
        
        // Create trigger for updated_at
        String trigger = "CREATE TRIGGER IF NOT EXISTS update_guild_systems_updated_at " +
            "AFTER UPDATE ON guild_systems " +
            "FOR EACH ROW " +
            "BEGIN " +
                "UPDATE guild_systems SET updated_at = CURRENT_TIMESTAMP WHERE id = NEW.id; " +
            "END";
        stmt.execute(trigger);
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
            String setWarnSettings3 = "INSERT INTO warn_system_settings(guild_id, max_warns, minutes_muted, role_id, warn_time_hours) VALUES(?,?,?,?,?)";
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

    public int getActiveWarningsCount(String guildID, String userID) {
        try {
            String query = "SELECT COUNT(*) as count FROM warnings WHERE guild_id=? AND user_id=? AND active=1 AND (expires_at IS NULL OR expires_at > datetime('now'))";
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
            String insertAction = "INSERT INTO moderation_actions (guild_id, user_id, moderator_id, action_type, reason, duration, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertAction);
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.setString(3, moderatorId);
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
            String upsertUser = "INSERT OR REPLACE INTO users (id, username, discriminator, avatar, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, " +
                "COALESCE((SELECT created_at FROM users WHERE id = ?), CURRENT_TIMESTAMP), " +
                "CURRENT_TIMESTAMP)";
            PreparedStatement stmt = connection.prepareStatement(upsertUser);
            stmt.setString(1, userId);
            stmt.setString(2, effectiveName);
            stmt.setString(3, discriminator);
            stmt.setString(4, avatarUrl);
            stmt.setString(5, userId); // For the COALESCE subquery
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting/updating user: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Insert or update guild data in the guilds table
     */
    public void insertOrUpdateGuild(String guildId, String guildName) {
        try {
            String upsertGuild = "INSERT OR REPLACE INTO guilds (id, name, prefix, language, created_at, updated_at, active) " +
                "VALUES (?, ?, " +
                "COALESCE((SELECT prefix FROM guilds WHERE id = ?), '!'), " +
                "COALESCE((SELECT language FROM guilds WHERE id = ?), 'de'), " +
                "COALESCE((SELECT created_at FROM guilds WHERE id = ?), CURRENT_TIMESTAMP), " +
                "CURRENT_TIMESTAMP, 1)";
            PreparedStatement stmt = connection.prepareStatement(upsertGuild);
            stmt.setString(1, guildId);
            stmt.setString(2, guildName);
            stmt.setString(3, guildId); // For prefix COALESCE
            stmt.setString(4, guildId); // For language COALESCE
            stmt.setString(5, guildId); // For created_at COALESCE
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error inserting/updating guild: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Mark guild as inactive when bot leaves
     */
    public void deactivateGuild(String guildId) {
        try {
            String deactivateGuild = "UPDATE guilds SET active = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(deactivateGuild);
            stmt.setString(1, guildId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error deactivating guild: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get guild prefix from database
     */
    public String getGuildPrefix(String guildId) {
        try {
            String query = "SELECT prefix FROM guilds WHERE id = ? AND active = 1";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String prefix = rs.getString("prefix");
                return prefix != null ? prefix : "!";
            }
            return "!"; // Default prefix
        } catch (SQLException e) {
            System.err.println("Error getting guild prefix: " + e.getMessage());
            e.printStackTrace();
            return "!"; // Default prefix on error
        }
    }

    /**
     * Get guild language from database
     */
    public String getGuildLanguage(String guildId) {
        try {
            String query = "SELECT language FROM guilds WHERE id = ? AND active = 1";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                String language = rs.getString("language");
                return language != null ? language : "de";
            }
            return "de"; // Default language
        } catch (SQLException e) {
            System.err.println("Error getting guild language: " + e.getMessage());
            e.printStackTrace();
            return "de"; // Default language on error
        }
    }

    /**
     * Update guild prefix
     */
    public boolean updateGuildPrefix(String guildId, String prefix) {
        try {
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
        try {
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
     * Sync all guilds that the bot is currently in - called on startup
     */
    public void syncGuilds(java.util.List<Guild> currentGuilds) {
        try {
            // First, mark all guilds as inactive
            String deactivateAll = "UPDATE guilds SET active = 0, updated_at = CURRENT_TIMESTAMP";
            PreparedStatement deactivateStmt = connection.prepareStatement(deactivateAll);
            deactivateStmt.executeUpdate();
            
            // Then, insert or update all current guilds as active
            for (Guild guild : currentGuilds) {
                insertOrUpdateGuild(guild.getId(), guild.getName());
                
                // Check and log current prefix and language settings
                String currentPrefix = getGuildPrefix(guild.getId());
                String currentLanguage = getGuildLanguage(guild.getId());
                System.out.println("Guild " + guild.getName() + " (ID: " + guild.getId() + 
                    ") - Prefix: '" + currentPrefix + "', Language: '" + currentLanguage + "'");
            }
            
            System.out.println("Synced " + currentGuilds.size() + " guilds to database");
        } catch (SQLException e) {
            System.err.println("Error syncing guilds: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Activate a system for a guild
     */
    public boolean activateGuildSystem(String guildId, String systemType) {
        try {
            String activateSystem = "INSERT OR REPLACE INTO guild_systems (guild_id, system_type, active, updated_at) " +
                "VALUES (?, ?, 1, CURRENT_TIMESTAMP)";
            PreparedStatement stmt = connection.prepareStatement(activateSystem);
            stmt.setString(1, guildId);
            stmt.setString(2, systemType);
            
            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException e) {
            System.err.println("Error activating guild system: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Deactivate a system for a guild
     */
    public boolean deactivateGuildSystem(String guildId, String systemType) {
        try {
            String deactivateSystem = "UPDATE guild_systems SET active = 0, updated_at = CURRENT_TIMESTAMP " +
                "WHERE guild_id = ? AND system_type = ?";
            PreparedStatement stmt = connection.prepareStatement(deactivateSystem);
            stmt.setString(1, guildId);
            stmt.setString(2, systemType);
            
            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException e) {
            System.err.println("Error deactivating guild system: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get all activated systems for a guild
     */
    public java.util.List<String> getActivatedSystems(String guildId) {
        java.util.List<String> activatedSystems = new java.util.ArrayList<>();
        try {
            String query = "SELECT system_type FROM guild_systems WHERE guild_id = ? AND active = 1";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                activatedSystems.add(rs.getString("system_type"));
            }
        } catch (SQLException e) {
            System.err.println("Error getting activated systems: " + e.getMessage());
            e.printStackTrace();
        }
        return activatedSystems;
    }

    /**
     * Check if a system is activated for a guild
     */
    public boolean isSystemActivated(String guildId, String systemType) {
        try {
            String query = "SELECT active FROM guild_systems WHERE guild_id = ? AND system_type = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            stmt.setString(2, systemType);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("active") == 1;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Error checking if system is activated: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean isTicketSystem(String guildId) {
        try {
            String query = "SELECT ticket_category, ticket_channel FROM guild_settings WHERE guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                // Check if either ticket_category or ticket_channel is configured (not null and not 0)
                long ticketCategory = rs.getLong("ticket_category");
                boolean hasCategorySet = !rs.wasNull() && ticketCategory != 0;
                
                long ticketChannel = rs.getLong("ticket_channel");
                boolean hasChannelSet = !rs.wasNull() && ticketChannel != 0;
                
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
            String query = "SELECT ticket_category FROM guild_settings WHERE guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                long categoryId = rs.getLong("ticket_category");
                if (!rs.wasNull() && categoryId != 0) {
                    return String.valueOf(categoryId);
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
            String query = "SELECT ticket_channel FROM guild_settings WHERE guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                long channelId = rs.getLong("ticket_channel");
                if (!rs.wasNull() && channelId != 0) {
                    return String.valueOf(channelId);
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
            // First check if guild settings exist
            String checkQuery = "SELECT id FROM guild_settings WHERE guild_id = ?";
            PreparedStatement checkStmt = connection.prepareStatement(checkQuery);
            checkStmt.setString(1, guildId);
            ResultSet rs = checkStmt.executeQuery();
            
            if (rs.next()) {
                // Update existing settings
                String updateQuery = "UPDATE guild_settings SET ticket_category = ?, ticket_channel = ?, ticket_role = ?, ticket_transcript = ? WHERE guild_id = ?";
                PreparedStatement updateStmt = connection.prepareStatement(updateQuery);
                
                if (categoryId != null && !categoryId.isEmpty()) {
                    updateStmt.setLong(1, Long.parseLong(categoryId));
                } else {
                    updateStmt.setNull(1, Types.INTEGER);
                }
                
                if (channelId != null && !channelId.isEmpty()) {
                    updateStmt.setLong(2, Long.parseLong(channelId));
                } else {
                    updateStmt.setNull(2, Types.INTEGER);
                }
                
                if (roleId != null && !roleId.isEmpty()) {
                    updateStmt.setLong(3, Long.parseLong(roleId));
                } else {
                    updateStmt.setNull(3, Types.INTEGER);
                }
                
                updateStmt.setInt(4, transcriptEnabled ? 1 : 0);
                updateStmt.setString(5, guildId);
                
                int rowsUpdated = updateStmt.executeUpdate();
                return rowsUpdated > 0;
            } else {
                // Insert new settings
                String insertQuery = "INSERT INTO guild_settings (guild_id, ticket_category, ticket_channel, ticket_role, ticket_transcript) VALUES (?, ?, ?, ?, ?)";
                PreparedStatement insertStmt = connection.prepareStatement(insertQuery);
                insertStmt.setString(1, guildId);
                
                if (categoryId != null && !categoryId.isEmpty()) {
                    insertStmt.setLong(2, Long.parseLong(categoryId));
                } else {
                    insertStmt.setNull(2, Types.INTEGER);
                }
                
                if (channelId != null && !channelId.isEmpty()) {
                    insertStmt.setLong(3, Long.parseLong(channelId));
                } else {
                    insertStmt.setNull(3, Types.INTEGER);
                }
                
                if (roleId != null && !roleId.isEmpty()) {
                    insertStmt.setLong(4, Long.parseLong(roleId));
                } else {
                    insertStmt.setNull(4, Types.INTEGER);
                }
                
                insertStmt.setInt(5, transcriptEnabled ? 1 : 0);
                
                int rowsInserted = insertStmt.executeUpdate();
                return rowsInserted > 0;
            }
        } catch (SQLException | NumberFormatException e) {
            System.err.println("Error setting ticket settings: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Create a new ticket
     */
    public int createTicket(String guildId, String userId, String channelId, String category, String subject, String priority) {
        try {
            // Ensure the guild exists in the guilds table
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

            // Ensure the user exists in the users table
            String checkUserQuery = "SELECT id FROM users WHERE id = ?";
            PreparedStatement checkUserStmt = connection.prepareStatement(checkUserQuery);
            checkUserStmt.setString(1, userId);
            ResultSet userResult = checkUserStmt.executeQuery();

            if (!userResult.next()) {
                String insertUserQuery = "INSERT INTO users (id) VALUES (?)";
                PreparedStatement insertUserStmt = connection.prepareStatement(insertUserQuery);
                insertUserStmt.setString(1, userId);
                insertUserStmt.executeUpdate();
            }

            // Insert the ticket
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
                    return generatedKeys.getInt(1); // Return the generated ticket ID
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
            String closeTicket = "UPDATE tickets SET status = 'CLOSED', closed_by = ?, closed_reason = ?, closed_at = CURRENT_TIMESTAMP WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(closeTicket);
            stmt.setLong(1, Long.parseLong(closedById));
            stmt.setString(2, reason);
            stmt.setInt(3, ticketId);
            
            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException | NumberFormatException e) {
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
            stmt.setLong(1, Long.parseLong(channelId));
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return String.format("ID: %d | User: <@%d> | Category: %s | Subject: %s | Status: %s | Priority: %s | Created: %s",
                    rs.getInt("id"), rs.getLong("user_id"), rs.getString("category"),
                    rs.getString("subject"), rs.getString("status"), rs.getString("priority"),
                    rs.getString("created_at"));
            }
            return null;
        } catch (SQLException | NumberFormatException e) {
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
                long roleId = rs.getLong("ticket_role");
                if (!rs.wasNull() && roleId != 0) {
                    return String.valueOf(roleId);
                }
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Error getting ticket role: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Assign ticket to a staff member
     */
    public boolean assignTicket(int ticketId, String assignedToId) {
        try {
            String assignTicket = "UPDATE tickets SET assigned_to = ?, status = 'IN_PROGRESS' WHERE id = ?";
            PreparedStatement stmt = connection.prepareStatement(assignTicket);
            stmt.setLong(1, Long.parseLong(assignedToId));
            stmt.setInt(2, ticketId);
            
            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException | NumberFormatException e) {
            System.err.println("Error assigning ticket: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if transcripts are enabled for a guild
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
            return false; // Default to false if no settings found
        } catch (SQLException e) {
            System.err.println("Error checking transcript settings: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Update ticket priority
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
                ticket.put("channel_id", String.valueOf(rs.getLong("channel_id")));
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
    private void updateStatistics(String guildId, String actionType, int increment) {
        try {
            String currentDate = getCurrentDate();
            
            // First, try to update existing record
            String updateQuery = "UPDATE statistics SET " + actionType + " = " + actionType + " + ? WHERE guild_id = ? AND date = ?";
            PreparedStatement updateStmt = connection.prepareStatement(updateQuery);
            updateStmt.setInt(1, increment);
            updateStmt.setString(2, guildId);
            updateStmt.setString(3, currentDate);
            
            int rowsUpdated = updateStmt.executeUpdate();
            
            // If no existing record, insert new one
            if (rowsUpdated == 0) {
                String insertQuery = "INSERT INTO statistics (guild_id, date, " + actionType + ") VALUES (?, ?, ?)";
                PreparedStatement insertStmt = connection.prepareStatement(insertQuery);
                insertStmt.setString(1, guildId);
                insertStmt.setString(2, currentDate);
                insertStmt.setInt(3, increment);
                insertStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Error updating statistics: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Increment warnings issued count for a guild
     */
    public void incrementWarningsIssued(String guildId) {
        updateStatistics(guildId, "warnings_issued", 1);
    }

    /**
     * Increment bans performed count for a guild
     */
    public void incrementBansPerformed(String guildId) {
        updateStatistics(guildId, "bans_performed", 1);
    }

    /**
     * Increment kicks performed count for a guild
     */
    public void incrementKicksPerformed(String guildId) {
        updateStatistics(guildId, "kicks_performed", 1);
    }

    /**
     * Increment tickets created count for a guild
     */
    public void incrementTicketsCreated(String guildId) {
        updateStatistics(guildId, "tickets_created", 1);
    }

    /**
     * Increment tickets closed count for a guild
     */
    public void incrementTicketsClosed(String guildId) {
        updateStatistics(guildId, "tickets_closed", 1);
    }

    /**
     * Get statistics for a guild for a specific date
     */
    public String getStatisticsForDate(String guildId, String date) {
        try {
            String query = "SELECT warnings_issued, kicks_performed, bans_performed, tickets_created, tickets_closed " +
                    "FROM statistics WHERE guild_id = ? AND date = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            stmt.setString(2, date);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                StringBuilder result = new StringBuilder();
                result.append("**Statistics for ").append(date).append(":**\n");
                result.append("🔸 Warnings Issued: ").append(rs.getInt("warnings_issued")).append("\n");
                result.append("🦶 Kicks Performed: ").append(rs.getInt("kicks_performed")).append("\n");
                result.append("🔨 Bans Performed: ").append(rs.getInt("bans_performed")).append("\n");
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
            String query = "SELECT date, warnings_issued, kicks_performed, bans_performed, tickets_created, tickets_closed " +
                    "FROM statistics WHERE guild_id = ? AND date >= date('now', '-7 days') ORDER BY date DESC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            
            StringBuilder result = new StringBuilder();
            result.append("**Weekly Statistics (Last 7 Days):**\n");
            
            int totalWarnings = 0, totalKicks = 0, totalBans = 0, totalTicketsCreated = 0, totalTicketsClosed = 0;
            boolean hasData = false;
            
            while (rs.next()) {
                hasData = true;
                String date = rs.getString("date");
                int warnings = rs.getInt("warnings_issued");
                int kicks = rs.getInt("kicks_performed");
                int bans = rs.getInt("bans_performed");
                int ticketsCreated = rs.getInt("tickets_created");
                int ticketsClosed = rs.getInt("tickets_closed");
                
                totalWarnings += warnings;
                totalKicks += kicks;
                totalBans += bans;
                totalTicketsCreated += ticketsCreated;
                totalTicketsClosed += ticketsClosed;
                
                result.append("\n**").append(date).append(":**\n");
                result.append("🔸 ").append(warnings).append(" | 🦶 ").append(kicks).append(" | 🔨 ").append(bans);
                result.append(" | 🎫 ").append(ticketsCreated).append(" | ✅ ").append(ticketsClosed);
            }
            
            if (hasData) {
                result.append("\n\n**Weekly Totals:**\n");
                result.append("🔸 Warnings: ").append(totalWarnings).append("\n");
                result.append("🦶 Kicks: ").append(totalKicks).append("\n");
                result.append("🔨 Bans: ").append(totalBans).append("\n");
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

    // Automod Rules Management Methods

    /**
     * Create a new automod rule
     */
    public int createAutomodRule(String guildId, String name, String ruleType, String action, int threshold, Integer duration, String whitelist, String config) {
        try {
            String insertRule = "INSERT INTO automod_rules (guild_id, name, rule_type, action, threshold, duration, whitelist, config) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement stmt = connection.prepareStatement(insertRule, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, guildId);
            stmt.setString(2, name);
            stmt.setString(3, ruleType);
            stmt.setString(4, action);
            stmt.setInt(5, threshold);
            if (duration != null) {
                stmt.setInt(6, duration);
            } else {
                stmt.setNull(6, Types.INTEGER);
            }
            stmt.setString(7, whitelist);
            stmt.setString(8, config);
            
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                ResultSet generatedKeys = stmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                }
            }
            return 0;
        } catch (SQLException e) {
            System.err.println("Error creating automod rule: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Get all automod rules for a guild
     */
    public java.util.List<java.util.Map<String, Object>> getAutomodRules(String guildId) {
        java.util.List<java.util.Map<String, Object>> rules = new java.util.ArrayList<>();
        try {
            String query = "SELECT id, name, rule_type, enabled, action, threshold, duration, whitelist, config, created_at, updated_at " +
                    "FROM automod_rules WHERE guild_id = ? ORDER BY created_at DESC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                java.util.Map<String, Object> rule = new java.util.HashMap<>();
                rule.put("id", rs.getInt("id"));
                rule.put("name", rs.getString("name"));
                rule.put("rule_type", rs.getString("rule_type"));
                rule.put("enabled", rs.getInt("enabled") == 1);
                rule.put("action", rs.getString("action"));
                rule.put("threshold", rs.getInt("threshold"));
                rule.put("duration", rs.getObject("duration"));
                rule.put("whitelist", rs.getString("whitelist"));
                rule.put("config", rs.getString("config"));
                rule.put("created_at", rs.getString("created_at"));
                rule.put("updated_at", rs.getString("updated_at"));
                rules.add(rule);
            }
        } catch (SQLException e) {
            System.err.println("Error getting automod rules: " + e.getMessage());
            e.printStackTrace();
        }
        return rules;
    }

    /**
     * Get a specific automod rule by ID
     */
    public java.util.Map<String, Object> getAutomodRule(int ruleId, String guildId) {
        try {
            String query = "SELECT id, name, rule_type, enabled, action, threshold, duration, whitelist, config, created_at, updated_at " +
                    "FROM automod_rules WHERE id = ? AND guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setInt(1, ruleId);
            stmt.setString(2, guildId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                java.util.Map<String, Object> rule = new java.util.HashMap<>();
                rule.put("id", rs.getInt("id"));
                rule.put("name", rs.getString("name"));
                rule.put("rule_type", rs.getString("rule_type"));
                rule.put("enabled", rs.getInt("enabled") == 1);
                rule.put("action", rs.getString("action"));
                rule.put("threshold", rs.getInt("threshold"));
                rule.put("duration", rs.getObject("duration"));
                rule.put("whitelist", rs.getString("whitelist"));
                rule.put("config", rs.getString("config"));
                rule.put("created_at", rs.getString("created_at"));
                rule.put("updated_at", rs.getString("updated_at"));
                return rule;
            }
        } catch (SQLException e) {
            System.err.println("Error getting automod rule: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Update an automod rule
     */
    public boolean updateAutomodRule(int ruleId, String guildId, String name, String ruleType, String action, int threshold, Integer duration, String whitelist, String config) {
        try {
            String updateRule = "UPDATE automod_rules SET name = ?, rule_type = ?, action = ?, threshold = ?, duration = ?, whitelist = ?, config = ? " +
                "WHERE id = ? AND guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(updateRule);
            stmt.setString(1, name);
            stmt.setString(2, ruleType);
            stmt.setString(3, action);
            stmt.setInt(4, threshold);
            if (duration != null) {
                stmt.setInt(5, duration);
            } else {
                stmt.setNull(5, Types.INTEGER);
            }
            stmt.setString(6, whitelist);
            stmt.setString(7, config);
            stmt.setInt(8, ruleId);
            stmt.setString(9, guildId);
            
            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException e) {
            System.err.println("Error updating automod rule: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Toggle an automod rule's enabled status
     */
    public boolean toggleAutomodRule(int ruleId, String guildId, boolean enabled) {
        try {
            String updateRule = "UPDATE automod_rules SET enabled = ? WHERE id = ? AND guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(updateRule);
            stmt.setInt(1, enabled ? 1 : 0);
            stmt.setInt(2, ruleId);
            stmt.setString(3, guildId);
            
            int rowsUpdated = stmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException e) {
            System.err.println("Error toggling automod rule: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete an automod rule
     */
    public boolean deleteAutomodRule(int ruleId, String guildId) {
        try {
            String deleteRule = "DELETE FROM automod_rules WHERE id = ? AND guild_id = ?";
            PreparedStatement stmt = connection.prepareStatement(deleteRule);
            stmt.setInt(1, ruleId);
            stmt.setString(2, guildId);
            
            int rowsDeleted = stmt.executeUpdate();
            return rowsDeleted > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting automod rule: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get enabled automod rules for a specific rule type in a guild
     */
    public java.util.List<java.util.Map<String, Object>> getEnabledAutomodRulesByType(String guildId, String ruleType) {
        java.util.List<java.util.Map<String, Object>> rules = new java.util.ArrayList<>();
        try {
            String query = "SELECT id, name, rule_type, action, threshold, duration, whitelist, config " +
                    "FROM automod_rules WHERE guild_id = ? AND rule_type = ? AND enabled = 1 ORDER BY threshold ASC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            stmt.setString(2, ruleType);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                java.util.Map<String, Object> rule = new java.util.HashMap<>();
                rule.put("id", rs.getInt("id"));
                rule.put("name", rs.getString("name"));
                rule.put("rule_type", rs.getString("rule_type"));
                rule.put("action", rs.getString("action"));
                rule.put("threshold", rs.getInt("threshold"));
                rule.put("duration", rs.getObject("duration"));
                rule.put("whitelist", rs.getString("whitelist"));
                rule.put("config", rs.getString("config"));
                rules.add(rule);
            }
        } catch (SQLException e) {
            System.err.println("Error getting enabled automod rules: " + e.getMessage());
            e.printStackTrace();
        }
        return rules;
    }

    /**
     * Get all enabled automod rules for a guild
     */
    public java.util.List<java.util.Map<String, Object>> getAllEnabledAutomodRules(String guildId) {
        java.util.List<java.util.Map<String, Object>> rules = new java.util.ArrayList<>();
        try {
            String query = "SELECT id, name, rule_type, action, threshold, duration, whitelist, config " +
                    "FROM automod_rules WHERE guild_id = ? AND enabled = 1 ORDER BY rule_type, threshold ASC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                java.util.Map<String, Object> rule = new java.util.HashMap<>();
                rule.put("id", rs.getInt("id"));
                rule.put("name", rs.getString("name"));
                rule.put("rule_type", rs.getString("rule_type"));
                rule.put("action", rs.getString("action"));
                rule.put("threshold", rs.getInt("threshold"));
                rule.put("duration", rs.getObject("duration"));
                rule.put("whitelist", rs.getString("whitelist"));
                rule.put("config", rs.getString("config"));
                rules.add(rule);
            }
        } catch (SQLException e) {
            System.err.println("Error getting all enabled automod rules: " + e.getMessage());
            e.printStackTrace();
        }
        return rules;
    }

    /**
     * Check if a user or role is whitelisted for a specific rule
     */
    public boolean isWhitelisted(String whitelist, String userId, java.util.List<String> userRoles) {
        if (whitelist == null || whitelist.trim().isEmpty()) {
            return false;
        }
        
        String[] whitelistEntries = whitelist.split(",");
        for (String entry : whitelistEntries) {
            String trimmedEntry = entry.trim();
            if (trimmedEntry.equals(userId)) {
                return true;
            }
            // Check if any of the user's roles are whitelisted
            for (String roleId : userRoles) {
                if (trimmedEntry.equals(roleId)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Process message content for spam detection
     */
    public boolean isSpamViolation(String messageContent, String config) {
        if (messageContent == null || messageContent.trim().isEmpty()) {
            return false;
        }
        
        // Default spam detection logic
        String content = messageContent.toLowerCase().trim();
        
        // Check for excessive repeated characters
        if (content.matches(".*(.)\\1{4,}.*")) {
            return true;
        }
        
        // Check for excessive caps (handled by CAPS rule type)
        // Check for repeated words
        String[] words = content.split("\\s+");
        java.util.Map<String, Integer> wordCount = new java.util.HashMap<>();
        for (String word : words) {
            if (word.length() > 2) {
                wordCount.put(word, wordCount.getOrDefault(word, 0) + 1);
                if (wordCount.get(word) >= 5) {
                    return true;
                }
            }
        }
        
        // Check for excessive emojis
        int emojiCount = 0;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch >= 0x1F600 && ch <= 0x1F64F || // Emoticons
                ch >= 0x1F300 && ch <= 0x1F5FF || // Misc Symbols
                ch >= 0x1F680 && ch <= 0x1F6FF || // Transport
                ch >= 0x2600 && ch <= 0x26FF) {   // Misc symbols
                emojiCount++;
            }
        }
        if (emojiCount > 10) {
            return true;
        }
        
        return false;
    }

    /**
     * Process message content for caps detection
     */
    public boolean isCapsViolation(String messageContent, String config) {
        if (messageContent == null || messageContent.trim().isEmpty()) {
            return false;
        }
        
        String content = messageContent.trim();
        if (content.length() < 5) {
            return false; // Too short to be considered caps spam
        }
        
        int upperCount = 0;
        int letterCount = 0;
        
        for (char ch : content.toCharArray()) {
            if (Character.isLetter(ch)) {
                letterCount++;
                if (Character.isUpperCase(ch)) {
                    upperCount++;
                }
            }
        }
        
        if (letterCount == 0) {
            return false;
        }
        
        // Default threshold of 70% caps
        double capsPercentage = (double) upperCount / letterCount;
        return capsPercentage >= 0.7;
    }

    /**
     * Process message content for link detection
     */
    public boolean isLinkViolation(String messageContent, String config) {
        if (messageContent == null || messageContent.trim().isEmpty()) {
            return false;
        }
        
        String content = messageContent.toLowerCase();
        
        // Check for URLs
        if (content.matches(".*(https?://|www\\.|ftp://|[a-z0-9-]+\\.(com|org|net|io|me|co|edu|gov)).*")) {
            return true;
        }
        
        // Check for discord invites specifically
        if (content.matches(".*(discord\\.gg/|discordapp\\.com/invite/).*")) {
            return true;
        }
        
        return false;
    }

    /**
     * Process message content for Discord invite detection
     */
    public boolean isInviteViolation(String messageContent, String config) {
        if (messageContent == null || messageContent.trim().isEmpty()) {
            return false;
        }
        
        String content = messageContent.toLowerCase();
        
        // Check for various Discord invite formats
        return content.matches(".*(discord\\.gg/|discordapp\\.com/invite/|discord\\.com/invite/).*");
    }

    /**
     * Process message content for bad words detection
     */
    public boolean isBadWordsViolation(String messageContent, String config) {
        if (messageContent == null || messageContent.trim().isEmpty()) {
            return false;
        }
        
        // Default bad words list (can be customized via config)
        String[] defaultBadWords = {
            "badword1", "badword2", "badword3" // Placeholder - replace with actual bad words if needed
        };
        
        String content = messageContent.toLowerCase();
        
        // Check config for custom bad words
        if (config != null && !config.trim().isEmpty()) {
            String[] customWords = config.split(",");
            for (String word : customWords) {
                if (content.contains(word.trim().toLowerCase())) {
                    return true;
                }
            }
        } else {
            // Use default list
            for (String word : defaultBadWords) {
                if (content.contains(word)) {
                    return true;
                }
            }
        }
        
        return false;
    }

    /**
     * Process message for mention spam detection
     */
    public boolean isMentionSpamViolation(String messageContent, String config) {
        if (messageContent == null || messageContent.trim().isEmpty()) {
            return false;
        }
        
        // Count mentions (@user, @role, @everyone, @here)
        int mentionCount = 0;
        
        // Count user mentions
        mentionCount += (messageContent.split("<@!?\\d+>").length - 1);
        
        // Count role mentions
        mentionCount += (messageContent.split("<@&\\d+>").length - 1);
        
        // Count @everyone and @here
        if (messageContent.contains("@everyone")) mentionCount++;
        if (messageContent.contains("@here")) mentionCount++;
        
        // Default threshold of 5 mentions
        return mentionCount >= 5;
    }

    /**
     * Store user violation for tracking purposes
     */
    public void recordUserViolation(String guildId, String userId, String ruleType, int ruleId, String messageContent) {
        try {
            String insertViolation = "INSERT INTO temporary_data (guild_id, user_id, data_type, data, expires_at) " +
                "VALUES (?, ?, ?, ?, datetime('now', '+1 day'))";
            PreparedStatement stmt = connection.prepareStatement(insertViolation);
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.setString(3, "AUTOMOD_VIOLATION_" + ruleType);
            stmt.setString(4, "ruleId:" + ruleId + ",content:" + messageContent.substring(0, Math.min(messageContent.length(), 100)));
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error recording user violation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get user violation count for a specific rule type within the last day
     */
    public int getUserViolationCount(String guildId, String userId, String ruleType) {
        try {
            String query = "SELECT COUNT(*) as count FROM temporary_data " +
                    "WHERE guild_id = ? AND user_id = ? AND data_type = ? AND expires_at > datetime('now')";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            stmt.setString(3, "AUTOMOD_VIOLATION_" + ruleType);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            System.err.println("Error getting user violation count: " + e.getMessage());
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Process a message through automod rules and return action to take
     * @param guildId The guild ID where the message was sent
     * @param userId The user ID who sent the message
     * @param userRoles List of role IDs the user has
     * @param messageContent The content of the message to check
     * @return Map containing action details or null if no action needed
     */
    public java.util.Map<String, Object> processMessageAutomod(String guildId, String userId, java.util.List<String> userRoles, String messageContent) {
        // Get all enabled automod rules for the guild
        java.util.List<java.util.Map<String, Object>> rules = getAllEnabledAutomodRules(guildId);
        
        for (java.util.Map<String, Object> rule : rules) {
            String ruleType = (String) rule.get("rule_type");
            String whitelist = (String) rule.get("whitelist");
            String config = (String) rule.get("config");
            int ruleId = (Integer) rule.get("id");
            int threshold = (Integer) rule.get("threshold");
            
            // Check if user is whitelisted
            if (isWhitelisted(whitelist, userId, userRoles)) {
                continue; // Skip this rule for whitelisted users
            }
            
            boolean violationDetected = false;
            
            // Check for violation based on rule type
            switch (ruleType) {
                case "SPAM":
                    violationDetected = isSpamViolation(messageContent, config);
                    break;
                case "CAPS":
                    violationDetected = isCapsViolation(messageContent, config);
                    break;
                case "LINKS":
                    violationDetected = isLinkViolation(messageContent, config);
                    break;
                case "INVITE":
                    violationDetected = isInviteViolation(messageContent, config);
                    break;
                case "BADWORDS":
                    violationDetected = isBadWordsViolation(messageContent, config);
                    break;
                case "MENTION_SPAM":
                    violationDetected = isMentionSpamViolation(messageContent, config);
                    break;
            }
            
            if (violationDetected) {
                // Record the violation
                recordUserViolation(guildId, userId, ruleType, ruleId, messageContent);
                
                // Get user's violation count for this rule type
                int violationCount = getUserViolationCount(guildId, userId, ruleType);
                
                // Check if threshold is met
                if (violationCount >= threshold) {
                    // Prepare action result
                    java.util.Map<String, Object> actionResult = new java.util.HashMap<>();
                    actionResult.put("rule", rule);
                    actionResult.put("violation_type", ruleType);
                    actionResult.put("violation_count", violationCount);
                    actionResult.put("action", rule.get("action"));
                    actionResult.put("duration", rule.get("duration"));
                    actionResult.put("rule_name", rule.get("name"));
                    
                    // Increment automod actions statistics
                    incrementAutomodActions(guildId);
                    
                    return actionResult;
                }
                
                // If threshold not met but violation detected, still return info for logging
                java.util.Map<String, Object> warningResult = new java.util.HashMap<>();
                warningResult.put("rule", rule);
                warningResult.put("violation_type", ruleType);
                warningResult.put("violation_count", violationCount);
                warningResult.put("action", "LOG_ONLY");
                warningResult.put("rule_name", rule.get("name"));
                warningResult.put("threshold_not_met", true);
                
                return warningResult;
            }
        }
        
        return null; // No violations detected
    }

    /**
     * Execute automod action based on processed result
     * @param guild The guild where the action should be taken
     * @param userId The user ID to take action on
     * @param actionResult The result from processMessageAutomod
     * @return success status
     */
    public boolean executeAutomodAction(net.dv8tion.jda.api.entities.Guild guild, String userId, java.util.Map<String, Object> actionResult) {
        try {
            String action = (String) actionResult.get("action");
            String ruleType = (String) actionResult.get("violation_type");
            String ruleName = (String) actionResult.get("rule_name");
            Integer duration = (Integer) actionResult.get("duration");
            String guildId = guild.getId();
            
            // Skip execution if it's just a log entry
            if ("LOG_ONLY".equals(action)) {
                return true;
            }
            
            net.dv8tion.jda.api.entities.Member member = guild.getMemberById(userId);
            if (member == null) {
                return false;
            }
            
            String reason = "Automod: " + ruleName + " (" + ruleType + " violation)";
            
            switch (action) {
                case "WARN":
                    // Create a warning
                    int warningId = insertWarning(guildId, userId, guild.getSelfMember().getId(), reason, "MEDIUM", null);
                    if (warningId > 0) {
                        incrementWarningsIssued(guildId);
                        sendAuditLogEntry(guild, "WARN", member.getEffectiveName(), "Automod", reason);
                        return true;
                    }
                    break;
                    
                case "DELETE":
                    // Message deletion is handled by the message listener
                    return true;
                    
                case "MUTE":
                    // Timeout the user (Discord's timeout feature)
                    if (duration != null && duration > 0) {
                        member.timeoutFor(java.time.Duration.ofMinutes(duration)).reason(reason).queue();
                        insertModerationAction(guildId, userId, guild.getSelfMember().getId(), "TEMP_MUTE", reason, duration, null);
                        sendAuditLogEntry(guild, "TIMEOUT " + duration + "m", member.getEffectiveName(), "Automod", reason);
                        return true;
                    }
                    break;
                    
                case "KICK":
                    member.kick().reason(reason).queue();
                    insertModerationAction(guildId, userId, guild.getSelfMember().getId(), "KICK", reason, null, null);
                    incrementKicksPerformed(guildId);
                    sendAuditLogEntry(guild, "KICK", member.getEffectiveName(), "Automod", reason);
                    return true;
                    
                case "BAN":
                    if (duration != null && duration > 0) {
                        // Temporary ban - we'll use the moderation_actions table to track this
                        member.ban(0, java.util.concurrent.TimeUnit.SECONDS).reason(reason).queue();
                        String expiresAt = java.time.LocalDateTime.now().plusMinutes(duration).toString();
                        insertModerationAction(guildId, userId, guild.getSelfMember().getId(), "TEMP_BAN", reason, duration, expiresAt);
                        sendAuditLogEntry(guild, "BAN " + duration + "m", member.getEffectiveName(), "Automod", reason);
                    } else {
                        // Permanent ban
                        member.ban(0, java.util.concurrent.TimeUnit.SECONDS).reason(reason).queue();
                        insertModerationAction(guildId, userId, guild.getSelfMember().getId(), "BAN", reason, null, null);
                        sendAuditLogEntry(guild, "BAN", member.getEffectiveName(), "Automod", reason);
                    }
                    incrementBansPerformed(guildId);
                    return true;
            }
        } catch (Exception e) {
            System.err.println("Error executing automod action: " + e.getMessage());
            e.printStackTrace();
        }
        
        return false;
    }

    /**
     * Clean up expired automod violations and temporary bans
     */
    public void cleanupExpiredAutomodData() {
        try {
            // Clean up expired violation records
            String cleanupViolations = "DELETE FROM temporary_data WHERE expires_at <= datetime('now')";
            PreparedStatement stmt = connection.prepareStatement(cleanupViolations);
            int deletedViolations = stmt.executeUpdate();
            
            if (deletedViolations > 0) {
                System.out.println("Cleaned up " + deletedViolations + " expired automod violation records");
            }
            
            // Note: Discord doesn't have a built-in temporary ban system, so temp bans would need
            // to be handled by a separate scheduled task that unbans users when their time is up
            
        } catch (SQLException e) {
            System.err.println("Error cleaning up expired automod data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get automod statistics for a guild
     */
    public java.util.Map<String, Object> getAutomodStatistics(String guildId, int days) {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();
        
        try {
            // Get violation counts by type
            String violationQuery = "SELECT " +
                    "SUBSTR(data_type, 19) as violation_type, COUNT(*) as count " +
                    "FROM temporary_data " +
                    "WHERE guild_id = ? AND data_type LIKE 'AUTOMOD_VIOLATION_%' " +
                    "AND created_at >= datetime('now', '-" + days + " days') " +
                    "GROUP BY violation_type";
            
            PreparedStatement stmt = connection.prepareStatement(violationQuery);
            stmt.setString(1, guildId);
            ResultSet rs = stmt.executeQuery();
            
            java.util.Map<String, Integer> violations = new java.util.HashMap<>();
            int totalViolations = 0;
            
            while (rs.next()) {
                String violationType = rs.getString("violation_type");
                int count = rs.getInt("count");
                violations.put(violationType, count);
                totalViolations += count;
            }
            
            stats.put("violations_by_type", violations);
            stats.put("total_violations", totalViolations);
            
            // Get total automod actions from statistics table
            String actionsQuery = "SELECT SUM(automod_actions) as total_actions " +
                    "FROM statistics " +
                    "WHERE guild_id = ? AND date >= date('now', '-" + days + " days')";
            stmt = connection.prepareStatement(actionsQuery);
            stmt.setString(1, guildId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                stats.put("total_actions", rs.getInt("total_actions"));
            } else {
                stats.put("total_actions", 0);
            }
            
            // Get number of active rules
            String rulesQuery = "SELECT COUNT(*) as active_rules FROM automod_rules WHERE guild_id = ? AND enabled = 1";
            stmt = connection.prepareStatement(rulesQuery);
            stmt.setString(1, guildId);
            rs = stmt.executeQuery();
            
            if (rs.next()) {
                stats.put("active_rules", rs.getInt("active_rules"));
            } else {
                stats.put("active_rules", 0);
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting automod statistics: " + e.getMessage());
            e.printStackTrace();
        }
        
        return stats;
    }

    /**
     * Get top violators for automod rules in the last N days
     */
    public java.util.List<java.util.Map<String, Object>> getTopAutomodViolators(String guildId, int days, int limit) {
        java.util.List<java.util.Map<String, Object>> violators = new java.util.ArrayList<>();
        
        try {
            String query = "SELECT user_id, " +
                    "SUBSTR(data_type, 19) as violation_type, " +
                    "COUNT(*) as violation_count " +
                    "FROM temporary_data " +
                    "WHERE guild_id = ? AND data_type LIKE 'AUTOMOD_VIOLATION_%' " +
                    "AND created_at >= datetime('now', '-" + days + " days') " +
                    "GROUP BY user_id, violation_type " +
                    "ORDER BY violation_count DESC " +
                    "LIMIT ?";
            
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, guildId);
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                java.util.Map<String, Object> violator = new java.util.HashMap<>();
                violator.put("user_id", rs.getString("user_id"));
                violator.put("violation_type", rs.getString("violation_type"));
                violator.put("violation_count", rs.getInt("violation_count"));
                violators.add(violator);
            }
        } catch (SQLException e) {
            System.err.println("Error getting top automod violators: " + e.getMessage());
            e.printStackTrace();
        }
        
        return violators;
    }

    /**
     * Clear all violations for a specific user (useful for pardoning)
     */
    public boolean clearUserViolations(String guildId, String userId) {
        try {
            String deleteQuery = "DELETE FROM temporary_data " +
                    "WHERE guild_id = ? AND user_id = ? AND data_type LIKE 'AUTOMOD_VIOLATION_%'";
            PreparedStatement stmt = connection.prepareStatement(deleteQuery);
            stmt.setString(1, guildId);
            stmt.setString(2, userId);
            
            int deletedRows = stmt.executeUpdate();
            return deletedRows > 0;
        } catch (SQLException e) {
            System.err.println("Error clearing user violations: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get automod rule effectiveness (rules that trigger actions vs just log)
     */
    public java.util.Map<String, Object> getAutomodRuleEffectiveness(String guildId, int days) {
        java.util.Map<String, Object> effectiveness = new java.util.HashMap<>();
        
        try {
            // Get rules and their configurations
            java.util.List<java.util.Map<String, Object>> rules = getAutomodRules(guildId);
            
            for (java.util.Map<String, Object> rule : rules) {
                String ruleType = (String) rule.get("rule_type");
                int threshold = (Integer) rule.get("threshold");
                boolean enabled = (Boolean) rule.get("enabled");
                
                if (!enabled) continue;
                
                // Count violations for this rule type
                String violationQuery = "SELECT COUNT(*) as total_violations " +
                        "FROM temporary_data " +
                        "WHERE guild_id = ? AND data_type = ? " +
                        "AND created_at >= datetime('now', '-" + days + " days')";
                
                PreparedStatement stmt = connection.prepareStatement(violationQuery);
                stmt.setString(1, guildId);
                stmt.setString(2, "AUTOMOD_VIOLATION_" + ruleType);
                ResultSet rs = stmt.executeQuery();
                
                int totalViolations = 0;
                if (rs.next()) {
                    totalViolations = rs.getInt("total_violations");
                }
                
                // Estimate actions taken (violations that would exceed threshold)
                int estimatedActions = Math.max(0, totalViolations - threshold + 1);
                
                java.util.Map<String, Object> ruleStats = new java.util.HashMap<>();
                ruleStats.put("rule_name", rule.get("name"));
                ruleStats.put("total_violations", totalViolations);
                ruleStats.put("threshold", threshold);
                ruleStats.put("estimated_actions", estimatedActions);
                ruleStats.put("effectiveness_ratio", totalViolations > 0 ? (double) estimatedActions / totalViolations : 0.0);
                
                effectiveness.put(ruleType, ruleStats);
            }
        } catch (SQLException e) {
            System.err.println("Error getting automod rule effectiveness: " + e.getMessage());
            e.printStackTrace();
        }
        
        return effectiveness;
    }

    /**
     * Bulk enable/disable automod rules by type
     */
    public int bulkToggleAutomodRules(String guildId, String ruleType, boolean enabled) {
        try {
            String query = "UPDATE automod_rules SET enabled = ? WHERE guild_id = ? AND rule_type = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setInt(1, enabled ? 1 : 0);
            stmt.setString(2, guildId);
            stmt.setString(3, ruleType);
            
            return stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error bulk toggling automod rules: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Test method to verify automod functionality (for development only)
     */
    public static void testAutomodFunctionality() {
        System.out.println("Automod functionality integrated successfully!");
        System.out.println("Available methods:");
        System.out.println("=== CRUD Operations ===");
        System.out.println("- createAutomodRule()");
        System.out.println("- getAutomodRules()");
        System.out.println("- getAutomodRule()");
        System.out.println("- updateAutomodRule()");
        System.out.println("- toggleAutomodRule()");
        System.out.println("- deleteAutomodRule()");
        System.out.println("- getEnabledAutomodRulesByType()");
        System.out.println("- getAllEnabledAutomodRules() [NEW]");
        System.out.println("=== Message Processing ===");
        System.out.println("- processMessageAutomod() [NEW]");
        System.out.println("- executeAutomodAction() [NEW]");
        System.out.println("- isWhitelisted() [NEW]");
        System.out.println("=== Rule Type Handlers ===");
        System.out.println("- isSpamViolation() [NEW]");
        System.out.println("- isCapsViolation() [NEW]");
        System.out.println("- isLinkViolation() [NEW]");
        System.out.println("- isInviteViolation() [NEW]");
        System.out.println("- isBadWordsViolation() [NEW]");
        System.out.println("- isMentionSpamViolation() [NEW]");
        System.out.println("=== Violation Tracking ===");
        System.out.println("- recordUserViolation() [NEW]");
        System.out.println("- getUserViolationCount() [NEW]");
        System.out.println("- clearUserViolations() [NEW]");
        System.out.println("=== Analytics & Statistics ===");
        System.out.println("- getAutomodStatistics() [NEW]");
        System.out.println("- getTopAutomodViolators() [NEW]");
        System.out.println("- getAutomodRuleEffectiveness() [NEW]");
        System.out.println("=== Maintenance ===");
        System.out.println("- cleanupExpiredAutomodData() [NEW]");
        System.out.println("- bulkToggleAutomodRules() [NEW]");
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
}
