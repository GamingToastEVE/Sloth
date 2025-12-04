package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RoleEventConfigListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public RoleEventConfigListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("event")) return;
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Nur Administratoren können Events verwalten.").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        String guildId = event.getGuild().getId();

        switch (subcommand) {
            case "create":
                String name = event.getOption("name").getAsString();
                // Standard: MEMBER_JOIN
                handler.createRoleEvent(guildId, name, "MEMBER_JOIN", "0", "ADD", 0, "REFRESH", null);

                List<DatabaseHandler.RoleEventData> events = handler.getRoleEventsByType(guildId, RoleEventType.MEMBER_JOIN);
                if (!events.isEmpty()) {
                    DatabaseHandler.RoleEventData newEvent = events.get(events.size() - 1);
                    sendEventDashboard(event, newEvent);
                } else {
                    event.reply("Fehler beim Erstellen des Events.").setEphemeral(true).queue();
                }
                break;

            case "list":
                showEventList(event, guildId);
                break;
        }
    }

    // ==========================================
    // INTERACTION HANDLERS (Update Logic)
    // ==========================================

    // 1. ENTITY SELECT (Rollen Auswahl)
    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String id = event.getComponentId();
        String guildId = event.getGuild().getId();

        List<Role> roles = event.getMentions().getRoles();
        if (roles.isEmpty()) return;
        Role selectedRole = roles.get(0);

        if (id.startsWith("event_role_select_")) {
            // ZIEL-ROLLE ÄNDERN
            int eventId = Integer.parseInt(id.replace("event_role_select_", ""));
            DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);
            if (data == null) return;

            handler.updateRoleEvent(eventId, guildId, data.name, data.eventType, selectedRole.getId(),
                    data.actionType, data.durationSeconds, "REFRESH", data.triggerData, data.active);

            // Sofortiges Update des Dashboards
            sendEventDashboard(event, handler.getRoleEvent(eventId));
        }
        else if (id.startsWith("event_trigger_role_select_")) {
            // TRIGGER-ROLLE ÄNDERN (für ROLE_ADD/REMOVE)
            int eventId = Integer.parseInt(id.replace("event_trigger_role_select_", ""));
            DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);
            if (data == null) return;

            // JSON automatisch bauen
            String json = "{\"trigger_role_id\": \"" + selectedRole.getId() + "\"}";

            handler.updateRoleEvent(eventId, guildId, data.name, data.eventType, data.roleId,
                    data.actionType, data.durationSeconds, "REFRESH", json, data.active);

            sendEventDashboard(event, handler.getRoleEvent(eventId));
        }
    }

    // 2. STRING SELECT (Menü Navigation & Trigger Typ)
    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        String guildId = event.getGuild().getId();

        if (id.equals("event_select_edit")) {
            int eventId = Integer.parseInt(event.getValues().get(0));
            DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);
            if (data != null) sendEventDashboard(event, data);
        }
        else if (id.startsWith("event_edit_select_")) {
            int eventId = Integer.parseInt(id.replace("event_edit_select_", ""));
            String action = event.getValues().get(0);
            handleDashboardAction(event, eventId, action);
        }
        else if (id.startsWith("event_trigger_type_select_")) {
            // TRIGGER TYP GEÄNDERT
            int eventId = Integer.parseInt(id.replace("event_trigger_type_select_", ""));
            String newType = event.getValues().get(0);
            DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);

            if (data != null) {
                // Reset trigger_data bei Typwechsel (Sicherheit)
                handler.updateRoleEvent(eventId, guildId, data.name, newType, data.roleId,
                        data.actionType, data.durationSeconds, "REFRESH", null, data.active);

                // Dashboard neu laden (zeigt jetzt ggf. neue Felder an)
                sendEventDashboard(event, handler.getRoleEvent(eventId));
            }
        }
    }

    // 3. BUTTONS (Toggle/Delete)
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        String guildId = event.getGuild().getId();

        if (id.startsWith("event_toggle_")) {
            int eventId = Integer.parseInt(id.replace("event_toggle_", ""));
            DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);
            if (data != null) {
                handler.toggleRoleEventActive(guildId, eventId, !data.active);
                sendEventDashboard(event, handler.getRoleEvent(eventId));
            }
        }
        else if (id.startsWith("event_delete_")) {
            int eventId = Integer.parseInt(id.replace("event_delete_", ""));
            handler.deleteRoleEvent(guildId, eventId);
            event.reply("🗑️ Event gelöscht.").setEphemeral(true).queue();
            event.getMessage().delete().queue();
        }
    }

    // 4. MODALS (Text Eingaben)
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();

        if (modalId.startsWith("modal_event_")) {
            String[] parts = modalId.split("_");
            String action = parts[2];
            int eventId = Integer.parseInt(parts[3]);
            String guildId = event.getGuild().getId();

            DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);
            if (data == null) return;

            String input = event.getValue("input_field").getAsString();
            boolean success = true;

            switch (action) {
                case "duration":
                    long seconds = parseDuration(input);
                    if (seconds >= 0) {
                        handler.updateRoleEvent(eventId, guildId, data.name, data.eventType, data.roleId, data.actionType, seconds, "REFRESH", data.triggerData, data.active);
                    } else {
                        success = false;
                    }
                    break;
                case "name":
                    handler.updateRoleEvent(eventId, guildId, input, data.eventType, data.roleId, data.actionType, data.durationSeconds, "REFRESH", data.triggerData, data.active);
                    break;
                case "data": // Manuelles JSON oder Zahl
                    if (data.eventType.equals("WARN_THRESHOLD")) {
                        try {
                            int count = Integer.parseInt(input);
                            String json = "{\"threshold\": " + count + "}";
                            handler.updateRoleEvent(eventId, guildId, data.name, data.eventType, data.roleId, data.actionType, data.durationSeconds, "REFRESH", json, data.active);
                        } catch (NumberFormatException e) { success = false; }
                    } else {
                        handler.updateRoleEvent(eventId, guildId, data.name, data.eventType, data.roleId, data.actionType, data.durationSeconds, "REFRESH", input, data.active);
                    }
                    break;
            }

            if (success) {
                // WICHTIG: Das Embed aktualisieren!
                // Bei Modals nutzen wir editMessageEmbeds auf der Source-Interaction, wenn möglich
                // Da Modals "neue" Interaktionen sind, müssen wir die Originalnachricht bearbeiten.
                // Trick: editMessageEmbeds auf dem Modal-Event bearbeitet die Nachricht, die das Modal ausgelöst hat (meistens).
                sendEventDashboard(event, handler.getRoleEvent(eventId));
            } else {
                event.reply("❌ Ungültige Eingabe.").setEphemeral(true).queue();
            }
        }
    }

    // ==========================================
    // UI LOGIC & DASHBOARD
    // ==========================================

    private void handleDashboardAction(StringSelectInteractionEvent event, int eventId, String action) {
        DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);

        switch (action) {
            case "edit_name":
                event.replyModal(createModal("modal_event_name_" + eventId, "Name ändern", "Neuer Name", data.name)).queue();
                break;

            case "edit_trigger":
                // Dropdown für Trigger-Typen
                StringSelectMenu.Builder typeMenu = StringSelectMenu.create("event_trigger_type_select_" + eventId)
                        .setPlaceholder("Wähle einen Auslöser...");

                for (RoleEventType type : RoleEventType.values()) {
                    typeMenu.addOption(type.toString(), type.toString(), getTriggerDescription(type));
                }

                event.reply("Wann soll das Event ausgelöst werden?")
                        .addActionRow(typeMenu.build())
                        .setEphemeral(true)
                        .queue();
                break;

            case "edit_role":
                // Ziel-Rolle (Entity Select)
                EntitySelectMenu roleMenu = EntitySelectMenu.create("event_role_select_" + eventId, EntitySelectMenu.SelectTarget.ROLE)
                        .setPlaceholder("Suche und wähle die Ziel-Rolle...")
                        .setMinValues(1)
                        .setMaxValues(1)
                        .build();
                event.reply("Welche Rolle soll vergeben/entfernt werden?")
                        .addActionRow(roleMenu)
                        .setEphemeral(true)
                        .queue();
                break;

            case "edit_action":
                // Toggle ADD/REMOVE und sofort Refresh
                String newAction = data.actionType.equals("ADD") ? "REMOVE" : "ADD";
                handler.updateRoleEvent(eventId, event.getGuild().getId(), data.name, data.eventType, data.roleId, newAction, data.durationSeconds, "REFRESH", data.triggerData, data.active);
                sendEventDashboard(event, handler.getRoleEvent(eventId));
                break;

            case "edit_duration":
                event.replyModal(createModal("modal_event_duration_" + eventId, "Dauer ändern", "Dauer (z.B. 1d, 30m, 0 für permanent)", "0")).queue();
                break;

            case "edit_data":
                // Fallunterscheidung je nach Typ
                if (data.eventType.equals("WARN_THRESHOLD")) {
                    event.replyModal(createModal("modal_event_data_" + eventId, "Warn Limit", "Anzahl Warns (z.B. 3)", "3")).queue();
                } else {
                    event.replyModal(createModal("modal_event_data_" + eventId, "Bedingungen (JSON)", "JSON Daten", data.triggerData)).queue();
                }
                break;
        }
    }

    /**
     * Baut das Dashboard und sendet es (oder editiert es).
     * Hier passiert die Magie für dynamische Felder!
     */
    public void sendEventDashboard(IReplyCallback event, DatabaseHandler.RoleEventData data) {
        if (data == null) return;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚙️ Konfiguration: " + data.name);
        embed.setColor(data.active ? Color.GREEN : Color.RED);

        String statusEmoji = data.active ? "✅ Aktiv" : "🔴 Inaktiv";
        embed.addField("Status", statusEmoji, true);
        embed.addField("1. Auslöser", "`" + data.eventType + "`", true);

        // Rolle auflösen
        Role role = event.getGuild().getRoleById(data.roleId);
        String roleText = (role != null) ? role.getAsMention() : "❌ ID: " + data.roleId;
        String actionText = data.actionType.equals("ADD") ? "Hinzufügen" : "Entfernen";

        embed.addField("2. Aktion", actionText + " -> " + roleText, false);

        String durationText = (data.durationSeconds > 0) ? formatDuration(data.durationSeconds) : "Permanent / Sofort";
        embed.addField("3. Dauer", durationText, true);

        // Bedingung lesbar machen
        String conditionText = formatConditionText(event, data);
        embed.addField("4. Bedingungen", conditionText, false);

        embed.setFooter("Event-ID: " + data.id);

        // --- KOMPONENTEN BAUEN ---
        List<LayoutComponent> rows = new ArrayList<>();

        // 1. Haupt-Menü (Einstellungen)
        StringSelectMenu menu = StringSelectMenu.create("event_edit_select_" + data.id)
                .setPlaceholder("Einstellung bearbeiten...")
                .addOption("Name ändern", "edit_name", Emoji.fromUnicode("📝"))
                .addOption("Trigger ändern", "edit_trigger", Emoji.fromUnicode("⚡"))
                .addOption("Ziel-Rolle ändern", "edit_role", Emoji.fromUnicode("🎭"))
                .addOption("Aktion ändern (+/-)", "edit_action", Emoji.fromUnicode("🔄"))
                .addOption("Dauer ändern", "edit_duration", Emoji.fromUnicode("⏱️"))
                .addOption("Bedingungen ändern", "edit_data", Emoji.fromUnicode("📋"))
                .build();
        rows.add(ActionRow.of(menu));

        // 2. DYNAMISCHE REIHE: Trigger-Rolle Auswahl
        // Wenn der Trigger "ROLE_ADD" oder "ROLE_REMOVE" ist, zeigen wir direkt ein Rollen-Select an!
        if (data.eventType.equals("ROLE_ADD") || data.eventType.equals("ROLE_REMOVE")) {
            EntitySelectMenu triggerRoleMenu = EntitySelectMenu.create("event_trigger_role_select_" + data.id, EntitySelectMenu.SelectTarget.ROLE)
                    .setPlaceholder("Optional: Wähle die Auslöser-Rolle direkt hier...")
                    .setMinValues(1)
                    .setMaxValues(1)
                    .build();
            rows.add(ActionRow.of(triggerRoleMenu));
        }

        // 3. Buttons
        Button toggleBtn = data.active
                ? Button.secondary("event_toggle_" + data.id, "Deaktivieren")
                : Button.success("event_toggle_" + data.id, "Aktivieren");
        Button deleteBtn = Button.danger("event_delete_" + data.id, "Löschen");
        rows.add(ActionRow.of(toggleBtn, deleteBtn));

        // Senden oder Editieren
        if (event instanceof IMessageEditCallback) {
            // Wenn wir schon eine Nachricht haben (Button/Select Klick oder Modal Submit)
            ((IMessageEditCallback) event).editMessageEmbeds(embed.build())
                    .setComponents(rows)
                    .queue();
        } else {
            // Wenn es ein neuer Slash Command ist
            event.replyEmbeds(embed.build())
                    .setComponents(rows)
                    .setEphemeral(true)
                    .queue();
        }
    }

    // --- Helper für Text ---

    private String formatConditionText(IReplyCallback event, DatabaseHandler.RoleEventData data) {
        if (data.triggerData == null || data.triggerData.equals("{}") || data.triggerData.isEmpty()) {
            // Warnung bei Role-Events ohne Bedingung
            if (data.eventType.equals("ROLE_ADD")) return "⚠️ Keine (Feuert bei JEDER Rolle!)";
            return "Keine";
        }

        if (data.triggerData.contains("trigger_role_id")) {
            String id = data.triggerData.replaceAll("[^0-9]", "");
            Role tr = event.getGuild().getRoleById(id);
            return "Bei Rolle: " + (tr != null ? tr.getAsMention() : id);
        } else if (data.triggerData.contains("threshold")) {
            return "Ab " + data.triggerData.replaceAll("[^0-9]", "") + " Warns";
        }

        return "`" + data.triggerData + "`";
    }

    private String getTriggerDescription(RoleEventType type) {
        switch (type) {
            case MEMBER_JOIN: return "User tritt Server bei";
            case ROLE_ADD: return "User erhält eine Rolle";
            case ROLE_REMOVE: return "User verliert eine Rolle";
            case WARN_THRESHOLD: return "Warn-Limit erreicht";
            case MEMBER_BOOST: return "User boostet Server";
            case VOICE_LEAVE: return "Verlässt Voice";
            default: return type.name();
        }
    }

    private void showEventList(SlashCommandInteractionEvent event, String guildId) {
        List<DatabaseHandler.RoleEventData> allEvents = new ArrayList<>();
        for (RoleEventType type : RoleEventType.values()) {
            allEvents.addAll(handler.getRoleEventsByType(guildId, type));
        }

        if (allEvents.isEmpty()) {
            event.reply("Keine Events gefunden.").setEphemeral(true).queue();
            return;
        }

        StringSelectMenu.Builder menu = StringSelectMenu.create("event_select_edit")
                .setPlaceholder("Wähle ein Event zum Bearbeiten");

        for (DatabaseHandler.RoleEventData evt : allEvents) {
            if (menu.getOptions().size() >= 25) break;
            menu.addOption(evt.name, String.valueOf(evt.id), evt.eventType + " -> " + evt.actionType);
        }

        event.reply("Wähle ein Event:")
                .addActionRow(menu.build())
                .setEphemeral(true)
                .queue();
    }

    private Modal createModal(String id, String title, String label, String value) {
        TextInput input = TextInput.create("input_field", label, TextInputStyle.SHORT)
                .setValue(value != null ? value : "")
                .setRequired(true)
                .build();
        return Modal.create(id, title).addActionRow(input).build();
    }

    private long parseDuration(String input) {
        try {
            input = input.toLowerCase().trim();
            if (input.equals("0")) return 0;
            long val = Long.parseLong(input.replaceAll("[^0-9]", ""));
            if (input.endsWith("d")) return TimeUnit.DAYS.toSeconds(val);
            if (input.endsWith("h")) return TimeUnit.HOURS.toSeconds(val);
            if (input.endsWith("m")) return TimeUnit.MINUTES.toSeconds(val);
            if (input.endsWith("s")) return val;
            return val * 60;
        } catch (Exception e) { return -1; }
    }

    private String formatDuration(long seconds) {
        if (seconds == 0) return "Permanent";
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds/60) + "m";
        if (seconds < 86400) return (seconds/3600) + "h";
        return (seconds/86400) + "d";
    }
}