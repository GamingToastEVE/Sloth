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
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import org.json.JSONObject;

import java.awt.Color;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class WarnCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public WarnCommandListener(DatabaseHandler handler) {
        this.handler = handler;
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
            case "list":
                if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) return;
                handler.insertOrUpdateGlobalStatistic("warn-list");
                handleListWarningsCommand(event, guildId);
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

    private void handleListWarningsCommand(SlashCommandInteractionEvent event, String guildId) {
        Member targetMember = event.getOption("user").getAsMember();
        if (targetMember == null) {
            event.reply("User not found.").setEphemeral(true).queue();
            return;
        }

        sendWarnListEmbed(event, guildId, targetMember.getUser());
    }

    // --- NEUE HELPER METHODE: Embed & Menü bauen ---
    // Ausgelagert, damit wir sie auch nach dem Löschen eines Warns aufrufen können (Refresh)
    private void sendWarnListEmbed(IReplyCallback event, String guildId, User targetUser) {
        List<DatabaseHandler.WarningData> warnings = handler.getUserActiveWarnings(guildId, targetUser.getId());

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚠️ Active Warnings for " + targetUser.getName());
        embed.setColor(Color.ORANGE);
        embed.setThumbnail(targetUser.getAvatarUrl());

        if (warnings.isEmpty()) {
            embed.setDescription("✅ This user has no active warnings.");
            embed.setColor(Color.GREEN);
            // Wenn keine Warns da sind, senden wir nur das Embed ohne Menü
            if (event instanceof SlashCommandInteractionEvent) {
                event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
            } else if (event instanceof StringSelectInteractionEvent) {
                // Wenn wir aus dem Dropdown kommen (letzter Warn gelöscht), updaten wir die Nachricht
                ((StringSelectInteractionEvent) event).editMessageEmbeds(embed.build()).setComponents().queue();
            }
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

            // Text für Embed
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
        embed.setFooter("Select a warning below to remove it or view details.");

        // Antwort senden
        if (event instanceof SlashCommandInteractionEvent) {
            event.replyEmbeds(embed.build()).setComponents(ActionRow.of(menuBuilder.build())).setEphemeral(true).queue();
        } else if (event instanceof StringSelectInteractionEvent) {
            event.replyEmbeds(embed.build()).setComponents(ActionRow.of(menuBuilder.build())).setEphemeral(true).queue();
        } else {
            event.replyEmbeds(embed.build()).setComponents(ActionRow.of(menuBuilder.build())).setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        // Warn Lösch Bestätigung
        if (componentId.startsWith("warn_delete_confirm:")) {
            if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
                event.reply("❌ No permission.").setEphemeral(true).queue();
                return;
            }

            String warnIdStr = componentId.split(":")[1];
            try {
                int warnId = Integer.parseInt(warnIdStr);
                boolean success = handler.deactivateWarning(warnId, event.getGuild().getId());
                if (success) {
                    event.reply("✅ Warning deleted successfully.").setEphemeral(true).queue();

                    // Nach dem Löschen die Warnliste neu senden (Refresh)
                    String guildId = event.getGuild().getId();
                    String targetUserId = componentId.split(":")[2];
                    if (targetUserId != null) {
                        User targetUser = event.getJDA().getUserById(targetUserId);
                        if (targetUser != null) {
                            event.getMessage().delete().queue();
                            sendWarnListEmbed(event, guildId, targetUser);
                        }
                    }
                } else {
                    event.getMessage().delete().queue();
                    event.reply("❌ Failed to delete warning. It may not exist.").setEphemeral(true).queue();
                }
            } catch (NumberFormatException e) {
                event.getMessage().delete().queue();
                event.reply("❌ Invalid warning ID.").setEphemeral(true).queue();
            }
        }

        // Zurück zur Warnliste
        else if (componentId.startsWith("warn_delete_back:")) {
            String targetUserId = componentId.split(":")[1];
            System.out.println("DEBUG: Back button pressed for user ID " + targetUserId);
            User targetUser = event.getJDA().retrieveUserById(targetUserId).complete();
            System.out.println("DEBUG: Retrieved user: " + targetUser);
            if (targetUser != null) {
                String guildId = event.getGuild().getId();
                sendWarnListEmbed(event, guildId, targetUser);
            } else {
                event.reply("❌ User not found.").setEphemeral(true).queue();
            }
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

        // Berechtigungscheck (Sicherheitshalber nochmal)
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("❌ No permission.").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String targetUserId = componentId.split(":")[1]; // User ID aus der ID extrahieren
        String selectedWarnIdStr = event.getValues().get(0); // Die ausgewählte Warn ID

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
                    embed.setFooter("Use the buttons below to delete this warning or go back to the previous view.");
                    Button deleteButton = Button.danger("warn_delete_confirm:" + warnId + ":" + targetUserId, "Delete Warning");
                    Button backButton = Button.secondary("warn_delete_back:" + targetUserId, "Back to Warnings");
                    event.getMessage().delete().queue();
                    event.replyEmbeds(embed.build())
                            .setComponents(ActionRow.of(backButton, deleteButton))
                            .setEphemeral(true)
                            .queue();
                    return;
                }
            }
            event.reply("❌ Warning not found.").setEphemeral(true).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Invalid warning ID.").setEphemeral(true).queue();
        }
    }

    private void handleWarnCommand(SlashCommandInteractionEvent event, String guildId) {
        Member targetMember = event.getOption("user").getAsMember();
        String reason = event.getOption("reason").getAsString();
        String severity = event.getOption("severity") != null ? event.getOption("severity").getAsString() : "MEDIUM";
        String evidence = event.getOption("evidence") != null ? event.getOption("evidence").getAsAttachment().getUrl() : null;

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

        List<DatabaseHandler.RoleEventData> data = handler.getRoleEventsByType(guildId, RoleEventType.WARN_THRESHOLD);

        if (!data.isEmpty()) {
            for (DatabaseHandler.RoleEventData eventData : data) {
                int threshold;
                JSONObject triggerDataJson = new JSONObject(eventData.triggerData);
                try {
                    threshold = Integer.parseInt(triggerDataJson.getString("threshold"));
                } catch (NumberFormatException e) {
                    continue; // Skip invalid entries
                }

                int activeWarnings = handler.getActiveWarningsCount(guildId, targetMember.getId());

                if (activeWarnings + 1 == threshold) { // +1 because we are about to add a warning
                    // Apply role
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

        // Use new insertWarning method instead of legacy approach
        int warningId = handler.insertWarning(guildId, userId, moderatorId, reason, severity, expiresAt, evidence);

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

            // Update user statistics
            handler.incrementUserWarningsReceived(guildId, userId);
            handler.incrementUserWarningsIssued(guildId, moderatorId);

            // Send audit log entry to log channel
            handler.sendAuditLogEntry(event.getGuild(), "WARN", "", targetMember,
                    event.getMember(), reason);
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
            event.reply("No warn system settings configured for this server. Use `/warn settings-set` to configure.").setEphemeral(true).queue();
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
