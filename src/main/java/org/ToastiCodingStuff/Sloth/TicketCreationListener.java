package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;

import java.awt.*;
import java.util.EnumSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles ticket creation from ticket panels and ticket management actions.
 */
public class TicketCreationListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    // Stores pending ticket data between modals (Subject/Description and custom forms)
    private final Map<String, PendingTicketData> pendingTickets = new ConcurrentHashMap<>();

    private static class PendingTicketData {
        int categoryId;
        String subject;
        String description;
        List<Integer> formIds;  // All form IDs to process
        int currentFormIndex;   // Current form being processed (0-based)
        Map<Integer, String> allFormResponses;  // Collected responses from all forms
        long timestamp;
        String channelId;       // The ticket channel ID (created after first modal)
        int ticketId;           // The ticket ID in database
        String oderId;         // User ID

        PendingTicketData(int categoryId, String subject, String description, List<Integer> formIds) {
            this.categoryId = categoryId;
            this.subject = subject;
            this.description = description;
            this.formIds = formIds;
            this.currentFormIndex = 0;
            this.allFormResponses = new java.util.HashMap<>();
            this.timestamp = System.currentTimeMillis();
            this.channelId = null;
            this.ticketId = -1;
            this.oderId = null;
        }

        int getCurrentFormId() {
            if (formIds == null || currentFormIndex >= formIds.size()) return -1;
            return formIds.get(currentFormIndex);
        }

        boolean hasMoreForms() {
            return formIds != null && currentFormIndex < formIds.size() - 1;
        }

        void advanceToNextForm() {
            currentFormIndex++;
        }

        int getTotalForms() {
            return formIds != null ? formIds.size() : 0;
        }

        boolean isChannelCreated() {
            return channelId != null && ticketId > 0;
        }
    }

    public TicketCreationListener(DatabaseHandler handler) {
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

    // ==================== BUTTON INTERACTION HANDLER ====================

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String customId = event.getComponentId();

        // Handle ticket creation buttons
        if (customId.startsWith("create_ticket_")) {
            int panelId = Integer.parseInt(customId.replace("create_ticket_", ""));
            handleCreateTicketButton(event, panelId);
            return;
        }

        // Handle category-specific ticket creation
        if (customId.startsWith("ticket_cat_")) {
            int categoryId = Integer.parseInt(customId.replace("ticket_cat_", ""));
            handleCreateTicketFromCategory(event, categoryId);
            return;
        }

        // Handle continue form button (opens next custom form modal)
        if (customId.startsWith("ticket_continue_form_")) {
            String remaining = customId.replace("ticket_continue_form_", "");
            String[] parts = remaining.split("_");
            int formId = Integer.parseInt(parts[0]);
            int categoryId = Integer.parseInt(parts[1]);
            handleContinueFormButton(event, formId, categoryId);
            return;
        }

        // Handle close ticket button
        if (customId.startsWith("close_ticket_panel_")) {
            int panelId = Integer.parseInt(customId.replace("close_ticket_panel_", ""));
            handleCloseTicketButton(event, panelId);
            return;
        }

        // Handle delete channel button
        if (customId.equals("delete_ticket_channel")) {
            handleDeleteChannel(event);
        }
    }

    // ==================== MODAL INTERACTION HANDLER ====================

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();

        if (modalId.startsWith("ticket_create_modal_")) {
            int panelId = Integer.parseInt(modalId.replace("ticket_create_modal_", ""));
            handleTicketCreationModal(event, panelId);
        } else if (modalId.startsWith("ticket_cat_modal_") && modalId.contains("_withforms_")) {
            // First modal with Subject/Description, needs to show custom forms as subsequent modals
            String remaining = modalId.replace("ticket_cat_modal_", "");
            String[] parts = remaining.split("_withforms_");
            int categoryId = Integer.parseInt(parts[0]);
            // Parse comma-separated form IDs
            String[] formIdStrings = parts[1].split(",");
            java.util.List<Integer> formIds = new java.util.ArrayList<>();
            for (String formIdStr : formIdStrings) {
                formIds.add(Integer.parseInt(formIdStr));
            }
            handleFirstModalWithCustomForms(event, categoryId, formIds);
        } else if (modalId.startsWith("ticket_cat_modal_")) {
            int categoryId = Integer.parseInt(modalId.replace("ticket_cat_modal_", ""));
            handleCategoryTicketCreationModal(event, categoryId);
        } else if (modalId.startsWith("ticket_custom_form_")) {
            // Handle subsequent modals with custom form fields
            String remaining = modalId.replace("ticket_custom_form_", "");
            int formId = Integer.parseInt(remaining);
            handleCustomFormModal(event, formId);
        }
    }

    // ==================== TICKET CREATION ====================

    /**
     * A max of 0 (or below) means "unlimited" - the setup wizard offers that as an
     * explicit option, so it must not be compared against the open ticket count.
     */
    static boolean hasReachedTicketLimit(int openTickets, int maxTicketsPerUser) {
        return maxTicketsPerUser > 0 && openTickets >= maxTicketsPerUser;
    }

    /**
     * Add the subject and description inputs the panel asks for.
     * <p>
     * Used by every ticket creation path so a panel's requireSubject / requireDescription
     * settings apply whether the ticket is opened from the panel button or from one of its
     * category buttons. When a panel requires neither, an optional subject is offered so
     * the modal is never empty.
     */
    private void addSubjectAndDescriptionInputs(Modal.Builder modalBuilder,
                                                DatabaseHandler.TicketPanelData panel) {
        if (panel.requireSubject) {
            TextInput subjectInput = TextInput.create("subject", TextInputStyle.SHORT)
                .setPlaceholder("Brief description of your issue...")
                .setRequiredRange(5, 100)
                .build();
            modalBuilder.addComponents(Label.of("Subject", subjectInput));
        }

        if (panel.requireDescription) {
            TextInput descInput = TextInput.create("description", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Please provide as much detail as possible...")
                .setRequiredRange(10, 1000)
                .build();
            modalBuilder.addComponents(Label.of("Description", descInput));
        }

        if (!panel.requireSubject && !panel.requireDescription) {
            TextInput subjectInput = TextInput.create("subject", TextInputStyle.SHORT)
                .setPlaceholder("Brief description (optional)")
                .setRequired(false)
                .build();
            modalBuilder.addComponents(Label.of("Subject (optional)", subjectInput));
        }
    }

    private void handleCreateTicketButton(ButtonInteractionEvent event, int panelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);

        if (panel == null) {
            event.reply(t(guildId, "ticket_panels.not_found")).setEphemeral(true).queue();
            return;
        }

        // Check max tickets per user
        int openTickets = handler.getUserOpenTicketCount(guildId, event.getUser().getId(), panelId);
        if (hasReachedTicketLimit(openTickets, panel.maxTicketsPerUser)) {
            event.reply(t(guildId, "tickets.max_tickets_reached", panel.maxTicketsPerUser)).setEphemeral(true).queue();
            return;
        }

        // Show modal for ticket creation
        Modal.Builder modalBuilder = Modal.create("ticket_create_modal_" + panelId, "Create Ticket - " + panel.name);
        addSubjectAndDescriptionInputs(modalBuilder, panel);

        event.replyModal(modalBuilder.build()).queue();
    }

    private void handleTicketCreationModal(ModalInteractionEvent event, int panelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String userId = event.getUser().getId();

        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
        if (panel == null) {
            event.reply(t(guildId, "ticket_panels.not_found")).setEphemeral(true).queue();
            return;
        }

        if (panel.categoryId == null) {
            event.reply(t(guildId, "ticket_panels.category_not_set")).setEphemeral(true).queue();
            return;
        }

        Category ticketCategory = event.getGuild().getCategoryById(panel.categoryId);
        if (ticketCategory == null) {
            event.reply(t(guildId, "ticket_panels.category_not_found")).setEphemeral(true).queue();
            return;
        }

        var subjectValue = event.getValue("subject");
        String subject = (subjectValue != null && !subjectValue.getAsString().isEmpty())
            ? subjectValue.getAsString()
            : "Support Ticket";
        var descriptionValue = event.getValue("description");
        String description = (descriptionValue != null && !descriptionValue.getAsString().isEmpty())
            ? descriptionValue.getAsString()
            : "";

        // Create ticket channel
        String channelName = "ticket-" + panel.name.toLowerCase().replaceAll("[^a-z0-9]", "") + "-"
            + event.getUser().getName().toLowerCase().replaceAll("[^a-z0-9]", "");

        ticketCategory.createTextChannel(channelName)
            .addPermissionOverride(event.getGuild().getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
            .addPermissionOverride(Objects.requireNonNull(event.getMember()),
                EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null)
            .queue(channel -> {
                // Add support role permissions if configured
                if (panel.supportRoleId != null) {
                    Role supportRole = event.getGuild().getRoleById(panel.supportRoleId);
                    if (supportRole != null) {
                        channel.getManager().putPermissionOverride(supportRole,
                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MANAGE_CHANNEL),
                            null).queue();
                    }
                }

                // Create ticket in database
                int ticketId = handler.createTicketWithPanel(guildId, userId, channel.getId(), panelId,
                    subject, "MEDIUM", event.getUser().getEffectiveName(),
                    event.getUser().getDiscriminator(), event.getUser().getAvatarUrl());

                if (ticketId > 0) {
                    // Update channel name to include ticket ID
                    String newChannelName = "ticket-" + ticketId + "-" + event.getUser().getName().toLowerCase().replaceAll("[^a-z0-9]", "");
                    channel.getManager().setName(newChannelName).queue();

                    // Update statistics
                    handler.incrementTicketsCreated(guildId);
                    handler.incrementUserTicketsCreated(guildId, userId);

                    // Send audit log entry
                    handler.sendAuditLogEntry(event.getGuild(), "TICKET_CREATED",
                        "Ticket #" + ticketId + " - " + subject + " (Panel: " + panel.name + ")",
                        event.getMember(), null, "");

                    // Send welcome message in ticket channel
                    EmbedBuilder welcomeEmbed = new EmbedBuilder()
                        .setTitle("🎫 Ticket #" + ticketId + " - " + subject)
                        .setDescription("**" + panel.name + "**\n\n" +
                            (description.isEmpty() ? "" : "**Description:**\n" + handler.processLinebreaks(description) + "\n\n") +
                            handler.processLinebreaks(panel.welcomeMessage))
                        .addField("👤 Created by", event.getUser().getAsMention(), true)
                        .addField("📅 Created", "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true)
                        .setColor(parseColor(panel.embedColor))
                        .setFooter("Ticket ID: " + ticketId + " • " + panel.name);

                    Button closeButton = Button.danger("close_ticket_panel_" + panelId, t(guildId, "tickets.close_button"));

                    // Ping support role if configured
                    String pingMessage = event.getUser().getAsMention() + " " + panel.welcomeMessage.split("\n")[0];
                    if (panel.pingRoleId != null) {
                        Role pingRole = event.getGuild().getRoleById(panel.pingRoleId);
                        if (pingRole != null) {
                            pingMessage = pingRole.getAsMention() + " " + pingMessage;
                        }
                    }

                    channel.sendMessage(pingMessage)
                        .addEmbeds(welcomeEmbed.build())
                        .setComponents(ActionRow.of(closeButton))
                        .queue();

                    event.reply(t(guildId, "tickets.created", channel.getAsMention())).setEphemeral(true).queue();
                } else {
                    channel.delete().queue();
                    event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
                }
            },
            error -> event.reply(t(guildId, "general.error")).setEphemeral(true).queue());
    }

    // ==================== TICKET CLOSING ====================

    private void handleCloseTicketButton(ButtonInteractionEvent event, int panelId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        TextChannel channel = event.getChannel().asTextChannel();
        Integer ticketId = handler.getTicketIdByChannelId(channel.getId());

        if (ticketId == null) {
            event.reply(t(guildId, "tickets.not_found")).setEphemeral(true).queue();
            return;
        }

        boolean success = handler.closeTicket(ticketId, event.getUser().getId(), "Closed via button");

        if (success) {
            // Update statistics
            handler.incrementTicketsClosed(guildId);
            handler.incrementUserTicketsClosed(guildId, event.getUser().getId());

            // Send audit log entry
            handler.sendAuditLogEntry(event.getGuild(), "TICKET_CLOSED",
                "Ticket #" + ticketId, null, event.getMember(), "Closed via button");

            DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
            String panelName = panel != null ? panel.name : "Unknown";

            EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔒 Ticket Closed")
                .setDescription("This ticket has been closed by " + event.getUser().getAsMention())
                .addField("Closed at", "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true)
                .setColor(Color.RED)
                .setFooter(panelName);

            Button deleteButton = Button.danger("delete_ticket_channel", "🗑️ Delete Channel");

            event.replyEmbeds(embed.build())
                .setComponents(ActionRow.of(deleteButton))
                .queue();

            channel.getManager().setName("closed-" + channel.getName()).queue();
        } else {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
        }
    }

    private void handleDeleteChannel(ButtonInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        TextChannel channel = event.getChannel().asTextChannel();

        // Check if this is a closed ticket channel
        if (!channel.getName().startsWith("closed-")) {
            event.reply(t(guildId, "tickets.cannot_delete")).setEphemeral(true).queue();
            return;
        }

        // Check permissions
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_CHANNEL)) {
            // Check if user has support role for any panel
            Integer panelId = handler.getTicketPanelIdByChannel(channel.getId());
            if (panelId != null) {
                DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
                if (panel != null && panel.supportRoleId != null) {
                    boolean hasRole = event.getMember().getRoles().stream()
                        .anyMatch(role -> role.getId().equals(panel.supportRoleId));
                    if (!hasRole) {
                        event.reply(t(guildId, "general.permission_denied")).setEphemeral(true).queue();
                        return;
                    }
                }
            } else {
                event.reply(t(guildId, "general.permission_denied")).setEphemeral(true).queue();
                return;
            }
        }

        event.reply("🗑️ Deleting channel...").setEphemeral(true).queue(
            success -> channel.delete()
                .reason("Ticket channel deleted by " + event.getUser().getEffectiveName())
                .queue(),
            error -> event.reply(t(guildId, "general.error")).setEphemeral(true).queue()
        );
    }

    // ==================== CONTINUE FORM BUTTON HANDLER ====================

    /**
     * Handles the "Continue" button that opens the next custom form modal.
     */
    private void handleContinueFormButton(ButtonInteractionEvent event, int formId, int categoryId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String oderId = event.getUser().getId();
        String pendingKey = oderId + "_" + guildId;

        // Get pending ticket data
        PendingTicketData pendingData = pendingTickets.get(pendingKey);
        if (pendingData == null) {
            event.reply(t(guildId, "tickets.session_expired")).setEphemeral(true).queue();
            return;
        }

        // Check if data is still valid (5 minute timeout)
        if (System.currentTimeMillis() - pendingData.timestamp > 300000) {
            pendingTickets.remove(pendingKey);
            // If channel was created, delete it
            if (pendingData.isChannelCreated()) {
                TextChannel channel = event.getGuild().getTextChannelById(pendingData.channelId);
                if (channel != null) {
                    channel.delete().reason("Form submission timeout").queue();
                }
            }
            event.reply(t(guildId, "tickets.session_expired")).setEphemeral(true).queue();
            return;
        }

        // Get the form and its fields
        DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);
        if (form == null) {
            event.reply(t(guildId, "ticket_panels.form_not_found")).setEphemeral(true).queue();
            return;
        }

        java.util.List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(formId);
        if (fields.isEmpty()) {
            // No fields in this form, move to next
            pendingData.advanceToNextForm();
            // Check if there are more forms
            if (pendingData.getCurrentFormId() == -1) {
                pendingTickets.remove(pendingKey);
                finalizeTicketFromButton(event, guildId, pendingData);
            } else {
                // Show continue button for next form
                showContinueButtonForNextForm(event, guildId, pendingData);
            }
            return;
        }

        // Build modal with custom form fields
        String formTitle = form.name != null ? form.name : "Additional Information";
        int currentStep = pendingData.currentFormIndex + 2;
        int totalSteps = pendingData.getTotalForms() + 1;

        Modal.Builder modalBuilder = Modal.create("ticket_custom_form_" + formId,
            formTitle + " (" + currentStep + "/" + totalSteps + ")");

        for (DatabaseHandler.TicketFormFieldData field : fields) {
            TextInputStyle style = field.fieldType.equals("PARAGRAPH") ? TextInputStyle.PARAGRAPH : TextInputStyle.SHORT;
            TextInput.Builder inputBuilder = TextInput.create("field_" + field.id, style)
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

    /**
     * Shows a continue button for the next form (from button context).
     */
    private void showContinueButtonForNextForm(ButtonInteractionEvent event, String guildId, PendingTicketData pendingData) {
        int currentFormId = pendingData.getCurrentFormId();
        DatabaseHandler.TicketFormData form = handler.getTicketForm(currentFormId);

        int currentStep = pendingData.currentFormIndex + 2;
        int totalSteps = pendingData.getTotalForms() + 1;
        String formName = form != null && form.name != null ? form.name : "Additional Information";

        EmbedBuilder continueEmbed = new EmbedBuilder()
            .setTitle("📋 " + t(guildId, "tickets.form_progress") + " (" + currentStep + "/" + totalSteps + ")")
            .setDescription(t(guildId, "tickets.continue_form_desc", formName))
            .setColor(new Color(88, 101, 242))
            .setFooter(t(guildId, "tickets.click_to_continue"));

        Button continueButton = Button.primary("ticket_continue_form_" + currentFormId + "_" + pendingData.categoryId,
            "📝 " + t(guildId, "tickets.continue_button"));

        event.replyEmbeds(continueEmbed.build())
            .setComponents(ActionRow.of(continueButton))
            .setEphemeral(true)
            .queue();
    }

    /**
     * Finalizes the ticket from a button context (when no more forms).
     */
    private void finalizeTicketFromButton(ButtonInteractionEvent event, String guildId, PendingTicketData pendingData) {
        if (!pendingData.isChannelCreated()) {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
            return;
        }

        TextChannel channel = event.getGuild().getTextChannelById(pendingData.channelId);
        if (channel == null) {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
            return;
        }

        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(pendingData.categoryId);
        DatabaseHandler.TicketPanelData panel = category != null ? handler.getTicketPanel(category.panelId) : null;

        if (category == null || panel == null) {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
            return;
        }

        // Collect all form fields for saving responses
        java.util.List<DatabaseHandler.TicketFormFieldData> allFormFields = new java.util.ArrayList<>();
        if (pendingData.formIds != null) {
            for (int fId : pendingData.formIds) {
                allFormFields.addAll(handler.getTicketFormFieldsByFormId(fId));
            }
        }

        // Save form responses to database
        if (!pendingData.allFormResponses.isEmpty()) {
            handler.saveTicketFormResponses(pendingData.ticketId, allFormFields, pendingData.allFormResponses);
        }

        // Grant write permissions to the user
        channel.upsertPermissionOverride(Objects.requireNonNull(event.getMember()))
            .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
            .queue();

        // Build form responses string for welcome embed
        StringBuilder formResponses = new StringBuilder();
        if (!pendingData.description.isEmpty()) {
            formResponses.append("**").append(t(guildId, "tickets.description")).append(":**\n")
                .append(pendingData.description).append("\n\n");
        }
        for (DatabaseHandler.TicketFormFieldData field : allFormFields) {
            String response = pendingData.allFormResponses.get(field.id);
            if (response != null && !response.isBlank()) {
                formResponses.append("**").append(field.label).append(":**\n")
                    .append(response).append("\n\n");
            }
        }

        // Build welcome embed
        EmbedBuilder welcomeEmbed = new EmbedBuilder();
        welcomeEmbed.setTitle("🎫 " + category.name + " - Ticket #" + pendingData.ticketId);
        welcomeEmbed.setColor(parseColor(panel.embedColor));
        welcomeEmbed.addField("👤 " + t(guildId, "tickets.created_by"), event.getUser().getAsMention(), true);
        welcomeEmbed.addField("📂 " + t(guildId, "tickets.category"), category.name, true);
        welcomeEmbed.addField("📋 " + t(guildId, "tickets.subject"), pendingData.subject, false);

        if (!formResponses.isEmpty()) {
            String responses = formResponses.toString();
            if (responses.length() > 1024) {
                responses = responses.substring(0, 1020) + "...";
            }
            welcomeEmbed.addField("📝 " + t(guildId, "tickets.form_responses"), responses, false);
        }

        String welcomeMessage = category.welcomeMessage != null ? category.welcomeMessage : panel.welcomeMessage;
        welcomeEmbed.setDescription("✅ " + t(guildId, "tickets.forms_completed") + "\n\n" + welcomeMessage);
        welcomeEmbed.setTimestamp(java.time.Instant.now());

        // Ping role if configured
        String pingContent = "";
        if (panel.pingRoleId != null) {
            Role pingRole = event.getGuild().getRoleById(panel.pingRoleId);
            if (pingRole != null) {
                pingContent = pingRole.getAsMention() + " ";
            }
        }

        channel.sendMessage(pingContent + event.getUser().getAsMention() + " " + t(guildId, "tickets.you_can_now_chat"))
            .setEmbeds(welcomeEmbed.build())
            .setComponents(ActionRow.of(
                Button.danger("close_ticket_panel_" + category.panelId, t(guildId, "tickets.close_button"))
            ))
            .queue();

        // Send audit log entry
        handler.sendAuditLogEntry(event.getGuild(), "TICKET_CREATED",
            "Ticket #" + pendingData.ticketId + " - " + pendingData.subject + " (Panel: " + panel.name + ")",
            event.getMember(), null, "");

        event.reply(t(guildId, "tickets.created_success", channel.getAsMention()))
            .setEphemeral(true)
            .queue();
    }

    // ==================== CATEGORY-BASED TICKET CREATION ====================

    private void handleCreateTicketFromCategory(ButtonInteractionEvent event, int categoryId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(categoryId);

        if (category == null) {
            event.reply(t(guildId, "ticket_panels.category_not_found")).setEphemeral(true).queue();
            return;
        }

        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(category.panelId);
        if (panel == null) {
            event.reply(t(guildId, "ticket_panels.not_found")).setEphemeral(true).queue();
            return;
        }

        // Check max tickets per user
        int openTickets = handler.getUserOpenTicketCount(guildId, event.getUser().getId(), category.panelId);
        if (hasReachedTicketLimit(openTickets, panel.maxTicketsPerUser)) {
            event.reply(t(guildId, "tickets.max_tickets_reached", panel.maxTicketsPerUser)).setEphemeral(true).queue();
            return;
        }

        // Check for custom forms (new system) - these will be shown as modals AFTER subject/description
        java.util.List<DatabaseHandler.TicketFormData> customForms = handler.getTicketForms(categoryId);

        // Get legacy custom form fields for this category
        java.util.List<DatabaseHandler.TicketFormFieldData> legacyFormFields = handler.getTicketFormFields(categoryId);

        // If we have custom forms (new system), always show Subject/Description first, then custom forms after
        if (!customForms.isEmpty()) {
            // Collect all form IDs that have fields
            java.util.List<Integer> formIdsWithFields = new java.util.ArrayList<>();
            for (DatabaseHandler.TicketFormData form : customForms) {
                java.util.List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(form.id);
                if (!fields.isEmpty()) {
                    formIdsWithFields.add(form.id);
                }
            }

            // If we have forms with fields, show Subject/Description modal first
            if (!formIdsWithFields.isEmpty()) {
                // Create a comma-separated list of form IDs for the modal ID
                StringBuilder formIdsStr = new StringBuilder();
                for (int i = 0; i < formIdsWithFields.size(); i++) {
                    if (i > 0) formIdsStr.append(",");
                    formIdsStr.append(formIdsWithFields.get(i));
                }

                int totalSteps = formIdsWithFields.size() + 1; // +1 for Subject/Description modal

                // Show default modal first (Subject + Description)
                Modal.Builder modalBuilder = Modal.create("ticket_cat_modal_" + categoryId + "_withforms_" + formIdsStr,
                    "Create Ticket - " + category.name + " (1/" + totalSteps + ")");
                addSubjectAndDescriptionInputs(modalBuilder, panel);

                event.replyModal(modalBuilder.build()).queue();
                return;
            }
        }

        // Legacy behavior: if we have legacy form fields, use them directly
        if (!legacyFormFields.isEmpty()) {
            // Use custom form fields (legacy system)
            Modal.Builder modalBuilder = Modal.create("ticket_cat_modal_" + categoryId, "Create Ticket - " + category.name);

            for (DatabaseHandler.TicketFormFieldData field : legacyFormFields) {
                TextInputStyle style = field.fieldType.equals("PARAGRAPH") ? TextInputStyle.PARAGRAPH : TextInputStyle.SHORT;
                TextInput.Builder inputBuilder = TextInput.create("field_" + field.id, style)
                    .setRequired(field.required)
                    .setMinLength(field.minLength)
                    .setMaxLength(field.maxLength);

                if (field.placeholder != null && !field.placeholder.isBlank()) {
                    inputBuilder.setPlaceholder(field.placeholder);
                }

                modalBuilder.addComponents(Label.of(field.label, inputBuilder.build()));
            }

            event.replyModal(modalBuilder.build()).queue();
        } else {
            // No custom form fields - use default modal
            Modal.Builder modalBuilder = Modal.create("ticket_cat_modal_" + categoryId, "Create Ticket - " + category.name);
            addSubjectAndDescriptionInputs(modalBuilder, panel);

            event.replyModal(modalBuilder.build()).queue();
        }
    }

    private void handleCategoryTicketCreationModal(ModalInteractionEvent event, int categoryId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String userId = event.getUser().getId();

        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(categoryId);
        if (category == null) {
            event.reply(t(guildId, "ticket_panels.category_not_found")).setEphemeral(true).queue();
            return;
        }

        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(category.panelId);
        if (panel == null) {
            event.reply(t(guildId, "ticket_panels.not_found")).setEphemeral(true).queue();
            return;
        }

        // Use category's categoryId if set, otherwise use panel's categoryId
        String discordCategoryId = category.categoryId != null ? category.categoryId : panel.categoryId;
        if (discordCategoryId == null) {
            event.reply(t(guildId, "ticket_panels.category_not_set")).setEphemeral(true).queue();
            return;
        }

        Category ticketCategory = event.getGuild().getCategoryById(discordCategoryId);
        if (ticketCategory == null) {
            event.reply(t(guildId, "ticket_panels.category_not_found")).setEphemeral(true).queue();
            return;
        }

        // Get form field responses
        java.util.List<DatabaseHandler.TicketFormFieldData> formFields = handler.getTicketFormFields(categoryId);
        StringBuilder formResponses = new StringBuilder();
        String subject = category.name + " Ticket";
        java.util.Map<Integer, String> formResponsesMap = new java.util.HashMap<>();

        if (formFields.isEmpty()) {
            // Default fields
            var subjectValue = event.getValue("subject");
            subject = (subjectValue != null && !subjectValue.getAsString().isEmpty())
                ? subjectValue.getAsString() : subject;
            var descriptionValue = event.getValue("description");
            if (descriptionValue != null && !descriptionValue.getAsString().isEmpty()) {
                formResponses.append("**Description:**\n").append(descriptionValue.getAsString()).append("\n");
            }
        } else {
            // Custom form fields
            for (DatabaseHandler.TicketFormFieldData field : formFields) {
                var value = event.getValue("field_" + field.id);
                if (value != null && !value.getAsString().isBlank()) {
                    formResponses.append("**").append(field.label).append(":**\n")
                        .append(value.getAsString()).append("\n\n");
                    formResponsesMap.put(field.id, value.getAsString());
                    // Use first field as subject if it's short
                    if (field.position == 0 && field.fieldType.equals("SHORT") && value.getAsString().length() <= 100) {
                        subject = value.getAsString();
                    }
                }
            }
        }

        // Create ticket channel
        String channelName = "ticket-" + category.name.toLowerCase().replaceAll("[^a-z0-9]", "") + "-"
            + event.getUser().getName().toLowerCase().replaceAll("[^a-z0-9]", "");

        final String finalSubject = subject;
        final String finalFormResponses = formResponses.toString();
        final java.util.Map<Integer, String> finalFormResponsesMap = formResponsesMap;
        final java.util.List<DatabaseHandler.TicketFormFieldData> finalFormFields = formFields;

        ticketCategory.createTextChannel(channelName)
            .addPermissionOverride(event.getGuild().getPublicRole(), null, java.util.EnumSet.of(Permission.VIEW_CHANNEL))
            .addPermissionOverride(Objects.requireNonNull(event.getMember()),
                java.util.EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null)
            .queue(channel -> {
                // Add support role permissions if configured
                if (panel.supportRoleId != null) {
                    Role supportRole = event.getGuild().getRoleById(panel.supportRoleId);
                    if (supportRole != null) {
                        channel.getManager().putPermissionOverride(supportRole,
                            java.util.EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MANAGE_CHANNEL),
                            null).queue();
                    }
                }

                // Create ticket in database
                int ticketId = handler.createTicketWithPanel(guildId, userId, channel.getId(), category.panelId,
                    finalSubject, "MEDIUM", event.getUser().getEffectiveName(),
                    event.getUser().getDiscriminator(), event.getUser().getAvatarUrl());

                if (ticketId > 0) {
                    // Save form responses to database
                    if (!finalFormResponsesMap.isEmpty()) {
                        handler.saveTicketFormResponses(ticketId, finalFormFields, finalFormResponsesMap);
                    }

                    String newChannelName = "ticket-" + ticketId + "-" + event.getUser().getName().toLowerCase().replaceAll("[^a-z0-9]", "");
                    channel.getManager().setName(newChannelName).queue();

                    // Build welcome embed with form responses
                    EmbedBuilder welcomeEmbed = new EmbedBuilder();
                    welcomeEmbed.setTitle("🎫 " + category.name + " - Ticket #" + ticketId);
                    welcomeEmbed.setColor(parseColor(panel.embedColor));
                    welcomeEmbed.addField("👤 " + t(guildId, "tickets.created_by"), event.getUser().getAsMention(), true);
                    welcomeEmbed.addField("📂 " + t(guildId, "tickets.category"), category.name, true);
                    welcomeEmbed.addField("📋 " + t(guildId, "tickets.subject"), finalSubject, false);

                    if (!finalFormResponses.isBlank()) {
                        String responses = finalFormResponses.length() > 1024
                            ? finalFormResponses.substring(0, 1020) + "..."
                            : finalFormResponses;
                        welcomeEmbed.addField("📝 " + t(guildId, "tickets.form_responses"), responses, false);
                    }

                    String welcomeMessage = category.welcomeMessage != null ? category.welcomeMessage : panel.welcomeMessage;
                    welcomeEmbed.setDescription(welcomeMessage);
                    welcomeEmbed.setTimestamp(java.time.Instant.now());

                    // Ping role if configured
                    String pingContent = "";
                    if (panel.pingRoleId != null) {
                        Role pingRole = event.getGuild().getRoleById(panel.pingRoleId);
                        if (pingRole != null) {
                            pingContent = pingRole.getAsMention() + " ";
                        }
                    }

                    channel.sendMessage(pingContent + event.getUser().getAsMention())
                        .setEmbeds(welcomeEmbed.build())
                        .setComponents(ActionRow.of(
                            Button.danger("close_ticket_panel_" + category.panelId, t(guildId, "tickets.close_button"))
                        ))
                        .queue();

                    event.reply(t(guildId, "tickets.created_success", channel.getAsMention()))
                        .setEphemeral(true)
                        .queue();
                } else {
                    channel.delete().queue();
                    event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
                }
            }, error -> {
                event.reply(t(guildId, "general.error") + ": " + error.getMessage()).setEphemeral(true).queue();
            });
    }

    // ==================== HELPER METHODS ====================

    /**
     * Handles the first modal (Subject/Description) when custom forms exist.
     * Stores the data and shows the first custom form modal.
     */
    private void handleFirstModalWithCustomForms(ModalInteractionEvent event, int categoryId, java.util.List<Integer> formIds) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String oderId = event.getUser().getId();

        // Get subject and description from first modal
        var subjectValue = event.getValue("subject");
        String subject = (subjectValue != null && !subjectValue.getAsString().isEmpty())
            ? subjectValue.getAsString() : "Support Ticket";
        var descriptionValue = event.getValue("description");
        String description = (descriptionValue != null && !descriptionValue.getAsString().isEmpty())
            ? descriptionValue.getAsString() : "";

        // Store pending ticket data with all form IDs
        String pendingKey = oderId + "_" + guildId;
        PendingTicketData pendingData = new PendingTicketData(categoryId, subject, description, formIds);
        pendingData.oderId = oderId;
        pendingTickets.put(pendingKey, pendingData);

        // Get category and panel info
        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(categoryId);
        if (category == null) {
            event.reply(t(guildId, "ticket_panels.category_not_found")).setEphemeral(true).queue();
            pendingTickets.remove(pendingKey);
            return;
        }

        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(category.panelId);
        if (panel == null) {
            event.reply(t(guildId, "ticket_panels.not_found")).setEphemeral(true).queue();
            pendingTickets.remove(pendingKey);
            return;
        }

        String discordCategoryId = category.categoryId != null ? category.categoryId : panel.categoryId;
        if (discordCategoryId == null) {
            event.reply(t(guildId, "ticket_panels.category_not_set")).setEphemeral(true).queue();
            pendingTickets.remove(pendingKey);
            return;
        }

        Category ticketCategory = event.getGuild().getCategoryById(discordCategoryId);
        if (ticketCategory == null) {
            event.reply(t(guildId, "ticket_panels.category_not_found")).setEphemeral(true).queue();
            pendingTickets.remove(pendingKey);
            return;
        }

        // Create ticket channel immediately (WITHOUT write permissions for user - only view)
        String channelName = "ticket-" + category.name.toLowerCase().replaceAll("[^a-z0-9]", "") + "-"
            + event.getUser().getName().toLowerCase().replaceAll("[^a-z0-9]", "");

        int totalSteps = formIds.size() + 1;
        final String finalSubject = subject;
        final String finalDescription = description;

        ticketCategory.createTextChannel(channelName)
            .addPermissionOverride(event.getGuild().getPublicRole(), null, java.util.EnumSet.of(Permission.VIEW_CHANNEL))
            // User can VIEW but NOT SEND messages until all forms are completed
            .addPermissionOverride(Objects.requireNonNull(event.getMember()),
                java.util.EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY),
                java.util.EnumSet.of(Permission.MESSAGE_SEND))
            .queue(channel -> {
                // Add support role permissions if configured
                if (panel.supportRoleId != null) {
                    Role supportRole = event.getGuild().getRoleById(panel.supportRoleId);
                    if (supportRole != null) {
                        channel.getManager().putPermissionOverride(supportRole,
                            java.util.EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MANAGE_CHANNEL),
                            null).queue();
                    }
                }

                // Create ticket in database
                int ticketId = handler.createTicketWithPanel(guildId, oderId, channel.getId(), category.panelId,
                    finalSubject, "MEDIUM", event.getUser().getEffectiveName(),
                    event.getUser().getDiscriminator(), event.getUser().getAvatarUrl());

                if (ticketId > 0) {
                    // Update channel name with ticket ID
                    String newChannelName = "ticket-" + ticketId + "-" + event.getUser().getName().toLowerCase().replaceAll("[^a-z0-9]", "");
                    channel.getManager().setName(newChannelName).queue();

                    // Store channel info in pending data
                    pendingData.channelId = channel.getId();
                    pendingData.ticketId = ticketId;

                    // Send initial checkpoint message
                    EmbedBuilder checkpointEmbed = new EmbedBuilder()
                        .setTitle("📋 " + t(guildId, "tickets.form_progress"))
                        .setDescription(t(guildId, "tickets.form_in_progress", event.getUser().getAsMention()))
                        .addField("✅ " + t(guildId, "tickets.step_completed", 1, totalSteps),
                            "**" + t(guildId, "tickets.subject") + ":** " + finalSubject +
                            (finalDescription.isEmpty() ? "" : "\n**" + t(guildId, "tickets.description") + ":** " +
                            (finalDescription.length() > 100 ? finalDescription.substring(0, 100) + "..." : finalDescription)), false)
                        .setColor(new Color(255, 193, 7)) // Yellow/warning color for in-progress
                        .setFooter(t(guildId, "tickets.waiting_for_forms", totalSteps - 1));

                    channel.sendMessageEmbeds(checkpointEmbed.build()).queue();

                    // Update statistics
                    handler.incrementTicketsCreated(guildId);
                    handler.incrementUserTicketsCreated(guildId, oderId);

                    // Show the first custom form modal
                    showNextCustomFormModalDirect(event, guildId, pendingKey);
                } else {
                    channel.delete().queue();
                    pendingTickets.remove(pendingKey);
                    event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
                }
            }, error -> {
                pendingTickets.remove(pendingKey);
                event.reply(t(guildId, "general.error") + ": " + error.getMessage()).setEphemeral(true).queue();
            });
    }

    /**
     * Shows the next custom form modal directly (called from channel creation callback).
     */
    private void showNextCustomFormModalDirect(ModalInteractionEvent event, String guildId, String pendingKey) {
        PendingTicketData pendingData = pendingTickets.get(pendingKey);
        if (pendingData == null) {
            event.reply(t(guildId, "tickets.session_expired")).setEphemeral(true).queue();
            return;
        }

        int currentFormId = pendingData.getCurrentFormId();
        if (currentFormId == -1) {
            // No forms, finalize the ticket
            pendingTickets.remove(pendingKey);
            finalizeTicket(event, guildId, pendingData);
            return;
        }

        DatabaseHandler.TicketFormData form = handler.getTicketForm(currentFormId);
        if (form == null) {
            event.reply(t(guildId, "ticket_panels.form_not_found")).setEphemeral(true).queue();
            pendingTickets.remove(pendingKey);
            return;
        }

        java.util.List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(currentFormId);
        if (fields.isEmpty()) {
            // Skip this form, move to next
            pendingData.advanceToNextForm();
            showNextCustomFormModalDirect(event, guildId, pendingKey);
            return;
        }

        // Since we can't reply with a modal from a ModalInteractionEvent,
        // we send a message with a button that will open the next modal
        int currentStep = pendingData.currentFormIndex + 2; // +1 for 1-based, +1 for Subject/Description modal
        int totalSteps = pendingData.getTotalForms() + 1; // +1 for Subject/Description modal
        String formName = form.name != null ? form.name : "Additional Information";

        EmbedBuilder continueEmbed = new EmbedBuilder()
            .setTitle("📋 " + t(guildId, "tickets.form_progress") + " (" + currentStep + "/" + totalSteps + ")")
            .setDescription(t(guildId, "tickets.continue_form_desc", formName))
            .setColor(new Color(88, 101, 242))
            .setFooter(t(guildId, "tickets.click_to_continue"));

        Button continueButton = Button.primary("ticket_continue_form_" + currentFormId + "_" + pendingData.categoryId,
            "📝 " + t(guildId, "tickets.continue_button"));

        event.replyEmbeds(continueEmbed.build())
            .setComponents(ActionRow.of(continueButton))
            .setEphemeral(true)
            .queue();
    }

    /**
     * Shows the next custom form modal in the sequence (after a modal was submitted).
     * Since we can't reply with a modal from ModalInteractionEvent, we send a button instead.
     */
    private void showNextCustomFormModal(ModalInteractionEvent event, String guildId, String pendingKey) {
        PendingTicketData pendingData = pendingTickets.get(pendingKey);
        if (pendingData == null) {
            event.reply(t(guildId, "tickets.session_expired")).setEphemeral(true).queue();
            return;
        }

        int currentFormId = pendingData.getCurrentFormId();
        if (currentFormId == -1) {
            // No more forms, finalize the ticket
            pendingTickets.remove(pendingKey);
            finalizeTicket(event, guildId, pendingData);
            return;
        }

        DatabaseHandler.TicketFormData form = handler.getTicketForm(currentFormId);
        if (form == null) {
            event.reply(t(guildId, "ticket_panels.form_not_found")).setEphemeral(true).queue();
            pendingTickets.remove(pendingKey);
            return;
        }

        java.util.List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(currentFormId);
        if (fields.isEmpty()) {
            // Skip this form, move to next
            pendingData.advanceToNextForm();
            showNextCustomFormModal(event, guildId, pendingKey);
            return;
        }

        // Since we can't reply with a modal from a ModalInteractionEvent,
        // we send a message with a button that will open the next modal
        int currentStep = pendingData.currentFormIndex + 2; // +1 for 1-based, +1 for Subject/Description modal
        int totalSteps = pendingData.getTotalForms() + 1; // +1 for Subject/Description modal
        String formName = form.name != null ? form.name : "Additional Information";

        EmbedBuilder continueEmbed = new EmbedBuilder()
            .setTitle("📋 " + t(guildId, "tickets.form_progress") + " (" + currentStep + "/" + totalSteps + ")")
            .setDescription(t(guildId, "tickets.continue_form_desc", formName))
            .setColor(new Color(88, 101, 242))
            .setFooter(t(guildId, "tickets.click_to_continue"));

        Button continueButton = Button.primary("ticket_continue_form_" + currentFormId + "_" + pendingData.categoryId,
            "📝 " + t(guildId, "tickets.continue_button"));

        event.replyEmbeds(continueEmbed.build())
            .setComponents(ActionRow.of(continueButton))
            .setEphemeral(true)
            .queue();
    }

    /**
     * Handles custom form modals (second, third, etc.).
     * Collects responses, posts checkpoint, and either shows next form or finalizes the ticket.
     */
    private void handleCustomFormModal(ModalInteractionEvent event, int formId) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String oderId = event.getUser().getId();
        String pendingKey = oderId + "_" + guildId;

        // Get pending ticket data (don't remove yet, we might need it for more forms)
        PendingTicketData pendingData = pendingTickets.get(pendingKey);
        if (pendingData == null) {
            // Timeout or invalid state
            event.reply(t(guildId, "tickets.session_expired")).setEphemeral(true).queue();
            return;
        }

        // Check if data is still valid (5 minute timeout)
        if (System.currentTimeMillis() - pendingData.timestamp > 300000) {
            pendingTickets.remove(pendingKey);
            // If channel was created, delete it
            if (pendingData.isChannelCreated()) {
                TextChannel channel = event.getGuild().getTextChannelById(pendingData.channelId);
                if (channel != null) {
                    channel.delete().reason("Form submission timeout").queue();
                }
            }
            event.reply(t(guildId, "tickets.session_expired")).setEphemeral(true).queue();
            return;
        }

        // Get form and its fields for this form
        DatabaseHandler.TicketFormData form = handler.getTicketForm(formId);
        java.util.List<DatabaseHandler.TicketFormFieldData> fields = handler.getTicketFormFieldsByFormId(formId);

        // Collect responses for this form
        StringBuilder formResponsesSummary = new StringBuilder();
        for (DatabaseHandler.TicketFormFieldData field : fields) {
            var value = event.getValue("field_" + field.id);
            if (value != null && !value.getAsString().isBlank()) {
                pendingData.allFormResponses.put(field.id, value.getAsString());
                String responseText = value.getAsString();
                if (responseText.length() > 100) {
                    responseText = responseText.substring(0, 100) + "...";
                }
                formResponsesSummary.append("**").append(field.label).append(":** ").append(responseText).append("\n");
            }
        }

        // Post checkpoint in the ticket channel
        if (pendingData.isChannelCreated()) {
            TextChannel channel = event.getGuild().getTextChannelById(pendingData.channelId);
            if (channel != null) {
                int currentStep = pendingData.currentFormIndex + 2; // +1 for 1-based, +1 for Subject/Description
                int totalSteps = pendingData.getTotalForms() + 1;
                String formName = form != null ? form.name : "Form " + currentStep;

                EmbedBuilder checkpointEmbed = new EmbedBuilder()
                    .setTitle("✅ " + t(guildId, "tickets.step_completed", currentStep, totalSteps))
                    .setDescription("**" + formName + "**")
                    .setColor(new Color(255, 193, 7)); // Yellow for in-progress

                if (formResponsesSummary.length() > 0) {
                    String responses = formResponsesSummary.toString();
                    if (responses.length() > 1024) {
                        responses = responses.substring(0, 1020) + "...";
                    }
                    checkpointEmbed.addField(t(guildId, "tickets.form_responses"), responses, false);
                }

                if (pendingData.hasMoreForms()) {
                    checkpointEmbed.setFooter(t(guildId, "tickets.waiting_for_forms", totalSteps - currentStep));
                } else {
                    checkpointEmbed.setFooter(t(guildId, "tickets.forms_completing"));
                }

                channel.sendMessageEmbeds(checkpointEmbed.build()).queue();
            }
        }

        // Check if there are more forms to show
        if (pendingData.hasMoreForms()) {
            pendingData.advanceToNextForm();
            showNextCustomFormModal(event, guildId, pendingKey);
        } else {
            // All forms completed, finalize the ticket
            pendingTickets.remove(pendingKey);
            finalizeTicket(event, guildId, pendingData);
        }
    }

    /**
     * Finalizes the ticket after all forms are completed.
     * Grants write permissions and sends the welcome message.
     */
    private void finalizeTicket(ModalInteractionEvent event, String guildId, PendingTicketData pendingData) {
        if (!pendingData.isChannelCreated()) {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
            return;
        }

        TextChannel channel = event.getGuild().getTextChannelById(pendingData.channelId);
        if (channel == null) {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
            return;
        }

        DatabaseHandler.TicketCategoryData category = handler.getTicketCategory(pendingData.categoryId);
        DatabaseHandler.TicketPanelData panel = category != null ? handler.getTicketPanel(category.panelId) : null;

        if (category == null || panel == null) {
            event.reply(t(guildId, "general.error")).setEphemeral(true).queue();
            return;
        }

        // Collect all form fields for saving responses
        java.util.List<DatabaseHandler.TicketFormFieldData> allFormFields = new java.util.ArrayList<>();
        if (pendingData.formIds != null) {
            for (int fId : pendingData.formIds) {
                allFormFields.addAll(handler.getTicketFormFieldsByFormId(fId));
            }
        }

        // Save form responses to database
        if (!pendingData.allFormResponses.isEmpty()) {
            handler.saveTicketFormResponses(pendingData.ticketId, allFormFields, pendingData.allFormResponses);
        }

        // Grant write permissions to the user
        channel.upsertPermissionOverride(Objects.requireNonNull(event.getMember()))
            .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
            .queue();

        // Build form responses string for welcome embed
        StringBuilder formResponses = new StringBuilder();
        if (!pendingData.description.isEmpty()) {
            formResponses.append("**").append(t(guildId, "tickets.description")).append(":**\n")
                .append(pendingData.description).append("\n\n");
        }
        for (DatabaseHandler.TicketFormFieldData field : allFormFields) {
            String response = pendingData.allFormResponses.get(field.id);
            if (response != null && !response.isBlank()) {
                formResponses.append("**").append(field.label).append(":**\n")
                    .append(response).append("\n\n");
            }
        }

        // Build welcome embed
        EmbedBuilder welcomeEmbed = new EmbedBuilder();
        welcomeEmbed.setTitle("🎫 " + category.name + " - Ticket #" + pendingData.ticketId);
        welcomeEmbed.setColor(parseColor(panel.embedColor));
        welcomeEmbed.addField("👤 " + t(guildId, "tickets.created_by"), event.getUser().getAsMention(), true);
        welcomeEmbed.addField("📂 " + t(guildId, "tickets.category"), category.name, true);
        welcomeEmbed.addField("📋 " + t(guildId, "tickets.subject"), pendingData.subject, false);

        if (formResponses.length() > 0) {
            String responses = formResponses.toString();
            if (responses.length() > 1024) {
                responses = responses.substring(0, 1020) + "...";
            }
            welcomeEmbed.addField("📝 " + t(guildId, "tickets.form_responses"), responses, false);
        }

        String welcomeMessage = category.welcomeMessage != null ? category.welcomeMessage : panel.welcomeMessage;
        welcomeEmbed.setDescription("✅ " + t(guildId, "tickets.forms_completed") + "\n\n" + welcomeMessage);
        welcomeEmbed.setTimestamp(java.time.Instant.now());

        // Ping role if configured
        String pingContent = "";
        if (panel.pingRoleId != null) {
            Role pingRole = event.getGuild().getRoleById(panel.pingRoleId);
            if (pingRole != null) {
                pingContent = pingRole.getAsMention() + " ";
            }
        }

        channel.sendMessage(pingContent + event.getUser().getAsMention() + " " + t(guildId, "tickets.you_can_now_chat"))
            .setEmbeds(welcomeEmbed.build())
            .setComponents(ActionRow.of(
                Button.danger("close_ticket_panel_" + category.panelId, t(guildId, "tickets.close_button"))
            ))
            .queue();

        // Send audit log entry
        handler.sendAuditLogEntry(event.getGuild(), "TICKET_CREATED",
            "Ticket #" + pendingData.ticketId + " - " + pendingData.subject + " (Panel: " + panel.name + ")",
            event.getMember(), null, "");

        event.reply(t(guildId, "tickets.created_success", channel.getAsMention()))
            .setEphemeral(true)
            .queue();
    }

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
}

