package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class StatisticsCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public StatisticsCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        switch (event.getName()) {
            case "stats-today":
                handleTodayStatsCommand(event, guildId);
                break;
            case "stats-week":
                handleWeeklyStatsCommand(event, guildId);
                break;
            case "stats-date":
                handleDateStatsCommand(event, guildId);
                break;
        }
    }

    private void handleTodayStatsCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has moderate members permission
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ You need Moderate Members permission to view statistics.").setEphemeral(true).queue();
            return;
        }

        String statistics = handler.getTodaysStatistics(guildId);
        event.reply(statistics).setEphemeral(false).queue();
    }

    private void handleWeeklyStatsCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has moderate members permission
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ You need Moderate Members permission to view statistics.").setEphemeral(true).queue();
            return;
        }

        String statistics = handler.getWeeklyStatistics(guildId);
        event.reply(statistics).setEphemeral(false).queue();
    }

    private void handleDateStatsCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has moderate members permission
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ You need Moderate Members permission to view statistics.").setEphemeral(true).queue();
            return;
        }

        String dateString = event.getOption("date").getAsString();
        
        // Validate date format
        try {
            LocalDate.parse(dateString);
        } catch (DateTimeParseException e) {
            event.reply("❌ Invalid date format. Please use YYYY-MM-DD (e.g., 2024-01-15).").setEphemeral(true).queue();
            return;
        }

        String statistics = handler.getStatisticsForDate(guildId, dateString);
        event.reply(statistics).setEphemeral(false).queue();
    }
}