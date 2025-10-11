package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class OnGuildLeaveListener extends ListenerAdapter {
    private final DatabaseHandler handler;

    public OnGuildLeaveListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onGuildLeave(net.dv8tion.jda.api.events.guild.GuildLeaveEvent event) {
        String guildId = event.getGuild().getId();
        String guildName = event.getGuild().getName();

        System.out.println("Bot left guild: " + guildName + " (ID: " + guildId + ")");
        handler.deactivateGuild(guildId);
    }
}
