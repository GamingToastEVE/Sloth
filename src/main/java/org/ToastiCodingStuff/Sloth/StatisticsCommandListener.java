package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
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
            case "stats-user":
                handleUserInfoCommand(event, guildId);
                break;
            case "stats-user-date":
                handleUserStatsDateCommand(event, guildId);
                break;
        }
    }

    private void handleTodayStatsCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has moderate members permission
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ You need Moderate Members permission to view statistics.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = handler.getTodaysModerationStatisticsEmbed(guildId);
        event.replyEmbeds(embed.build()).setEphemeral(false).queue();
    }

    private void handleWeeklyStatsCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has moderate members permission
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ You need Moderate Members permission to view statistics.").setEphemeral(true).queue();
            return;
        }

        String currentDate = LocalDate.now().minusDays(7).toString(); // Get date 7 days ago

        EmbedBuilder embed = handler.getWeeklyModerationStatisticsEmbed(guildId, currentDate);
        event.replyEmbeds(embed.build()).setEphemeral(false).queue();
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

        EmbedBuilder embed = handler.getModerationStatisticsForDateEmbed(guildId);
        event.replyEmbeds(embed.build()).setEphemeral(false).queue();
    }

    private void handleUserInfoCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has moderate members permission
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ You need Moderate Members permission to view user statistics.").setEphemeral(true).queue();
            return;
        }

        String userId = event.getOption("user").getAsUser().getId();
        
        EmbedBuilder embed = handler.getUserInfoEmbed(guildId, userId);
        event.replyEmbeds(embed.build()).setEphemeral(false).queue();
    }

    private void handleUserStatsDateCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has moderate members permission
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ You need Moderate Members permission to view user statistics.").setEphemeral(true).queue();
            return;
        }

        String userId = event.getOption("user").getAsUser().getId();
        String dateString = event.getOption("date").getAsString();
        
        // Validate date format
        try {
            LocalDate.parse(dateString);
        } catch (DateTimeParseException e) {
            event.reply("❌ Invalid date format. Please use YYYY-MM-DD (e.g., 2024-01-15).").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = handler.getUserStatisticsForDateEmbed(guildId, userId, dateString);
        event.replyEmbeds(embed.build()).setEphemeral(false).queue();
    }
}