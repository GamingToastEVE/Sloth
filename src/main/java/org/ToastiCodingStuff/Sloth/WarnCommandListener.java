package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class WarnCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public WarnCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        switch (event.getName()) {
            case "warn":
                handleWarnCommand(event, guildId);
                break;
            case "set-warn-settings":
                handleSetWarnSettingsCommand(event, guildId);
                break;
            case "get-warn-settings":
                handleGetWarnSettingsCommand(event, guildId);
                break;
        }
    }

    private void handleWarnCommand(SlashCommandInteractionEvent event, String guildId) {
        Member targetMember = event.getOption("user").getAsMember();
        String reason = event.getOption("reason").getAsString();
        String severity = event.getOption("severity") != null ? event.getOption("severity").getAsString() : "MEDIUM";

        if (targetMember == null) {
            event.reply("User not found in this server.").setEphemeral(true).queue();
            return;
        }

        String userId = targetMember.getId();
        String moderatorId = event.getMember().getId();

        // Insert or update user data using new method
        handler.insertOrUpdateUser(userId, targetMember.getEffectiveName(),
                targetMember.getUser().getDiscriminator(),
                targetMember.getUser().getAvatarUrl());

        // Insert or update moderator data
        handler.insertOrUpdateUser(moderatorId, event.getMember().getEffectiveName(),
                event.getUser().getDiscriminator(),
                event.getUser().getAvatarUrl());

        // Calculate expiration time based on warn settings
        String expiresAt = null;
        if (handler.hasWarnSystemSettings(guildId)) {
            int warnTimeHours = handler.getWarnTimeHours(guildId);
            if (warnTimeHours > 0) {
                LocalDateTime expiration = LocalDateTime.now().plusHours(warnTimeHours);
                expiresAt = expiration.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        }

        // Use new insertWarning method instead of legacy approach
        int warningId = handler.insertWarning(guildId, userId, moderatorId, reason, severity, expiresAt);

        if (warningId > 0) {
            // Check if user has reached max warnings and apply timeout if needed
            int activeWarnings = handler.getActiveWarningsCount(guildId, userId);
            String timeoutMessage = "";
            
            if (handler.hasWarnSystemSettings(guildId)) {
                int maxWarns = handler.getMaxWarns(guildId);
                int timeoutMinutes = handler.getTimeMuted(guildId);
                
                if (activeWarnings >= maxWarns) {
                    // Check timeout permissions
                    if (event.getGuild().getSelfMember().hasPermission(Permission.MODERATE_MEMBERS) &&
                        event.getGuild().getSelfMember().canInteract(targetMember)) {
                        
                        // Apply Discord timeout
                        Duration timeoutDuration = Duration.ofMinutes(timeoutMinutes);
                        targetMember.timeoutFor(timeoutDuration)
                            .reason("Maximum warnings reached (" + activeWarnings + "/" + maxWarns + ")")
                            .queue(
                                success -> {
                                    // Log timeout action
                                    String timeoutExpiresAt = LocalDateTime.now().plusMinutes(timeoutMinutes)
                                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                                    handler.insertModerationAction(guildId, userId, moderatorId, "TIMEOUT", 
                                        "Maximum warnings reached", timeoutDuration.toString(), timeoutExpiresAt);
                                },
                                error -> {
                                    // Handle timeout failure silently - warning was still issued
                                }
                            );
                        timeoutMessage = "\n⏱️ **User has been timed out for " + timeoutMinutes + " minutes** (reached " + activeWarnings + "/" + maxWarns + " warnings)";
                    } else {
                        timeoutMessage = "\n⚠️ **Warning:** User has reached maximum warnings (" + activeWarnings + "/" + maxWarns + ") but I cannot timeout them due to permissions";
                    }
                }
            }
            
            event.reply("Warning issued to " + targetMember.getAsMention() + " for: " + reason +
                    "\nWarning ID: " + warningId +
                    (expiresAt != null ? "\nExpires: " + expiresAt : "") + timeoutMessage).queue();

            // Insert moderation action using new method
            handler.insertModerationAction(guildId, userId, moderatorId, "WARN", reason, null, expiresAt);
            
            // Update statistics for warnings issued
            handler.incrementWarningsIssued(guildId);
            
            // Send audit log entry to log channel
            handler.sendAuditLogEntry(event.getGuild(), "WARN", targetMember.getEffectiveName(), 
                    event.getMember().getEffectiveName(), reason);
        } else {
            event.reply("Failed to issue warning. Please try again or contact an administrator.").setEphemeral(true).queue();
        }
    }

    private void handleSetWarnSettingsCommand(SlashCommandInteractionEvent event, String guildId) {
        int maxWarns = event.getOption("max_warns").getAsInt();
        int timeoutMinutes = event.getOption("timeout_minutes").getAsInt();
        int warnTimeHours = event.getOption("warn_time_hours") != null ?
                event.getOption("warn_time_hours").getAsInt() : 24;

        // Validate timeout duration (max 28 days = 40320 minutes)
        if (timeoutMinutes < 1 || timeoutMinutes > 40320) {
            event.reply("❌ Timeout duration must be between 1 and 40320 minutes (28 days).").setEphemeral(true).queue();
            return;
        }

        // Use existing setWarnSettings method but pass null for roleID since we don't use mute roles anymore
        handler.setWarnSettings(guildId, maxWarns, timeoutMinutes, null, warnTimeHours);

        event.reply("Warn settings updated successfully!\n" +
                "Max Warns: " + maxWarns + "\n" +
                "Timeout Duration: " + timeoutMinutes + " minutes\n" +
                "Warning Expiry: " + warnTimeHours + " hours").queue();
    }

    private void handleGetWarnSettingsCommand(SlashCommandInteractionEvent event, String guildId) {
        if (!handler.hasWarnSystemSettings(guildId)) {
            event.reply("No warn system settings configured for this server. Use `/set-warn-settings` to configure.").setEphemeral(true).queue();
            return;
        }

        // Use existing getter methods
        int maxWarns = handler.getMaxWarns(guildId);
        int timeoutMinutes = handler.getTimeMuted(guildId);
        int warnTimeHours = handler.getWarnTimeHours(guildId);

        event.reply("**Current Warn Settings:**\n" +
                "Max Warns: " + maxWarns + "\n" +
                "Timeout Duration: " + timeoutMinutes + " minutes\n" +
                "Warning Expiry: " + warnTimeHours + " hours\n\n" +
                "ℹ️ When users reach max warnings, they will be automatically timed out using Discord's built-in timeout feature.").queue();
    }
}
