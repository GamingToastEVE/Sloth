package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class GlobalCommandListener extends ListenerAdapter {
    private final DatabaseHandler handler;

    public GlobalCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent event) {
        if (!event.getName().equals("global-stats")) {return;}

        EmbedBuilder embed = handler.getGlobalStats();
        EmbedBuilder embed2 = handler.getGlobalModStats();
        event.replyEmbeds(embed.build()).setEphemeral(false).queue();
        event.getChannel().sendMessageEmbeds(embed2.build()).queue();
    }
}
