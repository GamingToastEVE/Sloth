package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Text;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LevelingSystemCommandListener extends ListenerAdapter {
    private final DatabaseHandler handler;
    private final Random random = new Random();

    // Voice XP tracking - maps guildId:userId to join timestamp
    private final Map<String, Long> voiceJoinTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> lastVoiceXpTime = new ConcurrentHashMap<>();

    // JDA reference for scheduler (set on first voice event)
    private net.dv8tion.jda.api.JDA jdaInstance = null;

    // Scheduled executor for voice XP awards
    private final ScheduledExecutorService voiceXpScheduler = Executors.newScheduledThreadPool(1);

    public LevelingSystemCommandListener(DatabaseHandler handler) {
        this.handler = handler;

        // Start voice XP award task - runs every 60 seconds
        voiceXpScheduler.scheduleAtFixedRate(this::awardVoiceXp, 60, 60, TimeUnit.SECONDS);
    }

    // ==================== LANGUAGE HELPER METHODS ====================

    /**
     * Get a translated string for a guild
     */
    private String t(String guildId, String key) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            return lang.get(guildId, key);
        }
        return key;
    }

    /**
     * Get a translated string with format arguments
     */
    private String t(String guildId, String key, Object... args) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            return lang.get(guildId, key, args);
        }
        try {
            return String.format(key, args);
        } catch (Exception e) {
            return key;
        }
    }

    // ==================== MESSAGE EVENT FOR XP ====================

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bots and DMs
        if (event.getAuthor().isBot() || event.getChannel() instanceof PrivateChannel) {
            return;
        }

        // Store JDA reference
        if (jdaInstance == null) {
            jdaInstance = event.getJDA();
        }

        String guildId = event.getGuild().getId();
        String oderId = event.getAuthor().getId();
        String channelId = event.getChannel().getId();

        // Check if leveling system is active
        if (!handler.isSystemActive(guildId, "leveling")) {
            return;
        }

        // Handle leveling XP
        handleLevelingXp(event, guildId, oderId, channelId);
    }

    // ==================== VOICE EVENT FOR XP ====================

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        if (event.getMember().getUser().isBot()) return;

        // Store JDA reference and sync existing voice users on first event
        if (jdaInstance == null) {
            jdaInstance = event.getJDA();
            syncExistingVoiceUsers();
        }

        String guildId = event.getGuild().getId();
        String oderId = event.getMember().getId();
        String key = guildId + ":" + oderId;

        // Check if leveling system is active
        if (!handler.isSystemActive(guildId, "leveling")) {
            return;
        }

        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);
        if (!settings.enabled || !settings.voiceXpEnabled) {
            return;
        }

        AudioChannel joined = event.getChannelJoined();
        AudioChannel left = event.getChannelLeft();

        if (joined != null && left == null) {
            // User joined a voice channel
            voiceJoinTimes.put(key, System.currentTimeMillis());
            System.out.println("[VoiceXP] User " + event.getMember().getEffectiveName() + " joined voice, now tracking");
        } else if (left != null && joined == null) {
            // User left voice channel
            voiceJoinTimes.remove(key);
            lastVoiceXpTime.remove(key);
            System.out.println("[VoiceXP] User " + event.getMember().getEffectiveName() + " left voice, stopped tracking");
        } else if (joined != null) {
            // User switched channels - keep tracking
            System.out.println("[VoiceXP] User " + event.getMember().getEffectiveName() + " switched channels, still tracking");
        }
    }

    /**
     * Sync existing users who are already in voice channels when the bot starts
     */
    private void syncExistingVoiceUsers() {
        if (jdaInstance == null) return;

        System.out.println("[VoiceXP] Syncing existing voice users...");
        int count = 0;

        for (Guild guild : jdaInstance.getGuilds()) {
            // Check if leveling is active for this guild
            if (!handler.isSystemActive(guild.getId(), "leveling")) {
                continue;
            }

            DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guild.getId());
            if (!settings.enabled || !settings.voiceXpEnabled) {
                continue;
            }

            // Iterate through all voice channels
            for (net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel voiceChannel : guild.getVoiceChannels()) {
                for (Member member : voiceChannel.getMembers()) {
                    if (member.getUser().isBot()) continue;

                    String key = guild.getId() + ":" + member.getId();
                    if (!voiceJoinTimes.containsKey(key)) {
                        voiceJoinTimes.put(key, System.currentTimeMillis());
                        count++;
                    }
                }
            }

            // Also check stage channels
            for (net.dv8tion.jda.api.entities.channel.concrete.StageChannel stageChannel : guild.getStageChannels()) {
                for (Member member : stageChannel.getMembers()) {
                    if (member.getUser().isBot()) continue;

                    String key = guild.getId() + ":" + member.getId();
                    if (!voiceJoinTimes.containsKey(key)) {
                        voiceJoinTimes.put(key, System.currentTimeMillis());
                        count++;
                    }
                }
            }
        }

        System.out.println("[VoiceXP] Synced " + count + " users already in voice channels");
    }

    /**
     * Award voice XP to all users currently in voice channels
     * This runs periodically via scheduler
     */
    private void awardVoiceXp() {
        if (jdaInstance == null) {
            System.out.println("[VoiceXP] JDA instance is null, skipping...");
            return;
        }

        try {
            Map<String, Long> snapshot = new HashMap<>(voiceJoinTimes);
            System.out.println("[VoiceXP] Processing " + snapshot.size() + " users in voice channels");

            for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
                String[] parts = entry.getKey().split(":");
                if (parts.length != 2) continue;

                String guildId = parts[0];
                String oderId = parts[1];
                String key = entry.getKey();

                // Get settings
                DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);
                if (!settings.enabled || !settings.voiceXpEnabled) {
                    System.out.println("[VoiceXP] Voice XP disabled for guild " + guildId);
                    continue;
                }

                // Check cooldown
                Long lastAward = lastVoiceXpTime.get(key);
                long now = System.currentTimeMillis();

                if (lastAward != null) {
                    long elapsed = (now - lastAward) / 1000;
                    if (elapsed < settings.voiceXpCooldown) {
                        System.out.println("[VoiceXP] User " + oderId + " on cooldown (" + elapsed + "s / " + settings.voiceXpCooldown + "s)");
                        continue;
                    }
                }

                // Get the guild and member
                Guild guild = jdaInstance.getGuildById(guildId);
                if (guild == null) {
                    System.out.println("[VoiceXP] Guild " + guildId + " not found, removing from tracking");
                    voiceJoinTimes.remove(key);
                    continue;
                }

                Member member = guild.getMemberById(oderId);
                if (member == null) {
                    System.out.println("[VoiceXP] Member " + oderId + " not found, removing from tracking");
                    voiceJoinTimes.remove(key);
                    continue;
                }

                // Check if member is still in a voice channel
                if (member.getVoiceState() == null || member.getVoiceState().getChannel() == null) {
                    System.out.println("[VoiceXP] Member " + member.getEffectiveName() + " no longer in voice, removing from tracking");
                    voiceJoinTimes.remove(key);
                    lastVoiceXpTime.remove(key);
                    continue;
                }

                AudioChannel voiceChannel = member.getVoiceState().getChannel();

                // Check minimum members requirement
                long humanMembers = voiceChannel.getMembers().stream()
                        .filter(m -> !m.getUser().isBot())
                        .count();
                if (humanMembers < settings.voiceXpMinMembers) {
                    System.out.println("[VoiceXP] Not enough members in channel (" + humanMembers + " / " + settings.voiceXpMinMembers + " required)");
                    continue;
                }

                // Check anti-AFK (user must not be muted and not deafened)
                if (settings.voiceXpAntiAfk) {
                    if (member.getVoiceState().isSelfMuted() ||
                        member.getVoiceState().isSelfDeafened() ||
                        member.getVoiceState().isGuildMuted() ||
                        member.getVoiceState().isGuildDeafened()) {
                        System.out.println("[VoiceXP] Member " + member.getEffectiveName() + " is AFK (muted/deafened)");
                        continue;
                    }
                }

                // Check if user has an ignored role
                if (hasIgnoredRole(settings, member)) {
                    System.out.println("[VoiceXP] Member " + member.getEffectiveName() + " has ignored role");
                    continue;
                }

                // Check max level
                if (settings.maxLevel > 0) {
                    DatabaseHandler.UserLevelData userData = handler.getUserLevel(guildId, oderId);
                    if (userData.level >= settings.maxLevel) {
                        System.out.println("[VoiceXP] Member " + member.getEffectiveName() + " at max level");
                        continue;
                    }
                }

                // Calculate XP amount (random between min and max)
                int xpAmount = settings.voiceXpMin;
                if (settings.voiceXpMax > settings.voiceXpMin) {
                    xpAmount = random.nextInt(settings.voiceXpMax - settings.voiceXpMin + 1) + settings.voiceXpMin;
                }

                // Apply multiplier
                xpAmount = (int) Math.round(xpAmount * settings.xpMultiplier);

                // Calculate minutes to add (based on cooldown interval)
                int minutesToAdd = settings.voiceXpCooldown / 60;
                if (minutesToAdd < 1) minutesToAdd = 1;

                // Award Voice XP using the correct method that tracks voice_minutes
                int newLevel = handler.addVoiceXpToUser(guildId, oderId, xpAmount, minutesToAdd);

                // Update last award time
                lastVoiceXpTime.put(key, now);

                System.out.println("[VoiceXP] Awarded " + xpAmount + " XP to " + member.getEffectiveName() +
                                   " (+" + minutesToAdd + " minutes). New level: " + (newLevel > 0 ? newLevel : "no change"));

                // Handle level up
                if (newLevel > 0) {
                    handleVoiceLevelUp(guild, settings, member, newLevel);
                }
            }
        } catch (Exception e) {
            System.err.println("[VoiceXP] Error in voice XP scheduler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handle level up from voice XP
     */
    private void handleVoiceLevelUp(net.dv8tion.jda.api.entities.Guild guild,
                                     DatabaseHandler.LevelSettingsData settings,
                                     Member member, int newLevel) {
        // Apply role rewards
        applyRoleRewardsForMember(guild, settings, member, newLevel);

        // Build level-up message
        String levelUpMessage = buildLevelUpMessageForMember(settings, member, newLevel);

        // Send notification
        sendVoiceLevelUpNotification(guild, settings, member, levelUpMessage, newLevel);

        // Fire level up event
        LevelUpEvent levelUpEvent = new LevelUpEvent(jdaInstance, guild, member, newLevel, newLevel - 1);
        jdaInstance.getEventManager().handle(levelUpEvent);
    }

    /**
     * Send level-up notification for voice level ups
     */
    private void sendVoiceLevelUpNotification(net.dv8tion.jda.api.entities.Guild guild,
                                               DatabaseHandler.LevelSettingsData settings,
                                               Member member, String message, int newLevel) {
        // Send DM if enabled
        if (settings.levelupDm) {
            member.getUser().openPrivateChannel().queue(
                    dm -> dm.sendMessage(message).queue(success -> {}, error -> {}),
                    error -> {}
            );
        }

        String channelSetting = settings.levelupChannelId;
        if (channelSetting == null || channelSetting.equals("0") || channelSetting.isEmpty() || channelSetting.equals("current")) {
            return; // Can't send to "current" for voice level ups
        }

        TextChannel targetChannel = guild.getTextChannelById(channelSetting);
        if (targetChannel != null) {
            DatabaseHandler.UserLevelData userData = handler.getUserLevel(guild.getId(), member.getId());
            long xpForNext = userData.getXpForNextLevel();
            double progress = userData.getProgressPercent();
            String progressBar = buildProgressBar(progress);

            Container levelUpContainer = Container.of(
                    TextDisplay.of("# " + t(guild.getId(), "leveling_.level_up_voice")),
                    Separator.createDivider(Separator.Spacing.SMALL),
                    TextDisplay.of(message),
                    Separator.createDivider(Separator.Spacing.SMALL),
                    TextDisplay.of(String.format(
                            "**%s %d** → **%s %d**\n%s\n`%d / %d XP` (%.1f%%)",
                            t(guild.getId(), "leveling_.level"), newLevel - 1,
                            t(guild.getId(), "leveling_.level"), newLevel,
                            progressBar, userData.xp, xpForNext, progress
                    ))
            ).withAccentColor(0x9B59B6); // Purple for voice

            MessageCreateBuilder messageBuilder = new MessageCreateBuilder().setComponents(levelUpContainer);
            targetChannel.sendMessage(messageBuilder.useComponentsV2().build()).queue(
                    success -> {},
                    error -> System.err.println("Failed to send voice level-up message: " + error.getMessage())
            );
        }
    }

    // ==================== REACTION EVENT FOR XP ====================

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        // Ignore bots and DMs
        if (event.getUser() == null || event.getUser().isBot()) return;
        if (!event.isFromGuild()) return;

        String guildId = event.getGuild().getId();
        String reactorId = event.getUserId(); // Person who added the reaction
        String channelId = event.getChannel().getId();

        // Check if leveling system is active
        if (!handler.isSystemActive(guildId, "leveling")) {
            return;
        }

        // Get settings
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);
        if (!settings.enabled || !settings.reactionXpEnabled) {
            return;
        }

        // Check if channel is ignored
        if (isChannelIgnored(settings, channelId)) {
            return;
        }

        // Check if reactor has an ignored role
        Member reactorMember = event.getMember();
        if (reactorMember != null && hasIgnoredRole(settings, reactorMember)) {
            return;
        }

        // Get the message to find the author
        event.retrieveMessage().queue(message -> {
            handleReactionXp(event, settings, guildId, reactorId, message);
        }, error -> {
            // Message might be deleted, ignore
        });
    }

    /**
     * Handle XP gain from reactions
     */
    private void handleReactionXp(MessageReactionAddEvent event, DatabaseHandler.LevelSettingsData settings,
                                   String guildId, String reactorId, Message message) {
        String messageAuthorId = message.getAuthor().getId();

        // Don't give XP for self-reactions
        if (reactorId.equals(messageAuthorId)) {
            return;
        }

        // Don't give XP for bot message reactions
        if (message.getAuthor().isBot()) {
            return;
        }

        // Calculate XP amount
        int xpAmount = settings.reactionXpMin;
        if (settings.reactionXpMax > settings.reactionXpMin) {
            xpAmount = random.nextInt(settings.reactionXpMax - settings.reactionXpMin + 1) + settings.reactionXpMin;
        }

        // Apply multiplier
        xpAmount = (int) Math.round(xpAmount * settings.xpMultiplier);

        // Award XP based on awards setting
        switch (settings.reactionXpAwards) {
            case "sender" -> {
                // Only reactor gets XP
                awardReactionXpToUser(event, settings, guildId, reactorId, xpAmount, "reaction_sender");
            }
            case "receiver" -> {
                // Only message author gets XP
                awardReactionXpToUser(event, settings, guildId, messageAuthorId, xpAmount, "reaction_receiver");
            }
            default -> { // "both"
                // Both get XP
                awardReactionXpToUser(event, settings, guildId, reactorId, xpAmount, "reaction_sender");
                awardReactionXpToUser(event, settings, guildId, messageAuthorId, xpAmount, "reaction_receiver");
            }
        }
    }

    /**
     * Award reaction XP to a user with cooldown check
     */
    private void awardReactionXpToUser(MessageReactionAddEvent event, DatabaseHandler.LevelSettingsData settings,
                                        String guildId, String userId, int xpAmount, String cooldownType) {
        // Check cooldown (use reaction-specific cooldown)
        if (handler.isUserOnReactionXpCooldown(guildId, userId, cooldownType, settings.reactionXpCooldown)) {
            return;
        }

        // Check if user has an ignored role
        Member member = event.getGuild().getMemberById(userId);
        if (member != null && hasIgnoredRole(settings, member)) {
            return;
        }

        // Check max level
        if (settings.maxLevel > 0) {
            DatabaseHandler.UserLevelData userData = handler.getUserLevel(guildId, userId);
            if (userData.level >= settings.maxLevel) {
                return; // User is at max level
            }
        }

        // Add XP
        int newLevel = handler.addXpToUser(guildId, userId, xpAmount);

        // Handle level up if occurred
        if (newLevel > 0 && member != null) {
            handleReactionLevelUp(event, settings, guildId, member, newLevel);
        }
    }

    /**
     * Handle level up from reaction XP
     */
    private void handleReactionLevelUp(MessageReactionAddEvent event, DatabaseHandler.LevelSettingsData settings,
                                        String guildId, Member member, int newLevel) {
        // Apply role rewards
        applyRoleRewardsForMember(event.getGuild(), settings, member, newLevel);

        // Build and send level-up message
        String levelUpMessage = buildLevelUpMessageForMember(settings, member, newLevel);
        sendLevelUpNotificationForReaction(event, settings, member, levelUpMessage, newLevel);

        // Fire level up event
        LevelUpEvent levelUpEvent = new LevelUpEvent(event.getJDA(), event.getGuild(), member, newLevel, newLevel - 1);
        event.getJDA().getEventManager().handle(levelUpEvent);
    }

    /**
     * Apply role rewards for a member (generic version for non-message events)
     */
    private void applyRoleRewardsForMember(net.dv8tion.jda.api.entities.Guild guild,
                                            DatabaseHandler.LevelSettingsData settings,
                                            Member member, int newLevel) {
        if (settings.rewards == null || settings.rewards.isEmpty()) {
            return;
        }

        try {
            JSONArray rewards = new JSONArray(settings.rewards);
            Role previousRewardRole = null;
            Role newRewardRole = null;
            int previousRewardLevel = 0;

            for (int i = 0; i < rewards.length(); i++) {
                JSONObject reward = rewards.getJSONObject(i);
                int rewardLevel = reward.getInt("level");
                String roleId = reward.getString("role_id");

                Role role = guild.getRoleById(roleId);
                if (role == null) continue;

                if (rewardLevel == newLevel) {
                    newRewardRole = role;
                } else if (rewardLevel < newLevel && !settings.stackRewards) {
                    if (rewardLevel > previousRewardLevel) {
                        previousRewardRole = role;
                        previousRewardLevel = rewardLevel;
                    }
                }
            }

            if (newRewardRole != null) {
                guild.addRoleToMember(member, newRewardRole).queue(
                        success -> {},
                        error -> System.err.println("Failed to add reward role: " + error.getMessage())
                );
            }

            if (!settings.stackRewards && previousRewardRole != null && newRewardRole != null) {
                guild.removeRoleFromMember(member, previousRewardRole).queue(
                        success -> {},
                        error -> System.err.println("Failed to remove previous reward role: " + error.getMessage())
                );
            }
        } catch (Exception e) {
            System.err.println("Error applying role rewards: " + e.getMessage());
        }
    }

    /**
     * Build level-up message for a member (generic version)
     */
    private String buildLevelUpMessageForMember(DatabaseHandler.LevelSettingsData settings, Member member, int newLevel) {
        String message = settings.levelupMessages;
        String guildId = member.getGuild().getId();

        if (message == null || message.isEmpty()) {
            message = t(guildId, "leveling_.level_up_message");
        }

        DatabaseHandler.UserLevelData userData = handler.getUserLevel(guildId, member.getId());

        message = message
                .replace("{mention}", member.getAsMention())
                .replace("{username}", member.getEffectiveName())
                .replace("{user}", member.getEffectiveName())
                .replace("{level}", String.valueOf(newLevel))
                .replace("{xp}", String.valueOf(userData.totalXp));

        return message;
    }

    /**
     * Send level-up notification for reaction events
     */
    private void sendLevelUpNotificationForReaction(MessageReactionAddEvent event,
                                                     DatabaseHandler.LevelSettingsData settings,
                                                     Member member, String message, int newLevel) {
        // Send DM if enabled
        if (settings.levelupDm) {
            member.getUser().openPrivateChannel().queue(
                    dm -> dm.sendMessage(message).queue(success -> {}, error -> {}),
                    error -> {}
            );
        }

        String channelSetting = settings.levelupChannelId;
        if (channelSetting == null || channelSetting.equals("0") || channelSetting.isEmpty()) {
            return;
        }

        TextChannel targetChannel = null;

        if (channelSetting.equals("current")) {
            if (event.getChannel() instanceof TextChannel) {
                targetChannel = (TextChannel) event.getChannel();
            }
        } else {
            targetChannel = event.getGuild().getTextChannelById(channelSetting);
        }

        if (targetChannel != null) {
            DatabaseHandler.UserLevelData userData = handler.getUserLevel(member.getGuild().getId(), member.getId());
            long xpForNext = userData.getXpForNextLevel();
            double progress = userData.getProgressPercent();
            String progressBar = buildProgressBar(progress);
            String guildId = member.getGuild().getId();

            Container levelUpContainer = Container.of(
                    TextDisplay.of("# " + t(guildId, "leveling_.level_up")),
                    Separator.createDivider(Separator.Spacing.SMALL),
                    TextDisplay.of(message),
                    Separator.createDivider(Separator.Spacing.SMALL),
                    TextDisplay.of(String.format(
                            "**%s %d** → **%s %d**\n%s\n`%d / %d XP` (%.1f%%)",
                            t(guildId, "leveling_.level"), newLevel - 1,
                            t(guildId, "leveling_.level"), newLevel,
                            progressBar, userData.xp, xpForNext, progress
                    ))
            ).withAccentColor(0x5865F2);

            MessageCreateBuilder messageBuilder = new MessageCreateBuilder().setComponents(levelUpContainer);
            targetChannel.sendMessage(messageBuilder.useComponentsV2().build()).queue(
                    success -> {},
                    error -> System.err.println("Failed to send level-up message: " + error.getMessage())
            );
        }
    }

    // ==================== HELPER METHODS ====================

    /**
     * Check if a channel is in the ignored list
     */
    private boolean isChannelIgnored(DatabaseHandler.LevelSettingsData settings, String channelId) {
        if (settings.ignoredChannels == null || settings.ignoredChannels.isEmpty()) {
            return false;
        }
        String[] ignoredChannels = settings.ignoredChannels.split(",");
        for (String ignored : ignoredChannels) {
            if (ignored.trim().equals(channelId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a member has an ignored role
     */
    private boolean hasIgnoredRole(DatabaseHandler.LevelSettingsData settings, Member member) {
        if (settings.ignoredRoles == null || settings.ignoredRoles.isEmpty()) {
            return false;
        }
        String[] ignoredRoles = settings.ignoredRoles.split(",");
        for (Role role : member.getRoles()) {
            for (String ignored : ignoredRoles) {
                if (ignored.trim().equals(role.getId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Handle XP gain for leveling system
     */
    private void handleLevelingXp(MessageReceivedEvent event, String guildId, String userId, String channelId) {
        // Get leveling settings
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        // Check if leveling is enabled
        if (!settings.enabled || !settings.messageXpEnabled) {
            return;
        }

        // Check if channel is ignored
        if (isChannelIgnored(settings, channelId)) {
            return;
        }

        // Check if user has an ignored role
        Member member = event.getMember();
        if (member != null && hasIgnoredRole(settings, member)) {
            return;
        }

        // Check minimum message length
        /*String messageContent = event.getMessage().getContentRaw();
        if (messageContent.length() < settings.minMessageLength) {
            return;
        }*/ //not possible due to missing intent

        // Check cooldown
        if (handler.isUserOnXpCooldown(guildId, userId, settings.cooldownSeconds)) {
            return;
        }

        // Check max level
        if (settings.maxLevel > 0) {
            DatabaseHandler.UserLevelData userData = handler.getUserLevel(guildId, userId);
            if (userData.level >= settings.maxLevel) {
                return; // User is at max level
            }
        }

        // Calculate XP based on mode
        int xpAmount;
        if (settings.messageXpMode.equals("fixed")) {
            xpAmount = settings.xpMax; // Fixed mode uses max value
        } else {
            // Random mode
            xpAmount = settings.xpMin;
            if (settings.xpMax > settings.xpMin) {
                xpAmount = random.nextInt(settings.xpMax - settings.xpMin + 1) + settings.xpMin;
            }
        }

        // Apply multiplier
        xpAmount = (int) Math.round(xpAmount * settings.xpMultiplier);

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

        LevelUpEvent levelUpEvent = new LevelUpEvent(event.getJDA(), event.getGuild(), member, newLevel,
                newLevel - 1);

        event.getJDA().getEventManager().handle(levelUpEvent);
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
        String guildId = member.getGuild().getId();

        // Default message if none set
        if (message == null || message.isEmpty()) {
            message = t(guildId, "leveling_.level_up_message");
        }

        // Get user's total XP
        DatabaseHandler.UserLevelData userData = handler.getUserLevel(guildId, member.getId());

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
            String levelGuildId = member.getGuild().getId();

            // Build progress bar
            String progressBar = buildProgressBar(progress);

            Container levelUpContainer = Container.of(
                    TextDisplay.of("# " + t(levelGuildId, "leveling_.level_up")),
                    Separator.createDivider(Separator.Spacing.SMALL),
                    TextDisplay.of(message),
                    Separator.createDivider(Separator.Spacing.SMALL),
                    TextDisplay.of(String.format(
                            "**%s %d** → **%s %d**\n" +
                            "%s\n" +
                            "`%d / %d XP` (%.1f%%)",
                            t(levelGuildId, "leveling_.level"), newLevel,
                            t(levelGuildId, "leveling_.level"), newLevel + 1,
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
            case "rewards" -> handleRewards(event);
            case "sync-role-levels" -> applyLevelToMembersWithRole(event, null, event.getGuild().getId());
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("level_reward_remove_select")) return;

        String guildId = event.getGuild().getId();
        String roleIdToRemove = event.getValues().get(0);

        // Permission check
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need **Manage Server** permission to change these settings.").setEphemeral(true).queue();
            return;
        }

        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);
        if (settings.rewards == null || settings.rewards.isEmpty()) {
            event.reply("❌ No rewards found to remove.").setEphemeral(true).queue();
            return;
        }

        try {
            JSONArray rewards = new JSONArray(settings.rewards);
            JSONArray newRewards = new JSONArray();
            boolean found = false;

            for (int i = 0; i < rewards.length(); i++) {
                JSONObject reward = rewards.getJSONObject(i);
                // Keep the reward if the role ID doesn't match the one we want to remove
                if (!reward.getString("role_id").equals(roleIdToRemove)) {
                    newRewards.put(reward);
                } else {
                    found = true;
                }
            }

            if (found) {
                boolean success = handler.updateLevelSetting(guildId, "rewards", newRewards.toString());
                if (success) {
                    // Refresh the view
                    Container container = showRewardEditPage(event.getGuild());
                    event.editMessage(new MessageEditBuilder().setComponents(container).useComponentsV2().build()).queue();
                    event.getHook().sendMessage("✅ Reward removed successfully.").setEphemeral(true).queue();
                } else {
                    event.reply("❌ Failed to update database.").setEphemeral(true).queue();
                }
            } else {
                event.reply("❌ Reward not found in settings.").setEphemeral(true).queue();
            }

        } catch (Exception e) {
            event.reply("❌ Error processing removal: " + e.getMessage()).setEphemeral(true).queue();
            e.printStackTrace();
        }
    }

    private void handleRewards(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        // Permission check
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(t(guildId, "general.permission_denied")).setEphemeral(true).queue();
            return;
        }

        Container container = showRewardEditPage(event.getGuild());
        event.replyComponents(container).useComponentsV2().queue();
    }

    private Container showRewardEditPage(net.dv8tion.jda.api.entities.Guild guild) {
        String guildId = guild.getId();
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        // We use a list to build components dynamically
        TextDisplay title = TextDisplay.of(t(guildId, "leveling_.rewards_title"));
        Separator sep1 = Separator.createDivider(Separator.Spacing.SMALL);
        TextDisplay rewardsDesc = TextDisplay.of(t(guildId, "leveling_.rewards_desc"));
        Separator sep2 = Separator.createDivider(Separator.Spacing.SMALL);
        ActionRow ac1 = ActionRow.of(
                EntitySelectMenu.create("help.reward_role_select", EntitySelectMenu.SelectTarget.ROLE)
                        .setPlaceholder(t(guildId, "leveling_select_roles_placeholder"))
                        .setMinValues(1)
                        .setMaxValues(1)
                        .build());
        Separator optSep1 = null;
        TextDisplay optTxt1 = null;
        ActionRow optAc1 = null;
        if (settings.rewards != null && !settings.rewards.isEmpty() && !settings.rewards.equals("[]")) {
            try {
                JSONArray rewards = new JSONArray(settings.rewards);
                if (rewards.length() > 0) {
                    optSep1 = Separator.createDivider(Separator.Spacing.SMALL);
                    optTxt1 = TextDisplay.of(t(guildId, "leveling_.remove_reward_title", "Remove Reward"));

                    StringSelectMenu.Builder removeMenu = StringSelectMenu.create("level_reward_remove_select")
                            .setPlaceholder(t(guildId, "leveling_.remove_reward_placeholder", "Select a reward to remove"));

                    for (int i = 0; i < rewards.length(); i++) {
                        JSONObject reward = rewards.getJSONObject(i);
                        String roleId = reward.getString("role_id");
                        int level = reward.getInt("level");

                        Role role = guild.getRoleById(roleId);
                        String roleName = (role != null) ? role.getName() : "Unknown Role (" + roleId + ")";

                        // Nutze roleId als Value, um sie später identifizieren zu können
                        removeMenu.addOption("Level " + level + ": " + roleName, roleId, "Role ID: " + roleId);
                    }
                    optAc1 = ActionRow.of(removeMenu.build());
                }
            } catch (Exception e) {
                System.err.println("Error parsing rewards for edit page: " + e.getMessage());
            }
        }
        Container container = Container.of(title, sep1, rewardsDesc, sep2, ac1);
        if (optSep1 != null && optTxt1 != null && optAc1 != null) {
            container = Container.of(title, sep1, rewardsDesc, sep2, ac1, optSep1, optTxt1, optAc1);
        }
        return container;
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String componentId = event.getComponentId();
        if (componentId.equals("help.reward_role_select")) {
            String guildId = event.getGuild().getId();
            TextInput roleIdInput = TextInput.create("reward_role_id_input", TextInputStyle.SHORT)
                    .setValue(event.getValues().get(0).getId())
                    .setRequired(true)
                    .build();
            TextInput level = TextInput.create("reward_level_input", TextInputStyle.SHORT)
                    .setPlaceholder(t(guildId, "leveling_.reward_level_input_placeholder"))
                    .setRequired(true)
                    .build();
            Modal modal = Modal.create("level_modal_add_reward", t(guildId, "leveling_.add_reward_modal_title"))
                    .addComponents(
                            Label.of(t(guildId, "leveling_.add_reward_modal_label"),
                            roleIdInput),
                            Label.of(t(guildId, "leveling_.add_reward_level_modal_label"),
                            level)
                    )
                    .build();
            event.replyModal(modal).queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("level_")) return;
        String guildId = event.getGuild().getId();

        // Permission check for settings buttons
        if (componentId.startsWith("level_toggle_") || componentId.startsWith("level_settings_") || componentId.startsWith("level_set_")) {
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
        // Handle set buttons (direct value changes)
        if (componentId.startsWith("level_set_")) {
            handleSetSetting(event, guildId, componentId);
            return;
        }
        System.out.println("Button clicked: " + componentId);
        // Handle leaderboard pagination
        if (componentId.startsWith("level_leaderboard_")) {
            handleLeaderboardPagination(event, guildId, componentId);
            return;
        }
        System.out.println("Button clicked: " + componentId);
        // Handle navigation/action buttons
        switch (componentId) {
            // Navigation
            case "level_settings_formula" -> showFormulaSettingsPage(event, guildId);
            case "level_settings_message_xp" -> showMessageXpSettingsPage(event, guildId);
            case "level_settings_voice_xp" -> showVoiceXpSettingsPage(event, guildId);
            case "level_settings_reaction_xp" -> showReactionXpSettingsPage(event, guildId);
            case "level_settings_xp" -> showMessageXpSettingsPage(event, guildId); // Legacy support
            case "level_settings_notifications" -> showNotificationSettingsPage(event, guildId);
            case "level_settings_rewards" -> showRewardSettingsPage(event, guildId);
            case "level_settings_exceptions" -> showExceptionsSettingsPage(event, guildId);
            case "level_settings_back" -> showMainSettingsPage(event, guildId);
            case "level_settings_refresh" -> showMainSettingsPage(event, guildId);

            // Formula modals
            case "level_settings_multiplier_change" -> showMultiplierModal(event, guildId);
            case "level_settings_max_level_change" -> showMaxLevelModal(event, guildId);

            // Message XP modals
            case "level_settings_xp_min_max_change" -> showXpMinMaxModal(event, guildId);
            case "level_settings_xp_cooldown_change" -> showCooldownModal(event, guildId);
            case "level_settings_min_message_length_change" -> showMinMessageLengthModal(event, guildId);

            // Voice XP modals
            case "level_settings_voice_xp_min_max_change" -> showVoiceXpMinMaxModal(event, guildId);
            case "level_settings_voice_xp_cooldown_change" -> showVoiceXpCooldownModal(event, guildId);
            case "level_settings_voice_min_members_change" -> showVoiceMinMembersModal(event, guildId);
            case "level_settings_voice_xp_amount_change" -> showVoiceXpAmountModal(event, guildId); // Legacy

            // Reaction XP modals
            case "level_settings_reaction_xp_min_max_change" -> showReactionXpMinMaxModal(event, guildId);
            case "level_settings_reaction_xp_cooldown_change" -> showReactionXpCooldownModal(event, guildId);

            // Notification modals
            case "level_settings_levelup_channel_change" -> showLevelUpChannelModal(event, guildId);
            case "level_settings_levelup_message_change" -> showLevelUpMessageModal(event, guildId);

            // Exception modals
            case "level_settings_ignored_channels_change" -> showIgnoredChannelsModal(event, guildId);
            case "level_settings_ignored_roles_change" -> showIgnoredRolesModal(event, guildId);
            case "level_apply_level_to_members_with_role" -> applyLevelToMembersWithRole(null, event, guildId);
        }
    }

    private void applyLevelToMembersWithRole(SlashCommandInteractionEvent sEvent, ButtonInteractionEvent bEvent, String guildId) {
        // 1. Das aktive Event ermitteln (IReplyCallback ist das gemeinsame Interface)
        IReplyCallback event = (sEvent != null) ? sEvent : bEvent;

        if (event == null) {
            System.err.println("Error: Both events are null.");
            return;
        }

        // Ab hier nutzen wir nur noch die Variable 'event'
        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(t(guildId, "general.permission_denied")).setEphemeral(true).queue();
            return;
        }

        event.deferReply().setEphemeral(true).queue();

        try {
            List<Member> members = event.getGuild().getMembers();
            DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

            // --- Optimierung: JSON vorher parsen (nur 1x statt 500x) ---
            JSONArray rewardsArray = null;
            if (settings.rewards != null && !settings.rewards.isEmpty()) {
                try {
                    rewardsArray = new JSONArray(settings.rewards);
                } catch (Exception e) {
                    System.err.println("Error parsing rewards JSON: " + e.getMessage());
                }
            }
            // ------------------------------------------------------------

            int updatedCount = 0;

            for (Member member : members) {
                if (member.getUser().isBot()) continue;

                // Check max level
                if (settings.maxLevel > 0) {
                    DatabaseHandler.UserLevelData userData = handler.getUserLevel(guildId, member.getId());
                    if (userData.level >= settings.maxLevel) {
                        continue;
                    }
                }

                // Check Rewards
                if (rewardsArray != null) {
                    for (int i = 0; i < rewardsArray.length(); i++) {
                        JSONObject reward = rewardsArray.getJSONObject(i);
                        // Sicherstellen, dass role_id existiert
                        String roleId = reward.optString("role_id", null);
                        if (roleId == null) continue;

                        Role role = event.getGuild().getRoleById(roleId);

                        // Hat der Member die Rolle?
                        if (role != null && member.getRoles().contains(role)) {

                            // Alle Rollen-IDs des Members sammeln für deine Helper-Funktion
                            List<String> memberRoleIds = new ArrayList<>();
                            for (Role r : member.getRoles()) {
                                memberRoleIds.add(r.getId());
                            }

                            // Deine Logik zum Level-Setzen
                            int targetLevel = handler.getHighestRoleRewardLevel(guildId, member.getId(), memberRoleIds);

                            // Nur Logik ausführen, wenn targetLevel > 0 (Sicherheitshalber)
                            if (targetLevel > 0) {
                                System.out.println("Applying level " + targetLevel + " to member " + member.getEffectiveName());

                                // XP zurücksetzen und neu setzen basierend auf dem Level
                                handler.setUserXp(guildId, member.getId(), 0);
                                handler.addUserXp(guildId, member.getId(), DatabaseHandler.UserLevelData.getTotalXpForLevel(targetLevel));

                                // Level in DB final speichern
                                DatabaseHandler.UserLevelData userData = handler.getUserLevel(guildId, member.getId());
                                int recalculatedLevel = handler.calculateLevelFromXp(guildId, userData.totalXp);

                                if (recalculatedLevel != userData.level) {
                                    handler.setUserLevel(guildId, member.getId(), recalculatedLevel);
                                    updatedCount++;
                                }
                            }
                            // Break, da wir den Member bearbeitet haben (verhindert doppelte Bearbeitung bei mehreren Reward-Rollen)
                            break;
                        }
                    }
                }
            }

            event.getHook().sendMessage("✅ " + t(guildId, "leveling_.applied_levels_to_members_with_role_success") + " (User updated: " + updatedCount + ")").setEphemeral(true).queue();

        } catch (Exception e) {
            e.printStackTrace();
            event.getHook().sendMessage("❌ Error: " + e.getMessage()).setEphemeral(true).queue();
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
            // Formula modals
            case "level_modal_multiplier" -> handleMultiplierModal(event, guildId);
            case "level_modal_max_level" -> handleMaxLevelModal(event, guildId);

            // Message XP modals
            case "level_modal_xp_min_max" -> handleXpMinMaxModal(event, guildId);
            case "level_modal_cooldown" -> handleCooldownModal(event, guildId);
            case "level_modal_min_message_length" -> handleMinMessageLengthModal(event, guildId);

            // Voice XP modals
            case "level_modal_voice_xp_min_max" -> handleVoiceXpMinMaxModal(event, guildId);
            case "level_modal_voice_xp_cooldown" -> handleVoiceXpCooldownModal(event, guildId);
            case "level_modal_voice_min_members" -> handleVoiceMinMembersModal(event, guildId);
            case "level_modal_voice_xp_amount" -> handleVoiceXpAmountModal(event, guildId); // Legacy

            // Reaction XP modals
            case "level_modal_reaction_xp_min_max" -> handleReactionXpMinMaxModal(event, guildId);
            case "level_modal_reaction_xp_cooldown" -> handleReactionXpCooldownModal(event, guildId);

            // Notification modals
            case "level_modal_levelup_channel" -> handleLevelUpChannelModal(event, guildId);
            case "level_modal_levelup_message" -> handleLevelUpMessageModal(event, guildId);

            // Exception modals
            case "level_modal_ignored_channels" -> handleIgnoredChannelsModal(event, guildId);
            case "level_modal_ignored_roles" -> handleIgnoredRolesModal(event, guildId);
            case "level_modal_add_reward" -> handleAddRewardModal(event, guildId);
        }
    }

    private void handleAddRewardModal(ModalInteractionEvent event, String guildId) {
        String roleId = event.getValue("reward_role_id_input").getAsString();
        String levelStr = event.getValue("reward_level_input").getAsString();

        int level;
        try {
            level = Integer.parseInt(levelStr);
            if (level <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            event.reply("❌ " + t(guildId, "leveling_.invalid_level")).setEphemeral(true).queue();
            return;
        }



        // Add reward to database
        DatabaseHandler.LevelSettingsData data = handler.getLevelSettings(guildId);
        JSONArray rewards = new JSONArray();
        if (data.rewards != null && !data.rewards.isEmpty()) {
            rewards = new JSONArray(data.rewards);
        }
        JSONObject newReward = new JSONObject();
        newReward.put("level", level);
        newReward.put("role_id", roleId);
        rewards.put(newReward);
        boolean success = handler.updateLevelSetting(guildId, "rewards", rewards.toString());
        if (success) {
            event.reply("✅ " + t(guildId, "leveling_.reward_added_success")).setComponents(ActionRow.of(Button.primary("level_apply_level_to_members_with_role", t(guildId, "leveling_.apply_level_to_members_with_role")))).setEphemeral(true).queue();
        } else {
            event.reply("❌ " + t(guildId, "leveling_.reward_added_failure")).setEphemeral(true).queue();
        }
    }

    public void handleRank(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        // Get target user (self or mentioned user)
        Member targetMember = event.getOption("user") != null
                ? event.getOption("user").getAsMember()
                : event.getMember();

        if (targetMember == null) {
            event.reply(t(guildId, "general.not_found")).setEphemeral(true).queue();
            return;
        }

        String oderId = targetMember.getId();

        // Get user level data
        DatabaseHandler.UserLevelData userData = handler.getUserLevel(guildId, oderId);
        int rank = handler.getUserRank(guildId, oderId);
        int totalUsers = handler.getTotalLeveledUsers(guildId);

        // Calculate progress
        long xpForNext = userData.getXpForNextLevel();
        double progress = userData.getProgressPercent();
        String progressBar = buildProgressBar(progress);

        // Build rank card container
        Container rankContainer = Container.of(
                TextDisplay.of(String.format("# 📊 %s's %s", targetMember.getEffectiveName(), t(guildId, "leveling_.rank"))),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(String.format(
                        "**🏆 %s:** #%d / %d\n" +
                        "**⭐ %s:** %d\n" +
                        "**✨ %s:** %,d",
                        t(guildId, "leveling_.rank"), rank, totalUsers,
                        t(guildId, "leveling_.level"), userData.level,
                        t(guildId, "leveling_.total_xp"), userData.totalXp
                )),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("## " + (t(guildId, "leveling_.level").equals("Level") ? "Progress to Next Level" : "Fortschritt zum nächsten Level")),
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
            String noDataMsg = t(guildId, "leveling_.level").equals("Level")
                    ? "📭 No leveling data yet! Start chatting to earn XP."
                    : "📭 Noch keine Level-Daten! Beginne zu chatten um XP zu verdienen.";
            event.reply(noDataMsg).setEphemeral(true).queue();
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
                    "-# %s %d • %,d XP\n\n",
                    medal, displayName,
                    t(guildId, "leveling_.level"), userData.level, userData.totalXp
            ));
            rank++;
        }

        // Get caller's rank
        String callerId = event.getUser().getId();
        int callerRank = handler.getUserRank(guildId, callerId);
        DatabaseHandler.UserLevelData callerData = handler.getUserLevel(guildId, callerId);

        // Build leaderboard container
        Container leaderboardContainer = Container.of(
                TextDisplay.of(String.format("# " + t(guildId, "leveling_.leaderboard_title"), event.getGuild().getName())),
                TextDisplay.of(String.format("-# " + t(guildId, "leveling_.page") + " • " + t(guildId, "leveling_.total_members"),
                        page, Math.max(1, totalPages), totalUsers)),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(entries.toString().trim()),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(String.format(
                        "**%s:** #%d (%s %d, %,d XP)",
                        t(guildId, "leveling_.your_rank"), callerRank,
                        t(guildId, "leveling_.level"), callerData.level, callerData.totalXp
                )),

                Separator.createDivider(Separator.Spacing.SMALL),

                // Pagination buttons
                ActionRow.of(
                        Button.secondary("level_leaderboard_prev_" + page, "⬅️")
                                .withDisabled(page <= 1),
                        Button.secondary("level_leaderboard_refresh_" + page, t(guildId, "buttons.refresh")),
                        Button.secondary("level_leaderboard_next_" + page, "➡️")
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
                TextDisplay.of(String.format("# " + t(guildId, "leveling_.leaderboard_title"), event.getGuild().getName())),
                TextDisplay.of(String.format("-# " + t(guildId, "leveling_.page") + " • " + t(guildId, "leveling_.total_members"), newPage, Math.max(1, totalPages), totalUsers)),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(entries.toString().trim()),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(String.format(
                        "**%s:** #%d (%s %d, %,d XP)",
                        t(guildId, "leveling_.your_rank"), callerRank,
                        t(guildId, "leveling_.level"), callerData.level, callerData.totalXp
                )),

                Separator.createDivider(Separator.Spacing.SMALL),

                ActionRow.of(
                        Button.secondary("level_leaderboard_prev_" + newPage, t(guildId, "buttons.previous_page"))
                                .withDisabled(newPage <= 1),
                        Button.secondary("level_leaderboard_refresh_" + newPage, t(guildId, "buttons.refresh")),
                        Button.secondary("level_leaderboard_next_" + newPage, t(guildId, "buttons.next_page"))
                                .withDisabled(newPage >= totalPages)
                )
        ).withAccentColor(0xFEE75C);

        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(leaderboardContainer);

        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    public void handleSettings(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply(t(event.getGuild().getId(), "general.permission_denied")).setEphemeral(true).queue();
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

        String enabledText = settings.enabled ? t(guildId, "general.enabled") : t(guildId, "general.disabled");
        String enabledStatus = settings.enabled ? "✅ " + enabledText : "❌ " + enabledText;
        String messageXpStatus = settings.messageXpEnabled ? "✅" : "❌";
        String voiceXpStatus = settings.voiceXpEnabled ? "✅" : "❌";
        String reactionXpStatus = settings.reactionXpEnabled ? "✅" : "❌";
        String stackRewardsStatus = settings.stackRewards ? "✅ " + t(guildId, "leveling_.stack_roles") : "🔄 " + t(guildId, "leveling_.replace_roles");
        String resetOnLeaveStatus = settings.resetOnLeave ? "✅ " + t(guildId, "leveling_.reset_xp") : "💾 " + t(guildId, "leveling_.keep_xp");
        String maxLevelText = settings.maxLevel == 0 ? t(guildId, "leveling_.unlimited") : String.valueOf(settings.maxLevel);
        String curveText = settings.xpCurve.substring(0, 1).toUpperCase() + settings.xpCurve.substring(1);

        return Container.of(
                // Header
                TextDisplay.of("# " + t(guildId, "leveling_.settings_title")),
                TextDisplay.of(t(guildId, "leveling_.settings_description")),

                Separator.createDivider(Separator.Spacing.LARGE),

                // Status Overview
                TextDisplay.of("## " + t(guildId, "leveling_.current_status")),
                TextDisplay.of(String.format(
                        "**%s:** %s\n" +
                        "**%s:** %s × %.2f | Max Level: %s\n" +
                        "**%s:** %s | **%s:** %s | **%s:** %s",
                        t(guildId, "leveling_.system"), enabledStatus,
                        t(guildId, "leveling_.formula"), curveText, settings.xpMultiplier, maxLevelText,
                        t(guildId, "leveling_.message_xp"), messageXpStatus,
                        t(guildId, "leveling_.voice_xp"), voiceXpStatus,
                        t(guildId, "leveling_.reaction_xp"), reactionXpStatus
                )),

                Separator.createDivider(Separator.Spacing.LARGE),

                // Quick Toggles
                TextDisplay.of("## " + t(guildId, "leveling_.quick_toggles")),
                ActionRow.of(
                        Button.of(settings.enabled ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.DANGER,
                                "level_toggle_enabled", settings.enabled ? "✅ " + t(guildId, "general.on") : "❌ " + t(guildId, "general.off")),
                        Button.of(settings.messageXpEnabled ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_message_xp_enabled", "💬 " + t(guildId, "leveling_.message_xp")),
                        Button.of(settings.voiceXpEnabled ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_voice_xp_enabled", "🎤 " + t(guildId, "leveling_.voice_xp")),
                        Button.of(settings.reactionXpEnabled ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_reaction_xp_enabled", "👍 " + t(guildId, "leveling_.reaction_xp"))
                ),
                ActionRow.of(
                        Button.of(settings.levelupDm ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_levelup_dm", "📬 " + t(guildId, "leveling_.dm_notifications")),
                        Button.of(settings.stackRewards ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_stack_rewards", stackRewardsStatus),
                        Button.of(settings.resetOnLeave ? net.dv8tion.jda.api.components.buttons.ButtonStyle.DANGER : net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS,
                                "level_toggle_reset_on_leave", resetOnLeaveStatus)
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                // Navigation Buttons
                TextDisplay.of("## " + t(guildId, "leveling_.detailed_settings")),
                ActionRow.of(
                        Button.primary("level_settings_formula", "📐 " + t(guildId, "leveling_.formula")),
                        Button.primary("level_settings_message_xp", "💬 " + t(guildId, "leveling_.message_xp")),
                        Button.primary("level_settings_voice_xp", "🎤 " + t(guildId, "leveling_.voice_xp")),
                        Button.primary("level_settings_reaction_xp", "👍 " + t(guildId, "leveling_.reaction_xp"))
                ),
                ActionRow.of(
                        Button.primary("level_settings_notifications", "🔔 " + t(guildId, "leveling_.notifications")),
                        Button.primary("level_settings_rewards", "🎁 " + t(guildId, "leveling_.role_rewards")),
                        Button.primary("level_settings_exceptions", "🚫 " + t(guildId, "leveling_.exceptions"))
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                // Footer - show different footer text based on language
                TextDisplay.of("-# " + getFooterHint(guildId))
        ).withAccentColor(settings.enabled ? 0x57F287 : 0xED4245); // Green if enabled, red if disabled
    }

    private String getFooterHint(String guildId) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null && lang.getGuildLanguage(guildId).equals("de")) {
            return "Klicke auf die Buttons oben, um Einstellungen zu ändern oder zu detaillierten Konfigurationsseiten zu navigieren.";
        }
        return "Click buttons above to toggle settings or navigate to detailed configuration pages.";
    }

    // ==================== FORMULA SETTINGS PAGE ====================

    private Container buildFormulaSettingsContainer(String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        String curveText = settings.xpCurve.substring(0, 1).toUpperCase() + settings.xpCurve.substring(1);
        String maxLevelText = settings.maxLevel == 0 ? "Unlimited" : String.valueOf(settings.maxLevel);

        return Container.of(
                TextDisplay.of("# 📐 Formula Settings"),
                TextDisplay.of("Configure how XP requirements scale per level."),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 📈 " + t(guildId, "leveling_.xp_curve")),
                TextDisplay.of(String.format(
                        "**%s:** %s\n\n" +
                        "• **%s** - %s\n" +
                        "• **%s** - %s\n" +
                        "• **%s** - %s",
                        t(guildId, "leveling_.xp_curve"), curveText,
                        t(guildId, "leveling_.curve_linear"), t(guildId, "leveling_.curve_linear_desc"),
                        t(guildId, "leveling_.curve_exponential"), t(guildId, "leveling_.curve_exponential_desc"),
                        t(guildId, "leveling_.curve_logarithmic"), t(guildId, "leveling_.curve_logarithmic_desc")
                )),

                ActionRow.of(
                        Button.of(settings.xpCurve.equals("linear") ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_set_curve_linear", t(guildId, "leveling_.curve_linear")),
                        Button.of(settings.xpCurve.equals("exponential") ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_set_curve_exponential", t(guildId, "leveling_.curve_exponential")),
                        Button.of(settings.xpCurve.equals("logarithmic") ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_set_curve_logarithmic", t(guildId, "leveling_.curve_logarithmic"))
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## ✖️ " + t(guildId, "leveling_.multiplier")),
                TextDisplay.of(String.format(
                        "**%s:** %.2fx\n\n" +
                        "-# %s",
                        t(guildId, "leveling_.multiplier"), settings.xpMultiplier,
                        t(guildId, "leveling_.multiplier_desc")
                )),

                ActionRow.of(Button.primary("level_settings_multiplier_change", "✏️ " + t(guildId, "general.edit"))),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 🎯 " + t(guildId, "leveling_.max_level")),
                TextDisplay.of(String.format(
                        "**%s:** %s\n\n" +
                        "-# %s",
                        t(guildId, "leveling_.max_level"), maxLevelText,
                        t(guildId, "leveling_.max_level_desc")
                )),

                ActionRow.of(Button.primary("level_settings_max_level_change", "✏️ " + t(guildId, "general.edit"))),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("level_settings_back", t(guildId, "buttons.back_to_overview")),
                        Button.secondary("level_settings_refresh", t(guildId, "buttons.refresh"))
                )
        ).withAccentColor(0x9B59B6); // Purple
    }

    // ==================== MESSAGE XP SETTINGS PAGE ====================

    private Container buildMessageXpSettingsContainer(String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        String modeText = settings.messageXpMode.equals("random") ? t(guildId, "leveling_.mode_random") : t(guildId, "leveling_.mode_fixed");
        String statusText = settings.messageXpEnabled ? "✅ " + t(guildId, "general.enabled") : "❌ " + t(guildId, "general.disabled");

        return Container.of(
                TextDisplay.of("# " + t(guildId, "leveling_.message_xp_title")),
                TextDisplay.of(t(guildId, "leveling_.message_xp_description")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## " + t(guildId, "leveling_.status_mode")),
                TextDisplay.of(String.format(
                        "**Status:** %s\n" +
                        "**%s:** %s\n\n" +
                        "• **%s** - %s\n" +
                        "• **%s** - %s",
                        statusText,
                        t(guildId, "leveling_.mode"), modeText,
                        t(guildId, "leveling_.mode_random"), t(guildId, "leveling_.mode_random_desc"),
                        t(guildId, "leveling_.mode_fixed"), t(guildId, "leveling_.mode_fixed_desc")
                )),

                ActionRow.of(
                        Button.of(settings.messageXpEnabled ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.DANGER,
                                "level_toggle_message_xp_enabled", statusText),
                        Button.of(settings.messageXpMode.equals("random") ? net.dv8tion.jda.api.components.buttons.ButtonStyle.PRIMARY : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_message_xp_mode", t(guildId, "leveling_.mode") + ": " + modeText)
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 📊 XP Values"),
                TextDisplay.of(String.format(
                        "**Min XP:** %d\n" +
                        "**Max XP:** %d\n" +
                        "**Cooldown:** %d seconds\n",
                        settings.xpMin, settings.xpMax, settings.cooldownSeconds
                )),

                ActionRow.of(
                        Button.primary("level_settings_xp_min_max_change", "✏️ Min/Max XP"),
                        Button.primary("level_settings_xp_cooldown_change", "✏️ Cooldown")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("level_settings_back", "⬅️ Back to Overview"),
                        Button.secondary("level_settings_refresh", "🔄 Refresh")
                )
        ).withAccentColor(settings.messageXpEnabled ? 0x3498DB : 0x95A5A6); // Blue if enabled, gray if disabled
    }

    // ==================== VOICE XP SETTINGS PAGE ====================

    private Container buildVoiceXpSettingsContainer(String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        return Container.of(
                TextDisplay.of("# 🎤 Voice XP"),
                TextDisplay.of("Configure XP earned from voice channels."),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 🔘 Status"),
                TextDisplay.of(String.format(
                        "**Status:** %s\n",
                        settings.voiceXpEnabled ? "✅ Enabled" : "❌ Disabled"
                )),

                ActionRow.of(
                        Button.of(settings.voiceXpEnabled ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.DANGER,
                                "level_toggle_voice_xp_enabled", settings.voiceXpEnabled ? "✅ Enabled" : "❌ Disabled")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 📊 XP Values"),
                TextDisplay.of(String.format(
                        "**Min XP:** %d\n" +
                        "**Max XP:** %d\n" +
                        "**Cooldown:** %d seconds\n",
                        settings.voiceXpMin, settings.voiceXpMax, settings.voiceXpCooldown
                )),

                ActionRow.of(
                        Button.primary("level_settings_voice_xp_min_max_change", "✏️ Min/Max XP"),
                        Button.primary("level_settings_voice_xp_cooldown_change", "✏️ Cooldown")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## ⚙️ Voice Settings"),
                TextDisplay.of(String.format(
                        "**Minimum Members:** %d\n" +
                        "**Anti-AFK:** %s\n\n" +
                        "-# Minimum members required in the channel.\n" +
                        "-# Anti-AFK requires users to be unmuted.\n",
                        settings.voiceXpMinMembers,
                        settings.voiceXpAntiAfk ? "✅ Enabled" : "❌ Disabled"
                )),

                ActionRow.of(
                        Button.primary("level_settings_voice_min_members_change", "✏️ Min Members"),
                        Button.of(settings.voiceXpAntiAfk ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_voice_xp_anti_afk", settings.voiceXpAntiAfk ? "✅ Anti-AFK ON" : "❌ Anti-AFK OFF")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("level_settings_back", "⬅️ Back to Overview"),
                        Button.secondary("level_settings_refresh", "🔄 Refresh")
                )
        ).withAccentColor(settings.voiceXpEnabled ? 0x9B59B6 : 0x95A5A6); // Purple if enabled, gray if disabled
    }

    // ==================== REACTION XP SETTINGS PAGE ====================

    private Container buildReactionXpSettingsContainer(String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        String awardsText = switch (settings.reactionXpAwards) {
            case "sender" -> "Sender Only";
            case "receiver" -> "Receiver Only";
            default -> "Both";
        };

        return Container.of(
                TextDisplay.of("# 👍 Reaction XP"),
                TextDisplay.of("Configure XP earned from reactions."),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 🔘 Status"),
                TextDisplay.of(String.format(
                        "**Status:** %s\n",
                        settings.reactionXpEnabled ? "✅ Enabled" : "❌ Disabled"
                )),

                ActionRow.of(
                        Button.of(settings.reactionXpEnabled ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.DANGER,
                                "level_toggle_reaction_xp_enabled", settings.reactionXpEnabled ? "✅ Enabled" : "❌ Disabled")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 🎁 Awards"),
                TextDisplay.of(String.format(
                        "**Current:** %s\n\n" +
                        "• **Both** - Sender and receiver get XP\n" +
                        "• **Sender** - Only the reactor gets XP\n" +
                        "• **Receiver** - Only the message author gets XP",
                        awardsText
                )),

                ActionRow.of(
                        Button.of(settings.reactionXpAwards.equals("both") ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_set_reaction_awards_both", "Both"),
                        Button.of(settings.reactionXpAwards.equals("sender") ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_set_reaction_awards_sender", "Sender"),
                        Button.of(settings.reactionXpAwards.equals("receiver") ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_set_reaction_awards_receiver", "Receiver")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## 📊 XP Values"),
                TextDisplay.of(String.format(
                        "**Min XP:** %d\n" +
                        "**Max XP:** %d\n" +
                        "**Cooldown:** %d seconds\n",
                        settings.reactionXpMin, settings.reactionXpMax, settings.reactionXpCooldown
                )),

                ActionRow.of(
                        Button.primary("level_settings_reaction_xp_min_max_change", "✏️ Min/Max XP"),
                        Button.primary("level_settings_reaction_xp_cooldown_change", "✏️ Cooldown")
                ),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("level_settings_back", "⬅️ Back to Overview"),
                        Button.secondary("level_settings_refresh", "🔄 Refresh")
                )
        ).withAccentColor(settings.reactionXpEnabled ? 0xE91E63 : 0x95A5A6); // Pink if enabled, gray if disabled
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
                        settings.stackRewards ? "✅ Stack Roles" : "🔄 Replace Roles",
                        settings.applyRoleRewardsOnAddRoleReward ? "✅ Apply Level on Role Add" : "❌ Dont apply Level on Role Add"
                )),

                ActionRow.of(
                        Button.of(settings.stackRewards ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.PRIMARY,
                                "level_toggle_stack_rewards", settings.stackRewards ? "✅ Stacking ON" : "🔄 Replacing"),
                        Button.of(settings.applyRoleRewardsOnAddRoleReward ? net.dv8tion.jda.api.components.buttons.ButtonStyle.SUCCESS : net.dv8tion.jda.api.components.buttons.ButtonStyle.SECONDARY,
                                "level_toggle_apply_role_reward_on_add", settings.applyRoleRewardsOnAddRoleReward ? "✅ Apply Level on Role add ON" : "❌ Apply Level on Role add OFF")
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
            case "message_xp_enabled" -> "message_xp_enabled";
            case "voice_xp_enabled" -> "voice_xp_enabled";
            case "reaction_xp_enabled" -> "reaction_xp_enabled";
            case "voice_xp_anti_afk" -> "voice_xp_anti_afk";
            case "levelup_dm" -> "levelup_dm";
            case "stack_rewards" -> "stack_rewards";
            case "reset_on_leave" -> "reset_on_leave";
            case "message_xp_mode" -> null; // Special handling
            default -> null;
        };

        // Special handling for message_xp_mode toggle
        if (setting.equals("message_xp_mode")) {
            DatabaseHandler.LevelSettingsData currentSettings = handler.getLevelSettings(guildId);
            String newMode = currentSettings.messageXpMode.equals("random") ? "fixed" : "random";
            handler.updateLevelSetting(guildId, "message_xp_mode", newMode);
        } else if (column != null) {
            handler.toggleLevelSetting(guildId, column);
        } else {
            event.reply("❌ Unknown setting.").setEphemeral(true).queue();
            return;
        }

        // Determine which page to refresh based on which setting was toggled
        Container updatedContainer = switch (setting) {
            case "message_xp_enabled", "message_xp_mode" -> buildMessageXpSettingsContainer(guildId);
            case "voice_xp_enabled", "voice_xp_anti_afk" -> buildVoiceXpSettingsContainer(guildId);
            case "reaction_xp_enabled" -> buildReactionXpSettingsContainer(guildId);
            case "levelup_dm" -> buildNotificationSettingsContainer(guildId);
            case "stack_rewards" -> buildRewardSettingsContainer(guildId);
            case "reset_on_leave" -> buildExceptionsSettingsContainer(guildId);
            default -> buildMainSettingsContainer(guildId);
        };

        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(updatedContainer);

        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private void handleSetSetting(ButtonInteractionEvent event, String guildId, String componentId) {
        // Handle curve settings
        if (componentId.startsWith("level_set_curve_")) {
            String curve = componentId.replace("level_set_curve_", "");
            handler.updateLevelSetting(guildId, "xp_curve", curve);
            Container container = buildFormulaSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();
            return;
        }

        // Handle reaction awards settings
        if (componentId.startsWith("level_set_reaction_awards_")) {
            String awards = componentId.replace("level_set_reaction_awards_", "");
            handler.updateLevelSetting(guildId, "reaction_xp_awards", awards);
            Container container = buildReactionXpSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();
            return;
        }

        event.reply("❌ Unknown setting.").setEphemeral(true).queue();
    }

    private void showMainSettingsPage(ButtonInteractionEvent event, String guildId) {
        Container container = buildMainSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private void showFormulaSettingsPage(ButtonInteractionEvent event, String guildId) {
        Container container = buildFormulaSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private void showMessageXpSettingsPage(ButtonInteractionEvent event, String guildId) {
        Container container = buildMessageXpSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private void showVoiceXpSettingsPage(ButtonInteractionEvent event, String guildId) {
        Container container = buildVoiceXpSettingsContainer(guildId);
        MessageEditBuilder editBuilder = new MessageEditBuilder()
                .setComponents(container);
        event.editMessage(editBuilder.useComponentsV2().build()).queue();
    }

    private void showReactionXpSettingsPage(ButtonInteractionEvent event, String guildId) {
        Container container = buildReactionXpSettingsContainer(guildId);
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

    // Formula Modals
    private void showMultiplierModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        TextInput multiplier = TextInput.create("multiplier", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 1.5")
                .setValue(String.valueOf(settings.xpMultiplier))
                .setRequiredRange(1, 10)
                .build();

        Modal modal = Modal.create("level_modal_multiplier", "✖️ XP Multiplier")
                .addComponents(Label.of("Multiplier (e.g., 1.0, 1.5, 2.0)", multiplier))
                .build();

        event.replyModal(modal).queue();
    }

    private void showMaxLevelModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        TextInput maxLevel = TextInput.create("max_level", TextInputStyle.SHORT)
                .setPlaceholder("0 for unlimited")
                .setValue(String.valueOf(settings.maxLevel))
                .setRequiredRange(1, 5)
                .build();

        Modal modal = Modal.create("level_modal_max_level", "🎯 Max Level")
                .addComponents(Label.of("Maximum Level (0 = unlimited)", maxLevel))
                .build();

        event.replyModal(modal).queue();
    }

    // Message XP Modals
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

        Modal modal = Modal.create("level_modal_xp_min_max", "💬 Message XP Range")
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

        Modal modal = Modal.create("level_modal_cooldown", "⏱️ Message XP Cooldown")
                .addComponents(Label.of("Cooldown (seconds)", cooldown))
                .build();

        event.replyModal(modal).queue();
    }

    // Voice XP Modals
    private void showVoiceXpMinMaxModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        TextInput minXp = TextInput.create("voice_xp_min", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 15")
                .setValue(String.valueOf(settings.voiceXpMin))
                .setRequiredRange(1, 5)
                .build();

        TextInput maxXp = TextInput.create("voice_xp_max", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 40")
                .setValue(String.valueOf(settings.voiceXpMax))
                .setRequiredRange(1, 5)
                .build();

        Modal modal = Modal.create("level_modal_voice_xp_min_max", "🎤 Voice XP Range")
                .addComponents(
                        Label.of("Minimum Voice XP", minXp),
                        Label.of("Maximum Voice XP", maxXp)
                )
                .build();

        event.replyModal(modal).queue();
    }

    private void showVoiceXpCooldownModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        TextInput cooldown = TextInput.create("voice_xp_cooldown", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 180")
                .setValue(String.valueOf(settings.voiceXpCooldown))
                .setRequiredRange(1, 5)
                .build();

        Modal modal = Modal.create("level_modal_voice_xp_cooldown", "⏱️ Voice XP Cooldown")
                .addComponents(Label.of("Cooldown (seconds)", cooldown))
                .build();

        event.replyModal(modal).queue();
    }

    private void showVoiceMinMembersModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        TextInput minMembers = TextInput.create("voice_min_members", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 2")
                .setValue(String.valueOf(settings.voiceXpMinMembers))
                .setRequiredRange(1, 3)
                .build();

        Modal modal = Modal.create("level_modal_voice_min_members", "👥 Minimum Members")
                .addComponents(Label.of("Minimum members in voice channel", minMembers))
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

    // Reaction XP Modals
    private void showReactionXpMinMaxModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        TextInput minXp = TextInput.create("reaction_xp_min", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 5")
                .setValue(String.valueOf(settings.reactionXpMin))
                .setRequiredRange(1, 5)
                .build();

        TextInput maxXp = TextInput.create("reaction_xp_max", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 25")
                .setValue(String.valueOf(settings.reactionXpMax))
                .setRequiredRange(1, 5)
                .build();

        Modal modal = Modal.create("level_modal_reaction_xp_min_max", "👍 Reaction XP Range")
                .addComponents(
                        Label.of("Minimum Reaction XP", minXp),
                        Label.of("Maximum Reaction XP", maxXp)
                )
                .build();

        event.replyModal(modal).queue();
    }

    private void showReactionXpCooldownModal(ButtonInteractionEvent event, String guildId) {
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);

        TextInput cooldown = TextInput.create("reaction_xp_cooldown", TextInputStyle.SHORT)
                .setPlaceholder("e.g., 300")
                .setValue(String.valueOf(settings.reactionXpCooldown))
                .setRequiredRange(1, 5)
                .build();

        Modal modal = Modal.create("level_modal_reaction_xp_cooldown", "⏱️ Reaction XP Cooldown")
                .addComponents(Label.of("Cooldown (seconds)", cooldown))
                .build();

        event.replyModal(modal).queue();
    }

    // Notification Modals
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

    // Formula Modal Handlers
    private void handleMultiplierModal(ModalInteractionEvent event, String guildId) {
        String multiplierStr = Objects.requireNonNull(event.getValue("multiplier")).getAsString().trim();

        try {
            double multiplier = Double.parseDouble(multiplierStr);

            if (multiplier < 0.1) {
                event.reply("❌ Multiplier must be at least 0.1.").setEphemeral(true).queue();
                return;
            }

            if (multiplier > 10.0) {
                event.reply("❌ Multiplier cannot exceed 10.0.").setEphemeral(true).queue();
                return;
            }

            handler.updateLevelSetting(guildId, "xp_multiplier", multiplier);

            Container container = buildFormulaSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter a valid number for multiplier.").setEphemeral(true).queue();
        }
    }

    private void handleMaxLevelModal(ModalInteractionEvent event, String guildId) {
        String maxLevelStr = Objects.requireNonNull(event.getValue("max_level")).getAsString().trim();

        try {
            int maxLevel = Integer.parseInt(maxLevelStr);

            if (maxLevel < 0) {
                event.reply("❌ Max level cannot be negative.").setEphemeral(true).queue();
                return;
            }

            if (maxLevel > 10000) {
                event.reply("❌ Max level cannot exceed 10,000.").setEphemeral(true).queue();
                return;
            }

            handler.updateLevelSetting(guildId, "max_level", maxLevel);

            Container container = buildFormulaSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter a valid number for max level.").setEphemeral(true).queue();
        }
    }

    // Message XP Modal Handlers
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

            Container container = buildMessageXpSettingsContainer(guildId);
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

            Container container = buildMessageXpSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter a valid number for cooldown.").setEphemeral(true).queue();
        }
    }

    // Voice XP Modal Handlers
    private void handleVoiceXpMinMaxModal(ModalInteractionEvent event, String guildId) {
        String minXpStr = Objects.requireNonNull(event.getValue("voice_xp_min")).getAsString().trim();
        String maxXpStr = Objects.requireNonNull(event.getValue("voice_xp_max")).getAsString().trim();

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

            handler.updateLevelSetting(guildId, "voice_xp_min", minXp);
            handler.updateLevelSetting(guildId, "voice_xp_max", maxXp);

            Container container = buildVoiceXpSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter valid numbers for voice XP values.").setEphemeral(true).queue();
        }
    }

    private void handleVoiceXpCooldownModal(ModalInteractionEvent event, String guildId) {
        String cooldownStr = Objects.requireNonNull(event.getValue("voice_xp_cooldown")).getAsString().trim();

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

            handler.updateLevelSetting(guildId, "voice_xp_cooldown", cooldown);

            Container container = buildVoiceXpSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter a valid number for voice XP cooldown.").setEphemeral(true).queue();
        }
    }

    private void handleVoiceMinMembersModal(ModalInteractionEvent event, String guildId) {
        String minMembersStr = Objects.requireNonNull(event.getValue("voice_min_members")).getAsString().trim();

        try {
            int minMembers = Integer.parseInt(minMembersStr);

            if (minMembers < 1) {
                event.reply("❌ Minimum members must be at least 1.").setEphemeral(true).queue();
                return;
            }

            if (minMembers > 100) {
                event.reply("❌ Minimum members cannot exceed 100.").setEphemeral(true).queue();
                return;
            }

            handler.updateLevelSetting(guildId, "voice_xp_min_members", minMembers);

            Container container = buildVoiceXpSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter a valid number for minimum members.").setEphemeral(true).queue();
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

            Container container = buildVoiceXpSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter a valid number for voice XP.").setEphemeral(true).queue();
        }
    }

    // Reaction XP Modal Handlers
    private void handleReactionXpMinMaxModal(ModalInteractionEvent event, String guildId) {
        String minXpStr = Objects.requireNonNull(event.getValue("reaction_xp_min")).getAsString().trim();
        String maxXpStr = Objects.requireNonNull(event.getValue("reaction_xp_max")).getAsString().trim();

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

            handler.updateLevelSetting(guildId, "reaction_xp_min", minXp);
            handler.updateLevelSetting(guildId, "reaction_xp_max", maxXp);

            Container container = buildReactionXpSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter valid numbers for reaction XP values.").setEphemeral(true).queue();
        }
    }

    private void handleReactionXpCooldownModal(ModalInteractionEvent event, String guildId) {
        String cooldownStr = Objects.requireNonNull(event.getValue("reaction_xp_cooldown")).getAsString().trim();

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

            handler.updateLevelSetting(guildId, "reaction_xp_cooldown", cooldown);

            Container container = buildReactionXpSettingsContainer(guildId);
            MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
            event.editMessage(editBuilder.useComponentsV2().build()).queue();

        } catch (NumberFormatException e) {
            event.reply("❌ Please enter a valid number for reaction XP cooldown.").setEphemeral(true).queue();
        }
    }

    // Notification Modal Handlers
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

            Container container = buildMessageXpSettingsContainer(guildId);
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
