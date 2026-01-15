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
        try {
            reminderId = Integer.parseInt(selectedValue);
        } catch (NumberFormatException e) {
            event.reply("❌ ID Error.").setEphemeral(true).queue();
            return;
        }

        DatabaseHandler.ReminderData reminder = handler.getReminder(reminderId);

        if (reminder == null) {
            event.reply("❌ This reminder does not exist anymore.").setEphemeral(true).queue();
            return;
        }

        // Sicherheitscheck: Gehört der Reminder dem User?
        if (!reminder.userId.equals(event.getUser().getId())) {
            event.reply("❌ This is not your reminder.").setEphemeral(true).queue();
            return;
        }

        long unixSec = reminder.remindAt.getTime() / 1000;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🔔 Reminder Details");
        embed.setColor(Color.ORANGE);
        embed.addField("Title", reminder.title.isEmpty() ? "(No title)" : reminder.title, false);
        embed.addField("Message", reminder.message, false);
        embed.addField("In", "<t:" + unixSec + ":R> (" + "<t:" + unixSec + ":F>)", false);
        embed.addField("Type", reminder.dm ? "Per DM" : "In Server-Channel", true);
        embed.setFooter("ID: " + reminderId);

        // Delete Button hinzufügen
        Button deleteBtn = Button.danger("reminder_delete:" + reminderId, "Delete").withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🗑️"));

        event.replyEmbeds(embed.build())
                .setComponents(ActionRow.of(deleteBtn))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("reminder_delete:")) return;

        String[] parts = event.getComponentId().split(":");
        if (parts.length != 2) {
            event.reply("❌ ID Error.").setEphemeral(true).queue();
            return;
        }

        int reminderId;
        try {
            reminderId = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            event.reply("❌ ID Error.").setEphemeral(true).queue();
            return;
        }

        // Versuche den Reminder zu löschen
        boolean success = handler.deleteReminder(reminderId, event.getUser().getId());

        if (success) {
            event.reply("🗑️ Reminder with ID " + reminderId + " got deleted.").setEphemeral(true).queue();
        } else {
            event.reply("❌ Could not find reminder.").setEphemeral(true).queue();
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
        String regex = "^(\\d+[dmhDMH]|\\d{4}-\\d{2}-\\d{2}|\\d{2}\\.\\d{2}\\.\\d{4})$";
        if (timeStr.matches(regex)) {
            secondsToAdd = parseDuration(timeStr);
            if (secondsToAdd <= 0) {
                secondsToAdd = parseDate(timeStr);
            }
        }

        if (secondsToAdd <= 0) {
            event.reply("❌ Couldn't convert time, please use: `10m`, `1h`, `2d`.").setEphemeral(true).queue();
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
        event.reply("✅ I will remind you in <t:" + timestampSeconds + ":R> of this:\n`" + title + "\n" + message + "`" + (dm ? " (per DM)" : ""))
                .setEphemeral(true)
                .queue();
    }

    private void handleListReminders(SlashCommandInteractionEvent event, String userId) {
        List<DatabaseHandler.ReminderData> reminders = handler.getUserReminders(userId);

        if (reminders.isEmpty()) {
            event.reply("📭 You do not have any active reminders.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⏰ Your reminders");
        embed.setColor(Color.CYAN);

        StringBuilder desc = new StringBuilder();
        for (DatabaseHandler.ReminderData rem : reminders) {
            long unixSec = rem.remindAt.getTime() / 1000;
            desc.append("**ID: ").append(rem.id).append("** | <t:").append(unixSec).append(":R>\n")
                    .append(rem.title.isEmpty() ? "" : "**" + rem.title + "**\n")
                    .append("📝 `").append(rem.message).append("`\n")
                    .append(rem.dm ? "📩 via DM" : "📢 in Channel")
                    .append("\n\n");
        }
        embed.setDescription(desc.toString());

        StringSelectMenu.Builder selectMenu = StringSelectMenu.create("reminder_list_select")
                .setPlaceholder("Select a reminder to view details")
                .setMinValues(1)
                .setMaxValues(1);

        for (DatabaseHandler.ReminderData rem : reminders) {
            String label = "ID " + rem.id + " | " + (rem.title.isEmpty() ? "(No title)" : rem.title);
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

        if (success) {
            event.reply("🗑️ Reminder with ID " + id + " got deleted.").setEphemeral(true).queue();
        } else {
            event.reply("❌ Could not find reminder.").setEphemeral(true).queue();
        }
    }

    // Helper Methode für DMs
    private void sendReminderList(String userId, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
        List<DatabaseHandler.ReminderData> reminders = handler.getUserReminders(userId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⏰ Your active reminders");
        embed.setColor(Color.CYAN);

        if (reminders.isEmpty()) {
            embed.setDescription("You do not have any active reminders.");
        } else {
            StringBuilder desc = new StringBuilder();
            for (DatabaseHandler.ReminderData rem : reminders) {
                long unixSec = rem.remindAt.getTime() / 1000;
                desc.append("**ID: ").append(rem.id).append("** | <t:").append(unixSec).append(":R>\n")
                        .append(rem.title.isEmpty() ? "" : "**" + rem.title + "**\n")
                        .append("📝 `").append(rem.message).append("`\n\n");
            }
            embed.setDescription(desc.toString());
            embed.setFooter("Delete Reminders by going into the details via the /reminder list command.");
        }

        StringSelectMenu.Builder selectMenu = StringSelectMenu.create("reminder_list_select")
                .setPlaceholder("Select a reminder to view details")
                .setMinValues(1)
                .setMaxValues(1);

        for (DatabaseHandler.ReminderData rem : reminders) {
            String label = "ID " + rem.id + " | " + (rem.title.isEmpty() ? "(No title)" : rem.title);
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
    private long parseDuration(String input) {
        Pattern p = Pattern.compile("(\\d+)([smhd])");
        Matcher m = p.matcher(input.toLowerCase());

        long totalSeconds = 0;
        boolean found = false;

        while (m.find()) {
            found = true;
            int amount = Integer.parseInt(m.group(1));
            String unit = m.group(2);
            switch (unit) {
                case "s": totalSeconds += amount; break;
                case "m": totalSeconds += amount * 60L; break;
                case "h": totalSeconds += amount * 3600L; break;
                case "d": totalSeconds += amount * 86400L; break;
            }
        }
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
