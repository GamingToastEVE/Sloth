package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class LogChannelSlashCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public LogChannelSlashCommandListener(DatabaseHandler handler) {
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

    @Override
    public void onSlashCommandInteraction (SlashCommandInteractionEvent event) {
        if (!event.getName().equals("log-channel")) {
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            return;
        }

        event.deferReply().setEphemeral(true).queue();
        String guildId = event.getGuild().getId();

        switch (subcommand) {
            case "set":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("log-channel-set");
                Channel channel = event.getOption("channel").getAsChannel();
                String channelID = handler.setLogChannel(guildId, channel.getId());
                if (!channelID.equals("Error")) {
                    event.getHook().sendMessage(t(guildId, "general.success") + " " + channel.getAsMention()).queue();
                } else {
                    event.getHook().sendMessage(t(guildId, "general.error"));
                }
                break;
            case "get":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("log-channel-get");
                if (handler.hasLogChannel(guildId)) {
                    Channel logChannel = event.getGuild().getTextChannelById(handler.getLogChannelID(guildId));
                    event.getHook().sendMessage("Log Channel: " + logChannel.getAsMention()).queue();
                    return;
                }
                event.getHook().sendMessage(t(guildId, "general.not_found")).queue();
                break;
        }
    }
}
