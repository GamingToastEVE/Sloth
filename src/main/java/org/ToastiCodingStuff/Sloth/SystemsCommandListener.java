package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SystemsCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public SystemsCommandListener(DatabaseHandler handler) {
        this.handler = handler;
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
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("systems")) return;

        event.deferReply().queue();

        String guildId = event.getGuild().getId();

        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.getHook().sendMessage(t(guildId, "general.permission_denied")).setEphemeral(true).queue();
            return;
        }

        Map<String, Boolean> statuses = handler.getGuildSystemsStatus(guildId);

        event.getHook().sendMessageEmbeds(buildEmbed(guildId, statuses).build())
                .setComponents(buildButtons(statuses))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("sys_toggle:")) return;

        event.deferReply().queue();

        String guildId = event.getGuild().getId();

        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.getHook().sendMessage(t(guildId, "general.permission_denied")).setEphemeral(true).queue();
            return;
        }

        String systemName = event.getComponentId().split(":")[1];

        // 1. Toggle state in DB
        boolean newState = handler.toggleSystem(guildId, systemName);

        Map<String, Boolean> statuses = handler.getGuildSystemsStatus(guildId);

        System.out.println("New State: " + newState);

        AddGuildSlashCommands cmdUpdater = new AddGuildSlashCommands(event.getGuild(), handler);
        cmdUpdater.updateGuildCommandsFromActiveSystems("");

        event.getMessage().editMessageEmbeds(buildEmbed(guildId, statuses).build())
                .setComponents(buildButtons(statuses))
                .queue();
        event.getHook().deleteOriginal().queue();
    }

    private EmbedBuilder buildEmbed(String guildId, Map<String, Boolean> statuses) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(t(guildId, "systems.title"));

        String descEn = "Click the buttons below to enable or disable specific bot systems for this server.\n" +
                "\n**Note:** Disabling a system will remove its slash commands from the server. \nData will remain intact.\n\n" +
                "**It might take a while for changes to take effect. Refreshing discord can help.**";
        String descDe = "Klicke auf die Buttons unten, um bestimmte Bot-Systeme für diesen Server zu aktivieren oder zu deaktivieren.\n" +
                "\n**Hinweis:** Das Deaktivieren eines Systems entfernt dessen Slash-Befehle vom Server. \nDaten bleiben erhalten.\n\n" +
                "**Es kann eine Weile dauern, bis Änderungen wirksam werden. Das Aktualisieren von Discord kann helfen.**";

        LanguageManager lang = LanguageManager.getInstance();
        embed.setDescription(lang != null && lang.getGuildLanguage(guildId).equals("de") ? descDe : descEn);
        embed.setColor(Color.decode("#2b2d31"));
        embed.setFooter("Sloth Bot Systems Management");

        StringBuilder activeList = new StringBuilder();
        StringBuilder inactiveList = new StringBuilder();

        statuses.forEach((name, active) -> {
            String formattedName = formatSystemName(name);
            if (active) activeList.append("✅ ").append(formattedName).append("\n");
            else inactiveList.append("❌ ").append(formattedName).append("\n");
        });

        if (activeList.length() > 0) embed.addField("🟢 Active Systems", activeList.toString(), true);
        if (inactiveList.length() > 0) embed.addField("🔴 Disabled Systems", inactiveList.toString(), true);

        return embed;
    }

    private List<ActionRow> buildButtons(Map<String, Boolean> statuses) {
        List<Button> buttons = new ArrayList<>();

        // Logical order of buttons
        String[] order = {
                "log-channel", "warn", "ticket", "mod", "stats",
                "verify-button", "select-roles", "temprole", "role-event",
                "embed", "reminders", "leveling"
        };

        for (String sys : order) {
            boolean isActive = statuses.getOrDefault(sys, false);
            String label = formatSystemName(sys);

            if (isActive) {
                buttons.add(Button.success("sys_toggle:" + sys, label).withEmoji(Emoji.fromFormatted("✅")));
            } else {
                buttons.add(Button.danger("sys_toggle:" + sys, label).withEmoji(Emoji.fromFormatted("❌")));
            }
        }

        // Chunk buttons into rows of 5 (Discord limit)
        List<ActionRow> rows = new ArrayList<>();
        List<Button> tempRow = new ArrayList<>();
        for (Button btn : buttons) {
            tempRow.add(btn);
            if (tempRow.size() == 5) {
                rows.add(ActionRow.of(tempRow));
                tempRow = new ArrayList<>();
            }
        }
        if (!tempRow.isEmpty()) rows.add(ActionRow.of(tempRow));

        return rows;
    }

    public void sendSystemMessage(ButtonInteractionEvent event) {
        String guildId = event.getGuild().getId();

        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.getHook().sendMessage(t(guildId, "general.permission_denied")).setEphemeral(true).queue();
        }

        Map<String, Boolean> statuses = handler.getGuildSystemsStatus(guildId);

        event.getHook().sendMessageEmbeds(buildEmbed(guildId, statuses).build())
                .setComponents(buildButtons(statuses))
                .setEphemeral(true)
                .queue();
    }

    private String formatSystemName(String key) {
        return switch (key) {
            case "log-channel" -> "Logging";
            case "warn" -> "Warnings";
            case "ticket" -> "Tickets";
            case "mod" -> "Moderation";
            case "stats" -> "Statistics";
            case "verify-button" -> "Verify";
            case "select-roles" -> "Self Roles";
            case "temprole" -> "Temp Roles";
            case "role-event" -> "Role Events";
            case "embed" -> "Embed Creation";
            case "reminders" -> "Reminders";
            case "leveling" -> "Level/XP System";
            default -> key;
        };
    }
}
