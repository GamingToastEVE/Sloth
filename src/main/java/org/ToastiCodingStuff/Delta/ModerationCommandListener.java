package org.ToastiCodingStuff.Delta;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

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
                
                // Send to log channel if configured
                sendToLogChannel(event, guildId, "BAN", targetName, moderatorName, reason);
            },
            error -> {
                event.reply("❌ Failed to ban " + targetName + ". Please try again.").setEphemeral(true).queue();
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
                    String emoji = actionType.equals("KICK") ? "🦶" : "🔨";
                    String logMessage = String.format("%s **%s** | %s %s\n**Moderator:** %s\n**Reason:** %s",
                            emoji, actionType, emoji, targetName, moderatorName, reason);
                    logChannel.sendMessage(logMessage).queue();
                }
            }
        }
    }
}