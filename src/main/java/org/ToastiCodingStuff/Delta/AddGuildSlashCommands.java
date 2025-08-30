package org.ToastiCodingStuff.Delta;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class AddGuildSlashCommands {
    private final Guild guild;

    public AddGuildSlashCommands(Guild guild) {
        this.guild = guild;
    }

    public void addlogChannelCommands () {
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
                        .addOption(OptionType.INTEGER, "max_warns", "Maximum warnings before action", true)
                        .addOption(OptionType.INTEGER, "minutes_muted", "Minutes to mute user when reaching max warns", true)
                        .addOption(OptionType.ROLE, "mute_role", "Role to assign when muting", true)
                        .addOption(OptionType.INTEGER, "warn_time_hours", "Hours after which warnings expire", false),
                Commands.slash("get-warn-settings", "View current warning system settings")
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
                Commands.slash("ticket-info", "Get information about the current ticket")
        ).queue();
    }

    public void addSystemManagementCommands() {
        OptionData systemOption = new OptionData(OptionType.STRING, "system", "Which system to add", true)
                .addChoice("Log Channel System", "log-channel")
                .addChoice("Warning System", "warn-system")
                .addChoice("Ticket System", "ticket-system");

        guild.updateCommands().addCommands(
                Commands.slash("add-system", "Add commands for a specific system")
                        .addOptions(systemOption)
        ).queue();
    }
}
