package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;
import java.util.Random;

public class LevelingSystemCommandListener extends ListenerAdapter {
    private final DatabaseHandler handler;
    private final Random random = new Random();

    public LevelingSystemCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    // ==================== MESSAGE EVENT FOR XP ====================

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bots and DMs
        if (event.getAuthor().isBot() || event.getChannel() instanceof PrivateChannel) {
            return;
        }

        String guildId = event.getGuild().getId();
        String userId = event.getAuthor().getId();
        String channelId = event.getChannel().getId();

        // Check if leveling system is active
        if (!handler.isSystemActive(guildId, "leveling")) {
            return;
        }

        // Handle leveling XP
        handleLevelingXp(event, guildId, userId, channelId);
    }

    /**
     * Handle XP gain for leveling system
     */
    private void handleLevelingXp(MessageReceivedEvent event, String guildId, String userId, String channelId) {
        // Get leveling settings
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        // Check if leveling is enabled
        if (!settings.enabled) {
            return;
        }

        // Check if channel is ignored
        if (settings.ignoredChannels != null && !settings.ignoredChannels.isEmpty()) {
            String[] ignoredChannels = settings.ignoredChannels.split(",");
            for (String ignored : ignoredChannels) {
                if (ignored.trim().equals(channelId)) {
                    return;
                }
            }
        }

        // Check if user has an ignored role
        if (settings.ignoredRoles != null && !settings.ignoredRoles.isEmpty()) {
            Member member = event.getMember();
            if (member != null) {
                String[] ignoredRoles = settings.ignoredRoles.split(",");
                for (Role role : member.getRoles()) {
                    for (String ignored : ignoredRoles) {
                        if (ignored.trim().equals(role.getId())) {
                            return;
                        }
                    }
                }
            }
        }

        // Check minimum message length
        /*String messageContent = event.getMessage().getContentRaw();
        if (messageContent.length() < settings.minMessageLength) {
            return;
        }*/

        // Check cooldown
        if (handler.isUserOnXpCooldown(guildId, userId, settings.cooldownSeconds)) {
            return;
        }

        // Calculate random XP between min and max
        int xpAmount = settings.xpMin;
        if (settings.xpMax > settings.xpMin) {
            xpAmount = random.nextInt(settings.xpMax - settings.xpMin + 1) + settings.xpMin;
        }

        // Add XP and check for level up
        int newLevel = handler.addXpToUser(guildId, userId, xpAmount);

        // Handle level up
        if (newLevel > 0) {
            handleLevelUp(event, settings, guildId, userId, newLevel);
        }
    }

    /**
     * Handle level up - send notification and apply role rewards
     */
    private void handleLevelUp(MessageReceivedEvent event, DatabaseHandler.LevelSettingsData settings,
                               String guildId, String userId, int newLevel) {
        Member member = event.getMember();
        if (member == null) return;

        // Apply role rewards
        applyRoleRewards(event, settings, guildId, member, newLevel);

        // Build level-up message
        String levelUpMessage = buildLevelUpMessage(settings, member, newLevel);

        // Send level-up notification
        sendLevelUpNotification(event, settings, member, levelUpMessage, newLevel);
    }

    /**
     * Apply role rewards for reaching a new level
     */
    private void applyRoleRewards(MessageReceivedEvent event, DatabaseHandler.LevelSettingsData settings,
                                  String guildId, Member member, int newLevel) {
        if (settings.rewards == null || settings.rewards.isEmpty()) {
            return;
        }

        try {
            JSONArray rewards = new JSONArray(settings.rewards);
            Role previousRewardRole = null;
            Role newRewardRole = null;
            int previousRewardLevel = 0;

            // Find applicable rewards
            for (int i = 0; i < rewards.length(); i++) {
                JSONObject reward = rewards.getJSONObject(i);
                int rewardLevel = reward.getInt("level");
                String roleId = reward.getString("role_id");

                Role role = event.getGuild().getRoleById(roleId);
                if (role == null) continue;

                if (rewardLevel == newLevel) {
                    newRewardRole = role;
                } else if (rewardLevel < newLevel && !settings.stackRewards) {
                    // Track previous reward for replacement mode
                    if (rewardLevel > previousRewardLevel) {
                        previousRewardRole = role;
                        previousRewardLevel = rewardLevel;
                    }
                }
            }

            // Apply new reward role
            if (newRewardRole != null) {
                event.getGuild().addRoleToMember(member, newRewardRole).queue(
                        success -> {},
                        error -> System.err.println("Failed to add reward role: " + error.getMessage())
                );
            }

            // Remove previous role if not stacking
            if (!settings.stackRewards && previousRewardRole != null && newRewardRole != null) {
                event.getGuild().removeRoleFromMember(member, previousRewardRole).queue(
                        success -> {},
                        error -> System.err.println("Failed to remove previous reward role: " + error.getMessage())
                );
            }
        } catch (Exception e) {
            System.err.println("Error applying role rewards: " + e.getMessage());
        }
    }

    /**
     * Build the level-up message with placeholders replaced
     */
    private String buildLevelUpMessage(DatabaseHandler.LevelSettingsData settings, Member member, int newLevel) {
        String message = settings.levelupMessages;

        // Default message if none set
        if (message == null || message.isEmpty()) {
            message = "🎉 Congratulations {mention}, you reached **Level {level}**!";
        }

        // Get user's total XP
        DatabaseHandler.UserLevelData userData = handler.getUserLevel(member.getGuild().getId(), member.getId());

        // Replace placeholders
        message = message
                .replace("{mention}", member.getAsMention())
                .replace("{username}", member.getEffectiveName())
                .replace("{user}", member.getEffectiveName())
                .replace("{level}", String.valueOf(newLevel))
                .replace("{xp}", String.valueOf(userData.totalXp));

        return message;
    }

    /**
     * Send level-up notification to the appropriate channel or DM
     */
    private void sendLevelUpNotification(MessageReceivedEvent event, DatabaseHandler.LevelSettingsData settings,
                                         Member member, String message, int newLevel) {
        // Send DM if enabled
        if (settings.levelupDm) {
            member.getUser().openPrivateChannel().queue(
                    dm -> dm.sendMessage(message).queue(
                            success -> {},
                            error -> {} // Silently fail if DMs are disabled
                    ),
                    error -> {} // Silently fail if can't open DM
            );
        }

        // Determine target channel
        String channelSetting = settings.levelupChannelId;

        // If set to "0" or empty, don't send channel message
        if (channelSetting == null || channelSetting.equals("0") || channelSetting.isEmpty()) {
            return;
        }

        TextChannel targetChannel = null;

        if (channelSetting.equals("current")) {
            // Send in the channel where the message was sent
            if (event.getChannel() instanceof TextChannel) {
                targetChannel = (TextChannel) event.getChannel();
            }
        } else {
            // Send in the specified channel
            targetChannel = event.getGuild().getTextChannelById(channelSetting);
        }

        if (targetChannel != null) {
            // Create a Components V2 container for the level-up message
            DatabaseHandler.UserLevelData userData = handler.getUserLevel(member.getGuild().getId(), member.getId());
            long xpForNext = userData.getXpForNextLevel();
            double progress = userData.getProgressPercent();

            // Build progress bar
            String progressBar = buildProgressBar(progress);

            Container levelUpContainer = Container.of(
                    TextDisplay.of("# 🎉 Level Up!"),
                    Separator.createDivider(Separator.Spacing.SMALL),
                    TextDisplay.of(message),
                    Separator.createDivider(Separator.Spacing.SMALL),
                    TextDisplay.of(String.format(
                            "**Level %d** → **Level %d**\n" +
                            "%s\n" +
                            "`%d / %d XP` (%.1f%%)",
                            newLevel - 1, newLevel,
                            progressBar,
                            userData.xp, xpForNext, progress
                    ))
            ).withAccentColor(0x5865F2);

            MessageCreateBuilder messageBuilder = new MessageCreateBuilder()
                    .setComponents(levelUpContainer);

            targetChannel.sendMessage(messageBuilder.useComponentsV2().build()).queue(
                    success -> {},
                    error -> System.err.println("Failed to send level-up message: " + error.getMessage())
            );
        }
    }

    /**
     * Build a visual progress bar
     */
    private String buildProgressBar(double percentage) {
        int filled = (int) (percentage / 10);
        int empty = 10 - filled;
        return "▓".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, empty));
    }

    // ==================== SLASH COMMANDS ====================

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("leveling")) return;

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        switch (subcommand) {
            case "settings" -> handleSettings(event);
            case "leaderboard" -> handleLeaderboard(event);
            case "rank" -> handleRank(event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("level_")) return;

        String guildId = event.getGuild().getId();

        // Permission check for settings buttons
        if (componentId.startsWith("level_toggle_") || componentId.startsWith("level_settings_")) {
            if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
                event.reply("❌ You need **Manage Server** permission to change these settings.").setEphemeral(true).queue();
                return;
            }
        }

        // Handle toggle buttons
        if (componentId.startsWith("level_toggle_")) {
            String setting = componentId.replace("level_toggle_", "");
            handleToggleSetting(event, guildId, setting);
            return;
        }

        // Handle leaderboard pagination
        if (componentId.startsWith("level_leaderboard_")) {
            handleLeaderboardPagination(event, guildId, componentId);
            return;
        }

        // Handle navigation/action buttons
        switch (componentId) {
            case "level_settings_xp" -> showXpSettingsPage(event, guildId);
            case "level_settings_notifications" -> showNotificationSettingsPage(event, guildId);
            case "level_settings_rewards" -> showRewardSettingsPage(event, guildId);
            case "level_settings_exceptions" -> showExceptionsSettingsPage(event, guildId);
            case "level_settings_back" -> showMainSettingsPage(event, guildId);
            case "level_settings_refresh" -> showMainSettingsPage(event, guildId);

            // Edit buttons that open modals
            case "level_settings_xp_min_max_change" -> showXpMinMaxModal(event, guildId);
            case "level_settings_xp_cooldown_change" -> showCooldownModal(event, guildId);
            case "level_settings_voice_xp_amount_change" -> showVoiceXpAmountModal(event, guildId);
            case "level_settings_min_message_length_change" -> showMinMessageLengthModal(event, guildId);
            case "level_settings_levelup_channel_change" -> showLevelUpChannelModal(event, guildId);
            case "level_settings_levelup_message_change" -> showLevelUpMessageModal(event, guildId);
            case "level_settings_ignored_channels_change" -> showIgnoredChannelsModal(event, guildId);
            case "level_settings_ignored_roles_change" -> showIgnoredRolesModal(event, guildId);
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        if (!modalId.startsWith("level_modal_")) return;

        String guildId = event.getGuild().getId();

        // Permission check
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need **Manage Server** permission to change these settings.").setEphemeral(true).queue();
            return;
        }

        switch (modalId) {
            case "level_modal_xp_min_max" -> handleXpMinMaxModal(event, guildId);
            case "level_modal_cooldown" -> handleCooldownModal(event, guildId);
            case "level_modal_voice_xp_amount" -> handleVoiceXpAmountModal(event, guildId);
            case "level_modal_min_message_length" -> handleMinMessageLengthModal(event, guildId);
            case "level_modal_levelup_channel" -> handleLevelUpChannelModal(event, guildId);
            case "level_modal_levelup_message" -> handleLevelUpMessageModal(event, guildId);
            case "level_modal_ignored_channels" -> handleIgnoredChannelsModal(event, guildId);
            case "level_modal_ignored_roles" -> handleIgnoredRolesModal(event, guildId);
        }
    }

    public void handleRank(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        // Get target user (self or mentioned user)
        Member targetMember = event.getOption("user") != null
                ? event.getOption("user").getAsMember()
                : event.getMember();

        if (targetMember == null) {
            event.reply("❌ User not found.").setEphemeral(true).queue();
            return;
        }

        String userId = targetMember.getId();

        // Get user level data
        DatabaseHandler.UserLevelData userData = handler.getUserLevel(guildId, userId);
        int rank = handler.getUserRank(guildId, userId);
        int totalUsers = handler.getTotalLeveledUsers(guildId);

        // Calculate progress
        long xpForNext = userData.getXpForNextLevel();
        double progress = userData.getProgressPercent();
        String progressBar = buildProgressBar(progress);

        // Build rank card container
        Container rankContainer = Container.of(
                TextDisplay.of(String.format("# 📊 %s's Rank", targetMember.getEffectiveName())),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(String.format(
                        "**🏆 Rank:** #%d / %d\n" +
                        "**⭐ Level:** %d\n" +
                        "**✨ Total XP:** %,d",
                        rank, totalUsers,
                        userData.level,
                        userData.totalXp
                )),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("## Progress to Next Level"),
                TextDisplay.of(String.format(
                        "%s\n" +
                        "`%,d / %,d XP` (%.1f%%)",
                        progressBar,
                        userData.xp, xpForNext, progress
                )),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(String.format(
                        "-# 💬 Messages: %,d | 🎤 Voice: %d min",
                        userData.messagesCount,
                        userData.voiceMinutes
                ))
        ).withAccentColor(0x5865F2);

        MessageCreateBuilder message = new MessageCreateBuilder()
                .setComponents(rankContainer);

        event.reply(message.useComponentsV2().build()).setEphemeral(false).queue();
    }

    public void handleLeaderboard(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        // Get page from options (default 1)
        int page = event.getOption("page") != null
                ? (int) event.getOption("page").getAsLong()
                : 1;

        int pageSize = 10;
        int offset = (page - 1) * pageSize;

        // Get leaderboard data
        List<DatabaseHandler.UserLevelData> leaderboard = handler.getLeaderboard(guildId, pageSize, offset);
        int totalUsers = handler.getTotalLeveledUsers(guildId);
        int totalPages = (int) Math.ceil((double) totalUsers / pageSize);

        if (leaderboard.isEmpty()) {
            event.reply("📭 No leveling data yet! Start chatting to earn XP.").setEphemeral(true).queue();
            return;
        }

        // Build leaderboard entries
        StringBuilder entries = new StringBuilder();
        int rank = offset + 1;

        for (DatabaseHandler.UserLevelData userData : leaderboard) {
            String medal = switch (rank) {
                case 1 -> "🥇";
                case 2 -> "🥈";
                case 3 -> "🥉";
                default -> "**#" + rank + "**";
            };

            // Try to get member name
            Member member = event.getGuild().getMemberById(userData.userId);
            String displayName = member != null ? member.getEffectiveName() : "Unknown User";

            entries.append(String.format(
                    "%s %s\n" +
                    "-# Level %d • %,d XP\n\n",
                    medal, displayName,
                    userData.level, userData.totalXp
            ));
            rank++;
        }

        // Get caller's rank
        String callerId = event.getUser().getId();
        int callerRank = handler.getUserRank(guildId, callerId);
        DatabaseHandler.UserLevelData callerData = handler.getUserLevel(guildId, callerId);

        // Build leaderboard container
        Container leaderboardContainer = Container.of(
                TextDisplay.of(String.format("# 🏆 %s Leaderboard", event.getGuild().getName())),
                TextDisplay.of(String.format("-# Page %d of %d • %,d total members", page, Math.max(1, totalPages), totalUsers)),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(entries.toString().trim()),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(String.format(
                        "**Your Rank:** #%d (Level %d, %,d XP)",
                        callerRank, callerData.level, callerData.totalXp
                )),

                Separator.createDivider(Separator.Spacing.SMALL),

                // Pagination buttons
                ActionRow.of(
                        Button.secondary("level_leaderboard_prev_" + page, "⬅️ Previous")
                                .withDisabled(page <= 1),
                        Button.secondary("level_leaderboard_refresh_" + page, "🔄 Refresh"),
                        Button.secondary("level_leaderboard_next_" + page, "➡️ Next")
                                .withDisabled(page >= totalPages)
                )
        ).withAccentColor(0xFEE75C);

        MessageCreateBuilder message = new MessageCreateBuilder()
                .setComponents(leaderboardContainer);

        event.reply(message.useComponentsV2().build()).setEphemeral(false).queue();
    }

    /**
     * Handle leaderboard pagination buttons
     */
    private void handleLeaderboardPagination(ButtonInteractionEvent event, String guildId, String componentId) {
        // Parse current page from button ID
        String[] parts = componentId.split("_");
        int currentPage = Integer.parseInt(parts[parts.length - 1]);

        int newPage = currentPage;
        if (componentId.contains("_prev_")) {
            newPage = Math.max(1, currentPage - 1);
        } else if (componentId.contains("_next_")) {
            newPage = currentPage + 1;
        }
        // refresh keeps same page

        int pageSize = 10;
        int offset = (newPage - 1) * pageSize;

        // Get leaderboard data
        List<DatabaseHandler.UserLevelData> leaderboard = handler.getLeaderboard(guildId, pageSize, offset);
        int totalUsers = handler.getTotalLeveledUsers(guildId);
        int totalPages = (int) Math.ceil((double) totalUsers / pageSize);

        if (leaderboard.isEmpty() && newPage > 1) {
            event.reply("📭 No more entries on this page.").setEphemeral(true).queue();
            return;
        }

        // Build leaderboard entries
        StringBuilder entries = new StringBuilder();
        int rank = offset + 1;

        for (DatabaseHandler.UserLevelData userData : leaderboard) {
            String medal = switch (rank) {
                case 1 -> "🥇";
                case 2 -> "🥈";
                case 3 -> "🥉";
                default -> "**#" + rank + "**";
            };

            Member member = event.getGuild().getMemberById(userData.userId);
            String displayName = member != null ? member.getEffectiveName() : "Unknown User";

            entries.append(String.format(
                    "%s %s\n" +
                    "-# Level %d • %,d XP\n\n",
                    medal, displayName,
                    userData.level, userData.totalXp
            ));
            rank++;
        }

        // Get caller's rank
        String callerId = event.getUser().getId();
        int callerRank = handler.getUserRank(guildId, callerId);
        DatabaseHandler.UserLevelData callerData = handler.getUserLevel(guildId, callerId);

        // Build leaderboard container
        Container leaderboardContainer = Container.of(
                TextDisplay.of(String.format("# 🏆 %s Leaderboard", event.getGuild().getName())),
                TextDisplay.of(String.format("-# Page %d of %d • %,d total members", newPage, Math.max(1, totalPages), totalUsers)),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(entries.toString().trim()),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(String.format(
                        "**Your Rank:** #%d (Level %d, %,d XP)",
                        callerRank, callerData.level, callerData.totalXp
                )),

                Separator.createDivider(Separator.Spacing.SMALL),

                ActionRow.of(
                        Button.secondary("level_leaderboard_prev_" + newPage, "⬅️ Previous")
                                .withDisabled(newPage <= 1),
                        Button.secondary("level_leaderboard_refresh_" + newPage, "🔄 Refresh"),
                        Button.secondary("level_leaderboard_next_" + newPage, "➡️ Next")
                                .withDisabled(newPage >= totalPages)
                )
        ).withAccentColor(0xFEE75C);

        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(leaderboardContainer);

        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    public void handleSettings(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need **Manage Server** permission to view leveling settings.").setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        Container settingsContainer = buildMainSettingsContainer(guildId);

        MessageCreateBuilder message = new MessageCreateBuilder()
                .setComponents(settingsContainer);

        event.reply(message.useComponentsV2().build()).setEphemeral(true).queue();
    }

    // ==================== MAIN SETTINGS PAGE ====================

    private Container buildMainSettingsContainer(String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        String enabledStatus = settings.enabled ? "✅ Enabled" : "❌ Disabled";
        String voiceXpStatus = settings.voiceXpEnabled ? "✅" : "❌";
        String stackRewardsStatus = settings.stackRewards ? "✅ Stack" : "🔄 Replace";
        String resetOnLeaveStatus = settings.resetOnLeave ? "✅ Reset" : "💾 Keep";

        return Container.of(
                // Header
                TextDisplay.of("# ⚙️ Leveling System Settings"),
                TextDisplay.of("Configure how the XP and leveling system works on your server."),

                Separator.createDivider(Separator.Spacing.LARGE),

                // Status Overview
                TextDisplay.of("## 📊 Current Status"),
                TextDisplay.of(String.format(
                        "**System:** %s\n" +
                        "**XP Range:** %d - %d per message\n" +
                        "**Cooldown:** %ds between XP gains\n" +
                        "**Min Message Length:** %d characters\n" +
                        "**Voice XP:** %s (%d XP/interval)\n",
                        enabledStatus,
                        settings.xpMin, settings.xpMax,
                        settings.cooldownSeconds,
                        settings.minMessageLength,
                        voiceXpStatus, settings.voiceXpAmount
                )),

                Separator.createDivider(Separator.Spacing.LARGE),

                // Quick Toggles
                TextDisplay.of("## ⚡ Quick Toggles"),
                ActionRow.of(
                        Button.of(settings.enabled ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.DANGER,
                                "level_toggle_enabled", settings.enabled ? "✅ System ON" : "❌ System OFF"),
                        Button.of(settings.voiceXpEnabled ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_voice_xp_enabled", "🎤 Voice XP"),
                        Button.of(settings.levelupDm ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_levelup_dm", "📬 DM Notifications")
                ),
                ActionRow.of(
                        Button.of(settings.stackRewards ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_stack_rewards", stackRewardsStatus + " Roles"),
                        Button.of(settings.resetOnLeave ? net.dv8tion.jda.api.components.buttons.ButtonStyle.DANGER : net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS,
                                "level_toggle_reset_on_leave", resetOnLeaveStatus + " on Leave")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                // Navigation Buttons
                TextDisplay.of("## 🔧 Detailed Settings"),
                ActionRow.of(
                        Button.primary("level_settings_xp", "📈 XP Settings"),
                        Button.primary("level_settings_notifications", "🔔 Notifications"),
                        Button.primary("level_settings_rewards", "🎁 Role Rewards"),
                        Button.primary("level_settings_exceptions", "🚫 Exceptions")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                // Footer
                TextDisplay.of("-# Click buttons above to toggle settings or navigate to detailed configuration pages.")
        ).withAccentColor(settings.enabled ? 0x57F287 : 0xED4245); // Green if enabled, red if disabled
    }

    // ==================== XP SETTINGS PAGE ====================

    private Container buildXpSettingsContainer(String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        return Container.of(
                TextDisplay.of("# 📈 XP Settings"),
                TextDisplay.of("Configure how XP is earned on your server."),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 💬 Message XP"),
                TextDisplay.of(String.format(
                        "**XP per Message:** %d - %d (random)\n" +
                        "**Cooldown:** %d seconds\n" +
                        "-# Users earn random XP between min/max for each qualifying message.\n" +
                        "-# The cooldown prevents spam farming.\n",
                        settings.xpMin, settings.xpMax,
                        settings.cooldownSeconds
                )),

                ActionRow.of(Button.primary("level_settings_xp_min_max_change", "✏️ Change Min/Max XP"),
                            Button.primary("level_settings_xp_cooldown_change", "✏️ Change Cooldown")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 🎤 Voice XP"),
                TextDisplay.of(String.format(
                        "**Status:** %s\n" +
                        "**XP per Interval:** %d\n\n" +
                        "-# Voice XP is awarded periodically while users are in voice channels.\n",
                        settings.voiceXpEnabled ? "✅ Enabled" : "❌ Disabled",
                        settings.voiceXpAmount
                )),

                ActionRow.of(
                        Button.of(settings.voiceXpEnabled ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_voice_xp_enabled", settings.voiceXpEnabled ? "✅ Voice XP ON" : "❌ Voice XP OFF"),
                        Button.primary("level_settings_voice_xp_amount_change", "✏️ Change Voice XP")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 📏 Message Requirements"),
                TextDisplay.of(String.format(
                        "**Min Message Length:** %d characters\n\n" +
                        "-# Messages shorter than this won't earn XP.\n",
                        settings.minMessageLength
                )),

                ActionRow.of(Button.primary("level_settings_min_message_length_change", "✏️ Change Min Length")),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("level_settings_back", "⬅️ Back to Overview"),
                        Button.secondary("level_settings_refresh", "🔄 Refresh")
                )
        ).withAccentColor(0x5865F2); // Blurple
    }

    // ==================== NOTIFICATION SETTINGS PAGE ====================

    private Container buildNotificationSettingsContainer(String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        String channelDisplay = settings.levelupChannelId;
        if (channelDisplay == null || channelDisplay.equals("current")) {
            channelDisplay = "📍 Current Channel";
        } else if (channelDisplay.equals("0") || channelDisplay.isEmpty()) {
            channelDisplay = "🔇 Disabled";
        } else {
            channelDisplay = "<#" + channelDisplay + ">";
        }

        return Container.of(
                TextDisplay.of("# 🔔 Notification Settings"),
                TextDisplay.of("Configure level-up announcements."),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 📢 Level-Up Announcements"),
                TextDisplay.of(String.format(
                        "**Announcement Channel:** %s\n" +
                        "**DM Notifications:** %s\n\n" +
                        "-# Set channel to 'current' to announce in the channel where the user leveled up.\n",
                        channelDisplay,
                        settings.levelupDm ? "✅ Enabled" : "❌ Disabled"
                )),

                ActionRow.of(
                        Button.of(settings.levelupDm ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_levelup_dm", settings.levelupDm ? "✅ DM ON" : "❌ DM OFF"),
                        Button.primary("level_settings_levelup_channel_change", "✏️ Set Channel")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 📝 Custom Messages"),
                TextDisplay.of(
                        "Custom level-up messages support placeholders:\n" +
                        "• `{mention}` - User mention\n" +
                        "• `{username}` - Username\n" +
                        "• `{level}` - New level\n" +
                        "• `{xp}` - Total XP\n"),

                ActionRow.of(Button.primary("level_settings_levelup_message_change", "✏️ Edit Message")),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("level_settings_back", "⬅️ Back to Overview"),
                        Button.secondary("level_settings_refresh", "🔄 Refresh")
                )
        ).withAccentColor(0xFEE75C); // Yellow
    }

    // ==================== REWARD SETTINGS PAGE ====================

    private Container buildRewardSettingsContainer(String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        String rewardsDisplay = "No role rewards configured yet.";
        if (settings.rewards != null && !settings.rewards.isEmpty()) {
            // Parse JSON rewards (simplified display)
            rewardsDisplay = "Role rewards are configured. Use `/leveling rewards` to manage.";
        }

        return Container.of(
                TextDisplay.of("# 🎁 Role Rewards"),
                TextDisplay.of("Configure roles given at specific levels."),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 🏆 Reward Behavior"),
                TextDisplay.of(String.format(
                        "**Stack Mode:** %s\n\n" +
                        "• **Stack:** Users keep all earned roles\n" +
                        "• **Replace:** Only the highest level role is kept\n",
                        settings.stackRewards ? "✅ Stack Roles" : "🔄 Replace Roles"
                )),

                ActionRow.of(
                        Button.of(settings.stackRewards ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.PRIMARY,
                                "level_toggle_stack_rewards", settings.stackRewards ? "✅ Stacking ON" : "🔄 Replacing")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 📋 Configured Rewards"),
                TextDisplay.of(rewardsDisplay),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("level_settings_back", "⬅️ Back to Overview"),
                        Button.secondary("level_settings_refresh", "🔄 Refresh")
                )
        ).withAccentColor(0xEB459E); // Fuchsia
    }

    // ==================== EXCEPTIONS SETTINGS PAGE ====================

    private Container buildExceptionsSettingsContainer(String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        String ignoredChannelsDisplay = "None";
        if (settings.ignoredChannels != null && !settings.ignoredChannels.isEmpty()) {
            String[] channels = settings.ignoredChannels.split(",");
            StringBuilder sb = new StringBuilder();
            for (String ch : channels) {
                if (!ch.isEmpty()) sb.append("<#").append(ch.trim()).append("> ");
            }
            ignoredChannelsDisplay = sb.toString().trim();
            if (ignoredChannelsDisplay.isEmpty()) ignoredChannelsDisplay = "None";
        }

        String ignoredRolesDisplay = "None";
        if (settings.ignoredRoles != null && !settings.ignoredRoles.isEmpty()) {
            String[] roles = settings.ignoredRoles.split(",");
            StringBuilder sb = new StringBuilder();
            for (String r : roles) {
                if (!r.isEmpty()) sb.append("<@&").append(r.trim()).append("> ");
            }
            ignoredRolesDisplay = sb.toString().trim();
            if (ignoredRolesDisplay.isEmpty()) ignoredRolesDisplay = "None";
        }

        return Container.of(
                TextDisplay.of("# 🚫 Exceptions & Reset"),
                TextDisplay.of("Configure channels/roles to exclude and leave behavior."),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 📵 Ignored Channels"),
                TextDisplay.of("Users won't earn XP in these channels:\n" + ignoredChannelsDisplay),

                ActionRow.of(Button.primary("level_settings_ignored_channels_change", "✏️ Edit Ignored Channels")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 🚷 Ignored Roles"),
                TextDisplay.of("Users with these roles won't earn XP:\n" + ignoredRolesDisplay),

                ActionRow.of(Button.primary("level_settings_ignored_roles_change", "✏️ Edit Ignored Roles")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 🚪 On Server Leave"),
                TextDisplay.of(String.format(
                        "**Current Setting:** %s\n\n" +
                        "• **Reset:** XP is deleted when user leaves\n" +
                        "• **Keep:** XP is preserved if user rejoins\n",
                        settings.resetOnLeave ? "🗑️ Reset XP on Leave" : "💾 Keep XP on Leave"
                )),

                ActionRow.of(
                        Button.of(settings.resetOnLeave ? net.dv8tion.jda.api.components.buttons.ButtonStyle.DANGER : net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS,
                                "level_toggle_reset_on_leave", settings.resetOnLeave ? "🗑️ Reset ON" : "💾 Keep Data")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("level_settings_back", "⬅️ Back to Overview"),
                        Button.secondary("level_settings_refresh", "🔄 Refresh")
                )
        ).withAccentColor(0xED4245); // Red
    }

    // ==================== INTERACTION HANDLERS ====================

    private void handleToggleSetting(ButtonInteractionEvent event, String guildId, String setting) {
        String column = switch (setting) {
            case "enabled" -> "enabled";
            case "voice_xp_enabled" -> "voice_xp_enabled";
            case "levelup_dm" -> "levelup_dm";
            case "stack_rewards" -> "stack_rewards";
            case "reset_on_leave" -> "reset_on_leave";
            default -> null;
        };

        if (column == null) {
            event.reply("❌ Unknown setting.").setEphemeral(true).queue();
            return;
        }

        handler.toggleLevelSetting(guildId, column);

        // Determine which page to refresh based on which setting was toggled
        String currentPage = getCurrentPageFromButton(event.getComponentId());
        Container updatedContainer = switch (currentPage) {
            case "xp" -> buildXpSettingsContainer(guildId);
            case "notifications" -> buildNotificationSettingsContainer(guildId);
            case "rewards" -> buildRewardSettingsContainer(guildId);
            case "exceptions" -> buildExceptionsSettingsContainer(guildId);
            default -> buildMainSettingsContainer(guildId);
        };

        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(updatedContainer);

        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private String getCurrentPageFromButton(String buttonId) {
        // Determine page context based on which toggle was clicked
        // Most toggles are on main page, but some specific ones are on sub-pages
        if (buttonId.equals("level_toggle_voice_xp_enabled")) {
            // Could be main or xp page - default to main for safety
            return "main";
        }
        return "main";
    }

    private void showMainSettingsPage(ButtonInteractionEvent event, String guildId) {
        Container container = buildMainSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private void showXpSettingsPage(ButtonInteractionEvent event, String guildId) {
        Container container = buildXpSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private void showNotificationSettingsPage(ButtonInteractionEvent event, String guildId) {
        Container container = buildNotificationSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private void showRewardSettingsPage(ButtonInteractionEvent event, String guildId) {
        Container container = buildRewardSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private void showExceptionsSettingsPage(ButtonInteractionEvent event, String guildId) {
        Container container = buildExceptionsSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    // ==================== MODAL SHOW METHODS ====================

    private void showXpMinMaxModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        TextInput minXp = TextInput.create("xp_min", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 15")
                .setValue(String.valueOf(settings.xpMin))
                .setRequiredRange(1, 5)
                .build();

        TextInput maxXp = TextInput.create("xp_max", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 25")
                .setValue(String.valueOf(settings.xpMax))
                .setRequiredRange(1, 5)
                .build();

        Modal modal = Modal.create("level_modal_xp_min_max", "📈 XP Range Settings")
                .addComponents(
                        Label.of("Minimum XP per Message", minXp),
                        Label.of("Maximum XP per Message", maxXp)
                )
                .build();

        event.replyModal(modal).queue();
    }

    private void showCooldownModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        TextInput cooldown = TextInput.create("cooldown", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 60")
                .setValue(String.valueOf(settings.cooldownSeconds))
                .setRequiredRange(1, 5)
                .build();

        Modal modal = Modal.create("level_modal_cooldown", "⏱️ XP Cooldown Settings")
                .addComponents(Label.of("Cooldown (seconds)", cooldown))
                .build();

        event.replyModal(modal).queue();
    }

    private void showVoiceXpAmountModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        TextInput voiceXp = TextInput.create("voice_xp_amount", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 10")
                .setValue(String.valueOf(settings.voiceXpAmount))
                .setRequiredRange(1, 5)
                .build();

        Modal modal = Modal.create("level_modal_voice_xp_amount", "🎤 Voice XP Settings")
                .addComponents(Label.of("Voice XP per Interval", voiceXp))
                .build();

        event.replyModal(modal).queue();
    }

    private void showMinMessageLengthModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        TextInput minLength = TextInput.create("min_message_length", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 3")
                .setValue(String.valueOf(settings.minMessageLength))
                .setRequiredRange(1, 4)
                .build();

        Modal modal = Modal.create("level_modal_min_message_length", "📝 Message Length Settings")
                .addComponents(Label.of("Minimum Message Length (characters)", minLength))
                .build();

        event.replyModal(modal).queue();
    }

    private void showLevelUpChannelModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        String currentChannel = settings.levelupChannelId != null ? settings.levelupChannelId : "current";

        TextInput channel = TextInput.create("levelup_channel", TextInputStyle.SHORT)
                .setPlaceholder("Channel ID, 'current', or '0' to disable")
                .setValue(currentChannel)
                .setRequiredRange(1, 20)
                .build();

        Modal modal = Modal.create("level_modal_levelup_channel", "📢 Level-Up Channel")
                .addComponents(Label.of("Level-Up Channel ID", channel))
                .build();

        event.replyModal(modal).queue();
    }

    private void showLevelUpMessageModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        String currentMessage = settings.levelupMessages != null ? settings.levelupMessages : "";

        TextInput message = TextInput.create("levelup_message", TextInputStyle.PARAGRAPH)
                .setPlaceholder("🎉 {mention} reached level {level}!")
                .setValue(currentMessage)
                .setRequired(false)
                .setMaxLength(500)
                .build();

        Modal modal = Modal.create("level_modal_levelup_message", "💬 Custom Level-Up Message")
                .addComponents(Label.of("Custom Level-Up Message", message))
                .build();

        event.replyModal(modal).queue();
    }

    private void showIgnoredChannelsModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        String currentChannels = settings.ignoredChannels != null ? settings.ignoredChannels : "";

        TextInput channels = TextInput.create("ignored_channels", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Enter channel IDs separated by commas\ne.g., 123456789012345678,987654321098765432")
                .setValue(currentChannels)
                .setRequired(false)
                .setMaxLength(1000)
                .build();

        Modal modal = Modal.create("level_modal_ignored_channels", "📵 Ignored Channels")
                .addComponents(Label.of("Ignored Channel IDs", channels))
                .build();

        event.replyModal(modal).queue();
    }

    private void showIgnoredRolesModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        String currentRoles = settings.ignoredRoles != null ? settings.ignoredRoles : "";

        TextInput roles = TextInput.create("ignored_roles", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Enter role IDs separated by commas\ne.g., 123456789012345678,987654321098765432")
                .setValue(currentRoles)
                .setRequired(false)
                .setMaxLength(1000)
                .build();

        Modal modal = Modal.create("level_modal_ignored_roles", "🚷 Ignored Roles")
                .addComponents(Label.of("Ignored Role IDs", roles))
                .build();

        event.replyModal(modal).queue();
    }

    // ==================== MODAL HANDLE METHODS ====================

    private void handleXpMinMaxModal(ModalInteractionEvent event, String guildId) {
        String minXpStr = Objects.requireNonNull(event.getValue("xp_min")).getAsString().trim();
        String maxXpStr = Objects.requireNonNull(event.getValue("xp_max")).getAsString().trim();

        try {
            int minXp = Integer.parseInt(minXpStr);
            int maxXp = Integer.parseInt(maxXpStr);

            if (minXp < 1 || maxXp < 1) {
                event.reply("❌ XP values must be at least 1.").setEphemeral(true).queue();
                return;
            }

            if (minXp > maxXp) {
                event.reply("❌ Minimum XP cannot be greater than maximum XP.").setEphemeral(true).queue();
                return;
            }

            if (maxXp > 10000) {
                event.reply("❌ Maximum XP cannot exceed 10,000.").setEphemeral(true).queue();
                return;
            }

            handler.updateLevelSetting(guildId, "xp_min", minXp);
            handler.updateLevelSetting(guildId, "xp_max", maxXp);

            Container container = buildXpSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter valid numbers for XP values.").setEphemeral(true).queue();
        }
    }

    private void handleCooldownModal(ModalInteractionEvent event, String guildId) {
        String cooldownStr = Objects.requireNonNull(event.getValue("cooldown")).getAsString().trim();

        try {
            int cooldown = Integer.parseInt(cooldownStr);

            if (cooldown < 0) {
                event.reply("❌ Cooldown cannot be negative.").setEphemeral(true).queue();
                return;
            }

            if (cooldown > 86400) {
                event.reply("❌ Cooldown cannot exceed 86,400 seconds (24 hours).").setEphemeral(true).queue();
                return;
            }

            handler.updateLevelSetting(guildId, "cooldown_seconds", cooldown);

            Container container = buildXpSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter a valid number for cooldown.").setEphemeral(true).queue();
        }
    }

    private void handleVoiceXpAmountModal(ModalInteractionEvent event, String guildId) {
        String voiceXpStr = Objects.requireNonNull(event.getValue("voice_xp_amount")).getAsString().trim();

        try {
            int voiceXp = Integer.parseInt(voiceXpStr);

            if (voiceXp < 1) {
                event.reply("❌ Voice XP must be at least 1.").setEphemeral(true).queue();
                return;
            }

            if (voiceXp > 10000) {
                event.reply("❌ Voice XP cannot exceed 10,000.").setEphemeral(true).queue();
                return;
            }

            handler.updateLevelSetting(guildId, "voice_xp_amount", voiceXp);

            Container container = buildXpSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter a valid number for voice XP.").setEphemeral(true).queue();
        }
    }

    private void handleMinMessageLengthModal(ModalInteractionEvent event, String guildId) {
        String minLengthStr = Objects.requireNonNull(event.getValue("min_message_length")).getAsString().trim();

        try {
            int minLength = Integer.parseInt(minLengthStr);

            if (minLength < 0) {
                event.reply("❌ Minimum message length cannot be negative.").setEphemeral(true).queue();
                return;
            }

            if (minLength > 1000) {
                event.reply("❌ Minimum message length cannot exceed 1,000 characters.").setEphemeral(true).queue();
                return;
            }

            handler.updateLevelSetting(guildId, "min_message_length", minLength);

            Container container = buildXpSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter a valid number for minimum message length.").setEphemeral(true).queue();
        }
    }

    private void handleLevelUpChannelModal(ModalInteractionEvent event, String guildId) {
        String channelInput = Objects.requireNonNull(event.getValue("levelup_channel")).getAsString().trim();

        // Validate input
        if (!channelInput.equals("current") && !channelInput.equals("0")) {
            try {
                Long.parseLong(channelInput);
            } catch (NumberFormatException e) {
                event.reply("❌ Please enter a valid channel ID, 'current', or '0' to disable.").setEphemeral(true).queue();
                return;
            }
        }

        handler.updateLevelSetting(guildId, "levelup_channel_id", channelInput);

        Container container = buildNotificationSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private void handleLevelUpMessageModal(ModalInteractionEvent event, String guildId) {
        String messageInput = Objects.requireNonNull(event.getValue("levelup_message")).getAsString().trim();

        // Allow empty string to reset to default
        handler.updateLevelSetting(guildId, "levelup_messages", messageInput.isEmpty() ? null : messageInput);

        Container container = buildNotificationSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private void handleIgnoredChannelsModal(ModalInteractionEvent event, String guildId) {
        String channelsInput = Objects.requireNonNull(event.getValue("ignored_channels")).getAsString().trim();

        // Validate channel IDs if provided
        if (!channelsInput.isEmpty()) {
            String[] channels = channelsInput.split(",");
            for (String channel : channels) {
                String trimmed = channel.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        Long.parseLong(trimmed);
                    } catch (NumberFormatException e) {
                        event.reply("❌ Invalid channel ID: " + trimmed + ". Please enter valid channel IDs separated by commas.").setEphemeral(true).queue();
                        return;
                    }
                }
            }
        }

        handler.updateLevelSetting(guildId, "ignored_channels", channelsInput.isEmpty() ? null : channelsInput);

        Container container = buildExceptionsSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private void handleIgnoredRolesModal(ModalInteractionEvent event, String guildId) {
        String rolesInput = Objects.requireNonNull(event.getValue("ignored_roles")).getAsString().trim();

        // Validate role IDs if provided
        if (!rolesInput.isEmpty()) {
            String[] roles = rolesInput.split(",");
            for (String role : roles) {
                String trimmed = role.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        Long.parseLong(trimmed);
                    } catch (NumberFormatException e) {
                        event.reply("❌ Invalid role ID: " + trimmed + ". Please enter valid role IDs separated by commas.").setEphemeral(true).queue();
                        return;
                    }
                }
            }
        }

        handler.updateLevelSetting(guildId, "ignored_roles", rolesInput.isEmpty() ? null : rolesInput);

        Container container = buildExceptionsSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }
}
