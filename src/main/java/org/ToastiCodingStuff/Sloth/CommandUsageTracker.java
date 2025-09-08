package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.List;

public class CommandUsageTracker extends ListenerAdapter {
    
    private final DatabaseHandler handler;
    
    public CommandUsageTracker(DatabaseHandler handler) {
        this.handler = handler;
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Let the command be processed by other listeners first
            // We'll log after a short delay to capture execution time
            new Thread(() -> {
                try {
                    // Wait a bit to let the command complete
                    Thread.sleep(100);
                    
                    long executionTime = System.currentTimeMillis() - startTime;
                    
                    // Collect command information
                    String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
                    String userId = event.getUser().getId();
                    String commandName = event.getName();
                    String channelId = event.getChannel().getId();
                    
                    // Build options string
                    StringBuilder optionsBuilder = new StringBuilder();
                    List<OptionMapping> options = event.getOptions();
                    if (!options.isEmpty()) {
                        for (OptionMapping option : options) {
                            if (optionsBuilder.length() > 0) {
                                optionsBuilder.append(", ");
                            }
                            optionsBuilder.append(option.getName()).append("=");
                            
                            // Handle different option types
                            switch (option.getType()) {
                                case STRING:
                                    optionsBuilder.append('"').append(option.getAsString()).append('"');
                                    break;
                                case INTEGER:
                                    optionsBuilder.append(option.getAsLong());
                                    break;
                                case BOOLEAN:
                                    optionsBuilder.append(option.getAsBoolean());
                                    break;
                                case USER:
                                    optionsBuilder.append("@").append(option.getAsUser().getEffectiveName());
                                    break;
                                case CHANNEL:
                                    optionsBuilder.append("#").append(option.getAsChannel().getName());
                                    break;
                                case ROLE:
                                    optionsBuilder.append("@&").append(option.getAsRole().getName());
                                    break;
                                default:
                                    optionsBuilder.append(option.getAsString());
                            }
                        }
                    }
                    
                    String optionsString = optionsBuilder.length() > 0 ? optionsBuilder.toString() : null;
                    
                    // Log the command usage
                    handler.logCommandUsage(
                        guildId, 
                        userId, 
                        commandName, 
                        optionsString, 
                        channelId, 
                        executionTime, 
                        true, // We assume success for now - could be enhanced to track failures
                        null
                    );
                    
                } catch (Exception e) {
                    // If logging fails, don't interrupt the bot operation
                    System.err.println("Error tracking command usage: " + e.getMessage());
                }
            }).start();
            
        } catch (Exception e) {
            // Log the failed command
            try {
                String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
                String userId = event.getUser().getId();
                String commandName = event.getName();
                String channelId = event.getChannel().getId();
                long executionTime = System.currentTimeMillis() - startTime;
                
                handler.logCommandUsage(
                    guildId, 
                    userId, 
                    commandName, 
                    null, 
                    channelId, 
                    executionTime, 
                    false, 
                    e.getMessage()
                );
            } catch (Exception logError) {
                System.err.println("Error logging failed command usage: " + logError.getMessage());
            }
        }
    }
}