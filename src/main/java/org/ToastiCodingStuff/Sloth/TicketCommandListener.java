package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
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

public class TicketCommandListener extends ListenerAdapter implements SlashCommandHandler {

    private final DatabaseHandler handler;

    public TicketCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public String[] getHandledCommands() {
        return new String[]{"ticket"};
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
    public void handleSlashCommand(SlashCommandInteractionEvent event) {

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
        
        // Delete buttons - both the current and the legacy custom id - are owned by
        // TicketCreationListener, so a click is answered exactly once.
        if (customId.equals("close_ticket_confirm")) {
            handleCloseTicketConfirm(event);
        }
    }


    // ==================== TICKET LOOKUP HELPERS ====================

    /**
     * Whether a member may act on a ticket as staff: they hold the support role of the
     * panel this ticket belongs to (falling back to the legacy guild-wide role), or they
     * can manage channels anyway.
     * <p>
     * Resolving the role per panel matters because a server configured only through
     * /ticket-panels has no guild-wide ticket role at all.
     */
    private boolean hasSupportAccess(Member member, String guildId, String channelId) {
        if (member == null) {
            return false;
        }
        if (member.hasPermission(Permission.MANAGE_CHANNEL)) {
            return true;
        }

        String supportRoleId = handler.resolveTicketSupportRole(guildId, channelId);
        return supportRoleId != null && member.getRoles().stream()
                .anyMatch(role -> role.getId().equals(supportRoleId));
    }

    private void handleCloseTicket(SlashCommandInteractionEvent event, String guildId) {
        TextChannel channel = event.getChannel().asTextChannel();
        Integer ticketId = handler.getTicketIdByChannelId(channel.getId());
        String status = handler.getTicketStatusByChannelId(channel.getId());

        switch (TicketCloseFlow.evaluateClose(ticketId, status)) {
            case NOT_A_TICKET:
                event.reply(t(guildId, "tickets.not_a_ticket")).setEphemeral(true).queue();
                return;
            case ALREADY_CLOSED:
                event.reply(t(guildId, "tickets.already_closed")).setEphemeral(true).queue();
                return;
            default:
                break;
        }

        String reason = event.getOption("reason") != null
                ? Objects.requireNonNull(event.getOption("reason")).getAsString()
                : t(guildId, "moderation.no_reason");

        if (!handler.closeTicket(ticketId, event.getUser().getId(), reason)) {
            event.reply(t(guildId, "tickets.close_failed")).setEphemeral(true).queue();
            return;
        }

        handler.incrementTicketsClosed(guildId);
        handler.incrementUserTicketsClosed(guildId, event.getUser().getId());
        handler.sendAuditLogEntry(Objects.requireNonNull(event.getGuild()), "TICKET_CLOSED",
                t(guildId, "tickets.audit_log_target", ticketId),
                event.getMember(), null, reason);

        // The channel is kept and only marked as closed. Deleting it is a separate,
        // deliberate click - otherwise the whole conversation is gone before anyone has
        // read the closing embed.
        event.replyEmbeds(TicketCloseFlow
                        .buildClosedEmbed(guildId, event.getUser().getAsMention(), reason, null).build())
                .setComponents(ActionRow.of(TicketCloseFlow.buildDeleteButton(guildId)))
                .queue();

        renameToClosed(channel);
    }

    /** Mark the channel as closed, unless it already carries the prefix. */
    private void renameToClosed(TextChannel channel) {
        String closedName = TicketCloseFlow.closedChannelName(channel.getName());
        if (!closedName.equals(channel.getName())) {
            channel.getManager().setName(closedName).queue();
        }
    }

    /**
     * Legacy confirm button. Still handled because such buttons may sit under old
     * messages in live servers; new closes go through the panel close button.
     */
    private void handleCloseTicketConfirm(ButtonInteractionEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        Integer ticketId = handler.getTicketIdByChannelId(channel.getId());
        String status = handler.getTicketStatusByChannelId(channel.getId());

        switch (TicketCloseFlow.evaluateClose(ticketId, status)) {
            case NOT_A_TICKET:
                event.reply(t(guildId, "tickets.not_a_ticket")).setEphemeral(true).queue();
                return;
            case ALREADY_CLOSED:
                event.reply(t(guildId, "tickets.already_closed")).setEphemeral(true).queue();
                return;
            default:
                break;
        }

        String reason = t(guildId, "tickets.closed_via_button");
        if (!handler.closeTicket(ticketId, event.getUser().getId(), reason)) {
            event.reply(t(guildId, "tickets.close_failed")).setEphemeral(true).queue();
            return;
        }

        handler.incrementTicketsClosed(guildId);
        handler.incrementUserTicketsClosed(guildId, event.getUser().getId());
        handler.sendAuditLogEntry(event.getGuild(), "TICKET_CLOSED",
                t(guildId, "tickets.audit_log_target", ticketId),
                event.getMember(), null, reason);

        event.replyEmbeds(TicketCloseFlow
                        .buildClosedEmbed(guildId, event.getUser().getAsMention(), reason, null).build())
                .setComponents(ActionRow.of(TicketCloseFlow.buildDeleteButton(guildId)))
                .queue();

        renameToClosed(channel);
    }

    private void handleAssignTicket(SlashCommandInteractionEvent event, String guildId) {
        TextChannel channel = event.getChannel().asTextChannel();
        Integer ticketId = handler.getTicketIdByChannelId(channel.getId());

        if (ticketId == null) {
            event.reply(t(guildId, "tickets.not_a_ticket")).setEphemeral(true).queue();
            return;
        }

        Member staffMember = Objects.requireNonNull(event.getOption("staff")).getAsMember();
        if (staffMember == null) {
            event.reply(t(guildId, "tickets.staff_not_found")).setEphemeral(true).queue();
            return;
        }

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
        Integer ticketId = handler.getTicketIdByChannelId(channel.getId());

        if (ticketId == null) {
            event.reply(t(guildId, "tickets.not_a_ticket")).setEphemeral(true).queue();
            return;
        }

        // Check if user has permission to change priority (support role or manage channels)
        if (!hasSupportAccess(event.getMember(), guildId, channel.getId())) {
            event.reply(t(guildId, "tickets.priority_no_permission")).setEphemeral(true).queue();
            return;
        }

        String newPriority = Objects.requireNonNull(event.getOption("priority")).getAsString();

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
            
            // Sort channels by priority after updating, within the category this ticket
            // actually lives in
            sortTicketChannelsByPriority(event.getGuild(), guildId, channel.getId());
        } else {
            event.reply(t(guildId, "tickets.priority_failed")).setEphemeral(true).queue();
        }
    }

    private void sortTicketChannelsByPriority(Guild guild, String guildId, String ticketChannelId) {
        try {
            // Resolve from the ticket's own panel, so servers configured only through
            // /ticket-panels sort correctly instead of being skipped
            String categoryId = handler.resolveTicketDiscordCategory(guildId, ticketChannelId);
            if (categoryId == null) return;

            Category ticketCategory = guild.getCategoryById(categoryId);
            if (ticketCategory == null) return;

            // Get the ticket panel channel ID
            DatabaseHandler.TicketPanelData panel = handler.getTicketPanelByChannelId(ticketChannelId);
            String ticketPanelChannelId = panel != null && panel.channelId != null
                    ? panel.channelId
                    : handler.getTicketChannel(guildId);
            
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

