package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ModerationCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public ModerationCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        switch (event.getName()) {
            case "kick":
                handleKickCommand(event, guildId);
                break;
            case "ban":
                handleBanCommand(event, guildId);
                break;
            case "unban":
                handleUnbanCommand(event, guildId);
                break;
            case "timeout":
                handleTimeoutCommand(event, guildId);
                break;
            case "untimeout":
                handleUntimeoutCommand(event, guildId);
                break;
            case "purge":
                handlePurgeCommand(event, guildId);
                break;
            case "slowmode":
                handleSlowmodeCommand(event, guildId);
                break;
        }
    }

    private void handleKickCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has kick permissions
        if (!event.getMember().hasPermission(Permission.KICK_MEMBERS)) {
            event.reply("❌ You do not have permission to kick members.").setEphemeral(true).queue();
            return;
        }

        if (event.getOption("user") == null) {
            event.reply("❌ Please specify a user to kick.").setEphemeral(true).queue();
            return;
        }

        Member targetMember = event.getOption("user").getAsMember();
        if (targetMember == null) {
            event.reply("❌ User not found in this server.").setEphemeral(true).queue();
            return;
        }

        // Check if the target can be kicked
        if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
            event.reply("❌ I cannot kick this user due to role hierarchy.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(targetMember)) {
            event.reply("❌ You cannot kick this user due to role hierarchy.").setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("reason") != null ? 
                event.getOption("reason").getAsString() : "No reason provided";

        String userId = targetMember.getId();
        String moderatorId = event.getMember().getId();
        String targetName = targetMember.getEffectiveName();
        String moderatorName = event.getMember().getEffectiveName();

        // Insert or update user data
        handler.insertOrUpdateUser(userId, targetName, 
                targetMember.getUser().getDiscriminator(), 
                targetMember.getUser().getAvatarUrl());
        
        handler.insertOrUpdateUser(moderatorId, moderatorName,
                event.getMember().getUser().getDiscriminator(),
                event.getMember().getUser().getAvatarUrl());

        // Kick the member
        targetMember.kick().reason(reason).queue(
            success -> {
                // Kick successful
                event.reply("✅ Kicked " + targetName + " for: " + reason).queue();
                
                // Log moderation action
                handler.insertModerationAction(guildId, userId, moderatorId, "KICK", reason, null, null);
                
                // Update statistics
                handler.incrementKicksPerformed(guildId);
                
                // Update user statistics
                handler.incrementUserKicksReceived(guildId, userId);
                handler.incrementUserKicksPerformed(guildId, moderatorId);
                
                // Send to log channel if configured
                sendToLogChannel(event, guildId, "KICK", targetName, moderatorName, reason);
            },
            error -> {
                event.reply("❌ Failed to kick " + targetName + ". Please try again.").setEphemeral(true).queue();
            }
        );
    }

    private void handleBanCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has ban permissions
        if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("❌ You do not have permission to ban members.").setEphemeral(true).queue();
            return;
        }

        if (event.getOption("user") == null) {
            event.reply("❌ Please specify a user to ban.").setEphemeral(true).queue();
            return;
        }

        Member targetMember = event.getOption("user").getAsMember();
        if (targetMember == null) {
            event.reply("❌ User not found in this server.").setEphemeral(true).queue();
            return;
        }

        // Check if the target can be banned
        if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
            event.reply("❌ I cannot ban this user due to role hierarchy.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(targetMember)) {
            event.reply("❌ You cannot ban this user due to role hierarchy.").setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("reason") != null ? 
                event.getOption("reason").getAsString() : "No reason provided";

        String userId = targetMember.getId();
        String moderatorId = event.getMember().getId();
        String targetName = targetMember.getEffectiveName();
        String moderatorName = event.getMember().getEffectiveName();

        // Insert or update user data
        handler.insertOrUpdateUser(userId, targetName, 
                targetMember.getUser().getDiscriminator(), 
                targetMember.getUser().getAvatarUrl());
        
        handler.insertOrUpdateUser(moderatorId, moderatorName,
                event.getMember().getUser().getDiscriminator(),
                event.getMember().getUser().getAvatarUrl());

        // Ban the member (0 means no message deletion)
        targetMember.ban(0, TimeUnit.SECONDS).reason(reason).queue(
            success -> {
                // Ban successful
                event.reply("✅ Banned " + targetName + " for: " + reason).queue();
                
                // Log moderation action
                handler.insertModerationAction(guildId, userId, moderatorId, "BAN", reason, null, null);
                
                // Update statistics
                handler.incrementBansPerformed(guildId);
                
                // Update user statistics
                handler.incrementUserBansReceived(guildId, userId);
                handler.incrementUserBansPerformed(guildId, moderatorId);
                
                // Send to log channel if configured
                sendToLogChannel(event, guildId, "BAN", targetName, moderatorName, reason);
            },
            error -> {
                event.reply("❌ Failed to ban " + targetName + ". Please try again.").setEphemeral(true).queue();
            }
        );
    }

    private void handleUnbanCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has ban permissions
        if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("❌ You do not have permission to unban members.").setEphemeral(true).queue();
            return;
        }

        String userId = event.getOption("userid").getAsString();
        String reason = event.getOption("reason") != null ? 
                event.getOption("reason").getAsString() : "No reason provided";

        String moderatorId = event.getMember().getId();
        String moderatorName = event.getMember().getEffectiveName();

        // Unban the user
        event.getGuild().unban(net.dv8tion.jda.api.entities.UserSnowflake.fromId(userId)).reason(reason).queue(
            success -> {
                event.reply("✅ Unbanned user with ID " + userId + " for: " + reason).queue();
                
                // Insert or update moderator data
                handler.insertOrUpdateUser(moderatorId, moderatorName,
                        event.getMember().getUser().getDiscriminator(),
                        event.getMember().getUser().getAvatarUrl());
                
                // Log moderation action
                handler.insertModerationAction(guildId, userId, moderatorId, "UNBAN", reason, null, null);
                // Update statistics
                
                // Send to log channel if configured
                sendToLogChannel(event, guildId, "UNBAN", "User ID: " + userId, moderatorName, reason);
            },
            error -> {
                event.reply("❌ Failed to unban user. Please verify the user ID is correct.").setEphemeral(true).queue();
            }
        );
    }

    private void handleTimeoutCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has moderate members permission
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ You do not have permission to timeout members.").setEphemeral(true).queue();
            return;
        }

        Member targetMember = event.getOption("user").getAsMember();
        int minutes = event.getOption("minutes").getAsInt();
        String reason = event.getOption("reason") != null ? 
                event.getOption("reason").getAsString() : "No reason provided";

        if (targetMember == null) {
            event.reply("❌ User not found in this server.").setEphemeral(true).queue();
            return;
        }

        // Validate timeout duration (max 28 days = 40320 minutes)
        if (minutes < 1 || minutes > 40320) {
            event.reply("❌ Timeout duration must be between 1 and 40320 minutes (28 days).").setEphemeral(true).queue();
            return;
        }

        // Check if the target can be timed out
        if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
            event.reply("❌ I cannot timeout this user due to role hierarchy.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(targetMember)) {
            event.reply("❌ You cannot timeout this user due to role hierarchy.").setEphemeral(true).queue();
            return;
        }

        String userId = targetMember.getId();
        String moderatorId = event.getMember().getId();
        String targetName = targetMember.getEffectiveName();
        String moderatorName = event.getMember().getEffectiveName();

        // Apply timeout
        Duration duration = Duration.ofMinutes(minutes);
        targetMember.timeoutFor(duration).reason(reason).queue(
            success -> {
                event.reply("✅ Timed out " + targetName + " for " + minutes + " minutes. Reason: " + reason).queue();
                
                // Insert or update user data
                handler.insertOrUpdateUser(userId, targetName, 
                        targetMember.getUser().getDiscriminator(), 
                        targetMember.getUser().getAvatarUrl());
                
                // Log moderation action
                String expiresAt = java.time.LocalDateTime.now().plusMinutes(minutes)
                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                handler.insertModerationAction(guildId, userId, moderatorId, "TIMEOUT", reason, duration.toString(), expiresAt);
                
                // Update statistics
                handler.incrementTimeoutsPerformed(guildId);
                handler.incrementUserTimeoutsReceived(guildId, userId);
                handler.incrementUserTimeoutsPerformed(guildId, moderatorId);
                
                // Send to log channel if configured
                sendToLogChannel(event, guildId, "TIMEOUT (" + minutes + "m)", targetName, moderatorName, reason);
            },
            error -> {
                event.reply("❌ Failed to timeout " + targetName + ". Please try again.").setEphemeral(true).queue();
            }
        );
    }

    private void handleUntimeoutCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has moderate members permission
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ You do not have permission to remove timeouts from members.").setEphemeral(true).queue();
            return;
        }

        Member targetMember = event.getOption("user").getAsMember();
        String reason = event.getOption("reason") != null ? 
                event.getOption("reason").getAsString() : "No reason provided";

        if (targetMember == null) {
            event.reply("❌ User not found in this server.").setEphemeral(true).queue();
            return;
        }

        // Check if user is actually timed out
        if (!targetMember.isTimedOut()) {
            event.reply("❌ " + targetMember.getEffectiveName() + " is not currently timed out.").setEphemeral(true).queue();
            return;
        }

        String userId = targetMember.getId();
        String moderatorId = event.getMember().getId();
        String targetName = targetMember.getEffectiveName();
        String moderatorName = event.getMember().getEffectiveName();

        // Remove timeout
        targetMember.removeTimeout().reason(reason).queue(
            success -> {
                event.reply("✅ Removed timeout from " + targetName + ". Reason: " + reason).queue();
                
                // Insert or update user data
                handler.insertOrUpdateUser(userId, targetName, 
                        targetMember.getUser().getDiscriminator(), 
                        targetMember.getUser().getAvatarUrl());
                
                // Log moderation action
                handler.insertModerationAction(guildId, userId, moderatorId, "UNTIMEOUT", reason, null, null);
                
                // Update statistics
                handler.incrementUntimeoutsPerformed(guildId);
                handler.incrementUserUntimeoutsReceived(guildId, userId);
                handler.incrementUserUntimeoutsPerformed(guildId, moderatorId);
                
                // Send to log channel if configured
                sendToLogChannel(event, guildId, "UNTIMEOUT", targetName, moderatorName, reason);
            },
            error -> {
                event.reply("❌ Failed to remove timeout from " + targetName + ". Please try again.").setEphemeral(true).queue();
            }
        );
    }

    private void handlePurgeCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has manage messages permission
        if (!event.getMember().hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply("❌ You do not have permission to manage messages.").setEphemeral(true).queue();
            return;
        }

        int amount = event.getOption("amount").getAsInt();
        Member targetUser = event.getOption("user") != null ? event.getOption("user").getAsMember() : null;

        if (amount < 1 || amount > 100) {
            event.reply("❌ Amount must be between 1 and 100 messages.").setEphemeral(true).queue();
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        String moderatorName = event.getMember().getEffectiveName();

        event.deferReply(true).queue(); // Defer reply as this might take time

        // Retrieve messages
        channel.getHistory().retrievePast(amount + 1).queue(messages -> {
            // Filter out system messages and messages that might be problematic
            List<Message> messagesToDelete;
            if (targetUser != null) {
                // Filter messages from specific user
                messagesToDelete = messages.stream()
                    .filter(msg -> msg.getAuthor().getId().equals(targetUser.getId()))
                    .limit(amount)
                    .collect(java.util.stream.Collectors.toList());
            } else {
                // Take up to the amount requested
                messagesToDelete = messages.stream()
                    .limit(amount)
                    .collect(java.util.stream.Collectors.toList());
            }

            if (messagesToDelete.isEmpty()) {
                event.getHook().sendMessage("❌ No messages found to delete.").queue();
                return;
            }

            // Delete messages
            if (messagesToDelete.size() == 1) {
                messagesToDelete.get(0).delete().queue(
                    success -> {
                        String response = targetUser != null ?
                            "✅ Deleted " + messagesToDelete.size() + " message(s) from " + targetUser.getEffectiveName() :
                            "✅ Deleted " + messagesToDelete.size() + " message(s)";
                        event.getHook().sendMessage(response).queue();
                        
                        // Log the action
                        String reason = targetUser != null ? 
                            "Purged " + messagesToDelete.size() + " messages from " + targetUser.getEffectiveName() :
                            "Purged " + messagesToDelete.size() + " messages";
                        sendToLogChannel(event, guildId, "PURGE", channel.getName(), moderatorName, reason);
                    },
                    error -> {
                        event.getHook().sendMessage("❌ Failed to delete messages.").queue();
                    }
                );
            } else {
                channel.deleteMessages(messagesToDelete).queue(
                    success -> {
                        String response = targetUser != null ?
                            "✅ Deleted " + messagesToDelete.size() + " message(s) from " + targetUser.getEffectiveName() :
                            "✅ Deleted " + messagesToDelete.size() + " message(s)";
                        event.getHook().sendMessage(response).queue();
                        
                        // Log the action
                        String reason = targetUser != null ? 
                            "Purged " + messagesToDelete.size() + " messages from " + targetUser.getEffectiveName() :
                            "Purged " + messagesToDelete.size() + " messages";
                        sendToLogChannel(event, guildId, "PURGE", channel.getName(), moderatorName, reason);
                    },
                    error -> {
                        event.getHook().sendMessage("❌ Failed to delete messages. Messages older than 2 weeks cannot be bulk deleted.").queue();
                    }
                );
            }
        }, error -> {
            event.getHook().sendMessage("❌ Failed to retrieve messages.").queue();
        });
    }

    private void handleSlowmodeCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has manage channel permission
        if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("❌ You do not have permission to manage channels.").setEphemeral(true).queue();
            return;
        }

        int seconds = event.getOption("seconds").getAsInt();

        if (seconds < 0 || seconds > 21600) { // Max 6 hours
            event.reply("❌ Slowmode delay must be between 0 and 21600 seconds (6 hours).").setEphemeral(true).queue();
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        String moderatorName = event.getMember().getEffectiveName();

        channel.getManager().setSlowmode(seconds).queue(
            success -> {
                String response = seconds == 0 ?
                    "✅ Slowmode disabled in " + channel.getAsMention() :
                    "✅ Slowmode set to " + seconds + " seconds in " + channel.getAsMention();
                event.reply(response).queue();
                
                // Log the action
                String reason = seconds == 0 ? "Disabled slowmode" : "Set slowmode to " + seconds + " seconds";
                sendToLogChannel(event, guildId, "SLOWMODE", channel.getName(), moderatorName, reason);
            },
            error -> {
                event.reply("❌ Failed to set slowmode. Please try again.").setEphemeral(true).queue();
            }
        );
    }

    private void sendToLogChannel(SlashCommandInteractionEvent event, String guildId, 
                                 String actionType, String targetName, String moderatorName, String reason) {
        if (handler.hasLogChannel(guildId)) {
            String logChannelId = handler.getLogChannelID(guildId);
            if (!logChannelId.equals("Couldnt find a Log Channel") && !logChannelId.equals("Error")) {
                TextChannel logChannel = event.getGuild().getTextChannelById(logChannelId);
                if (logChannel != null) {
                    String emoji;
                    Color embedColor;
                    switch (actionType) {
                        case "KICK": emoji = "🦶"; embedColor = Color.ORANGE; break;
                        case "BAN": emoji = "🔨"; embedColor = Color.RED; break;
                        case "UNBAN": emoji = "🔓"; embedColor = Color.GREEN; break;
                        case "PURGE": emoji = "🧹"; embedColor = Color.YELLOW; break;
                        case "SLOWMODE": emoji = "🐌"; embedColor = Color.BLUE; break;
                        case "UNTIMEOUT": emoji = "⏰"; embedColor = Color.GREEN; break;
                        default:
                            if (actionType.startsWith("TIMEOUT")) {
                                emoji = "⏱️";
                                embedColor = Color.ORANGE;
                            } else {
                                emoji = "⚖️"; // Default moderation emoji
                                embedColor = Color.GRAY;
                            }
                            break;
                    }
                    
                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle(emoji + " " + actionType)
                            .setDescription(emoji + " " + targetName)
                            .addField("Moderator", moderatorName, true)
                            .addField("Reason", reason, true)
                            .setColor(embedColor)
                            .setTimestamp(java.time.Instant.now());
                    
                    logChannel.sendMessageEmbeds(embed.build()).queue();
                }
            }
        }
    }
}