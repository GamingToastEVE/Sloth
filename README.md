# Sloth Bot

Sloth is a comprehensive Discord moderation and management bot that provides multiple systems to help server administrators manage their communities effectively.

## Features

Sloth offers several systems that can be used:

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
- Multiple ticket panels per server, each with its own channel, category and support role
- Category buttons per panel, with optional multi-step forms
- Assign tickets to staff members
- Customizable ticket categories and priorities

### 📝 Log Channel System
- Configure dedicated logging channels
- Track server events and activities
- Comprehensive audit trail

### 📊 Statistics System
- Server activity statistics
- User engagement metrics
- Command usage tracking

### 🔘 Verify Button System
- Create custom verification buttons
- Assign roles when users verify
- Remove roles upon verification
- Support for multiple verify button configurations (max 3)

### 🎭 Select Roles System
- Allow users to self-assign roles
- Create role selection menus with descriptions and emojis
- Support for reactions, dropdowns, and buttons
- Easy role management for server members

### ⏱️ Timed Roles System
- Assign temporary roles that automatically expire
- Automated role management based on events
- Configure role triggers and durations
- Track active temporary roles per user

### 🎨 Embed Editor System
- Create custom embeds with an interactive visual editor
- Edit title, description, footer, author, color, main image, thumbnail, and fields
- Save embeds to database for reuse
- Load and modify saved embeds
- Publish embeds with optional verify button integration

### 🌐 Language System
- Multi-language support for the bot
- Server administrators can choose their preferred language
- Currently supports English and German
- Easy language switching via `/language` command

### 📈 Leveling System
- Track user activity with XP and levels
- Earn XP from messages and voice chat participation
- View individual rank cards with `/level rank`
- Server-wide leaderboard with `/level leaderboard`
- Configurable leveling settings for administrators
- Automatic level-up notifications

### ⏰ Reminder System
- Set personal reminders with custom titles and messages
- Flexible time format (e.g., 10m, 1h, 2d)
- Choose between DM or channel notifications
- List and manage all active reminders
- Commands:
  - `/reminder set` - Create a new reminder
  - `/reminder list` - View all your active reminders

### 🚀 Setup Wizard
- Interactive onboarding experience for new servers
- Step-by-step configuration of bot systems
- Guided setup for log channels, moderation, tickets, and more
- Makes initial configuration quick and easy

### ⚙️ Systems Management
- Enable or disable bot systems per server
- Use `/systems` command to control which features are active
- Customize Sloth to fit your server's specific needs

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

### 5. Deploy on a Server (optional)
The bot can run as a systemd service that picks up new commits on `main` by itself.
See [deploy/README.md](deploy/README.md).

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
- `/log-channel set` - Configure the server log channel
- `/log-channel get` - View current log channel

#### Warning System
- `/warn user` - Issue a warning to a user with severity level
- `/warn list` - View and manage active warnings for a user
- `/warn settings-set` - Configure warning system settings
- `/warn settings-get` - View current warning settings

#### Ticket System
- `/ticket-panels` - Set up and manage ticket panels, categories and forms
- `/ticket close` - Close a ticket
- `/ticket assign` - Assign ticket to staff member
- `/ticket priority` - Change ticket priority
- `/ticket info` - Get ticket information

Ticket panels can also be set up through `/setup`, which creates a panel and posts it
for you.

#### Moderation System
- `/mod kick` - Kick a user from the server
- `/mod ban` - Ban a user from the server
- `/mod unban` - Unban a user from the server
- `/mod timeout` - Timeout a user for a specified duration
- `/mod untimeout` - Remove timeout from a user
- `/mod purge` - Delete multiple messages from the channel
- `/mod slowmode` - Set slowmode for the current channel

#### Statistics System
- `/stats lifetime` - Lifetime server moderation statistics
- `/stats today` - Today's server moderation statistics
- `/stats week` - Weekly statistics
- `/stats date` - Statistics for specific date
- `/stats user` - View user information and statistics

#### Leveling System
- `/level rank` - View your or another user's rank and XP
- `/level leaderboard` - View the server's XP leaderboard
- `/level settings` - Configure leveling system settings (admin only)

#### Reminder System
- `/reminder set` - Create a new reminder with custom time and message
- `/reminder list` - View all your active reminders

#### General Commands
- `/data info` - See what data the bot has stored about you
- `/help` - Access interactive help system
- `/feedback` - Send feedback to the developer
- `/language` - Change the bot's language for your server
- `/systems` - Enable or disable bot systems for your server

#### Rules/Verification System
- `/rules add` - Create rules embeds with verification buttons
- `/rules setup` - Display rules in current channel
- `/rules list` - List all rules embeds
- `/rules remove` - Remove a rules embed

#### Verify Button System
- `/verify-button add` - Add verify button configuration (max 3)
- `/verify-button send` - Send verify button message
- `/verify-button remove` - Remove verify button from current channel

#### Select Roles System
- `/select-roles add` - Add role to selection list with optional description and emoji
- `/select-roles remove` - Remove role from selection list
- `/select-roles send` - Send role selection interface in current channel

#### Timed Roles System
- `/my-roles` - View your active temporary roles and their expiration times
- `/temprole add` - Assign a temporary role to a user for a specified duration
- `/temprole remove` - Remove a temporary role from a user
- `/role-event create` - Create automated role events based on triggers
- `/role-event list` - List and manage all role events

#### Embed Editor System
- `/embed create` - Open the interactive embed editor
- `/embed list` - View all saved embeds
- `/embed load` - Load a saved embed into the editor
- `/embed delete` - Delete a saved embed

## Database

Sloth uses MariaDB for data storage and automatically creates and manages the database that stores:
- Guild configurations
- User warnings and moderation history
- Ticket information
- System activation status
- Activity statistics
- User XP and leveling data
- Member role snapshots for tracking changes
- Reminders and scheduled tasks
- Saved embeds and role events

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
│   ├── TicketPanelCommandListener.java         # Ticket panel creation
│   ├── TicketCreationListener.java             # Ticket creation handling
│   ├── TicketPanelRenderer.java                # Shared panel embed/button rendering
│   ├── DataCommandListener.java                # Stored-data transparency command
│   ├── WarnCommandListener.java                # Warning system
│   ├── ModerationCommandListener.java          # Moderation system
│   ├── LogChannelSlashCommandListener.java     # Logging system
│   ├── StatisticsCommandListener.java          # Statistics tracking
│   ├── JustVerifyButtonCommandListener.java    # Verify button system
│   ├── SelectRolesCommandListener.java         # Select roles system
│   ├── FeedbackCommandListener.java            # Feedback system
│   ├── TimedRolesCommandListener.java          # Timed roles system
│   ├── TimedRoleTriggerListener.java           # Timed roles triggers
│   ├── RoleEventConfigListener.java            # Role event configuration
│   ├── EmbedEditorCommandListener.java         # Embed editor system
│   ├── LanguageCommandListener.java            # Language settings
│   ├── LanguageManager.java                    # Language management
│   ├── LevelingSystemCommandListener.java      # Leveling system
│   ├── ReminderCommandListener.java            # Reminder system
│   ├── SetupWizardListener.java                # Server setup wizard
│   ├── SystemsCommandListener.java             # Systems management
│   ├── GlobalCommandListener.java              # Global command handling
│   ├── GuildEventListener.java                 # Guild event handling
│   ├── MemberRoleTrackingListener.java         # Member role snapshot tracking
│   ├── MemberRoleChangeEvent.java              # Custom role change event
│   ├── OnGuildLeaveListener.java               # Guild leave handling
│   ├── LevelUpEvent.java                       # Level-up event
│   ├── ActionType.java                         # Action type enum
│   ├── RoleEventType.java                      # Role event type enum
│   └── ColorUtil.java                          # Color utility functions
├── src/main/resources/lang/
│   ├── en.json                                 # English translations
│   └── de.json                                 # German translations
├── build.gradle.kts                            # Build configuration
├── .env.example                                 # Environment variables template
├── DATABASE_SETUP.md                           # Database setup guide
├── MIGRATION_SYSTEM.md                         # Migration system documentation
├── PATCHNOTES.md                               # Version history and patch notes
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
