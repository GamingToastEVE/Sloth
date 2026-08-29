package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the embed and buttons of a ticket panel.
 * <p>
 * Both the ticket panel UI ({@link TicketPanelCommandListener}) and the setup wizard
 * ({@link SetupWizardListener}) render panels through this class, so a panel looks and
 * behaves the same no matter where it was sent from - including category buttons, which
 * the wizard's own copy of this logic used to omit.
 */
public final class TicketPanelRenderer {

    /** Legacy sentinel that older rows use instead of NULL for "no emoji". */
    private static final String NO_EMOJI = "No emoji selected";

    private static final Color DEFAULT_EMBED_COLOR = new Color(88, 101, 242);

    /** Discord allows at most 5 buttons per action row and 5 rows per message. */
    private static final int MAX_BUTTONS_PER_ROW = 5;
    private static final int MAX_ROWS = 5;

    private TicketPanelRenderer() {
    }

    /**
     * Build the panel embed. The handler is used to expand stored linebreak markers.
     */
    public static EmbedBuilder buildEmbed(DatabaseHandler handler, DatabaseHandler.TicketPanelData panel) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(panel.title);
        embed.setDescription(handler.processLinebreaks(panel.description));
        embed.setColor(parseColor(panel.embedColor));

        if (panel.embedFooter != null && !panel.embedFooter.isBlank()) {
            embed.setFooter(panel.embedFooter);
        } else {
            embed.setFooter("Ticket System • " + panel.name);
        }
        if (panel.embedThumbnail != null && !panel.embedThumbnail.isBlank()) {
            embed.setThumbnail(panel.embedThumbnail);
        }
        return embed;
    }

    /**
     * Build the panel's buttons: one button per category, or a single panel-wide button
     * when the panel has no categories.
     */
    public static List<ActionRow> buildComponents(DatabaseHandler handler,
                                                  DatabaseHandler.TicketPanelData panel, int panelId) {
        List<DatabaseHandler.TicketCategoryData> categories = handler.getTicketCategories(panelId);

        if (categories.isEmpty()) {
            Button ticketButton = buildButton(panel.buttonColor, "create_ticket_" + panelId,
                    panel.buttonLabel, panel.buttonEmoji);
            return List.of(ActionRow.of(ticketButton));
        }

        List<ActionRow> actionRows = new ArrayList<>();
        List<Button> currentRowButtons = new ArrayList<>();

        for (DatabaseHandler.TicketCategoryData category : categories) {
            currentRowButtons.add(buildButton(category.buttonColor, "ticket_cat_" + category.id,
                    category.buttonLabel, category.buttonEmoji));

            if (currentRowButtons.size() >= MAX_BUTTONS_PER_ROW) {
                actionRows.add(ActionRow.of(currentRowButtons));
                currentRowButtons = new ArrayList<>();

                if (actionRows.size() >= MAX_ROWS) {
                    return actionRows;
                }
            }
        }

        if (!currentRowButtons.isEmpty() && actionRows.size() < MAX_ROWS) {
            actionRows.add(ActionRow.of(currentRowButtons));
        }
        return actionRows;
    }

    /**
     * Build a single button, attaching the emoji only when one is actually configured
     * and usable. A stored emoji may be NULL, blank, the legacy sentinel, or no longer
     * valid - none of which should stop the button from being rendered.
     */
    public static Button buildButton(String buttonColor, String customId, String label, String storedEmoji) {
        Emoji emoji = resolveEmoji(storedEmoji);
        Button button = Button.of(getButtonStyle(buttonColor), customId, label);
        return emoji != null ? button.withEmoji(emoji) : button;
    }

    /**
     * Turn a stored emoji string into an {@link Emoji}, or null when there is none.
     */
    public static Emoji resolveEmoji(String storedEmoji) {
        if (storedEmoji == null || storedEmoji.isBlank() || storedEmoji.equals(NO_EMOJI)) {
            return null;
        }

        String trimmed = storedEmoji.trim();

        // Custom Discord emoji: <:name:id> or <a:name:id>
        if (trimmed.startsWith("<") && trimmed.endsWith(">") && !trimmed.matches("<a?:[a-zA-Z0-9_]+:\\d+>")) {
            return null;
        }

        try {
            return Emoji.fromFormatted(trimmed);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Whether a string can be used as a button emoji.
     */
    public static boolean isValidEmoji(String emoji) {
        return resolveEmoji(emoji) != null;
    }

    public static Color parseColor(String colorStr) {
        if (colorStr == null || colorStr.isBlank()) {
            return DEFAULT_EMBED_COLOR;
        }
        try {
            return Color.decode(colorStr.startsWith("#") ? colorStr : "#" + colorStr);
        } catch (Exception e) {
            return DEFAULT_EMBED_COLOR;
        }
    }

    public static ButtonStyle getButtonStyle(String style) {
        if (style == null) {
            return ButtonStyle.PRIMARY;
        }
        return switch (style.toUpperCase()) {
            case "SUCCESS", "GREEN" -> ButtonStyle.SUCCESS;
            case "DANGER", "RED" -> ButtonStyle.DANGER;
            case "SECONDARY", "GRAY", "GREY" -> ButtonStyle.SECONDARY;
            default -> ButtonStyle.PRIMARY;
        };
    }
}
