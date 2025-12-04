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

    @Override
    public void onSlashCommandInteraction (SlashCommandInteractionEvent event) {
        if (!event.getName().equals("log-channel")) {
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            return;
        }

        switch (subcommand) {
            case "set":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("log-channel-set");
                Channel channel = event.getOption("channel").getAsChannel();
                String channelID = handler.setLogChannel(event.getGuild().getId(), channel.getId());
                if (!channelID.equals("Error")) {
                    event.reply("Set log channel to: " + channel.getAsMention()).queue();
                } else {
                    event.reply("There was an Error. Please try again or contact the developer!");
                }
                break;
            case "get":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("log-channel-get");
                if (handler.hasLogChannel(event.getGuild().getId())) {
                    Channel logChannel = event.getGuild().getTextChannelById(handler.getLogChannelID(event.getGuild().getId()));
                    event.reply("Log Channel set to: " + logChannel.getAsMention()).queue();
                    return;
                }
                event.reply("Couldn't find a Log Channel.").queue();
                break;
        }
    }
}
