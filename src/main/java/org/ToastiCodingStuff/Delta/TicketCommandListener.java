package org.ToastiCodingStuff.Delta;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;

import java.awt.*;
import java.util.EnumSet;

public class TicketCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public TicketCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        switch (event.getName()) {
            case "ticket-setup":
                handleTicketSetup(event, guildId);
                break;
            case "ticket-panel":
                handleTicketPanel(event, guildId);
                break;
            case "close-ticket":
                handleCloseTicket(event, guildId);
                break;
            case "assign-ticket":
                handleAssignTicket(event, guildId);
                break;
            case "ticket-info":
                handleTicketInfo(event, guildId);
                break;
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String customId = event.getComponentId();
        
        if (customId.equals("create_ticket")) {
            handleCreateTicketButton(event);
        } else if (customId.equals("close_ticket_confirm")) {
            handleCloseTicketConfirm(event);
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        
        if (modalId.equals("ticket_creation_modal")) {
            handleTicketCreationModal(event);
        }
    }

    private void handleTicketSetup(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has admin permissions
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You need Administrator permissions to set up the ticket system.").setEphemeral(true).queue();
            return;
        }

        Category category = event.getOption("category").getAsChannel().asCategory();
        TextChannel channel = event.getOption("channel").getAsChannel().asTextChannel();
        Role supportRole = event.getOption("support_role") != null ? event.getOption("support_role").getAsRole() : null;
        boolean transcriptEnabled = event.getOption("transcript_enabled") != null ? event.getOption("transcript_enabled").getAsBoolean() : true;

        String supportRoleId = supportRole != null ? supportRole.getId() : null;
        
        boolean success = handler.setTicketSettings(guildId, category.getId(), channel.getId(), supportRoleId, transcriptEnabled);
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Ticket System Configured")
                    .setDescription("The ticket system has been successfully configured!")
                    .addField("Ticket Category", category.getAsMention(), true)
                    .addField("Ticket Panel Channel", channel.getAsMention(), true)
                    .addField("Support Role", supportRole != null ? supportRole.getAsMention() : "None", true)
                    .addField("Transcripts Enabled", transcriptEnabled ? "Yes" : "No", true)
                    .setColor(Color.GREEN);
            
            event.replyEmbeds(embed.build()).queue();
        } else {
            event.reply("❌ Failed to configure ticket system. Please try again.").setEphemeral(true).queue();
        }
    }

    private void handleTicketPanel(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has manage channels permission
        if (!event.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("❌ You need Manage Channels permission to create a ticket panel.").setEphemeral(true).queue();
            return;
        }

        if (!handler.isTicketSystem(guildId)) {
            event.reply("❌ Ticket system is not configured for this server. Use `/ticket-setup` first.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎫 Create a Ticket")
                .setDescription("Need help or have a question? Click the button below to create a ticket!\n\n" +
                        "Our support team will assist you as soon as possible.")
                .addField("📋 What to include:", 
                        "• A clear description of your issue\n" +
                        "• Any relevant information or screenshots\n" +
                        "• Your preferred priority level", false)
                .setColor(Color.BLUE)
                .setFooter("Ticket System • Click the button to get started");

        Button createTicketButton = Button.primary("create_ticket", "🎫 Create Ticket");

        event.getChannel().sendMessageEmbeds(embed.build())
                .setActionRow(createTicketButton)
                .queue(message -> {
                    event.reply("✅ Ticket panel created successfully!").setEphemeral(true).queue();
                });
    }

    private void handleCreateTicketButton(ButtonInteractionEvent event) {
        String guildId = event.getGuild().getId();
        
        if (!handler.isTicketSystem(guildId)) {
            event.reply("❌ Ticket system is not configured for this server.").setEphemeral(true).queue();
            return;
        }

        TextInput subjectInput = TextInput.create("subject", "Subject", TextInputStyle.SHORT)
                .setPlaceholder("Brief description of your issue...")
                .setRequiredRange(5, 100)
                .build();

        TextInput descriptionInput = TextInput.create("description", "Detailed Description", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Please provide as much detail as possible...")
                .setRequiredRange(10, 1000)
                .build();

        TextInput priorityInput = TextInput.create("priority", "Priority Level", TextInputStyle.SHORT)
                .setPlaceholder("LOW, MEDIUM, HIGH, or URGENT")
                .setValue("MEDIUM")
                .setRequiredRange(3, 6)
                .build();

        Modal modal = Modal.create("ticket_creation_modal", "Create New Ticket")
                .addActionRow(subjectInput)
                .addActionRow(descriptionInput)
                .addActionRow(priorityInput)
                .build();

        event.replyModal(modal).queue();
    }

    private void handleTicketCreationModal(ModalInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        String subject = event.getValue("subject").getAsString();
        String description = event.getValue("description").getAsString();
        String priorityInput = event.getValue("priority").getAsString().toUpperCase();

        // Validate priority
        final String priority = priorityInput.matches("LOW|MEDIUM|HIGH|URGENT") ? priorityInput : "MEDIUM";

        String categoryId = handler.getTicketCategory(guildId);
        if (categoryId == null) {
            event.reply("❌ Ticket system is not properly configured.").setEphemeral(true).queue();
            return;
        }

        Category ticketCategory = event.getGuild().getCategoryById(categoryId);
        if (ticketCategory == null) {
            event.reply("❌ Ticket category not found.").setEphemeral(true).queue();
            return;
        }

        // Create ticket channel
        String channelName = "ticket-" + event.getUser().getName().toLowerCase().replaceAll("[^a-z0-9]", "");
        
        ticketCategory.createTextChannel(channelName)
                .addPermissionOverride(event.getGuild().getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(event.getMember(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null)
                .queue(channel -> {
                    // Add support role permissions if configured
                    String supportRoleId = handler.getTicketRole(guildId);
                    if (supportRoleId != null) {
                        Role supportRole = event.getGuild().getRoleById(supportRoleId);
                        if (supportRole != null) {
                            channel.getManager().putPermissionOverride(supportRole, 
                                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MANAGE_CHANNEL), 
                                    null).queue();
                        }
                    }

                    // Create ticket in database
                    int ticketId = handler.createTicket(guildId, userId, channel.getId(), "general", subject, priority);
                    
                    if (ticketId > 0) {
                        // Send welcome message in ticket channel
                        EmbedBuilder welcomeEmbed = new EmbedBuilder()
                                .setTitle("🎫 Ticket #" + ticketId + " - " + subject)
                                .setDescription("**Description:**\n" + description)
                                .addField("👤 Created by", event.getUser().getAsMention(), true)
                                .addField("📈 Priority", priority, true)
                                .addField("📅 Created", "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true)
                                .setColor(getPriorityColor(priority))
                                .setFooter("Ticket ID: " + ticketId);

                        Button closeButton = Button.danger("close_ticket_confirm", "🔒 Close Ticket");

                        channel.sendMessage(event.getUser().getAsMention() + " Welcome to your support ticket!")
                                .addEmbeds(welcomeEmbed.build())
                                .setActionRow(closeButton)
                                .queue();

                        event.reply("✅ Ticket created successfully! " + channel.getAsMention()).setEphemeral(true).queue();
                    } else {
                        channel.delete().queue();
                        event.reply("❌ Failed to create ticket in database.").setEphemeral(true).queue();
                    }
                }, 
                error -> event.reply("❌ Failed to create ticket channel.").setEphemeral(true).queue());
    }

    private void handleCloseTicket(SlashCommandInteractionEvent event, String guildId) {
        TextChannel channel = event.getChannel().asTextChannel();
        String ticketInfo = handler.getTicketByChannelId(channel.getId());
        
        if (ticketInfo == null) {
            event.reply("❌ This is not a ticket channel.").setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";
        
        // Close ticket in database (extract ticket ID from ticketInfo)
        String[] parts = ticketInfo.split(" \\| ");
        int ticketId = Integer.parseInt(parts[0].substring(4)); // Remove "ID: " prefix
        
        boolean success = handler.closeTicket(ticketId, event.getUser().getId(), reason);
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔒 Ticket Closed")
                    .setDescription("This ticket has been closed by " + event.getUser().getAsMention())
                    .addField("Reason", reason, false)
                    .addField("Closed at", "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true)
                    .setColor(Color.RED);

            event.replyEmbeds(embed.build()).queue();
            
            // Archive channel after 5 seconds
            channel.getManager().setName("closed-" + channel.getName()).queue();
            
        } else {
            event.reply("❌ Failed to close ticket.").setEphemeral(true).queue();
        }
    }

    private void handleCloseTicketConfirm(ButtonInteractionEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();
        String ticketInfo = handler.getTicketByChannelId(channel.getId());
        
        if (ticketInfo == null) {
            event.reply("❌ This is not a ticket channel.").setEphemeral(true).queue();
            return;
        }

        // Close ticket in database
        String[] parts = ticketInfo.split(" \\| ");
        int ticketId = Integer.parseInt(parts[0].substring(4));
        
        boolean success = handler.closeTicket(ticketId, event.getUser().getId(), "Closed via button");
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔒 Ticket Closed")
                    .setDescription("This ticket has been closed by " + event.getUser().getAsMention())
                    .addField("Closed at", "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true)
                    .setColor(Color.RED);

            event.replyEmbeds(embed.build()).queue();
            channel.getManager().setName("closed-" + channel.getName()).queue();
        } else {
            event.reply("❌ Failed to close ticket.").setEphemeral(true).queue();
        }
    }

    private void handleAssignTicket(SlashCommandInteractionEvent event, String guildId) {
        TextChannel channel = event.getChannel().asTextChannel();
        String ticketInfo = handler.getTicketByChannelId(channel.getId());
        
        if (ticketInfo == null) {
            event.reply("❌ This is not a ticket channel.").setEphemeral(true).queue();
            return;
        }

        Member staffMember = event.getOption("staff").getAsMember();
        if (staffMember == null) {
            event.reply("❌ Staff member not found.").setEphemeral(true).queue();
            return;
        }

        // Assign ticket in database
        String[] parts = ticketInfo.split(" \\| ");
        int ticketId = Integer.parseInt(parts[0].substring(4));
        
        boolean success = handler.assignTicket(ticketId, staffMember.getId());
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("👨‍💼 Ticket Assigned")
                    .setDescription("This ticket has been assigned to " + staffMember.getAsMention())
                    .addField("Assigned by", event.getUser().getAsMention(), true)
                    .addField("Status", "IN_PROGRESS", true)
                    .setColor(Color.ORANGE);

            event.replyEmbeds(embed.build()).queue();
        } else {
            event.reply("❌ Failed to assign ticket.").setEphemeral(true).queue();
        }
    }

    private void handleTicketInfo(SlashCommandInteractionEvent event, String guildId) {
        TextChannel channel = event.getChannel().asTextChannel();
        String ticketInfo = handler.getTicketByChannelId(channel.getId());
        
        if (ticketInfo == null) {
            event.reply("❌ This is not a ticket channel.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎫 Ticket Information")
                .setDescription(ticketInfo)
                .setColor(Color.BLUE)
                .setTimestamp(java.time.Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private Color getPriorityColor(String priority) {
        switch (priority) {
            case "LOW": return Color.GREEN;
            case "MEDIUM": return Color.YELLOW;
            case "HIGH": return Color.ORANGE;
            case "URGENT": return Color.RED;
            default: return Color.GRAY;
        }
    }
}