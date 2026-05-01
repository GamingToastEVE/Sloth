package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for managing multiple ticket panels with an interactive UI.
 * This allows servers to have multiple ticket systems for different purposes.
 */
public class TicketPanelCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;
    private final Map<String, UserSession> userSessions = new ConcurrentHashMap<>();

    private static class UserSession {
        String guildId;
        int selectedPanelId = -1;
        String pendingAction;
        long lastInteraction = System.currentTimeMillis();

        UserSession(String guildId) {
            this.guildId = guildId;
        }
    }

    public TicketPanelCommandListener(DatabaseHandler handler) {
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

    private UserSession getOrCreateSession(String oderId, String guildId) {
        userSessions.computeIfAbsent(oderId, k -> new UserSession(guildId));
        UserSession session = userSessions.get(oderId);
        session.lastInteraction = System.currentTimeMillis();
        session.guildId = guildId;
        return session;
    }

    // ==================== SLASH COMMAND HANDLER ====================

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("ticket-panels")) {
            return;
        }

        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(t(event.getGuild().getId(), "general.permission_denied")).setEphemeral(true).queue();
            return;
        }

        handler.insertOrUpdateGlobalStatistic("ticket-panels");
        showMainMenu(event);
    }

    // ==================== MAIN MENU UI ====================

    private void showMainMenu(SlashCommandInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎫 " + t(guildId, "ticket_panels.ui_title"));
        embed.setDescription(t(guildId, "ticket_panels.ui_description"));
        embed.setColor(new Color(88, 101, 242));

        List<DatabaseHandler.TicketPanelData> panels = handler.getTicketPanels(guildId);
        embed.addField("📊 " + t(guildId, "ticket_panels.stats"),
            "**" + panels.size() + "** " + t(guildId, "ticket_panels.panels_count"), true);

        embed.setFooter(t(guildId, "ticket_panels.breadcrumb_main"));
        embed.setTimestamp(java.time.Instant.now());

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
            Button.primary("tp_panels", "📋 " + t(guildId, "ticket_panels.btn_manage_panels")),
            Button.success("tp_create", "➕ " + t(guildId, "ticket_panels.btn_create"))
        ));
        rows.add(ActionRow.of(
            Button.danger("tp_close", "❌ " + t(guildId, "general.close"))
        ));

        event.replyEmbeds(embed.build())
            .setComponents(rows)
            .setEphemeral(true)
            .queue();
    }

    private void showMainMenuEdit(ButtonInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎫 " + t(guildId, "ticket_panels.ui_title"));
        embed.setDescription(t(guildId, "ticket_panels.ui_description"));
        embed.setColor(new Color(88, 101, 242));

        List<DatabaseHandler.TicketPanelData> panels = handler.getTicketPanels(guildId);
        embed.addField("📊 " + t(guildId, "ticket_panels.stats"),
            "**" + panels.size() + "** " + t(guildId, "ticket_panels.panels_count"), true);

        embed.setFooter(t(guildId, "ticket_panels.breadcrumb_main"));
        embed.setTimestamp(java.time.Instant.now());

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
            Button.primary("tp_panels", "📋 " + t(guildId, "ticket_panels.btn_manage_panels")),
            Button.success("tp_create", "➕ " + t(guildId, "ticket_panels.btn_create"))
        ));
        rows.add(ActionRow.of(
            Button.danger("tp_close", "❌ " + t(guildId, "general.close"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    // ==================== PANELS LIST UI ====================

    private void showPanelsList(ButtonInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        List<DatabaseHandler.TicketPanelData> panels = handler.getTicketPanels(guildId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📋 " + t(guildId, "ticket_panels.list_title"));
        embed.setColor(new Color(87, 242, 135));
        embed.setFooter(t(guildId, "ticket_panels.breadcrumb_list"));

        if (panels.isEmpty()) {
            embed.setDescription(t(guildId, "ticket_panels.no_panels_hint"));
        } else {
            StringBuilder desc = new StringBuilder();
            for (DatabaseHandler.TicketPanelData panel : panels) {
                desc.append("**").append(panel.position + 1).append(".** ")
                    .append(panel.name)
                    .append("\n  └ ").append(panel.title).append("\n");
            }
            embed.setDescription(desc.toString());
        }

        List<ActionRow> rows = new ArrayList<>();

        if (!panels.isEmpty()) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("tp_select_panel")
                .setPlaceholder(t(guildId, "ticket_panels.select_panel_placeholder"))
                .setMinValues(1)
                .setMaxValues(1);

            for (DatabaseHandler.TicketPanelData panel : panels) {
                menuBuilder.addOption(panel.name, String.valueOf(panel.id), panel.title);
            }
            rows.add(ActionRow.of(menuBuilder.build()));
        }

        rows.add(ActionRow.of(
            Button.success("tp_create", "➕ " + t(guildId, "ticket_panels.btn_create")),
            Button.secondary("tp_back_main", "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    // ==================== PANEL DETAILS UI ====================

    private void showPanelDetails(ButtonInteractionEvent event, int panelId) {
        showPanelDetailsInternal(event, panelId, true);
    }

    private void showPanelDetailsFromSelect(StringSelectInteractionEvent event, int panelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        UserSession session = getOrCreateSession(event.getUser().getId(), guildId);
        session.selectedPanelId = panelId;

        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
        if (panel == null) {
            event.reply(t(guildId, "ticket_panels.not_found")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = buildPanelDetailsEmbed(event.getGuild(), panel, guildId);
        List<ActionRow> rows = buildPanelDetailsButtons(panelId, guildId);

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showPanelDetailsInternal(ButtonInteractionEvent event, int panelId, boolean edit) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        UserSession session = getOrCreateSession(event.getUser().getId(), guildId);
        session.selectedPanelId = panelId;

        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
        if (panel == null) {
            event.reply(t(guildId, "ticket_panels.not_found")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = buildPanelDetailsEmbed(event.getGuild(), panel, guildId);
        List<ActionRow> rows = buildPanelDetailsButtons(panelId, guildId);

        if (edit) {
            event.editMessageEmbeds(embed.build())
                .setComponents(rows)
                .queue();
        } else {
            event.replyEmbeds(embed.build())
                .setComponents(rows)
                .setEphemeral(true)
                .queue();
        }
    }

    private EmbedBuilder buildPanelDetailsEmbed(Guild guild, DatabaseHandler.TicketPanelData panel, String guildId) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🎫 " + panel.name);
        embed.setColor(parseColor(panel.embedColor));
        embed.setFooter(t(guildId, "ticket_panels.breadcrumb_detail", panel.name));

        embed.addField("📝 " + t(guildId, "ticket_panels.field_title"), panel.title, false);
        embed.addField("📄 " + t(guildId, "ticket_panels.field_description"),
            panel.description.length() > 200 ? panel.description.substring(0, 200) + "..." : panel.description, false);
        embed.addField("🔘 " + t(guildId, "ticket_panels.field_button"), panel.buttonLabel, true);
        embed.addField("🎨 " + t(guildId, "ticket_panels.field_color"), panel.embedColor, true);
        embed.addField("📍 " + t(guildId, "ticket_panels.field_position"), String.valueOf(panel.position + 1), true);

        // Channel info
        StringBuilder channelInfo = new StringBuilder();
        if (panel.categoryId != null) {
            Category cat = guild.getCategoryById(panel.categoryId);
            channelInfo.append("**Category:** ").append(cat != null ? cat.getName() : "Not found").append("\n");
        }
        if (panel.channelId != null) {
            TextChannel ch = guild.getTextChannelById(panel.channelId);
            channelInfo.append("**Channel:** ").append(ch != null ? ch.getAsMention() : "Not found").append("\n");
        }
        if (panel.supportRoleId != null) {
            Role role = guild.getRoleById(panel.supportRoleId);
            channelInfo.append("**Support Role:** ").append(role != null ? role.getAsMention() : "Not found");
        }
        if (!channelInfo.isEmpty()) {
            embed.addField("🔧 " + t(guildId, "ticket_panels.field_setup"), channelInfo.toString(), false);
        } else {
            embed.addField("⚠️ " + t(guildId, "ticket_panels.field_setup"),
                t(guildId, "ticket_panels.not_configured"), false);
        }

        embed.addField("📊 " + t(guildId, "ticket_panels.field_settings"),
            "Max tickets per user: " + panel.maxTicketsPerUser + "\n" +
            "Require subject: " + (panel.requireSubject ? "✅" : "❌") + "\n" +
            "Require description: " + (panel.requireDescription ? "✅" : "❌"), true);

        return embed;
    }

    private List<ActionRow> buildPanelDetailsButtons(int panelId, String guildId) {
        List<ActionRow> rows = new ArrayList<>();

        rows.add(ActionRow.of(
            Button.primary("tp_edit_content_" + panelId, "✏️ " + t(guildId, "ticket_panels.btn_edit_content")),
            Button.primary("tp_edit_channels_" + panelId, "🔧 " + t(guildId, "ticket_panels.btn_edit_channels")),
            Button.primary("tp_edit_appearance_" + panelId, "🎨 " + t(guildId, "ticket_panels.btn_edit_appearance"))
        ));

        rows.add(ActionRow.of(
            Button.primary("tp_edit_settings_" + panelId, "⚙️ " + t(guildId, "ticket_panels.btn_edit_settings")),
            Button.primary("tp_categories_" + panelId, "📂 " + t(guildId, "ticket_panels.btn_categories")),
            Button.primary("tp_panel_forms_" + panelId, "📋 " + t(guildId, "ticket_panels.btn_forms"))
        ));

        rows.add(ActionRow.of(
            Button.success("tp_send_" + panelId, "📤 " + t(guildId, "ticket_panels.btn_send_panel")),
            Button.secondary("tp_up_" + panelId, "⬆️"),
            Button.secondary("tp_down_" + panelId, "⬇️"),
            Button.danger("tp_delete_" + panelId, "🗑️ " + t(guildId, "general.delete"))
        ));

        rows.add(ActionRow.of(
            Button.secondary("tp_back_panels", "⬅️ " + t(guildId, "general.back")),
            Button.danger("tp_close", "❌ " + t(guildId, "general.close"))
        ));

        return rows;
    }

    // ==================== BUTTON INTERACTION HANDLER ====================

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String customId = event.getComponentId();

        if (!customId.startsWith("tp_")) {
            return;
        }

        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        // Main menu buttons
        if (customId.equals("tp_close")) {
            event.deferEdit().queue();
            event.getHook().deleteOriginal().queue(
                success -> {},
                error -> event.getMessage().delete().queue()
            );
            return;
        }

        if (customId.equals("tp_panels")) {
            showPanelsList(event);
            return;
        }

        if (customId.equals("tp_create")) {
            showCreatePanelModal(event);
            return;
        }

        if (customId.equals("tp_back_main")) {
            showMainMenuEdit(event);
            return;
        }

        if (customId.equals("tp_back_panels")) {
            showPanelsList(event);
            return;
        }

        // Panel-specific buttons with ID
        if (customId.startsWith("tp_edit_content_")) {
            int panelId = Integer.parseInt(customId.replace("tp_edit_content_", ""));
            showEditContentModal(event, panelId);
            return;
        }

        if (customId.startsWith("tp_edit_channels_")) {
            int panelId = Integer.parseInt(customId.replace("tp_edit_channels_", ""));
            showEditChannelsMenu(event, panelId);
            return;
        }

        if (customId.startsWith("tp_edit_appearance_")) {
            int panelId = Integer.parseInt(customId.replace("tp_edit_appearance_", ""));
            showEditAppearanceModal(event, panelId);
            return;
        }

        if (customId.startsWith("tp_edit_settings_")) {
            int panelId = Integer.parseInt(customId.replace("tp_edit_settings_", ""));
            showEditSettingsModal(event, panelId);
            return;
        }

        if (customId.startsWith("tp_panel_forms_")) {
            int panelId = Integer.parseInt(customId.replace("tp_panel_forms_", ""));
            showPanelFormsList(event, panelId);
            return;
        }

        if (customId.startsWith("tp_send_")) {
            int panelId = Integer.parseInt(customId.replace("tp_send_", ""));
            sendTicketPanel(event, panelId);
            return;
        }

        if (customId.startsWith("tp_delete_")) {
            int panelId = Integer.parseInt(customId.replace("tp_delete_", ""));
            showDeleteConfirmation(event, panelId);
            return;
        }

        if (customId.startsWith("tp_confirm_delete_")) {
            int panelId = Integer.parseInt(customId.replace("tp_confirm_delete_", ""));
            deletePanel(event, panelId);
            return;
        }

        if (customId.startsWith("tp_up_")) {
            int panelId = Integer.parseInt(customId.replace("tp_up_", ""));
            handler.moveTicketPanelUp(guildId, panelId);
            showPanelDetails(event, panelId);
            return;
        }

        if (customId.startsWith("tp_down_")) {
            int panelId = Integer.parseInt(customId.replace("tp_down_", ""));
            handler.moveTicketPanelDown(guildId, panelId);
            showPanelDetails(event, panelId);
            return;
        }

        if (customId.startsWith("tp_back_detail_")) {
            int panelId = Integer.parseInt(customId.replace("tp_back_detail_", ""));
            showPanelDetails(event, panelId);
            return;
        }

        // Categories management
        if (customId.startsWith("tp_categories_")) {
            int panelId = Integer.parseInt(customId.replace("tp_categories_", ""));
            showCategoriesList(event, panelId);
            return;
        }

        if (customId.startsWith("tp_cat_create_")) {
            int panelId = Integer.parseInt(customId.replace("tp_cat_create_", ""));
            showCreateCategoryModal(event, panelId);
            return;
        }

        if (customId.startsWith("tp_cat_edit_")) {
            int categoryId = Integer.parseInt(customId.replace("tp_cat_edit_", ""));
            showEditCategoryModal(event, categoryId);
            return;
        }

        if (customId.startsWith("tp_cat_delete_")) {
            int categoryId = Integer.parseInt(customId.replace("tp_cat_delete_", ""));
            handler.deleteTicketCategory(categoryId);
            DatabaseHandler.TicketCategoryData cat = handler.getTicketCategory(categoryId);
            // Refresh categories list - get panel ID from session
            UserSession session = getOrCreateSession(event.getUser().getId(), guildId);
            showCategoriesList(event, session.selectedPanelId);
            return;
        }

        if (customId.startsWith("tp_cat_up_")) {
            int categoryId = Integer.parseInt(customId.replace("tp_cat_up_", ""));
            handler.moveTicketCategoryUp(categoryId);
            UserSession session = getOrCreateSession(event.getUser().getId(), guildId);
            showCategoryDetails(event, categoryId);
            return;
        }

        if (customId.startsWith("tp_cat_down_")) {
            int categoryId = Integer.parseInt(customId.replace("tp_cat_down_", ""));
            handler.moveTicketCategoryDown(categoryId);
            showCategoryDetails(event, categoryId);
            return;
        }

        if (customId.startsWith("tp_cat_forms_")) {
            int categoryId = Integer.parseInt(customId.replace("tp_cat_forms_", ""));
            showFormsList(event, categoryId);
            return;
        }

        if (customId.startsWith("tp_cat_back_")) {
            if (customId.startsWith("tp_cat_back_detail_")) {
                int categoryId = Integer.parseInt(customId.replace("tp_cat_back_detail_", ""));
                showCategoryDetails(event, categoryId);
                return;
            }
            int panelId = Integer.parseInt(customId.replace("tp_cat_back_", ""));
            showCategoriesList(event, panelId);
            return;
        }

        // Forms management
        if (customId.startsWith("tp_form_create_")) {
            int categoryId = Integer.parseInt(customId.replace("tp_form_create_", ""));
            showCreateFormModal(event, categoryId);
            return;
        }

        if (customId.startsWith("tp_form_edit_")) {
            int formId = Integer.parseInt(customId.replace("tp_form_edit_", ""));
            showEditFormModal(event, formId);
            return;
        }

        if (customId.startsWith("tp_form_delete_")) {
            int formId = Integer.parseInt(customId.replace("tp_form_delete_", ""));
            showDeleteFormConfirmation(event, formId);
            return;
        }

        if (customId.startsWith("tp_form_confirm_delete_")) {
            int formId = Integer.parseInt(customId.replace("tp_form_confirm_delete_", ""));
            DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);
            handler.deleteTicketForm(formId);
            if (form != null) {
                showFormsList(event, form.categoryId);
            }
            return;
        }

        if (customId.startsWith("tp_form_cancel_delete_")) {
            int formId = Integer.parseInt(customId.replace("tp_form_cancel_delete_", ""));
            DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);
            if (form != null) {
                showFormDetails(event, formId);
            }
            return;
        }

        if (customId.startsWith("tp_form_fields_")) {
            int formId = Integer.parseInt(customId.replace("tp_form_fields_", ""));
            showFormFieldsListForForm(event, formId);
            return;
        }

        if (customId.startsWith("tp_form_back_")) {
            int categoryId = Integer.parseInt(customId.replace("tp_form_back_", ""));
            showFormsList(event, categoryId);
            return;
        }

        // Form fields management
        if (customId.startsWith("tp_field_create_")) {
            String[] parts = customId.replace("tp_field_create_", "").split("_");
            if (parts.length == 2) {
                int formId = Integer.parseInt(parts[0]);
                showCreateFormFieldModalForForm(event, formId);
            } else {
                int categoryId = Integer.parseInt(parts[0]);
                showCreateFormFieldModal(event, categoryId);
            }
            return;
        }

        if (customId.startsWith("tp_field_edit_")) {
            int fieldId = Integer.parseInt(customId.replace("tp_field_edit_", ""));
            showEditFormFieldModal(event, fieldId);
            return;
        }

        if (customId.startsWith("tp_field_delete_")) {
            int fieldId = Integer.parseInt(customId.replace("tp_field_delete_", ""));
            showDeleteFieldConfirmation(event, fieldId);
            return;
        }

        if (customId.startsWith("tp_field_confirm_delete_")) {
            int fieldId = Integer.parseInt(customId.replace("tp_field_confirm_delete_", ""));
            DatabaseHandler.TicketFormFieldData field = handler.getTicketFormField(fieldId);
            handler.deleteTicketFormField(fieldId);
            if (field != null) {
                if (field.formId > 0) {
                    showFormFieldsListForForm(event, field.formId);
                } else {
                    showFormFieldsList(event, field.categoryId);
                }
            }
            return;
        }

        if (customId.startsWith("tp_field_cancel_delete_")) {
            int fieldId = Integer.parseInt(customId.replace("tp_field_cancel_delete_", ""));
            DatabaseHandler.TicketFormFieldData field = handler.getTicketFormField(fieldId);
            if (field != null) {
                if (field.formId > 0) {
                    showFormFieldDetailsForFormFromButton(event, fieldId, field.formId);
                } else {
                    showFormFieldDetailsFromButton(event, fieldId, field.categoryId);
                }
            }
            return;
        }

        if (customId.startsWith("tp_field_back_")) {
            int categoryId = Integer.parseInt(customId.replace("tp_field_back_", ""));
            showCategoryDetails(event, categoryId);
            return;
        }

        // Form field preview
        if (customId.startsWith("tp_field_preview_")) {
            int categoryId = Integer.parseInt(customId.replace("tp_field_preview_", ""));
            showFormPreviewModal(event, categoryId);
            return;
        }

        if (customId.startsWith("tp_form_preview_fields_")) {
            int formId = Integer.parseInt(customId.replace("tp_form_preview_fields_", ""));
            showFormPreviewModalForForm(event, formId);
            return;
        }

        if (customId.startsWith("tp_form_detail_back_")) {
            int formId = Integer.parseInt(customId.replace("tp_form_detail_back_", ""));
            showFormDetails(event, formId);
            return;
        }

        if (customId.startsWith("tp_cat_back_detail_")) {
            int categoryId = Integer.parseInt(customId.replace("tp_cat_back_detail_", ""));
            showCategoryDetails(event, categoryId);
            return;
        }

        // Channel selection confirmation buttons
        if (customId.startsWith("tp_save_channels_")) {
            int panelId = Integer.parseInt(customId.replace("tp_save_channels_", ""));
            showPanelDetails(event, panelId);
            return;
        }
    }

    // ==================== STRING SELECT HANDLER ====================

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String customId = event.getComponentId();

        if (!customId.startsWith("tp_")) {
            return;
        }

        if (customId.equals("tp_select_panel")) {
            int panelId = Integer.parseInt(event.getValues().get(0));
            showPanelDetailsFromSelect(event, panelId);
        } else if (customId.startsWith("tp_select_category_")) {
            int panelId = Integer.parseInt(customId.replace("tp_select_category_", ""));
            int categoryId = Integer.parseInt(event.getValues().get(0));
            showCategoryDetailsFromSelect(event, categoryId, panelId);
        } else if (customId.startsWith("tp_select_form_")) {
            int categoryId = Integer.parseInt(customId.replace("tp_select_form_", ""));
            int formId = Integer.parseInt(event.getValues().get(0));
            showFormDetailsFromSelect(event, formId, categoryId);
        } else if (customId.startsWith("tp_select_formfield_")) {
            int formId = Integer.parseInt(customId.replace("tp_select_formfield_", ""));
            int fieldId = Integer.parseInt(event.getValues().get(0));
            showFormFieldDetailsForFormFromSelect(event, fieldId, formId);
        } else if (customId.startsWith("tp_select_field_")) {
            int categoryId = Integer.parseInt(customId.replace("tp_select_field_", ""));
            int fieldId = Integer.parseInt(event.getValues().get(0));
            showFormFieldDetailsFromSelect(event, fieldId, categoryId);
        } else if (customId.startsWith("tp_field_move_")) {
            // Format: tp_field_move_{fieldId}_{categoryId or formId}
            String[] parts = customId.replace("tp_field_move_", "").split("_");
            int fieldId = Integer.parseInt(parts[0]);
            int parentId = Integer.parseInt(parts[1]);
            int newPosition = Integer.parseInt(event.getValues().get(0));
            handleFieldPositionChange(event, fieldId, parentId, newPosition);
        } else if (customId.startsWith("tp_form_move_")) {
            // Format: tp_form_move_{formId}_{categoryId}
            String[] parts = customId.replace("tp_form_move_", "").split("_");
            int formId = Integer.parseInt(parts[0]);
            int categoryId = Integer.parseInt(parts[1]);
            int newPosition = Integer.parseInt(event.getValues().get(0));
            handleFormPositionChange(event, formId, categoryId, newPosition);
        } else if (customId.startsWith("tp_select_cat_forms_")) {
            int panelId = Integer.parseInt(customId.replace("tp_select_cat_forms_", ""));
            int categoryId = Integer.parseInt(event.getValues().get(0));
            showFormsListFromPanelSelect(event, categoryId, panelId);
        }
    }

    // ==================== ENTITY SELECT HANDLER ====================

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String customId = event.getComponentId();

        if (!customId.startsWith("tp_")) {
            return;
        }

        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        UserSession session = getOrCreateSession(event.getUser().getId(), guildId);

        if (customId.startsWith("tp_select_category_")) {
            int panelId = Integer.parseInt(customId.replace("tp_select_category_", ""));
            if (!event.getMentions().getChannels().isEmpty()) {
                Channel channel = event.getMentions().getChannels().get(0);
                if (channel instanceof Category) {
                    handler.updateTicketPanelChannels(panelId, channel.getId(), null, null, null);
                    event.reply(t(guildId, "ticket_panels.category_set")).setEphemeral(true).queue();
                }
            }
            return;
        }

        if (customId.startsWith("tp_select_channel_")) {
            int panelId = Integer.parseInt(customId.replace("tp_select_channel_", ""));
            if (!event.getMentions().getChannels().isEmpty()) {
                Channel channel = event.getMentions().getChannels().get(0);
                DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
                if (panel != null) {
                    handler.updateTicketPanelChannels(panelId, panel.categoryId, channel.getId(),
                        panel.supportRoleId, panel.pingRoleId);
                    event.reply(t(guildId, "ticket_panels.channel_set")).setEphemeral(true).queue();
                }
            }
            return;
        }

        if (customId.startsWith("tp_select_support_role_")) {
            int panelId = Integer.parseInt(customId.replace("tp_select_support_role_", ""));
            if (!event.getMentions().getRoles().isEmpty()) {
                Role role = event.getMentions().getRoles().get(0);
                DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
                if (panel != null) {
                    handler.updateTicketPanelChannels(panelId, panel.categoryId, panel.channelId,
                        role.getId(), panel.pingRoleId);
                    event.reply(t(guildId, "ticket_panels.support_role_set")).setEphemeral(true).queue();
                }
            }
            return;
        }

        if (customId.startsWith("tp_select_ping_role_")) {
            int panelId = Integer.parseInt(customId.replace("tp_select_ping_role_", ""));
            if (!event.getMentions().getRoles().isEmpty()) {
                Role role = event.getMentions().getRoles().get(0);
                DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
                if (panel != null) {
                    handler.updateTicketPanelChannels(panelId, panel.categoryId, panel.channelId,
                        panel.supportRoleId, role.getId());
                    event.reply(t(guildId, "ticket_panels.ping_role_set")).setEphemeral(true).queue();
                }
            }
            return;
        }
    }

    // ==================== MODAL HANDLERS ====================

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();

        if (!modalId.startsWith("tp_")) {
            return;
        }

        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        if (modalId.equals("tp_create_panel")) {
            handleCreatePanelModal(event, guildId);
            return;
        }

        if (modalId.startsWith("tp_edit_content_modal_")) {
            int panelId = Integer.parseInt(modalId.replace("tp_edit_content_modal_", ""));
            handleEditContentModal(event, panelId, guildId);
            return;
        }

        if (modalId.startsWith("tp_edit_appearance_modal_")) {
            int panelId = Integer.parseInt(modalId.replace("tp_edit_appearance_modal_", ""));
            handleEditAppearanceModal(event, panelId, guildId);
            return;
        }

        if (modalId.startsWith("tp_edit_settings_modal_")) {
            int panelId = Integer.parseInt(modalId.replace("tp_edit_settings_modal_", ""));
            handleEditSettingsModal(event, panelId, guildId);
            return;
        }

        // Category modals
        if (modalId.startsWith("tp_create_category_modal_")) {
            int panelId = Integer.parseInt(modalId.replace("tp_create_category_modal_", ""));
            handleCreateCategoryModal(event, panelId, guildId);
            return;
        }

        if (modalId.startsWith("tp_edit_category_modal_")) {
            int categoryId = Integer.parseInt(modalId.replace("tp_edit_category_modal_", ""));
            handleEditCategoryModal(event, categoryId, guildId);
            return;
        }

        // Form modals
        if (modalId.startsWith("tp_create_form_modal_")) {
            int categoryId = Integer.parseInt(modalId.replace("tp_create_form_modal_", ""));
            handleCreateFormModal(event, categoryId, guildId);
            return;
        }

        if (modalId.startsWith("tp_edit_form_modal_")) {
            int formId = Integer.parseInt(modalId.replace("tp_edit_form_modal_", ""));
            handleEditFormModalResponse(event, formId, guildId);
            return;
        }

        // Form field modals
        if (modalId.startsWith("tp_create_formfield_modal_")) {
            int formId = Integer.parseInt(modalId.replace("tp_create_formfield_modal_", ""));
            handleCreateFormFieldModalForForm(event, formId, guildId);
            return;
        }

        if (modalId.startsWith("tp_create_field_modal_")) {
            int categoryId = Integer.parseInt(modalId.replace("tp_create_field_modal_", ""));
            handleCreateFormFieldModal(event, categoryId, guildId);
            return;
        }

        if (modalId.startsWith("tp_edit_field_modal_")) {
            int fieldId = Integer.parseInt(modalId.replace("tp_edit_field_modal_", ""));
            handleEditFormFieldModal(event, fieldId, guildId);
            return;
        }

        // Form preview modal - just acknowledge it
        if (modalId.startsWith("tp_form_preview_")) {
            event.reply(t(guildId, "ticket_panels.preview_complete")).setEphemeral(true).queue();
            return;
        }
    }

    // ==================== MODAL CREATORS ====================

    private void showCreatePanelModal(ButtonInteractionEvent event) {
        TextInput nameInput = TextInput.create("name", TextInputStyle.SHORT)
            .setPlaceholder("e.g., Support, Applications, Reports...")
            .setRequiredRange(1, 100)
            .build();

        Modal modal = Modal.create("tp_create_panel", "Create New Ticket Panel")
            .addComponents(Label.of("Panel Name", nameInput))
            .build();

        event.replyModal(modal).queue();
    }

    private void showEditContentModal(ButtonInteractionEvent event, int panelId) {
        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
        if (panel == null) return;

        TextInput nameInput = TextInput.create("name", TextInputStyle.SHORT)
            .setValue(panel.name)
            .setRequiredRange(1, 100)
            .build();

        TextInput titleInput = TextInput.create("title", TextInputStyle.SHORT)
            .setValue(panel.title)
            .setRequiredRange(1, 256)
            .build();

        TextInput descInput = TextInput.create("description", TextInputStyle.PARAGRAPH)
            .setValue(panel.description)
            .setRequiredRange(1, 2000)
            .build();

        TextInput buttonInput = TextInput.create("button_label", TextInputStyle.SHORT)
            .setValue(panel.buttonLabel)
            .setRequiredRange(1, 80)
            .build();

        Modal modal = Modal.create("tp_edit_content_modal_" + panelId, "Edit Panel Content")
            .addComponents(
                Label.of("Panel Name", nameInput),
                Label.of("Embed Title", titleInput),
                Label.of("Embed Description", descInput),
                Label.of("Button Label", buttonInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void showEditAppearanceModal(ButtonInteractionEvent event, int panelId) {
        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
        if (panel == null) return;

        TextInput colorInput = TextInput.create("color", TextInputStyle.SHORT)
            .setValue(panel.embedColor)
            .setPlaceholder("#5865F2")
            .setRequiredRange(1, 10)
            .build();

        TextInput footerInput = TextInput.create("footer", TextInputStyle.SHORT)
            .setValue(panel.embedFooter != null ? panel.embedFooter : "Sloth Bot")
            .setPlaceholder("Optional footer text")
            .setRequired(false)
            .build();

        TextInput welcomeInput = TextInput.create("welcome", TextInputStyle.PARAGRAPH)
            .setValue(panel.welcomeMessage)
            .setPlaceholder("Message shown when ticket is created")
            .setRequiredRange(1, 1000)
            .build();

        TextInput emojiInput = TextInput.create("emoji", TextInputStyle.SHORT)
            .setValue(panel.buttonEmoji != null ? panel.buttonEmoji : "No emoji selected")
            .setPlaceholder("📩 or custom emoji")
            .setRequired(false)
            .build();

        Modal modal = Modal.create("tp_edit_appearance_modal_" + panelId, "Edit Panel Appearance")
            .addComponents(
                Label.of("Embed Color (hex)", colorInput),
                Label.of("Embed Footer", footerInput),
                Label.of("Welcome Message", welcomeInput),
                Label.of("Button Emoji", emojiInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void showEditSettingsModal(ButtonInteractionEvent event, int panelId) {
        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
        if (panel == null) return;

        TextInput maxTicketsInput = TextInput.create("max_tickets", TextInputStyle.SHORT)
            .setValue(String.valueOf(panel.maxTicketsPerUser))
            .setPlaceholder("1-10")
            .setRequiredRange(1, 2)
            .build();

        TextInput requireSubjectInput = TextInput.create("require_subject", TextInputStyle.SHORT)
            .setValue(panel.requireSubject ? "yes" : "no")
            .setPlaceholder("yes or no")
            .setRequiredRange(2, 3)
            .build();

        TextInput requireDescInput = TextInput.create("require_description", TextInputStyle.SHORT)
            .setValue(panel.requireDescription ? "yes" : "no")
            .setPlaceholder("yes or no")
            .setRequiredRange(2, 3)
            .build();

        Modal modal = Modal.create("tp_edit_settings_modal_" + panelId, "Edit Panel Settings")
            .addComponents(
                Label.of("Max Tickets per User (1-10)", maxTicketsInput),
                Label.of("Require Subject (yes/no)", requireSubjectInput),
                Label.of("Require Description (yes/no)", requireDescInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    // ==================== MODAL RESPONSE HANDLERS ====================

    private void handleCreatePanelModal(ModalInteractionEvent event, String guildId) {
        String name = Objects.requireNonNull(event.getValue("name")).getAsString();

        // Check if name already exists
        if (handler.getTicketPanelByName(guildId, name) != null) {
            event.reply(t(guildId, "ticket_panels.name_exists")).setEphemeral(true).queue();
            return;
        }

        int panelId = handler.createTicketPanel(guildId, name);
        if (panelId > 0) {
            event.reply(t(guildId, "ticket_panels.created", name)).setEphemeral(true).queue();
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    private void handleEditContentModal(ModalInteractionEvent event, int panelId, String guildId) {
        String name = Objects.requireNonNull(event.getValue("name")).getAsString();
        String title = Objects.requireNonNull(event.getValue("title")).getAsString();
        String description = Objects.requireNonNull(event.getValue("description")).getAsString();
        String buttonLabel = Objects.requireNonNull(event.getValue("button_label")).getAsString();

        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
        if (panel == null) {
            event.reply(t(guildId, "ticket_panels.not_found")).setEphemeral(true).queue();
            return;
        }

        boolean success = handler.updateTicketPanel(panelId, name, title, description,
            buttonLabel, panel.buttonEmoji, panel.buttonColor);

        if (success) {
            event.reply(t(guildId, "ticket_panels.updated")).setEphemeral(true).queue();
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    private void handleEditAppearanceModal(ModalInteractionEvent event, int panelId, String guildId) {
        String color = Objects.requireNonNull(event.getValue("color")).getAsString();
        var footerValue = event.getValue("footer");
        String footer = footerValue != null ? footerValue.getAsString() : null;
        String welcome = Objects.requireNonNull(event.getValue("welcome")).getAsString();
        var emojiValue = event.getValue("emoji");
        String emoji = emojiValue != null ? emojiValue.getAsString() : null;

        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
        if (panel == null) {
            event.reply(t(guildId, "ticket_panels.not_found")).setEphemeral(true).queue();
            return;
        }

        // Update button emoji in panel
        handler.updateTicketPanel(panelId, panel.name, panel.title, panel.description,
            panel.buttonLabel, emoji != null && !emoji.isBlank() ? emoji : null, panel.buttonColor);

        boolean success = handler.updateTicketPanelAppearance(panelId, color,
            footer != null && !footer.isBlank() ? footer : null, panel.embedThumbnail, welcome);

        if (success) {
            event.reply(t(guildId, "ticket_panels.updated")).setEphemeral(true).queue();
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    private void handleEditSettingsModal(ModalInteractionEvent event, int panelId, String guildId) {
        String maxTicketsStr = Objects.requireNonNull(event.getValue("max_tickets")).getAsString();
        String requireSubjectStr = Objects.requireNonNull(event.getValue("require_subject")).getAsString().toLowerCase();
        String requireDescStr = Objects.requireNonNull(event.getValue("require_description")).getAsString().toLowerCase();

        int maxTickets;
        try {
            maxTickets = Integer.parseInt(maxTicketsStr);
            if (maxTickets < 1) maxTickets = 1;
            if (maxTickets > 10) maxTickets = 10;
        } catch (NumberFormatException e) {
            maxTickets = 1;
        }

        boolean requireSubject = requireSubjectStr.equals("yes") || requireSubjectStr.equals("ja");
        boolean requireDesc = requireDescStr.equals("yes") || requireDescStr.equals("ja");

        boolean success = handler.updateTicketPanelSettings(panelId, maxTickets, requireSubject, requireDesc);

        if (success) {
            event.reply(t(guildId, "ticket_panels.updated")).setEphemeral(true).queue();
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    // ==================== CHANNEL EDIT MENU ====================

    private void showEditChannelsMenu(ButtonInteractionEvent event, int panelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);

        if (panel == null) {
            event.reply(t(guildId, "ticket_panels.not_found")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🔧 " + t(guildId, "ticket_panels.edit_channels_title"));
        embed.setDescription(t(guildId, "ticket_panels.edit_channels_desc"));
        embed.setColor(parseColor(panel.embedColor));
        embed.setFooter(t(guildId, "ticket_panels.breadcrumb_channels", panel.name));

        // Show current settings
        StringBuilder current = new StringBuilder();
        if (panel.categoryId != null) {
            Category cat = event.getGuild().getCategoryById(panel.categoryId);
            current.append("**Category:** ").append(cat != null ? cat.getName() : "Not found").append("\n");
        } else {
            current.append("**Category:** Not set\n");
        }
        if (panel.channelId != null) {
            TextChannel ch = event.getGuild().getTextChannelById(panel.channelId);
            current.append("**Panel Channel:** ").append(ch != null ? ch.getAsMention() : "Not found").append("\n");
        } else {
            current.append("**Panel Channel:** Not set\n");
        }
        if (panel.supportRoleId != null) {
            Role role = event.getGuild().getRoleById(panel.supportRoleId);
            current.append("**Support Role:** ").append(role != null ? role.getAsMention() : "Not found").append("\n");
        } else {
            current.append("**Support Role:** Not set\n");
        }
        if (panel.pingRoleId != null) {
            Role role = event.getGuild().getRoleById(panel.pingRoleId);
            current.append("**Ping Role:** ").append(role != null ? role.getAsMention() : "Not found");
        } else {
            current.append("**Ping Role:** Not set");
        }

        embed.addField(t(guildId, "ticket_panels.current_settings"), current.toString(), false);

        List<ActionRow> rows = new ArrayList<>();

        // Category selector
        rows.add(ActionRow.of(
            EntitySelectMenu.create("tp_select_category_" + panelId, EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.CATEGORY)
                .setPlaceholder(t(guildId, "ticket_panels.select_category"))
                .setMinValues(1)
                .setMaxValues(1)
                .build()
        ));

        // Channel selector
        rows.add(ActionRow.of(
            EntitySelectMenu.create("tp_select_channel_" + panelId, EntitySelectMenu.SelectTarget.CHANNEL)
                .setChannelTypes(ChannelType.TEXT)
                .setPlaceholder(t(guildId, "ticket_panels.select_channel"))
                .setMinValues(1)
                .setMaxValues(1)
                .build()
        ));

        // Support role selector
        rows.add(ActionRow.of(
            EntitySelectMenu.create("tp_select_support_role_" + panelId, EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder(t(guildId, "ticket_panels.select_support_role"))
                .setMinValues(1)
                .setMaxValues(1)
                .build()
        ));

        // Ping role selector
        rows.add(ActionRow.of(
            EntitySelectMenu.create("tp_select_ping_role_" + panelId, EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder(t(guildId, "ticket_panels.select_ping_role"))
                .setMinValues(1)
                .setMaxValues(1)
                .build()
        ));

        rows.add(ActionRow.of(
            Button.secondary("tp_back_detail_" + panelId, "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    // ==================== SEND PANEL ====================

    private void sendTicketPanel(ButtonInteractionEvent event, int panelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);

        if (panel == null) {
            event.reply(t(guildId, "ticket_panels.not_found")).setEphemeral(true).queue();
            return;
        }

        if (panel.categoryId == null) {
            event.reply(t(guildId, "ticket_panels.category_not_set")).setEphemeral(true).queue();
            return;
        }

        // Get the channel to send to (either the configured channel or current channel)
        TextChannel targetChannel;
        if (panel.channelId != null) {
            targetChannel = event.getGuild().getTextChannelById(panel.channelId);
            if (targetChannel == null) {
                event.reply(t(guildId, "ticket_panels.channel_not_found")).setEphemeral(true).queue();
                return;
            }
        } else {
            targetChannel = event.getChannel().asTextChannel();
        }

        // Build the embed
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(panel.title);
        embed.setDescription(handler.processLinebreaks(panel.description));
        embed.setColor(parseColor(panel.embedColor));
        if (panel.embedFooter != null && !panel.embedFooter.isBlank()) {
            embed.setFooter(panel.embedFooter);
        } else {
            embed.setFooter("Ticket System • " + panel.name);
        }

        // Check if panel has categories
        List<DatabaseHandler.TicketCategoryData> categories = handler.getTicketCategories(panelId);
        List<ActionRow> actionRows = new ArrayList<>();

        if (!categories.isEmpty()) {
            // Build category buttons (max 5 per row, max 5 rows = 25 buttons)
            List<Button> currentRowButtons = new ArrayList<>();
            for (DatabaseHandler.TicketCategoryData category : categories) {
                Button catButton;
                System.out.println("Category Button Emoji: " + category.buttonEmoji);
                if (!category.buttonEmoji.equals("No emoji selected") && isValidEmoji(category.buttonEmoji)) {
                    try {
                        catButton = Button.of(getButtonStyle(category.buttonColor), "ticket_cat_" + category.id,
                            category.buttonLabel, Emoji.fromFormatted(category.buttonEmoji));
                    } catch (Exception e) {
                        catButton = Button.of(getButtonStyle(category.buttonColor), "ticket_cat_" + category.id,
                            category.buttonLabel);
                    }
                } else {
                    catButton = Button.of(getButtonStyle(category.buttonColor), "ticket_cat_" + category.id,
                        category.buttonLabel);
                }
                currentRowButtons.add(catButton);

                // Max 5 buttons per row
                if (currentRowButtons.size() >= 5) {
                    actionRows.add(ActionRow.of(currentRowButtons));
                    currentRowButtons = new ArrayList<>();
                }

                // Max 5 rows
                if (actionRows.size() >= 5) break;
            }

            // Add remaining buttons
            if (!currentRowButtons.isEmpty() && actionRows.size() < 5) {
                actionRows.add(ActionRow.of(currentRowButtons));
            }
        } else {
            // No categories - use single button
            Button ticketButton;
            System.out.println("Panel Button Emoji: " + panel.buttonEmoji);
            if (!panel.buttonEmoji.equals("No emoji selected") && isValidEmoji(panel.buttonEmoji)) {
                try {
                    ticketButton = Button.of(getButtonStyle(panel.buttonColor), "create_ticket_" + panelId,
                        panel.buttonLabel, Emoji.fromFormatted(panel.buttonEmoji));
                } catch (Exception e) {
                    ticketButton = Button.of(getButtonStyle(panel.buttonColor), "create_ticket_" + panelId, panel.buttonLabel);
                }
            } else {
                ticketButton = Button.of(getButtonStyle(panel.buttonColor), "create_ticket_" + panelId, panel.buttonLabel);
            }
            actionRows.add(ActionRow.of(ticketButton));
        }

        targetChannel.sendMessageEmbeds(embed.build())
            .setComponents(actionRows)
            .queue(message -> {
                handler.updateTicketPanelMessageId(panelId, message.getId());
                event.reply(t(guildId, "ticket_panels.sent", targetChannel.getAsMention())).setEphemeral(true).queue();
            }, error -> {
                System.out.println("Error sending ticket panel message: " + error.getMessage());
                event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
            });
    }

    // ==================== DELETE PANEL ====================

    private void showDeleteConfirmation(ButtonInteractionEvent event, int panelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);

        if (panel == null) {
            event.reply(t(guildId, "ticket_panels.not_found")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚠️ " + t(guildId, "ticket_panels.delete_confirm_title"));
        embed.setDescription(t(guildId, "ticket_panels.delete_confirm_desc", panel.name));
        embed.setColor(Color.RED);

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
            Button.danger("tp_confirm_delete_" + panelId, t(guildId, "general.confirm")),
            Button.secondary("tp_back_detail_" + panelId, t(guildId, "general.cancel"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void deletePanel(ButtonInteractionEvent event, int panelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        boolean success = handler.deleteTicketPanel(panelId);
        if (success) {
            event.reply(t(guildId, "ticket_panels.deleted")).setEphemeral(true).queue();
            // Go back to panels list
            showMainMenuEdit(event);
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    // ==================== CATEGORIES UI ====================

    private void showCategoriesList(ButtonInteractionEvent event, int panelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        UserSession session = getOrCreateSession(event.getUser().getId(), guildId);
        session.selectedPanelId = panelId;

        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
        List<DatabaseHandler.TicketCategoryData> categories = handler.getTicketCategories(panelId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📂 " + t(guildId, "ticket_panels.categories_title"));
        embed.setDescription(t(guildId, "ticket_panels.categories_desc", panel != null ? panel.name : "Panel"));
        embed.setColor(new Color(87, 242, 135));
        embed.setFooter(t(guildId, "ticket_panels.breadcrumb_categories", panel != null ? panel.name : "Panel"));

        if (categories.isEmpty()) {
            embed.addField(t(guildId, "ticket_panels.no_categories"),
                t(guildId, "ticket_panels.no_categories_hint"), false);
        } else {
            StringBuilder desc = new StringBuilder();
            for (DatabaseHandler.TicketCategoryData cat : categories) {
                String emoji = cat.buttonEmoji != null ? cat.buttonEmoji + " " : "";
                desc.append("**").append(cat.position + 1).append(".** ")
                    .append(emoji).append(cat.name)
                    .append("\n  └ ").append(cat.buttonLabel).append("\n");
            }
            embed.addField(t(guildId, "ticket_panels.categories_list") + " (" + categories.size() + ")",
                desc.toString(), false);
        }

        List<ActionRow> rows = new ArrayList<>();

        if (!categories.isEmpty()) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("tp_select_category_" + panelId)
                .setPlaceholder(t(guildId, "ticket_panels.select_category_placeholder"))
                .setMinValues(1)
                .setMaxValues(1);

            for (DatabaseHandler.TicketCategoryData cat : categories) {
                menuBuilder.addOption(cat.name, String.valueOf(cat.id), cat.description != null ? cat.description : cat.buttonLabel);
            }
            rows.add(ActionRow.of(menuBuilder.build()));
        }

        rows.add(ActionRow.of(
            Button.success("tp_cat_create_" + panelId, "➕ " + t(guildId, "ticket_panels.btn_add_category")),
            Button.secondary("tp_back_detail_" + panelId, "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showCategoryDetails(ButtonInteractionEvent event, int categoryId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(categoryId);

        if (category == null) {
            event.reply(t(guildId, "ticket_panels.category_not_found")).setEphemeral(true).queue();
            return;
        }

        List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFields(categoryId);

        EmbedBuilder embed = buildCategoryDetailsEmbed(category, fields, guildId);
        List<ActionRow> rows = buildCategoryDetailsButtons(categoryId, category.panelId, guildId);

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showCategoryDetailsFromSelect(StringSelectInteractionEvent event, int categoryId, int panelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(categoryId);

        if (category == null) {
            event.reply(t(guildId, "ticket_panels.category_not_found")).setEphemeral(true).queue();
            return;
        }

        List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFields(categoryId);

        EmbedBuilder embed = buildCategoryDetailsEmbed(category, fields, guildId);
        List<ActionRow> rows = buildCategoryDetailsButtons(categoryId, panelId, guildId);

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private EmbedBuilder buildCategoryDetailsEmbed(DatabaseHandler.TicketCategoryData category,
                                                    List<DatabaseHandler.TicketFormFieldData> fields, String guildId) {
        EmbedBuilder embed = new EmbedBuilder();
        String emoji = category.buttonEmoji != null ? category.buttonEmoji + " " : "📂 ";
        embed.setTitle(emoji + category.name);
        embed.setColor(new Color(235, 69, 158));
        DatabaseHandler.TicketPanelData parentPanel = handler.getTicketPanel(category.panelId);
        embed.setFooter(t(guildId, "ticket_panels.breadcrumb_category_detail",
            parentPanel != null ? parentPanel.name : "Panel", category.name));

        embed.addField("🔘 " + t(guildId, "ticket_panels.field_button"), category.buttonLabel, true);
        embed.addField("🎨 " + t(guildId, "ticket_panels.field_color"), category.buttonColor, true);
        embed.addField("📍 " + t(guildId, "ticket_panels.field_position"), String.valueOf(category.position + 1), true);

        if (category.description != null) {
            embed.addField("📄 " + t(guildId, "ticket_panels.field_description"), category.description, false);
        }

        if (category.welcomeMessage != null) {
            embed.addField("👋 " + t(guildId, "ticket_panels.field_welcome"),
                category.welcomeMessage.length() > 200 ? category.welcomeMessage.substring(0, 200) + "..." : category.welcomeMessage, false);
        }

        // Forms info
        List<DatabaseHandler.TicketFormData> forms = handler.getTicketForms(category.id);
        if (forms.isEmpty()) {
            // Show legacy form fields info
            if (fields.isEmpty()) {
                embed.addField("📋 " + t(guildId, "ticket_panels.forms_title") + " (0)",
                    t(guildId, "ticket_panels.no_forms_hint"), false);
            } else {
                StringBuilder fieldsStr = new StringBuilder();
                for (DatabaseHandler.TicketFormFieldData field : fields) {
                    String required = field.required ? "✅" : "❌";
                    fieldsStr.append(required).append(" **").append(field.label).append("** (")
                        .append(field.fieldType).append(")\n");
                }
                embed.addField("📝 " + t(guildId, "ticket_panels.form_fields") + " (" + fields.size() + ")",
                    fieldsStr.toString(), false);
            }
        } else {
            StringBuilder formsStr = new StringBuilder();
            for (DatabaseHandler.TicketFormData form : forms) {
                List<DatabaseHandler.TicketFormFieldData> formFields = handler.getTicketFormFieldsByFormId(form.id);
                formsStr.append("📋 **").append(form.name).append("** (")
                    .append(formFields.size()).append(" ").append(t(guildId, "ticket_panels.fields_count")).append(")\n");
            }
            embed.addField("📋 " + t(guildId, "ticket_panels.forms_title") + " (" + forms.size() + ")",
                formsStr.toString(), false);
        }

        return embed;
    }

    private List<ActionRow> buildCategoryDetailsButtons(int categoryId, int panelId, String guildId) {
        List<ActionRow> rows = new ArrayList<>();

        rows.add(ActionRow.of(
            Button.primary("tp_cat_edit_" + categoryId, "✏️ " + t(guildId, "general.edit")),
            Button.primary("tp_cat_forms_" + categoryId, "📝 " + t(guildId, "ticket_panels.btn_edit_form"))
        ));

        rows.add(ActionRow.of(
            Button.secondary("tp_cat_up_" + categoryId, "⬆️"),
            Button.secondary("tp_cat_down_" + categoryId, "⬇️"),
            Button.danger("tp_cat_delete_" + categoryId, "🗑️ " + t(guildId, "general.delete")),
            Button.secondary("tp_cat_back_" + panelId, "⬅️ " + t(guildId, "general.back"))
        ));

        return rows;
    }

    // ==================== FORMS UI ====================

    private void showPanelFormsList(ButtonInteractionEvent event, int panelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
        List<DatabaseHandler.TicketCategoryData> categories = handler.getTicketCategories(panelId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📋 " + t(guildId, "ticket_panels.panel_forms_title"));
        embed.setDescription(t(guildId, "ticket_panels.panel_forms_desc", panel != null ? panel.name : "Panel"));
        embed.setColor(new Color(114, 137, 218));
        if (panel != null) embed.setFooter(t(guildId, "ticket_panels.breadcrumb_forms", panel.name, "Forms"));

        if (categories.isEmpty()) {
            embed.addField(t(guildId, "ticket_panels.no_categories"),
                t(guildId, "ticket_panels.no_categories_forms_hint"), false);
        } else {
            int totalForms = 0;
            for (DatabaseHandler.TicketCategoryData category : categories) {
                List<DatabaseHandler.TicketFormData> forms = handler.getTicketForms(category.id);
                String emoji = category.buttonEmoji != null ? category.buttonEmoji + " " : "📂 ";

                if (forms.isEmpty()) {
                    // Check for legacy fields
                    List<DatabaseHandler.TicketFormFieldData> legacyFields = handler.getTicketFormFields(category.id);
                    if (legacyFields.isEmpty()) {
                        embed.addField(emoji + category.name,
                            t(guildId, "ticket_panels.no_forms") + "\n" +
                            t(guildId, "ticket_panels.click_to_add_form"), true);
                    } else {
                        embed.addField(emoji + category.name,
                            "📝 " + legacyFields.size() + " " + t(guildId, "ticket_panels.legacy_fields"), true);
                    }
                } else {
                    totalForms += forms.size();
                    StringBuilder formsList = new StringBuilder();
                    for (DatabaseHandler.TicketFormData form : forms) {
                        List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(form.id);
                        formsList.append("📋 ").append(form.name)
                            .append(" (").append(fields.size()).append(" ").append(t(guildId, "ticket_panels.fields_count")).append(")\n");
                    }
                    embed.addField(emoji + category.name, formsList.toString(), true);
                }
            }
            embed.setFooter(t(guildId, "ticket_panels.total_forms", totalForms, categories.size()));
        }

        List<ActionRow> rows = new ArrayList<>();

        // Dropdown to select a category to manage its forms
        if (!categories.isEmpty()) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("tp_select_cat_forms_" + panelId)
                .setPlaceholder(t(guildId, "ticket_panels.select_category_for_forms"))
                .setMinValues(1)
                .setMaxValues(1);

            for (DatabaseHandler.TicketCategoryData category : categories) {
                String emoji = category.buttonEmoji != null ? category.buttonEmoji : "📂";
                menuBuilder.addOption(category.name, String.valueOf(category.id),
                    t(guildId, "ticket_panels.manage_forms_for") + " " + category.name,
                    Emoji.fromFormatted(emoji));
            }
            rows.add(ActionRow.of(menuBuilder.build()));
        }

        rows.add(ActionRow.of(
            Button.secondary("tp_back_detail_" + panelId, "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showFormsList(ButtonInteractionEvent event, int categoryId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(categoryId);
        List<DatabaseHandler.TicketFormData> forms = handler.getTicketForms(categoryId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📋 " + t(guildId, "ticket_panels.forms_title"));
        embed.setDescription(t(guildId, "ticket_panels.forms_desc", category != null ? category.name : "Category"));
        embed.setColor(new Color(114, 137, 218));
        DatabaseHandler.TicketPanelData fp = category != null ? handler.getTicketPanel(category.panelId) : null;
        embed.setFooter(t(guildId, "ticket_panels.breadcrumb_forms",
            fp != null ? fp.name : "Panel", category != null ? category.name : "Category"));

        if (forms.isEmpty()) {
            embed.addField(t(guildId, "ticket_panels.no_forms"),
                t(guildId, "ticket_panels.no_forms_hint"), false);
        } else {
            for (DatabaseHandler.TicketFormData form : forms) {
                List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(form.id);
                String desc = form.description != null ? form.description : "";
                desc += "\n📝 " + fields.size() + " " + t(guildId, "ticket_panels.fields_count");
                embed.addField((form.position + 1) + ". " + form.name, desc, false);
            }
        }

        List<ActionRow> rows = new ArrayList<>();

        if (!forms.isEmpty()) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("tp_select_form_" + categoryId)
                .setPlaceholder(t(guildId, "ticket_panels.select_form_placeholder"))
                .setMinValues(1)
                .setMaxValues(1);

            for (DatabaseHandler.TicketFormData form : forms) {
                menuBuilder.addOption(form.name, String.valueOf(form.id),
                    form.description != null ? form.description : "");
            }
            rows.add(ActionRow.of(menuBuilder.build()));
        }

        rows.add(ActionRow.of(
            Button.success("tp_form_create_" + categoryId, "➕ " + t(guildId, "ticket_panels.btn_add_form")),
            Button.secondary("tp_cat_back_detail_" + categoryId, "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showFormsListFromPanelSelect(StringSelectInteractionEvent event, int categoryId, int panelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(categoryId);
        List<DatabaseHandler.TicketFormData> forms = handler.getTicketForms(categoryId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📋 " + t(guildId, "ticket_panels.forms_title"));
        embed.setDescription(t(guildId, "ticket_panels.forms_desc", category != null ? category.name : "Category"));
        embed.setColor(new Color(114, 137, 218));
        DatabaseHandler.TicketPanelData fp2 = category != null ? handler.getTicketPanel(category.panelId) : null;
        embed.setFooter(t(guildId, "ticket_panels.breadcrumb_forms",
            fp2 != null ? fp2.name : "Panel", category != null ? category.name : "Category"));

        if (forms.isEmpty()) {
            embed.addField(t(guildId, "ticket_panels.no_forms"),
                t(guildId, "ticket_panels.no_forms_hint"), false);
        } else {
            for (DatabaseHandler.TicketFormData form : forms) {
                List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(form.id);
                String desc = form.description != null ? form.description : "";
                desc += "\n📝 " + fields.size() + " " + t(guildId, "ticket_panels.fields_count");
                embed.addField((form.position + 1) + ". " + form.name, desc, false);
            }
        }

        List<ActionRow> rows = new ArrayList<>();

        if (!forms.isEmpty()) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("tp_select_form_" + categoryId)
                .setPlaceholder(t(guildId, "ticket_panels.select_form_placeholder"))
                .setMinValues(1)
                .setMaxValues(1);

            for (DatabaseHandler.TicketFormData form : forms) {
                menuBuilder.addOption(form.name, String.valueOf(form.id),
                    form.description != null ? form.description : "");
            }
            rows.add(ActionRow.of(menuBuilder.build()));
        }

        rows.add(ActionRow.of(
            Button.success("tp_form_create_" + categoryId, "➕ " + t(guildId, "ticket_panels.btn_add_form")),
            Button.secondary("tp_panel_forms_" + panelId, "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showFormDetails(ButtonInteractionEvent event, int formId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);

        if (form == null) {
            event.reply(t(guildId, "ticket_panels.form_not_found")).setEphemeral(true).queue();
            return;
        }

        List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(formId);
        List<DatabaseHandler.TicketFormData> allForms = handler.getTicketForms(form.categoryId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📋 " + form.name);
        embed.setColor(new Color(114, 137, 218));
        DatabaseHandler.TicketCategoryData formCat = handler.getTicketCategory(form.categoryId);
        DatabaseHandler.TicketPanelData formPanel = formCat != null ? handler.getTicketPanel(formCat.panelId) : null;
        embed.setFooter(t(guildId, "ticket_panels.breadcrumb_form_detail",
            formPanel != null ? formPanel.name : "Panel", form.name));

        if (form.description != null) {
            embed.addField("📄 " + t(guildId, "ticket_panels.field_description"), form.description, false);
        }

        embed.addField("📍 " + t(guildId, "ticket_panels.field_position"), String.valueOf(form.position + 1), true);
        embed.addField("📝 " + t(guildId, "ticket_panels.fields_count"), String.valueOf(fields.size()) + "/5", true);

        if (!fields.isEmpty()) {
            StringBuilder fieldsStr = new StringBuilder();
            for (DatabaseHandler.TicketFormFieldData field : fields) {
                String required = field.required ? "✅" : "❌";
                fieldsStr.append(required).append(" **").append(field.label).append("** (")
                    .append(field.fieldType).append(")\n");
            }
            embed.addField("📝 " + t(guildId, "ticket_panels.form_fields"), fieldsStr.toString(), false);
        }

        List<ActionRow> rows = new ArrayList<>();

        // Position dropdown if multiple forms
        if (allForms.size() > 1) {
            StringSelectMenu.Builder positionMenu = StringSelectMenu.create("tp_form_move_" + formId + "_" + form.categoryId)
                .setPlaceholder(t(guildId, "ticket_panels.select_position"))
                .setMinValues(1)
                .setMaxValues(1);

            for (int i = 0; i < allForms.size(); i++) {
                DatabaseHandler.TicketFormData f = allForms.get(i);
                String label = (i + 1) + ". " + f.name;
                if (label.length() > 100) label = label.substring(0, 97) + "...";
                positionMenu.addOption(label, String.valueOf(i),
                    f.id == formId ? "📍 " + t(guildId, "ticket_panels.current_position") : "");
            }
            rows.add(ActionRow.of(positionMenu.build()));
        }

        rows.add(ActionRow.of(
            Button.primary("tp_form_fields_" + formId, "📝 " + t(guildId, "ticket_panels.btn_edit_fields")),
            Button.primary("tp_form_edit_" + formId, "✏️ " + t(guildId, "general.edit")),
            Button.danger("tp_form_delete_" + formId, "🗑️ " + t(guildId, "general.delete"))
        ));

        rows.add(ActionRow.of(
            Button.secondary("tp_form_back_" + form.categoryId, "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showFormDetailsFromSelect(StringSelectInteractionEvent event, int formId, int categoryId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);

        if (form == null) {
            event.reply(t(guildId, "ticket_panels.form_not_found")).setEphemeral(true).queue();
            return;
        }

        List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(formId);
        List<DatabaseHandler.TicketFormData> allForms = handler.getTicketForms(categoryId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📋 " + form.name);
        embed.setColor(new Color(114, 137, 218));
        DatabaseHandler.TicketCategoryData formCat2 = handler.getTicketCategory(categoryId);
        DatabaseHandler.TicketPanelData formPanel2 = formCat2 != null ? handler.getTicketPanel(formCat2.panelId) : null;
        embed.setFooter(t(guildId, "ticket_panels.breadcrumb_form_detail",
            formPanel2 != null ? formPanel2.name : "Panel", form.name));

        if (form.description != null) {
            embed.addField("📄 " + t(guildId, "ticket_panels.field_description"), form.description, false);
        }

        embed.addField("📍 " + t(guildId, "ticket_panels.field_position"), String.valueOf(form.position + 1), true);
        embed.addField("📝 " + t(guildId, "ticket_panels.fields_count"), String.valueOf(fields.size()) + "/5", true);

        if (!fields.isEmpty()) {
            StringBuilder fieldsStr = new StringBuilder();
            for (DatabaseHandler.TicketFormFieldData field : fields) {
                String required = field.required ? "✅" : "❌";
                fieldsStr.append(required).append(" **").append(field.label).append("** (")
                    .append(field.fieldType).append(")\n");
            }
            embed.addField("📝 " + t(guildId, "ticket_panels.form_fields"), fieldsStr.toString(), false);
        }

        List<ActionRow> rows = new ArrayList<>();

        if (allForms.size() > 1) {
            StringSelectMenu.Builder positionMenu = StringSelectMenu.create("tp_form_move_" + formId + "_" + categoryId)
                .setPlaceholder(t(guildId, "ticket_panels.select_position"))
                .setMinValues(1)
                .setMaxValues(1);

            for (int i = 0; i < allForms.size(); i++) {
                DatabaseHandler.TicketFormData f = allForms.get(i);
                String label = (i + 1) + ". " + f.name;
                if (label.length() > 100) label = label.substring(0, 97) + "...";
                positionMenu.addOption(label, String.valueOf(i),
                    f.id == formId ? "📍 " + t(guildId, "ticket_panels.current_position") : "");
            }
            rows.add(ActionRow.of(positionMenu.build()));
        }

        rows.add(ActionRow.of(
            Button.primary("tp_form_fields_" + formId, "📝 " + t(guildId, "ticket_panels.btn_edit_fields")),
            Button.primary("tp_form_edit_" + formId, "✏️ " + t(guildId, "general.edit")),
            Button.danger("tp_form_delete_" + formId, "🗑️ " + t(guildId, "general.delete"))
        ));

        rows.add(ActionRow.of(
            Button.secondary("tp_form_back_" + categoryId, "⬅️ " + t(guildId, "general.back"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void handleFormPositionChange(StringSelectInteractionEvent event, int formId, int categoryId, int newPosition) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);

        if (form == null) {
            event.reply(t(guildId, "ticket_panels.form_not_found")).setEphemeral(true).queue();
            return;
        }

        if (form.position == newPosition) {
            showFormDetailsFromSelect(event, formId, categoryId);
            return;
        }

        boolean success = handler.moveTicketFormToPosition(formId, newPosition);
        if (success) {
            showFormDetailsFromSelect(event, formId, categoryId);
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    private void showDeleteFormConfirmation(ButtonInteractionEvent event, int formId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);

        if (form == null) {
            event.reply(t(guildId, "ticket_panels.form_not_found")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚠️ " + t(guildId, "ticket_panels.delete_form_confirm_title"));
        embed.setDescription(t(guildId, "ticket_panels.delete_form_confirm_desc", form.name));
        embed.setColor(Color.RED);

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
            Button.danger("tp_form_confirm_delete_" + formId, t(guildId, "general.confirm")),
            Button.secondary("tp_form_cancel_delete_" + formId, t(guildId, "general.cancel"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showCreateFormModal(ButtonInteractionEvent event, int categoryId) {
        TextInput nameInput = TextInput.create("name", TextInputStyle.SHORT)
            .setPlaceholder("e.g., Standard Form, Detailed Form...")
            .setRequiredRange(1, 100)
            .build();

        TextInput descInput = TextInput.create("description", TextInputStyle.SHORT)
            .setPlaceholder("Short description for this form")
            .setRequired(false)
            .setMaxLength(256)
            .build();

        Modal modal = Modal.create("tp_create_form_modal_" + categoryId, "Create New Form")
            .addComponents(
                Label.of("Form Name", nameInput),
                Label.of("Description (Optional)", descInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void showEditFormModal(ButtonInteractionEvent event, int formId) {
        DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);
        if (form == null) return;

        TextInput nameInput = TextInput.create("name", TextInputStyle.SHORT)
            .setValue(form.name)
            .setRequiredRange(1, 100)
            .build();

        TextInput descInput = TextInput.create("description", TextInputStyle.SHORT)
            .setValue(form.description != null ? form.description : "")
            .setRequired(false)
            .setMaxLength(256)
            .build();

        Modal modal = Modal.create("tp_edit_form_modal_" + formId, "Edit Form")
            .addComponents(
                Label.of("Form Name", nameInput),
                Label.of("Description", descInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void handleCreateFormModal(ModalInteractionEvent event, int categoryId, String guildId) {
        String name = Objects.requireNonNull(event.getValue("name")).getAsString();
        var descValue = event.getValue("description");
        String description = descValue != null && !descValue.getAsString().isBlank() ? descValue.getAsString() : null;

        int formId = handler.createTicketForm(categoryId, name, description);
        if (formId > 0) {
            // Show form details directly so user can edit it
            DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);
            List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(formId);
            List<DatabaseHandler.TicketFormData> allForms = handler.getTicketForms(categoryId);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("📋 " + form.name);
            embed.setDescription(t(guildId, "ticket_panels.form_created", name));
            embed.setColor(new Color(114, 137, 218));

            if (form.description != null) {
                embed.addField("📄 " + t(guildId, "ticket_panels.field_description"), form.description, false);
            }

            embed.addField("📍 " + t(guildId, "ticket_panels.field_position"), String.valueOf(form.position + 1), true);
            embed.addField("📝 " + t(guildId, "ticket_panels.fields_count"), fields.size() + "/5", true);

            embed.addField("💡 " + t(guildId, "general.info"),
                t(guildId, "ticket_panels.no_form_fields_hint"), false);

            List<ActionRow> rows = new ArrayList<>();

            if (allForms.size() > 1) {
                StringSelectMenu.Builder positionMenu = StringSelectMenu.create("tp_form_move_" + formId + "_" + categoryId)
                    .setPlaceholder(t(guildId, "ticket_panels.select_position"))
                    .setMinValues(1)
                    .setMaxValues(1);

                for (int i = 0; i < allForms.size(); i++) {
                    DatabaseHandler.TicketFormData f = allForms.get(i);
                    String label = (i + 1) + ". " + f.name;
                    if (label.length() > 100) label = label.substring(0, 97) + "...";
                    positionMenu.addOption(label, String.valueOf(i),
                        f.id == formId ? "📍 " + t(guildId, "ticket_panels.current_position") : "");
                }
                rows.add(ActionRow.of(positionMenu.build()));
            }

            rows.add(ActionRow.of(
                Button.primary("tp_form_fields_" + formId, "📝 " + t(guildId, "ticket_panels.btn_edit_fields")),
                Button.primary("tp_form_edit_" + formId, "✏️ " + t(guildId, "general.edit")),
                Button.danger("tp_form_delete_" + formId, "🗑️ " + t(guildId, "general.delete"))
            ));

            rows.add(ActionRow.of(
                Button.secondary("tp_form_back_" + categoryId, "⬅️ " + t(guildId, "general.back"))
            ));

            event.replyEmbeds(embed.build())
                .setComponents(rows)
                .setEphemeral(true)
                .queue();
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    private void handleEditFormModalResponse(ModalInteractionEvent event, int formId, String guildId) {
        String name = Objects.requireNonNull(event.getValue("name")).getAsString();
        var descValue = event.getValue("description");
        String description = descValue != null && !descValue.getAsString().isBlank() ? descValue.getAsString() : null;

        boolean success = handler.updateTicketForm(formId, name, description);
        if (success) {
            event.reply(t(guildId, "ticket_panels.form_updated")).setEphemeral(true).queue();
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    // ==================== FORM FIELDS FOR FORM UI ====================

    private void showFormFieldsListForForm(ButtonInteractionEvent event, int formId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);
        List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(formId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📝 " + t(guildId, "ticket_panels.form_fields_title"));
        embed.setDescription(t(guildId, "ticket_panels.form_fields_desc", form != null ? form.name : "Form"));
        embed.setColor(new Color(254, 231, 92));

        if (fields.isEmpty()) {
            embed.addField(t(guildId, "ticket_panels.no_form_fields"),
                t(guildId, "ticket_panels.no_form_fields_hint"), false);
        } else {
            for (DatabaseHandler.TicketFormFieldData field : fields) {
                String required = field.required ? "✅ Required" : "❌ Optional";
                String type = field.fieldType.equals("PARAGRAPH") ? "📄 Paragraph" : "📝 Short";
                embed.addField((field.position + 1) + ". " + field.label,
                    type + " | " + required + "\n" +
                    (field.placeholder != null ? "Placeholder: " + field.placeholder : ""), false);
            }
        }

        List<ActionRow> rows = new ArrayList<>();

        if (!fields.isEmpty()) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("tp_select_formfield_" + formId)
                .setPlaceholder(t(guildId, "ticket_panels.select_field_placeholder"))
                .setMinValues(1)
                .setMaxValues(1);

            for (DatabaseHandler.TicketFormFieldData field : fields) {
                menuBuilder.addOption(field.label, String.valueOf(field.id), field.fieldType);
            }
            rows.add(ActionRow.of(menuBuilder.build()));
        }

        if (fields.size() < 5) {
            rows.add(ActionRow.of(
                Button.success("tp_field_create_" + formId + "_form", "➕ " + t(guildId, "ticket_panels.btn_add_field")),
                Button.primary("tp_form_preview_fields_" + formId, "👁️ " + t(guildId, "ticket_panels.btn_preview_form")),
                Button.secondary("tp_form_detail_back_" + formId, "⬅️ " + t(guildId, "general.back"))
            ));
        } else {
            rows.add(ActionRow.of(
                Button.primary("tp_form_preview_fields_" + formId, "👁️ " + t(guildId, "ticket_panels.btn_preview_form")),
                Button.secondary("tp_form_detail_back_" + formId, "⬅️ " + t(guildId, "general.back"))
            ));
            embed.setFooter(t(guildId, "ticket_panels.max_fields_reached"));
        }

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showFormFieldDetailsForFormFromSelect(StringSelectInteractionEvent event, int fieldId, int formId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormFieldData field = handler.getTicketFormField(fieldId);

        if (field == null) {
            event.reply(t(guildId, "ticket_panels.field_not_found")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = buildFormFieldEmbed(field, guildId);
        List<ActionRow> rows = buildFormFieldButtonsForForm(fieldId, formId, guildId);

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private List<ActionRow> buildFormFieldButtonsForForm(int fieldId, int formId, String guildId) {
        List<ActionRow> rows = new ArrayList<>();

        List<DatabaseHandler.TicketFormFieldData> allFields = handler.getTicketFormFieldsByFormId(formId);

        if (allFields.size() > 1) {
            StringSelectMenu.Builder positionMenu = StringSelectMenu.create("tp_field_move_" + fieldId + "_" + formId)
                .setPlaceholder(t(guildId, "ticket_panels.select_position"))
                .setMinValues(1)
                .setMaxValues(1);

            for (int i = 0; i < allFields.size(); i++) {
                DatabaseHandler.TicketFormFieldData f = allFields.get(i);
                String label = (i + 1) + ". " + f.label;
                if (label.length() > 100) label = label.substring(0, 97) + "...";
                positionMenu.addOption(label, String.valueOf(i),
                    f.id == fieldId ? "📍 " + t(guildId, "ticket_panels.current_position") : "");
            }
            rows.add(ActionRow.of(positionMenu.build()));
        }

        rows.add(ActionRow.of(
            Button.primary("tp_field_edit_" + fieldId, "✏️ " + t(guildId, "general.edit")),
            Button.danger("tp_field_delete_" + fieldId, "🗑️ " + t(guildId, "general.delete")),
            Button.secondary("tp_form_fields_" + formId, "⬅️ " + t(guildId, "general.back"))
        ));

        return rows;
    }

    private void showCreateFormFieldModalForForm(ButtonInteractionEvent event, int formId) {
        TextInput labelInput = TextInput.create("label", TextInputStyle.SHORT)
            .setPlaceholder("e.g., What is your issue?")
            .setRequiredRange(1, 45)
            .build();

        TextInput placeholderInput = TextInput.create("placeholder", TextInputStyle.SHORT)
            .setPlaceholder("Placeholder text shown in the input field")
            .setRequired(false)
            .setMaxLength(100)
            .build();

        TextInput typeInput = TextInput.create("type", TextInputStyle.SHORT)
            .setPlaceholder("SHORT or PARAGRAPH")
            .setValue("SHORT")
            .setRequiredRange(1, 20)
            .build();

        TextInput requiredInput = TextInput.create("required", TextInputStyle.SHORT)
            .setPlaceholder("true or false")
            .setValue("true")
            .setRequiredRange(1, 10)
            .build();

        Modal modal = Modal.create("tp_create_formfield_modal_" + formId, "Add Form Field")
            .addComponents(
                Label.of("Field Label (max 45 chars)", labelInput),
                Label.of("Placeholder Text (Optional)", placeholderInput),
                Label.of("Field Type (SHORT/PARAGRAPH)", typeInput),
                Label.of("Required? (true/false)", requiredInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void handleCreateFormFieldModalForForm(ModalInteractionEvent event, int formId, String guildId) {
            DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);
            if (form == null) {
                event.reply(t(guildId, "ticket_panels.form_not_found")).setEphemeral(true).queue();
                return;
            }
            int categoryId = form.categoryId;

            String label = Objects.requireNonNull(event.getValue("label")).getAsString();
            var placeholderValue = event.getValue("placeholder");
            String placeholder = placeholderValue != null && !placeholderValue.getAsString().isBlank()
                ? placeholderValue.getAsString() : null;
            String type = Objects.requireNonNull(event.getValue("type")).getAsString().toUpperCase();
            String requiredStr = Objects.requireNonNull(event.getValue("required")).getAsString();
            boolean required = requiredStr.equalsIgnoreCase("true") || requiredStr.equals("1");

            if (!type.equals("SHORT") && !type.equals("PARAGRAPH")) {
                type = "SHORT";
            }

            int maxLength = type.equals("PARAGRAPH") ? 1000 : 100;

            int fieldId = handler.createTicketFormFieldForForm(categoryId, formId, label, placeholder, type, 0, maxLength, required);
            if (fieldId > 0) {
                event.reply(t(guildId, "ticket_panels.field_created", label)).setEphemeral(true).queue();
            } else {
                event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
            }
        }

    // ==================== FORM FIELDS UI ====================

    private void showFormFieldsList(ButtonInteractionEvent event, int categoryId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(categoryId);
        List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFields(categoryId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📝 " + t(guildId, "ticket_panels.form_fields_title"));
        embed.setDescription(t(guildId, "ticket_panels.form_fields_desc", category != null ? category.name : "Category"));
        embed.setColor(new Color(254, 231, 92));

        if (fields.isEmpty()) {
            embed.addField(t(guildId, "ticket_panels.no_form_fields"),
                t(guildId, "ticket_panels.no_form_fields_hint"), false);
        } else {
            for (DatabaseHandler.TicketFormFieldData field : fields) {
                String required = field.required ? "✅ Required" : "❌ Optional";
                String type = field.fieldType.equals("PARAGRAPH") ? "📄 Paragraph" : "📝 Short";
                embed.addField((field.position + 1) + ". " + field.label,
                    type + " | " + required + "\n" +
                    (field.placeholder != null ? "Placeholder: " + field.placeholder : ""), false);
            }
        }

        List<ActionRow> rows = new ArrayList<>();

        if (!fields.isEmpty()) {
            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("tp_select_field_" + categoryId)
                .setPlaceholder(t(guildId, "ticket_panels.select_field_placeholder"))
                .setMinValues(1)
                .setMaxValues(1);

            for (DatabaseHandler.TicketFormFieldData field : fields) {
                menuBuilder.addOption(field.label, String.valueOf(field.id), field.fieldType);
            }
            rows.add(ActionRow.of(menuBuilder.build()));
        }

        // Max 5 form fields due to Discord modal limits
        if (fields.size() < 5) {
            rows.add(ActionRow.of(
                Button.success("tp_field_create_" + categoryId, "➕ " + t(guildId, "ticket_panels.btn_add_field")),
                Button.primary("tp_field_preview_" + categoryId, "👁️ " + t(guildId, "ticket_panels.btn_preview_form")),
                Button.secondary("tp_field_back_" + categoryId, "⬅️ " + t(guildId, "general.back"))
            ));
        } else {
            rows.add(ActionRow.of(
                Button.primary("tp_field_preview_" + categoryId, "👁️ " + t(guildId, "ticket_panels.btn_preview_form")),
                Button.secondary("tp_field_back_" + categoryId, "⬅️ " + t(guildId, "general.back"))
            ));
            embed.setFooter(t(guildId, "ticket_panels.max_fields_reached"));
        }

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showFormFieldDetailsFromSelect(StringSelectInteractionEvent event, int fieldId, int categoryId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormFieldData field = handler.getTicketFormField(fieldId);

        if (field == null) {
            event.reply(t(guildId, "ticket_panels.field_not_found")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = buildFormFieldEmbed(field, guildId);
        List<ActionRow> rows = buildFormFieldButtons(fieldId, categoryId, guildId);

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private EmbedBuilder buildFormFieldEmbed(DatabaseHandler.TicketFormFieldData field, String guildId) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("📝 " + field.label);
        embed.setColor(new Color(254, 231, 92));

        String type = field.fieldType.equals("PARAGRAPH") ? "📄 Paragraph" : "📝 Short";
        embed.addField(t(guildId, "ticket_panels.field_type_label"), type, true);
        embed.addField(t(guildId, "ticket_panels.field_required"), field.required ? "✅ Yes" : "❌ No", true);
        embed.addField(t(guildId, "ticket_panels.field_position"), String.valueOf(field.position + 1), true);

        if (field.placeholder != null) {
            embed.addField(t(guildId, "ticket_panels.field_placeholder"), field.placeholder, false);
        }

        embed.addField(t(guildId, "ticket_panels.field_length"),
            "Min: " + field.minLength + " | Max: " + field.maxLength, false);

        return embed;
    }

    private List<ActionRow> buildFormFieldButtons(int fieldId, int categoryId, String guildId) {
        List<ActionRow> rows = new ArrayList<>();

        // Get all fields to build position dropdown
        List<DatabaseHandler.TicketFormFieldData> allFields = handler.getTicketFormFields(categoryId);

        if (allFields.size() > 1) {
            StringSelectMenu.Builder positionMenu = StringSelectMenu.create("tp_field_move_" + fieldId + "_" + categoryId)
                .setPlaceholder(t(guildId, "ticket_panels.select_position"))
                .setMinValues(1)
                .setMaxValues(1);

            for (int i = 0; i < allFields.size(); i++) {
                DatabaseHandler.TicketFormFieldData f = allFields.get(i);
                String label = (i + 1) + ". " + f.label;
                if (label.length() > 100) label = label.substring(0, 97) + "...";
                positionMenu.addOption(label, String.valueOf(i),
                    f.id == fieldId ? "📍 " + t(guildId, "ticket_panels.current_position") : "");
            }
            rows.add(ActionRow.of(positionMenu.build()));
        }

        rows.add(ActionRow.of(
            Button.primary("tp_field_edit_" + fieldId, "✏️ " + t(guildId, "general.edit")),
            Button.danger("tp_field_delete_" + fieldId, "🗑️ " + t(guildId, "general.delete")),
            Button.secondary("tp_field_back_" + categoryId, "⬅️ " + t(guildId, "general.back"))
        ));

        return rows;
    }

    private void showDeleteFieldConfirmation(ButtonInteractionEvent event, int fieldId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormFieldData field = handler.getTicketFormField(fieldId);

        if (field == null) {
            event.reply(t(guildId, "ticket_panels.field_not_found")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚠️ " + t(guildId, "ticket_panels.delete_field_confirm_title"));
        embed.setDescription(t(guildId, "ticket_panels.delete_field_confirm_desc", field.label));
        embed.setColor(Color.RED);

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(
            Button.danger("tp_field_confirm_delete_" + fieldId, t(guildId, "general.confirm")),
            Button.secondary("tp_field_cancel_delete_" + fieldId, t(guildId, "general.cancel"))
        ));

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showFormFieldDetailsFromButton(ButtonInteractionEvent event, int fieldId, int categoryId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormFieldData field = handler.getTicketFormField(fieldId);

        if (field == null) {
            event.reply(t(guildId, "ticket_panels.field_not_found")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = buildFormFieldEmbed(field, guildId);
        List<ActionRow> rows = buildFormFieldButtons(fieldId, categoryId, guildId);

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void handleFieldPositionChange(StringSelectInteractionEvent event, int fieldId, int categoryId, int newPosition) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormFieldData field = handler.getTicketFormField(fieldId);

        if (field == null) {
            event.reply(t(guildId, "ticket_panels.field_not_found")).setEphemeral(true).queue();
            return;
        }

        int currentPosition = field.position;

        if (currentPosition == newPosition) {
            // Position unchanged, just refresh the view
            showFormFieldDetailsFromSelectRefresh(event, fieldId, categoryId);
            return;
        }

        // Move the field to the new position
        boolean success = handler.moveTicketFormFieldToPosition(fieldId, newPosition);

        if (success) {
            showFormFieldDetailsFromSelectRefresh(event, fieldId, categoryId);
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    private void showFormFieldDetailsFromSelectRefresh(StringSelectInteractionEvent event, int fieldId, int categoryId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormFieldData field = handler.getTicketFormField(fieldId);

        if (field == null) {
            event.reply(t(guildId, "ticket_panels.field_not_found")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = buildFormFieldEmbed(field, guildId);
        List<ActionRow> rows = buildFormFieldButtons(fieldId, categoryId, guildId);

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    private void showFormPreviewModal(ButtonInteractionEvent event, int categoryId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(categoryId);

        if (category == null) {
            event.reply(t(guildId, "ticket_panels.category_not_found")).setEphemeral(true).queue();
            return;
        }

        List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFields(categoryId);

        if (fields.isEmpty()) {
            event.reply(t(guildId, "ticket_panels.no_form_fields_to_preview")).setEphemeral(true).queue();
            return;
        }

        Modal.Builder modalBuilder = Modal.create("tp_form_preview_" + categoryId,
            "Preview: " + category.name);

        for (DatabaseHandler.TicketFormFieldData field : fields) {
            TextInputStyle style = field.fieldType.equals("PARAGRAPH")
                ? TextInputStyle.PARAGRAPH
                : TextInputStyle.SHORT;
            TextInput.Builder inputBuilder = TextInput.create("preview_" + field.id, style)
                .setRequired(field.required)
                .setMinLength(field.minLength)
                .setMaxLength(field.maxLength);

            if (field.placeholder != null && !field.placeholder.isBlank()) {
                inputBuilder.setPlaceholder(field.placeholder);
            }

            modalBuilder.addComponents(Label.of(field.label, inputBuilder.build()));
        }

        event.replyModal(modalBuilder.build()).queue();
    }

    private void showFormPreviewModalForForm(ButtonInteractionEvent event, int formId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);

        if (form == null) {
            event.reply(t(guildId, "ticket_panels.form_not_found")).setEphemeral(true).queue();
            return;
        }

        List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(formId);

        if (fields.isEmpty()) {
            event.reply(t(guildId, "ticket_panels.no_form_fields_to_preview")).setEphemeral(true).queue();
            return;
        }

        Modal.Builder modalBuilder = Modal.create("tp_form_preview_" + formId,
            "Preview: " + form.name);

        for (DatabaseHandler.TicketFormFieldData field : fields) {
            TextInputStyle style = field.fieldType.equals("PARAGRAPH")
                ? TextInputStyle.PARAGRAPH
                : TextInputStyle.SHORT;
            TextInput.Builder inputBuilder = TextInput.create("preview_" + field.id, style)
                .setRequired(field.required)
                .setMinLength(field.minLength)
                .setMaxLength(field.maxLength);

            if (field.placeholder != null && !field.placeholder.isBlank()) {
                inputBuilder.setPlaceholder(field.placeholder);
            }

            modalBuilder.addComponents(Label.of(field.label, inputBuilder.build()));
        }

        event.replyModal(modalBuilder.build()).queue();
    }

    private void showFormFieldDetailsForFormFromButton(ButtonInteractionEvent event, int fieldId, int formId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketFormFieldData field = handler.getTicketFormField(fieldId);

        if (field == null) {
            event.reply(t(guildId, "ticket_panels.field_not_found")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = buildFormFieldEmbed(field, guildId);
        List<ActionRow> rows = buildFormFieldButtonsForForm(fieldId, formId, guildId);

        event.editMessageEmbeds(embed.build())
            .setComponents(rows)
            .queue();
    }

    // ==================== CATEGORY MODALS ====================

    private void showCreateCategoryModal(ButtonInteractionEvent event, int panelId) {
        TextInput nameInput = TextInput.create("name", TextInputStyle.SHORT)
            .setPlaceholder("e.g., Support, Bug Report, Application...")
            .setRequiredRange(1, 100)
            .build();

        TextInput labelInput = TextInput.create("button_label", TextInputStyle.SHORT)
            .setPlaceholder("e.g., 📩 Open Support Ticket")
            .setRequiredRange(1, 80)
            .build();

        TextInput descInput = TextInput.create("description", TextInputStyle.SHORT)
            .setPlaceholder("Short description for this category")
            .setRequired(false)
            .setMaxLength(256)
            .build();

        TextInput emojiInput = TextInput.create("emoji", TextInputStyle.SHORT)
            .setPlaceholder("e.g., 🎫 or leave empty")
            .setRequired(false)
            .setMaxLength(50)
            .build();

        Modal modal = Modal.create("tp_create_category_modal_" + panelId, "Create Ticket Category")
            .addComponents(
                Label.of("Category Name", nameInput),
                Label.of("Button Label", labelInput),
                Label.of("Description (Optional)", descInput),
                Label.of("Button Emoji (Optional)", emojiInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void showEditCategoryModal(ButtonInteractionEvent event, int categoryId) {
        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(categoryId);
        if (category == null) return;

        TextInput nameInput = TextInput.create("name", TextInputStyle.SHORT)
            .setValue(category.name)
            .setRequiredRange(1, 100)
            .build();

        TextInput labelInput = TextInput.create("button_label", TextInputStyle.SHORT)
            .setValue(category.buttonLabel)
            .setRequiredRange(1, 80)
            .build();

        TextInput descInput = TextInput.create("description", TextInputStyle.SHORT)
            .setValue(category.description != null ? category.description : "")
            .setRequired(false)
            .setMaxLength(256)
            .build();

        TextInput emojiInput = TextInput.create("emoji", TextInputStyle.SHORT)
            .setValue(category.buttonEmoji != null ? category.buttonEmoji : "")
            .setRequired(false)
            .setMaxLength(50)
            .build();

        TextInput colorInput = TextInput.create("color", TextInputStyle.SHORT)
            .setValue(category.buttonColor)
            .setPlaceholder("PRIMARY, SUCCESS, DANGER, SECONDARY")
            .setRequiredRange(1, 20)
            .build();

        Modal modal = Modal.create("tp_edit_category_modal_" + categoryId, "Edit Category")
            .addComponents(
                Label.of("Category Name", nameInput),
                Label.of("Button Label", labelInput),
                Label.of("Description", descInput),
                Label.of("Button Emoji", emojiInput),
                Label.of("Button Color", colorInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void handleCreateCategoryModal(ModalInteractionEvent event, int panelId, String guildId) {
        String name = Objects.requireNonNull(event.getValue("name")).getAsString();
        String buttonLabel = Objects.requireNonNull(event.getValue("button_label")).getAsString();
        var descValue = event.getValue("description");
        String description = descValue != null && !descValue.getAsString().isBlank() ? descValue.getAsString() : null;
        var emojiValue = event.getValue("emoji");
        String emoji = emojiValue != null && !emojiValue.getAsString().isBlank() ? emojiValue.getAsString() : null;

        int categoryId = handler.createTicketCategory(panelId, name, buttonLabel);
        if (categoryId > 0) {
            if (description != null || emoji != null) {
                handler.updateTicketCategory(categoryId, name, description, buttonLabel, emoji, "PRIMARY", null, null);
            }
            event.reply(t(guildId, "ticket_panels.category_created", name)).setEphemeral(true).queue();
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    private void handleEditCategoryModal(ModalInteractionEvent event, int categoryId, String guildId) {
        String name = Objects.requireNonNull(event.getValue("name")).getAsString();
        String buttonLabel = Objects.requireNonNull(event.getValue("button_label")).getAsString();
        var descValue = event.getValue("description");
        String description = descValue != null && !descValue.getAsString().isBlank() ? descValue.getAsString() : null;
        var emojiValue = event.getValue("emoji");
        String emoji = emojiValue != null && !emojiValue.getAsString().isBlank() ? emojiValue.getAsString() : null;
        String color = Objects.requireNonNull(event.getValue("color")).getAsString();

        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(categoryId);
        boolean success = handler.updateTicketCategory(categoryId, name, description, buttonLabel, emoji, color,
            category != null ? category.categoryId : null, category != null ? category.welcomeMessage : null);

        if (success) {
            event.reply(t(guildId, "ticket_panels.category_updated")).setEphemeral(true).queue();
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    // ==================== FORM FIELD MODALS ====================

    private void showCreateFormFieldModal(ButtonInteractionEvent event, int categoryId) {
        TextInput labelInput = TextInput.create("label", TextInputStyle.SHORT)
            .setPlaceholder("e.g., What is your issue?")
            .setRequiredRange(1, 45)
            .build();

        TextInput placeholderInput = TextInput.create("placeholder", TextInputStyle.SHORT)
            .setPlaceholder("Placeholder text shown in the input field")
            .setRequired(false)
            .setMaxLength(100)
            .build();

        TextInput typeInput = TextInput.create("type", TextInputStyle.SHORT)
            .setPlaceholder("SHORT or PARAGRAPH")
            .setValue("SHORT")
            .setRequiredRange(1, 20)
            .build();

        TextInput requiredInput = TextInput.create("required", TextInputStyle.SHORT)
            .setPlaceholder("true or false")
            .setValue("true")
            .setRequiredRange(1, 10)
            .build();

        Modal modal = Modal.create("tp_create_field_modal_" + categoryId, "Add Form Field")
            .addComponents(
                Label.of("Field Label (max 45 chars)", labelInput),
                Label.of("Placeholder Text (Optional)", placeholderInput),
                Label.of("Field Type (SHORT/PARAGRAPH)", typeInput),
                Label.of("Required? (true/false)", requiredInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void showEditFormFieldModal(ButtonInteractionEvent event, int fieldId) {
        DatabaseHandler.TicketFormFieldData field = handler.getTicketFormField(fieldId);
        if (field == null) return;

        TextInput labelInput = TextInput.create("label", TextInputStyle.SHORT)
            .setValue(field.label)
            .setRequiredRange(1, 45)
            .build();

        TextInput placeholderInput = TextInput.create("placeholder", TextInputStyle.SHORT)
            .setValue(field.placeholder != null ? field.placeholder : "")
            .setRequired(false)
            .setMaxLength(100)
            .build();

        TextInput typeInput = TextInput.create("type", TextInputStyle.SHORT)
            .setValue(field.fieldType)
            .setRequiredRange(1, 20)
            .build();

        TextInput requiredInput = TextInput.create("required", TextInputStyle.SHORT)
            .setValue(String.valueOf(field.required))
            .setRequiredRange(1, 10)
            .build();

        Modal modal = Modal.create("tp_edit_field_modal_" + fieldId, "Edit Form Field")
            .addComponents(
                Label.of("Field Label", labelInput),
                Label.of("Placeholder Text", placeholderInput),
                Label.of("Field Type (SHORT/PARAGRAPH)", typeInput),
                Label.of("Required? (true/false)", requiredInput)
            )
            .build();

        event.replyModal(modal).queue();
    }

    private void handleCreateFormFieldModal(ModalInteractionEvent event, int categoryId, String guildId) {
        String label = Objects.requireNonNull(event.getValue("label")).getAsString();
        var placeholderValue = event.getValue("placeholder");
        String placeholder = placeholderValue != null && !placeholderValue.getAsString().isBlank()
            ? placeholderValue.getAsString() : null;
        String type = Objects.requireNonNull(event.getValue("type")).getAsString().toUpperCase();
        String requiredStr = Objects.requireNonNull(event.getValue("required")).getAsString();
        boolean required = requiredStr.equalsIgnoreCase("true") || requiredStr.equals("1");

        // Validate type
        if (!type.equals("SHORT") && !type.equals("PARAGRAPH")) {
            type = "SHORT";
        }

        int maxLength = type.equals("PARAGRAPH") ? 1000 : 100;

        int fieldId = handler.createTicketFormField(categoryId, label, placeholder, type, 0, maxLength, required);
        if (fieldId > 0) {
            event.reply(t(guildId, "ticket_panels.field_created", label)).setEphemeral(true).queue();
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    private void handleEditFormFieldModal(ModalInteractionEvent event, int fieldId, String guildId) {
        String label = Objects.requireNonNull(event.getValue("label")).getAsString();
        var placeholderValue = event.getValue("placeholder");
        String placeholder = placeholderValue != null && !placeholderValue.getAsString().isBlank()
            ? placeholderValue.getAsString() : null;
        String type = Objects.requireNonNull(event.getValue("type")).getAsString().toUpperCase();
        String requiredStr = Objects.requireNonNull(event.getValue("required")).getAsString();
        boolean required = requiredStr.equalsIgnoreCase("true") || requiredStr.equals("1");

        if (!type.equals("SHORT") && !type.equals("PARAGRAPH")) {
            type = "SHORT";
        }

        int maxLength = type.equals("PARAGRAPH") ? 1000 : 100;

        DatabaseHandler.TicketFormFieldData field = handler.getTicketFormField(fieldId);
        boolean success = handler.updateTicketFormField(fieldId, label, placeholder, type, 0, maxLength, required);

        if (success) {
            event.reply(t(guildId, "ticket_panels.field_updated")).setEphemeral(true).queue();
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    // ==================== HELPER METHODS ====================

    private Color parseColor(String colorStr) {
        if (colorStr == null || colorStr.isBlank()) {
            return new Color(88, 101, 242);
        }
        try {
            if (colorStr.startsWith("#")) {
                return Color.decode(colorStr);
            }
            return Color.decode("#" + colorStr);
        } catch (Exception e) {
            return new Color(88, 101, 242);
        }
    }

    private ButtonStyle getButtonStyle(String style) {
        if (style == null) return ButtonStyle.PRIMARY;
        return switch (style.toUpperCase()) {
            case "SUCCESS", "GREEN" -> ButtonStyle.SUCCESS;
            case "DANGER", "RED" -> ButtonStyle.DANGER;
            case "SECONDARY", "GRAY", "GREY" -> ButtonStyle.SECONDARY;
            default -> ButtonStyle.PRIMARY;
        };
    }

    /**
     * Validates if a string is a valid emoji format for Discord buttons.
     * Accepts Unicode emojis and custom Discord emojis in format <:name:id> or <a:name:id>
     */
    private boolean isValidEmoji(String emoji) {
        if (emoji == null || emoji.isBlank()) {
            return false;
        }

        String trimmed = emoji.trim();

        // Check for custom Discord emoji format: <:name:id> or <a:name:id>
        if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
            // Must match pattern <:name:123456789> or <a:name:123456789>
            return trimmed.matches("<a?:[a-zA-Z0-9_]+:\\d+>");
        }

        // For Unicode emojis, try to create the emoji object and validate
        try {
            Emoji testEmoji = Emoji.fromFormatted(trimmed);
            // If we get here without exception and emoji is not null, it's valid
            return testEmoji != null;
        } catch (Exception e) {
            return false;
        }
    }
}

