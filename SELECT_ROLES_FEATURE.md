# Select Roles Feature

The Select Roles feature allows server administrators to create interactive messages where users can select roles through buttons, dropdown menus, or reactions.

## Commands

### `/create-select-roles`
Creates a new select roles message in the current channel.

**Options:**
- `type` (required): Type of role selection
  - `buttons`: Interactive buttons (max 25 roles)
  - `dropdown`: Dropdown menu (max 25 roles)
  - `reactions`: Manual reaction-based (reactions must be added manually)
- `title` (optional): Title of the embed (default: "Select Your Roles")
- `description` (optional): Description text (default: "Click below to select your roles.")
- `ephemeral` (optional): Whether responses are ephemeral/private (default: false)

**Example:**
```
/create-select-roles type:buttons title:"Choose Your Roles" description:"Select roles to customize your experience" ephemeral:true
```

### `/add-select-role`
Adds a role option to an existing select roles message.

**Options:**
- `message_id` (required): ID of the select roles message
- `role` (required): The role to add
- `label` (optional): Display label for the role (default: role name)
- `description` (optional): Description for the role option (dropdown only)
- `emoji` (optional): Emoji for the role option (e.g., `:star:` or 🌟)

**Example:**
```
/add-select-role message_id:1234567890 role:@Member label:"Member Role" emoji:✅
```

### `/remove-select-role`
Removes a role option from a select roles message.

**Options:**
- `message_id` (required): ID of the select roles message
- `role` (required): The role to remove

**Example:**
```
/remove-select-role message_id:1234567890 role:@Member
```

### `/delete-select-roles-message`
Deletes a select roles message and removes all its configuration.

**Options:**
- `message_id` (required): ID of the select roles message to delete

**Example:**
```
/delete-select-roles-message message_id:1234567890
```

## Setup Guide

### Creating a Button-based Role Selection

1. Create the select roles message:
   ```
   /create-select-roles type:buttons title:"Server Roles"
   ```
   *Note the message ID from the bot's response*

2. Add role options:
   ```
   /add-select-role message_id:<MESSAGE_ID> role:@Announcements label:"📢 Announcements" emoji:📢
   /add-select-role message_id:<MESSAGE_ID> role:@Events label:"🎉 Events" emoji:🎉
   /add-select-role message_id:<MESSAGE_ID> role:@Gaming label:"🎮 Gaming" emoji:🎮
   ```

3. The message will automatically update with buttons as you add roles!

### Creating a Dropdown Role Selection

1. Create the select roles message:
   ```
   /create-select-roles type:dropdown title:"Select Your Interests" description:"Choose one or more interest roles"
   ```

2. Add role options with descriptions:
   ```
   /add-select-role message_id:<MESSAGE_ID> role:@Art description:"Get notified about art events and showcases"
   /add-select-role message_id:<MESSAGE_ID> role:@Music description:"Join music listening parties and discussions"
   /add-select-role message_id:<MESSAGE_ID> role:@Coding description:"Participate in coding challenges and help"
   ```

3. Users can select multiple roles from the dropdown menu!

## Permissions

- All setup commands require **Manage Server** permission
- The bot needs **Manage Roles** permission
- The bot's highest role must be above the roles being assigned

## Features

### Button Type
- Users click buttons to toggle roles on/off
- Each button click toggles the role (add if not present, remove if present)
- Maximum 25 buttons (5 rows × 5 buttons)
- Supports custom emojis and labels

### Dropdown Type
- Users select one or multiple roles from a menu
- Automatically manages role assignment based on selection
- Maximum 25 options in dropdown
- Supports descriptions for each option

### Reactions Type
- Traditional reaction-based role assignment
- Message is created but reactions must be added manually
- Useful for custom reaction setups

### Ephemeral Responses
- When enabled, role assignment confirmations are only visible to the user
- Keeps the channel clean from bot responses
- Set when creating the message with `ephemeral:true`

## Database Structure

The feature uses two database tables:

### `select_roles_messages`
Stores the main select roles message configuration:
- guild_id, message_id, channel_id
- type (buttons/dropdown/reactions)
- title, description
- ephemeral setting

### `select_roles_options`
Stores individual role options:
- message_id (links to select_roles_messages)
- role_id, label, description, emoji

## Notes

- Messages automatically update when roles are added or removed
- Deleted roles are handled gracefully with error messages
- All changes are persisted in the database
- The feature supports multiple select roles messages per server
