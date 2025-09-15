# Database Setup

The Delta bot now uses MariaDB instead of SQLite. Follow these steps to set up the database:

## Requirements

- MariaDB 10.3 or higher
- Java 11 or higher

## Installation

1. Install MariaDB:
   ```bash
   # Ubuntu/Debian
   sudo apt update
   sudo apt install mariadb-server mariadb-client
   
   # CentOS/RHEL
   sudo yum install mariadb-server mariadb
   
   # macOS
   brew install mariadb
   ```

2. Start MariaDB service:
   ```bash
   sudo systemctl start mariadb
   sudo systemctl enable mariadb
   ```

3. Secure the installation:
   ```bash
   sudo mysql_secure_installation
   ```

## Database Configuration

1. Create the database and user:
   ```sql
   CREATE DATABASE delta_bot;
   CREATE USER 'delta_bot'@'localhost' IDENTIFIED BY 'delta_bot';
   GRANT ALL PRIVILEGES ON delta_bot.* TO 'delta_bot'@'localhost';
   FLUSH PRIVILEGES;
   ```

2. Set up environment variables:
   - Copy `.env.example` to `.env`
   - Modify the database settings in `.env` if needed:
     ```
     DB_HOST=localhost
     DB_PORT=3306
     DB_NAME=delta_bot
     DB_USER=delta_bot
     DB_PASSWORD=delta_bot
     ```

## Migration from SQLite

The bot will automatically create all necessary tables when it starts. If you're migrating from SQLite, you'll need to export your data and import it into MariaDB manually.

## Tables

The database will automatically create the following tables:
- users
- guilds
- warnings
- moderation_actions
- tickets
- ticket_messages
- guild_settings
- role_permissions
- bot_logs
- statistics
- temporary_data
- guild_systems
- rules_embeds_channel
- warn_system_settings
- log_channels
- user_statistics
- database_migrations

All table creation and schema migrations are handled automatically by the bot's migration system.