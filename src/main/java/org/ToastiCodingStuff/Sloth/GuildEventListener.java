package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Listener for guild-related events to automatically update the guilds table
 */
public class GuildEventListener extends ListenerAdapter {
    
    private final DatabaseHandler handler;
    
    public GuildEventListener(DatabaseHandler handler) {
        this.handler = handler;
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
        handler.deactivateGuild(guildId);
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