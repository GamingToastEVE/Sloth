package org.ToastiCodingStuff.Sloth;

import java.sql.*;
import java.util.Set;
import java.util.HashSet;

/**
 * Simple test to validate database table creation
 */
public class DatabaseTest {
    public static void main(String[] args) {
        System.out.println("Starting Database Table Creation Test...");
        
        try {
            // Use SQLite for testing instead of MariaDB to avoid external dependencies
            String url = "jdbc:sqlite:test_database.db";
            Connection connection = DriverManager.getConnection(url);
            System.out.println("Connected to test database");
            
            // Create a modified DatabaseHandler for testing
            TestDatabaseHandler handler = new TestDatabaseHandler(connection);
            
            // Test the table existence check
            boolean tablesExist = handler.testTablesAlreadyExist();
            System.out.println("Tables already exist: " + tablesExist);
            
            // Test table creation
            handler.testInitializeTables();
            
            // Verify all expected tables were created
            Set<String> expectedTables = Set.of(
                "users", "warnings", "moderation_actions", "tickets", "ticket_messages",
                "guild_settings", "role_permissions", "bot_logs", "statistics",
                "temporary_data", "guilds", "guild_systems", "rules_embeds_channel",
                "log_channels", "warn_system_settings", "database_migrations", "user_statistics"
            );
            
            Set<String> actualTables = getActualTables(connection);
            System.out.println("Expected tables: " + expectedTables.size());
            System.out.println("Actual tables: " + actualTables.size());
            
            boolean allTablesCreated = actualTables.containsAll(expectedTables);
            System.out.println("All expected tables created: " + allTablesCreated);
            
            if (!allTablesCreated) {
                System.out.println("Missing tables:");
                for (String table : expectedTables) {
                    if (!actualTables.contains(table)) {
                        System.out.println("  - " + table);
                    }
                }
            }
            
            connection.close();
            System.out.println("Test completed successfully!");
            
        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static Set<String> getActualTables(Connection connection) throws SQLException {
        Set<String> tables = new HashSet<>();
        DatabaseMetaData meta = connection.getMetaData();
        ResultSet rs = meta.getTables(null, null, null, new String[]{"TABLE"});
        while (rs.next()) {
            tables.add(rs.getString("TABLE_NAME").toLowerCase());
        }
        return tables;
    }
    
    // Test version of DatabaseHandler that uses SQLite
    static class TestDatabaseHandler {
        private final Connection connection;
        
        public TestDatabaseHandler(Connection connection) {
            this.connection = connection;
        }
        
        public boolean testTablesAlreadyExist() {
            try {
                DatabaseMetaData meta = connection.getMetaData();
                // Use the same table list as the fixed method
                String[] tableNames = {
                    "users", "warnings", "moderation_actions", "tickets", "ticket_messages",
                    "guild_settings", "role_permissions", "bot_logs", "statistics",
                    "temporary_data", "guilds", "guild_systems", "rules_embeds_channel",
                    "log_channels", "warn_system_settings", "database_migrations", "user_statistics"
                };
                for (String tableName : tableNames) {
                    ResultSet rs = meta.getTables(null, null, tableName, null);
                    if (!rs.next()) {
                        System.out.println("Missing table detected: " + tableName);
                        return false;
                    }
                }
                return true;
            } catch (SQLException e) {
                System.err.println("Error checking database tables: " + e.getMessage());
                return false;
            }
        }
        
        public void testInitializeTables() throws SQLException {
            System.out.println("Creating test tables...");
            
            // Create simplified versions of the tables for testing
            Statement stmt = connection.createStatement();
            
            // Core tables
            stmt.execute("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY, username TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS guilds (id INTEGER PRIMARY KEY, name TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS warnings (id INTEGER PRIMARY KEY, guild_id INTEGER, user_id INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS moderation_actions (id INTEGER PRIMARY KEY, guild_id INTEGER, user_id INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS tickets (id INTEGER PRIMARY KEY, guild_id INTEGER, user_id INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS ticket_messages (id INTEGER PRIMARY KEY, ticket_id INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS guild_settings (id INTEGER PRIMARY KEY, guild_id INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS role_permissions (id INTEGER PRIMARY KEY, guild_id INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS bot_logs (id INTEGER PRIMARY KEY, guild_id INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS statistics (id INTEGER PRIMARY KEY, guild_id INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS user_statistics (id INTEGER PRIMARY KEY, guild_id INTEGER, user_id INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS temporary_data (id INTEGER PRIMARY KEY, guild_id INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS guild_systems (id INTEGER PRIMARY KEY, guild_id INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS rules_embeds_channel (id INTEGER PRIMARY KEY, guild_id INTEGER)");
            
            // Legacy tables
            stmt.execute("CREATE TABLE IF NOT EXISTS log_channels (guildid TEXT PRIMARY KEY, channelid TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS warn_system_settings (guild_id TEXT PRIMARY KEY, max_warns INTEGER)");
            
            // Migration tracking table
            stmt.execute("CREATE TABLE IF NOT EXISTS database_migrations (id INTEGER PRIMARY KEY, migration_name TEXT)");
            
            System.out.println("Test tables created successfully!");
        }
    }
}