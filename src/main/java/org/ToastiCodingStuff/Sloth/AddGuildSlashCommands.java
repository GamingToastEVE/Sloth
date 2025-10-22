package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

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
        allCommands.addAll(getLogChannelCommands());
        allCommands.addAll(getWarnCommands());
        allCommands.addAll(getTicketCommands());
        allCommands.addAll(getModerationCommands());
        allCommands.addAll(getStatisticsCommands());
        allCommands.addAll(getRuleCommands());
        allCommands.addAll(getJustVerifyButtonCommand());

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
     * Get log channel commands without updating guild
     */
    private List<SlashCommandData> getLogChannelCommands() {
        List<SlashCommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("set-log-channel", "Sets the log channel.")
                .addOption(OptionType.CHANNEL, "logchannel", "Specified Channel will be log channel", true));
        commands.add(Commands.slash("get-log-channel", "gets the log channel"));
        return commands;
    }

    private List<SlashCommandData> getJustVerifyButtonCommand() {
        List<SlashCommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("send-just-verify-button", "Sends a message with a button that gives a role"));
        commands.add(Commands.slash("remove-just-verify-button", "Removes the Just Verify Button embed from the current channel"));
        commands.add(Commands.slash("add-just-verify-button", "Adds a Just Verify Button embed to the database (max 3)")
                .addOption(OptionType.ROLE, "role-to-give", "Role to give members after pressing the verify button", true)
                .addOption(OptionType.ROLE, "role-to-remove", "Role to remove from members after pressing the verify button", false)
                .addOption(OptionType.STRING, "button-label", "Name of the button", false)
                .addOption(OptionType.STRING, "button-emoji", "Emoji for the button", false));
        return commands;
    }

    private List<SlashCommandData> getSelectRolesCommands () {
        List<SlashCommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("create-select-roles", "Creates a select roles message in the current channel")
                .addOption(OptionType.STRING, "type", "Type of select roles (reactions, dropdown or buttons)", true)
                .addOption(OptionType.STRING, "title", "Title of the embed", false)
                .addOption(OptionType.STRING, "description", "Description of the embed", false)
                .addOption(OptionType.BOOLEAN, "ephemeral", "Whether the role selection is ephemeral", false));
        commands.add(Commands.slash("add-select-role", "Adds a role option to the select roles message")
                .addOption(OptionType.INTEGER, "message_id", "ID of the select roles message", true)
                .addOption(OptionType.ROLE, "role", "Role to add as an option", true)
                .addOption(OptionType.STRING, "label", "Label for the role option", false)
                .addOption(OptionType.STRING, "description", "Description for the role option", false)
                .addOption(OptionType.STRING, "emoji", "Emoji for the role option", false));
        commands.add(Commands.slash("remove-select-role", "Removes a role option from the select roles message")
                .addOption(OptionType.INTEGER, "message_id", "ID of the select roles message", true)
                .addOption(OptionType.ROLE, "role", "Role to remove from options", true));
        commands.add(Commands.slash("delete-select-roles-message", "Deletes a select roles message and its configuration")
                .addOption(OptionType.INTEGER, "message_id", "ID of the select roles message to delete", true));
        commands.add(Commands.slash("remove-select-role", "Removes a role option from the select roles message")
                .addOption(OptionType.ROLE, "role", "Role to remove from options", true));
        return commands;
    }

    /**
     * Get Rule Commands without updating guild
     */
    private List<SlashCommandData> getRuleCommands() {
        List<SlashCommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("setup-rules", "Sets up the rules in the current channel"));
        commands.add(Commands.slash("add-rules-embed", "Adds a rules embed to the database (max 3)")
                .addOption(OptionType.ROLE, "role_to_give", "Role to give members after pressing the verify button", true)
                .addOption(OptionType.STRING, "color", "Color of the embed (e.g., green)", false));
        commands.add(Commands.slash("list-rules-embeds", "Lists all rules embeds in this server"));
        commands.add(Commands.slash("remove-rules-embed", "Removes a rules embed from the database")
                .addOption(OptionType.INTEGER, "embed_id", "ID of the embed to remove", true));
        return commands;
    }

    /**
     * Get warn commands without updating guild
     */
    private List<SlashCommandData> getWarnCommands() {
        List<SlashCommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("warn", "Issue a warning to a user")
                .addOption(OptionType.USER, "user", "User to warn", true)
                .addOption(OptionType.STRING, "reason", "Reason for the warning", true)
                .addOption(OptionType.STRING, "severity", "Severity level (LOW, MEDIUM, HIGH, SEVERE)", false));
        commands.add(Commands.slash("set-warn-settings", "Configure warning system settings")
                .addOption(OptionType.INTEGER, "max_warns", "Maximum warnings before timeout", true)
                .addOption(OptionType.INTEGER, "timeout_minutes", "Minutes to timeout user when reaching max warns", true)
                .addOption(OptionType.INTEGER, "warn_time_hours", "Hours after which warnings expire", false));
        commands.add(Commands.slash("get-warn-settings", "View current warning system settings"));
        return commands;
    }

    /**
     * Get moderation commands without updating guild
     */
    private List<SlashCommandData> getModerationCommands() {
        List<SlashCommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("kick", "Kick a user from the server")
                .addOption(OptionType.USER, "user", "User to kick", true)
                .addOption(OptionType.STRING, "reason", "Reason for the kick", false));
        commands.add(Commands.slash("ban", "Ban a user from the server")
                .addOption(OptionType.USER, "user", "User to ban", true)
                .addOption(OptionType.STRING, "reason", "Reason for the ban", false));
        commands.add(Commands.slash("unban", "Unban a user from the server")
                .addOption(OptionType.STRING, "userid", "User ID to unban", true)
                .addOption(OptionType.STRING, "reason", "Reason for the unban", false));
        commands.add(Commands.slash("timeout", "Timeout a user for a specified duration")
                .addOption(OptionType.USER, "user", "User to timeout", true)
                .addOption(OptionType.INTEGER, "minutes", "Duration in minutes (max 40320 = 28 days)", true)
                .addOption(OptionType.STRING, "reason", "Reason for the timeout", false));
        commands.add(Commands.slash("untimeout", "Remove timeout from a user")
                .addOption(OptionType.USER, "user", "User to remove timeout from", true)
                .addOption(OptionType.STRING, "reason", "Reason for removing timeout", false));
        commands.add(Commands.slash("purge", "Delete multiple messages from the channel")
                .addOption(OptionType.INTEGER, "amount", "Number of messages to delete (1-100)", true)
                .addOption(OptionType.USER, "user", "Only delete messages from this user", false));
        commands.add(Commands.slash("slowmode", "Set slowmode for the current channel")
                .addOption(OptionType.INTEGER, "seconds", "Slowmode delay in seconds (0 to disable, max 21600)", true));
        return commands;
    }

    /**
     * Get ticket commands without updating guild
     */
    private List<SlashCommandData> getTicketCommands() {
        List<SlashCommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("ticket-setup", "Configure the ticket system for this server")
                .addOption(OptionType.CHANNEL, "category", "Category for ticket channels", true)
                .addOption(OptionType.CHANNEL, "channel", "Channel for ticket creation panel", true)
                .addOption(OptionType.ROLE, "support-role", "Role that can manage tickets", false));
                //.addOption(OptionType.BOOLEAN, "transcript_enabled", "Enable ticket transcripts", false));
        commands.add(Commands.slash("ticket-panel", "Create a ticket creation panel in current channel"));
        commands.add(Commands.slash("close-ticket", "Close the current ticket")
                .addOption(OptionType.STRING, "reason", "Reason for closing the ticket", false));
        commands.add(Commands.slash("assign-ticket", "Assign current ticket to a staff member")
                .addOption(OptionType.USER, "staff", "Staff member to assign ticket to", true));
        commands.add(Commands.slash("set-ticket-priority", "Change the priority of the current ticket")
                .addOptions(new OptionData(OptionType.STRING, "priority", "Priority level", true)
                        .addChoice("Low", "LOW")
                        .addChoice("Medium", "MEDIUM")
                        .addChoice("High", "HIGH")
                        .addChoice("Urgent", "URGENT")));
        commands.add(Commands.slash("ticket-info", "Get information about the current ticket"));
        //commands.add(Commands.slash("ticket-transcript", "Generate a transcript of the current ticket"));
        return commands;
    }


    /**
     * Get statistics commands without updating guild
     */
    private List<SlashCommandData> getStatisticsCommands() {
        List<SlashCommandData> commands = new ArrayList<>();
        commands.add(Commands.slash("stats", "View lifetime server moderation statistics"));
        commands.add(Commands.slash("stats-today", "View today's server moderation statistics"));
        commands.add(Commands.slash("stats-week", "View this week's server moderation statistics"));
        commands.add(Commands.slash("stats-date", "View server statistics for a specific date")
                .addOption(OptionType.STRING, "date", "Date in YYYY-MM-DD format (e.g., 2024-01-15)", true));
        commands.add(Commands.slash("stats-user", "View user information and statistics")
                .addOption(OptionType.USER, "user", "User to view information for", true)
                .addOption(OptionType.STRING, "date", "Date in YYYY-MM-DD format to view stats for (optional)", false));
        return commands;
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
