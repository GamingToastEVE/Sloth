package org.ToastiCodingStuff.Delta;

import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class setLogchannelSlashCommandListener extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction (SlashCommandInteractionEvent event) {
        if (event.getName().equals("set-log-channel")) {
            databaseHandler handler = new databaseHandler();
            Channel channel = event.getOption("logchannel").getAsChannel();
            String channelID = handler.setLogChannel(event.getGuild().getId(), channel.getId());
            if (!channelID.equals("Error")) {
                event.reply("Set log channel to: " + channel.getAsMention()).queue();
            }else {
                event.reply("There was an Error. Please try again or contact the developer!");
            }
            handler.closeConnection();
        }
    }
}
