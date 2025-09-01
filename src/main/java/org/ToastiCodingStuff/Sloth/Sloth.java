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
        JDA api = JDABuilder.createDefault(dotenv.get("TOKEN"))
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
        api.addEventListener(new AutomodCommandListener(handler));
        api.addEventListener(new AutomodMessageListener(handler)); // NEW: Processes messages through automod
        api.addEventListener(new SystemManagementCommandListener(handler));
        api.addEventListener(new HelpCommandListener());
        api.addEventListener(new GuildEventListener(handler));
        
        // Register global system management command
        OptionData systemOption = new OptionData(OptionType.STRING, "system", "Which system to add", true)
                .addChoice("Log Channel System", "log-channel")
                .addChoice("Warning System", "warn-system")
                .addChoice("Ticket System", "ticket-system")
                .addChoice("Moderation System", "moderation-system")
                .addChoice("Automod System", "automod-system");

        api.updateCommands().addCommands(
                Commands.slash("add-system", "Add commands for a specific system")
                        .addOptions(systemOption),
                Commands.slash("help", "Show help and documentation for Sloth bot")
        ).queue();
        
        // Sync all current guilds to database
        handler.syncGuilds(api.getGuilds());
        
        // Automatically activate systems for all guilds based on database
        activateStoredSystems(handler, api.getGuilds());
        
        // Schedule periodic cleanup of expired automod data
        scheduleAutomodCleanup(handler);
    }
    
    /**
     * Activate systems for all guilds based on what's stored in the database
     */
    private static void activateStoredSystems(DatabaseHandler handler, java.util.List<Guild> guilds) {
        for (Guild guild : guilds) {
            java.util.List<String> activatedSystems = handler.getActivatedSystems(guild.getId());
            if (!activatedSystems.isEmpty()) {
                AddGuildSlashCommands adder = new AddGuildSlashCommands(guild);
                System.out.println("Activating stored systems for guild " + guild.getName() + " (ID: " + guild.getId() + "):");
                
                for (String systemType : activatedSystems) {
                    System.out.println("  - Activating " + systemType + " system");
                    switch (systemType) {
                        case "log-channel":
                            adder.addLogChannelCommands();
                            break;
                        case "warn-system":
                            adder.addWarnCommands();
                            break;
                        case "ticket-system":
                            adder.addTicketCommands();
                            break;
                        case "moderation-system":
                            adder.addModerationCommands();
                            break;
                        case "automod-system":
                            adder.addAutomodCommands();
                            break;
                    }
                }
            }
            
            // Always add statistics commands (they are not part of the system management)
            AddGuildSlashCommands adder = new AddGuildSlashCommands(guild);
            adder.addStatisticsCommands();
        }
        System.out.println("Finished activating stored systems for all guilds");
    }
    
    /**
     * Schedule periodic cleanup of expired automod data
     */
    private static void scheduleAutomodCleanup(DatabaseHandler handler) {
        // Create a timer that runs every hour to clean up expired data
        java.util.Timer timer = new java.util.Timer(true); // daemon thread
        java.util.TimerTask cleanupTask = new java.util.TimerTask() {
            @Override
            public void run() {
                try {
                    handler.cleanupExpiredAutomodData();
                } catch (Exception e) {
                    System.err.println("Error during automod cleanup: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        
        // Schedule to run every hour (3600000 milliseconds)
        timer.scheduleAtFixedRate(cleanupTask, 3600000, 3600000);
        System.out.println("Scheduled automod cleanup to run every hour");
    }
}
