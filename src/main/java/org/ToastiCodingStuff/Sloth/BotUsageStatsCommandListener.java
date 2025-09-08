package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class BotUsageStatsCommandListener extends ListenerAdapter {
    
    private final DatabaseHandler handler;
    private static final String AUTHORIZED_USER_ID = "365042010626719745";
    private static final String AUTHORIZED_GUILD_ID = "1169699077986988112";
    
    public BotUsageStatsCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("bot-usage-stats")) {
            return;
        }
        
        // Check if the command is being used in the authorized guild
        if (!AUTHORIZED_GUILD_ID.equals(event.getGuild().getId())) {
            event.reply("❌ This command is not available in this server.").setEphemeral(true).queue();
            return;
        }
        
        // Check if the user is authorized
        if (!AUTHORIZED_USER_ID.equals(event.getUser().getId())) {
            event.reply("❌ You are not authorized to use this command.").setEphemeral(true).queue();
            return;
        }
        
        handleBotUsageStatsCommand(event);
    }
    
    private void handleBotUsageStatsCommand(SlashCommandInteractionEvent event) {
        // Get the days parameter (default to 7 days)
        int days = 7;
        if (event.getOption("days") != null) {
            days = (int) event.getOption("days").getAsLong();
            
            // Validate days parameter
            if (days < 1 || days > 365) {
                event.reply("❌ Days parameter must be between 1 and 365.").setEphemeral(true).queue();
                return;
            }
        }
        
        // Get scope parameter (default to "guild")
        String scope = "guild";
        if (event.getOption("scope") != null) {
            scope = event.getOption("scope").getAsString();
        }
        
        String guildId = "global".equals(scope) ? null : event.getGuild().getId();
        
        try {
            EmbedBuilder embed = handler.getBotUsageStatisticsEmbed(guildId, days);
            
            // Add scope information to the embed
            if ("global".equals(scope)) {
                embed.setFooter("Scope: Global (All Servers) • Requested by " + event.getUser().getEffectiveName());
            } else {
                embed.setFooter("Scope: " + event.getGuild().getName() + " • Requested by " + event.getUser().getEffectiveName());
            }
            
            event.replyEmbeds(embed.build()).setEphemeral(false).queue();
        } catch (Exception e) {
            System.err.println("Error generating bot usage statistics: " + e.getMessage());
            e.printStackTrace();
            event.reply("❌ An error occurred while generating the statistics. Please try again later.").setEphemeral(true).queue();
        }
    }
}