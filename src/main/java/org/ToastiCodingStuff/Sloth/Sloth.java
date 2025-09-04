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


        api.addEventListener(new HelpCommandListener());
        api.addEventListener(new GuildEventListener(handler));

        api.upsertCommand(Commands.slash("help", "Show help and documentation for Sloth bot")).queue();
        
        // Sync all current guilds to database
        handler.syncGuilds(api.getGuilds());
        
        // Automatically activate systems for all guilds based on database
        activateStoredSystems(handler, api.getGuilds());
    }
    
    /**
     * Add all system commands to all guilds
     */
    private static void activateStoredSystems(DatabaseHandler handler, java.util.List<Guild> guilds) {
        for (Guild guild : guilds) {
            System.out.println("Adding all system commands to guild " + guild.getName() + " (ID: " + guild.getId() + ")");
            
            // Add all commands to every guild
            AddGuildSlashCommands adder = new AddGuildSlashCommands(guild, handler);
            adder.updateAllGuildCommands();
        }
        System.out.println("Finished adding all commands to all guilds");
    }
}
