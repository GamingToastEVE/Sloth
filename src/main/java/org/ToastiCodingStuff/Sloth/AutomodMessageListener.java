package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Listener that processes all messages through automod rules
 */
public class AutomodMessageListener extends ListenerAdapter {
    
    private final DatabaseHandler databaseHandler;
    
    public AutomodMessageListener(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Skip if message is from a bot
        if (event.getAuthor().isBot()) {
            return;
        }
        
        // Skip if not from a guild
        if (!event.isFromGuild()) {
            return;
        }
        
        Guild guild = event.getGuild();
        Member member = event.getMember();
        Message message = event.getMessage();
        
        if (member == null) {
            return;
        }
        
        // Skip if user has admin permissions (optional - can be configured)
        if (member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            return;
        }
        
        String guildId = guild.getId();
        String userId = member.getId();
        String messageContent = message.getContentRaw();
        
        // Get user's role IDs
        List<String> userRoles = member.getRoles().stream()
                .map(Role::getId)
                .collect(Collectors.toList());
        
        // Process message through automod
        Map<String, Object> actionResult = databaseHandler.processMessageAutomod(guildId, userId, userRoles, messageContent);
        
        if (actionResult != null) {
            String violationType = (String) actionResult.get("violation_type");
            String action = (String) actionResult.get("action");
            String ruleName = (String) actionResult.get("rule_name");
            Integer violationCount = (Integer) actionResult.get("violation_count");
            Boolean thresholdNotMet = (Boolean) actionResult.get("threshold_not_met");
            
            // Handle message deletion first if needed
            if ("DELETE".equals(action) && thresholdNotMet == null) {
                try {
                    message.delete().queue();
                } catch (Exception e) {
                    System.err.println("Failed to delete message: " + e.getMessage());
                }
            }
            
            // Execute the action if threshold is met
            if (thresholdNotMet == null || !thresholdNotMet) {
                boolean success = databaseHandler.executeAutomodAction(guild, userId, actionResult);
                
                if (success) {
                    System.out.println("Automod action executed: " + action + " for user " + member.getEffectiveName() + 
                                     " in guild " + guild.getName() + " (Rule: " + ruleName + ", Violation: " + violationType + ")");
                } else {
                    System.err.println("Failed to execute automod action: " + action + " for user " + member.getEffectiveName());
                }
            } else {
                // Log the violation but don't take action yet
                System.out.println("Automod violation logged: " + violationType + " by " + member.getEffectiveName() + 
                                 " in guild " + guild.getName() + " (Count: " + violationCount + "/" + 
                                 ((Map<String, Object>) actionResult.get("rule")).get("threshold") + ")");
            }
        }
    }
}