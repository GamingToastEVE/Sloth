package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.json.JSONObject;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class WarnCommandListener extends ListenerAdapter implements SlashCommandHandler {

    private final DatabaseHandler handler;

    public WarnCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public String[] getHandledCommands() {
        return new String[]{"warn"};
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
    public void onUserContextInteraction(net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent event) {
        if (!event.getName().equals("warning")) {
            return;
        }
        // Bug fix: null-check for guild
        if (event.getGuild() == null) return;
        handleWarnCommand(event, event.getGuild().getId());
    }

    @Override
    public void handleSlashCommand(SlashCommandInteractionEvent event) {

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            return;
        }

        // Bug fix: null-check for guild and member
        if (event.getGuild() == null || event.getMember() == null) return;

        String guildId = event.getGuild().getId();

        switch (subcommand) {
            case "user":
                if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {return;}
                handleWarnCommand(event, guildId);
                break;
            case "list":
                if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) return;
                handleListWarningsCommand(event, guildId);
                break;
            case "settings-set":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handleSetWarnSettingsCommand(event, guildId);
                break;
            case "settings-get":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handleGetWarnSettingsCommand(event, guildId);
                break;
        }
    }

    private void handleListWarningsCommand(SlashCommandInteractionEvent event, String guildId) {
        // Bug fix: null-check for option
        if (event.getOption("user") == null) {
            event.reply(t(guildId, "moderation.specify_user")).setEphemeral(true).queue();
            return;
        }
        Member targetMember = event.getOption("user").getAsMember();
        if (targetMember == null) {
            event.reply(t(guildId, "moderation.user_not_found")).setEphemeral(true).queue();
            return;
        }

        sendWarnListEmbed(event, guildId, targetMember.getUser());
    }

    // --- HELPER METHODE: Embed & Menü bauen ---
    // Bug fix: Unterscheidung zwischen erstem Reply und Edit-Reply
    private void sendWarnListEmbed(IReplyCallback event, String guildId, User targetUser) {
        List<DatabaseHandler.WarningData> warnings = handler.getUserActiveWarnings(guildId, targetUser.getId());

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(t(guildId, "moderation.warnings_title", targetUser.getName()));
        embed.setColor(Color.ORANGE);
        embed.setThumbnail(targetUser.getAvatarUrl());

        if (warnings.isEmpty()) {
            embed.setDescription(t(guildId, "moderation.no_warnings"));
            embed.setColor(Color.GREEN);
            // Bug fix: Alle Pfade nutzen replyEmbeds – da wir nach message.delete() immer
            // eine frische Interaction haben, ist reply() korrekt.
            event.replyEmbeds(embed.build()).setComponents().setEphemeral(true).queue();
            return;
        }

        // Dropdown Menü erstellen
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("warn_delete_menu:" + targetUser.getId())
                .setPlaceholder("🗑️ Select a warning to view details.")
                .setMinValues(1)
                .setMaxValues(1);

        StringBuilder desc = new StringBuilder();

        // Discord Limits beachten: Max 25 Optionen im Dropdown
        int count = 0;
        for (DatabaseHandler.WarningData warn : warnings) {
            if (count >= 25) break;

            desc.append("**ID: ").append(warn.id).append("** | ")
                    .append(warn.date).append("\n")
                    .append("Reason: `").append(warn.reason).append("`\n")
                    .append("Mod: <@").append(warn.moderatorId).append(">\n\n");

            String label = "ID " + warn.id + ": " + warn.reason;
            if (label.length() > 100) label = label.substring(0, 97) + "...";

            menuBuilder.addOption(label, String.valueOf(warn.id));
            count++;
        }

        embed.setDescription(desc.toString());
        embed.setFooter(t(guildId, "moderation.breadcrumb_warnings_list", targetUser.getName()));

        event.replyEmbeds(embed.build()).setComponents(ActionRow.of(menuBuilder.build())).setEphemeral(true).queue();
    }

    @Override
    public void onButtonInteraction(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        // Bug fix: null-checks for guild and member
        if (event.getGuild() == null || event.getMember() == null) return;

        String componentId = event.getComponentId();

        // Warn Lösch Bestätigung
        if (componentId.startsWith("warn_delete_confirm:")) {
            String gId = event.getGuild().getId();
            if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
                event.reply(t(gId, "moderation.no_permission")).setEphemeral(true).queue();
                return;
            }

            String[] parts = componentId.split(":");
            // Bug fix: Bounds-Check vor Zugriff auf parts[1] und parts[2]
            if (parts.length < 3) {
                event.reply(t(gId, "moderation.warn_invalid_id")).setEphemeral(true).queue();
                return;
            }

            String warnIdStr = parts[1];
            String targetUserId = parts[2];

            try {
                int warnId = Integer.parseInt(warnIdStr);
                boolean success = handler.deactivateWarning(warnId, event.getGuild().getId());

                // Bug fix: Erst Nachricht löschen, dann ein einziges reply() senden.
                // Danach Warnliste als neues Reply holen (über retrieveUserById + queue).
                event.getMessage().delete().queue();

                if (success) {
                    event.getJDA().retrieveUserById(targetUserId).queue(
                        targetUser -> {
                            if (targetUser != null) {
                                // sendWarnListEmbed sendet ein neues replyEmbeds
                                sendWarnListEmbed(event, gId, targetUser);
                            } else {
                                event.reply(t(gId, "moderation.warn_deleted_success")).setEphemeral(true).queue();
                            }
                        },
                        error -> event.reply(t(gId, "moderation.warn_deleted_success")).setEphemeral(true).queue()
                    );
                } else {
                    event.reply(t(gId, "moderation.warn_delete_failed")).setEphemeral(true).queue();
                }
            } catch (NumberFormatException e) {
                event.getMessage().delete().queue();
                event.reply(t(gId, "moderation.warn_invalid_id")).setEphemeral(true).queue();
            }
        }

        // Zurück zur Warnliste
        else if (componentId.startsWith("warn_delete_back:")) {
            String targetUserId = componentId.split(":")[1];
            String guildId = event.getGuild().getId();

            // Bug fix: Blocking .complete() durch .queue() ersetzt + Nachricht erst löschen
            event.getMessage().delete().queue();
            event.getJDA().retrieveUserById(targetUserId).queue(
                targetUser -> {
                    if (targetUser != null) {
                        sendWarnListEmbed(event, guildId, targetUser);
                    } else {
                        event.reply(t(guildId, "moderation.user_not_found")).setEphemeral(true).queue();
                    }
                },
                error -> event.reply(t(guildId, "moderation.user_not_found")).setEphemeral(true).queue()
            );
        }
    }

    // --- NEUES EVENT: Dropdown Interaktion ---
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();

        // Prüfen ob es unser Menü ist (Format: warn_delete_menu:USER_ID)
        if (!componentId.startsWith("warn_delete_menu:")) {
            return;
        }

        // Bug fix: null-checks for guild and member
        if (event.getGuild() == null || event.getMember() == null) return;

        // Berechtigungscheck
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply(t(event.getGuild().getId(), "moderation.no_permission")).setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String targetUserId = componentId.split(":")[1];
        String selectedWarnIdStr = event.getValues().get(0);

        try {
            int warnId = Integer.parseInt(selectedWarnIdStr);
            List<DatabaseHandler.WarningData> warnings = handler.getUserActiveWarnings(guildId, targetUserId);

            int i = 0;
            for (DatabaseHandler.WarningData warns : warnings) {
                i++;
                if (warns.id == warnId) {
                    EmbedBuilder embed = new EmbedBuilder();
                    User targetUser = event.getJDA().getUserById(targetUserId);
                    if (targetUser == null) {
                        embed.setTitle("🗑️ Warning #" + i + " for " + targetUserId);
                    } else {
                        embed.setTitle("🗑️ Warning #" + i + " for " + targetUser.getName());
                        embed.setThumbnail(targetUser.getAvatarUrl());
                    }
                    embed.setColor(Color.RED);
                    embed.addField("Warning ID", String.valueOf(warns.id), false);
                    embed.addField("Date Issued", warns.date, false);
                    embed.addField("Reason", warns.reason, false);
                    embed.addField("Severity", warns.severity, false);
                    embed.addField("Issued by", "<@" + warns.moderatorId + ">", false);
                    if (warns.expiresAt != null) {
                        embed.addField("Expires At", warns.expiresAt, false);
                    } else {
                        embed.addField("Expires At", "Never", false);
                    }
                    if (warns.evidence != null) {
                        embed.setImage(warns.evidence);
                    }
                    embed.setFooter(t(guildId, "moderation.breadcrumb_warning_detail",
                            targetUser != null ? targetUser.getName() : targetUserId, i));
                    Button deleteButton = Button.danger("warn_delete_confirm:" + warnId + ":" + targetUserId, "Delete Warning");
                    Button backButton = Button.secondary("warn_delete_back:" + targetUserId, "Back to Warnings");

                    // Bug fix: Erst Nachricht löschen, dann neues reply() senden
                    event.getMessage().delete().queue();
                    event.replyEmbeds(embed.build())
                            .setComponents(ActionRow.of(backButton, deleteButton))
                            .setEphemeral(true)
                            .queue();
                    return;
                }
            }
            event.reply(t(guildId, "moderation.warn_not_found_entry")).setEphemeral(true).queue();

        } catch (NumberFormatException e) {
            event.reply(t(guildId, "moderation.warn_invalid_id")).setEphemeral(true).queue();
        }
    }

    private void handleWarnCommand(SlashCommandInteractionEvent event, String guildId) {
        // Bug fix: null-checks für Pflichtoptionen
        if (event.getOption("user") == null || event.getOption("reason") == null) {
            event.reply(t(guildId, "moderation.specify_user")).setEphemeral(true).queue();
            return;
        }

        Member targetMember = event.getOption("user").getAsMember();
        String reason = event.getOption("reason").getAsString();
        String severity = event.getOption("severity") != null ? event.getOption("severity").getAsString() : "MEDIUM";
        String evidence = event.getOption("evidence") != null ? event.getOption("evidence").getAsAttachment().getUrl() : null;

        if (targetMember == null) {
            event.reply(t(guildId, "moderation.user_not_found")).setEphemeral(true).queue();
            return;
        }

        // Bug fix: null-check for member (should not happen in guild context, but defensive)
        if (event.getMember() == null) {
            event.reply(t(guildId, "moderation.moderator_not_found")).setEphemeral(true).queue();
            return;
        }

        String userId = targetMember.getId();
        String moderatorId = event.getMember().getId();

        handler.insertOrUpdateUser(userId, targetMember.getEffectiveName(),
                targetMember.getUser().getDiscriminator(),
                targetMember.getUser().getAvatarUrl());

        handler.insertOrUpdateUser(moderatorId, event.getMember().getEffectiveName(),
                event.getUser().getDiscriminator(),
                event.getUser().getAvatarUrl());

        String expiresAt = null;
        if (handler.hasWarnSystemSettings(guildId)) {
            int warnTimeHours = handler.getWarnTimeHours(guildId);
            if (warnTimeHours > 0) {
                LocalDateTime expiration = LocalDateTime.now().plusHours(warnTimeHours);
                expiresAt = expiration.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        }

        List<DatabaseHandler.RoleEventData> data = handler.getRoleEventsByType(guildId, RoleEventType.WARN_THRESHOLD);

        if (!data.isEmpty()) {
            for (DatabaseHandler.RoleEventData eventData : data) {
                int threshold;
                JSONObject triggerDataJson = new JSONObject(eventData.triggerData);
                try {
                    threshold = Integer.parseInt(triggerDataJson.getString("threshold"));
                } catch (NumberFormatException e) {
                    continue;
                }

                int activeWarnings = handler.getActiveWarningsCount(guildId, targetMember.getId());

                if (activeWarnings + 1 == threshold) {
                    Role role = event.getGuild().getRoleById(eventData.roleId);
                    if (role != null) {
                        long durationSeconds = eventData.durationSeconds;
                        handler.addActiveTimer(event.getGuild().getId(), targetMember.getId(), role.getId(), eventData.id, durationSeconds);
                        event.getGuild().addRoleToMember(targetMember, role)
                                .reason("Warn Threshold reached: " + threshold)
                                .queue();
                    }
                }
            }
        }

        int warningId = handler.insertWarning(guildId, userId, moderatorId, reason, severity, expiresAt, evidence);

        if (warningId > 0) {
            int activeWarnings = handler.getActiveWarningsCount(guildId, userId);
            String timeoutMessage = "";

            if (handler.hasWarnSystemSettings(guildId)) {
                int maxWarns = handler.getMaxWarns(guildId);
                int timeoutMinutes = handler.getTimeMuted(guildId);

                if (activeWarnings >= maxWarns) {
                    if (event.getGuild().getSelfMember().hasPermission(Permission.MODERATE_MEMBERS) &&
                        event.getGuild().getSelfMember().canInteract(targetMember)) {

                        Duration timeoutDuration = Duration.ofMinutes(timeoutMinutes);
                        targetMember.timeoutFor(timeoutDuration)
                            .reason("Maximum warnings reached (" + activeWarnings + "/" + maxWarns + ")")
                            .queue(
                                success -> {
                                    String timeoutExpiresAt = LocalDateTime.now().plusMinutes(timeoutMinutes)
                                            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                                    handler.insertModerationAction(guildId, userId, moderatorId, "TIMEOUT",
                                        "Maximum warnings reached", timeoutDuration.toString(), timeoutExpiresAt);
                                    handler.incrementTimeoutsPerformed(guildId);
                                    handler.incrementUserTimeoutsReceived(guildId, userId);
                                    handler.incrementUserTimeoutsPerformed(guildId, moderatorId);
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

            handler.insertModerationAction(guildId, userId, moderatorId, "WARN", reason, null, expiresAt);
            handler.incrementWarningsIssued(guildId);
            handler.incrementUserWarningsReceived(guildId, userId);
            handler.incrementUserWarningsIssued(guildId, moderatorId);
            handler.sendAuditLogEntry(event.getGuild(), "WARN", "", targetMember, event.getMember(), reason);
        } else {
            event.reply(t(guildId, "moderation.warn_issue_failed")).setEphemeral(true).queue();
        }
    }

    private void handleWarnCommand(UserContextInteractionEvent event, String guildId) {
        // Bug fix: null-check for guild and member
        if (event.getGuild() == null || event.getMember() == null) return;

        Member targetMember = event.getTargetMember();
        String reason = t(guildId, "moderation.warned_via_context_menu");
        String severity = t(guildId, "moderation.warned_via_context_menu");
        String evidence = null;

        if (targetMember == null) {
            event.reply("User not found in this server.").setEphemeral(true).queue();
            return;
        }

        String userId = targetMember.getId();
        String moderatorId = event.getMember().getId();

        handler.insertOrUpdateUser(userId, targetMember.getEffectiveName(),
                targetMember.getUser().getDiscriminator(),
                targetMember.getUser().getAvatarUrl());

        handler.insertOrUpdateUser(moderatorId, event.getMember().getEffectiveName(),
                event.getUser().getDiscriminator(),
                event.getUser().getAvatarUrl());

        String expiresAt = null;
        if (handler.hasWarnSystemSettings(guildId)) {
            int warnTimeHours = handler.getWarnTimeHours(guildId);
            if (warnTimeHours > 0) {
                LocalDateTime expiration = LocalDateTime.now().plusHours(warnTimeHours);
                expiresAt = expiration.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        }

        List<DatabaseHandler.RoleEventData> data = handler.getRoleEventsByType(guildId, RoleEventType.WARN_THRESHOLD);

        if (!data.isEmpty()) {
            for (DatabaseHandler.RoleEventData eventData : data) {
                int threshold;
                JSONObject triggerDataJson = new JSONObject(eventData.triggerData);
                try {
                    threshold = Integer.parseInt(triggerDataJson.getString("threshold"));
                } catch (NumberFormatException e) {
                    continue;
                }

                int activeWarnings = handler.getActiveWarningsCount(guildId, targetMember.getId());

                if (activeWarnings + 1 == threshold) {
                    Role role = event.getGuild().getRoleById(eventData.roleId);
                    if (role != null) {
                        long durationSeconds = eventData.durationSeconds;
                        handler.addActiveTimer(event.getGuild().getId(), targetMember.getId(), role.getId(), eventData.id, durationSeconds);
                        event.getGuild().addRoleToMember(targetMember, role)
                                .reason("Warn Threshold reached: " + threshold)
                                .queue();
                    }
                }
            }
        }

        int warningId = handler.insertWarning(guildId, userId, moderatorId, reason, severity, expiresAt, evidence);

        if (warningId > 0) {
            int activeWarnings = handler.getActiveWarningsCount(guildId, userId);
            String timeoutMessage = "";

            if (handler.hasWarnSystemSettings(guildId)) {
                int maxWarns = handler.getMaxWarns(guildId);
                int timeoutMinutes = handler.getTimeMuted(guildId);

                if (activeWarnings >= maxWarns) {
                    if (event.getGuild().getSelfMember().hasPermission(Permission.MODERATE_MEMBERS) &&
                            event.getGuild().getSelfMember().canInteract(targetMember)) {

                        Duration timeoutDuration = Duration.ofMinutes(timeoutMinutes);
                        targetMember.timeoutFor(timeoutDuration)
                                .reason("Maximum warnings reached (" + activeWarnings + "/" + maxWarns + ")")
                                .queue(
                                        success -> {
                                            String timeoutExpiresAt = LocalDateTime.now().plusMinutes(timeoutMinutes)
                                                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                                            handler.insertModerationAction(guildId, userId, moderatorId, "TIMEOUT",
                                                    "Maximum warnings reached", timeoutDuration.toString(), timeoutExpiresAt);
                                            handler.incrementTimeoutsPerformed(guildId);
                                            handler.incrementUserTimeoutsReceived(guildId, userId);
                                            handler.incrementUserTimeoutsPerformed(guildId, moderatorId);
                                        },
                                        error -> {
                                            // Handle timeout failure silently
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

            handler.insertModerationAction(guildId, userId, moderatorId, "WARN", reason, null, expiresAt);
            handler.incrementWarningsIssued(guildId);
            handler.incrementUserWarningsReceived(guildId, userId);
            handler.incrementUserWarningsIssued(guildId, moderatorId);
            handler.sendAuditLogEntry(event.getGuild(), "WARN", "", targetMember, event.getMember(), reason);
        } else {
            event.reply(t(guildId, "moderation.warn_issue_failed")).setEphemeral(true).queue();
        }
    }

    private void handleSetWarnSettingsCommand(SlashCommandInteractionEvent event, String guildId) {
        // Bug fix: null-checks for required options
        if (event.getOption("max_warns") == null || event.getOption("timeout_minutes") == null) {
            event.reply(t(guildId, "moderation.specify_user")).setEphemeral(true).queue();
            return;
        }

        int maxWarns = event.getOption("max_warns").getAsInt();
        int timeoutMinutes = event.getOption("timeout_minutes").getAsInt();
        int warnTimeHours = event.getOption("warn_time_hours") != null ?
                event.getOption("warn_time_hours").getAsInt() : 24;

        if (timeoutMinutes < 1 || timeoutMinutes > 40320) {
            event.reply(t(guildId, "moderation.timeout_invalid_duration")).setEphemeral(true).queue();
            return;
        }

        handler.setWarnSettings(guildId, maxWarns, timeoutMinutes, null, warnTimeHours);

        event.reply("Warn settings updated successfully!\n" +
                "Max Warns: " + maxWarns + "\n" +
                "Timeout Duration: " + timeoutMinutes + " minutes\n" +
                "Warning Expiry: " + warnTimeHours + " hours").queue();
    }

    private void handleGetWarnSettingsCommand(SlashCommandInteractionEvent event, String guildId) {
        if (!handler.hasWarnSystemSettings(guildId)) {
            event.reply("No warn system settings configured for this server. Use `/warn settings-set` to configure.").setEphemeral(true).queue();
            return;
        }

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
