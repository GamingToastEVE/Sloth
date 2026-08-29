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
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Sloth {
    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.load();

        // Which .env key holds the token. Defaults to TOKEN_TEST so a local run keeps
        // using the test bot; the server sets TOKEN_KEY=TOKEN in its own .env.
        // Deliberately explicit: silently preferring TOKEN would make a development
        // run log in as the production bot.
        String tokenKey = dotenv.get("TOKEN_KEY", "TOKEN_TEST");
        String token = dotenv.get(tokenKey);
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("No bot token found: .env has no value for " + tokenKey
                    + " (set TOKEN_KEY to choose a different key)");
        }
        System.out.println("Starting with token from " + tokenKey);

        JDA api = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                .setChunkingFilter(ChunkingFilter.ALL)
                .build();
        api.awaitReady();

        DatabaseHandler handler = new DatabaseHandler();

        // Initialize language manager
        LanguageManager languageManager = new LanguageManager(handler);

        Guild guild = api.getGuildById("1169699077986988112");

        // Set bot status to "Playing /help"
        api.getPresence().setActivity(Activity.playing("Starting up..."));

        SystemsCommandListener systemsCommandListener = new SystemsCommandListener(handler);
        LogChannelSlashCommandListener logChannelListener = new LogChannelSlashCommandListener(handler);
        WarnCommandListener warnListener = new WarnCommandListener(handler);
        TicketCommandListener ticketListener = new TicketCommandListener(handler);
        TicketPanelCommandListener ticketPanelListener = new TicketPanelCommandListener(handler);
        StatisticsCommandListener statisticsListener = new StatisticsCommandListener(handler);
        ModerationCommandListener moderationListener = new ModerationCommandListener(handler);
        JustVerifyButtonCommandListener verifyListener = new JustVerifyButtonCommandListener(handler);
        GlobalCommandListener globalListener = new GlobalCommandListener(handler);
        FeedbackCommandListener feedbackListener = new FeedbackCommandListener(guild);
        SelectRolesCommandListener selectRolesListener = new SelectRolesCommandListener(handler);
        TimedRolesCommandListener timedRolesListener = new TimedRolesCommandListener(handler);
        RoleEventConfigListener roleEventListener = new RoleEventConfigListener(handler);
        EmbedEditorCommandListener embedEditorListener = new EmbedEditorCommandListener(handler);
        ReminderCommandListener reminderListener = new ReminderCommandListener(handler);
        LevelingSystemCommandListener levelingListener = new LevelingSystemCommandListener(handler);
        LanguageCommandListener languageListener = new LanguageCommandListener(languageManager);
        HelpCommandListener helpListener = new HelpCommandListener(handler);
        DataCommandListener dataListener = new DataCommandListener(handler);

        // Zentraler Slash-Command-Router: leitet SlashCommandInteractionEvents direkt an den
        // zuständigen Handler weiter, statt alle Listener zu durchlaufen.
        SlashCommandRouter slashCommandRouter = new SlashCommandRouter(List.of(
                logChannelListener, warnListener, ticketListener, ticketPanelListener,
                statisticsListener, moderationListener, verifyListener, globalListener,
                feedbackListener, selectRolesListener, timedRolesListener, roleEventListener,
                embedEditorListener, systemsCommandListener, reminderListener,
                levelingListener, languageListener, helpListener, dataListener
        ));

        // Router für Slash-Commands (ein einzelner Listener statt vieler)
        api.addEventListener(slashCommandRouter);

        // Alle anderen Listener (Button, Select, Modal, Message-Events etc.)
        api.addEventListener(warnListener);
        api.addEventListener(ticketListener);
        api.addEventListener(ticketPanelListener);
        api.addEventListener(new TicketCreationListener(handler));
        api.addEventListener(statisticsListener);
        api.addEventListener(moderationListener);
        api.addEventListener(verifyListener);
        api.addEventListener(new OnGuildLeaveListener(handler));
        api.addEventListener(selectRolesListener);
        api.addEventListener(timedRolesListener);
        api.addEventListener(roleEventListener);
        api.addEventListener(new TimedRoleTriggerListener(handler, api));
        api.addEventListener(new MemberRoleTrackingListener(handler));
        api.addEventListener(embedEditorListener);
        api.addEventListener(systemsCommandListener);
        api.addEventListener(reminderListener);
        api.addEventListener(levelingListener);
        api.addEventListener(languageListener);
        api.addEventListener(new SetupWizardListener(handler, systemsCommandListener));
        api.addEventListener(helpListener);
        api.addEventListener(dataListener);
        api.addEventListener(new GuildEventListener(handler));

        // Register all system commands globally
        registerGlobalCommands(api, handler);

        handler.initializeTables();

        handler.runMigrationCheck();

        // Sync all current guilds to database
        List<Guild> guilds = api.getGuilds();
        handler.syncGuilds(guilds);
        handler.updateGuildActivityStatus(guilds);

        // Must run after syncGuilds, which clears the marker for guilds the bot is in.
        // Starts the retention clock for guilds that were left before the clock existed.
        handler.backfillGuildLeftTimestamps();

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

        // Delete the data of guilds the bot was removed from once the retention period
        // stated in the privacy policy has passed. Checked once on startup and daily after.
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                List<String> purged = handler.purgeExpiredGuildData();
                if (!purged.isEmpty()) {
                    System.out.println("Retention purge removed data of " + purged.size() + " guild(s)");
                }
                // Afterwards, drop profiles that the purge left without any reference
                handler.purgeOrphanedUsers();
            } catch (Exception e) {
                System.err.println("Error purging expired data: " + e.getMessage());
                e.printStackTrace();
            }
        }, 0, 1, java.util.concurrent.TimeUnit.DAYS);

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
     * Register global commands and update guild-specific commands based on active systems.
     * This method is called once at bot startup.
     */
    private static void registerGlobalCommands(JDA api, DatabaseHandler handler) throws InterruptedException {
        System.out.println("\n=== Starting Command Registration ===");

        // 1. Register global commands (core commands available everywhere)
        AddGuildSlashCommands commandProvider = new AddGuildSlashCommands(null, handler);
        List<SlashCommandData> globalCommands = new java.util.ArrayList<>(commandProvider.getCoreCommands());

        // Add reminder command with DM support
        globalCommands.add(Commands.slash("reminder", "Manage your reminders")
                .addSubcommands(
                        new SubcommandData("set", "Create a new reminder")
                                .addOption(OptionType.STRING, "time", "Time until i remind you (10m, 1h, 2d)", true)
                                .addOption(OptionType.STRING, "title", "Title of the reminder", true)
                                .addOption(OptionType.STRING, "message", "What should I remind you of?", false)
                                .addOption(OptionType.BOOLEAN, "dm", "DM? Standard: YES", false),
                        new SubcommandData("list", "Shows all active reminders"))
                .setContexts(InteractionContextType.BOT_DM));

        System.out.println("Registering " + globalCommands.size() + " global commands:");
        for (SlashCommandData command : globalCommands) {
            System.out.println("  - " + command.getName());
        }

        api.updateCommands().addCommands(globalCommands).queue(
                success -> System.out.println("✓ Successfully registered global commands"),
                error -> System.err.println("✗ Failed to register global commands: " + error.getMessage())
        );

        // 2. Register test server specific commands (if needed)
        Guild testServer = api.getGuildById("1169699077986988112");
        if (testServer != null) {
            testServer.updateCommands()
                    .addCommands(Commands.slash("global-stats", "Show global bot statistics"))
                    .queue(
                            success -> System.out.println("✓ Registered test server commands"),
                            error -> System.err.println("✗ Failed to register test server commands: " + error.getMessage())
                    );
        }

        // 3. Update guild-specific commands asynchronously to avoid blocking startup
        System.out.println("\nStarting guild-specific command registration...");
        Thread commandRegistrationThread = new Thread(() -> {
            List<Guild> guilds = api.getGuilds();
            System.out.println("Updating commands for " + guilds.size() + " guilds");

            for (Guild guild : guilds) {
                try {
                    AddGuildSlashCommands provider = new AddGuildSlashCommands(guild, handler);
                    provider.updateGuildCommandsFromActiveSystems(null);

                    // Rate limit protection: wait 500ms between guild updates
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    System.err.println("Command registration interrupted for guild: " + guild.getName());
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error updating commands for guild " + guild.getName() + ": " + e.getMessage());
                }
            }
            System.out.println("=== Finished Guild Command Registration ===\n");
        }, "CommandRegistration-Thread");

        commandRegistrationThread.setDaemon(true);
        commandRegistrationThread.start();
    }

    /**
     * Updates guild commands based on active systems.
     * This method is called when a system is toggled on/off.
     *
     * @param guildId The ID of the guild to update
     * @param databaseHandler The database handler instance
     * @param api The JDA instance
     */
    public static void updateGuildCommandsFromActiveSystems(String guildId, DatabaseHandler databaseHandler, JDA api) {
        if (guildId == null || guildId.isBlank() || databaseHandler == null || api == null) {
            System.err.println("Cannot update guild commands - invalid parameters");
            return;
        }

        Guild guild = api.getGuildById(guildId);
        if (guild == null) {
            System.err.println("Cannot update guild commands - guild not found: " + guildId);
            return;
        }

        // Delegate to AddGuildSlashCommands for consistent behavior
        AddGuildSlashCommands commandProvider = new AddGuildSlashCommands(guild, databaseHandler);
        commandProvider.updateGuildCommandsFromActiveSystems(null);
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
