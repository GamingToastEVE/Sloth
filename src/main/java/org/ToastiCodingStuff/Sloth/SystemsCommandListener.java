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

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("systems")) return;

        event.deferReply().queue();

        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.getHook().sendMessage("❌ You need **Manage Server** permission to manage bot systems.").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        Map<String, Boolean> statuses = handler.getGuildSystemsStatus(guildId);

        event.getHook().sendMessageEmbeds(buildEmbed(statuses).build())
                .setComponents(buildButtons(statuses))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("sys_toggle:")) return;

        event.deferReply().queue();

        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.getHook().sendMessage("❌ You need **Manage Server** permission to use this.").setEphemeral(true).queue();
            return;
        }

        String systemName = event.getComponentId().split(":")[1];
        String guildId = event.getGuild().getId();

        // 1. Toggle state in DB
        boolean newState = handler.toggleSystem(guildId, systemName);

        Map<String, Boolean> statuses = handler.getGuildSystemsStatus(guildId);

        System.out.println("New State: " + newState);

        AddGuildSlashCommands cmdUpdater = new AddGuildSlashCommands(event.getGuild(), handler);
        cmdUpdater.updateGuildCommandsFromActiveSystems("");

        event.getMessage().editMessageEmbeds(buildEmbed(statuses).build())
                .setComponents(buildButtons(statuses))
                .queue();
        event.getHook().deleteOriginal().queue();
    }

    private EmbedBuilder buildEmbed(Map<String, Boolean> statuses) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚙️ System Configuration");
        embed.setDescription("Click the buttons below to enable or disable specific bot systems for this server.\n" +
                "\n**Note:** Disabling a system will remove its slash commands from the server. \nData will remain intact.\n\n" +
                "**It might take a while for changes to take effect. Refreshing discord can help.**");
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
            } else if (sys.equals("leveling")) {
                // Special case: Leveling system cannot be enabled for now
                buttons.add(Button.secondary("sys_toggle:" + sys, label + " to be implemented").withEmoji(Emoji.fromFormatted("⚠️")));
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
