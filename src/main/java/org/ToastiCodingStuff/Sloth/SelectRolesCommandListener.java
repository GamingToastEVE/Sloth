package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class SelectRolesCommandListener extends ListenerAdapter implements SlashCommandHandler {

    private final DatabaseHandler handler;

    // Temporärer Speicher für Benutzer-Sessions (für Multi-Step-Workflows)
    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();

    // Session-Daten für komplexe Workflows
    private static class UserSession {
        String guildId;
        int selectedGroupId = -1;
        String selectedRoleId;
        String selectedChannelId;
        String pendingEmoji;
        String pendingDescription;
        String pendingSendSelection; // für die Auswahl "ungrouped" oder "group_X"
        String pendingSendType; // "reaction" oder "buttons"
        long lastInteraction = System.currentTimeMillis();

        UserSession(String guildId) {
            this.guildId = guildId;
        }
    }

    public SelectRolesCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public String[] getHandledCommands() {
        return new String[]{"select-roles"};
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

    private UserSession getOrCreateSession(String oderId, String guildId) {
        userSessions.computeIfAbsent(oderId, k -> new UserSession(guildId));
        UserSession session = userSessions.get(oderId);
        session.lastInteraction = System.currentTimeMillis();
        session.guildId = guildId;
        return session;
    }

    // ==================== SLASH COMMAND HANDLER ====================

    @Override
    public void handleSlashCommand(SlashCommandInteractionEvent event) {

        handler.insertOrUpdateGlobalStatistic("select-roles");

        // Zeige das Hauptmenü
        showMainMenu(event);
    }

    // ==================== MAIN MENU UI ====================

    private void showMainMenu(SlashCommandInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎭 " + t(guildId, "selectroles_ui_title"));
        embed.setDescription(t(guildId, "selectroles_ui_description"));
        embed.setColor(new Color(88, 101, 242)); // Discord Blurple

        // Statistiken hinzufügen
        List<DatabaseHandler.RoleSelectGroupData> groups = handler.getRoleSelectGroups(guildId);
        List<String> ungroupedRoles = handler.getUngroupedRoles(guildId);
        int totalRoles = ungroupedRoles.size();
        for (DatabaseHandler.RoleSelectGroupData group : groups) {
            totalRoles += handler.getRolesInGroup(guildId, group.id).size();
        }

        embed.addField("📊 " + t(guildId, "selectroles_ui_stats"),
            "**" + groups.size() + "** " + t(guildId, "selectroles_groups") + "\n" +
            "**" + totalRoles + "** " + t(guildId, "selectroles_roles_total"), true);

        embed.setFooter(t(guildId, "selectroles_breadcrumb_main"));
        embed.setTimestamp(java.time.Instant.now());

        // Buttons für Hauptaktionen
        List<ActionRow> rows = new ArrayList<>();

        rows.add(ActionRow.of(
            Button.primary("sr_groups", "📁 " + t(guildId, "selectroles_btn_manage_groups")),
            Button.primary("sr_roles", "🏷️ " + t(guildId, "selectroles_btn_manage_roles")),
            Button.success("sr_send", "📤 " + t(guildId, "selectroles_btn_send"))
        ));

        rows.add(ActionRow.of(
            Button.secondary("sr_settings", "⚙️ " + t(guildId, "selectroles_btn_settings")),
            Button.danger("sr_close", "❌ " + t(guildId, "general.close"))
        ));

        event.replyEmbeds(embed.build())
            .setComponents(rows)
            .setEphemeral(true)
            .queue();
    }

    private void showMainMenuEdit(ButtonInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎭 " + t(guildId, "selectroles_ui_title"));
        embed.setDescription(t(guildId, "selectroles_ui_description"));
        embed.setColor(new Color(88, 101, 242));

        List<DatabaseHandler.RoleSelectGroupData> groups = handler.getRoleSelectGroups(guildId);
        List<String> ungroupedRoles = handler.getUngroupedRoles(guildId);
        int totalRoles = ungroupedRoles.size();
        for (DatabaseHandler.RoleSelectGroupData group : groups) {
            totalRoles += handler.getRolesInGroup(guildId, group.id).size();
        }

        embed.addField("📊 " + t(guildId, "selectroles_ui_stats"),
            "**" + groups.size() + "** " + t(guildId, "selectroles_groups") + "\n" +
            "**" + totalRoles + "** " + t(guildId, "selectroles_roles_total"), true);

        embed.setFooter(t(guildId, "selectroles_breadcrumb_main"));
        embed.setTimestamp(java.time.Instant.now());

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
            Button.primary("sr_groups", "📁 " + t(guildId, "selectroles_btn_manage_groups")),
            Button.primary("sr_roles", "🏷️ " + t(guildId, "selectroles_btn_manage_roles")),
            Button.success("sr_send", "📤 " + t(guildId, "selectroles_btn_send"))
        ));
        rows.add(ActionRow.of(
            Button.secondary("sr_settings", "⚙️ " + t(guildId, "selectroles_btn_settings")),
            Button.danger("sr_close", "❌ " + t(guildId, "general.close"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    // ==================== GROUPS MANAGEMENT UI ====================

    private void showGroupsMenu(ButtonInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        List<DatabaseHandler.RoleSelectGroupData> groups = handler.getRoleSelectGroups(guildId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📁 " + t(guildId, "selectroles_groups_title"));
        embed.setColor(new Color(87, 242, 135)); // Grün
        embed.setFooter(t(guildId, "selectroles_breadcrumb_groups"));

        if (groups.isEmpty()) {
            embed.setDescription(t(guildId, "selectroles_no_groups_hint"));
        } else {
            StringBuilder desc = new StringBuilder();
            for (DatabaseHandler.RoleSelectGroupData group : groups) {
                List<String> rolesInGroup = handler.getRolesInGroup(guildId, group.id);
                desc.append("**").append(group.position + 1).append(".** ")
                    .append(group.name)
                    .append(" • `").append(rolesInGroup.size()).append(" ").append(t(guildId, "selectroles_roles_count")).append("`\n");
            }
            embed.setDescription(desc.toString());
        }

        List<ActionRow> rows = new ArrayList<>();

        // Gruppe auswählen (wenn vorhanden)
        if (!groups.isEmpty()) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("sr_select_group")
                .setPlaceholder(t(guildId, "selectroles_select_group_placeholder"))
                .setMinValues(1)
                .setMaxValues(1);

            for (DatabaseHandler.RoleSelectGroupData group : groups) {
                menuBuilder.addOption(group.name, String.valueOf(group.id),
                    group.title != null ? group.title : "No title set");
            }
            rows.add(ActionRow.of(menuBuilder.build()));
        }

        // Aktions-Buttons
        rows.add(ActionRow.of(
            Button.success("sr_group_create", "➕ " + t(guildId, "selectroles_btn_create_group")),
            Button.secondary("sr_back_main", "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showGroupDetails(ButtonInteractionEvent event, int groupId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroup(guildId, groupId);

        if (group == null) {
            event.reply(t(guildId, "selectroles_group_not_found", "")).setEphemeral(true).queue();
            return;
        }

        List<String> roleIds = handler.getRolesInGroup(guildId, groupId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📁 " + group.name);
        embed.setColor(parseColor(group.color));
        embed.setFooter(t(guildId, "selectroles_breadcrumb_group_detail", group.name));

        embed.addField("📝 " + t(guildId, "selectroles_group_title"), group.title, false);
        embed.addField("📄 " + t(guildId, "selectroles_group_description"),
            group.description != null ? group.description : "-", false);
        embed.addField("🎨 " + t(guildId, "selectroles_group_color"), group.color, true);
        embed.addField("📍 " + t(guildId, "selectroles_group_position"), String.valueOf(group.position + 1), true);

        // Rollen in der Gruppe
        if (!roleIds.isEmpty()) {
            StringBuilder rolesStr = new StringBuilder();
            for (String roleId : roleIds) {
                Role role = event.getGuild().getRoleById(roleId);
                if (role != null) {
                    String emoji = handler.getRoleSelectEmoji(guildId, roleId);
                    rolesStr.append(emoji != null ? emoji : "✅").append(" ").append(role.getAsMention()).append("\n");
                }
            }
            embed.addField("🏷️ " + t(guildId, "selectroles_roles_in_group") + " (" + roleIds.size() + ")",
                rolesStr.toString(), false);
        } else {
            embed.addField("🏷️ " + t(guildId, "selectroles_roles_in_group"),
                t(guildId, "selectroles_no_roles_in_group"), false);
        }

        List<ActionRow> rows = new ArrayList<>();

        // Dropdown für Rollen-Position verschieben (nur wenn mehrere Rollen vorhanden)
        if (roleIds.size() > 1) {
            StringSelectMenu.Builder moveRoleMenu = StringSelectMenu.create("sr_role_move_select_" + groupId)
                .setPlaceholder(t(guildId, "selectroles_select_role_to_move"))
                .setRequiredRange(1, 1);
            for (String roleId : roleIds) {
                Role role = event.getGuild().getRoleById(roleId);
                if (role != null) {
                    String emoji = handler.getRoleSelectEmoji(guildId, roleId);
                    if (emoji != null && !emoji.isEmpty()) {
                        try {
                            moveRoleMenu.addOption(role.getName(), roleId, Emoji.fromFormatted(emoji));
                        } catch (Exception e) {
                            moveRoleMenu.addOption(role.getName(), roleId);
                        }
                    } else {
                        moveRoleMenu.addOption(role.getName(), roleId);
                    }
                }
            }
            rows.add(ActionRow.of(moveRoleMenu.build()));
        }

        rows.add(ActionRow.of(
            Button.primary("sr_group_edit_" + groupId, "✏️ " + t(guildId, "general.edit")),
            Button.primary("sr_group_add_role_" + groupId, "➕ " + t(guildId, "selectroles_btn_add_role")),
            Button.success("sr_group_send_" + groupId, "📤 " + t(guildId, "selectroles_btn_send"))
        ));

        rows.add(ActionRow.of(
            Button.secondary("sr_group_up_" + groupId, "⬆️"),
            Button.secondary("sr_group_down_" + groupId, "⬇️"),
            Button.danger("sr_group_delete_" + groupId, "🗑️ " + t(guildId, "general.delete")),
            Button.secondary("sr_back_groups", "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showGroupDetailsFromSelect(StringSelectInteractionEvent event, int groupId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroup(guildId, groupId);

        if (group == null) {
            event.reply(t(guildId, "selectroles_group_not_found", "")).setEphemeral(true).queue();
            return;
        }

        List<String> roleIds = handler.getRolesInGroup(guildId, groupId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📁 " + group.name);
        embed.setColor(parseColor(group.color));
        embed.setFooter(t(guildId, "selectroles_breadcrumb_group_detail", group.name));

        embed.addField("📝 " + t(guildId, "selectroles_group_title"), group.title, false);
        embed.addField("📄 " + t(guildId, "selectroles_group_description"),
            group.description != null ? group.description : "-", false);
        embed.addField("🎨 " + t(guildId, "selectroles_group_color"), group.color, true);
        embed.addField("📍 " + t(guildId, "selectroles_group_position"), String.valueOf(group.position + 1), true);

        if (!roleIds.isEmpty()) {
            StringBuilder rolesStr = new StringBuilder();
            for (String roleId : roleIds) {
                Role role = event.getGuild().getRoleById(roleId);
                if (role != null) {
                    String emoji = handler.getRoleSelectEmoji(guildId, roleId);
                    rolesStr.append(emoji != null ? emoji : "✅").append(" ").append(role.getAsMention()).append("\n");
                }
            }
            embed.addField("🏷️ " + t(guildId, "selectroles_roles_in_group") + " (" + roleIds.size() + ")",
                rolesStr.toString(), false);
        } else {
            embed.addField("🏷️ " + t(guildId, "selectroles_roles_in_group"),
                t(guildId, "selectroles_no_roles_in_group"), false);
        }

        List<ActionRow> rows = new ArrayList<>();

        // Dropdown für Rollen-Position verschieben (nur wenn mehrere Rollen vorhanden)
        if (roleIds.size() > 1) {
            StringSelectMenu.Builder moveRoleMenu = StringSelectMenu.create("sr_role_move_select_" + groupId)
                .setPlaceholder(t(guildId, "selectroles_select_role_to_move"))
                .setRequiredRange(1, 1);
            for (String roleId : roleIds) {
                Role role = event.getGuild().getRoleById(roleId);
                if (role != null) {
                    String emoji = handler.getRoleSelectEmoji(guildId, roleId);
                    if (emoji != null && !emoji.isEmpty()) {
                        try {
                            moveRoleMenu.addOption(role.getName(), roleId, Emoji.fromFormatted(emoji));
                        } catch (Exception e) {
                            moveRoleMenu.addOption(role.getName(), roleId);
                        }
                    } else {
                        moveRoleMenu.addOption(role.getName(), roleId);
                    }
                }
            }
            rows.add(ActionRow.of(moveRoleMenu.build()));
        }

        rows.add(ActionRow.of(
            Button.primary("sr_group_edit_" + groupId, "✏️ " + t(guildId, "general.edit")),
            Button.primary("sr_group_add_role_" + groupId, "➕ " + t(guildId, "selectroles_btn_add_role")),
            Button.success("sr_group_send_" + groupId, "📤 " + t(guildId, "selectroles_btn_send"))
        ));

        rows.add(ActionRow.of(
            Button.secondary("sr_group_up_" + groupId, "⬆️"),
            Button.secondary("sr_group_down_" + groupId, "⬇️"),
            Button.danger("sr_group_delete_" + groupId, "🗑️ " + t(guildId, "general.delete")),
            Button.secondary("sr_back_groups", "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    // ==================== ROLE MOVE MENU UI ====================

    private void showRoleMoveMenu(StringSelectInteractionEvent event, int groupId, String roleId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        Role role = event.getGuild().getRoleById(roleId);

        if (role == null) {
            event.reply(t(guildId, "selectroles_role_not_found")).setEphemeral(true).queue();
            return;
        }

        List<String> roleIds = handler.getRolesInGroup(guildId, groupId);
        int currentIndex = roleIds.indexOf(roleId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🔃 " + t(guildId, "selectroles_move_role_title"));
        embed.setDescription(t(guildId, "selectroles_move_role_desc", role.getAsMention()));
        embed.setColor(new Color(88, 101, 242));
        DatabaseHandler.RoleSelectGroupData grp = handler.getRoleSelectGroup(guildId, groupId);
        embed.setFooter(t(guildId, "selectroles_breadcrumb_role_move", grp != null ? grp.name : String.valueOf(groupId)));

        // Zeige aktuelle Position
        embed.addField("📍 " + t(guildId, "selectroles_current_position"),
            String.valueOf(currentIndex + 1) + " / " + roleIds.size(), true);

        List<ActionRow> rows = new ArrayList<>();

        // Auf/Ab Buttons - deaktiviert wenn am Anfang/Ende
        boolean canMoveUp = currentIndex > 0;
        boolean canMoveDown = currentIndex < roleIds.size() - 1;

        rows.add(ActionRow.of(
            Button.secondary("sr_role_up_" + groupId + "_" + roleId, "⬆️ " + t(guildId, "selectroles_move_up")).withDisabled(!canMoveUp),
            Button.secondary("sr_role_down_" + groupId + "_" + roleId, "⬇️ " + t(guildId, "selectroles_move_down")).withDisabled(!canMoveDown),
            Button.primary("sr_back_group_" + groupId, "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showRoleMoveMenuFromButton(ButtonInteractionEvent event, int groupId, String roleId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        Role role = event.getGuild().getRoleById(roleId);

        if (role == null) {
            event.reply(t(guildId, "selectroles_role_not_found")).setEphemeral(true).queue();
            return;
        }

        List<String> roleIds = handler.getRolesInGroup(guildId, groupId);
        int currentIndex = roleIds.indexOf(roleId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🔃 " + t(guildId, "selectroles_move_role_title"));
        embed.setDescription(t(guildId, "selectroles_move_role_desc", role.getAsMention()));
        embed.setColor(new Color(88, 101, 242));
        DatabaseHandler.RoleSelectGroupData grp2 = handler.getRoleSelectGroup(guildId, groupId);
        embed.setFooter(t(guildId, "selectroles_breadcrumb_role_move", grp2 != null ? grp2.name : String.valueOf(groupId)));

        embed.addField("📍 " + t(guildId, "selectroles_current_position"),
            String.valueOf(currentIndex + 1) + " / " + roleIds.size(), true);

        List<ActionRow> rows = new ArrayList<>();

        boolean canMoveUp = currentIndex > 0;
        boolean canMoveDown = currentIndex < roleIds.size() - 1;

        rows.add(ActionRow.of(
            Button.secondary("sr_role_up_" + groupId + "_" + roleId, "⬆️ " + t(guildId, "selectroles_move_up")).withDisabled(!canMoveUp),
            Button.secondary("sr_role_down_" + groupId + "_" + roleId, "⬇️ " + t(guildId, "selectroles_move_down")).withDisabled(!canMoveDown),
            Button.primary("sr_back_group_" + groupId, "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    // ==================== ROLES MANAGEMENT UI ====================

    private void showRolesMenu(ButtonInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        List<String> ungroupedRoles = handler.getUngroupedRoles(guildId);
        List<DatabaseHandler.RoleSelectGroupData> groups = handler.getRoleSelectGroups(guildId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🏷️ " + t(guildId, "selectroles_roles_title"));
        embed.setColor(new Color(235, 69, 158)); // Pink
        embed.setFooter(t(guildId, "selectroles_breadcrumb_roles"));

        // Ungrouped Roles
        if (!ungroupedRoles.isEmpty()) {
            StringBuilder rolesStr = new StringBuilder();
            for (String roleId : ungroupedRoles) {
                Role role = event.getGuild().getRoleById(roleId);
                if (role != null) {
                    String emoji = handler.getRoleSelectEmoji(guildId, roleId);
                    rolesStr.append(emoji != null ? emoji : "✅").append(" ").append(role.getAsMention()).append("\n");
                }
            }
            embed.addField("📦 " + t(guildId, "selectroles_ungrouped") + " (" + ungroupedRoles.size() + ")",
                rolesStr.toString(), false);
        }

        // Grouped Roles
        for (DatabaseHandler.RoleSelectGroupData group : groups) {
            List<String> groupRoles = handler.getRolesInGroup(guildId, group.id);
            if (!groupRoles.isEmpty()) {
                StringBuilder rolesStr = new StringBuilder();
                for (String roleId : groupRoles) {
                    Role role = event.getGuild().getRoleById(roleId);
                    if (role != null) {
                        String emoji = handler.getRoleSelectEmoji(guildId, roleId);
                        rolesStr.append(emoji != null ? emoji : "✅").append(" ").append(role.getAsMention()).append("\n");
                    }
                }
                embed.addField("📁 " + group.name + " (" + groupRoles.size() + ")",
                    rolesStr.toString(), false);
            }
        }

        if (ungroupedRoles.isEmpty() && groups.stream().allMatch(g -> handler.getRolesInGroup(guildId, g.id).isEmpty())) {
            embed.setDescription(t(guildId, "selectroles_no_roles_hint"));
        }

        List<ActionRow> rows = new ArrayList<>();

        // Role Select Menu zum Hinzufügen
        rows.add(ActionRow.of(
            EntitySelectMenu.create("sr_add_role_select", EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder(t(guildId, "selectroles_add_role_placeholder"))
                .setMinValues(1)
                .setMaxValues(1)
                .build()
        ));

        // Wenn Rollen vorhanden, Dropdown zum Entfernen
        List<String> allRoles = new ArrayList<>(ungroupedRoles);
        for (DatabaseHandler.RoleSelectGroupData group : groups) {
            allRoles.addAll(handler.getRolesInGroup(guildId, group.id));
        }

        if (!allRoles.isEmpty()) {
            StringSelectMenu.Builder removeMenuBuilder = StringSelectMenu.create("sr_remove_role_select")
                .setPlaceholder(t(guildId, "selectroles_remove_role_placeholder"))
                .setMinValues(1)
                .setMaxValues(1);

            for (String roleId : allRoles) {
                Role role = event.getGuild().getRoleById(roleId);
                if (role != null) {
                    removeMenuBuilder.addOption(role.getName(), roleId);
                }
            }
            rows.add(ActionRow.of(removeMenuBuilder.build()));
        }

        rows.add(ActionRow.of(
            Button.secondary("sr_back_main", "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    // ==================== SEND UI ====================

    private void showSendMenu(ButtonInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        List<DatabaseHandler.RoleSelectGroupData> groups = handler.getRoleSelectGroups(guildId);
        List<String> ungroupedRoles = handler.getUngroupedRoles(guildId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📤 " + t(guildId, "selectroles_send_title"));
        embed.setDescription(t(guildId, "selectroles_send_description"));
        embed.setColor(new Color(87, 242, 135));
        embed.setFooter(t(guildId, "selectroles_breadcrumb_send"));

        List<ActionRow> rows = new ArrayList<>();

        // Dropdown für Gruppen-Auswahl
        if (!groups.isEmpty() || !ungroupedRoles.isEmpty()) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("sr_send_select")
                .setPlaceholder(t(guildId, "selectroles_send_select_placeholder"))
                .setMinValues(1)
                .setMaxValues(1);

            if (!ungroupedRoles.isEmpty()) {
                menuBuilder.addOption(t(guildId, "selectroles_ungrouped") + " (" + ungroupedRoles.size() + " Rollen)",
                    "ungrouped", "Alle nicht gruppierten Rollen senden");
            }

            for (DatabaseHandler.RoleSelectGroupData group : groups) {
                List<String> rolesInGroup = handler.getRolesInGroup(guildId, group.id);
                menuBuilder.addOption(group.name + " (" + rolesInGroup.size() + " Rollen)",
                    "group_" + group.id, group.title);
            }

            rows.add(ActionRow.of(menuBuilder.build()));
        } else {
            embed.setDescription(t(guildId, "selectroles_no_roles_to_send"));
        }

        rows.add(ActionRow.of(
            Button.secondary("sr_back_main", "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showSendTypeSelection(StringSelectInteractionEvent event, String selection) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        UserSession session = getOrCreateSession(event.getUser().getId(), guildId);
        session.pendingSendSelection = selection;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📤 " + t(guildId, "selectroles_send_type_title"));
        embed.setDescription(t(guildId, "selectroles_send_type_description"));
        embed.setColor(new Color(87, 242, 135));
        embed.setFooter(t(guildId, "selectroles_breadcrumb_send_type"));

        String targetInfo;
        if (selection.equals("ungrouped")) {
            session.selectedGroupId = 0; // 0 = ungrouped
            targetInfo = t(guildId, "selectroles_ungrouped");
        } else {
            int groupId = Integer.parseInt(selection.replace("group_", ""));
            session.selectedGroupId = groupId;
            DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroup(guildId, groupId);
            targetInfo = group != null ? group.name : "Unknown";
        }

        embed.addField(t(guildId, "selectroles_sending"), targetInfo, false);
        embed.addField(t(guildId, "selectroles_choose_type"), t(guildId, "selectroles_choose_type_desc"), false);

        List<ActionRow> rows = new ArrayList<>();

        // Typ-Auswahl Buttons
        rows.add(ActionRow.of(
            Button.primary("sr_type_reaction", "😀 " + t(guildId, "selectroles_type_reaction")),
            Button.primary("sr_type_buttons", "🔘 " + t(guildId, "selectroles_type_buttons"))
        ));

        rows.add(ActionRow.of(
            Button.secondary("sr_back_send", "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showChannelSelection(ButtonInteractionEvent event, String sendType) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        UserSession session = getOrCreateSession(event.getUser().getId(), guildId);
        session.pendingSendType = sendType; // "reaction" oder "buttons"


        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📤 " + t(guildId, "selectroles_select_channel"));
        embed.setDescription(t(guildId, "selectroles_select_channel_desc"));
        embed.setColor(new Color(87, 242, 135));
        embed.setFooter(t(guildId, "selectroles_breadcrumb_send_channel"));

        String targetInfo;
        if (session.pendingSendSelection != null && session.pendingSendSelection.equals("ungrouped")) {
            targetInfo = t(guildId, "selectroles_ungrouped");
        } else if (session.pendingSendSelection != null) {
            int groupId = Integer.parseInt(session.pendingSendSelection.replace("group_", ""));
            DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroup(guildId, groupId);
            targetInfo = group != null ? group.name : "Unknown";
        } else {
            targetInfo = "Unknown";
        }

        String typeInfo = sendType.equals("reaction") ? t(guildId, "selectroles_type_reaction") : t(guildId, "selectroles_type_buttons");

        embed.addField(t(guildId, "selectroles_sending"), targetInfo, true);
        embed.addField(t(guildId, "selectroles_choose_type"), typeInfo, true);

        List<ActionRow> rows = new ArrayList<>();

        // Channel-Auswahl
        rows.add(ActionRow.of(
            EntitySelectMenu.create("sr_send_channel_select", EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(net.dv8tion.jda.api.entities.channel.ChannelType.TEXT)
                .setPlaceholder(t(guildId, "selectroles_select_channel_placeholder"))
                .setMinValues(1)
                .setMaxValues(1)
                .build()
        ));

        rows.add(ActionRow.of(
            Button.secondary("sr_back_send", "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    @Override
    public void onMessageReactionAdd (net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) return;

        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String messageId = event.getMessageId();
        String userId = event.getUser().getId();

        // Prüfe ob dies eine Emoji-Auswahl für Rollen-Konfiguration ist
        UserSession session = userSessions.get(userId);
        if (session != null && messageId.equals(session.pendingEmoji)) {
            String emoji = event.getReaction().getEmoji().getFormatted();
            String roleId = session.selectedRoleId;
            String description = session.pendingDescription;
            int groupId = session.selectedGroupId;

            // Speichere die Rolle mit dem ausgewählten Emoji
            if (groupId > 0) {
                handler.addRoleSelectToGroup(guildId, roleId, groupId, description, emoji);
            } else {
                handler.addRoleSelectToGuild(guildId, roleId, description, emoji);
            }

            // Lösche die Auswahl-Nachricht
            event.getChannel().deleteMessageById(messageId).queue(
                success -> {},
                error -> {} // Ignoriere Fehler beim Löschen
            );

            Role role = event.getGuild().getRoleById(roleId);
            String roleName = role != null ? role.getName() : roleId;

            event.getChannel().sendMessage(t(guildId, "selectroles_role_added_with_emoji", roleName, emoji))
                .queue(msg -> msg.delete().queueAfter(30, java.util.concurrent.TimeUnit.SECONDS));

            // Session aufräumen
            session.pendingEmoji = null;
            session.selectedRoleId = null;
            session.pendingDescription = null;
            session.selectedGroupId = -1;

            return;
        }

        // Bestehende Reaktionsrollen-Logik
        if (handler.getAllRoleSelectForGuild(guildId).isEmpty()) return;

        String emoji = event.getReaction().getEmoji().getFormatted();
        String roleId = handler.getRoleSelectRoleIDByEmoji(guildId, emoji);
        if (roleId != null) {
            Role role = event.getGuild().getRoleById(roleId);
            if (event.getMember() == null || event.getMember().getRoles().contains(role)) {
                return;
            }
            if (role != null) {
                Objects.requireNonNull(event.getMember()).getGuild().addRoleToMember(event.getMember(), role).queue(
                    success -> {},
                    error -> System.out.println("Fehler beim Hinzufügen der Rolle via Reaction Role: " + error.getMessage())
                );
            }
        }
    }

    @Override
    public void onMessageReactionRemove (MessageReactionRemoveEvent event) {
        Member member = event.getGuild().getMemberById(event.getUserId());
        if (member == null) {
            member = event.getGuild().retrieveMemberById(event.getUserId()).complete();
        }
        if (handler.getAllRoleSelectForGuild(event.getGuild().getId()).isEmpty()) {
            return;
        }
        if (event.getUser() != null && event.getUser().isBot()) {
            return;
        }
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String emoji = event.getReaction().getEmoji().getFormatted();
        String roleId = handler.getRoleSelectRoleIDByEmoji(guildId, emoji);
        if (roleId != null) {
            Role role = event.getGuild().getRoleById(roleId);
            if (member == null || !member.getRoles().contains(role)) {
                return;
            }
            if (role != null) {
                member.getGuild().removeRoleFromMember(member, role).queue(
                    success -> {},
                    error -> System.out.println("Fehler beim Entfernen der Rolle via Reaction Role: " + error.getMessage())
                );
            }
        }
    }

    // ==================== BUTTON INTERACTION HANDLER ====================

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getButton().getCustomId();
        if (buttonId == null) return;

        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        // UI Navigation Buttons
        if (buttonId.equals("sr_groups")) {
            showGroupsMenu(event);
        } else if (buttonId.equals("sr_roles")) {
            showRolesMenu(event);
        } else if (buttonId.equals("sr_send")) {
            showSendMenu(event);
        } else if (buttonId.equals("sr_settings")) {
            showSettingsMenu(event);
        } else if (buttonId.equals("sr_close")) {
            event.getMessage().delete().queue();
        } else if (buttonId.equals("sr_back_main")) {
            showMainMenuEdit(event);
        } else if (buttonId.equals("sr_back_groups")) {
            showGroupsMenu(event);
        } else if (buttonId.equals("sr_back_send")) {
            showSendMenu(event);
        } else if (buttonId.equals("sr_back_roles")) {
            showRolesMenu(event);
        }
        // Group Creation
        else if (buttonId.equals("sr_group_create")) {
            showGroupCreateModal(event);
        }
        // Group Details & Actions
        else if (buttonId.startsWith("sr_group_edit_")) {
            int groupId = Integer.parseInt(buttonId.replace("sr_group_edit_", ""));
            showGroupEditModal(event, groupId);
        } else if (buttonId.startsWith("sr_group_add_role_")) {
            int groupId = Integer.parseInt(buttonId.replace("sr_group_add_role_", ""));
            showAddRoleToGroupMenu(event, groupId);
        } else if (buttonId.startsWith("sr_group_send_")) {
            int groupId = Integer.parseInt(buttonId.replace("sr_group_send_", ""));
            showGroupSendTypeMenu(event, groupId);
        } else if (buttonId.startsWith("sr_group_up_")) {
            int groupId = Integer.parseInt(buttonId.replace("sr_group_up_", ""));
            handler.moveGroupUp(guildId, groupId);
            showGroupDetails(event, groupId);
        } else if (buttonId.startsWith("sr_group_down_")) {
            int groupId = Integer.parseInt(buttonId.replace("sr_group_down_", ""));
            handler.moveGroupDown(guildId, groupId);
            showGroupDetails(event, groupId);
        } else if (buttonId.startsWith("sr_group_delete_")) {
            int groupId = Integer.parseInt(buttonId.replace("sr_group_delete_", ""));
            showGroupDeleteConfirm(event, groupId);
        } else if (buttonId.startsWith("sr_group_delete_confirm_")) {
            int groupId = Integer.parseInt(buttonId.replace("sr_group_delete_confirm_", ""));
            handler.deleteRoleSelectGroup(guildId, groupId);
            showGroupsMenu(event);
        } else if (buttonId.equals("sr_group_delete_cancel")) {
            showGroupsMenu(event);
        }
        // Role Move Buttons
        else if (buttonId.startsWith("sr_role_up_")) {
            String remaining = buttonId.replace("sr_role_up_", "");
            int underscoreIndex = remaining.indexOf("_");
            int groupId = Integer.parseInt(remaining.substring(0, underscoreIndex));
            String roleId = remaining.substring(underscoreIndex + 1);
            handler.moveRoleUpInGroup(guildId, groupId, roleId);
            showRoleMoveMenuFromButton(event, groupId, roleId);
        } else if (buttonId.startsWith("sr_role_down_")) {
            String remaining = buttonId.replace("sr_role_down_", "");
            int underscoreIndex = remaining.indexOf("_");
            int groupId = Integer.parseInt(remaining.substring(0, underscoreIndex));
            String roleId = remaining.substring(underscoreIndex + 1);
            handler.moveRoleDownInGroup(guildId, groupId, roleId);
            showRoleMoveMenuFromButton(event, groupId, roleId);
        } else if (buttonId.startsWith("sr_back_group_")) {
            int groupId = Integer.parseInt(buttonId.replace("sr_back_group_", ""));
            showGroupDetails(event, groupId);
        }
        // Emoji Selection Buttons
        else if (buttonId.startsWith("sr_emoji_skip_")) {
            String roleId = buttonId.replace("sr_emoji_skip_", "");
            UserSession session = getOrCreateSession(event.getUser().getId(), guildId);
            String description = session.pendingDescription;
            int groupId = session.selectedGroupId;

            // Verwende Standard-Emoji ✅
            String defaultEmoji = "✅";

            if (groupId > 0) {
                handler.addRoleSelectToGroup(guildId, roleId, groupId, description, defaultEmoji);
            } else {
                handler.addRoleSelectToGuild(guildId, roleId, description, defaultEmoji);
            }

            Role role = event.getGuild().getRoleById(roleId);
            String roleName = role != null ? role.getName() : roleId;

            event.editMessage(t(guildId, "selectroles_role_added_with_emoji", roleName, defaultEmoji))
                .setEmbeds()
                .setComponents()
                .queue();

            // Session aufräumen
            session.pendingEmoji = null;
            session.selectedRoleId = null;
            session.pendingDescription = null;
            session.selectedGroupId = -1;
        }
        else if (buttonId.startsWith("sr_emoji_cancel_")) {
            UserSession session = userSessions.get(event.getUser().getId());
            if (session != null) {
                session.pendingEmoji = null;
                session.selectedRoleId = null;
                session.pendingDescription = null;
                session.selectedGroupId = -1;
            }

            event.editMessage(t(guildId, "selectroles_cancelled"))
                .setEmbeds()
                .setComponents()
                .queue();
        }
        // Send Type Buttons - zeigen Channel-Auswahl
        else if (buttonId.equals("sr_type_reaction")) {
            showChannelSelection(event, "reaction");
        } else if (buttonId.equals("sr_type_buttons")) {
            showChannelSelection(event, "buttons");
        } else if (buttonId.startsWith("sr_send_reaction_")) {
            String selection = buttonId.replace("sr_send_reaction_", "");
            executeSendReaction(event, selection);
        } else if (buttonId.startsWith("sr_send_buttons_")) {
            String selection = buttonId.replace("sr_send_buttons_", "");
            executeSendButtons(event, selection);
        }
        // Direct Role Toggle (für Endnutzer)
        else if (buttonId.startsWith("select_role_")) {
            event.deferReply(true).queue();
            String roleId = buttonId.replace("select_role_", "");
            Role role = event.getGuild().getRoleById(roleId);
            if (role != null) {
                if (Objects.requireNonNull(event.getMember()).getRoles().contains(role)) {
                    event.getGuild().removeRoleFromMember(event.getMember(), role).queue(
                        success -> event.getHook().sendMessage(t(guildId, "selectroles_role_removed", role.getAsMention())).setEphemeral(true).queue(),
                        error -> event.getHook().sendMessage(t(guildId, "selectroles_error_role_change")).setEphemeral(true).queue()
                    );
                } else {
                    event.getGuild().addRoleToMember(event.getMember(), role).queue(
                        success -> event.getHook().sendMessage(t(guildId, "selectroles_role_given", role.getAsMention())).setEphemeral(true).queue(),
                        error -> event.getHook().sendMessage(t(guildId, "selectroles_error_role_change")).setEphemeral(true).queue()
                    );
                }
            }
        }
        // Legacy button support
        else if (buttonId.startsWith("role_select_button_")) {
            event.deferReply(true).queue();
            String selectId = buttonId.replace("role_select_button_", "");
            List<String> roleIds = handler.getAllRoleSelectForGuild(guildId);
            for (String roleInfo : roleIds) {
                String currentSelectId = String.valueOf(handler.getRoleSelectID(guildId, roleInfo));
                if (currentSelectId.equals(selectId)) {
                    Role role = event.getGuild().getRoleById(roleInfo);
                    if (role != null) {
                        if (Objects.requireNonNull(event.getMember()).getRoles().contains(role)) {
                            event.getGuild().removeRoleFromMember(event.getMember(), role).queue(
                                success -> event.getHook().sendMessage(t(guildId, "selectroles_role_removed", role.getAsMention())).setEphemeral(true).queue(),
                                error -> event.getHook().sendMessage(t(guildId, "selectroles_error_role_change")).setEphemeral(true).queue()
                            );
                        } else {
                            event.getGuild().addRoleToMember(event.getMember(), role).queue(
                                success -> event.getHook().sendMessage(t(guildId, "selectroles_role_given", role.getAsMention())).setEphemeral(true).queue(),
                                error -> event.getHook().sendMessage(t(guildId, "selectroles_error_role_change")).setEphemeral(true).queue()
                            );
                        }
                        return;
                    }
                }
            }
        }
    }

    // ==================== STRING SELECT INTERACTION HANDLER ====================

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String menuId = event.getSelectMenu().getCustomId();
        if (menuId == null) return;

        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        if (menuId.equals("sr_select_group")) {
            int groupId = Integer.parseInt(event.getValues().get(0));
            showGroupDetailsFromSelect(event, groupId);
        } else if (menuId.equals("sr_send_select")) {
            String selection = event.getValues().get(0);
            showSendTypeSelection(event, selection);
        } else if (menuId.equals("sr_remove_role_select")) {
            String roleId = event.getValues().get(0);
            handler.removeRoleSelectFromGuild(guildId, roleId);
            event.reply(t(guildId, "selectroles_role_removed_success")).setEphemeral(true).queue();
        } else if (menuId.startsWith("sr_assign_group_")) {
            String roleId = menuId.replace("sr_assign_group_", "");
            String groupSelection = event.getValues().get(0);

            if (groupSelection.equals("ungrouped")) {
                handler.removeRoleFromGroup(guildId, roleId);
            } else {
                int groupId = Integer.parseInt(groupSelection);
                String desc = handler.getRoleSelectDescription(guildId, roleId);
                String emoji = handler.getRoleSelectEmoji(guildId, roleId);
                handler.addRoleSelectToGroup(guildId, roleId, groupId, desc, emoji);
            }
            event.reply(t(guildId, "selectroles_role_assigned")).setEphemeral(true).queue();
        } else if (menuId.startsWith("sr_role_move_select_")) {
            int groupId = Integer.parseInt(menuId.replace("sr_role_move_select_", ""));
            String roleId = event.getValues().get(0);
            showRoleMoveMenu(event, groupId, roleId);
        }
        // Legacy dropdown support
        else if (menuId.equals("role_select_dropdown")) {
            event.deferReply(true).queue();
            List<String> selectedRoleIds = event.getValues();
            for (String roleId : selectedRoleIds) {
                Role role = event.getGuild().getRoleById(roleId);
                if (role != null) {
                    if (!Objects.requireNonNull(event.getMember()).getRoles().contains(role)) {
                        event.getGuild().addRoleToMember(event.getMember(), role).queue(
                            success -> event.getHook().sendMessage(t(guildId, "selectroles_role_given", role.getAsMention())).setEphemeral(true).queue(),
                            error -> event.getHook().sendMessage(t(guildId, "selectroles_error_role_change")).setEphemeral(true).queue()
                        );
                    } else {
                        event.getGuild().removeRoleFromMember(event.getMember(), role).queue(
                            success -> event.getHook().sendMessage(t(guildId, "selectroles_role_removed", role.getAsMention())).setEphemeral(true).queue(),
                            error -> event.getHook().sendMessage(t(guildId, "selectroles_error_role_change")).setEphemeral(true).queue()
                        );
                    }
                }
            }
        }
    }

    // ==================== ENTITY SELECT INTERACTION HANDLER ====================

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String menuId = event.getSelectMenu().getCustomId();
        if (menuId == null) return;

        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        UserSession session = getOrCreateSession(event.getUser().getId(), guildId);

        if (menuId.equals("sr_add_role_select")) {
            Role role = (Role) event.getMentions().getRoles().get(0);
            if (role != null) {
                session.selectedRoleId = role.getId();
                showRoleConfigModal(event, role);
            }
        } else if (menuId.startsWith("sr_group_role_select_")) {
            int groupId = Integer.parseInt(menuId.replace("sr_group_role_select_", ""));
            Role role = (Role) event.getMentions().getRoles().get(0);
            if (role != null) {
                session.selectedRoleId = role.getId();
                session.selectedGroupId = groupId;
                showRoleConfigModalForGroup(event, role, groupId);
            }
        } else if (menuId.equals("sr_send_channel_select")) {
            net.dv8tion.jda.api.entities.channel.middleman.GuildChannel channel = event.getMentions().getChannels().get(0);
            if (channel != null) {
                session.selectedChannelId = channel.getId();
                executeSendToChannel(event, session.pendingSendSelection, session.pendingSendType, channel.getId());
            }
        }
    }

    // ==================== MODAL INTERACTION HANDLER ====================

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        if (modalId.equals("sr_group_create_modal")) {
            String name = event.getValue("group_name").getAsString();
            String title = event.getValue("group_title") != null ? event.getValue("group_title").getAsString() : null;
            String description = event.getValue("group_description") != null ? event.getValue("group_description").getAsString() : null;
            String color = event.getValue("group_color") != null ? event.getValue("group_color").getAsString() : "#3498db";

            // Prüfen ob Gruppe bereits existiert
            if (handler.getRoleSelectGroupByName(guildId, name) != null) {
                event.reply(t(guildId, "selectroles_group_exists", name)).setEphemeral(true).queue();
                return;
            }

            int groupId = handler.createRoleSelectGroup(guildId, name);
            if (groupId > 0 && (title != null || description != null || color != null)) {
                handler.updateRoleSelectGroup(groupId, guildId,
                    title != null ? title : "Select Your Roles",
                    description != null ? description : "Choose from the roles below:",
                    null, color);
            }

            event.reply(t(guildId, "selectroles_group_created", name)).setEphemeral(true).queue();
        } else if (modalId.startsWith("sr_group_edit_modal_")) {
            int groupId = Integer.parseInt(modalId.replace("sr_group_edit_modal_", ""));

            String title = event.getValue("group_title").getAsString();
            String description = event.getValue("group_description").getAsString();
            String footer = event.getValue("group_footer") != null ? event.getValue("group_footer").getAsString() : null;
            String color = event.getValue("group_color").getAsString();

            handler.updateRoleSelectGroup(groupId, guildId, title, description, footer, color);
            event.reply(t(guildId, "selectroles_group_updated", "")).setEphemeral(true).queue();
        } else if (modalId.equals("sr_role_config_modal")) {
            UserSession session = getOrCreateSession(event.getUser().getId(), guildId);
            String roleId = session.selectedRoleId;
            String description = event.getValue("role_description") != null
                ? event.getValue("role_description").getAsString() : "";

            // Zeige Emoji-Auswahl per Reaktion
            showEmojiSelectionMessage(event, roleId, description, session.selectedGroupId > 0 ? session.selectedGroupId : null);
        } else if (modalId.startsWith("sr_role_config_group_modal_")) {
            int groupId = Integer.parseInt(modalId.replace("sr_role_config_group_modal_", ""));
            UserSession session = getOrCreateSession(event.getUser().getId(), guildId);
            String roleId = session.selectedRoleId;
            String description = event.getValue("role_description") != null
                ? event.getValue("role_description").getAsString() : "";

            // Zeige Emoji-Auswahl per Reaktion
            showEmojiSelectionMessage(event, roleId, description, groupId);
        }
    }

    // ==================== EMOJI SELECTION VIA REACTION ====================

    private void showEmojiSelectionMessage(ModalInteractionEvent event, String roleId, String description, Integer groupId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        Role role = event.getGuild().getRoleById(roleId);

        if (role == null) {
            event.reply(t(guildId, "selectroles_role_not_found")).setEphemeral(true).queue();
            return;
        }

        UserSession session = getOrCreateSession(event.getUser().getId(), guildId);
        session.selectedRoleId = roleId;
        session.pendingDescription = description;
        session.selectedGroupId = groupId != null ? groupId : -1;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("😀 " + t(guildId, "selectroles_select_emoji_title"));
        embed.setDescription(t(guildId, "selectroles_select_emoji_desc", role.getName()));
        embed.setColor(new Color(88, 101, 242));
        embed.addField(t(guildId, "general.role"), role.getAsMention(), true);
        if (description != null && !description.isEmpty()) {
            embed.addField(t(guildId, "selectroles_role_description"), description, false);
        }
        embed.setFooter(t(guildId, "selectroles_select_emoji_footer"));

        // Sende eine Nachricht, auf die der User reagieren kann
        event.replyEmbeds(embed.build())
            .setComponents(ActionRow.of(
                Button.secondary("sr_emoji_skip_" + roleId, "⏭️ " + t(guildId, "selectroles_use_default_emoji")),
                Button.danger("sr_emoji_cancel_" + roleId, "❌ " + t(guildId, "general.cancel"))
            ))
            .setEphemeral(false) // Muss public sein für Reaktionen
            .queue(interactionHook -> interactionHook.retrieveOriginal().queue(message ->
                session.pendingEmoji = message.getId()
            ));
    }

    // ==================== MODAL DISPLAY METHODS ====================

    private void showGroupCreateModal(ButtonInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        TextInput nameInput = TextInput.create("group_name", TextInputStyle.SHORT)
            .setPlaceholder("z.B. Gaming, Colors, Pronouns...")
            .setRequiredRange(1, 64)
            .build();

        TextInput titleInput = TextInput.create("group_title", TextInputStyle.SHORT)
            .setPlaceholder("Titel des Embeds")
            .setRequired(false)
            .setMaxLength(255)
            .build();

        TextInput descInput = TextInput.create("group_description", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Beschreibung des Embeds...")
            .setRequired(false)
            .setMaxLength(1000)
            .build();

        TextInput colorInput = TextInput.create("group_color", TextInputStyle.SHORT)
            .setPlaceholder("#3498db oder blue, red, green...")
            .setRequired(false)
            .setMaxLength(32)
            .build();

        Modal modal = Modal.create("sr_group_create_modal", t(guildId, "selectroles_create_group_modal_title"))
            .addComponents(
                Label.of(t(guildId, "selectroles_group_name"), nameInput),
                Label.of(t(guildId, "selectroles_group_title"), titleInput),
                Label.of(t(guildId, "selectroles_group_description"), descInput),
                Label.of(t(guildId, "selectroles_group_color"), colorInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void showGroupEditModal(ButtonInteractionEvent event, int groupId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroup(guildId, groupId);

        if (group == null) {
            event.reply(t(guildId, "selectroles_group_not_found", "")).setEphemeral(true).queue();
            return;
        }

        TextInput titleInput = TextInput.create("group_title", TextInputStyle.SHORT)
            .setValue(group.title)
            .setRequiredRange(1, 255)
            .build();

        TextInput descInput = TextInput.create("group_description", TextInputStyle.PARAGRAPH)
            .setValue(group.description)
            .setRequired(false)
            .setMaxLength(1000)
            .build();

        TextInput footerInput = TextInput.create("group_footer", TextInputStyle.SHORT)
            .setValue(group.footer != null ? group.footer : "Write a footer...")
            .setRequired(false)
            .setMaxLength(255)
            .build();

        TextInput colorInput = TextInput.create("group_color", TextInputStyle.SHORT)
            .setValue(group.color)
            .setRequired(false)
            .setMaxLength(32)
            .build();

        Modal modal = Modal.create("sr_group_edit_modal_" + groupId, t(guildId, "selectroles_edit_group_modal_title"))
            .addComponents(
                Label.of(t(guildId, "selectroles_group_title"), titleInput),
                Label.of(t(guildId, "selectroles_group_description"), descInput),
                Label.of(t(guildId, "selectroles_group_footer"), footerInput),
                Label.of(t(guildId, "selectroles_group_color"), colorInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void showAddRoleToGroupMenu(ButtonInteractionEvent event, int groupId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("➕ " + t(guildId, "selectroles_add_role_title"));
        embed.setDescription(t(guildId, "selectroles_add_role_to_group_desc"));
        embed.setColor(new Color(87, 242, 135));

        List<ActionRow> rows = new ArrayList<>();

        rows.add(ActionRow.of(
            EntitySelectMenu.create("sr_group_role_select_" + groupId, EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder(t(guildId, "selectroles_select_role_placeholder"))
                .setMinValues(1)
                .setMaxValues(1)
                .build()
        ));

        rows.add(ActionRow.of(
            Button.secondary("sr_back_groups", "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showRoleConfigModal(EntitySelectInteractionEvent event, Role role) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        TextInput descInput = TextInput.create("role_description", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Beschreibung für diese Rolle...")
            .setRequired(false)
            .setMaxLength(255)
            .build();

        Modal modal = Modal.create("sr_role_config_modal", t(guildId, "selectroles_config_role_modal_title") + " - " + role.getName())
            .addComponents(
                Label.of(t(guildId, "selectroles_role_description"), descInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void showRoleConfigModalForGroup(EntitySelectInteractionEvent event, Role role, int groupId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        TextInput descInput = TextInput.create("role_description", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Beschreibung für diese Rolle...")
            .setRequired(false)
            .setMaxLength(255)
            .build();

        Modal modal = Modal.create("sr_role_config_group_modal_" + groupId, t(guildId, "selectroles_config_role_modal_title") + " - " + role.getName())
            .addComponents(
                Label.of(t(guildId, "selectroles_role_description"), descInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void showGroupDeleteConfirm(ButtonInteractionEvent event, int groupId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroup(guildId, groupId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚠️ " + t(guildId, "selectroles_delete_confirm_title"));
        embed.setDescription(t(guildId, "selectroles_delete_confirm_desc", group != null ? group.name : "Unknown"));
        embed.setColor(Color.RED);

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
            Button.danger("sr_group_delete_confirm_" + groupId, "🗑️ " + t(guildId, "selectroles_btn_confirm_delete")),
            Button.secondary("sr_group_delete_cancel", "❌ " + t(guildId, "general.cancel"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showGroupSendTypeMenu(ButtonInteractionEvent event, int groupId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroup(guildId, groupId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📤 " + t(guildId, "selectroles_send_type_title"));
        embed.setDescription(t(guildId, "selectroles_send_type_description"));
        embed.setColor(new Color(87, 242, 135));

        if (group != null) {
            embed.addField(t(guildId, "selectroles_sending"), group.name, false);
        }

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
            Button.primary("sr_send_reaction_group_" + groupId, "😀 " + t(guildId, "selectroles_type_reaction")),
            Button.primary("sr_send_buttons_group_" + groupId, "🔘 " + t(guildId, "selectroles_type_buttons"))
        ));
        rows.add(ActionRow.of(
            Button.secondary("sr_back_groups", "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showSettingsMenu(ButtonInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚙️ " + t(guildId, "selectroles_settings_title"));
        embed.setDescription(t(guildId, "selectroles_settings_desc"));
        embed.setColor(new Color(153, 170, 181));
        embed.setFooter(t(guildId, "selectroles_breadcrumb_settings"));

        // Zeige aktuelle Einstellungen
        embed.addField("📋 " + t(guildId, "selectroles_default_embed"),
            t(guildId, "selectroles_settings_default_hint"), false);

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
            Button.primary("sr_edit_default_embed", "✏️ " + t(guildId, "selectroles_btn_edit_default")),
            Button.secondary("sr_back_main", "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    // ==================== SEND EXECUTION METHODS ====================

    private void executeSendToChannel(EntitySelectInteractionEvent event, String selection, String sendType, String channelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        TextChannel channel = event.getGuild().getTextChannelById(channelId);

        if (channel == null) {
            event.reply(t(guildId, "selectroles_error_channel_not_found")).setEphemeral(true).queue();
            return;
        }

        if (selection == null || sendType == null) {
            event.reply(t(guildId, "selectroles_error_no_selection")).setEphemeral(true).queue();
            return;
        }

        if (sendType.equals("reaction")) {
            if (selection.equals("ungrouped")) {
                handleSendSelectRolesReaction(event.getGuild(), channel);
            } else if (selection.startsWith("group_")) {
                int groupId = Integer.parseInt(selection.replace("group_", ""));
                handleSendSelectRolesReactionForGroup(event.getGuild(), channel, groupId);
            }
        } else if (sendType.equals("buttons")) {
            if (selection.equals("ungrouped")) {
                handleSendSelectRolesButtons(event.getGuild(), channel);
            } else if (selection.startsWith("group_")) {
                int groupId = Integer.parseInt(selection.replace("group_", ""));
                handleSendSelectRolesButtonsForGroup(event.getGuild(), channel, groupId);
            }
        }

        event.editMessage(t(guildId, "selectroles_sent_success_to_channel", channel.getAsMention()))
            .setEmbeds()
            .setComponents()
            .queue();

        // Session aufräumen
        UserSession session = userSessions.get(event.getUser().getId());
        if (session != null) {
            session.pendingSendSelection = null;
            session.selectedChannelId = null;
            session.pendingSendType = null;
        }
    }

    private void executeSendReaction(ButtonInteractionEvent event, String selection) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        Channel channel = event.getChannel();

        if (selection.equals("ungrouped")) {
            handleSendSelectRolesReaction(event.getGuild(), channel);
        } else if (selection.startsWith("group_")) {
            int groupId = Integer.parseInt(selection.replace("group_", ""));
            System.out.println("groupId: " + groupId);
            handleSendSelectRolesReactionForGroup(event.getGuild(), channel, groupId);
        }

        event.editMessage(t(guildId, "selectroles_sent_success"))
            .setEmbeds()
            .setComponents()
            .queue();
    }

    private void executeSendButtons(ButtonInteractionEvent event, String selection) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        Channel channel = event.getChannel();

        if (selection.equals("ungrouped")) {
            handleSendSelectRolesButtons(event.getGuild(), channel);
        } else if (selection.startsWith("group_")) {
            int groupId = Integer.parseInt(selection.replace("group_", ""));
            handleSendSelectRolesButtonsForGroup(event.getGuild(), channel, groupId);
        }

        event.editMessage(t(guildId, "selectroles_sent_success"))
            .setEmbeds()
            .setComponents()
            .queue();
    }

    // ==================== LEGACY SEND METHODS ====================

    private void handleSendSelectRolesReaction(Guild guild, Channel channel) {
        if (!channel.getType().isMessage()) {
            return;
        }
        String title = "Select Your Roles";
        String descriptionText = "React with the corresponding emoji to get the role:";
        String color = "#3498db";
        String footer = "Select Roles";
        if (handler.isSelectRoleEmbedExist(guild.getId())) {
            if (handler.getSelectRoleEmbedTitle(guild.getId()) != null) {
                title = handler.getSelectRoleEmbedTitle(guild.getId());
            }
            if (handler.getSelectRolesDescription(guild.getId()) != null) {
                descriptionText = handler.getSelectRolesDescription(guild.getId());
            }
            if (handler.getSelectRolesColor(guild.getId()) != null) {
                color = handler.getSelectRolesColor(guild.getId());
            }
            if (handler.getSelectRolesFooter(guild.getId()) != null) {
                footer = handler.getSelectRolesFooter(guild.getId());
            }
        }
        MessageChannel mChannel = guild.getTextChannelById(channel.getId());
        List<String> roleList = handler.getAllRoleSelectForGuild(Objects.requireNonNull(guild.getId()));
        List<String> emojiList = new ArrayList<>();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(title);
        StringBuilder description = new StringBuilder(descriptionText);
        // replace Color.getColor with robust parsing
        try {
            embedBuilder.setColor(parseColor(color));
        } catch (Exception e) {
            embedBuilder.setColor(Color.BLUE);
        }
        embedBuilder.setDescription(description);
        embedBuilder.setFooter(footer);
        embedBuilder.setTimestamp(java.time.Instant.now());
        for (String roleInfo : roleList) {
            Role role = guild.getRoleById(roleInfo);
            if (role != null) {
                String roleDescription = handler.getRoleSelectDescription(Objects.requireNonNull(guild.getId()), role.getId());
                String roleEmoji = handler.getRoleSelectEmoji(Objects.requireNonNull(guild.getId()), role.getId());
                emojiList.add(roleEmoji);
                embedBuilder.addField(roleEmoji + " " + role.getName(), roleDescription, false);
            }
        }
        assert mChannel != null;
        Message message = mChannel.sendMessageEmbeds(embedBuilder.build()).complete();
        for (String emoji : emojiList) {
            Emoji emj = Emoji.fromFormatted(emoji);
            System.out.println(emj + "; " + emj.getName());
            message.addReaction(Emoji.fromFormatted(emoji)).queue();
        }
    }

    private void handleSendSelectRolesDropdown (Guild guild, Channel channel) {
        if (!channel.getType().isMessage()) {
            return;
        }
        MessageChannel mChannel = guild.getTextChannelById(channel.getId());
        List<String> roleList = handler.getAllRoleSelectForGuild(Objects.requireNonNull(guild.getId()));

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Select Your Roles");
        embedBuilder.setDescription("Use the dropdown menu below to select your roles:");

        StringSelectMenu.Builder menuBuilder =
            StringSelectMenu.create("role_select_dropdown")
                .setPlaceholder("Choose your roles")
                .setMinValues(0)
                .setMaxValues(Math.min(roleList.size(), 25));

        for (String roleInfo : roleList) {
            Role role = guild.getRoleById(roleInfo);
            if (role != null) {
                String roleDescription = handler.getRoleSelectDescription(Objects.requireNonNull(guild.getId()), role.getId());
                String roleEmoji = handler.getRoleSelectEmoji(Objects.requireNonNull(guild.getId()), role.getId());
                menuBuilder.addOption(role.getName(), role.getId(), roleDescription, Emoji.fromFormatted(roleEmoji));
            }
        }

        assert mChannel != null;
        mChannel.sendMessageEmbeds(embedBuilder.build())
            .setComponents(ActionRow.of(menuBuilder.build()));
    }

    private void handleSendSelectRolesButtons (Guild guild, Channel channel) {
        MessageChannel mChannel = guild.getTextChannelById(channel.getId());
        List<String> roleList = handler.getAllRoleSelectForGuild(Objects.requireNonNull(guild.getId()));

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Select Your Roles");
        embedBuilder.setDescription("Click the buttons below to toggle your roles:");

        List<Button> buttons = new ArrayList<>();

        for (String roleInfo : roleList) {
            Role role = guild.getRoleById(roleInfo);
            if (role != null) {
                String roleDescription = handler.getRoleSelectDescription(Objects.requireNonNull(guild.getId()), role.getId());
                String roleEmoji = handler.getRoleSelectEmoji(Objects.requireNonNull(guild.getId()), role.getId());
                buttons.add(Button.primary(
                        "role_select_button_" + handler.getRoleSelectID(Objects.requireNonNull(guild.getId()), role.getId()),
                        role.getName()
                ).withEmoji(Emoji.fromFormatted(roleEmoji)));

                embedBuilder.addField(roleEmoji + " " + role.getName(), roleDescription, false);
            }

            if (buttons.size() == 25) break;
        }

        assert mChannel != null;
        MessageCreateAction message = mChannel.sendMessageEmbeds(embedBuilder.build());
        int size = buttons.size();
        int remainder = buttons.size() % 5;
        for (int i = 0; i < size; i += 5) {
            if (remainder > 0) {
                if (i + 5 > size) {
                    message.addComponents(ActionRow.of(buttons.subList(i, i + remainder)));
                    break;
                }
            }
            message.addComponents(ActionRow.of(buttons.subList(i, i + 5)));
        }
        message.queue();
    }

    private void handleEditSelectRoleEmbed (SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) {
            event.reply("Fehler: Guild nicht gefunden.").setEphemeral(true).queue();
            return;
        }
        String guildId = guild.getId();

        // Bestehende Werte laden
        String currentTitle = handler.getSelectRoleEmbedTitle(guildId);
        String currentDescription = handler.getSelectRolesDescription(guildId);
        String currentFooter = handler.getSelectRolesFooter(guildId);
        String currentColor = handler.getSelectRolesColor(guildId);

        // Neue Werte aus Optionen oder Fallback auf bestehend
        String newTitle = event.getOption("title") != null ? Objects.requireNonNull(event.getOption("title")).getAsString() : currentTitle;
        String newDescription = event.getOption("description") != null ? Objects.requireNonNull(event.getOption("description")).getAsString() : currentDescription;
        String newFooter = event.getOption("footer") != null ? Objects.requireNonNull(event.getOption("footer")).getAsString() : currentFooter;
        String newColor = event.getOption("color") != null ? Objects.requireNonNull(event.getOption("color")).getAsString() : currentColor;

        // Defaults setzen falls weiterhin leer
        if (newTitle == null || newTitle.isBlank()) newTitle = "Select Your Roles";
        if (newDescription == null || newDescription.isBlank()) newDescription = "Choose from the roles below:";
        if (newFooter == null || newFooter.isBlank()) newFooter = "Role Selection";
        if (newColor == null || newColor.isBlank()) newColor = "#3498db";

        // Validierung Farbe
        if (!isValidColor(newColor)) {
            event.getHook().sendMessage("This colour is not allowed. Allowed: #RRGGBB or red/blue/green/yellow/orange/pink/cyan/magenta/white/black/gray.").setEphemeral(true).queue();
            return;
        }

        // Persistieren
        handler.editSelectRoleEmbed(newTitle, newDescription, newFooter, newColor, guildId);

        // Preview Embed
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(newTitle);
        embed.setDescription(newDescription);
        embed.setFooter(newFooter);
        try {
            embed.setColor(parseColor(newColor));
        } catch (Exception ex) {
            embed.setColor(Color.BLUE);
        }
        embed.setTimestamp(java.time.Instant.now());

        event.getHook().sendMessageEmbeds(embed.build())
                .addContent("Select Role Embed aktualisiert. Verwende /send-select-roles zum Versand.")
                .setEphemeral(true)
                .queue();
    }

    // Farb-Hilfsmethoden
    private boolean isValidColor(String c) {
        if (c == null) return false;
        c = c.trim();
        if (c.matches("(?i)^(red|blue|green|yellow|orange|pink|cyan|magenta|white|black|gray|grey)$")) return true;
        return c.matches("^#?[0-9A-Fa-f]{6}$");
    }

    private Color parseColor(String c) {
        c = c.trim();
        if (c.startsWith("#")) return Color.decode(c);
        switch (c.toLowerCase()) {
            case "red": return Color.RED;
            case "blue": return Color.BLUE;
            case "green": return Color.GREEN;
            case "yellow": return Color.YELLOW;
            case "orange": return Color.ORANGE;
            case "pink": return Color.PINK;
            case "cyan": return Color.CYAN;
            case "magenta": return Color.MAGENTA;
            case "white": return Color.WHITE;
            case "black": return Color.BLACK;
            case "gray":
            case "grey": return Color.GRAY;
            default: return Color.BLUE;
        }
    }

    // ==================== GROUP MANAGEMENT HANDLERS ====================

    private void handleGroupCreate(SlashCommandInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();

        // Check if group already exists
        if (handler.getRoleSelectGroupByName(guildId, name) != null) {
            event.getHook().sendMessage(t(guildId, "selectroles_group_exists", name)).setEphemeral(true).queue();
            return;
        }

        int groupId = handler.createRoleSelectGroup(guildId, name);
        if (groupId > 0) {
            event.getHook().sendMessage(t(guildId, "selectroles_group_created", name)).setEphemeral(true).queue();
        } else {
            event.getHook().sendMessage(t(guildId, "selectroles_group_create_failed")).setEphemeral(true).queue();
        }
    }

    private void handleGroupDelete(SlashCommandInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();

        DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroupByName(guildId, name);
        if (group == null) {
            event.getHook().sendMessage(t(guildId, "selectroles_group_not_found", name)).setEphemeral(true).queue();
            return;
        }

        if (handler.deleteRoleSelectGroup(guildId, group.id)) {
            event.getHook().sendMessage(t(guildId, "selectroles_group_deleted", name)).setEphemeral(true).queue();
        } else {
            event.getHook().sendMessage(t(guildId, "selectroles_group_delete_failed")).setEphemeral(true).queue();
        }
    }

    private void handleGroupEdit(SlashCommandInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();

        DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroupByName(guildId, name);
        if (group == null) {
            event.getHook().sendMessage(t(guildId, "selectroles_group_not_found", name)).setEphemeral(true).queue();
            return;
        }

        String newTitle = event.getOption("title") != null ? event.getOption("title").getAsString() : group.title;
        String newDescription = event.getOption("description") != null ? event.getOption("description").getAsString() : group.description;
        String newFooter = event.getOption("footer") != null ? event.getOption("footer").getAsString() : group.footer;
        String newColor = event.getOption("color") != null ? event.getOption("color").getAsString() : group.color;

        if (handler.updateRoleSelectGroup(group.id, guildId, newTitle, newDescription, newFooter, newColor)) {
            EmbedBuilder preview = new EmbedBuilder();
            preview.setTitle(newTitle);
            preview.setDescription(newDescription);
            if (newFooter != null && !newFooter.isEmpty()) {
                preview.setFooter(newFooter);
            }
            try {
                preview.setColor(parseColor(newColor));
            } catch (Exception e) {
                preview.setColor(Color.BLUE);
            }

            event.getHook().sendMessage(t(guildId, "selectroles_group_updated", name))
                    .addEmbeds(preview.build())
                    .setEphemeral(true).queue();
        } else {
            event.getHook().sendMessage(t(guildId, "selectroles_group_update_failed")).setEphemeral(true).queue();
        }
    }

    private void handleGroupList(SlashCommandInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        List<DatabaseHandler.RoleSelectGroupData> groups = handler.getRoleSelectGroups(guildId);

        if (groups.isEmpty()) {
            event.getHook().sendMessage(t(guildId, "selectroles_no_groups")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(t(guildId, "selectroles_group_list_title"));
        embed.setColor(Color.BLUE);

        StringBuilder description = new StringBuilder();
        for (DatabaseHandler.RoleSelectGroupData group : groups) {
            List<String> rolesInGroup = handler.getRolesInGroup(guildId, group.id);
            description.append("**").append(group.position + 1).append(".** ")
                    .append(group.name)
                    .append(" - ").append(rolesInGroup.size()).append(" ")
                    .append(t(guildId, "selectroles_roles_count"))
                    .append("\n");
        }

        // Also show ungrouped roles
        List<String> ungroupedRoles = handler.getUngroupedRoles(guildId);
        if (!ungroupedRoles.isEmpty()) {
            description.append("\n**").append(t(guildId, "selectroles_ungrouped")).append(":** ")
                    .append(ungroupedRoles.size()).append(" ")
                    .append(t(guildId, "selectroles_roles_count"));
        }

        embed.setDescription(description.toString());
        event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleGroupMoveUp(SlashCommandInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();

        DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroupByName(guildId, name);
        if (group == null) {
            event.getHook().sendMessage(t(guildId, "selectroles_group_not_found", name)).setEphemeral(true).queue();
            return;
        }

        if (group.position <= 0) {
            event.getHook().sendMessage(t(guildId, "selectroles_group_already_top")).setEphemeral(true).queue();
            return;
        }

        if (handler.moveGroupUp(guildId, group.id)) {
            event.getHook().sendMessage(t(guildId, "selectroles_group_moved_up", name)).setEphemeral(true).queue();
        } else {
            event.getHook().sendMessage(t(guildId, "selectroles_group_move_failed")).setEphemeral(true).queue();
        }
    }

    private void handleGroupMoveDown(SlashCommandInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();

        DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroupByName(guildId, name);
        if (group == null) {
            event.getHook().sendMessage(t(guildId, "selectroles_group_not_found", name)).setEphemeral(true).queue();
            return;
        }

        if (handler.moveGroupDown(guildId, group.id)) {
            event.getHook().sendMessage(t(guildId, "selectroles_group_moved_down", name)).setEphemeral(true).queue();
        } else {
            event.getHook().sendMessage(t(guildId, "selectroles_group_move_failed")).setEphemeral(true).queue();
        }
    }

    // ==================== GROUP-SPECIFIC SEND HANDLERS ====================

    private void handleSendSelectRolesReactionForGroup(Guild guild, Channel channel, int groupId) {
        if (!channel.getType().isMessage()) {
            System.out.println("Channel is not a message channel.");
            return;
        }

        DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroup(guild.getId(), groupId);
        if (group == null) {
            System.out.println("Group not found.");
            return;
        }

        List<String> roleIds = handler.getRolesInGroup(guild.getId(), groupId);
        System.out.println(roleIds);
        if (roleIds.isEmpty()) {
            return;
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(group.title);

        StringBuilder description = new StringBuilder(group.description != null ? group.description + "\n\n" : "");
        for (String roleId : roleIds) {
            String emoji = handler.getRoleSelectEmoji(guild.getId(), roleId);
            String roleDescription = handler.getRoleSelectDescription(guild.getId(), roleId);
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                description.append(emoji != null ? emoji : "✅").append(" - ").append(role.getAsMention());
                if (roleDescription != null && !roleDescription.isEmpty()) {
                    description.append(": ").append(roleDescription);
                }
                description.append("\n");
            }
        }
        embedBuilder.setDescription(description.toString());

        if (group.footer != null && !group.footer.isEmpty()) {
            embedBuilder.setFooter(group.footer);
        }

        try {
            embedBuilder.setColor(parseColor(group.color));
        } catch (Exception e) {
            embedBuilder.setColor(Color.BLUE);
        }

        TextChannel textChannel = (TextChannel) channel;
        textChannel.sendMessageEmbeds(embedBuilder.build()).queue(msg -> {
            // Speichere Embed in Datenbank mit group_id
            handler.addEmbedToDatabase(guild.getId(), channel.getId(), msg.getId(), groupId,
                "REACTION", group.title, group.description, group.footer, group.color);

            for (String roleId : roleIds) {
                String emoji = handler.getRoleSelectEmoji(guild.getId(), roleId);
                if (emoji != null) {
                    try {
                        msg.addReaction(Emoji.fromFormatted(emoji)).queue();
                    } catch (Exception e) {
                        System.err.println("Error adding reaction: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void handleSendSelectRolesButtonsForGroup(Guild guild, Channel channel, int groupId) {
        if (!channel.getType().isMessage()) {
            return;
        }

        DatabaseHandler.RoleSelectGroupData group = handler.getRoleSelectGroup(guild.getId(), groupId);
        if (group == null) {
            return;
        }

        List<String> roleIds = handler.getRolesInGroup(guild.getId(), groupId);
        if (roleIds.isEmpty()) {
            return;
        }

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle(group.title);
        embedBuilder.setDescription(group.description != null ? group.description : "Select your roles:");

        if (group.footer != null && !group.footer.isEmpty()) {
            embedBuilder.setFooter(group.footer);
        }

        try {
            embedBuilder.setColor(parseColor(group.color));
        } catch (Exception e) {
            embedBuilder.setColor(Color.BLUE);
        }

        List<Button> buttons = new ArrayList<>();
        for (String roleId : roleIds) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                String emoji = handler.getRoleSelectEmoji(guild.getId(), roleId);
                Button button = Button.secondary("select_role_" + roleId, role.getName());
                if (emoji != null && !emoji.isEmpty()) {
                    try {
                        button = button.withEmoji(Emoji.fromFormatted(emoji));
                    } catch (Exception e) {
                        // Ignore invalid emoji
                    }
                }
                buttons.add(button);
            }
        }

        TextChannel textChannel = (TextChannel) channel;

        // Discord allows max 5 buttons per row, max 5 rows
        List<ActionRow> actionRows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += 5) {
            List<Button> rowButtons = buttons.subList(i, Math.min(i + 5, buttons.size()));
            actionRows.add(ActionRow.of(rowButtons));
        }

        textChannel.sendMessageEmbeds(embedBuilder.build())
                .setComponents(actionRows)
                .queue(msg -> {
                    // Speichere Embed in Datenbank mit group_id
                    handler.addEmbedToDatabase(guild.getId(), channel.getId(), msg.getId(), groupId,
                        "BUTTON", group.title, group.description, group.footer, group.color);
                });
    }
}
