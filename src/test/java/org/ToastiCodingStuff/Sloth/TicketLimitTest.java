package org.ToastiCodingStuff.Sloth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketLimitTest {

    @Test
    void underTheLimitIsAllowed() {
        assertFalse(TicketCreationListener.hasReachedTicketLimit(0, 1));
        assertFalse(TicketCreationListener.hasReachedTicketLimit(2, 3));
    }

    @Test
    void atOrOverTheLimitIsBlocked() {
        assertTrue(TicketCreationListener.hasReachedTicketLimit(1, 1));
        assertTrue(TicketCreationListener.hasReachedTicketLimit(5, 3));
    }

    @Test
    void zeroMeansUnlimited() {
        // Regression: the setup wizard stores 0 for its "unlimited" option. The old check
        // was "openTickets >= max", so 0 >= 0 blocked every single ticket.
        assertFalse(TicketCreationListener.hasReachedTicketLimit(0, 0));
        assertFalse(TicketCreationListener.hasReachedTicketLimit(42, 0));
    }

    @Test
    void negativeLimitAlsoMeansUnlimited() {
        assertFalse(TicketCreationListener.hasReachedTicketLimit(7, -1));
    }
}
