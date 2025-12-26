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

    public AddGuildSlashCommands(Guild guild) {
        this.guild = guild;
        this.databaseHandler = null; // For backwards compatibility
    }

    public AddGuildSlashCommands(Guild guild, DatabaseHandler databaseHandler) {
        this.guild = guild;
        this.databaseHandler = databaseHandler;
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
     * Update guild commands for all systems
     * This method adds commands from all systems to the guild
     * @deprecated Commands are now registered globally. This method is kept for compatibility.
     */
    @Deprecated
    public void updateAllGuildCommands() {
        if (guild == null) {
            System.out.println("Warning: Cannot update guild commands - guild is null. Commands should be registered globally.");
            return;
        }

        List<SlashCommandData> allCommands = getAllCommands();

        // Upsert guild commands to avoid replacing existing commands
        for (SlashCommandData command : allCommands) {
            guild.upsertCommand(command).queue();
        }
    }

    /**
     * Get log channel command with subcommands
     */
    private SlashCommandData getLogChannelCommand() {
        return Commands.slash("log-channel", "Manage the log channel")
                .addSubcommands(
                        new SubcommandData("set", "Set the log channel")
                                .addOption(OptionType.CHANNEL, "channel", "Channel to use as log channel", true),
                        new SubcommandData("get", "Get the current log channel")
                );
    }

    /**
     * Get select roles command with subcommands
     */
    private SlashCommandData getSelectRolesCommand() {
        return Commands.slash("select-roles", "Manage role selection")
                .addSubcommands(
                        new SubcommandData("send", "Send a select roles message in the current channel"),
                        new SubcommandData("add", "Add a role to the select roles message")
                                .addOption(OptionType.ROLE, "role", "Role to add to the select roles message", true)
                                .addOption(OptionType.STRING, "description", "Description for the role in the select menu", false)
                                .addOption(OptionType.STRING, "emoji", "Emoji for the role in the select menu", false),
                        new SubcommandData("remove", "Remove a role from the select roles message")
                                .addOption(OptionType.ROLE, "role", "Role to remove from the select roles message", true)
                );
    }

    /**
     * Get verify button command with subcommands
     */
    private SlashCommandData getVerifyButtonCommand() {
        return Commands.slash("verify-button", "Manage verification buttons")
                .addSubcommands(
                        new SubcommandData("send", "Send a message with a button that gives a role"),
                        new SubcommandData("remove", "Remove the verify button embed from the current channel"),
                        new SubcommandData("add", "Add a verify button configuration (max 3)")
                                .addOption(OptionType.ROLE, "role-to-give", "Role to give members after pressing the verify button", true)
                                .addOption(OptionType.ROLE, "role-to-remove", "Role to remove from members after pressing the verify button", false)
                                .addOption(OptionType.STRING, "button-label", "Name of the button", false)
                                .addOption(OptionType.STRING, "button-emoji", "Emoji for the button", false),
                        new SubcommandData("list", "List all verify button configurations in this server")
                );
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
        return Commands.slash("embed", "Erstelle und verwalte Embeds")
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

    /**
     * Get warn command with subcommands
     */
    // Innerhalb von getWarnCommand() in AddGuildSlashCommands.java

    private SlashCommandData getWarnCommand() {
        return Commands.slash("warn", "Manage warnings")
                .addSubcommands(
                        new SubcommandData("user", "Issue a warning to a user")
                                .addOption(OptionType.USER, "user", "User to warn", true)
                                .addOption(OptionType.STRING, "reason", "Reason for the warning", true)
                                .addOption(OptionType.STRING, "severity", "Severity level (LOW, MEDIUM, HIGH, SEVERE)", false),
                        // NEUER SUBCOMMAND HIER:
                        new SubcommandData("list", "List and manage active warnings of a user")
                                .addOption(OptionType.USER, "user", "The user to check", true),

                        new SubcommandData("settings-set", "Configure warning system settings")
                                // ... deine existierenden Optionen ...
                                .addOption(OptionType.INTEGER, "max_warns", "Maximum warnings before timeout", true)
                                .addOption(OptionType.INTEGER, "timeout_minutes", "Minutes to timeout user when reaching max warns", true)
                                .addOption(OptionType.INTEGER, "warn_time_hours", "Hours after which warnings expire", false),
                        new SubcommandData("settings-get", "View current warning system settings")
                )
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    /**
     * Get moderation command with subcommands
     */
    private SlashCommandData getModerationCommand() {
        return Commands.slash("mod", "Moderation commands")
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
                );
    }

    /**
     * Get ticket command with subcommands
     */
    private SlashCommandData getTicketCommand() {
        return Commands.slash("ticket", "Manage tickets")
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
                );
    }

    // In AddGuildSlashCommands.java

    /**
     * Commands for the Timed Roles system
     */
    private List<SlashCommandData> getTimedRoleCommands() {
        List<SlashCommandData> commands = new ArrayList<>();

        // 1. User Command: /my-roles
        commands.add(Commands.slash("my-roles", "Zeigt an, wie lange deine temporären Rollen noch gültig sind."));

        // 2. Admin Command: /temprole (mit Subcommands)
        SubcommandData addCmd = new SubcommandData("add", "Vergibt eine Rolle für eine bestimmte Zeit")
                .addOption(OptionType.USER, "user", "Der User", true)
                .addOption(OptionType.ROLE, "role", "Die Rolle", true)
                .addOption(OptionType.STRING, "duration", "Dauer (z.B. 30m, 12h, 7d)", true);

        SubcommandData removeCmd = new SubcommandData("remove", "Entfernt eine temporäre Rolle vorzeitig")
                .addOption(OptionType.USER, "user", "Der User", true)
                .addOption(OptionType.ROLE, "role", "Die Rolle", true);

        SlashCommandData tempRoleCmd = Commands.slash("temprole", "Verwaltet temporäre Rollen manuell")
                .addSubcommands(addCmd, removeCmd)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES)); // Nur für Mods sichtbar

        commands.add(tempRoleCmd);

        return commands;
    }

    // In AddGuildSlashCommands.java

    private List<SlashCommandData> getRoleEventCommands() {
        List<SlashCommandData> commands = new ArrayList<>();

        // Subcommand: Create
        SubcommandData createCmd = new SubcommandData("create", "Erstellt ein neues Event")
                .addOption(OptionType.STRING, "name", "Name des Events", true);

        // Subcommand: List (und Editieren via UI)
        SubcommandData listCmd = new SubcommandData("list", "lists all events and opens an editor UI");

        // Hauptcommand
        SlashCommandData eventCmd = Commands.slash("role-event", "Manages automatic role events")
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
        return Commands.slash("systems", "Enable or disable bot systems for this server")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_SERVER));
    }

    /**
     * Returns the list of CORE commands that should be registered GLOBALLY.
     * These commands are always active.
     */
    public List<SlashCommandData> getCoreCommands() {
        List<SlashCommandData> core = new ArrayList<>();
        core.add(getSystemsCommand());
        core.add(Commands.slash("help", "Show help and documentation for Sloth bot"));
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
        }
        return cmds;
    }

    /**
     * Updates the slash commands for the specified guild based on active systems.
     * This replaces all guild-specific commands with the current active set.
     */
    public void updateGuildCommandsFromActiveSystems(String guildId) {
        if (guildId.isBlank() || databaseHandler == null) {
            if (guild == null || databaseHandler == null) return;
            return;
        }
        Guild guild = this.guild.getJDA().getGuildById(guildId);

        java.util.Map<String, Boolean> systems = databaseHandler.getGuildSystemsStatus(guild.getId());
        List<SlashCommandData> activeCommands = new ArrayList<>();

        for (java.util.Map.Entry<String, Boolean> entry : systems.entrySet()) {
            if (entry.getValue()) { // If system is active
                activeCommands.addAll(getCommandsForSystem(entry.getKey()));
            }
        }

        guild.updateCommands().addCommands(activeCommands).queue(
                success -> System.out.println("Guild commands updated based on active systems for guild " + guild.getId()),
                error -> System.err.println("Failed to update guild commands for guild " + guild.getId() + ": " + error.getMessage())
        );
    }

// Und in getAllCommands() hinzufügen:
// allCommands.addAll(getRoleEventCommands()

// NICHT VERGESSEN:
// In der Methode getAllCommands() diese Zeile hinzufügen:
// allCommands.addAll(getTimedRoleCommands());


    /**
     * Get statistics command with subcommands
     */
    private SlashCommandData getStatisticsCommand() {
        return Commands.slash("stats", "View server statistics")
                .addSubcommands(
                        new SubcommandData("lifetime", "View lifetime server moderation statistics"),
                        new SubcommandData("today", "View today's server moderation statistics"),
                        new SubcommandData("week", "View this week's server moderation statistics"),
                        new SubcommandData("date", "View server statistics for a specific date")
                                .addOption(OptionType.STRING, "date", "Date in YYYY-MM-DD format (e.g., 2024-01-15)", true),
                        new SubcommandData("user", "View user information and statistics")
                                .addOption(OptionType.USER, "user", "User to view information for", true)
                                .addOption(OptionType.STRING, "date", "Date in YYYY-MM-DD format to view stats for (optional)", false)
                ).setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS));
    }

    // The old methods are kept for backwards compatibility but now use the new approach
    public void addLogChannelCommands () {
        updateAllGuildCommands();
    }

    public void addWarnCommands() {
        updateAllGuildCommands();
    }

    public void addModerationCommands() {
        updateAllGuildCommands();
    }

    public void addTicketCommands() {
        updateAllGuildCommands();
    }

    public void addStatisticsCommands() {
        updateAllGuildCommands();
    }
}
