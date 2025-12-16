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

        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need **Manage Server** permission to manage bot systems.").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        Map<String, Boolean> statuses = handler.getGuildSystemsStatus(guildId);

        event.replyEmbeds(buildEmbed(statuses).build())
                .setComponents(buildButtons(statuses))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("sys_toggle:")) return;

        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need **Manage Server** permission to use this.").setEphemeral(true).queue();
            return;
        }

        String systemName = event.getComponentId().split(":")[1];
        String guildId = event.getGuild().getId();

        // 1. Toggle state in DB
        boolean newState = handler.toggleSystem(guildId, systemName);

        Map<String, Boolean> statuses = handler.getGuildSystemsStatus(guildId);

        // 2. Refresh the guild's commands immediately
        // Note: In JDA 5, we can use the main class logic or separate command updater
        AddGuildSlashCommands cmdUpdater = new AddGuildSlashCommands(event.getGuild(), handler);
        cmdUpdater.updateGuildCommandsFromActiveSystems();

        event.editMessageEmbeds(buildEmbed(statuses).build())
                .setComponents(buildButtons(statuses))
                .queue();
    }

    private EmbedBuilder buildEmbed(Map<String, Boolean> statuses) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚙️ System Configuration");
        embed.setDescription("Click the buttons below to enable or disable specific bot systems for this server.\n" +
                "\n**Note:** Disabling a system will remove its slash commands from the server. \nData will remain intact.\n\n" +
                "**It might take a few seconds for changes to take effect.**");
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
                "embed"
        };

        for (String sys : order) {
            boolean isActive = statuses.getOrDefault(sys, true);
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

    private String formatSystemName(String key) {
        switch (key) {
            case "log-channel": return "Logging";
            case "warn": return "Warnings";
            case "ticket": return "Tickets";
            case "mod": return "Moderation";
            case "stats": return "Statistics";
            case "verify-button": return "Verify";
            case "select-roles": return "Self Roles";
            case "temprole": return "Temp Roles";
            case "role-event": return "Role Events";
            case "embed": return "Embed Creation";
            default: return key;
        }
    }
}
