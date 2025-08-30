package org.ToastiCodingStuff.Delta;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WarnCommandListener extends ListenerAdapter {

    private final databaseHandler handler;

    public WarnCommandListener(databaseHandler handler) {
        this.handler = handler;
    }

    @Override
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
            event.reply("Warning issued to " + targetMember.getAsMention() + " for: " + reason +
                    "\nWarning ID: " + warningId +
                    (expiresAt != null ? "\nExpires: " + expiresAt : "")).queue();

            // Insert moderation action using new method
            handler.insertModerationAction(guildId, userId, moderatorId, "WARN", reason, null, expiresAt);
        } else {
            event.reply("Failed to issue warning. Please try again or contact an administrator.").setEphemeral(true).queue();
        }
    }

    private void handleSetWarnSettingsCommand(SlashCommandInteractionEvent event, String guildId) {
        int maxWarns = event.getOption("max_warns").getAsInt();
        int minutesMuted = event.getOption("minutes_muted").getAsInt();
        Role muteRole = event.getOption("mute_role").getAsRole();
        int warnTimeHours = event.getOption("warn_time_hours") != null ?
                event.getOption("warn_time_hours").getAsInt() : 24;

        // Use existing setWarnSettings method (already updated to use proper parameters)
        handler.setWarnSettings(guildId, maxWarns, minutesMuted, muteRole.getId(), warnTimeHours);

        event.reply("Warn settings updated successfully!\n" +
                "Max Warns: " + maxWarns + "\n" +
                "Mute Duration: " + minutesMuted + " minutes\n" +
                "Mute Role: " + muteRole.getAsMention() + "\n" +
                "Warning Expiry: " + warnTimeHours + " hours").queue();
    }

    private void handleGetWarnSettingsCommand(SlashCommandInteractionEvent event, String guildId) {
        if (!handler.hasWarnSystemSettings(guildId)) {
            event.reply("No warn system settings configured for this server. Use `/set-warn-settings` to configure.").setEphemeral(true).queue();
            return;
        }

        // Use existing getter methods
        int maxWarns = handler.getMaxWarns(guildId);
        int timeMuted = handler.getTimeMuted(guildId);
        String roleId = handler.getWarnRoleID(guildId);
        int warnTimeHours = handler.getWarnTimeHours(guildId);

        Role muteRole = event.getGuild().getRoleById(roleId);
        String roleMention = muteRole != null ? muteRole.getAsMention() : "Role not found";

        event.reply("**Current Warn Settings:**\n" +
                "Max Warns: " + maxWarns + "\n" +
                "Mute Duration: " + timeMuted + " minutes\n" +
                "Mute Role: " + roleMention + "\n" +
                "Warning Expiry: " + warnTimeHours + " hours").queue();
    }
}
