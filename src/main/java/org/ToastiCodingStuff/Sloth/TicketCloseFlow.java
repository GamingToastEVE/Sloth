package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.Color;

/**
 * Shared rules for closing and deleting a ticket channel.
 * <p>
 * Both the panel close button ({@link TicketCreationListener}) and {@code /ticket close}
 * ({@link TicketCommandListener}) run through this class, so a ticket closes the same way
 * no matter which one was used - including the guards that stop a ticket from being closed
 * twice.
 */
public final class TicketCloseFlow {

    /** Prefix a ticket channel carries once its ticket has been closed. */
    public static final String CLOSED_PREFIX = "closed-";

    /** Discord rejects channel names longer than this. */
    private static final int MAX_CHANNEL_NAME_LENGTH = 100;

    private TicketCloseFlow() {
    }

    /**
     * The name a ticket channel should carry after being closed.
     * <p>
     * Idempotent: closing an already closed channel must not stack up prefixes, which
     * would eventually push the name past Discord's length limit and make the rename fail.
     */
    public static String closedChannelName(String currentName) {
        if (isClosedChannelName(currentName)) {
            return currentName;
        }

        String closed = CLOSED_PREFIX + currentName;
        return closed.length() <= MAX_CHANNEL_NAME_LENGTH
                ? closed
                : closed.substring(0, MAX_CHANNEL_NAME_LENGTH);
    }

    public static boolean isClosedChannelName(String channelName) {
        return channelName != null && channelName.startsWith(CLOSED_PREFIX);
    }

    /** Ticket status stored for a ticket that is no longer open. */
    public static final String STATUS_CLOSED = "CLOSED";

    /** What should happen when someone asks to close the ticket of a channel. */
    public enum CloseDecision { CLOSE, ALREADY_CLOSED, NOT_A_TICKET }

    /** What should happen when someone asks to delete a ticket channel. */
    public enum DeleteDecision { DELETE, NOT_CLOSED, NOT_A_TICKET }

    /**
     * Decide whether a close request should go through.
     *
     * @param ticketId the ticket of the channel, or null when the channel is not a ticket
     * @param status   the ticket's stored status; a missing status counts as open
     */
    public static CloseDecision evaluateClose(Integer ticketId, String status) {
        if (ticketId == null) {
            return CloseDecision.NOT_A_TICKET;
        }
        if (isClosedStatus(status)) {
            return CloseDecision.ALREADY_CLOSED;
        }
        return CloseDecision.CLOSE;
    }

    /**
     * Decide whether a ticket channel may be deleted.
     * <p>
     * The stored status decides, so a renamed channel stays deletable. Only when the
     * ticket row is gone - a wiped guild, a manually created channel - does the
     * "closed-" prefix stand in for it.
     */
    public static DeleteDecision evaluateDelete(Integer ticketId, String status, String channelName) {
        if (ticketId == null) {
            return isClosedChannelName(channelName) ? DeleteDecision.DELETE : DeleteDecision.NOT_A_TICKET;
        }
        return isClosedStatus(status) ? DeleteDecision.DELETE : DeleteDecision.NOT_CLOSED;
    }

    private static boolean isClosedStatus(String status) {
        return STATUS_CLOSED.equalsIgnoreCase(status);
    }

    // ==================== SHARED MESSAGE PARTS ====================

    /** Custom id of the delete button this class emits. */
    public static final String DELETE_BUTTON_ID = "delete_ticket_channel";

    /** Custom id emitted by older versions; still sitting under messages in live servers. */
    public static final String LEGACY_DELETE_BUTTON_ID = "delete_channel";

    public static boolean isDeleteButtonId(String customId) {
        return DELETE_BUTTON_ID.equals(customId) || LEGACY_DELETE_BUTTON_ID.equals(customId);
    }

    /**
     * The embed posted into a ticket channel once the ticket was closed.
     *
     * @param reason the close reason, or null when the ticket was closed without one
     * @param footer the panel name, or null when the ticket has no panel
     */
    public static EmbedBuilder buildClosedEmbed(String guildId, String closerMention, String reason, String footer) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(t(guildId, "tickets.close_title"))
                .setDescription(t(guildId, "tickets.close_description", closerMention))
                .setColor(Color.RED);

        if (reason != null && !reason.isBlank()) {
            embed.addField(t(guildId, "general.reason"), reason, false);
        }
        embed.addField(t(guildId, "tickets.closed_at"), "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true);

        if (footer != null && !footer.isBlank()) {
            embed.setFooter(footer);
        }
        return embed;
    }

    public static Button buildDeleteButton(String guildId) {
        return Button.danger(DELETE_BUTTON_ID, t(guildId, "tickets.delete_channel_btn"));
    }

    private static String t(String guildId, String key, Object... args) {
        LanguageManager lang = LanguageManager.getInstance();
        return lang != null ? lang.get(guildId, key, args) : key;
    }
}
