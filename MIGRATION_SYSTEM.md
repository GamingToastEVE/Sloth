# Database Migration System - Usage Examples

This document demonstrates how to use the comprehensive database migration system implemented in the Sloth Bot.

## Overview

The migration system automatically detects and adds missing columns to database tables, ensuring seamless schema evolution across software versions.

## Key Features

- **Automatic Detection**: Compares expected table schemas with actual database structure
- **Safe Migrations**: Only adds missing columns, preserves all existing data
- **SQLite Compatibility**: Handles SQLite constraints like CURRENT_TIMESTAMP defaults  
- **Migration Tracking**: Records all migrations with timestamps and execution times
- **Schema Validation**: Verifies database integrity after migrations

## How It Works

1. **Schema Definition**: All expected table structures are defined in `DatabaseMigrationManager.getExpectedSchemas()`
2. **Automatic Detection**: On startup, the system compares expected vs actual schemas
3. **Column Addition**: Missing columns are automatically added with proper defaults
4. **Data Preservation**: All existing data is preserved during migration
5. **Audit Trail**: Migration history is tracked for monitoring and debugging

## Adding New Features

To add new database columns for a feature:

1. **Update Schema Definition**: Modify the appropriate `create*Schema()` method in `DatabaseMigrationManager`
2. **Restart Application**: Migration runs automatically on startup
3. **Verify**: Check logs to confirm successful column addition

### Example: Adding User Preferences

```java
// In DatabaseMigrationManager.createUsersSchema():
private TableSchema createUsersSchema() {
    return new TableSchema("users")
        .addColumn("id", "INTEGER PRIMARY KEY")
        .addColumn("username", "TEXT NOT NULL")
        .addColumn("discriminator", "TEXT")
        .addColumn("avatar", "TEXT")
        .addColumn("created_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
        .addColumn("updated_at", "TEXT DEFAULT CURRENT_TIMESTAMP")
        // NEW COLUMNS FOR USER PREFERENCES:
        .addColumn("timezone", "TEXT DEFAULT 'UTC'")
        .addColumn("notification_preferences", "TEXT DEFAULT 'all'")
        .addColumn("last_activity", "TEXT");
}
```

## Migration Output Example

```
Starting comprehensive database migration check...
Found 3 missing columns in table 'users': timezone, notification_preferences, last_activity
Successfully added column 'timezone' to table 'users'
Successfully added column 'notification_preferences' to table 'users'  
Successfully added column 'last_activity' to table 'users'
Added 3 missing columns to table 'users'
Migration check completed in 45ms
Processed 12 tables, added 3 total columns
```

## Manual Migration Operations

```java
// Manual migration check
DatabaseHandler handler = new DatabaseHandler();
handler.runMigrationCheck();

// Get migration history
List<Map<String, Object>> history = handler.getMigrationHistory();
for (Map<String, Object> migration : history) {
    System.out.println(migration.get("migration_name") + " - " + 
                      migration.get("execution_time_ms") + "ms");
}

// Validate schema
boolean isValid = handler.validateDatabaseSchema();

// Add single column (if needed)
handler.addColumnIfNotExists("tickets", "priority_level", "INTEGER DEFAULT 1");
```

## Benefits

- **Zero Downtime**: Migrations are additive and preserve all data
- **Developer Friendly**: Simple schema definitions, automatic migration
- **Production Safe**: Only adds columns, never removes or modifies existing data
- **Auditable**: Complete migration history with performance metrics
- **SQLite Optimized**: Handles SQLite-specific constraints and limitations

## Migration History Tracking

The system automatically tracks:
- Migration name and version
- Execution timestamp  
- Performance metrics (execution time)
- Success/failure status
- Number of tables and columns affected

This provides a complete audit trail for database schema evolution.