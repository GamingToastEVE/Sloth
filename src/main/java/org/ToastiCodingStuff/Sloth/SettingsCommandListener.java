package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.util.Objects;

/**
 * Handles the /settings command for configuring bot settings per guild.
 */
public class SettingsCommandListener extends ListenerAdapter {
    
    private final DatabaseHandler handler;
    
    public SettingsCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("settings")) {
            return;
        }
        
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            return;
        }
        
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String lang = handler.getGuildLanguage(guildId);
        
        switch (subcommand) {
            case "language":
                handler.insertOrUpdateGlobalStatistic("settings-language");
                handleLanguageCommand(event, guildId, lang);
                break;
        }
    }
    
    private void handleLanguageCommand(SlashCommandInteractionEvent event, String guildId, String currentLang) {
        // Check if user has manage server permission
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(LocaleManager.getMessage(currentLang, "general.no_permission"))
                    .setEphemeral(true).queue();
            return;
        }
        
        // Get the language option
        String newLanguage = event.getOption("language") != null 
                ? Objects.requireNonNull(event.getOption("language")).getAsString().toLowerCase() 
                : null;
        
        if (newLanguage == null) {
            // Show current language
            String displayName = LocaleManager.getLanguageDisplayName(currentLang, currentLang);
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(LocaleManager.getMessage(currentLang, "settings.language.title"))
                    .setDescription(LocaleManager.getMessage(currentLang, "settings.language.current", displayName))
                    .setColor(Color.BLUE);
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            return;
        }
        
        // Validate language
        if (!LocaleManager.isSupported(newLanguage)) {
            event.reply(LocaleManager.getMessage(currentLang, "settings.language.invalid"))
                    .setEphemeral(true).queue();
            return;
        }
        
        // Update language in database
        boolean success = handler.updateGuildLanguage(guildId, newLanguage);
        
        if (success) {
            String displayName = LocaleManager.getLanguageDisplayName(newLanguage, newLanguage);
            event.reply(LocaleManager.getMessage(newLanguage, "settings.language.changed", displayName))
                    .queue();
        } else {
            event.reply(LocaleManager.getMessage(currentLang, "settings.language.failed"))
                    .setEphemeral(true).queue();
        }
    }
}
