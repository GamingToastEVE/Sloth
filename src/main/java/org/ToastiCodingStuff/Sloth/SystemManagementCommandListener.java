package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.util.List;

public class SystemManagementCommandListener extends ListenerAdapter {

    private final DatabaseHandler databaseHandler;

    public SystemManagementCommandListener(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "add-system":
                handleAddSystemCommand(event);
                break;
            case "list-systems":
                handleListSystemsCommand(event);
                break;
        }
    }

    private void handleAddSystemCommand(SlashCommandInteractionEvent event) {
        // Check if user has administrator permissions
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You need Administrator permissions to manage systems.").setEphemeral(true).queue();
            return;
        }

        String systemType = event.getOption("system").getAsString();
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        AddGuildSlashCommands adder = new AddGuildSlashCommands(guild);

        switch (systemType) {
            case "log-channel":
                adder.addLogChannelCommands();
                databaseHandler.activateGuildSystem(guildId, systemType);
                event.reply("✅ Log channel commands have been added to this server!").setEphemeral(true).queue();
                break;
            case "warn-system":
                adder.addWarnCommands();
                databaseHandler.activateGuildSystem(guildId, systemType);
                event.reply("✅ Warning system commands have been added to this server!").setEphemeral(true).queue();
                break;
            case "ticket-system":
                adder.addTicketCommands();
                databaseHandler.activateGuildSystem(guildId, systemType);
                event.reply("✅ Ticket system commands have been added to this server!").setEphemeral(true).queue();
                break;
            case "moderation-system":
                adder.addModerationCommands();
                databaseHandler.activateGuildSystem(guildId, systemType);
                event.reply("✅ Moderation system commands have been added to this server!").setEphemeral(true).queue();
                break;
            default:
                event.reply("❌ Invalid system type. Available systems: `Log Channel System`, `Warning System`, `Ticket System`, `Moderation System`").setEphemeral(true).queue();
                break;
        }
    }

    private void handleListSystemsCommand(SlashCommandInteractionEvent event) {
        // Check if user has administrator permissions
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You need Administrator permissions to view system information.").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        List<String> activatedSystems = databaseHandler.getActivatedSystems(guildId);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚙️ Activated Systems")
                .setColor(Color.CYAN)
                .setDescription("Systems currently active on **" + event.getGuild().getName() + "**:\n\n");

        if (activatedSystems.isEmpty()) {
            embed.addField("🚫 No Systems Active", 
                    "No systems are currently activated on this server.\n" +
                    "Use `/add-system` to activate systems.", false);
        } else {
            StringBuilder systemList = new StringBuilder();
            for (String systemType : activatedSystems) {
                String displayInfo = getSystemDisplayInfo(systemType);
                systemList.append(displayInfo).append("\n");
            }
            embed.addField("✅ Active Systems (" + activatedSystems.size() + ")", 
                    systemList.toString(), false);
        }

        embed.addField("💡 Tip", 
                "Use `/add-system` to activate additional systems for your server.", false)
                .setFooter("System Management • Sloth Bot", null);

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private String getSystemDisplayInfo(String systemType) {
        switch (systemType) {
            case "log-channel":
                return "🔍 **Log Channel System** - Server activity logging";
            case "warn-system":
                return "⚠️ **Warning System** - User warning management";
            case "ticket-system":
                return "🎫 **Ticket System** - Support ticket management";
            case "moderation-system":
                return "🛡️ **Moderation System** - User moderation tools";
            default:
                return "❓ **Unknown System** - " + systemType;
        }
    }
}