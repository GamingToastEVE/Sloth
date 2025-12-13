package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public class StatisticsCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public StatisticsCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        handler.incrementUserMessagesSent(event.getGuild().getId(), event.getAuthor().getId());
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("stats")) {
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            return;
        }

        String guildId = event.getGuild().getId();

        switch (subcommand) {
            case "today":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("stats-today");
                handleTodayStatsCommand(event, guildId);
                break;
            case "week":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("stats-week");
                handleWeeklyStatsCommand(event, guildId);
                break;
            case "date":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("stats-date");
                handleDateStatsCommand(event, guildId);
                break;
            case "user":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("stats-user");
                handleUserInfoCommand(event, guildId);
                break;
            case "lifetime":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("stats-lifetime");
                handleStatsCommand(event, guildId);
                break;
        }
    }

    private void handleStatsCommand (SlashCommandInteractionEvent event, String guildId) {
        // Check if user has moderate members permission
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ You need Moderate Members permission to view statistics.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = handler.getLifetimeModerationStatisticsEmbed(guildId);
        event.replyEmbeds(embed.build()).setEphemeral(false).queue();
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

        if (event.getOption("date") != null) {
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
            return;
        }
        
        EmbedBuilder embed = handler.getUserInfoEmbed(guildId, userId);
        event.replyEmbeds(embed.build()).setEphemeral(false).queue();
    }
}