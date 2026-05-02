package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Interface für alle Slash-Command-Handler.
 * Wird vom zentralen SlashCommandRouter verwendet, um Events effizient weiterzuleiten.
 */
public interface SlashCommandHandler {

    /**
     * Gibt alle Command-Namen zurück, die dieser Handler verarbeitet.
     */
    String[] getHandledCommands();

    /**
     * Verarbeitet das SlashCommandInteractionEvent.
     */
    void handleSlashCommand(SlashCommandInteractionEvent event);
}

