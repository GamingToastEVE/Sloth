package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

/**
 * Listener for guild-related events to automatically update the guilds table
 */
public class GuildEventListener extends ListenerAdapter {
    
    private final DatabaseHandler handler;
    
    public GuildEventListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    // ==================== LANGUAGE HELPER METHODS ====================

    private String t(String guildId, String key) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            return lang.get(guildId, key);
        }
        return key;
    }

    private String t(String guildId, String key, Object... args) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            return lang.get(guildId, key, args);
        }
        try {
            return String.format(key, args);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * Handle bot joining a guild - automatically add guild to database
     */
    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        String guildName = guild.getName();
        
        System.out.println("Bot joined guild: " + guildName + " (ID: " + guildId + ")");
        handler.insertOrUpdateGuild(guildId, guildName);

        var channel = guild.getSystemChannel();
        if (channel == null || !channel.canTalk()) {
            channel = guild.getTextChannels().stream()
                    .filter(c -> c.canTalk())
                    .findFirst()
                    .orElse(null);
        }

        if (channel != null) {
            // Build localized welcome message using Components V2 style
            Container container = Container.of(
                    TextDisplay.of(t(guildId, "welcome.title")),
                    TextDisplay.of(t(guildId, "welcome.description", guild.getName())),
                    ActionRow.of(
                            Button.success("setup_start", t(guildId, "welcome.btn_start_wizard")),
                            Button.secondary("help_home", t(guildId, "welcome.btn_open_wiki"))
                    )
            ).withAccentColor(0x3498DB);

            MessageCreateBuilder mb = new MessageCreateBuilder()
                    .setComponents(container);

            channel.sendMessage(mb.useComponentsV2().build()).queue();
        }
    }
    
    /**
     * Handle bot leaving a guild - mark guild as inactive
     */
    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        String guildName = guild.getName();
        
        System.out.println("Bot left guild: " + guildName + " (ID: " + guildId + ")");
    }
    
    /**
     * Handle guild name updates - update guild name in database
     */
    @Override
    public void onGuildUpdateName(GuildUpdateNameEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        String newGuildName = event.getNewName();
        
        System.out.println("Guild name updated: " + newGuildName + " (ID: " + guildId + ")");
        handler.insertOrUpdateGuild(guildId, newGuildName);
    }
}