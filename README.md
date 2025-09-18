# Delta Discord Bot

Delta is a comprehensive Discord moderation and management bot that provides multiple systems to help server administrators manage their communities effectively.

## Features

Delta offers several modular systems that can be independently activated per server:

### 🛡️ Moderation System
- Advanced user moderation capabilities
- Automated moderation actions
- Comprehensive logging of moderation activities

### ⚠️ Warning System
- Issue warnings to users with different severity levels
- Configurable automatic actions based on warning thresholds
- Automatic warning expiration
- User warning history tracking

### 🎫 Ticket System
- Create support ticket channels
- Assign tickets to staff members
- Generate ticket transcripts (not possible until discord approves message content intent)
- Customizable ticket categories and priorities

### 📝 Log Channel System
- Configure dedicated logging channels
- Track server events and activities
- Comprehensive audit trail

### 📊 Statistics System
- Server activity statistics
- User engagement metrics
- Command usage tracking

### 📋 Rules Embed System
- Create and manage rule embeds in channels
- Customizable rule formatting and styling
- Easy rule distribution across your server

## Setup

### 1. Clone the Repository
```bash
git clone https://github.com/GamingToastEVE/Delta.git
cd Delta
```

### 2. Configure Environment Variables
Create a `.env` file in the root directory based on `.env.example`:
```env
# Discord Bot Token
TOKEN=your_discord_bot_token_here

# Database Configuration (MariaDB)
DB_HOST=localhost
DB_PORT=3306
DB_NAME=delta_bot
DB_USER=delta_bot
DB_PASSWORD=delta_bot
```

**Important**: The bot now uses MariaDB instead of SQLite. See [DATABASE_SETUP.md](DATABASE_SETUP.md) for detailed database setup instructions.

### 3. Build the Project
```bash
./gradlew build
```

### 4. Run the Bot
```bash
./gradlew run
```

## Usage

Delta uses a modular system approach where server administrators can activate only the systems they need.

### Getting Help

Use the `/help` command to access Delta's interactive help system. The help system provides:
- 🏠 **Overview** - Learn about Delta's features and capabilities
- ⚙️ **Systems** - Browse all available modular systems
- 📋 **Setup** - Step-by-step configuration guides  
- 📖 **Commands** - Complete command reference

Navigate between help sections using the interactive buttons.

### Available Systems

#### Log Channel System
- `/set-log-channel` - Configure the server log channel
- `/get-log-channel` - View current log channel

#### Warning System
- `/warn` - Issue a warning to a user
- `/set-warn-settings` - Configure warning system settings
- `/get-warn-settings` - View current warning settings

#### Ticket System
- `/ticket-setup` - Configure the ticket system
- `/ticket-panel` - Create a ticket creation panel
- `/close-ticket` - Close a ticket
- `/assign-ticket` - Assign ticket to staff member
- `/ticket-info` - Get ticket information
- `/ticket-transcript` - Generate ticket transcript

#### Moderation System
- `/kick` - Kick a user from the server
- `/ban` - Ban a user from the server
- `/unban` - Unban a user from the server
- `/timeout` - Timeout a user for a specified duration
- `/untimeout` - Remove timeout from a user
- `/purge` - Delete multiple messages from the channel
- `/slowmode` - Set slowmode for the current channel

#### Rules Embed System
- `/add-rules-embed` - Add a formatted rules embed to a channel
- Supports custom formatting and styling options

## Database

Delta uses MariaDB for data storage and automatically creates and manages the database that stores:
- Guild configurations
- User warnings and moderation history
- Ticket information and transcripts
- System activation status
- Activity statistics

For detailed database setup instructions, see [DATABASE_SETUP.md](DATABASE_SETUP.md).

The bot includes an automatic migration system that handles schema updates. See [MIGRATION_SYSTEM.md](MIGRATION_SYSTEM.md) for more information about the migration capabilities.

## File Structure

```
Delta/
├── src/main/java/org/ToastiCodingStuff/Sloth/
│   ├── Sloth.java                              # Main bot class
│   ├── DatabaseHandler.java                    # Database operations
│   ├── DatabaseMigrationManager.java           # Database schema migrations
│   ├── AddGuildSlashCommands.java              # Command registration
│   ├── HelpCommandListener.java                # Help system
│   ├── TicketCommandListener.java              # Ticket system
│   ├── WarnCommandListener.java                # Warning system
│   ├── ModerationCommandListener.java          # Moderation system
│   ├── LogChannelSlashCommandListener.java     # Logging system
│   ├── StatisticsCommandListener.java          # Statistics tracking
│   ├── AddRulesEmbedToChannelCommandListener.java # Rules embed system
│   └── GuildEventListener.java                 # Guild event handling
├── build.gradle.kts                            # Build configuration
├── .env.example                                 # Environment variables template
├── DATABASE_SETUP.md                           # Database setup guide
├── MIGRATION_SYSTEM.md                         # Migration system documentation
└── README.md                                   # This file
```

## Development

### Building
```bash
./gradlew build
```

### Running in Development
```bash
./gradlew run
```

### Creating Distribution
```bash
./gradlew distTar  # Creates tar distribution
./gradlew distZip  # Creates zip distribution
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test thoroughly
5. Submit a pull request

## Legal

- [Terms of Service](Terms%20of%20Service.md)
- [Privacy Policy](privacy%20policy.md)

## License

This project is developed by gamingtoasti. Please refer to the Terms of Service for usage guidelines.

## Support

For support, please create a ticket using the bot's ticket system or open an issue in this repository.

- Support Server: https://discord.gg/dQT53fD8M5
- Discord: **gamingtoasti**
