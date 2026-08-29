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

        // Deactivate and start the retention clock. The data itself is removed by the
        // scheduled purge once DatabaseHandler.GUILD_DATA_RETENTION_DAYS have passed,
        // which is what the privacy policy promises.
        handler.markGuildLeft(guildId);
    }
}
