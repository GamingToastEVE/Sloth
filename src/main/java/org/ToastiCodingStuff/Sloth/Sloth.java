package org.ToastiCodingStuff.Sloth;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Sloth {
    public static void main(String[] args) throws Exception {
        DatabaseHandler handler = new DatabaseHandler();
        Dotenv dotenv = Dotenv.load();
        JDA api = JDABuilder.createDefault(dotenv.get("TOKEN"))
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                .build();
        api.awaitReady();

        // Set bot status to "Playing /help"
        api.getPresence().setActivity(Activity.playing("/help"));

        api.addEventListener(new LogChannelSlashCommandListener(handler));
        api.addEventListener(new WarnCommandListener(handler));
        api.addEventListener(new TicketCommandListener(handler));
        api.addEventListener(new StatisticsCommandListener(handler));
        api.addEventListener(new ModerationCommandListener(handler));


        api.addEventListener(new HelpCommandListener());
        api.addEventListener(new GuildEventListener(handler));
        
        // Register all system commands globally
        registerGlobalCommands(api, handler);

        // Sync all current guilds to database
        handler.syncGuilds(api.getGuilds());
    }

    /**
     * Register all system commands globally
     */
    private static void registerGlobalCommands(JDA api, DatabaseHandler handler) {
        System.out.println("Registering all system commands globally...");

        // Create a temporary AddGuildSlashCommands instance to get command lists
        // We can use null guild since we only need the command definitions
        AddGuildSlashCommands commandProvider = new AddGuildSlashCommands(null, handler);

        // Get all commands and register them globally
        java.util.List<net.dv8tion.jda.api.interactions.commands.build.SlashCommandData> allCommands = new java.util.ArrayList<>();
        allCommands.addAll(commandProvider.getAllCommands());
        allCommands.add(Commands.slash("help", "Show help and documentation for Sloth bot"));

        // Register each command globally
        api.updateCommands().addCommands(allCommands).queue();

        System.out.println("Finished registering " + allCommands.size() + " global commands");
    }
}
