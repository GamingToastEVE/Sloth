package org.ToastiCodingStuff.Sloth;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Sloth {
    private static BackupManager backupManager;
    
    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.load();
        JDA api = JDABuilder.createDefault(dotenv.get("TOKEN_TEST"))
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                .build();
        api.awaitReady();

        DatabaseHandler handler = new DatabaseHandler();
        
        // Initialize and start backup manager
        String dbHost = System.getenv().getOrDefault("DB_HOST", "localhost");
        String dbPort = System.getenv().getOrDefault("DB_PORT", "3306");
        String dbName = System.getenv().getOrDefault("DB_NAME", "sloth");
        String dbUser = System.getenv().getOrDefault("DB_USER", "root");
        String dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "admin");
        
        backupManager = new BackupManager(dbHost, dbPort, dbName, dbUser, dbPassword);
        backupManager.startScheduler();

        // Set bot status to "Playing /help"
        api.getPresence().setActivity(Activity.playing("/help"));

        api.addEventListener(new LogChannelSlashCommandListener(handler));
        api.addEventListener(new WarnCommandListener(handler));
        api.addEventListener(new TicketCommandListener(handler));
        api.addEventListener(new StatisticsCommandListener(handler));
        api.addEventListener(new ModerationCommandListener(handler));
        api.addEventListener(new AddRulesEmbedToChannelCommandListener(handler));
        api.addEventListener(new JustVerifyButtonCommandListener(handler));
        api.addEventListener(new OnGuildLeaveListener(handler));
        api.addEventListener(new GlobalCommandListener(handler));

        api.addEventListener(new HelpCommandListener(handler));
        api.addEventListener(new GuildEventListener(handler));
        
        // Register all system commands globally
        registerGlobalCommands(api, handler);

        handler.runMigrationCheck();

        // Sync all current guilds to database
        handler.syncGuilds(api.getGuilds());
        
        // Add shutdown hook to gracefully stop backup scheduler
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down bot...");
            if (backupManager != null) {
                backupManager.stopScheduler();
            }
        }));
    }

    /**
     * Register all system commands globally
     */
    private static void registerGlobalCommands(JDA api, DatabaseHandler handler) {
        System.out.println("Registering all system commands globally...");
        //Guild guild = api.getGuildById("1169699077986988112"); // Replace with your test server ID if needed

        // Create a temporary AddGuildSlashCommands instance to get command lists
        // We can use null guild since we only need the command definitions
        AddGuildSlashCommands commandProvider = new AddGuildSlashCommands(null, handler);



        // Get all commands and register them globally
        java.util.List<net.dv8tion.jda.api.interactions.commands.build.SlashCommandData> allCommands = new java.util.ArrayList<>();
        allCommands.addAll(commandProvider.getAllCommands());
        allCommands.add(Commands.slash("help", "Show help and documentation for Sloth bot"));

        Guild testServer = api.getGuildById("1169699077986988112");

        if (testServer == null) {
            System.out.println("Test server not found. Skipping test server command registration.");
        } else {
            testServer.updateCommands().addCommands(Commands.slash("global-stats", "Show global bot statistics")).queue();
        }

        for (SlashCommandData command : allCommands) {
            System.out.println(" - " + command.getName());
        }

        // Register each command globally
        //guild.updateCommands().addCommands(allCommands).queue();
        api.updateCommands().addCommands(allCommands).queue();

        System.out.println("Finished registering " + allCommands.size() + " global commands");
    }
}
