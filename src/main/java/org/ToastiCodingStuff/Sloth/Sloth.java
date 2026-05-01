package org.ToastiCodingStuff.Sloth;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.IntegrationType;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Sloth {
    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.load();
        JDA api = JDABuilder.createDefault(dotenv.get("TOKEN_TEST"))
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                .setChunkingFilter(ChunkingFilter.ALL)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .build();
        api.awaitReady();

        DatabaseHandler handler = new DatabaseHandler();

        // Initialize language manager
        LanguageManager languageManager = new LanguageManager(handler);

        Guild guild = api.getGuildById("1169699077986988112");

        // Set bot status to "Playing /help"
        api.getPresence().setActivity(Activity.playing("Starting up..."));

        SystemsCommandListener systemsCommandListener = new SystemsCommandListener(handler);

        api.addEventListener(new LogChannelSlashCommandListener(handler));
        api.addEventListener(new WarnCommandListener(handler));
        api.addEventListener(new TicketCommandListener(handler));
        api.addEventListener(new TicketPanelCommandListener(handler));
        api.addEventListener(new TicketCreationListener(handler));
        api.addEventListener(new StatisticsCommandListener(handler));
        api.addEventListener(new ModerationCommandListener(handler));
        api.addEventListener(new JustVerifyButtonCommandListener(handler));
        api.addEventListener(new OnGuildLeaveListener(handler));
        api.addEventListener(new GlobalCommandListener(handler));
        api.addEventListener(new FeedbackCommandListener(guild));
        api.addEventListener(new SelectRolesCommandListener(handler));
        api.addEventListener(new TimedRolesCommandListener(handler));
        api.addEventListener(new RoleEventConfigListener(handler));
        api.addEventListener(new TimedRoleTriggerListener(handler, api));
        api.addEventListener(new MemberRoleTrackingListener(handler));
        api.addEventListener(new EmbedEditorCommandListener(handler));
        api.addEventListener(systemsCommandListener);
        api.addEventListener(new ReminderCommandListener(handler));
        api.addEventListener(new LevelingSystemCommandListener(handler));
        api.addEventListener(new LanguageCommandListener(languageManager));
        api.addEventListener(new SetupWizardListener(handler, systemsCommandListener));

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
                    "/help | in " + api.getGuilds().size() + " servers!",
                    "/help | /feedback for bugreports"
            };
            String activity = activities[rand.nextInt(activities.length)];
            api.getPresence().setActivity(Activity.customStatus(activity));
        }, 0, 60, java.util.concurrent.TimeUnit.MINUTES);

        // check for inactive warnings and remove them every 10 minutes
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                handler.removeInactiveWarnings();
            } catch (Exception e) {
                System.err.println("Error updating guild activity status: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 10, java.util.concurrent.TimeUnit.MINUTES);

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
                            String sourceName = handler.getRoleEvent(timer.sourceEventId) != null
                                    ? handler.getRoleEvent(timer.sourceEventId).name
                                    : "No source found";

                            // Original action was REMOVE -> now ADD the role back
                            if (timer.actionType == null || timer.actionType.equalsIgnoreCase(String.valueOf(ActionType.REMOVE))) {
                                guild1.retrieveMemberById(timer.userId).queue(
                                        member -> {
                                            // Role was removed, now add it back
                                            guild1.addRoleToMember(member, role)
                                                    .reason("Timed Role expired, adding role back: " + sourceName)
                                                    .queue(
                                                            success -> System.out.println("Added role " + role.getName() + " back to " + member.getUser().getName()),
                                                            error -> {
                                                                System.err.println("Failed to add role: " + error.getMessage());
                                                                sendTimerErrorToLogChannel(handler, guild1, member.getUser().getName(), role.getName(), "ADD", error.getMessage());
                                                            }
                                                    );
                                        },
                                        error -> {
                                            System.err.println("Member " + timer.userId + " not found/left guild.");
                                            sendTimerErrorToLogChannel(handler, guild1, timer.userId, role.getName(), "MEMBER_NOT_FOUND", "Member not found or left the guild");
                                        }
                                );
                            // Original action was ADD -> now REMOVE the role
                            } else if (timer.actionType.equalsIgnoreCase(String.valueOf(ActionType.ADD))) {
                                guild1.retrieveMemberById(timer.userId).queue(
                                        member -> {
                                            // Role was added, now remove it
                                            guild1.removeRoleFromMember(member, role)
                                                    .reason("Timed Role expired, removing role: " + sourceName)
                                                    .queue(
                                                            success -> System.out.println("Removed role " + role.getName() + " from " + member.getUser().getName()),
                                                            error -> {
                                                                System.err.println("Failed to remove role: " + error.getMessage());
                                                                sendTimerErrorToLogChannel(handler, guild1, member.getUser().getName(), role.getName(), "REMOVE", error.getMessage());
                                                            }
                                                    );
                                        },
                                        error -> {
                                            System.err.println("Member " + timer.userId + " not found/left guild.");
                                            sendTimerErrorToLogChannel(handler, guild1, timer.userId, role.getName(), "MEMBER_NOT_FOUND", "Member not found or left the guild");
                                        }
                                );
                            }
                        } else {
                            System.err.println("Role " + timer.roleId + " not found in guild " + timer.guildId);
                            sendTimerErrorToLogChannel(handler, guild1, timer.userId, timer.roleId, "ROLE_NOT_FOUND", "Role no longer exists");
                        }
                    } else {
                        System.err.println("Guild " + timer.guildId + " not found");
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

        java.util.concurrent.ScheduledExecutorService reminderScheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        reminderScheduler.scheduleAtFixedRate(() -> {
            try {
                List<DatabaseHandler.ReminderData> dueReminders = handler.getDueReminders();

                for (DatabaseHandler.ReminderData rem : dueReminders) {
                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("⏰ Reminder " + rem.title);
                    embed.setColor(0x3498db);
                    embed.setDescription(rem.message != null && !rem.message.isBlank() ? rem.message : "You set a reminder!");
                    embed.setFooter("This is a reminder you set earlier.");

                    if (rem.dm) {
                        // Per DM senden
                        api.retrieveUserById(rem.userId).queue(user -> {
                            user.openPrivateChannel().queue(channel -> {
                                channel.sendMessageEmbeds(embed.build()).queue(null, error -> {
                                    System.err.println("Konnte DM für Reminder nicht senden (User blocked?): " + rem.userId);
                                });
                            });
                        }, error -> System.err.println("User für Reminder nicht gefunden: " + rem.userId));
                    } else {
                        // Im Server Channel senden
                        if (rem.guildId != null && rem.channelId != null) {
                            Guild g = api.getGuildById(rem.guildId);
                            if (g != null) {
                                TextChannel ch = g.getTextChannelById(rem.channelId);
                                if (ch != null && ch.canTalk()) {
                                    ch.sendMessageEmbeds(embed.build()).queue();
                                }
                            }
                        }
                    }
                    // 2. Reminder aus DB löschen
                    handler.deleteReminder(rem.id);
                }
            } catch (Exception e) {
                System.err.println("Error in Reminder loop: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 60, TimeUnit.SECONDS);
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

        allCommands.add(Commands.slash("reminder", "Manage your reminders")
                        .addSubcommands(
                                new SubcommandData("set", "Create a new reminder")
                                        .addOption(OptionType.STRING, "time", "Time until i remind you (10m, 1h, 2d)", true)
                                        .addOption(OptionType.STRING, "title", "Title of the reminder", true)
                                        .addOption(OptionType.STRING, "message", "What should I remind you of?", false)
                                        .addOption(OptionType.BOOLEAN, "dm", "DM? Standard: YES", false),
                                new SubcommandData("list", "Shows all active reminders")).setContexts(InteractionContextType.BOT_DM));

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

        api.updateCommands().addCommands(allCommands).queue();
        System.out.println("Finished registering " + allCommands.size() + " global commands");

        System.out.println("Starting registering commands in servers...");

        Thread t = new Thread(() -> {
            List<Guild> guilds = api.getGuilds();
            for (Guild guild : guilds) {
                System.out.println("Registering commands in guild: " + guild.getName() + " (" + guild.getId() + ")");
                AddGuildSlashCommands provider = new AddGuildSlashCommands(guild, handler);
                List<CommandData> commandsToRegister = new ArrayList<>();
                commandsToRegister.addAll(provider.getCoreCommands());
                java.util.Map<String, Boolean> systems = handler.getGuildSystemsStatus(guild.getId());
                for (java.util.Map.Entry<String, Boolean> entry : systems.entrySet()) {
                    if (entry.getValue()) { // If system is active
                        commandsToRegister.addAll(provider.getCommandsForSystem(entry.getKey()));
                        commandsToRegister.addAll(provider.getUserCommandsForSystem(entry.getKey()));
                    }
                }
                guild.updateCommands().addCommands(commandsToRegister).queue(
                        success -> System.out.println("Successfully registered commands in guild: " + guild.getName()),
                        error -> System.err.println("Failed to register commands in guild: " + guild.getName() + " - " + error.getMessage())
                );
                try {
                    TimeUnit.MILLISECONDS.sleep(500); // Sleep to avoid hitting rate limits
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        t.start();


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
        List<CommandData> activeCommands = new ArrayList<>();

        for (java.util.Map.Entry<String, Boolean> entry : systems.entrySet()) {
            if (entry.getValue()) { // If system is active
                activeCommands.addAll(commandProvider.getCommandsForSystem(entry.getKey()));
                activeCommands.addAll(commandProvider.getUserCommandsForSystem(entry.getKey()));
            }
        }

        guild.updateCommands().addCommands(activeCommands).queue(
                success -> System.out.println("Guild commands updated based on active systems for guild " + guild.getId()),
                error -> System.err.println("Failed to update guild commands for guild " + guild.getId() + ": " + error.getMessage())
        );
    }

    /**
     * Sends an error message to the guild's log channel when a timed role action fails
     */
    private static void sendTimerErrorToLogChannel(DatabaseHandler handler, Guild guild, String userIdentifier, String roleIdentifier, String action, String errorMessage) {
        if (!handler.hasLogChannel(guild.getId())) {
            return; // No log channel configured
        }

        String logChannelId = handler.getLogChannelID(guild.getId());
        TextChannel logChannel = guild.getTextChannelById(logChannelId);

        if (logChannel == null || !logChannel.canTalk()) {
            System.err.println("Log channel not found or cannot send messages: " + logChannelId);
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚠️ Timed Role Error");
        embed.setColor(0xFF6B6B); // Red color for errors

        String actionDescription = switch (action) {
            case "ADD" -> "Failed to add role back to user";
            case "REMOVE" -> "Failed to remove role from user";
            case "MEMBER_NOT_FOUND" -> "Member not found for role action";
            case "ROLE_NOT_FOUND" -> "Role not found for timed action";
            default -> "Unknown error during timed role action";
        };

        embed.setDescription(actionDescription);
        embed.addField("User", userIdentifier, true);
        embed.addField("Role", roleIdentifier, true);
        embed.addField("Error Details", errorMessage, false);
        embed.setTimestamp(java.time.Instant.now());
        embed.setFooter("Timed Roles System");

        logChannel.sendMessageEmbeds(embed.build()).queue(
                success -> {},
                error -> System.err.println("Failed to send error to log channel: " + error.getMessage())
        );
    }
}
