package org.ToastiCodingStuff.Sloth;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.List;

public class Sloth {
    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.load();
        JDA api = JDABuilder.createDefault(dotenv.get("TOKEN_TEST"))
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                .build();
        api.awaitReady();

        DatabaseHandler handler = new DatabaseHandler();

        Guild guild = api.getGuildById("1169699077986988112");

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
        api.addEventListener(new FeedbackCommandListener(guild));
        api.addEventListener(new SelectRolesCommandListener(handler));

        api.addEventListener(new HelpCommandListener(handler));
        api.addEventListener(new GuildEventListener(handler));
        
        // Register all system commands globally
        registerGlobalCommands(api, handler);

        handler.runMigrationCheck();

        // Sync all current guilds to database
        List<Guild> guilds = api.getGuilds();
        handler.syncGuilds(guilds);
        handler.updateGuildActivityStatus(guilds);

        // Start web dashboard if enabled
        String dashboardEnabled = dotenv.get("DASHBOARD_ENABLED");
        if ("true".equalsIgnoreCase(dashboardEnabled)) {
            int dashboardPort = 8080; // Default port
            String portStr = dotenv.get("DASHBOARD_PORT");
            if (portStr != null && !portStr.isEmpty()) {
                try {
                    dashboardPort = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid DASHBOARD_PORT, using default 8080");
                }
            }
            
            try {
                WebDashboard dashboard = new WebDashboard(api, handler, dashboardPort);
                dashboard.start();
            } catch (Exception e) {
                System.err.println("Failed to start web dashboard: " + e.getMessage());
            }
        }
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
        assert testServer != null;
        //testServer.updateCommands().addCommands(allCommands).queue();

        System.out.println("Finished registering " + allCommands.size() + " global commands");
    }
}
