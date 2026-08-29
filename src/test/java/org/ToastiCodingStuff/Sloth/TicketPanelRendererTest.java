package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketPanelRendererTest {

    // ==================== EMOJI RESOLUTION ====================

    @Test
    void nullEmojiResolvesToNone() {
        // button_emoji has no default in the schema, so panels created via the
        // "Create Panel" modal store NULL here
        assertNull(TicketPanelRenderer.resolveEmoji(null));
    }

    @Test
    void blankEmojiResolvesToNone() {
        assertNull(TicketPanelRenderer.resolveEmoji(""));
        assertNull(TicketPanelRenderer.resolveEmoji("   "));
    }

    @Test
    void legacySentinelResolvesToNone() {
        // Older rows store this placeholder text instead of NULL
        assertNull(TicketPanelRenderer.resolveEmoji("No emoji selected"));
    }

    @Test
    void malformedCustomEmojiResolvesToNone() {
        assertNull(TicketPanelRenderer.resolveEmoji("<:broken>"));
        assertNull(TicketPanelRenderer.resolveEmoji("<:name:notanumber>"));
    }

    @Test
    void unicodeEmojiResolves() {
        assertNotNull(TicketPanelRenderer.resolveEmoji("🎫"));
    }

    @Test
    void customEmojiResolves() {
        assertNotNull(TicketPanelRenderer.resolveEmoji("<:sloth:123456789>"));
        assertNotNull(TicketPanelRenderer.resolveEmoji("<a:sloth:123456789>"));
    }

    @Test
    void isValidEmojiAgreesWithResolveEmoji() {
        assertFalse(TicketPanelRenderer.isValidEmoji(null));
        assertFalse(TicketPanelRenderer.isValidEmoji("No emoji selected"));
        assertTrue(TicketPanelRenderer.isValidEmoji("🎫"));
    }

    // ==================== BUTTON BUILDING ====================

    @Test
    void buttonWithoutEmojiIsBuiltInsteadOfThrowing() {
        // Regression: the old code called panel.buttonEmoji.equals(...) directly, which
        // threw a NullPointerException for every panel created without an emoji
        Button button = assertDoesNotThrow(
                () -> TicketPanelRenderer.buildButton("PRIMARY", "create_ticket_1", "Open Ticket", null));

        assertEquals("create_ticket_1", button.getCustomId());
        assertEquals("Open Ticket", button.getLabel());
        assertNull(button.getEmoji());
    }

    @Test
    void buttonKeepsAValidEmoji() {
        Button button = TicketPanelRenderer.buildButton("SUCCESS", "ticket_cat_7", "Support", "🎫");

        assertNotNull(button.getEmoji());
        assertEquals(ButtonStyle.SUCCESS, button.getStyle());
    }

    @Test
    void buttonDropsAnUnusableEmojiButKeepsTheLabel() {
        Button button = TicketPanelRenderer.buildButton("DANGER", "ticket_cat_8", "Report", "<:broken>");

        assertNull(button.getEmoji());
        assertEquals("Report", button.getLabel());
    }

    // ==================== COLORS AND STYLES ====================

    @Test
    void colorParsesWithAndWithoutHash() {
        assertEquals(new Color(0x5865F2), TicketPanelRenderer.parseColor("#5865F2"));
        assertEquals(new Color(0x5865F2), TicketPanelRenderer.parseColor("5865F2"));
    }

    @Test
    void unusableColorFallsBackToBlurple() {
        Color fallback = new Color(88, 101, 242);

        assertEquals(fallback, TicketPanelRenderer.parseColor(null));
        assertEquals(fallback, TicketPanelRenderer.parseColor(""));
        assertEquals(fallback, TicketPanelRenderer.parseColor("not a color"));
    }

    @Test
    void buttonStyleNamesAndAliasesMap() {
        assertEquals(ButtonStyle.SUCCESS, TicketPanelRenderer.getButtonStyle("SUCCESS"));
        assertEquals(ButtonStyle.SUCCESS, TicketPanelRenderer.getButtonStyle("green"));
        assertEquals(ButtonStyle.DANGER, TicketPanelRenderer.getButtonStyle("RED"));
        assertEquals(ButtonStyle.SECONDARY, TicketPanelRenderer.getButtonStyle("grey"));
        assertEquals(ButtonStyle.PRIMARY, TicketPanelRenderer.getButtonStyle(null));
        assertEquals(ButtonStyle.PRIMARY, TicketPanelRenderer.getButtonStyle("something else"));
    }
}
