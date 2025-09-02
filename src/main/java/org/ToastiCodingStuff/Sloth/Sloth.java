package org.ToastiCodingStuff.Sloth;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Sloth {
    public static void main(String[] args) throws Exception {
        DatabaseHandler handler = new DatabaseHandler();
        Dotenv dotenv = Dotenv.load();
        JDA api = JDABuilder.createDefault(dotenv.get("TOKEN_TEST"))
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                .build();
        api.awaitReady();

        // Set bot status to "Playing /help"
        api.getPresence().setActivity(Activity.playing("/help"));

        api.addEventListener(new LogChannelSlashCommandListener(handler));
        api.addEventListener(new WarnCommandListener(handler));
        api.addEventListener(new TicketCommandListener(handler));
        api.addEventListener(new StatisticsCommandListener(handler));
        api.addEventListener(new ModerationCommandListener(handler));

        api.addEventListener(new SystemManagementCommandListener(handler));
        api.addEventListener(new HelpCommandListener());
        api.addEventListener(new GuildEventListener(handler));
        
        // Register global system management command
        OptionData systemOption = new OptionData(OptionType.STRING, "system", "Which system to add", true)
                .addChoice("Log Channel System", "log-channel")
                .addChoice("Warning System", "warn-system")
                .addChoice("Ticket System", "ticket-system")
                .addChoice("Moderation System", "moderation-system");

        // Register global commands using upsertCommand to avoid replacing existing commands
        api.upsertCommand(Commands.slash("add-system", "Add commands for a specific system")
                .addOptions(systemOption)).queue();
        api.upsertCommand(Commands.slash("list-systems", "Show which systems are currently activated on this server")).queue();
        api.upsertCommand(Commands.slash("help", "Show help and documentation for Sloth bot")).queue();
        
        // Sync all current guilds to database
        handler.syncGuilds(api.getGuilds());
        
        // Automatically activate systems for all guilds based on database
        activateStoredSystems(handler, api.getGuilds());
    }
    
    /**
     * Activate systems for all guilds based on what's stored in the database
     */
    private static void activateStoredSystems(DatabaseHandler handler, java.util.List<Guild> guilds) {
        for (Guild guild : guilds) {
            java.util.List<String> activatedSystems = handler.getActivatedSystems(guild.getId());
            if (!activatedSystems.isEmpty()) {
                System.out.println("Activating stored systems for guild " + guild.getName() + " (ID: " + guild.getId() + "):");
                
                for (String systemType : activatedSystems) {
                    System.out.println("  - Activating " + systemType + " system");
                }
                
                // Use the new constructor and method to update all commands at once
                AddGuildSlashCommands adder = new AddGuildSlashCommands(guild, handler);
                adder.updateAllGuildCommands();
            } else {
                // If no systems are activated, just add statistics commands
                AddGuildSlashCommands adder = new AddGuildSlashCommands(guild, handler);
                adder.updateAllGuildCommands(); // This will add only statistics commands
            }
        }
        System.out.println("Finished activating stored systems for all guilds");
    }
}
