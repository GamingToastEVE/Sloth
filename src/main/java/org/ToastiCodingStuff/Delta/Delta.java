package org.ToastiCodingStuff.Delta;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Delta {
    public static void main(String[] args) throws Exception {
        DatabaseHandler handler = new DatabaseHandler();
        Dotenv dotenv = Dotenv.load();
        JDA api = JDABuilder.createDefault(dotenv.get("TOKEN"))
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                .build();
        api.awaitReady();

        api.addEventListener(new LogChannelSlashCommandListener(handler));
        api.addEventListener(new WarnCommandListener(handler));
        api.addEventListener(new TicketCommandListener(handler));
        api.addEventListener(new StatisticsCommandListener(handler));
        api.addEventListener(new BanCommand(handler));
        api.addEventListener(new SystemManagementCommandListener());
        api.addEventListener(new GuildEventListener(handler));
        
        // Register global system management command
        OptionData systemOption = new OptionData(OptionType.STRING, "system", "Which system to add", true)
                .addChoice("Log Channel System", "log-channel")
                .addChoice("Warning System", "warn-system")
                .addChoice("Ticket System", "ticket-system");

        api.updateCommands().addCommands(
                Commands.slash("add-system", "Add commands for a specific system")
                        .addOptions(systemOption)
        ).queue();
        
        // Sync all current guilds to database
        handler.syncGuilds(api.getGuilds());
        
        Guild guild = api.getGuildById("1169699077986988112");
        AddGuildSlashCommands adder = new AddGuildSlashCommands(guild);
        adder.addLogChannelCommands();
        adder.addWarnCommands();
        adder.addTicketCommands();
        adder.addStatisticsCommands();
    }
}
