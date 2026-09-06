package org.ToastiCodingStuff.Sloth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketCloseFlowTest {

    // ==================== CHANNEL NAMING ====================

    @Test
    void marksAnOpenTicketChannelAsClosed() {
        assertEquals("closed-ticket-42-toasti", TicketCloseFlow.closedChannelName("ticket-42-toasti"));
    }

    @Test
    void renamingAnAlreadyClosedChannelChangesNothing() {
        // Regression: the close button stayed active after closing, so a second click
        // produced "closed-closed-ticket-42-toasti".
        assertEquals("closed-ticket-42-toasti", TicketCloseFlow.closedChannelName("closed-ticket-42-toasti"));
    }

    @Test
    void keepsTheChannelNameWithinDiscordsLimit() {
        String longName = "ticket-42-" + "a".repeat(95);
        String closed = TicketCloseFlow.closedChannelName(longName);

        assertTrue(closed.startsWith("closed-"), "prefix must survive the truncation");
        assertEquals(100, closed.length());
    }

    @Test
    void recognisesClosedChannelNames() {
        assertTrue(TicketCloseFlow.isClosedChannelName("closed-ticket-42-toasti"));
        assertFalse(TicketCloseFlow.isClosedChannelName("ticket-42-toasti"));
    }

    // ==================== CLOSE GUARD ====================

    @Test
    void anOpenTicketCanBeClosed() {
        assertEquals(TicketCloseFlow.CloseDecision.CLOSE, TicketCloseFlow.evaluateClose(42, "OPEN"));
        assertEquals(TicketCloseFlow.CloseDecision.CLOSE, TicketCloseFlow.evaluateClose(42, "IN_PROGRESS"));
    }

    @Test
    void aClosedTicketCannotBeClosedAgain() {
        // Regression: the close button stayed active, so every extra click closed the
        // ticket again - re-posting the embed and inflating the closed-tickets statistic.
        assertEquals(TicketCloseFlow.CloseDecision.ALREADY_CLOSED, TicketCloseFlow.evaluateClose(42, "CLOSED"));
    }

    @Test
    void aChannelWithoutATicketCannotBeClosed() {
        assertEquals(TicketCloseFlow.CloseDecision.NOT_A_TICKET, TicketCloseFlow.evaluateClose(null, null));
    }

    @Test
    void aTicketWithoutAStatusIsTreatedAsOpen() {
        assertEquals(TicketCloseFlow.CloseDecision.CLOSE, TicketCloseFlow.evaluateClose(42, null));
    }

    // ==================== DELETE GUARD ====================

    @Test
    void onlyAClosedTicketChannelMayBeDeleted() {
        assertEquals(TicketCloseFlow.DeleteDecision.DELETE,
                TicketCloseFlow.evaluateDelete(42, "CLOSED", "closed-ticket-42-toasti"));
        assertEquals(TicketCloseFlow.DeleteDecision.NOT_CLOSED,
                TicketCloseFlow.evaluateDelete(42, "OPEN", "ticket-42-toasti"));
    }

    @Test
    void aClosedTicketIsDeletableEvenAfterTheChannelWasRenamed() {
        // The old check only looked at the "closed-" prefix, so renaming the channel
        // locked staff out of the delete button.
        assertEquals(TicketCloseFlow.DeleteDecision.DELETE,
                TicketCloseFlow.evaluateDelete(42, "CLOSED", "archive-toasti"));
    }

    @Test
    void anOrphanedChannelFallsBackToItsName() {
        // The ticket row is gone (e.g. wiped via /data delete) but the channel remains.
        assertEquals(TicketCloseFlow.DeleteDecision.DELETE,
                TicketCloseFlow.evaluateDelete(null, null, "closed-ticket-42-toasti"));
        assertEquals(TicketCloseFlow.DeleteDecision.NOT_A_TICKET,
                TicketCloseFlow.evaluateDelete(null, null, "general"));
    }
}
