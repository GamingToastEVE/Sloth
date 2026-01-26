package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.ArrayList;
import java.util.List;

public class AddGuildSlashCommands {
    private final Guild guild;
    private final DatabaseHandler databaseHandler;

    public AddGuildSlashCommands(Guild guild, DatabaseHandler databaseHandler) {
        this.guild = guild;
        this.databaseHandler = databaseHandler;
    }

    // ==================== LANGUAGE HELPER METHODS ====================

    /**
     * Get a translated command description based on guild language
     */
    private String cmd(String key) {
        if (guild == null) return getDefaultCommandText(key);

        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            return lang.get(guild.getId(), "commands." + key);
        }
        return getDefaultCommandText(key);
    }

    /**
     * Default English command descriptions as fallback
     */
    private String getDefaultCommandText(String key) {
        return switch (key) {
            case "help" -> "Show help and documentation for Sloth bot";
            case "language" -> "Change the bot's language for this server";
            case "systems" -> "Enable or disable bot systems";
            case "level" -> "Leveling system commands";
            case "level_rank" -> "View your or another user's rank";
            case "level_leaderboard" -> "View the server leaderboard";
            case "level_settings" -> "Configure leveling system settings";
            case "mod" -> "Moderation commands";
            case "mod_kick" -> "Kick a user from the server";
            case "mod_ban" -> "Ban a user from the server";
            case "mod_unban" -> "Unban a user";
            case "mod_timeout" -> "Timeout a user";
            case "mod_untimeout" -> "Remove timeout from a user";
            case "mod_purge" -> "Delete multiple messages";
            case "mod_slowmode" -> "Set slowmode for a channel";
            case "warn" -> "Warning system commands";
            case "warn_user" -> "Warn a user";
            case "warn_list" -> "List warnings for a user";
            case "ticket" -> "Ticket system commands";
            case "ticket_setup" -> "Setup the ticket system";
            case "ticket_panel" -> "Send a ticket panel";
            case "ticket_close" -> "Close a ticket";
            case "stats" -> "View server statistics";
            case "reminder" -> "Set and manage reminders";
            case "reminder_set" -> "Set a new reminder";
            case "reminder_list" -> "List your reminders";
            case "reminder_remove" -> "Remove a reminder";
            case "temprole" -> "Manage temporary roles";
            case "temprole_add" -> "Add a temporary role to a user";
            case "temprole_remove" -> "Remove a temporary role from a user";
            case "my_roles" -> "View your temporary roles";
            case "select_roles" -> "Role selection menu commands";
            case "verify_button" -> "Verification button commands";
            case "embed" -> "Create and edit embeds";
            case "log_channel" -> "Configure the log channel";
            case "role_event" -> "Configure role events";
            default -> key;
        };
    }

    /**
     * Get all commands from all systems - used for global command registration
     */
    public List<SlashCommandData> getAllCommands() {
        List<SlashCommandData> allCommands = new ArrayList<>();

        // Add commands for all systems
        allCommands.add(getLogChannelCommand());
        allCommands.add(getWarnCommand());
        allCommands.add(getTicketCommand());
        allCommands.add(getModerationCommand());
        allCommands.add(getStatisticsCommand());
        allCommands.add(getVerifyButtonCommand());
        allCommands.addAll(getFeedbackCommands());
        allCommands.add(getSelectRolesCommand());
        allCommands.addAll(getTimedRoleCommands());
        allCommands.addAll(getRoleEventCommands());
        allCommands.add(getEmbedEditorCommand());
        return allCommands;
    }

    /**
     * Get log channel command with subcommands
     */
    private SlashCommandData getLogChannelCommand() {
        return Commands.slash("log-channel", cmd("log_channel"))
                .addSubcommands(
                        new SubcommandData("set", "Set the log channel")
                                .addOption(OptionType.CHANNEL, "channel", "Channel to use as log channel", true),
                        new SubcommandData("get", "Get the current log channel")
                ).setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    /**
     * Get select roles command with subcommands
     */
    private SlashCommandData getSelectRolesCommand() {
        return Commands.slash("select-roles", cmd("select_roles"))
                .addSubcommands(
                        new SubcommandData("send", "Send a select roles message in the current channel"),
                        new SubcommandData("add", "Add a role to the select roles message")
                                .addOption(OptionType.ROLE, "role", "Role to add to the select roles message", true)
                                .addOption(OptionType.STRING, "description", "Description for the role in the select menu", false)
                                .addOption(OptionType.STRING, "emoji", "Emoji for the role in the select menu", false),
                        new SubcommandData("remove", "Remove a role from the select roles message")
                                .addOption(OptionType.ROLE, "role", "Role to remove from the select roles message", true)
                ).setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    /**
     * Get verify button command with subcommands
     */
    private SlashCommandData getVerifyButtonCommand() {
        return Commands.slash("verify-button", cmd("verify_button"))
                .addSubcommands(
                        new SubcommandData("send", "Send a message with a button that gives a role"),
                        new SubcommandData("remove", "Remove the verify button embed from the current channel"),
                        new SubcommandData("add", "Add a verify button configuration (max 3)")
                                .addOption(OptionType.ROLE, "role-to-give", "Role to give members after pressing the verify button", true)
                                .addOption(OptionType.ROLE, "role-to-remove", "Role to remove from members after pressing the verify button", false)
                                .addOption(OptionType.STRING, "button-label", "Name of the button", false)
                                .addOption(OptionType.STRING, "button-emoji", "Emoji for the button", false),
                        new SubcommandData("list", "List all verify button configurations in this server")
                ).setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    private List<SlashCommandData> getFeedbackCommands() {
        List<SlashCommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("feedback", "Send feedback to the bot developer")
                .addOption(OptionType.STRING, "message", "Your feedback message", true));
        return commands;
    }

    /**
     * Get rules command with subcommands
     */
    @Deprecated
    private SlashCommandData getRulesCommand() {
        return Commands.slash("rules", "Manage server rules")
                .addSubcommands(
                        new SubcommandData("setup", "Set up the rules in the current channel"),
                        new SubcommandData("add", "Add a rules embed to the database (max 3)")
                                .addOption(OptionType.ROLE, "role_to_give", "Role to give members after pressing the verify button", true)
                                .addOption(OptionType.STRING, "color", "Color of the embed (e.g., green)", false),
                        new SubcommandData("list", "List all rules embeds in this server"),
                        new SubcommandData("remove", "Remove a rules embed from the database")
                                .addOption(OptionType.INTEGER, "embed_id", "ID of the embed to remove", true)
                );
    }

    // Ersetze die alte getEmbedEditorCommand Methode:
    private SlashCommandData getEmbedEditorCommand() {
        return Commands.slash("embed", cmd("embed"))
                .addSubcommands(
                        new SubcommandData("create", "Starts Embed-Editor"),
                        new SubcommandData("list", "Shows all saved Embeds"),
                        new SubcommandData("load", "Loads a saved Embed into the editor")
                                .addOption(OptionType.STRING, "name", "Name of the embed", true, true),
                        new SubcommandData("delete", "Deletes a saved Embed")
                                .addOption(OptionType.STRING, "name", "Name of the embed", true, true)
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    private SlashCommandData getReminderCommand() {
        return Commands.slash("reminder", cmd("reminder"))
                .addSubcommands(
                        new SubcommandData("set", "Create a new reminder")
                                .addOption(OptionType.STRING, "time", "Time until i remind you (10m, 1h, 2d)", true)
                                .addOption(OptionType.STRING, "title", "Title of the reminder", true)
                                .addOption(OptionType.STRING, "message", "What should I remind you of?", false)
                                .addOption(OptionType.BOOLEAN, "dm", "DM? Standard: YES", false),
                        new SubcommandData("list", "Shows all active reminders")
                );
    }

    /**
     * Get warn command with subcommands
     */
    // Innerhalb von getWarnCommand() in AddGuildSlashCommands.java

    private SlashCommandData getWarnCommand() {
        return Commands.slash("warn", cmd("warn"))
                .addSubcommands(
                        new SubcommandData("user", "Issue a warning to a user")
                                .addOption(OptionType.USER, "user", "User to warn", true)
                                .addOption(OptionType.STRING, "reason", "Reason for the warning", true)
                                .addOption(OptionType.ATTACHMENT, "evidence", "Evidence attachment (optional)", false)
                                .addOption(OptionType.STRING, "severity", "Severity level (LOW, MEDIUM, HIGH, SEVERE)", false),
                        new SubcommandData("list", "List and manage active warnings of a user")
                                .addOption(OptionType.USER, "user", "The user to check", true),

                        new SubcommandData("settings-set", "Configure warning system settings")
                                .addOption(OptionType.INTEGER, "max_warns", "Maximum warnings before timeout", true)
                                .addOption(OptionType.INTEGER, "timeout_minutes", "Minutes to timeout user when reaching max warns", true)
                                .addOption(OptionType.INTEGER, "warn_time_hours", "Hours after which warnings expire", false),
                        new SubcommandData("settings-get", "View current warning system settings")/*,
                        new SubcommandData("note", "Track a user without issuing a warning")
                                .addOption(OptionType.USER, "user", "User to note", true)
                                .addOption(OptionType.STRING, "note", "Note content", true)
                                .addOption(OptionType.ATTACHMENT, "evidence", "Evidence attachment (optional)", false)*/
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    /**
     * Get moderation command with subcommands
     */
    private SlashCommandData getModerationCommand() {
        return Commands.slash("mod", cmd("mod"))
                .addSubcommands(
                        new SubcommandData("kick", "Kick a user from the server")
                                .addOption(OptionType.USER, "user", "User to kick", true)
                                .addOption(OptionType.STRING, "reason", "Reason for the kick", false),
                        new SubcommandData("ban", "Ban a user from the server")
                                .addOption(OptionType.USER, "user", "User to ban", true)
                                .addOption(OptionType.STRING, "reason", "Reason for the ban", false),
                        new SubcommandData("unban", "Unban a user from the server")
                                .addOption(OptionType.STRING, "userid", "User ID to unban", true)
                                .addOption(OptionType.STRING, "reason", "Reason for the unban", false),
                        new SubcommandData("timeout", "Timeout a user for a specified duration")
                                .addOption(OptionType.USER, "user", "User to timeout", true)
                                .addOption(OptionType.INTEGER, "minutes", "Duration in minutes (max 40320 = 28 days)", true)
                                .addOption(OptionType.STRING, "reason", "Reason for the timeout", false),
                        new SubcommandData("untimeout", "Remove timeout from a user")
                                .addOption(OptionType.USER, "user", "User to remove timeout from", true)
                                .addOption(OptionType.STRING, "reason", "Reason for removing timeout", false),
                        new SubcommandData("purge", "Delete multiple messages from the channel")
                                .addOption(OptionType.INTEGER, "amount", "Number of messages to delete (1-100)", true)
                                .addOption(OptionType.USER, "user", "Only delete messages from this user", false),
                        new SubcommandData("slowmode", "Set slowmode for the current channel")
                                .addOption(OptionType.INTEGER, "seconds", "Slowmode delay in seconds (0 to disable, max 21600)", true)
                ).setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    /**
     * Get ticket command with subcommands
     */
    private SlashCommandData getTicketCommand() {
        return Commands.slash("ticket", cmd("ticket"))
                .addSubcommands(
                        new SubcommandData("setup", "Configure the ticket system for this server")
                                .addOption(OptionType.CHANNEL, "category", "Category for ticket channels", true)
                                .addOption(OptionType.CHANNEL, "channel", "Channel for ticket creation panel", true)
                                .addOption(OptionType.ROLE, "support-role", "Role that can manage tickets", false),
                        new SubcommandData("panel", "Create a ticket creation panel in current channel"),
                        new SubcommandData("config", "Set custom title and description for the ticket panel")
                                .addOption(OptionType.STRING, "title", "Title for the ticket panel embed", true)
                                .addOption(OptionType.STRING, "description", "Description for the ticket panel embed", true),
                        new SubcommandData("close", "Close the current ticket")
                                .addOption(OptionType.STRING, "reason", "Reason for closing the ticket", false),
                        new SubcommandData("assign", "Assign current ticket to a staff member")
                                .addOption(OptionType.USER, "staff", "Staff member to assign ticket to", true),
                        new SubcommandData("priority", "Change the priority of the current ticket")
                                .addOptions(new OptionData(OptionType.STRING, "priority", "Priority level", true)
                                        .addChoice("Low", "LOW")
                                        .addChoice("Medium", "MEDIUM")
                                        .addChoice("High", "HIGH")
                                        .addChoice("Urgent", "URGENT")),
                        new SubcommandData("info", "Get information about the current ticket")
                ).setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    // In AddGuildSlashCommands.java

    /**
     * Commands for the Timed Roles system
     */
    private List<SlashCommandData> getTimedRoleCommands() {
        List<SlashCommandData> commands = new ArrayList<>();

        // 1. User Command: /my-roles
        commands.add(Commands.slash("my-roles", cmd("my_roles")));

        // 2. Admin Command: /temprole (mit Subcommands)
        SubcommandData addCmd = new SubcommandData("add", "Add a temporary role to a user")
                .addOption(OptionType.USER, "user", "The user", true)
                .addOption(OptionType.ROLE, "role", "The role", true)
                .addOption(OptionType.STRING, "duration", "Duration (e.g. 30m, 12h, 7d)", true);

        SubcommandData removeCmd = new SubcommandData("remove", "Remove a temporary role early")
                .addOption(OptionType.USER, "user", "The user", true)
                .addOption(OptionType.ROLE, "role", "The role", true);

        SlashCommandData tempRoleCmd = Commands.slash("temprole", cmd("temprole"))
                .addSubcommands(addCmd, removeCmd)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES)); // Nur für Mods sichtbar

        commands.add(tempRoleCmd);

        return commands;
    }

    // In AddGuildSlashCommands.java

    private List<SlashCommandData> getRoleEventCommands() {
        List<SlashCommandData> commands = new ArrayList<>();

        // Subcommand: Create
        SubcommandData createCmd = new SubcommandData("create", "Create a new event")
                .addOption(OptionType.STRING, "name", "Name of the event", true);

        // Subcommand: List (und Editieren via UI)
        SubcommandData listCmd = new SubcommandData("list", "Lists all events and opens an editor UI");

        // Hauptcommand
        SlashCommandData eventCmd = Commands.slash("role-event", cmd("role_event"))
                .addSubcommands(createCmd, listCmd)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));

        commands.add(eventCmd);
        return commands;
    }

    //
// Add these methods

    /**
     * Get the /systems control command
     */
    private SlashCommandData getSystemsCommand() {
        return Commands.slash("systems", cmd("systems"))
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    /**
     * Returns the list of CORE commands that should be registered GLOBALLY.
     * These commands are always active.
     */
    public List<SlashCommandData> getCoreCommands() {
        List<SlashCommandData> core = new ArrayList<>();
        core.add(getSystemsCommand());
        core.add(Commands.slash("help", cmd("help")));
        core.add(Commands.slash("language", cmd("language"))
                .setDefaultPermissions(net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MANAGE_SERVER)));
        core.add(getFeedbackCommands().get(0));
        return core;
    }

    /**
     * Returns the list of commands associated with a specific system.
     */
    public List<SlashCommandData> getCommandsForSystem(String systemName) {
        List<SlashCommandData> cmds = new ArrayList<>();
        switch (systemName.toLowerCase()) {
            case "log-channel": cmds.add(getLogChannelCommand()); break;
            case "warn": cmds.add(getWarnCommand()); break;
            case "ticket": cmds.add(getTicketCommand()); break;
            case "mod": cmds.add(getModerationCommand()); break;
            case "stats": cmds.add(getStatisticsCommand()); break;
            case "verify-button": cmds.add(getVerifyButtonCommand()); break;
            case "select-roles": cmds.add(getSelectRolesCommand()); break;
            case "temprole": cmds.addAll(getTimedRoleCommands()); break;
            case "role-event": cmds.addAll(getRoleEventCommands()); break;
            case "embed": cmds.add(getEmbedEditorCommand()); break;
            case "reminders": cmds.add(getReminderCommand()); break;
            case "leveling": cmds.add(getLevelingCommands()); break;
        }
        return cmds;
    }

    /**
     * Updates the slash commands for the specified guild based on active systems.
     * This replaces all guild-specific commands with the current active set.
     */
    public void updateGuildCommandsFromActiveSystems(String guildId) {
        Guild guild;
        if (guildId.isBlank() || databaseHandler == null) {
            guild = this.guild;
            if (guild == null || databaseHandler == null) {
                System.out.println("No guild or databasehandler found.");
                return;
            }
        } else {
            guild = this.guild.getJDA().getGuildById(guildId);
            if (guild == null) {
                System.err.println("Cannot update guild commands - guild not found: " + guildId);
                return;
            }
        }


        java.util.Map<String, Boolean> systems = databaseHandler.getGuildSystemsStatus(guild.getId());
        List<SlashCommandData> activeCommands = new ArrayList<>();

        for (java.util.Map.Entry<String, Boolean> entry : systems.entrySet()) {
            if (entry.getValue()) { // If system is active
                System.out.println("Adding commands for active system: " + entry.getKey());
                activeCommands.addAll(getCommandsForSystem(entry.getKey()));
            }
        }

        guild.updateCommands().addCommands(activeCommands).queue(
                success -> System.out.println("Guild commands updated based on active systems for guild " + guild.getId()),
                error -> System.err.println("Failed to update guild commands for guild " + guild.getId() + ": " + error.getMessage())
        );
    }

    /**
     * Get statistics command with subcommands
     */
    private SlashCommandData getStatisticsCommand() {
        return Commands.slash("stats", cmd("stats"))
                .addSubcommands(
                        new SubcommandData("today", "View today's server moderation statistics"),
                        new SubcommandData("week", "View this week's server moderation statistics"),
                        new SubcommandData("date", "View server statistics for a specific date")
                                .addOption(OptionType.STRING, "date", "Date in YYYY-MM-DD format (e.g., 2024-01-15)", true),
                        new SubcommandData("user", "View user information and statistics")
                                .addOption(OptionType.USER, "user", "User to view information for", true)
                                .addOption(OptionType.STRING, "date", "Date in YYYY-MM-DD format to view stats for (optional)", false)
                ).setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    private SlashCommandData getLevelingCommands() {
        return Commands.slash("leveling", cmd("level"))
                .addSubcommands(
                        new SubcommandData("settings", "View or change leveling settings"),
                        new SubcommandData("rank", "View your current level and XP")
                                .addOption(OptionType.USER, "user", "User to view rank for (optional)", false),
                        new SubcommandData("rewards", "View or set level-up rewards"),
                        new SubcommandData("sync-role-levels", "Sync role levels with current member roles"),
                        new SubcommandData("leaderboard", "View the server's leveling leaderboard")
                );
    }
}
