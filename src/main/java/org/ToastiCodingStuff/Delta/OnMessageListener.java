package org.ToastiCodingStuff.Delta;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class OnMessageListener extends ListenerAdapter {

    private final databaseHandler handler;

    public OnMessageListener(databaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        Message message = event.getMessage();
        String content = message.getContentRaw();
        if (content.equals("!ping")) {
            event.getMessage().reply(handler.isTicketSystem(message.getGuildId()) + "").queue();
        }
    }
}
