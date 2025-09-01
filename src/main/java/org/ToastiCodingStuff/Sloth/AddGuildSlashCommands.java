package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class AddGuildSlashCommands {
    private final Guild guild;

    public AddGuildSlashCommands(Guild guild) {
        this.guild = guild;
    }

    public void addLogChannelCommands () {
        guild.updateCommands().addCommands(
                Commands.slash("set-log-channel", "Sets the log channel.")
                        .addOption(OptionType.CHANNEL, "logchannel", "Specified Channel will be log channel", true),
                Commands.slash("get-log-channel", "gets the log channel")
        ).queue();
    }

    public void addWarnCommands() {
        guild.updateCommands().addCommands(
                Commands.slash("warn", "Issue a warning to a user")
                        .addOption(OptionType.USER, "user", "User to warn", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the warning", true)
                        .addOption(OptionType.STRING, "severity", "Severity level (LOW, MEDIUM, HIGH, SEVERE)", false),
                Commands.slash("set-warn-settings", "Configure warning system settings")
                        .addOption(OptionType.INTEGER, "max_warns", "Maximum warnings before timeout", true)
                        .addOption(OptionType.INTEGER, "timeout_minutes", "Minutes to timeout user when reaching max warns", true)
                        .addOption(OptionType.INTEGER, "warn_time_hours", "Hours after which warnings expire", false),
                Commands.slash("get-warn-settings", "View current warning system settings")
        ).queue();
    }

    public void addModerationCommands() {
        guild.updateCommands().addCommands(
                Commands.slash("kick", "Kick a user from the server")
                        .addOption(OptionType.USER, "user", "User to kick", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the kick", false),
                Commands.slash("ban", "Ban a user from the server")
                        .addOption(OptionType.USER, "user", "User to ban", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the ban", false),
                Commands.slash("unban", "Unban a user from the server")
                        .addOption(OptionType.STRING, "userid", "User ID to unban", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the unban", false),
                Commands.slash("timeout", "Timeout a user for a specified duration")
                        .addOption(OptionType.USER, "user", "User to timeout", true)
                        .addOption(OptionType.INTEGER, "minutes", "Duration in minutes (max 40320 = 28 days)", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the timeout", false),
                Commands.slash("untimeout", "Remove timeout from a user")
                        .addOption(OptionType.USER, "user", "User to remove timeout from", true)
                        .addOption(OptionType.STRING, "reason", "Reason for removing timeout", false),
                Commands.slash("purge", "Delete multiple messages from the channel")
                        .addOption(OptionType.INTEGER, "amount", "Number of messages to delete (1-100)", true)
                        .addOption(OptionType.USER, "user", "Only delete messages from this user", false),
                Commands.slash("slowmode", "Set slowmode for the current channel")
                        .addOption(OptionType.INTEGER, "seconds", "Slowmode delay in seconds (0 to disable, max 21600)", true)
        ).queue();
    }

    public void addTicketCommands() {
        guild.updateCommands().addCommands(
                Commands.slash("ticket-setup", "Configure the ticket system for this server")
                        .addOption(OptionType.CHANNEL, "category", "Category for ticket channels", true)
                        .addOption(OptionType.CHANNEL, "channel", "Channel for ticket creation panel", true)
                        .addOption(OptionType.ROLE, "support_role", "Role that can manage tickets", false)
                        .addOption(OptionType.BOOLEAN, "transcript_enabled", "Enable ticket transcripts", false),
                Commands.slash("ticket-panel", "Create a ticket creation panel in current channel"),
                Commands.slash("close-ticket", "Close the current ticket")
                        .addOption(OptionType.STRING, "reason", "Reason for closing the ticket", false),
                Commands.slash("assign-ticket", "Assign current ticket to a staff member")
                        .addOption(OptionType.USER, "staff", "Staff member to assign ticket to", true),
                Commands.slash("set-ticket-priority", "Change the priority of the current ticket")
                        .addOptions(new OptionData(OptionType.STRING, "priority", "Priority level", true)
                                .addChoice("Low", "LOW")
                                .addChoice("Medium", "MEDIUM")
                                .addChoice("High", "HIGH")
                                .addChoice("Urgent", "URGENT")),
                Commands.slash("ticket-info", "Get information about the current ticket"),
                Commands.slash("ticket-transcript", "Generate a transcript of the current ticket")
        ).queue();
    }

    /*public void addSystemManagementCommands() {
        OptionData systemOption = new OptionData(OptionType.STRING, "system", "Which system to add", true)
                .addChoice("Log Channel System", "log-channel")
                .addChoice("Warning System", "warn-system")
                .addChoice("Ticket System", "ticket-system")
                .addChoice("Moderation System", "moderation-system");

        guild.updateCommands().addCommands(
                Commands.slash("add-system", "Add commands for a specific system")
                        .addOptions(systemOption)
        ).queue();
    }*/

    public void addAutomodCommands() {
        guild.updateCommands().addCommands(
                Commands.slash("automod-create", "Create a new automod rule")
                        .addOption(OptionType.STRING, "name", "Name for the automod rule", true)
                        .addOptions(new OptionData(OptionType.STRING, "rule_type", "Type of automod rule", true)
                                .addChoice("Spam Detection", "SPAM")
                                .addChoice("All Caps", "CAPS")
                                .addChoice("Link Blocking", "LINKS")
                                .addChoice("Invite Blocking", "INVITE")
                                .addChoice("Bad Words", "BADWORDS")
                                .addChoice("Mention Spam", "MENTION_SPAM"))
                        .addOptions(new OptionData(OptionType.STRING, "action", "Action to take when rule is triggered", true)
                                .addChoice("Warn", "WARN")
                                .addChoice("Mute", "MUTE")
                                .addChoice("Kick", "KICK")
                                .addChoice("Ban", "BAN")
                                .addChoice("Delete Message", "DELETE"))
                        .addOption(OptionType.INTEGER, "threshold", "How many violations before action is taken", false)
                        .addOption(OptionType.INTEGER, "duration", "Duration in minutes (for mute/ban actions)", false)
                        .addOption(OptionType.STRING, "whitelist", "Comma-separated list of whitelisted channels/roles", false)
                        .addOption(OptionType.STRING, "config", "Additional configuration (JSON format)", false),
                Commands.slash("automod-list", "List all automod rules for this server"),
                Commands.slash("automod-view", "View details of a specific automod rule")
                        .addOption(OptionType.INTEGER, "rule_id", "ID of the automod rule", true),
                Commands.slash("automod-edit", "Edit an existing automod rule")
                        .addOption(OptionType.INTEGER, "rule_id", "ID of the automod rule to edit", true)
                        .addOption(OptionType.STRING, "name", "New name for the rule", false)
                        .addOptions(new OptionData(OptionType.STRING, "rule_type", "New type of automod rule", false)
                                .addChoice("Spam Detection", "SPAM")
                                .addChoice("All Caps", "CAPS")
                                .addChoice("Link Blocking", "LINKS")
                                .addChoice("Invite Blocking", "INVITE")
                                .addChoice("Bad Words", "BADWORDS")
                                .addChoice("Mention Spam", "MENTION_SPAM"))
                        .addOptions(new OptionData(OptionType.STRING, "action", "New action to take", false)
                                .addChoice("Warn", "WARN")
                                .addChoice("Mute", "MUTE")
                                .addChoice("Kick", "KICK")
                                .addChoice("Ban", "BAN")
                                .addChoice("Delete Message", "DELETE"))
                        .addOption(OptionType.INTEGER, "threshold", "New threshold", false)
                        .addOption(OptionType.INTEGER, "duration", "New duration in minutes", false)
                        .addOption(OptionType.STRING, "whitelist", "New whitelist", false)
                        .addOption(OptionType.STRING, "config", "New configuration", false),
                Commands.slash("automod-toggle", "Enable or disable an automod rule")
                        .addOption(OptionType.INTEGER, "rule_id", "ID of the automod rule", true)
                        .addOption(OptionType.BOOLEAN, "enabled", "Whether the rule should be enabled", true),
                Commands.slash("automod-delete", "Delete an automod rule")
                        .addOption(OptionType.INTEGER, "rule_id", "ID of the automod rule to delete", true)
        ).queue();
    }

    public void addStatisticsCommands() {
        guild.updateCommands().addCommands(
                Commands.slash("stats-today", "View today's server moderation statistics"),
                Commands.slash("stats-week", "View this week's server moderation statistics"),
                Commands.slash("stats-date", "View server statistics for a specific date")
                        .addOption(OptionType.STRING, "date", "Date in YYYY-MM-DD format (e.g., 2024-01-15)", true)
        ).queue();
    }
}
