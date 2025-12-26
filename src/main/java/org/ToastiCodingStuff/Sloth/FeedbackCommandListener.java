package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.time.Instant;
import java.util.Objects;

public class FeedbackCommandListener extends ListenerAdapter {

    private Guild guild;

    public FeedbackCommandListener(Guild guild) {
        this.guild = guild;
    }

    @Override
    public void onSlashCommandInteraction (SlashCommandInteractionEvent event) {
        if (event.getName().equals("feedback")) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setTitle("Feedback");
            eb.setDescription("We value your feedback! Please share your thoughts and suggestions to help us improve. If you want to get updates on implementations, please consider joining the support server linked in the /help command.");
            eb.setColor(Color.GREEN);
            eb.setTimestamp(Instant.now());
            event.replyEmbeds(eb.build()).setEphemeral(true).queue();
            EmbedBuilder eb2 = new EmbedBuilder();
            eb2.setTitle("New Feedback Received");
            eb2.setDescription(Objects.requireNonNull(event.getOption("message")).getAsString());
            eb2.addField("User", event.getUser().getAsTag(), false);
            eb2.addField("User ID", event.getUser().getId(), false);
            eb2.setColor(Color.BLUE);
            eb2.setTimestamp(Instant.now());
            PrivateChannel channel = Objects.requireNonNull(guild.getOwner()).getUser().openPrivateChannel().complete();
            channel.sendMessageEmbeds(eb2.build()).queue();
        }
    }
}
