package org.ToastiCodingStuff.Delta;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

public class ServerListCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public ServerListCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "guild-list":
                handleGuildListCommand(event);
                break;
            case "guild-stats":
                handleGuildStatsCommand(event);
                break;
        }
    }

    private void handleGuildListCommand(SlashCommandInteractionEvent event) {
        // Get all guilds where the bot is present
        List<Guild> botGuilds = event.getJDA().getGuilds();
        StringBuilder response = new StringBuilder();
        response.append("**Servers where you can moderate and view statistics:**\n\n");
        
        boolean hasModeratePermissions = false;
        
        for (Guild guild : botGuilds) {
            // Get the member object for the user in this guild
            Member member = guild.getMemberById(event.getUser().getId());
            
            // Check if user is in this guild and has moderate members permission
            if (member != null && member.hasPermission(Permission.MODERATE_MEMBERS)) {
                hasModeratePermissions = true;
                response.append("🏛️ **").append(guild.getName()).append("**\n");
                response.append("   ID: `").append(guild.getId()).append("`\n");
                response.append("   Members: ").append(guild.getMemberCount()).append("\n\n");
            }
        }
        
        if (!hasModeratePermissions) {
            response.setLength(0);
            response.append("❌ You don't have Moderate Members permission on any servers where this bot is present.\n");
            response.append("You need the **Moderate Members** permission to view server statistics.");
        } else {
            response.append("💡 *Use `/guild-stats guild_id:<server_id>` to view detailed statistics for any of these servers.*");
        }
        
        event.reply(response.toString()).setEphemeral(true).queue();
    }

    private void handleGuildStatsCommand(SlashCommandInteractionEvent event) {
        String targetGuildId = event.getOption("guild_id").getAsString();
        
        // Find the target guild
        Guild targetGuild = event.getJDA().getGuildById(targetGuildId);
        if (targetGuild == null) {
            event.reply("❌ Guild not found or bot is not in that server.").setEphemeral(true).queue();
            return;
        }
        
        // Check if the user is a member of the target guild and has moderate members permission
        Member member = targetGuild.getMemberById(event.getUser().getId());
        if (member == null) {
            event.reply("❌ You are not a member of the server: **" + targetGuild.getName() + "**").setEphemeral(true).queue();
            return;
        }
        
        if (!member.hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ You need Moderate Members permission in **" + targetGuild.getName() + "** to view its statistics.").setEphemeral(true).queue();
            return;
        }
        
        // Get statistics type from user option
        String statsType = event.getOption("type").getAsString();
        String statistics;
        
        switch (statsType) {
            case "today":
                statistics = handler.getTodaysStatistics(targetGuildId);
                break;
            case "week":
                statistics = handler.getWeeklyStatistics(targetGuildId);
                break;
            case "date":
                String dateString = event.getOption("date").getAsString();
                if (dateString == null) {
                    event.reply("❌ Date parameter is required when using 'date' statistics type.").setEphemeral(true).queue();
                    return;
                }
                statistics = handler.getStatisticsForDate(targetGuildId, dateString);
                break;
            default:
                event.reply("❌ Invalid statistics type. Use 'today', 'week', or 'date'.").setEphemeral(true).queue();
                return;
        }
        
        // Prepend guild information to the statistics
        StringBuilder response = new StringBuilder();
        response.append("📊 **Statistics for ").append(targetGuild.getName()).append("**\n");
        response.append("Server ID: `").append(targetGuildId).append("`\n\n");
        response.append(statistics);
        
        event.reply(response.toString()).setEphemeral(false).queue();
    }
}