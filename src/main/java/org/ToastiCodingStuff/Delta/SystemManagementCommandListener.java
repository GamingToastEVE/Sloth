package org.ToastiCodingStuff.Delta;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class SystemManagementCommandListener extends ListenerAdapter {

    private final DatabaseHandler databaseHandler;

    public SystemManagementCommandListener(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("add-system")) {
            handleAddSystemCommand(event);
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
}