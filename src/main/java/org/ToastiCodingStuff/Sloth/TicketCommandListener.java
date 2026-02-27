package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;

public class TicketCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public TicketCommandListener(DatabaseHandler handler) {
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
            case "close":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {event.reply(t(guildId, "general.permission_denied")).setEphemeral(true).queue(); return;}
                handler.insertOrUpdateGlobalStatistic("ticket-close");
                handleCloseTicket(event, guildId);
                break;
            case "assign":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {event.reply(t(guildId, "general.permission_denied")).setEphemeral(true).queue(); return;}
                handler.insertOrUpdateGlobalStatistic("ticket-assign");
                handleAssignTicket(event, guildId);
                break;
            case "priority":
                if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {event.reply(t(guildId, "general.permission_denied")).setEphemeral(true).queue(); return;}
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
        
        if (customId.equals("close_ticket_confirm")) {
            handleCloseTicketConfirm(event);
        } else if (customId.equals("delete_channel")) {
            handleDeleteChannel(event);
        }
    }


    private void handleCloseTicket(SlashCommandInteractionEvent event, String guildId) {
        TextChannel channel = event.getChannel().asTextChannel();
        String ticketInfo = handler.getTicketByChannelId(channel.getId());
        
        if (ticketInfo == null) {
            event.reply(t(guildId, "tickets.not_a_ticket")).setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("reason") != null ? Objects.requireNonNull(event.getOption("reason")).getAsString() : t(guildId, "moderation.no_reason");

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
                    t(guildId, "tickets.audit_log_target", ticketId),
                    event.getMember(), null, reason);
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(t(guildId, "tickets.close_title"))
                    .setDescription(t(guildId, "tickets.close_description", event.getUser().getAsMention()))
                    .addField(t(guildId, "general.reason"), reason, false)
                    .addField(t(guildId, "tickets.closed_at"), "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true)
                    .setColor(Color.RED);

            event.replyEmbeds(embed.build()).queue();
            
            // Archive channel after 5 seconds
            channel.delete().reason(t(guildId, "tickets.channel_delete_reason_closed")).queue();

        } else {
            event.reply(t(guildId, "tickets.close_failed")).setEphemeral(true).queue();
        }
    }

    private void handleCloseTicketConfirm(ButtonInteractionEvent event) {
        if (!Objects.equals(event.getButton().getCustomId(), "close_ticket_confirm")) {
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        String ticketInfo = handler.getTicketByChannelId(channel.getId());
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        if (ticketInfo == null) {
            event.reply(t(guildId, "tickets.not_a_ticket")).setEphemeral(true).queue();
            return;
        }

        // Close ticket in database
        String[] parts = ticketInfo.split(" \\| ");
        int ticketId = Integer.parseInt(parts[0].substring(4));
        
        boolean success = handler.closeTicket(ticketId, event.getUser().getId(), t(guildId, "tickets.closed_via_button"));

        if (success) {
            handler.incrementTicketsClosed(guildId);
            handler.incrementUserTicketsClosed(guildId, event.getUser().getId());
            handler.sendAuditLogEntry(event.getGuild(), "TICKET_CLOSED",
                    t(guildId, "tickets.audit_log_target", ticketId),
                    event.getMember(), null, t(guildId, "tickets.closed_via_button"));

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(t(guildId, "tickets.close_title"))
                    .setDescription(t(guildId, "tickets.close_description", event.getUser().getAsMention()))
                    .addField(t(guildId, "tickets.closed_at"), "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true)
                    .setColor(Color.RED);

            Button deleteChannelButton = Button.danger("delete_channel", t(guildId, "tickets.delete_channel_btn"));

            event.replyEmbeds(embed.build()).setComponents(ActionRow.of(deleteChannelButton)).queue();
            channel.getManager().setName("closed-" + channel.getName()).queue();
        } else {
            event.reply(t(guildId, "tickets.close_failed")).setEphemeral(true).queue();
        }
    }

    private void handleDeleteChannel(ButtonInteractionEvent event) {
        if (!Objects.equals(event.getButton().getCustomId(), "delete_channel")) {
            return;
        }

        TextChannel channel = event.getChannel().asTextChannel();
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        
        // Check if this is a closed ticket channel (should start with "closed-")
        if (!channel.getName().startsWith("closed-")) {
            event.reply(t(guildId, "tickets.delete_only_closed")).setEphemeral(true).queue();
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
            event.reply(t(guildId, "tickets.delete_no_permission")).setEphemeral(true).queue();
            return;
        }

        // Acknowledge the interaction and delete the channel
        event.reply(t(guildId, "tickets.deleting_channel")).setEphemeral(true).queue(
            success -> channel.delete().reason(t(guildId, "tickets.channel_delete_reason_by_user", event.getUser().getEffectiveName())).queue(),
            error -> event.reply(t(guildId, "tickets.delete_failed")).setEphemeral(true).queue()
        );
    }

    private void handleAssignTicket(SlashCommandInteractionEvent event, String guildId) {
        TextChannel channel = event.getChannel().asTextChannel();
        String ticketInfo = handler.getTicketByChannelId(channel.getId());
        
        if (ticketInfo == null) {
            event.reply(t(guildId, "tickets.not_a_ticket")).setEphemeral(true).queue();
            return;
        }

        Member staffMember = Objects.requireNonNull(event.getOption("staff")).getAsMember();
        if (staffMember == null) {
            event.reply(t(guildId, "tickets.staff_not_found")).setEphemeral(true).queue();
            return;
        }

        // Assign ticket in database
        String[] parts = ticketInfo.split(" \\| ");
        int ticketId = Integer.parseInt(parts[0].substring(4));
        
        boolean success = handler.assignTicket(ticketId, staffMember.getId());
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(t(guildId, "tickets.assign_title"))
                    .setDescription(t(guildId, "tickets.assign_description", staffMember.getAsMention()))
                    .addField(t(guildId, "tickets.assigned_by"), event.getUser().getAsMention(), true)
                    .addField(t(guildId, "tickets.status"), t(guildId, "tickets.in_progress"), true)
                    .setColor(Color.ORANGE);

            try {
                channel.upsertPermissionOverride(staffMember).grant(EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY, Permission.MESSAGE_SEND, Permission.MANAGE_CHANNEL)).queue();
            } catch (PermissionException pe) {
                System.err.println("Failed to assign permissions to staff member: " + pe.getMessage());
            }
            event.replyEmbeds(embed.build()).queue();
        } else {
            event.reply(t(guildId, "tickets.assign_failed")).setEphemeral(true).queue();
        }
    }

    private void handleSetTicketPriority(SlashCommandInteractionEvent event, String guildId) {
        TextChannel channel = event.getChannel().asTextChannel();
        String ticketInfo = handler.getTicketByChannelId(channel.getId());
        
        if (ticketInfo == null) {
            event.reply(t(guildId, "tickets.not_a_ticket")).setEphemeral(true).queue();
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
            event.reply(t(guildId, "tickets.priority_no_permission")).setEphemeral(true).queue();
            return;
        }

        String newPriority = Objects.requireNonNull(event.getOption("priority")).getAsString();
        
        // Extract ticket ID from ticketInfo
        String[] parts = ticketInfo.split(" \\| ");
        int ticketId = Integer.parseInt(parts[0].substring(4)); // Remove "ID: " prefix
        
        boolean success = handler.updateTicketPriority(ticketId, newPriority);
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(t(guildId, "tickets.priority_title"))
                    .setDescription(t(guildId, "tickets.priority_description", newPriority))
                    .addField(t(guildId, "tickets.updated_by"), event.getUser().getAsMention(), true)
                    .addField(t(guildId, "tickets.new_priority"), newPriority, true)
                    .setColor(getPriorityColor(newPriority))
                    .setTimestamp(java.time.Instant.now());

            event.replyEmbeds(embed.build()).queue();
            
            // Sort channels by priority after updating
            sortTicketChannelsByPriority(event.getGuild(), guildId);
        } else {
            event.reply(t(guildId, "tickets.priority_failed")).setEphemeral(true).queue();
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
            event.reply(t(guildId, "tickets.not_a_ticket")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(t(guildId, "tickets.info_embed_title"))
                .setDescription(ticketInfo)
                .setColor(Color.BLUE)
                .setTimestamp(java.time.Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleTicketTranscript(SlashCommandInteractionEvent event, String guildId) {
        //check if bot has message content intent
        if (!event.getJDA().getGatewayIntents().contains(net.dv8tion.jda.api.requests.GatewayIntent.MESSAGE_CONTENT)) {
            event.reply(t(guildId, "tickets.transcript_no_intent")).setEphemeral(true).queue();
            return;
        }
        TextChannel channel = event.getChannel().asTextChannel();
        String ticketInfo = handler.getTicketByChannelId(channel.getId());
        
        if (ticketInfo == null) {
            event.reply(t(guildId, "tickets.not_a_ticket")).setEphemeral(true).queue();
            return;
        }

        // Check if transcripts are enabled for this guild
        if (!handler.areTranscriptsEnabled(guildId)) {
            event.reply(t(guildId, "tickets.transcript_disabled")).setEphemeral(true).queue();
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
            event.reply(t(guildId, "tickets.transcript_no_permission")).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue(); // Defer reply as this might take time
        
        // Generate transcript from channel history
        channel.getHistory().retrievePast(100).queue(messages -> {
            StringBuilder transcript = new StringBuilder();
            transcript.append(t(guildId, "tickets.transcript_header")).append("\n");
            transcript.append(t(guildId, "tickets.transcript_ticket_id", ticketIdStr)).append("\n");
            transcript.append(t(guildId, "tickets.transcript_channel", channel.getName())).append("\n");
            transcript.append(t(guildId, "tickets.transcript_generated_at", new java.util.Date())).append("\n");
            transcript.append(t(guildId, "tickets.transcript_separator")).append("\n\n");

            // Sort messages chronologically (oldest first)
            messages.sort(Comparator.comparing(ISnowflake::getTimeCreated));
            
            for (Message msg : messages) {
                transcript.append("[").append(msg.getTimeCreated()).append("] ");
                transcript.append(msg.getAuthor().getEffectiveName()).append(": ");
                transcript.append(msg.getContentDisplay()).append("\n");
                
                // Add attachment info if present
                if (!msg.getAttachments().isEmpty()) {
                    for (Message.Attachment attachment : msg.getAttachments()) {
                        transcript.append(t(guildId, "tickets.transcript_attachment", attachment.getFileName(), attachment.getUrl())).append("\n");
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
                    
                    event.getHook().sendMessage(t(guildId, "tickets.transcript_generated"))
                            .addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(tempFile, "ticket-" + ticketIdStr + "-transcript.txt"))
                            .queue(success -> tempFile.delete()); // Clean up temp file
                } catch (Exception e) {
                    event.getHook().sendMessage(t(guildId, "tickets.transcript_failed")).queue();
                }
            } else {
                // Send as embed if short enough
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle(t(guildId, "tickets.transcript_embed_title", ticketIdStr))
                        .setDescription("```\n" + transcriptText + "```")
                        .setColor(Color.BLUE)
                        .setTimestamp(java.time.Instant.now());
                
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            }
        }, error -> event.getHook().sendMessage(t(guildId, "tickets.transcript_history_failed")).queue());
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

