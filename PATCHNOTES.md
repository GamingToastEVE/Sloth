# Sloth Bot - Patch Notes

This document contains the version history and changelog for Sloth Bot.

---

## Version 1.0 - Initial Release

### 🎨 New Features

#### Embed Editor System
- **Interactive Embed Editor**: Create beautiful custom embeds with a visual editor interface
- **Full Customization**: Edit title, description, footer, author, color, images, thumbnail, and fields
- **Save & Load**: Save embeds to database and load them later for reuse
- **Verify Button Integration**: Optionally attach verify buttons when publishing embeds
- **Commands**:
  - `/embed create` - Open the interactive embed editor
  - `/embed list` - View all saved embeds
  - `/embed load` - Load a saved embed into the editor
  - `/embed delete` - Delete a saved embed

#### Warning Management
- **Warning List**: New `/warn list` command to view and manage active warnings
- **Interactive Dropdown**: Delete warnings directly from an interactive dropdown menu
- **Warning Details**: View warning ID, date, reason, and moderator for each warning

#### Timed Roles System
- **Temporary Roles**: Assign roles that automatically expire after a set duration
- **Role Events**: Create automated role events based on triggers
- **User View**: Users can check their active temporary roles with `/my-roles`
- **Commands**:
  - `/my-roles` - View your active temporary roles
  - `/temprole add` - Assign a temporary role to a user
  - `/temprole remove` - Remove a temporary role
  - `/role-event create` - Create automated role events
  - `/role-event list` - List and manage all role events

#### Select Roles System
- **Self-Assignable Roles**: Allow users to self-assign roles
- **Role Menus**: Create role selection menus with descriptions and emojis
- **Commands**:
  - `/select-roles add` - Add role to selection list
  - `/select-roles remove` - Remove role from selection list
  - `/select-roles send` - Send role selection interface

### 🛠️ Core Systems

#### Moderation System
- Kick, ban, unban users
- Timeout and untimeout functionality
- Message purging
- Slowmode management

#### Warning System
- Issue warnings with severity levels (LOW, MEDIUM, HIGH, SEVERE)
- Automatic timeout when max warnings reached
- Warning expiration
- Warning history tracking

#### Ticket System
- Create support ticket channels
- Staff assignment and priorities
- Custom ticket panel configuration

#### Log Channel System
- Dedicated logging channels
- Track server events and activities
- Comprehensive audit trail

#### Statistics System
- Lifetime, daily, and weekly statistics
- User engagement metrics
- Date-specific statistics

#### Verify Button System
- Custom verification buttons
- Assign/remove roles on verification
- Multiple configurations (max 3)

#### Rules/Verification System
- Custom rules embeds
- Verification buttons
- Role assignment upon verification

### 🔧 Technical Improvements

#### Database Migration System
- Automatic schema updates
- Version tracking
- Rollback support

#### MariaDB Integration
- Migrated from SQLite to MariaDB
- Connection pooling with HikariCP
- Improved performance and reliability

---

## Future Updates

Stay tuned for more features and improvements! For support or to suggest new features:
- **Support Server**: https://discord.gg/dQT53fD8M5
- **Discord**: gamingtoasti
- **Donate**: https://ko-fi.com/gamingtoast27542
