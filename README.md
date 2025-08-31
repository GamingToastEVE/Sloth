# Sloth Discord Bot

Sloth is a comprehensive Discord moderation and management bot that provides multiple systems to help server administrators manage their communities effectively. It also features a web dashboard with Discord Single Sign-On for remote server management.

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

### 🌐 Web Dashboard (NEW!)
- **Discord Single Sign-On** - Secure authentication via Discord OAuth2
- **Statistics Viewing** - View detailed server statistics through web interface
- **Ticket Management** - Browse and manage support tickets online
- **Server Configuration** - Configure bot settings through user-friendly web forms
- **Real-time Data** - Access to live bot data and settings

## Setup

### 1. Clone the Repository
```bash
git clone https://github.com/GamingToastEVE/Delta.git
cd Delta
```

### 2. Configure Environment Variables
Create a `.env` file in the root directory with your Discord bot token and OAuth2 credentials:
```env
TOKEN=your_discord_bot_token_here

# For web dashboard functionality
DISCORD_CLIENT_ID=your_discord_application_client_id
DISCORD_CLIENT_SECRET=your_discord_application_client_secret
```

To set up Discord OAuth2:
1. Go to [Discord Developer Portal](https://discord.com/developers/applications)
2. Create a new application or use existing one
3. Copy the Client ID and Client Secret
4. In OAuth2 settings, add redirect URI: `http://localhost:8080/login/oauth2/code/discord`

### 3. Build the Project
```bash
./gradlew build
```

### 4. Run the Bot
```bash
./gradlew run
```

The application will start both:
- Discord bot (connects to Discord)
- Web dashboard (available at http://localhost:8080)

## Usage

Sloth uses a modular system approach where server administrators can activate only the systems they need.

### Discord Bot Usage

Use the `/add-system` command to activate different systems on your server:

```
/add-system system:Log Channel System
/add-system system:Warning System  
/add-system system:Ticket System
/add-system system:Moderation System
```

### Web Dashboard Usage

1. **Access the Dashboard**: Navigate to `http://localhost:8080` in your browser
2. **Login**: Click "Login with Discord" to authenticate via Discord OAuth2
3. **Select Server**: Enter your Discord server's Guild ID to manage it
4. **Manage Server**: Use the web interface to:
   - View detailed statistics (today, weekly, or custom date ranges)
   - Browse and manage support tickets
   - Configure ticket system settings
   - Adjust warning system parameters

**Finding Your Guild ID**: 
In Discord, enable Developer Mode in settings, then right-click your server name and select "Copy Server ID"

### System Management

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

This project is developed by gamingtoasti. Please refer to the Terms of Service for usage guidelines.

## Support

For support, please create a ticket using the bot's ticket system or open an issue in this repository.
