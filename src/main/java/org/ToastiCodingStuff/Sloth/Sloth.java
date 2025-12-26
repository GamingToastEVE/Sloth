package org.ToastiCodingStuff.Sloth;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.*;
import java.util.concurrent.TimeUnit;

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
        api.addEventListener(new JustVerifyButtonCommandListener(handler));
        api.addEventListener(new OnGuildLeaveListener(handler));
        api.addEventListener(new GlobalCommandListener(handler));
        api.addEventListener(new FeedbackCommandListener(guild));
        api.addEventListener(new SelectRolesCommandListener(handler));
        api.addEventListener(new TimedRolesCommandListener(handler));
        api.addEventListener(new RoleEventConfigListener(handler));
        api.addEventListener(new TimedRoleTriggerListener(handler));
        api.addEventListener(new EmbedEditorCommandListener(handler));
        api.addEventListener(new SystemsCommandListener(handler));

        api.addEventListener(new HelpCommandListener(handler));
        api.addEventListener(new GuildEventListener(handler));

        // Register all system commands globally
        registerGlobalCommands(api, handler);

        handler.initializeTables();

        handler.runMigrationCheck();

        // Sync all current guilds to database
        List<Guild> guilds = api.getGuilds();
        handler.syncGuilds(guilds);
        handler.updateGuildActivityStatus(guilds);

        java.util.concurrent.ScheduledExecutorService activityRotator = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        activityRotator.scheduleAtFixedRate(() -> {
            Random rand = new Random();
            String[] activities = {
                    "/help | in " + api.getGuilds().size() + " servers",
                    "New Features out now!",
                    "Check out /feedback",
                    "For support, join our Discord!",
                    "Activate systems with /systems"
            };
            String activity = activities[rand.nextInt(activities.length)];
            api.getPresence().setActivity(Activity.customStatus(activity));
        }, 0, 60, java.util.concurrent.TimeUnit.MINUTES);

        // Starte den Background-Check für abgelaufene Rollen (jede Minute)
        java.util.concurrent.ScheduledExecutorService scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                // 1. Hole abgelaufene Timer aus der DB
                List<DatabaseHandler.ActiveTimerData> expired = handler.getExpiredTimers();

                for (DatabaseHandler.ActiveTimerData timer : expired) {
                    // 2. Finde Guild und Member
                    Guild guild1 = api.getGuildById(timer.guildId);
                    if (guild1 != null) {
                        Role role = guild1.getRoleById(timer.roleId);
                        if (role != null) {
                            if (timer.actionType.equalsIgnoreCase(String.valueOf(ActionType.REMOVE))) {
                                guild1.retrieveMemberById(timer.userId).queue(
                                        member -> {
                                            // 3. Rolle entfernen
                                            guild1.removeRoleFromMember(member, role).reason("Timed Role expired").queue();
                                            // Optional: User benachrichtigen
                                            // member.getUser().openPrivateChannel().queue(ch -> ch.sendMessage("Deine Rolle " + role.getName() + " auf " + guild1.getName() + " ist abgelaufen.").queue());
                                        },
                                        error -> System.err.println("Member " + timer.userId + " not found/left guild.")
                                );
                            } else if (timer.actionType.equalsIgnoreCase(String.valueOf(ActionType.ADD))) {
                                guild1.retrieveMemberById(timer.userId).queue(
                                        member -> {
                                            // 3. Rolle hinzufügen
                                            guild1.addRoleToMember(member, role).reason("Timed Role expired").queue();
                                            // Optional: User benachrichtigen
                                            // member.getUser().openPrivateChannel().queue(ch -> ch.sendMessage("Deine Rolle " + role.getName() + " wurde dir wieder hinzugefügt.").queue());
                                        },
                                        error -> System.err.println("Member " + timer.userId + " not found/left guild.")
                                );
                            }

                        }
                    }
                    // 4. Timer aus DB löschen (egal ob erfolgreich oder nicht, damit Loop nicht hängt)
                    handler.removeTimer(timer.id);
                    handler.removeWarningTimer();
                }
            } catch (Exception e) {
                System.err.println("Error in TimedRole loop: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    /**
     * Register all system commands globally
     */
    private static void registerGlobalCommands(JDA api, DatabaseHandler handler) throws InterruptedException {
        System.out.println("Registering all system commands globally...");
        //Guild guild = api.getGuildById("1169699077986988112"); // Replace with your test server ID if needed

        // Create a temporary AddGuildSlashCommands instance to get command lists
        // We can use null guild since we only need the command definitions
        AddGuildSlashCommands commandProvider = new AddGuildSlashCommands(null, handler);



        // Get all commands and register them globally
        List<SlashCommandData> allCommands = new java.util.ArrayList<>(commandProvider.getCoreCommands());

        Guild testServer = api.getGuildById("1169699077986988112");

        if (testServer == null) {
            System.out.println("Test server not found. Skipping test server command registration.");
        } else {
            testServer.updateCommands().addCommands(Commands.slash("global-stats", "Show global bot statistics")).queue();
        }

        for (SlashCommandData command : allCommands) {
            System.out.println(" - " + command.getName());
        }
        assert testServer != null;
        //testServer.updateCommands().addCommands(allCommands).queue();

        System.out.println("Finished registering " + allCommands.size() + " global commands");

        System.out.println("Starting registering commands in servers...");

        for (Guild guild : api.getGuilds()) {
            System.out.println("Registering commands in guild: " + guild.getName() + " (ID: " + guild.getId() + ")");

            updateGuildCommandsFromActiveSystems(guild.getId(), handler, api);
            TimeUnit.MILLISECONDS.sleep(100);
        }

        System.out.println("Finished registering commands in all servers.");
    }

    public static void updateGuildCommandsFromActiveSystems(String guildId, DatabaseHandler databaseHandler, JDA api) {
        if (guildId.isBlank() || databaseHandler == null) {
            System.err.println("Invalid guild ID or database handler is null.");
            return;
        }
        Guild guild = api.getGuildById(guildId);

        System.out.println("Updating guild commands based on active systems for guild " + guildId);

        AddGuildSlashCommands commandProvider = new AddGuildSlashCommands(guild, databaseHandler);

        java.util.Map<String, Boolean> systems = databaseHandler.getGuildSystemsStatus(guild.getId());
        List<SlashCommandData> activeCommands = new ArrayList<>();

        for (java.util.Map.Entry<String, Boolean> entry : systems.entrySet()) {
            if (entry.getValue()) { // If system is active
                activeCommands.addAll(commandProvider.getCommandsForSystem(entry.getKey()));
            }
        }

        guild.updateCommands().addCommands(activeCommands).queue(
                success -> System.out.println("Guild commands updated based on active systems for guild " + guild.getId()),
                error -> System.err.println("Failed to update guild commands for guild " + guild.getId() + ": " + error.getMessage())
        );
    }
}
