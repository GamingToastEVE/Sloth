# Sloth Bot

Sloth is a comprehensive Discord moderation and management bot that provides multiple systems to help server administrators manage their communities effectively.

## Features

Sloth offers several modular systems that can be independently activated per server:

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
- Set ticket priorities
- Generate ticket transcripts (pending Discord message content intent approval)
- Customizable ticket categories and priorities

### 📝 Log Channel System
- Configure dedicated logging channels
- Track server events and activities
- Comprehensive audit trail

### 📊 Statistics System
- Server activity statistics
- User engagement metrics
- Daily, weekly, and lifetime statistics tracking
- Individual user statistics
- Date-specific statistics queries

### 📋 Rules Embed System
- Create and manage rule embeds in channels
- Customizable rule formatting and styling
- Easy rule distribution across your server

### ✅ Verification System
- Create verification buttons for new members
- Automatic role assignment upon verification
- Customizable verification messages
- Multiple verification setups per server

### 🎭 Role Selection System
- Create interactive role selection menus
- Allow users to self-assign roles
- Customizable role options with descriptions
- Multiple role selection messages per server
- WIP

## Setup

### 1. Clone the Repository
```bash
git clone https://github.com/GamingToastEVE/Sloth.git
cd Sloth
```

### 2. Configure Environment Variables
Create a `.env` file in the root directory based on `.env.example`:
```env
# Discord Bot Token
TOKEN_TEST=your_discord_bot_token_here

# Database Configuration (MariaDB)
DB_HOST=localhost
DB_PORT=3306
DB_NAME=sloth
DB_USER=sloth
DB_PASSWORD=your_secure_password_here
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

Sloth uses a modular system approach where server administrators can activate only the systems they need.

### Getting Help

Use the `/help` command to access Sloth's interactive help system. The help system provides:
- 🏠 **Overview** - Learn about Sloth's features and capabilities
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
- `/set-ticket-priority` - Change the priority of the current ticket
- `/ticket-info` - Get ticket information
- `/ticket-transcript` - Generate ticket transcript (not yet available - pending Discord message content intent approval)

#### Moderation System
- `/kick` - Kick a user from the server
- `/ban` - Ban a user from the server
- `/unban` - Unban a user from the server
- `/timeout` - Timeout a user for a specified duration
- `/untimeout` - Remove timeout from a user
- `/purge` - Delete multiple messages from the channel
- `/slowmode` - Set slowmode for the current channel

#### Rules Embed System
- `/setup-rules` - Set up rules in the current channel
- `/add-rules-embed` - Add a formatted rules embed to the database (max 3)
- `/list-rules-embeds` - List all rules embeds in the server
- `/remove-rules-embed` - Remove a rules embed from the database
- Supports custom formatting and styling options

#### Verification System
- `/send-just-verify-button` - Send a verification button message in the current channel
- `/add-just-verify-button` - Add a verification button configuration to the database (max 3)
- `/remove-just-verify-button` - Remove a verification button embed from the current channel

#### Role Selection System
- `/create-select-roles` - Create a role selection message in the current channel
- `/add-select-role` - Add a role option to the role selection message
- `/remove-select-role` - Remove a role option from the role selection message
- `/delete-select-roles-message` - Delete a role selection message and its configuration

#### Statistics System
- `/stats` - View lifetime server moderation statistics
- `/stats-today` - View today's server moderation statistics
- `/stats-week` - View this week's server moderation statistics
- `/stats-date` - View server statistics for a specific date
- `/stats-user` - View user information and statistics

## Database

Sloth uses MariaDB for data storage and automatically creates and manages the database that stores:
- Guild configurations
- User warnings and moderation history
- Ticket information and transcripts
- System activation status
- Activity statistics

For detailed database setup instructions, see [DATABASE_SETUP.md](DATABASE_SETUP.md).

The bot includes an automatic migration system that handles schema updates. See [MIGRATION_SYSTEM.md](MIGRATION_SYSTEM.md) for more information about the migration capabilities.

## File Structure

```
Sloth/
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
│   ├── JustVerifyButtonCommandListener.java    # Verification system
│   ├── GuildEventListener.java                 # Guild event handling
│   ├── GlobalCommandListener.java              # Global command handling
│   └── OnGuildLeaveListener.java               # Guild leave events
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
