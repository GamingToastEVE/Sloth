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
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("mod")) {
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            return;
        }

        event.deferReply().setEphemeral(true).queue();

        String guildId = event.getGuild().getId();

        switch (subcommand) {
            case "kick":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {return;}
                handleKickCommand(event, guildId);
                break;
            case "ban":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {return;}
                handleBanCommand(event, guildId);
                break;
            case "unban":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {return;}
                handleUnbanCommand(event, guildId);
                break;
            case "timeout":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {return;}
                handleTimeoutCommand(event, guildId);
                break;
            case "untimeout":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {return;}
                handleUntimeoutCommand(event, guildId);
                break;
            case "purge":
                if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {return;}
                handlePurgeCommand(event, guildId);
                break;
            case "slowmode":
                if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {return;}
                handleSlowmodeCommand(event, guildId);
                break;
        }
    }

    private void handleKickCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has kick permissions
        if (!event.getMember().hasPermission(Permission.KICK_MEMBERS)) {
            event.getHook().sendMessage(t(guildId, "moderation.no_permission")).setEphemeral(true).queue();
            return;
        }

        if (event.getOption("user") == null) {
            event.getHook().sendMessage(t(guildId, "moderation.specify_user")).setEphemeral(true).queue();
            return;
        }

        Member targetMember = event.getOption("user").getAsMember();
        if (targetMember == null) {
            event.getHook().sendMessage(t(guildId, "moderation.user_not_found")).setEphemeral(true).queue();
            return;
        }

        // Check if the target can be kicked
        if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
            event.getHook().sendMessage(t(guildId, "moderation.cannot_interact")).setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(targetMember)) {
            event.getHook().sendMessage(t(guildId, "moderation.you_cannot_interact")).setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("reason") != null ? 
                event.getOption("reason").getAsString() : t(guildId, "moderation.no_reason");

        String oderId = targetMember.getId();
        String moderatorId = event.getMember().getId();
        String targetName = targetMember.getEffectiveName();
        String moderatorName = event.getMember().getEffectiveName();

        // Insert or update user data
        handler.insertOrUpdateUser(oderId, targetName,
                targetMember.getUser().getDiscriminator(),
                targetMember.getUser().getAvatarUrl());
        
        handler.insertOrUpdateUser(moderatorId, moderatorName,
                event.getMember().getUser().getDiscriminator(),
                event.getMember().getUser().getAvatarUrl());

        // Kick the member
        targetMember.kick().reason(reason).queue(
            success -> {
                // Kick successful
                event.getHook().sendMessage(t(guildId, "moderation.kick_success", targetName)).queue();

                // Log moderation action
                handler.insertModerationAction(guildId, oderId, moderatorId, "KICK", reason, null, null);
                
                // Update statistics
                handler.incrementKicksPerformed(guildId);
                
                // Update user statistics
                handler.incrementUserKicksReceived(guildId, oderId);
                handler.incrementUserKicksPerformed(guildId, moderatorId);
                
                // Send to log channel if configured
                sendToLogChannel(event, guildId, "KICK", targetName, moderatorName, reason);
            },
            error -> {
                event.getHook().sendMessage(t(guildId, "general.error")).setEphemeral(true).queue();
            }
        );
    }

    private void handleBanCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has ban permissions
        if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.getHook().sendMessage(t(guildId, "moderation.no_permission")).setEphemeral(true).queue();
            return;
        }

        if (event.getOption("user") == null) {
            event.getHook().sendMessage(t(guildId, "moderation.specify_user")).setEphemeral(true).queue();
            return;
        }

        Member targetMember = event.getOption("user").getAsMember();
        if (targetMember == null) {
            event.getHook().sendMessage(t(guildId, "moderation.user_not_found")).setEphemeral(true).queue();
            return;
        }

        // Check if the target can be banned
        if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
            event.getHook().sendMessage(t(guildId, "moderation.cannot_interact")).setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(targetMember)) {
            event.getHook().sendMessage(t(guildId, "moderation.you_cannot_interact")).setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("reason") != null ? 
                event.getOption("reason").getAsString() : t(guildId, "moderation.no_reason");

        String oderId = targetMember.getId();
        String moderatorId = event.getMember().getId();
        String targetName = targetMember.getEffectiveName();
        String moderatorName = event.getMember().getEffectiveName();

        // Insert or update user data
        handler.insertOrUpdateUser(oderId, targetName,
                targetMember.getUser().getDiscriminator(),
                targetMember.getUser().getAvatarUrl());
        
        handler.insertOrUpdateUser(moderatorId, moderatorName,
                event.getMember().getUser().getDiscriminator(),
                event.getMember().getUser().getAvatarUrl());

        // Ban the member (0 means no message deletion)
        targetMember.ban(0, TimeUnit.SECONDS).reason(reason).queue(
            success -> {
                // Ban successful
                event.getHook().sendMessage(t(guildId, "moderation.ban_success", targetName) + " " + t(guildId, "moderation.ban_reason", reason)).queue();

                // Log moderation action
                handler.insertModerationAction(guildId, oderId, moderatorId, "BAN", reason, null, null);
                
                // Update statistics
                handler.incrementBansPerformed(guildId);
                
                // Update user statistics
                handler.incrementUserBansReceived(guildId, oderId);
                handler.incrementUserBansPerformed(guildId, moderatorId);
                
                // Send to log channel if configured
                sendToLogChannel(event, guildId, "BAN", targetName, moderatorName, reason);
            },
            error -> {
                event.getHook().sendMessage("❌ Failed to ban " + targetName + ". Please try again.").setEphemeral(true).queue();
            }
        );
    }

    private void handleUnbanCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has ban permissions
        if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply(t(guildId, "moderation.no_permission")).setEphemeral(true).queue();
            return;
        }

        String oderId = event.getOption("userid").getAsString();
        String reason = event.getOption("reason") != null ?
                event.getOption("reason").getAsString() : t(guildId, "moderation.no_reason");

        String moderatorId = event.getMember().getId();
        String moderatorName = event.getMember().getEffectiveName();

        // Unban the user
        event.getGuild().unban(net.dv8tion.jda.api.entities.UserSnowflake.fromId(oderId)).reason(reason).queue(
            success -> {
                event.getHook().sendMessage(t(guildId, "moderation.unban_success", oderId)).queue();

                // Insert or update moderator data
                handler.insertOrUpdateUser(moderatorId, moderatorName,
                        event.getMember().getUser().getDiscriminator(),
                        event.getMember().getUser().getAvatarUrl());
                
                // Log moderation action
                handler.insertModerationAction(guildId, oderId, moderatorId, "UNBAN", reason, null, null);
                // Update statistics
                
                // Send to log channel if configured
                sendToLogChannel(event, guildId, "UNBAN", "User ID: " + oderId, moderatorName, reason);
            },
            error -> {
                event.getHook().sendMessage(t(guildId, "general.error")).setEphemeral(true).queue();
            }
        );
    }

    private void handleTimeoutCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has moderate members permission
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.getHook().sendMessage(t(guildId, "moderation.no_permission")).setEphemeral(true).queue();
            return;
        }

        Member targetMember = event.getOption("user").getAsMember();
        int minutes = event.getOption("minutes").getAsInt();
        String reason = event.getOption("reason") != null ? 
                event.getOption("reason").getAsString() : t(guildId, "moderation.no_reason");

        if (targetMember == null) {
            event.reply("❌ User not found in this server.").setEphemeral(true).queue();
            return;
        }

        // Validate timeout duration (max 28 days = 40320 minutes)
        if (minutes < 1 || minutes > 40320) {
            event.getHook().sendMessage("❌ Timeout duration must be between 1 and 40320 minutes (28 days).").setEphemeral(true).queue();
            return;
        }

        // Check if the target can be timed out
        if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
            event.getHook().sendMessage("❌ I cannot timeout this user due to role hierarchy.").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().canInteract(targetMember)) {
            event.getHook().sendMessage("❌ You cannot timeout this user due to role hierarchy.").setEphemeral(true).queue();
            return;
        }

        String oderId = targetMember.getId();
        String moderatorId = event.getMember().getId();
        String targetName = targetMember.getEffectiveName();
        String moderatorName = event.getMember().getEffectiveName();

        // Apply timeout
        Duration duration = Duration.ofMinutes(minutes);
        targetMember.timeoutFor(duration).reason(reason).queue(
            success -> {
                event.getHook().sendMessage(t(guildId, "moderation.timeout_success", targetName, minutes + " min")).queue();

                // Insert or update user data
                handler.insertOrUpdateUser(oderId, targetName, 
                        targetMember.getUser().getDiscriminator(), 
                        targetMember.getUser().getAvatarUrl());
                
                // Log moderation action
                String expiresAt = java.time.LocalDateTime.now().plusMinutes(minutes)
                        .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                handler.insertModerationAction(guildId, oderId, moderatorId, "TIMEOUT", reason, duration.toString(), expiresAt);
                
                // Update statistics
                handler.incrementTimeoutsPerformed(guildId);
                handler.incrementUserTimeoutsReceived(guildId, oderId);
                handler.incrementUserTimeoutsPerformed(guildId, moderatorId);
                
                // Send to log channel if configured
                sendToLogChannel(event, guildId, "TIMEOUT (" + minutes + "m)", targetName, moderatorName, reason);
            },
            error -> {
                event.getHook().sendMessage(t(guildId, "general.error")).setEphemeral(true).queue();
            }
        );
    }

    private void handleUntimeoutCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has moderate members permission
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.getHook().sendMessage(t(guildId, "moderation.no_permission")).setEphemeral(true).queue();
            return;
        }

        Member targetMember = event.getOption("user").getAsMember();
        String reason = event.getOption("reason") != null ? 
                event.getOption("reason").getAsString() : t(guildId, "moderation.no_reason");

        if (targetMember == null) {
            event.getHook().sendMessage(t(guildId, "moderation.user_not_found")).setEphemeral(true).queue();
            return;
        }

        // Check if user is actually timed out
        if (!targetMember.isTimedOut()) {
            event.getHook().sendMessage(t(guildId, "general.error")).setEphemeral(true).queue();
            return;
        }

        String oderId = targetMember.getId();
        String moderatorId = event.getMember().getId();
        String targetName = targetMember.getEffectiveName();
        String moderatorName = event.getMember().getEffectiveName();

        // Remove timeout
        targetMember.removeTimeout().reason(reason).queue(
            success -> {
                event.getHook().sendMessage(t(guildId, "moderation.untimeout_success", targetName)).queue();

                // Insert or update user data
                handler.insertOrUpdateUser(oderId, targetName, 
                        targetMember.getUser().getDiscriminator(), 
                        targetMember.getUser().getAvatarUrl());
                
                // Log moderation action
                handler.insertModerationAction(guildId, oderId, moderatorId, "UNTIMEOUT", reason, null, null);
                
                // Update statistics
                handler.incrementUntimeoutsPerformed(guildId);
                handler.incrementUserUntimeoutsReceived(guildId, oderId);
                handler.incrementUserUntimeoutsPerformed(guildId, moderatorId);
                
                // Send to log channel if configured
                sendToLogChannel(event, guildId, "UNTIMEOUT", targetName, moderatorName, reason);
            },
            error -> {
                event.getHook().sendMessage("❌ Failed to remove timeout from " + targetName + ". Please try again.").setEphemeral(true).queue();
            }
        );
    }

    private void handlePurgeCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has manage messages permission
        if (!event.getMember().hasPermission(Permission.MESSAGE_MANAGE)) {
            event.getHook().sendMessage("❌ You do not have permission to manage messages.").setEphemeral(true).queue();
            return;
        }

        int amount = event.getOption("amount").getAsInt();
        Member targetUser = event.getOption("user") != null ? event.getOption("user").getAsMember() : null;

        if (amount < 1 || amount > 100) {
            event.getHook().sendMessage("❌ Amount must be between 1 and 100 messages.").setEphemeral(true).queue();
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        String moderatorName = event.getMember().getEffectiveName();

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
                        event.getHook().sendMessage(t(guildId, "moderation.purge_success", messagesToDelete.size())).queue();

                        // Log the action
                        String reason = targetUser != null ? 
                            "Purged " + messagesToDelete.size() + " messages from " + targetUser.getEffectiveName() :
                            "Purged " + messagesToDelete.size() + " messages";
                        sendToLogChannel(event, guildId, "PURGE", channel.getName(), moderatorName, reason);
                    },
                    error -> {
                        event.getHook().sendMessage(t(guildId, "general.error")).queue();
                    }
                );
            } else {
                channel.deleteMessages(messagesToDelete).queue(
                    success -> {
                        event.getHook().sendMessage(t(guildId, "moderation.purge_success", messagesToDelete.size())).queue();

                        // Log the action
                        String reason = targetUser != null ? 
                            "Purged " + messagesToDelete.size() + " messages from " + targetUser.getEffectiveName() :
                            "Purged " + messagesToDelete.size() + " messages";
                        sendToLogChannel(event, guildId, "PURGE", channel.getName(), moderatorName, reason);
                    },
                    error -> {
                        event.getHook().sendMessage(t(guildId, "general.error")).queue();
                    }
                );
            }
        }, error -> {
            event.getHook().sendMessage(t(guildId, "general.error")).queue();
        });
    }

    private void handleSlowmodeCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has manage channel permission
        if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.getHook().sendMessage(t(guildId, "moderation.no_permission")).setEphemeral(true).queue();
            return;
        }

        int seconds = event.getOption("seconds").getAsInt();

        if (seconds < 0 || seconds > 21600) { // Max 6 hours
            event.getHook().sendMessage(t(guildId, "general.invalid_input")).setEphemeral(true).queue();
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        String moderatorName = event.getMember().getEffectiveName();

        channel.getManager().setSlowmode(seconds).queue(
            success -> {
                String response = seconds == 0 ?
                    t(guildId, "moderation.slowmode_disabled") :
                    t(guildId, "moderation.slowmode_success", seconds);
                event.getHook().sendMessage(response).queue();
                
                // Log the action
                String reason = seconds == 0 ? "Disabled slowmode" : "Set slowmode to " + seconds + " seconds";
                sendToLogChannel(event, guildId, "SLOWMODE", channel.getName(), moderatorName, reason);
            },
            error -> {
                event.getHook().sendMessage(t(guildId, "general.error")).setEphemeral(true).queue();
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