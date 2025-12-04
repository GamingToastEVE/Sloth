package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;

import java.awt.*;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;

public class TicketCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public TicketCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("ticket")) {
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            return;
        }

        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        switch (subcommand) {
            case "setup":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {event.reply("No permission.").setEphemeral(true).queue(); return;}
                handler.insertOrUpdateGlobalStatistic("ticket-setup");
                handleTicketSetup(event, guildId);
                break;
            case "panel":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {event.reply("No permission.").setEphemeral(true).queue(); return;}
                handler.insertOrUpdateGlobalStatistic("ticket-panel");
                handleTicketPanel(event, guildId);
                break;
            case "config":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {event.reply("No permission.").setEphemeral(true).queue(); return;}
                handler.insertOrUpdateGlobalStatistic("ticket-config");
                handleSetTicketConfig(event, guildId);
                break;
            case "close":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {event.reply("No permission.").setEphemeral(true).queue(); return;}
                handler.insertOrUpdateGlobalStatistic("ticket-close");
                handleCloseTicket(event, guildId);
                break;
            case "assign":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {event.reply("No permission.").setEphemeral(true).queue(); return;}
                handler.insertOrUpdateGlobalStatistic("ticket-assign");
                handleAssignTicket(event, guildId);
                break;
            case "priority":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {event.reply("No permission.").setEphemeral(true).queue(); return;}
                handler.insertOrUpdateGlobalStatistic("ticket-priority");
                handleSetTicketPriority(event, guildId);
                break;
            case "info":
                handler.insertOrUpdateGlobalStatistic("ticket-info");
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
        } else if (customId.equals("delete_channel")) {
            handleDeleteChannel(event);
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
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Administrator permissions to set up the ticket system.").setEphemeral(true).queue();
            return;
        }

        Category category = event.getOption("category").getAsChannel().asCategory();
        TextChannel channel = event.getOption("channel").getAsChannel().asTextChannel();
        Role supportRole = null;
        if (event.getOption("support-role") != null) {
            supportRole = event.getOption("support-role").getAsRole();
        }
        //boolean transcriptEnabled = event.getOption("transcript_enabled") == null || Objects.requireNonNull(event.getOption("transcript_enabled")).getAsBoolean();
        boolean transcriptEnabled = false; // Default to false for now

        String supportRoleId = supportRole != null ? supportRole.getId() : null;
        
        boolean success = handler.setTicketSettings(guildId, category.getId(), channel.getId(), supportRoleId, transcriptEnabled);
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Ticket System Configured")
                    .setDescription("The ticket system has been successfully configured!")
                    .addField("Ticket Category", category.getAsMention(), true)
                    .addField("Ticket Panel Channel", channel.getAsMention(), true)
                    .addField("Support Role", supportRole != null ? supportRole.getAsMention() : "None", true)
                    //.addField("Transcripts Enabled", transcriptEnabled ? "Yes" : "No", true)
                    .setColor(Color.GREEN);
            
            event.replyEmbeds(embed.build()).queue();
        } else {
            event.reply("❌ Failed to configure ticket system. Please try again.").setEphemeral(true).queue();
        }
    }

    private void handleSetTicketConfig(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has manage server permissions
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permissions to configure ticket settings.").setEphemeral(true).queue();
            return;
        }

        String title = Objects.requireNonNull(event.getOption("title")).getAsString();
        String description = Objects.requireNonNull(event.getOption("description")).getAsString();

        boolean success = handler.setTicketConfig(guildId, title, description);
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Ticket Configuration Updated")
                    .setDescription("The ticket panel title and description have been successfully updated!")
                    .addField("New Title", title, false)
                    .addField("New Description", description, false)
                    .setColor(Color.GREEN)
                    .setFooter("Use /ticket-panel to create a new panel with these settings");
            
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to update ticket configuration. Please try again.").setEphemeral(true).queue();
        }
    }

    private void handleTicketPanel(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has manage channels permission
        if (!Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_CHANNEL)) {
            event.reply("❌ You need Manage Channels permission to create a ticket panel.").setEphemeral(true).queue();
            return;
        }

        if (!handler.isTicketSystem(guildId)) {
            event.reply("❌ Ticket system is not configured for this server. Use `/ticket-setup` first.").setEphemeral(true).queue();
            return;
        }

        // Get customizable title and description from database
        String title = handler.getTicketTitle(guildId);
        String description = handler.getTicketDescription(guildId);
        description = handler.processLinebreaks(description);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(Color.BLUE)
                .setFooter("Ticket System • Click the button to get started");

        Button createTicketButton = Button.primary("create_ticket", "🎫 Create Ticket");

        event.getChannel().sendMessageEmbeds(embed.build())
                .setActionRow(createTicketButton)
                .queue(message -> {
                    event.reply("✅ Ticket panel created successfully!").setEphemeral(true).queue();
                    // Sort channels to ensure ticket panel channel stays on top
                    sortTicketChannelsByPriority(event.getGuild(), guildId);
                });
    }

    private void handleCreateTicketButton(ButtonInteractionEvent event) {
        if (!Objects.equals(event.getButton().getId(), "create_ticket")) {
            return;
        }

        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        
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

        /*TextInput priorityInput = TextInput.create("priority", "Priority Level", TextInputStyle.SHORT)
                .setPlaceholder("LOW, MEDIUM, HIGH, or URGENT")
                .setValue("MEDIUM")
                .setRequiredRange(3, 6)
                .build();
        */
        Modal modal = Modal.create("ticket_creation_modal", "Create New Ticket")
                .addActionRow(subjectInput)
                .addActionRow(descriptionInput)
                //.addActionRow(priorityInput)
                .build();

        event.replyModal(modal).queue();
    }

    private void handleTicketCreationModal(ModalInteractionEvent event) {
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String userId = event.getUser().getId();
        String subject = Objects.requireNonNull(event.getValue("subject")).getAsString();
        String description = Objects.requireNonNull(event.getValue("description")).getAsString();
        String priorityInput = "MEDIUM";     //event.getValue("priority").getAsString().toUpperCase();

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
                .addPermissionOverride(Objects.requireNonNull(event.getMember()), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null)
                .addPermissionOverride(Objects.requireNonNull(event.getGuild().getMemberById("1179144350119239831")), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY, Permission.MESSAGE_MANAGE, Permission.MANAGE_CHANNEL), null)
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
                    int ticketId = handler.createTicket(guildId, userId, channel.getId(), "general", subject, priority, 
                            event.getUser().getEffectiveName(), event.getUser().getDiscriminator(), event.getUser().getAvatarUrl());
                    
                    if (ticketId > 0) {
                        // Update channel name to include ticket ID
                        String newChannelName = "ticket-" + ticketId + "-" + event.getUser().getName().toLowerCase().replaceAll("[^a-z0-9]", "");
                        channel.getManager().setName(newChannelName).queue();
                        
                        // Update statistics for tickets created
                        handler.incrementTicketsCreated(guildId);
                        
                        // Update user statistics for ticket creation
                        handler.incrementUserTicketsCreated(guildId, userId);
                        
                        // Send audit log entry for ticket creation
                        handler.sendAuditLogEntry(event.getGuild(), "TICKET_CREATED", 
                                "Ticket #" + ticketId + " - " + subject, 
                                event.getUser().getEffectiveName(), "Priority: " + priority);
                        
                        // Send welcome message in ticket channel
                        EmbedBuilder welcomeEmbed = new EmbedBuilder()
                                .setTitle("🎫 Ticket #" + ticketId + " - " + subject)
                                .setDescription("**Description:**\n" + handler.processLinebreaks(description))
                                .addField("👤 Created by", event.getUser().getAsMention(), true)
                                //.addField("📈 Priority", priority, true)
                                .addField("📅 Created", "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true)
                                .setColor(getPriorityColor(priority))
                                .setFooter("Ticket ID: " + ticketId);

                        Button closeButton = Button.danger("close_ticket_confirm", "🔒 Close Ticket");

                        channel.sendMessage(event.getUser().getAsMention() + " Welcome to your support ticket!")
                                .addEmbeds(welcomeEmbed.build())
                                .setActionRow(closeButton)
                                .queue();

                        // Sort channels by priority after creating new ticket
                        sortTicketChannelsByPriority(event.getGuild(), guildId);

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

        String reason = event.getOption("reason") != null ? Objects.requireNonNull(event.getOption("reason")).getAsString() : "No reason provided";
        
        // Close ticket in database (extract ticket ID from ticketInfo)
        String[] parts = ticketInfo.split(" \\| ");
        int ticketId = Integer.parseInt(parts[0].substring(4)); // Remove "ID: " prefix
        
        boolean success = handler.closeTicket(ticketId, event.getUser().getId(), reason);
        
        if (success) {
            // Update statistics for tickets closed
            handler.incrementTicketsClosed(guildId);
            
            // Update user statistics for ticket closure
            handler.incrementUserTicketsClosed(guildId, event.getUser().getId());
            
            // Send audit log entry for ticket closure
            handler.sendAuditLogEntry(Objects.requireNonNull(event.getGuild()), "TICKET_CLOSED",
                    "Ticket #" + ticketId, 
                    event.getUser().getEffectiveName(), reason);
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔒 Ticket Closed")
                    .setDescription("This ticket has been closed by " + event.getUser().getAsMention())
                    .addField("Reason", reason, false)
                    .addField("Closed at", "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true)
                    .setColor(Color.RED);

            event.replyEmbeds(embed.build()).queue();
            
            // Archive channel after 5 seconds
            channel.delete().reason("Ticket closed").queue();
            
        } else {
            event.reply("❌ Failed to close ticket.").setEphemeral(true).queue();
        }
    }

    private void handleCloseTicketConfirm(ButtonInteractionEvent event) {
        if (!Objects.equals(event.getButton().getId(), "close_ticket_confirm")) {
            return;
        }

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
            // Update statistics for tickets closed
            String guildId = Objects.requireNonNull(event.getGuild()).getId();
            handler.incrementTicketsClosed(guildId);
            
            // Update user statistics for ticket closure
            handler.incrementUserTicketsClosed(guildId, event.getUser().getId());
            
            // Send audit log entry for ticket closure via button
            handler.sendAuditLogEntry(event.getGuild(), "TICKET_CLOSED", 
                    "Ticket #" + ticketId, 
                    event.getUser().getEffectiveName(), "Closed via button");
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔒 Ticket Closed")
                    .setDescription("This ticket has been closed by " + event.getUser().getAsMention())
                    .addField("Closed at", "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true)
                    .setColor(Color.RED);

            Button deleteChannelButton = Button.danger("delete_channel", "🗑️ Delete Channel");

            event.replyEmbeds(embed.build()).setActionRow(deleteChannelButton).queue();
            channel.getManager().setName("closed-" + channel.getName()).queue();
        } else {
            event.reply("❌ Failed to close ticket.").setEphemeral(true).queue();
        }
    }

    private void handleDeleteChannel(ButtonInteractionEvent event) {
        if (!Objects.equals(event.getButton().getId(), "delete_channel")) {
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        
        // Check if this is a closed ticket channel (should start with "closed-")
        if (!channel.getName().startsWith("closed-")) {
            event.reply("❌ This channel cannot be deleted. Only closed ticket channels can be deleted.").setEphemeral(true).queue();
            return;
        }
        
        // Check if user has permission to delete the channel
        // Support role members or users with manage channels permission can delete
        String supportRoleId = handler.getTicketRole(guildId);
        boolean hasPermission = false;
        
        if (supportRoleId != null && Objects.requireNonNull(event.getMember()).getRoles().stream()
                .anyMatch(role -> role.getId().equals(supportRoleId))) {
            hasPermission = true;
        } else if (Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_CHANNEL)) {
            hasPermission = true;
        }

        if (!hasPermission) {
            event.reply("❌ You don't have permission to delete this channel.").setEphemeral(true).queue();
            return;
        }

        // Acknowledge the interaction and delete the channel
        event.reply("🗑️ Deleting channel...").setEphemeral(true).queue(
            success -> channel.delete().reason("Ticket channel deleted by " + event.getUser().getEffectiveName()).queue(),
            error -> event.reply("❌ Failed to delete channel.").setEphemeral(true).queue()
        );
    }

    private void handleAssignTicket(SlashCommandInteractionEvent event, String guildId) {
        TextChannel channel = event.getChannel().asTextChannel();
        String ticketInfo = handler.getTicketByChannelId(channel.getId());
        
        if (ticketInfo == null) {
            event.reply("❌ This is not a ticket channel.").setEphemeral(true).queue();
            return;
        }

        Member staffMember = Objects.requireNonNull(event.getOption("staff")).getAsMember();
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

            try {
                channel.upsertPermissionOverride(staffMember).grant(EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY, Permission.MESSAGE_SEND, Permission.MANAGE_CHANNEL)).queue();
            } catch (PermissionException pe) {
                System.err.println("Failed to assign permissions to staff member: " + pe.getMessage());
            }
            event.replyEmbeds(embed.build()).queue();
        } else {
            event.reply("❌ Failed to assign ticket.").setEphemeral(true).queue();
        }
    }

    private void handleSetTicketPriority(SlashCommandInteractionEvent event, String guildId) {
        TextChannel channel = event.getChannel().asTextChannel();
        String ticketInfo = handler.getTicketByChannelId(channel.getId());
        
        if (ticketInfo == null) {
            event.reply("❌ This is not a ticket channel.").setEphemeral(true).queue();
            return;
        }

        // Check if user has permission to change priority (support role or manage channels)
        String supportRoleId = handler.getTicketRole(guildId);
        boolean hasPermission = false;
        
        if (supportRoleId != null && Objects.requireNonNull(event.getMember()).getRoles().stream()
                .anyMatch(role -> role.getId().equals(supportRoleId))) {
            hasPermission = true;
        } else if (Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_CHANNEL)) {
            hasPermission = true;
        }

        if (!hasPermission) {
            event.reply("❌ You don't have permission to change ticket priorities.").setEphemeral(true).queue();
            return;
        }

        String newPriority = Objects.requireNonNull(event.getOption("priority")).getAsString();
        
        // Extract ticket ID from ticketInfo
        String[] parts = ticketInfo.split(" \\| ");
        int ticketId = Integer.parseInt(parts[0].substring(4)); // Remove "ID: " prefix
        
        boolean success = handler.updateTicketPriority(ticketId, newPriority);
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔄 Priority Updated")
                    .setDescription("Ticket priority has been changed to **" + newPriority + "**")
                    .addField("Updated by", event.getUser().getAsMention(), true)
                    .addField("New Priority", newPriority, true)
                    .setColor(getPriorityColor(newPriority))
                    .setTimestamp(java.time.Instant.now());

            event.replyEmbeds(embed.build()).queue();
            
            // Sort channels by priority after updating
            sortTicketChannelsByPriority(event.getGuild(), guildId);
        } else {
            event.reply("❌ Failed to update ticket priority.").setEphemeral(true).queue();
        }
    }

    private void sortTicketChannelsByPriority(Guild guild, String guildId) {
        try {
            String categoryId = handler.getTicketCategory(guildId);
            if (categoryId == null) return;
            
            Category ticketCategory = guild.getCategoryById(categoryId);
            if (ticketCategory == null) return;
            
            // Get the ticket panel channel ID
            String ticketPanelChannelId = handler.getTicketChannel(guildId);
            
            // Get all ticket channels with their priorities
            java.util.List<java.util.Map<String, String>> ticketsWithPriority = handler.getTicketsByGuildWithPriority(guildId);
            
            // Create a map for quick priority lookup
            java.util.Map<String, String> channelToPriority = new java.util.HashMap<>();
            for (java.util.Map<String, String> ticket : ticketsWithPriority) {
                channelToPriority.put(ticket.get("channel_id"), ticket.get("priority"));
            }
            
            // Get all text channels in the category and sort them
            java.util.List<TextChannel> allChannels = ticketCategory.getTextChannels();
            TextChannel ticketPanelChannel = null;
            java.util.List<TextChannel> ticketChannels = new java.util.ArrayList<>();
            java.util.List<TextChannel> nonTicketChannels = new java.util.ArrayList<>();
            
            // Separate channels into three groups: ticket panel, ticket channels, other channels
            for (TextChannel textChannel : allChannels) {
                if (ticketPanelChannelId != null && textChannel.getId().equals(ticketPanelChannelId)) {
                    ticketPanelChannel = textChannel;
                } else if (channelToPriority.containsKey(textChannel.getId())) {
                    ticketChannels.add(textChannel);
                } else {
                    nonTicketChannels.add(textChannel);
                }
            }
            
            // Sort ticket channels by priority
            ticketChannels.sort((ch1, ch2) -> {
                String priority1 = channelToPriority.get(ch1.getId());
                String priority2 = channelToPriority.get(ch2.getId());
                return getPriorityOrder(priority1) - getPriorityOrder(priority2);
            });
            
            // Update channel positions - ticket panel first, then ticket channels (by priority), then other channels
            int position = 0;
            
            // Place ticket panel channel at position 0 (top)
            if (ticketPanelChannel != null && ticketPanelChannel.getPosition() != position) {
                ticketPanelChannel.getManager().setPosition(position).queue();
                position++;
            }
            
            // Place ticket channels sorted by priority
            for (TextChannel channel : ticketChannels) {
                if (channel.getPosition() != position) {
                    channel.getManager().setPosition(position).queue();
                }
                position++;
            }
            
            // Place other non-ticket channels
            for (TextChannel channel : nonTicketChannels) {
                if (channel.getPosition() != position) {
                    channel.getManager().setPosition(position).queue();
                }
                position++;
            }
        } catch (Exception e) {
            System.err.println("Error sorting ticket channels: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int getPriorityOrder(String priority) {
        switch (priority) {
            case "URGENT": return 1;
            case "HIGH": return 2;
            case "MEDIUM": return 3;
            case "LOW": return 4;
            default: return 5;
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

    private void handleTicketTranscript(SlashCommandInteractionEvent event, String guildId) {
        //check if bot has message content intent
        if (!event.getJDA().getGatewayIntents().contains(net.dv8tion.jda.api.requests.GatewayIntent.MESSAGE_CONTENT)) {
            event.reply("❌ Bot does not have Message Content Intent enabled. Cannot generate transcripts.").setEphemeral(true).queue();
            return;
        }
        TextChannel channel = event.getChannel().asTextChannel();
        String ticketInfo = handler.getTicketByChannelId(channel.getId());
        
        if (ticketInfo == null) {
            event.reply("❌ This is not a ticket channel.").setEphemeral(true).queue();
            return;
        }

        // Check if transcripts are enabled for this guild
        if (!handler.areTranscriptsEnabled(guildId)) {
            event.reply("❌ Transcripts are disabled for this server.").setEphemeral(true).queue();
            return;
        }

        // Check if user has permission (ticket creator, assigned staff, or support role)
        String supportRoleId = handler.getTicketRole(guildId);
        boolean hasPermission = false;
        
        // Extract ticket info
        String[] parts = ticketInfo.split(" \\| ");
        String ticketIdStr = parts[0].substring(4); // Remove "ID: " prefix
        String ticketUserIdStr = parts[1].substring(8, parts[1].length() - 1); // Extract user ID from <@...>
        
        // Check if user is ticket creator
        if (event.getUser().getId().equals(ticketUserIdStr)) {
            hasPermission = true;
        }
        // Check if user has support role
        else if (supportRoleId != null && Objects.requireNonNull(event.getMember()).getRoles().stream()
                .anyMatch(role -> role.getId().equals(supportRoleId))) {
            hasPermission = true;
        }
        // Check if user has manage channels permission
        else if (Objects.requireNonNull(event.getMember()).hasPermission(Permission.MANAGE_CHANNEL)) {
            hasPermission = true;
        }

        if (!hasPermission) {
            event.reply("❌ You don't have permission to generate transcripts for this ticket.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue(); // Defer reply as this might take time
        
        // Generate transcript from channel history
        channel.getHistory().retrievePast(100).queue(messages -> {
            StringBuilder transcript = new StringBuilder();
            transcript.append("=== TICKET TRANSCRIPT ===\n");
            transcript.append("Ticket ID: ").append(ticketIdStr).append("\n");
            transcript.append("Channel: #").append(channel.getName()).append("\n");
            transcript.append("Generated: ").append(new java.util.Date()).append("\n");
            transcript.append("=========================\n\n");
            
            // Sort messages chronologically (oldest first)
            messages.sort(Comparator.comparing(ISnowflake::getTimeCreated));
            
            for (Message msg : messages) {
                transcript.append("[").append(msg.getTimeCreated()).append("] ");
                transcript.append(msg.getAuthor().getEffectiveName()).append(": ");
                transcript.append(msg.getContentDisplay()).append("\n");
                
                // Add attachment info if present
                if (!msg.getAttachments().isEmpty()) {
                    for (Message.Attachment attachment : msg.getAttachments()) {
                        transcript.append("    [Attachment: ").append(attachment.getFileName())
                                 .append(" (").append(attachment.getUrl()).append(")]\n");
                    }
                }
                transcript.append("\n");
            }
            
            // Send transcript as a file if it's too long, otherwise as embed
            String transcriptText = transcript.toString();
            if (transcriptText.length() > 4000) {
                // Create temporary file and send as attachment
                try {
                    java.io.File tempFile = java.io.File.createTempFile("ticket-transcript-" + ticketIdStr, ".txt");
                    java.nio.file.Files.write(tempFile.toPath(), transcriptText.getBytes());
                    
                    event.getHook().sendMessage("📄 Ticket transcript generated:")
                            .addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(tempFile, "ticket-" + ticketIdStr + "-transcript.txt"))
                            .queue(success -> tempFile.delete()); // Clean up temp file
                } catch (Exception e) {
                    event.getHook().sendMessage("❌ Failed to generate transcript file.").queue();
                }
            } else {
                // Send as embed if short enough
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("📄 Ticket Transcript #" + ticketIdStr)
                        .setDescription("```\n" + transcriptText + "```")
                        .setColor(Color.BLUE)
                        .setTimestamp(java.time.Instant.now());
                
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            }
        }, error -> event.getHook().sendMessage("❌ Failed to retrieve channel history for transcript.").queue());
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