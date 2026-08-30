package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.time.Instant;
import java.util.Objects;

public class FeedbackCommandListener extends ListenerAdapter implements SlashCommandHandler {

    private Guild guild;

    public FeedbackCommandListener(Guild guild) {
        this.guild = guild;
    }

    @Override
    public String[] getHandledCommands() {
        return new String[]{"feedback"};
    }

    // ==================== LANGUAGE HELPER METHODS ====================

    private String t(String guildId, String key) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            return lang.get(guildId, key);
        }
        return key;
    }

    private String t(String guildId, String key, Object... args) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            return lang.get(guildId, key, args);
        }
        try {
            return String.format(key, args);
        } catch (Exception e) {
            return key;
        }
    }

    @Override
    public void handleSlashCommand(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(t(guildId, "feedback.title"));
        eb.setDescription(t(guildId, "feedback.success_description"));
        eb.setColor(Color.GREEN);
        eb.setTimestamp(Instant.now());
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
        EmbedBuilder eb2 = new EmbedBuilder();
        eb2.setTitle(t(guildId, "feedback.new_title"));
        eb2.setDescription(Objects.requireNonNull(event.getOption("message")).getAsString());
        eb2.addField(t(guildId, "feedback.field_user"), event.getUser().getAsTag(), false);
        eb2.addField(t(guildId, "feedback.field_user_id"), event.getUser().getId(), false);
        eb2.setColor(Color.BLUE);
        eb2.setTimestamp(Instant.now());
        // Chained instead of blocking on complete(): the feedback DM is a REST round-trip
        // that must not hold up the thread handling this event
        User owner = event.getJDA().getUserById("365042010626719745");
        if (owner == null) {
            System.err.println("Feedback could not be forwarded: owner user is not cached");
            return;
        }
        owner.openPrivateChannel().queue(
                channel -> channel.sendMessageEmbeds(eb2.build()).queue(),
                error -> System.err.println("Failed to forward feedback: " + error.getMessage()));
    }
}
