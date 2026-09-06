package org.ToastiCodingStuff.Sloth;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Closing a ticket must never destroy the channel on the spot.
 * <p>
 * Regression: /ticket close posted its "ticket closed" embed and called channel.delete()
 * in the very next line, so the whole conversation was gone before anyone could read the
 * embed - and without a confirmation step. Deleting a ticket channel is only ever allowed
 * from the delete-button handler, which asks for the click first.
 * <p>
 * There is no seam to drive a JDA event handler from a test, so this guards the rule at
 * the source level, the way LanguageKeyUsageTest guards translation keys.
 */
class TicketClosePathTest {

    private static final Path SOURCE_DIR = Path.of("src", "main", "java", "org", "ToastiCodingStuff", "Sloth");

    /** The body of a method, from its signature up to the start of the next one. */
    private String methodBody(Path file, String signature) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        int start = source.indexOf(signature);
        assumeTrue(start >= 0, signature + " not found in " + file.getFileName());

        int next = source.indexOf("\n    private ", start + signature.length());
        int alsoNext = source.indexOf("\n    public ", start + signature.length());
        if (next < 0 || (alsoNext >= 0 && alsoNext < next)) {
            next = alsoNext;
        }
        return next < 0 ? source.substring(start) : source.substring(start, next);
    }

    @Test
    void theSlashCommandCloseDoesNotDeleteTheChannel() throws IOException {
        String body = methodBody(SOURCE_DIR.resolve("TicketCommandListener.java"),
                "private void handleCloseTicket(SlashCommandInteractionEvent event");

        assertFalse(body.contains("channel.delete()"),
                "/ticket close must leave the channel in place and offer the delete button instead");
    }

    @Test
    void theCloseButtonDoesNotDeleteTheChannel() throws IOException {
        String body = methodBody(SOURCE_DIR.resolve("TicketCreationListener.java"),
                "private void handleCloseTicketButton(ButtonInteractionEvent event");

        assertFalse(body.contains("channel.delete()"),
                "the close button must leave the channel in place and offer the delete button instead");
    }
}
