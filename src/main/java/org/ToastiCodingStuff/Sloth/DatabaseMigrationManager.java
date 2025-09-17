package org.ToastiCodingStuff.Sloth;

import java.sql.*;
import java.util.*;

/**
 * Comprehensive database migration system for automatic column management.
 * This class handles schema evolution by detecting missing columns and automatically
 * adding them to maintain database consistency across versions.
 * 
 * <h2>Key Features:</h2>
 * <ul>
 *   <li><strong>Automatic Column Detection:</strong> Compares expected schemas with actual database structure</li>
 *   <li><strong>Safe Migrations:</strong> Only adds missing columns, never removes or modifies existing data</li>
 *   <li><strong>SQLite Compatibility:</strong> Handles SQLite-specific constraints like CURRENT_TIMESTAMP defaults</li>
 *   <li><strong>Migration Tracking:</strong> Records all migrations with timestamps and execution times</li>
 *   <li><strong>Schema Validation:</strong> Verifies database structure matches expected definitions</li>
 *   <li><strong>Index Management:</strong> Applies missing indexes</li>
 * </ul>
 * 
 * <h2>Usage Example:</h2>
 * <pre>
 * // Basic usage - automatically run on DatabaseHandler initialization
 * DatabaseHandler handler = new DatabaseHandler();
 * 
 * // Manual migration check
 * handler.runMigrationCheck();
 * 
 * // Get migration history
 * List&lt;Map&lt;String, Object&gt;&gt; history = handler.getMigrationHistory();
 * 
 * // Validate current schema
 * boolean isValid = handler.validateDatabaseSchema();
 * </pre>
 * 
 * <h2>How It Works:</h2>
 * <ol>
 *   <li>Define expected table schemas in {@link #getExpectedSchemas()}</li>
 *   <li>Compare expected columns with actual database columns</li>
 *   <li>Automatically add any missing columns with proper defaults</li>
 *   <li>Apply missing indexes</li>
 *   <li>Record migration in tracking table for audit purposes</li>
 * </ol>
 * 
 * <h2>Adding New Features:</h2>
 * To add new columns for a feature:
 * <ol>
 *   <li>Update the table schema definition in the appropriate create*Schema() method</li>
 *   <li>Restart the application - migration runs automatically</li>
 *   <li>New columns are added with proper defaults, existing data is preserved</li>
 * </ol>
 * 
 * @author Sloth Bot Development Team
 * @version 1.0
 * @since 2025-09-13
 */
public class DatabaseMigrationManager {
    
    private final Connection connection;
    
    /**
     * Represents a column definition with its SQL properties
     */
    public static class ColumnDefinition {
        public final String name;
        public final String sqlDefinition;
        public final boolean nullable;
        public final String defaultValue;
        
        public ColumnDefinition(String name, String sqlDefinition, boolean nullable, String defaultValue) {
            this.name = name;
            this.sqlDefinition = sqlDefinition;
            this.nullable = nullable;
            this.defaultValue = defaultValue;
        }
        
        public ColumnDefinition(String name, String sqlDefinition) {
            this(name, sqlDefinition, true, null);
        }
    }
    
    /**
     * Represents a complete table schema with all its columns
     */
    public static class TableSchema {
        public final String tableName;
        public final Map<String, ColumnDefinition> columns;
        public final List<String> indexes;
        
        public TableSchema(String tableName) {
            this.tableName = tableName;
            this.columns = new LinkedHashMap<>();
            this.indexes = new ArrayList<>();
        }
        
        public TableSchema addColumn(String name, String sqlDefinition) {
            columns.put(name, new ColumnDefinition(name, sqlDefinition));
            return this;
        }
        
        public TableSchema addColumn(String name, String sqlDefinition, boolean nullable, String defaultValue) {
            columns.put(name, new ColumnDefinition(name, sqlDefinition, nullable, defaultValue));
            return this;
        }
        
        public TableSchema addIndex(String indexDefinition) {
            indexes.add(indexDefinition);
            return this;
        }
    }
    
    public DatabaseMigrationManager(Connection connection) {
        this.connection = connection;
    }
    
    /**
     * Get all expected table schemas for the application.
     * This is the single source of truth for what the database structure should look like.
     */
    public Map<String, TableSchema> getExpectedSchemas() {
        Map<String, TableSchema> schemas = new HashMap<>();
        
        // Define all table schemas
        schemas.put("guilds", createGuildsSchema());
        schemas.put("users", createUsersSchema());
        schemas.put("warnings", createWarningsSchema());
        schemas.put("moderation_actions", createModerationActionsSchema());
        schemas.put("tickets", createTicketsSchema());
        schemas.put("ticket_messages", createTicketMessagesSchema());
        schemas.put("guild_settings", createGuildSettingsSchema());
        schemas.put("role_permissions", createRolePermissionsSchema());
        schemas.put("bot_logs", createBotLogsSchema());
        schemas.put("statistics", createStatisticsSchema());
        schemas.put("user_statistics", createUserStatisticsSchema());
        schemas.put("temporary_data", createTemporaryDataSchema());
        schemas.put("guild_systems", createGuildSystemsSchema());
        schemas.put("rules_embeds_channel", createRulesEmbedsChannelSchema());
        schemas.put("log_channels", createLogChannelsSchema());
        schemas.put("warn_system_settings", createWarnSystemSettingsSchema());
        schemas.put("database_migrations", createDatabaseMigrationsSchema());
        
        return schemas;
    }
    
    /**
     * Define the guilds table schema
     */
    private TableSchema createGuildsSchema() {
        return new TableSchema("guilds")
            .addColumn("id", "INTEGER PRIMARY KEY")
            .addColumn("name", "TEXT NOT NULL")
            .addColumn("prefix", "TEXT DEFAULT '!'")
            .addColumn("language", "TEXT DEFAULT 'de'")
            .addColumn("created_at", "TEXT")
            .addColumn("updated_at", "TEXT")
            .addColumn("active", "INTEGER DEFAULT 1");
    }
    
    /**
     * Define the users table schema
     */
    private TableSchema createUsersSchema() {
        return new TableSchema("users")
            .addColumn("id", "INTEGER PRIMARY KEY")
            .addColumn("username", "TEXT NOT NULL")
            .addColumn("discriminator", "TEXT")
            .addColumn("avatar", "TEXT")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addColumn("updated_at", "TEXT DEFAULT CURRENT_TIMESTAMP");
    }
    
    /**
     * Define the warnings table schema
     */
    private TableSchema createWarningsSchema() {
        return new TableSchema("warnings")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("guild_id", "INTEGER NOT NULL")
            .addColumn("user_id", "INTEGER NOT NULL")
            .addColumn("moderator_id", "INTEGER NOT NULL")
            .addColumn("reason", "TEXT NOT NULL")
            .addColumn("severity", "TEXT DEFAULT 'MEDIUM' CHECK(severity IN ('LOW', 'MEDIUM', 'HIGH', 'SEVERE'))")
            .addColumn("active", "INTEGER DEFAULT 1")
            .addColumn("expires_at", "TEXT")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_warnings_guild_user ON warnings(guild_id, user_id)")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_warnings_active ON warnings(active)")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_warnings_expires ON warnings(expires_at)");
    }
    
    /**
     * Define the moderation_actions table schema
     */
    private TableSchema createModerationActionsSchema() {
        return new TableSchema("moderation_actions")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("guild_id", "INTEGER NOT NULL")
            .addColumn("user_id", "INTEGER NOT NULL")
            .addColumn("moderator_id", "INTEGER NOT NULL")
            .addColumn("action_type", "TEXT NOT NULL CHECK(action_type IN ('KICK', 'BAN', 'TEMP_BAN', 'UNBAN', 'WARN', 'TIMEOUT', 'UNTIMEOUT'))")
            .addColumn("reason", "TEXT NOT NULL")
            .addColumn("duration", "INTEGER")
            .addColumn("expires_at", "TEXT")
            .addColumn("active", "INTEGER DEFAULT 1")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_moderation_guild_user ON moderation_actions(guild_id, user_id)")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_moderation_action_type ON moderation_actions(action_type)")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_moderation_active_expires ON moderation_actions(active, expires_at)");
    }
    
    /**
     * Define the tickets table schema
     */
    private TableSchema createTicketsSchema() {
        return new TableSchema("tickets")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("guild_id", "INTEGER NOT NULL")
            .addColumn("user_id", "INTEGER NOT NULL")
            .addColumn("channel_id", "INTEGER UNIQUE")
            .addColumn("category", "TEXT DEFAULT 'general'")
            .addColumn("subject", "TEXT")
            .addColumn("status", "TEXT DEFAULT 'OPEN' CHECK(status IN ('OPEN', 'IN_PROGRESS', 'WAITING', 'CLOSED'))")
            .addColumn("priority", "TEXT DEFAULT 'MEDIUM' CHECK(priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT'))")
            .addColumn("assigned_to", "INTEGER")
            .addColumn("closed_by", "INTEGER")
            .addColumn("closed_reason", "TEXT")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addColumn("updated_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addColumn("closed_at", "TEXT")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_tickets_guild_status ON tickets(guild_id, status)")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_tickets_user ON tickets(user_id)");
    }
    
    /**
     * Define the ticket_messages table schema
     */
    private TableSchema createTicketMessagesSchema() {
        return new TableSchema("ticket_messages")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("ticket_id", "INTEGER NOT NULL")
            .addColumn("user_id", "INTEGER NOT NULL")
            .addColumn("message_id", "INTEGER NOT NULL")
            .addColumn("content", "TEXT NOT NULL")
            .addColumn("attachments", "TEXT")
            .addColumn("is_staff", "INTEGER DEFAULT 0")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_ticket_messages_ticket_created ON ticket_messages(ticket_id, created_at)");
    }
    
    /**
     * Define the guild_settings table schema
     */
    private TableSchema createGuildSettingsSchema() {
        return new TableSchema("guild_settings")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("guild_id", "INTEGER NOT NULL UNIQUE")
            .addColumn("modlog_channel", "INTEGER")
            .addColumn("warn_threshold_kick", "INTEGER DEFAULT 5")
            .addColumn("warn_threshold_ban", "INTEGER DEFAULT 8")
            .addColumn("warn_expire_days", "INTEGER DEFAULT 30")
            .addColumn("ticket_category", "INTEGER")
            .addColumn("ticket_channel", "INTEGER")
            .addColumn("ticket_role", "INTEGER")
            .addColumn("ticket_transcript", "INTEGER DEFAULT 1")
            .addColumn("join_role", "INTEGER")
            .addColumn("mute_role", "INTEGER")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addColumn("updated_at", "TEXT DEFAULT CURRENT_TIMESTAMP");
    }
    
    /**
     * Define the role_permissions table schema
     */
    private TableSchema createRolePermissionsSchema() {
        return new TableSchema("role_permissions")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("guild_id", "INTEGER NOT NULL")
            .addColumn("role_id", "INTEGER NOT NULL")
            .addColumn("permission", "TEXT NOT NULL")
            .addColumn("allowed", "INTEGER DEFAULT 1")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_role_permissions_guild_role ON role_permissions(guild_id, role_id)");
    }
    
    /**
     * Define the bot_logs table schema
     */
    private TableSchema createBotLogsSchema() {
        return new TableSchema("bot_logs")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("guild_id", "INTEGER")
            .addColumn("user_id", "INTEGER")
            .addColumn("event_type", "TEXT NOT NULL")
            .addColumn("message", "TEXT NOT NULL")
            .addColumn("data", "TEXT")
            .addColumn("level", "TEXT DEFAULT 'INFO' CHECK(level IN ('DEBUG', 'INFO', 'WARN', 'ERROR'))")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_bot_logs_guild_created ON bot_logs(guild_id, created_at)")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_bot_logs_level_created ON bot_logs(level, created_at)");
    }
    
    /**
     * Define the statistics table schema
     */
    private TableSchema createStatisticsSchema() {
        return new TableSchema("statistics")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("guild_id", "INTEGER NOT NULL")
            .addColumn("date", "TEXT NOT NULL")
            .addColumn("warnings_issued", "INTEGER DEFAULT 0")
            .addColumn("kicks_performed", "INTEGER DEFAULT 0")
            .addColumn("bans_performed", "INTEGER DEFAULT 0")
            .addColumn("timeouts_performed", "INTEGER DEFAULT 0")
            .addColumn("untimeouts_performed", "INTEGER DEFAULT 0")
            .addColumn("tickets_created", "INTEGER DEFAULT 0")
            .addColumn("tickets_closed", "INTEGER DEFAULT 0")
            .addColumn("verifications_performed", "INTEGER DEFAULT 0")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP");
    }
    
    /**
     * Define the user_statistics table schema
     */
    private TableSchema createUserStatisticsSchema() {
        return new TableSchema("user_statistics")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("guild_id", "INTEGER NOT NULL")
            .addColumn("user_id", "INTEGER NOT NULL")
            .addColumn("date", "TEXT NOT NULL")
            .addColumn("warnings_received", "INTEGER DEFAULT 0")
            .addColumn("warnings_issued", "INTEGER DEFAULT 0")
            .addColumn("kicks_received", "INTEGER DEFAULT 0")
            .addColumn("kicks_performed", "INTEGER DEFAULT 0")
            .addColumn("bans_received", "INTEGER DEFAULT 0")
            .addColumn("bans_performed", "INTEGER DEFAULT 0")
            .addColumn("timeouts_received", "INTEGER DEFAULT 0")
            .addColumn("timeouts_performed", "INTEGER DEFAULT 0")
            .addColumn("untimeouts_received", "INTEGER DEFAULT 0")
            .addColumn("untimeouts_performed", "INTEGER DEFAULT 0")
            .addColumn("tickets_created", "INTEGER DEFAULT 0")
            .addColumn("tickets_closed", "INTEGER DEFAULT 0")
            .addColumn("verifications_performed", "INTEGER DEFAULT 0")
            .addColumn("messages_sent", "INTEGER DEFAULT 0")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_user_statistics_guild_user_date ON user_statistics(guild_id, user_id, date)")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_user_statistics_date ON user_statistics(date)");
    }
    
    /**
     * Define the temporary_data table schema
     */
    private TableSchema createTemporaryDataSchema() {
        return new TableSchema("temporary_data")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("guild_id", "INTEGER NOT NULL")
            .addColumn("user_id", "INTEGER NOT NULL")
            .addColumn("data_type", "TEXT NOT NULL")
            .addColumn("data", "TEXT NOT NULL")
            .addColumn("expires_at", "TEXT NOT NULL")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_temporary_data_expires ON temporary_data(expires_at)")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_temporary_data_guild_user_type ON temporary_data(guild_id, user_id, data_type)");
    }
    
    /**
     * Define the guild_systems table schema
     */
    private TableSchema createGuildSystemsSchema() {
        return new TableSchema("guild_systems")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("guild_id", "INTEGER NOT NULL")
            .addColumn("system_type", "TEXT NOT NULL CHECK(system_type IN ('log-channel', 'warn-system', 'ticket-system', 'moderation-system'))")
            .addColumn("active", "INTEGER DEFAULT 1")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addColumn("updated_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addIndex("CREATE INDEX IF NOT EXISTS idx_guild_systems_guild_active ON guild_systems(guild_id, active)");
    }
    
    /**
     * Define the rules_embeds_channel table schema
     */
    private TableSchema createRulesEmbedsChannelSchema() {
        return new TableSchema("rules_embeds_channel")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("guild_id", "INTEGER")
            .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addColumn("title", "TEXT NOT NULL")
            .addColumn("description", "TEXT NOT NULL")
            .addColumn("footer", "TEXT")
            .addColumn("color", "TEXT DEFAULT 'green'")
            .addColumn("role_id", "TEXT")
            .addColumn("button_label", "TEXT")
            .addColumn("button_emoji_id", "TEXT");
    }
    
    /**
     * Define the log_channels table schema (legacy compatibility)
     */
    private TableSchema createLogChannelsSchema() {
        return new TableSchema("log_channels")
            .addColumn("guildid", "TEXT PRIMARY KEY")
            .addColumn("channelid", "TEXT NOT NULL");
    }
    
    /**
     * Define the warn_system_settings table schema (legacy compatibility)
     */
    private TableSchema createWarnSystemSettingsSchema() {
        return new TableSchema("warn_system_settings")
            .addColumn("guild_id", "TEXT PRIMARY KEY")
            .addColumn("max_warns", "INTEGER NOT NULL")
            .addColumn("minutes_muted", "INTEGER NOT NULL")
            .addColumn("role_id", "TEXT NOT NULL")
            .addColumn("warn_time_hours", "INTEGER NOT NULL");
    }
    
    /**
     * Define the database_migrations table schema for tracking applied migrations
     */
    private TableSchema createDatabaseMigrationsSchema() {
        return new TableSchema("database_migrations")
            .addColumn("id", "INTEGER PRIMARY KEY AUTO_INCREMENT")
            .addColumn("migration_name", "TEXT NOT NULL UNIQUE")
            .addColumn("version", "TEXT NOT NULL")
            .addColumn("applied_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
            .addColumn("execution_time_ms", "INTEGER")
            .addColumn("success", "INTEGER DEFAULT 1");
    }
    
    /**
     * Core algorithm to detect and apply missing columns.
     * This method compares expected schemas with actual database schemas
     * and automatically adds any missing columns.
     */
    public void detectAndApplyMissingColumns() throws SQLException {
        System.out.println("Starting comprehensive database migration check...");
        
        long startTime = System.currentTimeMillis();
        Map<String, TableSchema> expectedSchemas = getExpectedSchemas();
        int totalColumnsAdded = 0;
        int tablesProcessed = 0;
        
        // Create migrations tracking table first if it doesn't exist
        ensureMigrationsTableExists();
        
        for (Map.Entry<String, TableSchema> entry : expectedSchemas.entrySet()) {
            String tableName = entry.getKey();
            TableSchema expectedSchema = entry.getValue();
            
            try {
                if (tableExists(tableName)) {
                    int columnsAdded = checkAndAddMissingColumns(tableName, expectedSchema);
                    totalColumnsAdded += columnsAdded;
                    tablesProcessed++;
                    
                    if (columnsAdded > 0) {
                        System.out.println("Added " + columnsAdded + " missing columns to table '" + tableName + "'");
                    }
                } else {
                    System.out.println("Table '" + tableName + "' does not exist - will be created by table initialization");
                }
            } catch (SQLException e) {
                System.err.println("Error processing table '" + tableName + "': " + e.getMessage());
                // Continue with other tables rather than failing completely
            }
        }
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        // Record this migration run
        recordMigrationRun("automatic_column_detection", "1.0", executionTime, true);
        
        System.out.println("Migration check completed in " + executionTime + "ms");
        System.out.println("Processed " + tablesProcessed + " tables, added " + totalColumnsAdded + " total columns");
    }
    
    /**
     * Check if a table exists in the database
     */
    private boolean tableExists(String tableName) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }
    
    /**
     * Check for missing columns in a specific table and add them
     */
    private int checkAndAddMissingColumns(String tableName, TableSchema expectedSchema) throws SQLException {
        Set<String> existingColumns = getExistingColumns(tableName);
        Map<String, String> columnsToAdd = new HashMap<>();
        
        // Find missing columns
        for (Map.Entry<String, ColumnDefinition> entry : expectedSchema.columns.entrySet()) {
            String columnName = entry.getKey();
            ColumnDefinition columnDef = entry.getValue();
            
            if (!existingColumns.contains(columnName.toLowerCase())) {
                columnsToAdd.put(columnName, columnDef.sqlDefinition);
            }
        }
        
        // Add missing columns if any found
        if (!columnsToAdd.isEmpty()) {
            System.out.println("Found " + columnsToAdd.size() + " missing columns in table '" + tableName + "': " + 
                             String.join(", ", columnsToAdd.keySet()));
            
            for (Map.Entry<String, String> column : columnsToAdd.entrySet()) {
                try {
                    addColumnToTable(tableName, column.getKey(), column.getValue());
                } catch (SQLException e) {
                    System.err.println("Failed to add column '" + column.getKey() + "' to table '" + tableName + "': " + e.getMessage());
                    // Continue with other columns
                }
            }
        }
        
        return columnsToAdd.size();
    }
    
    /**
     * Get existing column names for a table
     */
    private Set<String> getExistingColumns(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        
        try (Statement stmt = connection.createStatement()) {
            String query = "SHOW COLUMNS FROM " + tableName;
            try (ResultSet rs = stmt.executeQuery(query)) {
                while (rs.next()) {
                    columns.add(rs.getString("Field").toLowerCase()); // MariaDB uses "Field" column name
                }
            }
        }
        
        return columns;
    }
    
    /**
     * Add a single column to a table with special handling for SQLite constraints
     */
    private void addColumnToTable(String tableName, String columnName, String columnDefinition) throws SQLException {
        String alterQuery = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(alterQuery);
            System.out.println("Successfully added column '" + columnName + "' to table '" + tableName + "'");
        } catch (SQLException e) {
            // Handle SQLite constraint for columns with CURRENT_TIMESTAMP default
            if (e.getMessage().contains("Cannot add a column with non-constant default")) {
                System.out.println("Handling SQLite constraint for column '" + columnName + "' with CURRENT_TIMESTAMP default");
                
                // For timestamp columns, add them without default first, then update
                String modifiedDefinition = columnDefinition.replace("DEFAULT CURRENT_TIMESTAMP", "");
                String alterQueryNoDefault = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + modifiedDefinition;
                
                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(alterQueryNoDefault);
                    
                    // Update all existing rows to have the current timestamp
                    if (columnDefinition.contains("DEFAULT CURRENT_TIMESTAMP")) {
                        String updateQuery = "UPDATE " + tableName + " SET " + columnName + " = CURRENT_TIMESTAMP WHERE " + columnName + " IS NULL";
                        stmt.execute(updateQuery);
                    }
                    
                    System.out.println("Successfully added column '" + columnName + "' to table '" + tableName + "' (with SQLite constraint workaround)");
                }
            } else {
                throw e; // Re-throw if it's a different error
            }
        }
    }
    
    /**
     * Ensure the migrations tracking table exists
     */
    private void ensureMigrationsTableExists() throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS database_migrations (" +
            "id INTEGER PRIMARY KEY AUTO_INCREMENT, " +
            "migration_name TEXT NOT NULL UNIQUE, " +
            "version TEXT NOT NULL, " +
            "applied_at TEXT DEFAULT CURRENT_TIMESTAMP, " +
            "execution_time_ms INTEGER, " +
            "success INTEGER DEFAULT 1" +
            ")";
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createTable);
        }
    }
    
    /**
     * Record a migration run in the tracking table
     */
    private void recordMigrationRun(String migrationName, String version, long executionTimeMs, boolean success) {
        try {
            String insertMigration = "INSERT INTO database_migrations " +
                "(migration_name, version, execution_time_ms, success) VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE " +
                "version = VALUES(version), " +
                "execution_time_ms = VALUES(execution_time_ms), " +
                "success = VALUES(success), " +
                "applied_at = CURRENT_TIMESTAMP";
            
            try (PreparedStatement stmt = connection.prepareStatement(insertMigration)) {
                stmt.setString(1, migrationName);
                stmt.setString(2, version);
                stmt.setLong(3, executionTimeMs);
                stmt.setInt(4, success ? 1 : 0);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Failed to record migration run: " + e.getMessage());
            // Non-fatal error - don't stop the migration process
        }
    }
    
    /**
     * Get migration history from the tracking table
     */
    public List<Map<String, Object>> getMigrationHistory() throws SQLException {
        List<Map<String, Object>> history = new ArrayList<>();
        
        String query = "SELECT migration_name, version, applied_at, execution_time_ms, success " +
                      "FROM database_migrations ORDER BY applied_at DESC";
        
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            
            while (rs.next()) {
                Map<String, Object> migration = new HashMap<>();
                migration.put("migration_name", rs.getString("migration_name"));
                migration.put("version", rs.getString("version"));
                migration.put("applied_at", rs.getString("applied_at"));
                migration.put("execution_time_ms", rs.getLong("execution_time_ms"));
                migration.put("success", rs.getInt("success") == 1);
                history.add(migration);
            }
        }
        
        return history;
    }
    
    /**
     * Apply all indexes for a table if they don't already exist
     */
    public void applyIndexes(String tableName, TableSchema schema) throws SQLException {
        // Apply indexes
        for (String indexDefinition : schema.indexes) {
            try (Statement stmt = connection.createStatement()) {
                stmt.execute(indexDefinition);
            } catch (SQLException e) {
                // Index might already exist, which is fine
                if (!e.getMessage().contains("already exists")) {
                    System.err.println("Warning: Failed to create index for table '" + tableName + "': " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Validate that all expected tables and columns exist
     */
    public boolean validateDatabaseSchema() throws SQLException {
        Map<String, TableSchema> expectedSchemas = getExpectedSchemas();
        boolean isValid = true;
        
        System.out.println("Validating database schema...");
        
        for (Map.Entry<String, TableSchema> entry : expectedSchemas.entrySet()) {
            String tableName = entry.getKey();
            TableSchema expectedSchema = entry.getValue();
            
            if (!tableExists(tableName)) {
                System.err.println("Missing table: " + tableName);
                isValid = false;
                continue;
            }
            
            Set<String> existingColumns = getExistingColumns(tableName);
            for (String expectedColumn : expectedSchema.columns.keySet()) {
                if (!existingColumns.contains(expectedColumn.toLowerCase())) {
                    System.err.println("Missing column '" + expectedColumn + "' in table '" + tableName + "'");
                    isValid = false;
                }
            }
        }
        
        if (isValid) {
            System.out.println("Database schema validation passed");
        } else {
            System.err.println("Database schema validation failed");
        }
        
        return isValid;
    }
}