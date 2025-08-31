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
- Generate ticket transcripts
- Customizable ticket categories and priorities

### 📝 Log Channel System
- Configure dedicated logging channels
- Track server events and activities
- Comprehensive audit trail

### 📊 Statistics System
- Server activity statistics
- User engagement metrics
- Command usage tracking

## Requirements

- Java 11 or higher
- Discord Bot Token
- SQLite database (automatically created)

## Dependencies

The bot uses the following key dependencies:
- **JDA (Java Discord API)** v5.0.0-beta.24 - Discord API wrapper
- **SQLite JDBC** v3.46.0.0 - Database connectivity
- **Dotenv Java** v3.0.0 - Environment variable management

## Setup

### 1. Clone the Repository
```bash
git clone https://github.com/GamingToastEVE/Delta.git
cd Delta
```

### 2. Configure Environment Variables
Create a `.env` file in the root directory with your Discord bot token:
```env
TOKEN=your_discord_bot_token_here
```

### 3. Build the Project
```bash
./gradlew build
```

### 4. Run the Bot
```bash
./gradlew run
```

## Discord Bot Setup

1. Go to the [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application and bot
3. Copy the bot token and add it to your `.env` file
4. Enable the following bot permissions:
   - Send Messages
   - Read Message History
   - Embed Links
   - Manage Channels
   - Manage Roles
   - Use Slash Commands
   - View Channels
5. Enable the following privileged gateway intents:
   - Message Content Intent
   - Server Members Intent

## Usage

Delta uses a modular system approach where server administrators can activate only the systems they need.

### System Management

Use the `/add-system` command to activate different systems on your server:

```
/add-system system:Log Channel System
/add-system system:Warning System  
/add-system system:Ticket System
/add-system system:Moderation System
```

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
- Various moderation commands for server management

## Database

Delta automatically creates and manages an SQLite database (`server.db`) that stores:
- Guild configurations
- User warnings and moderation history
- Ticket information and transcripts
- System activation status
- Activity statistics

## File Structure

```
Delta/
├── src/main/java/org/ToastiCodingStuff/Delta/
│   ├── Delta.java                          # Main bot class
│   ├── DatabaseHandler.java                # Database operations
│   ├── AddGuildSlashCommands.java          # Command registration
│   ├── SystemManagementCommandListener.java # System activation
│   ├── TicketCommandListener.java          # Ticket system
│   ├── WarnCommandListener.java            # Warning system
│   ├── ModerationCommandListener.java      # Moderation system
│   ├── LogChannelSlashCommandListener.java # Logging system
│   ├── StatisticsCommandListener.java      # Statistics tracking
│   └── GuildEventListener.java             # Guild event handling
├── build.gradle.kts                        # Build configuration
├── .env                                     # Environment variables (create this)
└── README.md                               # This file
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

This project is developed by ToastiCodingStuff. Please refer to the Terms of Service for usage guidelines.

## Support

For support, please create a ticket using the bot's ticket system or open an issue in this repository.