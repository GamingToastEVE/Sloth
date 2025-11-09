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
        if (event.getName().equals("set-log-channel")) {
            if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
            handler.insertOrUpdateGlobalStatistic("set-log-channel");
            Channel channel = event.getOption("logchannel").getAsChannel();
            String channelID = handler.setLogChannel(event.getGuild().getId(), channel.getId());
            if (!channelID.equals("Error")) {
                event.reply("Set log channel to: " + channel.getAsMention()).queue();
            }else {
                event.reply("There was an Error. Please try again or contact the developer!");
            }
        }
        if (event.getName().equals("get-log-channel")) {
            if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
            handler.insertOrUpdateGlobalStatistic("get-log-channel");
            if (handler.hasLogChannel(event.getGuild().getId())) {
                Channel channel = event.getGuild().getTextChannelById(handler.getLogChannelID(event.getGuild().getId()));
                event.reply("Log Channel set to: " + channel.getAsMention()).queue();
                return;
            }
            event.reply("Couldn't find a Log Channel.").queue();
        }
    }
}
