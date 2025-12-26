package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.modals.Modal;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class RoleEventConfigListener extends ListenerAdapter {

    private final DatabaseHandler handler;
    
    // Discord limits messages to 5 action rows maximum
    private static final int MAX_ACTION_ROWS_BEFORE_REQUIRED_ROLES = 4;

    public RoleEventConfigListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        String guildId = event.getGuild().getId();
        List<DatabaseHandler.RoleEventData> joinEvents = handler.getRoleEventsByType(guildId, RoleEventType.MEMBER_JOIN);
        for (DatabaseHandler.RoleEventData data : joinEvents) {
            if (data.durationSeconds > 0) {
                handler.addActiveTimer(event.getGuild().getId(), event.getMember().getId(), data.roleId, data.id, data.durationSeconds);
            }
            if (data.actionType.equals("ADD")) {
                event.getGuild().addRoleToMember(event.getMember(), event.getGuild().getRoleById(data.roleId)).queue();
            } else if (data.actionType.equals("REMOVE")) {
                event.getGuild().removeRoleFromMember(event.getMember(), event.getGuild().getRoleById(data.roleId)).queue();
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("role-event")) return;

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        String guildId = event.getGuild().getId();

        switch (subcommand) {
            case "create":
                String name = event.getOption("name").getAsString();
                // Default: MEMBER_JOIN
                handler.createRoleEvent(guildId, name, "MEMBER_JOIN", "0", "ADD", 0, "REFRESH", null);

                List<DatabaseHandler.RoleEventData> events = handler.getRoleEventsByType(guildId, RoleEventType.MEMBER_JOIN);
                if (!events.isEmpty()) {
                    DatabaseHandler.RoleEventData newEvent = events.get(events.size() - 1);
                    sendEventDashboard(event, newEvent);
                } else {
                    event.reply("Error creating event.").setEphemeral(true).queue();
                }
                break;

            case "list":
                showEventList(event, guildId);
                break;
        }
    }

    // --- ENTITY SELECT (Role Selection) ---
    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String id = event.getComponentId();
        String guildId = event.getGuild().getId();

        List<Role> roles = event.getMentions().getRoles();
        if (roles.isEmpty()) return;
        Role selectedRole = roles.get(0);

        if (id.startsWith("event_role_select_")) {
            int eventId = Integer.parseInt(id.replace("event_role_select_", ""));
            DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);
            if (data == null) return;

            handler.updateRoleEvent(eventId, guildId, data.name, data.eventType, selectedRole.getId(),
                    data.actionType, data.durationSeconds, "REFRESH", data.triggerData, data.active);

            sendEventDashboard(event, handler.getRoleEvent(eventId));
        }
        else if (id.startsWith("event_trigger_role_select_")) {
            int eventId = Integer.parseInt(id.replace("event_trigger_role_select_", ""));
            DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);
            if (data == null) return;

            // Support multiple trigger roles - merge with existing conditions
            List<Role> selectedRoles = event.getMentions().getRoles();
            JSONArray roleIds = new JSONArray();
            for (Role r : selectedRoles) {
                roleIds.put(r.getId());
            }
            
            // Preserve existing conditions and add/update trigger_role_ids
            JSONObject jsonObj = parseExistingConditions(data.triggerData);
            jsonObj.put("trigger_role_ids", roleIds);
            String json = jsonObj.toString();

            handler.updateRoleEvent(eventId, guildId, data.name, data.eventType, data.roleId,
                    data.actionType, data.durationSeconds, "REFRESH", json, data.active);

            sendEventDashboard(event, handler.getRoleEvent(eventId));
        }
        else if (id.startsWith("event_required_role_select_")) {
            int eventId = Integer.parseInt(id.replace("event_required_role_select_", ""));
            DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);
            if (data == null) return;

            // Support multiple required roles - merge with existing conditions
            List<Role> selectedRoles = event.getMentions().getRoles();
            JSONArray roleIds = new JSONArray();
            for (Role r : selectedRoles) {
                roleIds.put(r.getId());
            }
            
            // Preserve existing conditions and add/update required_role_ids
            JSONObject jsonObj = parseExistingConditions(data.triggerData);
            jsonObj.put("required_role_ids", roleIds);
            String json = jsonObj.toString();

            handler.updateRoleEvent(eventId, guildId, data.name, data.eventType, data.roleId,
                    data.actionType, data.durationSeconds, "REFRESH", json, data.active);

            sendEventDashboard(event, handler.getRoleEvent(eventId));
        }
    }

    // --- STRING SELECT ---
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
            int eventId = Integer.parseInt(id.replace("event_trigger_type_select_", ""));
            String newType = event.getValues().get(0);
            DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);

            if (data != null) {
                handler.updateRoleEvent(eventId, guildId, data.name, newType, data.roleId,
                        data.actionType, data.durationSeconds, "REFRESH", null, data.active);
                sendEventDashboard(event, handler.getRoleEvent(eventId));
            }
        }
    }

    // --- BUTTONS ---
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
            event.reply("🗑️ Event deleted.").setEphemeral(true).queue();
            event.getMessage().delete().queue();
        }
        else if (id.startsWith("event_clear_conditions_")) {
            int eventId = Integer.parseInt(id.replace("event_clear_conditions_", ""));
            DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);
            if (data != null) {
                handler.updateRoleEvent(eventId, guildId, data.name, data.eventType, data.roleId,
                        data.actionType, data.durationSeconds, "REFRESH", null, data.active);
                sendEventDashboard(event, handler.getRoleEvent(eventId));
            }
        }
    }

    // --- MODALS ---
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
                case "data":
                    if (data.eventType.equals("WARN_THRESHOLD")) {
                        try {
                            int count = Integer.parseInt(input);
                            // Preserve existing conditions and add/update threshold
                            JSONObject jsonObj = parseExistingConditions(data.triggerData);
                            jsonObj.put("threshold", count);
                            handler.updateRoleEvent(eventId, guildId, data.name, data.eventType, data.roleId, data.actionType, data.durationSeconds, "REFRESH", jsonObj.toString(), data.active);
                        } catch (NumberFormatException e) { success = false; }
                    } else {
                        handler.updateRoleEvent(eventId, guildId, data.name, data.eventType, data.roleId, data.actionType, data.durationSeconds, "REFRESH", input, data.active);
                    }
                    break;
            }

            if (success) {
                sendEventDashboard(event, handler.getRoleEvent(eventId));
            } else {
                event.reply("❌ Invalid input.").setEphemeral(true).queue();
            }
        }
    }

    // ==========================================
    // UI BUILDER
    // ==========================================

    private void handleDashboardAction(StringSelectInteractionEvent event, int eventId, String action) {
        DatabaseHandler.RoleEventData data = handler.getRoleEvent(eventId);
        String guildId = event.getGuild().getId();

        switch (action) {
            case "edit_name":
                event.replyModal(createModal("modal_event_name_" + eventId, "Edit Name", "New Name", data.name)).queue();
                break;

            case "edit_trigger":
                StringSelectMenu.Builder typeMenu = StringSelectMenu.create("event_trigger_type_select_" + eventId)
                        .setPlaceholder("Select a trigger...");

                for (RoleEventType type : RoleEventType.values()) {
                    typeMenu.addOption(type.toString(), type.toString(), getTriggerDescription(type));
                }

                event.reply("When should this event fire?")
                        .setComponents(ActionRow.of(typeMenu.build()))
                        .setEphemeral(true).queue();
                break;

            case "edit_role":
                EntitySelectMenu roleMenu = EntitySelectMenu.create("event_role_select_" + eventId, EntitySelectMenu.SelectTarget.ROLE)
                        .setPlaceholder("Search and select target role...")
                        .setMinValues(1).setMaxValues(1).build();
                event.reply("Which role should be given/removed?")
                        .setComponents(ActionRow.of(roleMenu))
                        .setEphemeral(true).queue();
                break;

            case "edit_action":
                String newAction = data.actionType.equals("ADD") ? "REMOVE" : "ADD";
                handler.updateRoleEvent(eventId, guildId, data.name, data.eventType, data.roleId, newAction, data.durationSeconds, "REFRESH", data.triggerData, data.active);
                sendEventDashboard(event, handler.getRoleEvent(eventId));
                break;

            case "edit_duration":
                event.replyModal(createModal("modal_event_duration_" + eventId, "Edit Duration", "Duration (e.g. 1d, 30m, 0)", "0")).queue();
                break;

            case "edit_data":
                if (data.eventType.equals("WARN_THRESHOLD")) {
                    String maxWarns = String.valueOf(handler.getMaxWarns(event.getGuild().getId()));
                    event.replyModal(createModal("modal_event_data_" + eventId, "Warn Limit", "Count (e.g. " + maxWarns + ")", maxWarns)).queue();
                } else {
                    event.replyModal(createModal("modal_event_data_" + eventId, "Conditions", "JSON", data.triggerData)).queue();
                }
                break;
        }
    }

    public void sendEventDashboard(IReplyCallback event, DatabaseHandler.RoleEventData data) {
        if (data == null) return;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚙️ Configuration: " + data.name);
        embed.setColor(data.active ? Color.GREEN : Color.RED);
        embed.setDescription("Edit settings for this timed role event.");

        String statusEmoji = data.active ? "✅ Active" : "🔴 Inactive";
        embed.addField("Status", statusEmoji, true);
        embed.addField("1. Trigger", "`" + data.eventType + "`", true);

        Role role = event.getGuild().getRoleById(data.roleId);
        String roleText = (role != null) ? role.getAsMention() : "❌ ID: " + data.roleId;
        String actionText = data.actionType.equals("ADD") ? "Add" : "Remove";

        embed.addField("2. Action", actionText + " -> " + roleText, false);

        String durationText = (data.durationSeconds > 0) ? formatDuration(data.durationSeconds) : "Permanent / Instant";
        embed.addField("3. Duration", durationText, true);

        // Conditions Text - now showing multiple condition types
        StringBuilder conditionBuilder = new StringBuilder();
        if (data.triggerData != null && !data.triggerData.equals("{}") && !data.triggerData.isEmpty()) {
            try {
                JSONObject jsonObj = new JSONObject(data.triggerData);
                
                // Show trigger roles
                if (jsonObj.has("trigger_role_ids")) {
                    JSONArray roleIds = jsonObj.getJSONArray("trigger_role_ids");
                    conditionBuilder.append("**Trigger Roles:** ");
                    for (int i = 0; i < roleIds.length(); i++) {
                        String roleId = roleIds.getString(i);
                        Role tr = event.getGuild().getRoleById(roleId);
                        if (i > 0) conditionBuilder.append(", ");
                        conditionBuilder.append(tr != null ? tr.getAsMention() : roleId);
                    }
                    conditionBuilder.append("\n");
                } else if (jsonObj.has("trigger_role_id")) {
                    // Legacy single role format
                    String roleIdStr = jsonObj.getString("trigger_role_id");
                    Role tr = event.getGuild().getRoleById(roleIdStr);
                    conditionBuilder.append("**Trigger Role:** ").append(tr != null ? tr.getAsMention() : roleIdStr).append("\n");
                }
                
                // Show required roles
                if (jsonObj.has("required_role_ids")) {
                    JSONArray roleIds = jsonObj.getJSONArray("required_role_ids");
                    conditionBuilder.append("**Required Roles:** ");
                    for (int i = 0; i < roleIds.length(); i++) {
                        String roleId = roleIds.getString(i);
                        Role tr = event.getGuild().getRoleById(roleId);
                        if (i > 0) conditionBuilder.append(", ");
                        conditionBuilder.append(tr != null ? tr.getAsMention() : roleId);
                    }
                    conditionBuilder.append("\n");
                }
                
                // Show threshold
                if (jsonObj.has("threshold")) {
                    int threshold = jsonObj.getInt("threshold");
                    conditionBuilder.append("**Warn Threshold:** ").append(threshold).append("\n");
                }
                
            } catch (Exception e) {
                conditionBuilder.append("`").append(data.triggerData).append("`");
            }
        }
        
        String conditionText = conditionBuilder.length() > 0 ? conditionBuilder.toString().trim() : "None";
        if (conditionText.equals("None") && (data.eventType.equals("ROLE_ADD") || data.eventType.equals("ROLE_REMOVE"))) {
            conditionText = "⚠️ None (Fires on ANY role!)";
        }
        embed.addField("4. Conditions", conditionText, false);
        embed.setFooter("Event-ID: " + data.id);

        // COMPONENTS
        List<ActionRow> rows = new ArrayList<>();

        StringSelectMenu menu = StringSelectMenu.create("event_edit_select_" + data.id)
                .setPlaceholder("Edit setting...")
                .addOption("Change Name", "edit_name", Emoji.fromUnicode("📝"))
                .addOption("Change Trigger", "edit_trigger", Emoji.fromUnicode("⚡"))
                .addOption("Change Target Role", "edit_role", Emoji.fromUnicode("🎭"))
                .addOption("Change Action (+/-)", "edit_action", Emoji.fromUnicode("🔄"))
                .addOption("Change Duration", "edit_duration", Emoji.fromUnicode("⏱️"))
                .addOption("Change Conditions", "edit_data", Emoji.fromUnicode("📋"))
                .build();
        rows.add(ActionRow.of(menu));

        // Dynamic Menu for ROLE Trigger - Allow multiple role selection
        if (data.eventType.equals("ROLE_ADD") || data.eventType.equals("ROLE_REMOVE")) {
            EntitySelectMenu triggerRoleMenu = EntitySelectMenu.create("event_trigger_role_select_" + data.id, EntitySelectMenu.SelectTarget.ROLE)
                    .setPlaceholder("Select Trigger Role(s)...")
                    .setMinValues(1).setMaxValues(25).build();
            rows.add(ActionRow.of(triggerRoleMenu));
        }
        
        // Required roles selector - available for all event types
        // Only add if we have room (Discord limits to 5 action rows)
        if (rows.size() < MAX_ACTION_ROWS_BEFORE_REQUIRED_ROLES) {
            EntitySelectMenu requiredRoleMenu = EntitySelectMenu.create("event_required_role_select_" + data.id, EntitySelectMenu.SelectTarget.ROLE)
                    .setPlaceholder("Required Role(s) - user must have these...")
                    .setMinValues(1).setMaxValues(25).build();
            rows.add(ActionRow.of(requiredRoleMenu));
        }

        Button toggleBtn = data.active
                ? Button.secondary("event_toggle_" + data.id, "Disable")
                : Button.success("event_toggle_" + data.id, "Enable");
        Button deleteBtn = Button.danger("event_delete_" + data.id, "Delete");
        
        // Add "Clear Conditions" button if conditions are set
        boolean hasConditions = data.triggerData != null && !data.triggerData.isEmpty() && !data.triggerData.equals("{}");
        if (hasConditions) {
            Button clearConditionsBtn = Button.secondary("event_clear_conditions_" + data.id, "Clear Conditions");
            rows.add(ActionRow.of(toggleBtn, clearConditionsBtn, deleteBtn));
        } else {
            rows.add(ActionRow.of(toggleBtn, deleteBtn));
        }

        if (event instanceof IMessageEditCallback) {
            ((IMessageEditCallback) event).editMessageEmbeds(embed.build()).setComponents(rows).queue();
        } else {
            event.replyEmbeds(embed.build()).setComponents(rows).setEphemeral(true).queue();
        }
    }

    private String getTriggerDescription(RoleEventType type) {
        switch (type) {
            case MEMBER_JOIN: return "User joins server";
            case ROLE_ADD: return "User gets a role";
            case ROLE_REMOVE: return "User loses a role";
            //case WARN_THRESHOLD: return "Warn limit reached";
            //case MEMBER_BOOST: return "User boosts server";
            // case VOICE_LEAVE: return "Leaves voice channel";
            default: return type.name();
        }
    }

    private void showEventList(SlashCommandInteractionEvent event, String guildId) {
        List<DatabaseHandler.RoleEventData> allEvents = new ArrayList<>();
        for (RoleEventType type : RoleEventType.values()) {
            allEvents.addAll(handler.getRoleEventsByType(guildId, type));
        }

        if (allEvents.isEmpty()) {
            event.reply("No events found. (/event create)").setEphemeral(true).queue();
            return;
        }

        StringSelectMenu.Builder menu = StringSelectMenu.create("event_select_edit")
                .setPlaceholder("Select an event to edit");

        for (DatabaseHandler.RoleEventData evt : allEvents) {
            if (menu.getOptions().size() >= 25) break;
            menu.addOption(evt.name, String.valueOf(evt.id), evt.eventType + " -> " + evt.actionType);
        }

        event.reply("Select an event:")
                .setComponents(ActionRow.of(menu.build()))
                .setEphemeral(true)
                .queue();
    }

    private Modal createModal(String id, String title, String label, String value) {
        TextInput input = TextInput.create("input_field", TextInputStyle.SHORT)
                .setValue(value != null ? value : "")
                .setRequired(true)
                .build();
        return Modal.create(id, title).addComponents(Label.of(label, input)).build();
    }

    private long parseDuration(String input) {
        try {
            input = input.toLowerCase().trim();
            if (input.equals("0")) return 0;
            long val = Long.parseLong(input.replaceAll("[^0-9]", ""));
            if (input.endsWith("d")) return TimeUnit.DAYS.toSeconds(val);
            if (input.endsWith("h")) return TimeUnit.HOURS.toSeconds(val);
            if (input.endsWith("m")) return TimeUnit.MINUTES.toSeconds(val);
            return val * 60;
        } catch (Exception e) { return -1; }
    }

    private String formatDuration(long seconds) {
        if (seconds == 0) return "Permanent / Instant";
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds/60) + "m";
        if (seconds < 86400) return (seconds/3600) + "h";
        return (seconds/86400) + "d";
    }
    
    /**
     * Parse existing conditions from triggerData JSON, preserving existing values.
     * Returns an empty JSONObject if triggerData is null or invalid.
     */
    private JSONObject parseExistingConditions(String triggerData) {
        if (triggerData == null || triggerData.isEmpty() || triggerData.equals("{}")) {
            return new JSONObject();
        }
        try {
            return new JSONObject(triggerData);
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}