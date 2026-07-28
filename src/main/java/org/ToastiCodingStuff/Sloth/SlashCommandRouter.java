package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zentraler Slash-Command-Router.
 * Empfängt alle SlashCommandInteractionEvents einmalig und leitet sie
 * anhand des Command-Namens an den zuständigen Handler weiter.
 * Dadurch durchläuft jedes Event nur einen einzigen Listener statt alle.
 */
public class SlashCommandRouter extends ListenerAdapter {

    private final Map<String, SlashCommandHandler> handlerMap = new HashMap<>();

    public SlashCommandRouter(List<SlashCommandHandler> handlers) {
        for (SlashCommandHandler handler : handlers) {
            for (String command : handler.getHandledCommands()) {
                handlerMap.put(command, handler);
            }
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        SlashCommandHandler handler = handlerMap.get(event.getName());
        if (handler != null) {
            handler.handleSlashCommand(event);
        }
    }
}

