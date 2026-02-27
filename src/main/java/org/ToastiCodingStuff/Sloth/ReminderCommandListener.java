package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReminderCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public ReminderCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    // ==================== LANGUAGE HELPER METHODS ====================

    private String t(String guildId, String key) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            if (guildId != null) {
                return lang.get(guildId, key);
            }
            return lang.getTranslation(LanguageManager.DEFAULT_LANGUAGE, key);
        }
        return key;
    }

    private String t(String guildId, String key, Object... args) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            if (guildId != null) {
                return lang.get(guildId, key, args);
            }
            String translation = lang.getTranslation(LanguageManager.DEFAULT_LANGUAGE, key);
            try {
                return String.format(translation, args);
            } catch (Exception e) {
                return translation;
            }
        }
        try {
            return String.format(key, args);
        } catch (Exception e) {
            return key;
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("reminder")) return;

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        String userId = event.getUser().getId();
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;

        switch (subcommand) {
            case "set":
                handleSetReminder(event, userId, guildId);
                break;
            case "list":
                handleListReminders(event, userId);
                break;
            case "remove":
                handleRemoveReminder(event, userId);
                break;
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignoriere Bots
        if (event.getAuthor().isBot()) return;

        // Prüfe ob es eine DM ist
        if (event.isFromType(ChannelType.PRIVATE)) {
            // Wir antworten mit der Reminder Liste
            sendReminderList(event.getAuthor().getId(), event.getChannel());
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("reminder_list_select")) return;

        String selectedValue = event.getValues().get(0); // Die ID des Reminders
        int reminderId;
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        try {
            reminderId = Integer.parseInt(selectedValue);
        } catch (NumberFormatException e) {
            event.reply(t(guildId, "reminders.id_error")).setEphemeral(true).queue();
            return;
        }

        DatabaseHandler.ReminderData reminder = handler.getReminder(reminderId);

        if (reminder == null) {
            event.reply(t(guildId, "reminders.not_found")).setEphemeral(true).queue();
            return;
        }

        // Sicherheitscheck: Gehört der Reminder dem User?
        if (!reminder.userId.equals(event.getUser().getId())) {
            event.reply(t(guildId, "reminders.not_yours")).setEphemeral(true).queue();
            return;
        }

        long unixSec = reminder.remindAt.getTime() / 1000;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(t(guildId, "reminders.details_title"));
        embed.setColor(Color.ORANGE);
        embed.addField(t(guildId, "reminders.field_title"), reminder.title.isEmpty() ? t(guildId, "reminders.no_title") : reminder.title, false);
        embed.addField(t(guildId, "reminders.field_message"), reminder.message, false);
        embed.addField(t(guildId, "reminders.field_time"), "<t:" + unixSec + ":R> (" + "<t:" + unixSec + ":F>)", false);
        embed.addField(t(guildId, "reminders.field_type"), reminder.dm ? t(guildId, "reminders.type_dm") : t(guildId, "reminders.type_channel"), true);
        embed.setFooter("ID: " + reminderId);

        // Delete Button hinzufügen
        Button deleteBtn = Button.danger("reminder_delete:" + reminderId, t(guildId, "reminders.btn_delete")).withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🗑️"));

        event.replyEmbeds(embed.build())
                .setComponents(ActionRow.of(deleteBtn))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("reminder_delete:")) return;

        String[] parts = event.getComponentId().split(":");
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        if (parts.length != 2) {
            event.reply(t(guildId, "reminders.id_error")).setEphemeral(true).queue();
            return;
        }

        int reminderId;
        try {
            reminderId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            event.reply(t(guildId, "reminders.id_error")).setEphemeral(true).queue();
            return;
        }

        // Versuche den Reminder zu löschen
        boolean success = handler.deleteReminder(reminderId, event.getUser().getId());

        if (success) {
            event.reply(t(guildId, "reminders.deleted_success", reminderId)).setEphemeral(true).queue();
        } else {
            event.reply(t(guildId, "reminders.delete_failed")).setEphemeral(true).queue();
        }
    }

    private void handleSetReminder(SlashCommandInteractionEvent event, String userId, String guildId) {
        String title = event.getOption("title").getAsString();
        String timeStr = event.getOption("time").getAsString();
        String message = "";
        if (event.getOption("message") != null) {
            message = event.getOption("message").getAsString();
        }
        boolean dm = true;
        if (event.getOption("dm") != null && event.getOption("dm").getAsBoolean()) {
            dm = event.getOption("dm").getAsBoolean();
        }

        long secondsToAdd = 0;

        // check if timeStr is date or duration
        String regex = "(?i)^((\\d+\\s*[dhms]\\s*)+|\\d{4}-\\d{2}-\\d{2}|\\d{2}\\.\\d{2}\\.\\d{4})$";

        if (timeStr.trim().matches(regex)) {
            // Unterscheidung: Ist es ein Datum oder eine Dauer?
            if (timeStr.contains("-") || timeStr.contains(".")) {
                // Es ist ein Datum (z.B. 2024-01-01)
                // Hier musst du berechnen: Datum - Jetzt = Sekunden
                secondsToAdd = parseDate(timeStr);
            } else {
                // Es ist eine Dauer (z.B. 1h30m)
                secondsToAdd = parseDuration(timeStr);
            }
        } else {
            // Fehlerbehandlung: Format nicht erkannt
            System.out.println("Ungültiges Format: " + timeStr);
        }

        if (secondsToAdd <= 0) {
            event.reply(t(guildId, "reminders.invalid_time")).setEphemeral(true).queue();
            return;
        }

        long futureTimeMillis = System.currentTimeMillis() + (secondsToAdd * 1000);
        Timestamp remindAt = new Timestamp(futureTimeMillis);
        String channelId = event.getChannel().getId();

        if (!dm) {
            channelId = "DM";
        }

        handler.addReminder(userId, guildId, channelId, title, message, dm, remindAt);

        long timestampSeconds = futureTimeMillis / 1000;
        event.reply(t(guildId, "reminders.set_success", "<t:" + timestampSeconds + ":R>") +
                    "\n`" + title + "\n" + message + "`" + (dm ? " (DM)" : ""))
                .setEphemeral(true)
                .queue();
    }

    private void handleListReminders(SlashCommandInteractionEvent event, String oderId) {
        List<DatabaseHandler.ReminderData> reminders = handler.getUserReminders(oderId);
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;

        EmbedBuilder embed = new EmbedBuilder();

        if (guildId == null) {
            if (reminders.isEmpty()) {
                event.reply(t(guildId, "reminders.empty")).setEphemeral(true).queue();
                return;
            }
            embed.setTitle(t(guildId, "reminders.active_title"));
            embed.setColor(Color.CYAN);
        } else {
            if (reminders.isEmpty()) {
                event.reply(t(guildId, "reminders.no_reminders")).setEphemeral(true).queue();
                return;
            }
            embed.setTitle(t(guildId, "reminders.list_title"));
            embed.setColor(Color.CYAN);
        }

        StringBuilder desc = new StringBuilder();
        for (DatabaseHandler.ReminderData rem : reminders) {
            long unixSec = rem.remindAt.getTime() / 1000;
            desc.append("**ID: ").append(rem.id).append("** | <t:").append(unixSec).append(":R>\n")
                    .append(rem.title.isEmpty() ? "" : "**" + rem.title + "**\n")
                    .append("📝 `").append(rem.message).append("`\n")
                    .append(rem.dm ? "📩 DM" : "📢 Channel")
                    .append("\n\n");
        }
        embed.setDescription(desc.toString());

        StringSelectMenu.Builder selectMenu = StringSelectMenu.create("reminder_list_select")
                .setPlaceholder(t(guildId, "reminders.select_placeholder"))
                .setMinValues(1)
                .setMaxValues(1);

        for (DatabaseHandler.ReminderData rem : reminders) {
            String label = "ID " + rem.id + " | " + (rem.title.isEmpty() ? t(guildId, "reminders.no_title") : rem.title);
            if (label.length() > 100) {
                label = label.substring(0, 50) + "...";
            }
            selectMenu.addOption(label, String.valueOf(rem.id));
        }
        event.replyEmbeds(embed.build()).setComponents(ActionRow.of(selectMenu.build())).setEphemeral(true).queue();
    }

    private void handleRemoveReminder(SlashCommandInteractionEvent event, String userId) {
        int id = event.getOption("id").getAsInt();
        boolean success = handler.deleteReminder(id, userId);
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;

        if (success) {
            event.reply(t(guildId, "reminders.deleted_success", id)).setEphemeral(true).queue();
        } else {
            event.reply(t(guildId, "reminders.delete_failed")).setEphemeral(true).queue();
        }
    }

    // Helper Methode für DMs
    private void sendReminderList(String userId, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
        List<DatabaseHandler.ReminderData> reminders = handler.getUserReminders(userId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(t(null, "reminders.active_title"));
        embed.setColor(Color.CYAN);

        if (reminders.isEmpty()) {
            embed.setDescription(t(null, "reminders.no_active_reminders"));
        } else {
            StringBuilder desc = new StringBuilder();
            for (DatabaseHandler.ReminderData rem : reminders) {
                long unixSec = rem.remindAt.getTime() / 1000;
                desc.append("**ID: ").append(rem.id).append("** | <t:").append(unixSec).append(":R>\n")
                        .append(rem.title.isEmpty() ? "" : "**" + rem.title + "**\n")
                        .append("📝 `").append(rem.message).append("`\n\n");
            }
            embed.setDescription(desc.toString());
            embed.setFooter(t(null, "reminders.delete_footer"));
        }

        StringSelectMenu.Builder selectMenu = StringSelectMenu.create("reminder_list_select")
                .setPlaceholder(t(null, "reminders.select_placeholder"))
                .setMinValues(1)
                .setMaxValues(1);

        for (DatabaseHandler.ReminderData rem : reminders) {
            String label = "ID " + rem.id + " | " + (rem.title.isEmpty() ? t(null, "reminders.no_title") : rem.title);
            if (label.length() > 100) {
                label = label.substring(0, 50) + "...";
            }
            selectMenu.addOption(label, String.valueOf(rem.id));
        }

        if (selectMenu.getOptions().isEmpty()) {
            channel.sendMessageEmbeds(embed.build()).queue();
            return;
        }

        channel.sendMessageEmbeds(embed.build()).setComponents(ActionRow.of(selectMenu.build())).queue();
    }

    // Simple Regex Parser für "10m", "1h" etc.
    // update to support multiple units like "1h30m"
    private long parseDuration(String input) {
        if (input == null || input.trim().isEmpty()) {
            return 0;
        }

        // Update: Added \\s* to allow spaces between digits and unit (e.g., "1 h")
        Pattern p = Pattern.compile("(\\d+)\\s*([smhd])");
        Matcher m = p.matcher(input.toLowerCase());

        long totalSeconds = 0;
        boolean found = false;

        while (m.find()) {
            found = true;
            // Update: Use parseLong to prevent overflow on large inputs
            long amount = Long.parseLong(m.group(1));
            String unit = m.group(2);

            switch (unit) {
                case "s": totalSeconds += amount; break;
                case "m": totalSeconds += amount * 60L; break;
                case "h": totalSeconds += amount * 3600L; break;
                case "d": totalSeconds += amount * 86400L; break;
            }
        }
        System.out.println("Parsed duration: " + totalSeconds + " seconds from input: " + input);
        return found ? totalSeconds : 0;
    }

    private long parseDate(String input) {
        List<DateTimeFormatter> FORMATS = Arrays.asList(
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd.MM.yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("yyyy/MM/dd"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy")
        );
        if (input == null || input.isEmpty()) return 0;

        for (DateTimeFormatter formatter : FORMATS) {
            try {
                LocalDate date = LocalDate.parse(input, formatter);
                LocalDateTime dateTime = date.atStartOfDay();
                return ChronoUnit.SECONDS.between(LocalDateTime.now(), dateTime);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        return 0;
    }
}
