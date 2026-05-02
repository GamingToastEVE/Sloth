package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GlobalCommandListener extends ListenerAdapter implements SlashCommandHandler {

    private final DatabaseHandler handler;

    public GlobalCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    // ==================== SLASH COMMAND HANDLER ====================

    @Override
    public String[] getHandledCommands() {
        return new String[]{"global-stats"};
    }

    @Override
    public void handleSlashCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        EmbedBuilder embed = handler.getGlobalStats();
        EmbedBuilder embed2 = handler.getGlobalModStats();
        event.getHook().sendMessageEmbeds(embed.build(), embed2.build()).queue();
    }
}
