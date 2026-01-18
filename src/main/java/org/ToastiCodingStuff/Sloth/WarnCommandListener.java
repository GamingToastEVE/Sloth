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

    private String getLang(String guildId) {
        return handler.getGuildLanguage(guildId);
    }

    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("warn")) {
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            return;
        }

        String guildId = event.getGuild().getId();

        switch (subcommand) {
            case "user":
                if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {return;}
                handler.insertOrUpdateGlobalStatistic("warn-user");
                handleWarnCommand(event, guildId);
                break;
            case "settings-set":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("warn-settings-set");
                handleSetWarnSettingsCommand(event, guildId);
                break;
            case "settings-get":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("warn-settings-get");
                handleGetWarnSettingsCommand(event, guildId);
                break;
        }
    }

    private void handleWarnCommand(SlashCommandInteractionEvent event, String guildId) {
        String lang = getLang(guildId);
        Member targetMember = event.getOption("user").getAsMember();
        String reason = event.getOption("reason").getAsString();
        String severity = event.getOption("severity") != null ? event.getOption("severity").getAsString() : "MEDIUM";

        if (targetMember == null) {
            event.reply(LocaleManager.getMessage(lang, "warn.user_not_found")).setEphemeral(true).queue();
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
                                    
                                    // Update statistics
                                    handler.incrementTimeoutsPerformed(guildId);
                                    
                                    // Update user statistics
                                    handler.incrementUserTimeoutsReceived(guildId, userId);
                                    handler.incrementUserTimeoutsPerformed(guildId, moderatorId);
                                },
                                error -> {
                                    // Handle timeout failure silently - warning was still issued
                                }
                            );
                        timeoutMessage = LocaleManager.getMessage(lang, "warn.timeout_applied", timeoutMinutes, activeWarnings, maxWarns);
                    } else {
                        timeoutMessage = LocaleManager.getMessage(lang, "warn.timeout_no_permission", activeWarnings, maxWarns);
                    }
                }
            }
            
            String replyMessage = LocaleManager.getMessage(lang, "warn.success", targetMember.getAsMention(), reason, warningId);
            if (expiresAt != null) {
                replyMessage += LocaleManager.getMessage(lang, "warn.expires", expiresAt);
            }
            replyMessage += timeoutMessage;
            event.reply(replyMessage).queue();

            // Insert moderation action using new method
            handler.insertModerationAction(guildId, userId, moderatorId, "WARN", reason, null, expiresAt);
            
            // Update statistics for warnings issued
            handler.incrementWarningsIssued(guildId);
            
            // Update user statistics
            handler.incrementUserWarningsReceived(guildId, userId);
            handler.incrementUserWarningsIssued(guildId, moderatorId);
            
            // Send audit log entry to log channel
            handler.sendAuditLogEntry(event.getGuild(), "WARN", targetMember.getEffectiveName(), 
                    event.getMember().getEffectiveName(), reason);
        } else {
            event.reply(LocaleManager.getMessage(lang, "warn.failed")).setEphemeral(true).queue();
        }
    }

    private void handleSetWarnSettingsCommand(SlashCommandInteractionEvent event, String guildId) {
        String lang = getLang(guildId);
        int maxWarns = event.getOption("max_warns").getAsInt();
        int timeoutMinutes = event.getOption("timeout_minutes").getAsInt();
        int warnTimeHours = event.getOption("warn_time_hours") != null ?
                event.getOption("warn_time_hours").getAsInt() : 24;

        // Validate timeout duration (max 28 days = 40320 minutes)
        if (timeoutMinutes < 1 || timeoutMinutes > 40320) {
            event.reply(LocaleManager.getMessage(lang, "warn.settings.invalid_timeout")).setEphemeral(true).queue();
            return;
        }

        // Use existing setWarnSettings method but pass null for roleID since we don't use mute roles anymore
        handler.setWarnSettings(guildId, maxWarns, timeoutMinutes, null, warnTimeHours);

        event.reply(LocaleManager.getMessage(lang, "warn.settings.updated", maxWarns, timeoutMinutes, warnTimeHours)).queue();
    }

    private void handleGetWarnSettingsCommand(SlashCommandInteractionEvent event, String guildId) {
        String lang = getLang(guildId);
        if (!handler.hasWarnSystemSettings(guildId)) {
            event.reply(LocaleManager.getMessage(lang, "warn.settings.not_configured")).setEphemeral(true).queue();
            return;
        }

        // Use existing getter methods
        int maxWarns = handler.getMaxWarns(guildId);
        int timeoutMinutes = handler.getTimeMuted(guildId);
        int warnTimeHours = handler.getWarnTimeHours(guildId);

        event.reply(LocaleManager.getMessage(lang, "warn.settings.current", maxWarns, timeoutMinutes, warnTimeHours)).queue();
    }
}
