package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.util.Map;

/**
 * Transparency command: lets a user see what the bot has stored about them.
 * <p>
 * Always scoped to the invoking user and always ephemeral - there is deliberately no
 * option to look up another user, because that would turn a privacy feature into a way
 * of profiling other members.
 */
public class DataCommandListener extends ListenerAdapter implements SlashCommandHandler {

    private final DatabaseHandler handler;

    public DataCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public String[] getHandledCommands() {
        return new String[]{"data"};
    }

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
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            return;
        }

        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;

        if (subcommand.equals("info")) {
            handler.insertOrUpdateGlobalStatistic("data-info");
            handleDataInfo(event, guildId);
        }
    }

    private void handleDataInfo(SlashCommandInteractionEvent event, String guildId) {
        String userId = event.getUser().getId();

        Map<String, Integer> summary = handler.getUserDataSummary(userId);
        String[] profile = handler.getStoredUserProfile(userId);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(t(guildId, "data.info_title"))
                .setDescription(t(guildId, "data.info_description"))
                .setColor(new Color(0x3498DB))
                .setTimestamp(java.time.Instant.now());

        if (profile != null) {
            StringBuilder profileText = new StringBuilder();
            profileText.append(t(guildId, "data.field_username")).append(": ").append(profile[0]).append("\n");
            if (profile[1] != null && !profile[1].isBlank() && !profile[1].equals("0")) {
                profileText.append(t(guildId, "data.field_discriminator")).append(": ").append(profile[1]).append("\n");
            }
            profileText.append(t(guildId, "data.field_avatar")).append(": ")
                    .append(profile[2] != null && !profile[2].isBlank()
                            ? t(guildId, "data.stored")
                            : t(guildId, "data.not_stored")).append("\n");
            profileText.append(t(guildId, "data.field_first_seen")).append(": ").append(profile[3]);

            embed.addField(t(guildId, "data.section_profile"), profileText.toString(), false);
        } else {
            embed.addField(t(guildId, "data.section_profile"), t(guildId, "data.no_profile"), false);
        }

        StringBuilder records = new StringBuilder();
        records.append(line(guildId, "data.count_warnings", summary.get("warnings")));
        records.append(line(guildId, "data.count_moderation", summary.get("moderation_actions")));
        records.append(line(guildId, "data.count_tickets", summary.get("tickets")));
        records.append(line(guildId, "data.count_role_snapshots", summary.get("role_snapshots")));
        records.append(line(guildId, "data.count_timers", summary.get("active_timers")));
        records.append(line(guildId, "data.count_reminders", summary.get("reminders")));
        records.append(line(guildId, "data.count_levels", summary.get("levels")));
        records.append(line(guildId, "data.count_statistics", summary.get("statistics")));

        embed.addField(t(guildId, "data.section_records"), records.toString(), false);

        embed.addField(t(guildId, "data.section_not_stored"), t(guildId, "data.not_stored_note"), false);
        embed.addField(t(guildId, "data.section_rights"),
                t(guildId, "data.rights_note", DatabaseHandler.GUILD_DATA_RETENTION_DAYS), false);

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private String line(String guildId, String key, Integer count) {
        return "• " + t(guildId, key) + ": **" + (count != null ? count : 0) + "**\n";
    }
}
