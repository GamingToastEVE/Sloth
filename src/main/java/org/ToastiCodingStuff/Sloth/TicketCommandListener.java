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
                    event.getMember(), null, reason);
            
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
        if (!Objects.equals(event.getButton().getCustomId(), "close_ticket_confirm")) {
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
                    event.getMember(), null, "Closed via button");
            
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔒 Ticket Closed")
                    .setDescription("This ticket has been closed by " + event.getUser().getAsMention())
                    .addField("Closed at", "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true)
                    .setColor(Color.RED);

            Button deleteChannelButton = Button.danger("delete_channel", "🗑️ Delete Channel");

            event.replyEmbeds(embed.build()).setComponents(ActionRow.of(deleteChannelButton)).queue();
            channel.getManager().setName("closed-" + channel.getName()).queue();
        } else {
            event.reply("❌ Failed to close ticket.").setEphemeral(true).queue();
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