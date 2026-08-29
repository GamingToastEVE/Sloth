package org.ToastiCodingStuff.Sloth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The typed confirmation guarding /data delete. Getting this wrong in either
 * direction is bad: too strict and nobody can delete their data, too loose and an
 * irreversible deletion happens on a stray click.
 */
class DataDeleteConfirmationTest {

    private static final String KEYWORD = "DELETE";

    @Test
    void exactWordConfirms() {
        assertTrue(DataCommandListener.isConfirmationValid("DELETE", KEYWORD));
    }

    @Test
    void surroundingSpacesAreForgiven() {
        assertTrue(DataCommandListener.isConfirmationValid("  DELETE  ", KEYWORD));
        assertTrue(DataCommandListener.isConfirmationValid("DELETE\n", KEYWORD));
    }

    @Test
    void caseIsIgnored() {
        assertTrue(DataCommandListener.isConfirmationValid("delete", KEYWORD));
        assertTrue(DataCommandListener.isConfirmationValid("Delete", KEYWORD));
    }

    @Test
    void anythingElseDoesNotConfirm() {
        assertFalse(DataCommandListener.isConfirmationValid("", KEYWORD));
        assertFalse(DataCommandListener.isConfirmationValid("   ", KEYWORD));
        assertFalse(DataCommandListener.isConfirmationValid("yes", KEYWORD));
        assertFalse(DataCommandListener.isConfirmationValid("löschen", KEYWORD));
    }

    @Test
    void aNearMissDoesNotConfirm() {
        // Substrings and supersets must not pass - only the word itself
        assertFalse(DataCommandListener.isConfirmationValid("DEL", KEYWORD));
        assertFalse(DataCommandListener.isConfirmationValid("DELETE ME", KEYWORD));
        assertFalse(DataCommandListener.isConfirmationValid("DELETEE", KEYWORD));
    }

    @Test
    void missingInputDoesNotConfirm() {
        assertFalse(DataCommandListener.isConfirmationValid(null, KEYWORD));
        assertFalse(DataCommandListener.isConfirmationValid("DELETE", null));
    }
}
