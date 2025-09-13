package org.ToastiCodingStuff.Sloth;

import java.sql.*;
import java.util.Map;

/**
 * Test class to demonstrate the comprehensive database migration system.
 * This class shows how the migration system detects and adds missing columns automatically.
 */
public class DatabaseMigrationTest {
    
    public static void main(String[] args) {
        System.out.println("=== Database Migration System Test ===");
        
        try {
            // Create a test database connection
            Connection connection = DriverManager.getConnection("jdbc:sqlite:test_migration.db");
            
            // Create the migration manager
            DatabaseMigrationManager migrationManager = new DatabaseMigrationManager(connection);
            
            // Test 1: Create a table with some columns missing
            System.out.println("\n1. Creating test table with missing columns...");
            createTestTableWithMissingColumns(connection);
            
            // Test 2: Run migration to detect and add missing columns
            System.out.println("\n2. Running migration to detect and add missing columns...");
            migrationManager.detectAndApplyMissingColumns();
            
            // Test 3: Validate the schema
            System.out.println("\n3. Validating database schema...");
            boolean isValid = migrationManager.validateDatabaseSchema();
            System.out.println("Schema validation result: " + (isValid ? "PASSED" : "FAILED"));
            
            // Test 4: Show migration history
            System.out.println("\n4. Migration history:");
            var history = migrationManager.getMigrationHistory();
            for (Map<String, Object> migration : history) {
                System.out.println("  " + migration.get("migration_name") + " (v" + 
                                 migration.get("version") + ") - " + 
                                 migration.get("applied_at") + " - " +
                                 migration.get("execution_time_ms") + "ms");
            }
            
            // Test 5: Demonstrate that running migration again doesn't add columns again
            System.out.println("\n5. Running migration again (should not add any columns)...");
            migrationManager.detectAndApplyMissingColumns();
            
            // Clean up
            connection.close();
            
            System.out.println("\n=== Test completed successfully! ===");
            
        } catch (SQLException e) {
            System.err.println("Test failed with error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Create a test table that's missing some columns from the expected schema
     */
    private static void createTestTableWithMissingColumns(Connection connection) throws SQLException {
        Statement stmt = connection.createStatement();
        
        // Create a simplified users table missing some columns
        String createTable = "CREATE TABLE IF NOT EXISTS users (" +
            "id INTEGER PRIMARY KEY, " +
            "username TEXT NOT NULL" +
            // Missing: discriminator, avatar, created_at, updated_at
            ")";
        
        stmt.execute(createTable);
        
        // Create a simplified guilds table missing some columns  
        createTable = "CREATE TABLE IF NOT EXISTS guilds (" +
            "id INTEGER PRIMARY KEY, " +
            "name TEXT NOT NULL" +
            // Missing: prefix, language, created_at, updated_at, active
            ")";
        
        stmt.execute(createTable);
        
        System.out.println("Created test tables with missing columns");
        stmt.close();
    }
}