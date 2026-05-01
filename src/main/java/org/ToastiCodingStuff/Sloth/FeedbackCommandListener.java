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
    public void onSlashCommandInteraction (SlashCommandInteractionEvent event) {
        if (event.getName().equals("feedback")) {
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
            PrivateChannel channel = Objects.requireNonNull(guild.getOwner()).getUser().openPrivateChannel().complete();
            channel.sendMessageEmbeds(eb2.build()).queue();
        }
    }
}
