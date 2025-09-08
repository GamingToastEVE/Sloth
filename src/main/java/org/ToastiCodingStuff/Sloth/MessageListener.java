package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class MessageListener extends ListenerAdapter {
    
    private final DatabaseHandler handler;
    private static final String AUTHORIZED_USER_ID = "365042010626719745";
    
    public MessageListener(DatabaseHandler handler) {
        this.handler = handler;
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) {
            return;
        }
        
        String messageContent = event.getMessage().getContentRaw();
        String userId = event.getAuthor().getId();
        
        // Check for statistics command - only accessible by authorized user
        if (messageContent.equalsIgnoreCase("!statistics") || 
            messageContent.equalsIgnoreCase("!stats") ||
            messageContent.equalsIgnoreCase("statistics") ||
            messageContent.equalsIgnoreCase("stats")) {
            
            // Check if user is authorized
            if (!AUTHORIZED_USER_ID.equals(userId)) {
                event.getChannel().sendMessage("❌ You are not authorized to use this command.").queue();
                return;
            }
            
            // Send global statistics
            String statistics = handler.getGlobalStatistics();
            event.getChannel().sendMessage(statistics).queue();
        }
    }
}