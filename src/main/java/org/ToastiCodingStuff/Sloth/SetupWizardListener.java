package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

import java.awt.*;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SetupWizardListener extends ListenerAdapter {
    private final DatabaseHandler handler;
    private final SystemsCommandListener systemsListener;

    // Store temporary panel IDs created during setup
    private final Map<String, Integer> setupPanelIds = new ConcurrentHashMap<>();

    // System keys for easy access
    private static final String[] SYSTEMS = {
            "log-channel", "warn", "ticket", "mod", "stats",
            "verify-button", "select-roles", "temprole", "role-event",
            "embed", "reminders", "leveling"
    };

    public SetupWizardListener(DatabaseHandler handler, SystemsCommandListener systemsListener) {
        this.handler = handler;
        this.systemsListener = systemsListener;
    }

    // ==================== LANGUAGE HELPER METHODS ====================

    private static final int TOTAL_STEPS = 13;

    private String t(String guildId, String key) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            return lang.get(guildId, key);
        }
        return key;
    }

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

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (event.getGuild() == null) return;
        String guildId = event.getGuild().getId();

        if (componentId.equals("setup_start")) {
            if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
                event.reply(t(guildId, "setup_wizard.no_permission")).setEphemeral(true).queue();
                return;
            }
            // Step 1: Language
            event.editMessage(new MessageEditBuilder().setComponents(buildLanguageStep(guildId)).useComponentsV2().build()).queue();
        }
        // Navigation buttons for system steps
        else if (componentId.equals("setup_finish_log")) {
            // After log channel -> Moderation System Step
            event.editMessage(new MessageEditBuilder().setComponents(buildModerationStep(guildId)).useComponentsV2().build()).queue();
        }
        else if (componentId.equals("setup_skip_log")) {
            // Skip log channel -> Moderation System Step
            event.editMessage(new MessageEditBuilder().setComponents(buildModerationStep(guildId)).useComponentsV2().build()).queue();
        }
        // System toggle buttons - show choice dialog when enabling
        else if (componentId.startsWith("setup_toggle:")) {
            String systemName = componentId.split(":")[1];
            boolean currentlyEnabled = isSystemEnabled(guildId, systemName);

            if (!currentlyEnabled) {
                // System is being enabled - show choice dialog if system has config options
                if (hasConfigOptions(systemName)) {
                    event.editMessage(new MessageEditBuilder().setComponents(buildConfigChoiceStep(guildId, systemName)).useComponentsV2().build()).queue();
                    return;
                }
            }

            // Toggle the system
            handler.toggleSystem(guildId, systemName);
            event.editMessage(new MessageEditBuilder().setComponents(getCurrentStepContainer(guildId, systemName)).useComponentsV2().build()).queue();
        }
        // Choice buttons - use defaults or configure
        else if (componentId.startsWith("setup_use_defaults:")) {
            String systemName = componentId.split(":")[1];
            // Enable system with defaults
            handler.toggleSystem(guildId, systemName);
            event.editMessage(new MessageEditBuilder().setComponents(getCurrentStepContainer(guildId, systemName)).useComponentsV2().build()).queue();
        }
        else if (componentId.startsWith("setup_configure:")) {
            String systemName = componentId.split(":")[1];
            // Enable system and show config
            handler.toggleSystem(guildId, systemName);
            Container configStep = getConfigStepForSystem(guildId, systemName);
            if (configStep != null) {
                event.editMessage(new MessageEditBuilder().setComponents(configStep).useComponentsV2().build()).queue();
            } else {
                event.editMessage(new MessageEditBuilder().setComponents(getCurrentStepContainer(guildId, systemName)).useComponentsV2().build()).queue();
            }
        }
        else if (componentId.startsWith("setup_cancel_enable:")) {
            String systemName = componentId.split(":")[1];
            // Don't enable, go back to step
            event.editMessage(new MessageEditBuilder().setComponents(getCurrentStepContainer(guildId, systemName)).useComponentsV2().build()).queue();
        }
        // Skip config buttons - go to next main step
        else if (componentId.startsWith("setup_skip_config:")) {
            String systemName = componentId.split(":")[1];
            event.editMessage(new MessageEditBuilder().setComponents(getCurrentStepContainer(guildId, systemName)).useComponentsV2().build()).queue();
        }
        // Ticket panel step - skip sending panel
        else if (componentId.equals("setup_ticket_skip_panel")) {
            event.editMessage(new MessageEditBuilder().setComponents(buildTicketStep(guildId)).useComponentsV2().build()).queue();
        }
        // Next step buttons
        else if (componentId.equals("setup_next_mod")) {
            event.editMessage(new MessageEditBuilder().setComponents(buildWarningStep(guildId)).useComponentsV2().build()).queue();
        }
        else if (componentId.equals("setup_next_warn")) {
            event.editMessage(new MessageEditBuilder().setComponents(buildTicketStep(guildId)).useComponentsV2().build()).queue();
        }
        else if (componentId.equals("setup_next_ticket")) {
            event.editMessage(new MessageEditBuilder().setComponents(buildLevelingStep(guildId)).useComponentsV2().build()).queue();
        }
        else if (componentId.equals("setup_next_leveling")) {
            event.editMessage(new MessageEditBuilder().setComponents(buildVerifyStep(guildId)).useComponentsV2().build()).queue();
        }
        else if (componentId.equals("setup_next_verify")) {
            event.editMessage(new MessageEditBuilder().setComponents(buildSelectRolesStep(guildId)).useComponentsV2().build()).queue();
        }
        else if (componentId.equals("setup_next_selectroles")) {
            event.editMessage(new MessageEditBuilder().setComponents(buildTempRoleStep(guildId)).useComponentsV2().build()).queue();
        }
        else if (componentId.equals("setup_next_temprole")) {
            event.editMessage(new MessageEditBuilder().setComponents(buildRoleEventStep(guildId)).useComponentsV2().build()).queue();
        }
        else if (componentId.equals("setup_next_roleevent")) {
            event.editMessage(new MessageEditBuilder().setComponents(buildEmbedStep(guildId)).useComponentsV2().build()).queue();
        }
        else if (componentId.equals("setup_next_embed")) {
            event.editMessage(new MessageEditBuilder().setComponents(buildRemindersStep(guildId)).useComponentsV2().build()).queue();
        }
        else if (componentId.equals("setup_next_reminders")) {
            event.editMessage(new MessageEditBuilder().setComponents(buildStatsStep(guildId)).useComponentsV2().build()).queue();
        }
        else if (componentId.equals("setup_next_stats")) {
            event.editMessage(new MessageEditBuilder().setComponents(buildFinalStep(guildId)).useComponentsV2().build()).queue();
        }
        else if (componentId.equals("manage_systems")) {
            // Open system menu
            event.editMessage(new MessageEditBuilder().setComponents(Container.of(
                    TextDisplay.of(t(guildId, "setup_wizard.complete_title")),
                    TextDisplay.of(t(guildId, "setup_wizard.complete_description"))
            )).useComponentsV2().build()).queue();
            systemsListener.sendSystemMessage(event);
        }
    }

    // ==================== HELPER METHODS ====================

    private boolean hasConfigOptions(String systemName) {
        return switch (systemName) {
            case "leveling", "ticket", "verify-button" -> true;
            default -> false;
        };
    }

    // ==================== CONFIG CHOICE STEP ====================

    private Container buildConfigChoiceStep(String guildId, String systemName) {
        String title = getConfigChoiceTitle(guildId, systemName);
        String description = getConfigChoiceDescription(guildId, systemName);
        String defaultsInfo = getDefaultsInfo(guildId, systemName);
        int accentColor = getSystemAccentColor(systemName);

        return Container.of(
                buildBreadcrumbChoice(guildId, systemName),
                TextDisplay.of(title),
                TextDisplay.of(description),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.choice_defaults_title")),
                TextDisplay.of("```\n" + defaultsInfo + "\n```"),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.choice_question")),
                ActionRow.of(
                        Button.success("setup_use_defaults:" + systemName, t(guildId, "setup_wizard.btn_use_defaults"))
                                .withEmoji(Emoji.fromUnicode("⚡")),
                        Button.primary("setup_configure:" + systemName, t(guildId, "setup_wizard.btn_configure_now"))
                                .withEmoji(Emoji.fromUnicode("⚙️")),
                        Button.secondary("setup_cancel_enable:" + systemName, t(guildId, "general.cancel"))
                                .withEmoji(Emoji.fromUnicode("❌"))
                )
        ).withAccentColor(accentColor);
    }

    private String getConfigChoiceTitle(String guildId, String systemName) {
        return switch (systemName) {
            case "leveling" -> t(guildId, "setup_wizard.choice_leveling_title");
            case "ticket" -> t(guildId, "setup_wizard.choice_ticket_title");
            case "verify-button" -> t(guildId, "setup_wizard.choice_verify_title");
            default -> "## ⚙️ " + t(guildId, "setup_wizard.config_title");
        };
    }

    private String getConfigChoiceDescription(String guildId, String systemName) {
        return switch (systemName) {
            case "leveling" -> t(guildId, "setup_wizard.choice_leveling_desc");
            case "ticket" -> t(guildId, "setup_wizard.choice_ticket_desc");
            case "verify-button" -> t(guildId, "setup_wizard.choice_verify_desc");
            default -> t(guildId, "setup_wizard.choice_default_desc");
        };
    }

    private String getDefaultsInfo(String guildId, String systemName) {
        return switch (systemName) {
            case "leveling" -> t(guildId, "setup_wizard.leveling_defaults");
            case "ticket" -> t(guildId, "setup_wizard.ticket_defaults");
            case "verify-button" -> t(guildId, "setup_wizard.verify_defaults");
            default -> "";
        };
    }

    private int getSystemAccentColor(String systemName) {
        return switch (systemName) {
            case "leveling" -> 0x3498DB;
            case "ticket" -> 0x9B59B6;
            case "verify-button" -> 0x2ECC71;
            case "mod" -> 0xE74C3C;
            case "warn" -> 0xE67E22;
            case "select-roles" -> 0x1ABC9C;
            case "temprole" -> 0xF39C12;
            case "role-event" -> 0x8E44AD;
            case "embed" -> 0x16A085;
            case "reminders" -> 0xD35400;
            case "stats" -> 0x27AE60;
            default -> 0x5865F2;
        };
    }

    // ==================== CONFIG STEP BUILDERS ====================

    private Container getConfigStepForSystem(String guildId, String systemName) {
        return switch (systemName) {
            case "leveling" -> buildLevelingConfigStep(guildId);
            case "ticket" -> buildTicketConfigStep(guildId);
            case "verify-button" -> buildVerifyConfigStep(guildId);
            default -> null;
        };
    }

    private Container buildLevelingConfigStep(String guildId) {
        return Container.of(
                buildBreadcrumbConfig(guildId, "leveling"),
                TextDisplay.of(t(guildId, "setup_wizard.config_leveling_title")),
                TextDisplay.of(t(guildId, "setup_wizard.config_leveling_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.config_levelup_channel")),
                ActionRow.of(
                        EntitySelectMenu.create("setup_leveling_channel", EntitySelectMenu.SelectTarget.CHANNEL)
                                .setChannelTypes(EnumSet.of(ChannelType.TEXT))
                                .setPlaceholder(t(guildId, "setup_wizard.select_channel_placeholder"))
                                .setRequiredRange(0, 1)
                                .build()
                ),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.config_leveling_xp_mode")),
                ActionRow.of(
                        StringSelectMenu.create("setup_leveling_xp_mode")
                                .addOption(t(guildId, "setup_wizard.xp_mode_balanced"), "balanced",
                                        t(guildId, "setup_wizard.xp_mode_balanced_desc"), Emoji.fromUnicode("⚖️"))
                                .addOption(t(guildId, "setup_wizard.xp_mode_fast"), "fast",
                                        t(guildId, "setup_wizard.xp_mode_fast_desc"), Emoji.fromUnicode("🚀"))
                                .addOption(t(guildId, "setup_wizard.xp_mode_slow"), "slow",
                                        t(guildId, "setup_wizard.xp_mode_slow_desc"), Emoji.fromUnicode("🐢"))
                                .setPlaceholder(t(guildId, "setup_wizard.select_xp_mode_placeholder"))
                                .build()
                ),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.config_leveling_voice")),
                ActionRow.of(
                        StringSelectMenu.create("setup_leveling_voice")
                                .addOption(t(guildId, "setup_wizard.voice_xp_enabled"), "enabled",
                                        t(guildId, "setup_wizard.voice_xp_enabled_desc"), Emoji.fromUnicode("🎤"))
                                .addOption(t(guildId, "setup_wizard.voice_xp_disabled"), "disabled",
                                        t(guildId, "setup_wizard.voice_xp_disabled_desc"), Emoji.fromUnicode("🔇"))
                                .setPlaceholder(t(guildId, "setup_wizard.select_voice_xp_placeholder"))
                                .build()
                ),
                ActionRow.of(
                        Button.secondary("setup_skip_config:leveling", t(guildId, "setup_wizard.btn_skip_use_default"))
                )
        ).withAccentColor(0x3498DB);
    }

    private Container buildTicketConfigStep(String guildId) {
        return Container.of(
                buildBreadcrumbConfig(guildId, "ticket"),
                TextDisplay.of(t(guildId, "setup_wizard.config_ticket_title")),
                TextDisplay.of(t(guildId, "setup_wizard.config_ticket_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.config_ticket_category")),
                ActionRow.of(
                        EntitySelectMenu.create("setup_ticket_category", EntitySelectMenu.SelectTarget.CHANNEL)
                                .setChannelTypes(EnumSet.of(ChannelType.CATEGORY))
                                .setPlaceholder(t(guildId, "setup_wizard.select_category_placeholder"))
                                .setRequiredRange(1, 1)
                                .build()
                ),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.config_ticket_support_role")),
                ActionRow.of(
                        EntitySelectMenu.create("setup_ticket_role", EntitySelectMenu.SelectTarget.ROLE)
                                .setPlaceholder(t(guildId, "setup_wizard.select_role_placeholder"))
                                .setRequiredRange(0, 1)
                                .build()
                ),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.config_ticket_max_tickets")),
                ActionRow.of(
                        StringSelectMenu.create("setup_ticket_max")
                                .addOption("1 " + t(guildId, "setup_wizard.ticket_per_user"), "1", Emoji.fromUnicode("1️⃣"))
                                .addOption("2 " + t(guildId, "setup_wizard.tickets_per_user"), "2", Emoji.fromUnicode("2️⃣"))
                                .addOption("3 " + t(guildId, "setup_wizard.tickets_per_user"), "3", Emoji.fromUnicode("3️⃣"))
                                .addOption("5 " + t(guildId, "setup_wizard.tickets_per_user"), "5", Emoji.fromUnicode("5️⃣"))
                                .addOption(t(guildId, "setup_wizard.unlimited"), "0", Emoji.fromUnicode("♾️"))
                                .setPlaceholder(t(guildId, "setup_wizard.select_max_tickets_placeholder"))
                                .build()
                ),
                ActionRow.of(
                        Button.secondary("setup_skip_config:ticket", t(guildId, "setup_wizard.btn_skip_use_default"))
                )
        ).withAccentColor(0x9B59B6);
    }

    private Container buildTicketPanelChannelStep(String guildId) {
        return Container.of(
                buildBreadcrumbConfig(guildId, "ticket"),
                TextDisplay.of(t(guildId, "setup_wizard.config_ticket_panel_title")),
                TextDisplay.of(t(guildId, "setup_wizard.config_ticket_panel_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.config_ticket_panel_channel")),
                ActionRow.of(
                        EntitySelectMenu.create("setup_ticket_panel_channel", EntitySelectMenu.SelectTarget.CHANNEL)
                                .setChannelTypes(EnumSet.of(ChannelType.TEXT))
                                .setPlaceholder(t(guildId, "setup_wizard.select_channel_placeholder"))
                                .setRequiredRange(1, 1)
                                .build()
                ),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.config_ticket_panel_hint")),
                ActionRow.of(
                        Button.secondary("setup_ticket_skip_panel", t(guildId, "setup_wizard.btn_skip_no_panel"))
                )
        ).withAccentColor(0x9B59B6);
    }

    private Container buildVerifyConfigStep(String guildId) {
        return Container.of(
                buildBreadcrumbConfig(guildId, "verify-button"),
                TextDisplay.of(t(guildId, "setup_wizard.config_verify_title")),
                TextDisplay.of(t(guildId, "setup_wizard.config_verify_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.config_verify_role")),
                ActionRow.of(
                        EntitySelectMenu.create("setup_verify_role", EntitySelectMenu.SelectTarget.ROLE)
                                .setPlaceholder(t(guildId, "setup_wizard.select_role_placeholder"))
                                .setRequiredRange(1, 1)
                                .build()
                ),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.config_verify_hint")),
                ActionRow.of(
                        Button.secondary("setup_skip_config:verify-button", t(guildId, "setup_wizard.btn_skip_config_later"))
                )
        ).withAccentColor(0x2ECC71);
    }

    private Container getCurrentStepContainer(String guildId, String systemName) {
        return switch (systemName) {
            case "mod" -> buildModerationStep(guildId);
            case "warn" -> buildWarningStep(guildId);
            case "ticket" -> buildTicketStep(guildId);
            case "leveling" -> buildLevelingStep(guildId);
            case "verify-button" -> buildVerifyStep(guildId);
            case "select-roles" -> buildSelectRolesStep(guildId);
            case "temprole" -> buildTempRoleStep(guildId);
            case "role-event" -> buildRoleEventStep(guildId);
            case "embed" -> buildEmbedStep(guildId);
            case "reminders" -> buildRemindersStep(guildId);
            case "stats" -> buildStatsStep(guildId);
            default -> buildFinalStep(guildId);
        };
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.getGuild() == null) return;
        String guildId = event.getGuild().getId();
        String componentId = event.getComponentId();

        if (componentId.equals("setup_lang_select")) {
            String langCode = event.getValues().get(0);
            handler.updateGuildLanguage(guildId, langCode);
            event.editMessage(new MessageEditBuilder().setComponents(buildLogChannelStep(guildId)).useComponentsV2().build()).queue();
        }
        // Leveling XP mode selection
        else if (componentId.equals("setup_leveling_xp_mode")) {
            String mode = event.getValues().get(0);
            applyLevelingXpMode(guildId, mode);
            // Stay on config step to allow more selections
            event.deferEdit().queue();
        }
        // Leveling Voice XP selection
        else if (componentId.equals("setup_leveling_voice")) {
            String voiceMode = event.getValues().get(0);
            boolean enabled = voiceMode.equals("enabled");
            handler.updateLevelSetting(guildId, "voice_xp_enabled", enabled ? "1" : "0");
            // Stay on config step
            event.deferEdit().queue();
        }
        // Ticket max tickets selection
        else if (componentId.equals("setup_ticket_max")) {
            String maxTickets = event.getValues().get(0);
            Integer panelId = setupPanelIds.get(guildId);
            if (panelId != null) {
                handler.updateTicketPanelSettings(panelId, Integer.parseInt(maxTickets), false, false);
            }
            // Stay on config step
            event.deferEdit().queue();
        }
    }

    /**
     * Apply leveling XP mode presets
     */
    private void applyLevelingXpMode(String guildId, String mode) {
        switch (mode) {
            case "balanced" -> {
                // Default balanced settings
                handler.updateLevelSetting(guildId, "xp_min", "15");
                handler.updateLevelSetting(guildId, "xp_max", "25");
                handler.updateLevelSetting(guildId, "cooldown_seconds", "60");
                handler.updateLevelSetting(guildId, "xp_multiplier", "1.0");
                handler.updateLevelSetting(guildId, "voice_xp_min", "5");
                handler.updateLevelSetting(guildId, "voice_xp_max", "15");
            }
            case "fast" -> {
                // Fast progression - higher XP, shorter cooldown
                handler.updateLevelSetting(guildId, "xp_min", "25");
                handler.updateLevelSetting(guildId, "xp_max", "50");
                handler.updateLevelSetting(guildId, "cooldown_seconds", "30");
                handler.updateLevelSetting(guildId, "xp_multiplier", "1.5");
                handler.updateLevelSetting(guildId, "voice_xp_min", "10");
                handler.updateLevelSetting(guildId, "voice_xp_max", "25");
            }
            case "slow" -> {
                // Slow progression - lower XP, longer cooldown
                handler.updateLevelSetting(guildId, "xp_min", "5");
                handler.updateLevelSetting(guildId, "xp_max", "15");
                handler.updateLevelSetting(guildId, "cooldown_seconds", "120");
                handler.updateLevelSetting(guildId, "xp_multiplier", "0.75");
                handler.updateLevelSetting(guildId, "voice_xp_min", "2");
                handler.updateLevelSetting(guildId, "voice_xp_max", "8");
            }
        }
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        if (event.getGuild() == null) return;
        String guildId = event.getGuild().getId();
        String componentId = event.getComponentId();

        if (componentId.equals("setup_log_select")) {
            String channelId = event.getMentions().getChannels().get(0).getId();
            handler.setLogChannel(guildId, channelId);
            handler.toggleSystem(guildId, "log-channel");
            event.editMessage(new MessageEditBuilder().setComponents(buildModerationStep(guildId)).useComponentsV2().build()).queue();
        }
        // Leveling config - channel selection
        else if (componentId.equals("setup_leveling_channel")) {
            if (!event.getMentions().getChannels().isEmpty()) {
                String channelId = event.getMentions().getChannels().get(0).getId();
                handler.updateLevelSetting(guildId, "levelup_channel_id", channelId);
            }
            event.editMessage(new MessageEditBuilder().setComponents(buildLevelingStep(guildId)).useComponentsV2().build()).queue();
        }
        // Ticket config - category selection
        else if (componentId.equals("setup_ticket_category")) {
            if (!event.getMentions().getChannels().isEmpty()) {
                GuildChannel category = event.getMentions().getChannels().get(0);
                handler.setDefaultTicketCategory(guildId, category.getId());

                // Create a default ticket panel for this guild
                int panelId = handler.createTicketPanel(guildId, "Support");
                if (panelId > 0) {
                    // Configure the panel with the category
                    handler.updateTicketPanelChannels(panelId, category.getId(), null, null, null);
                    handler.updateTicketPanel(panelId, "Support", "🎫 " + t(guildId, "setup_wizard.ticket_panel_default_title"),
                            t(guildId, "setup_wizard.ticket_panel_default_desc"), t(guildId, "tickets.create_button"), "🎫", "PRIMARY");
                    setupPanelIds.put(guildId, panelId);
                }
            }
            // Show step to select panel channel
            event.editMessage(new MessageEditBuilder().setComponents(buildTicketPanelChannelStep(guildId)).useComponentsV2().build()).queue();
        }
        // Ticket config - support role selection
        else if (componentId.equals("setup_ticket_role")) {
            if (!event.getMentions().getRoles().isEmpty()) {
                Role role = event.getMentions().getRoles().get(0);
                handler.setDefaultTicketSupportRole(guildId, role.getId());

                // Update panel with support role if we have one
                Integer panelId = setupPanelIds.get(guildId);
                if (panelId != null) {
                    handler.updateTicketPanelChannels(panelId, null, null, role.getId(), null);
                }
            }
            // Show step to select panel channel
            event.editMessage(new MessageEditBuilder().setComponents(buildTicketPanelChannelStep(guildId)).useComponentsV2().build()).queue();
        }
        // Ticket config - panel channel selection (send the panel)
        else if (componentId.equals("setup_ticket_panel_channel")) {
            if (!event.getMentions().getChannels().isEmpty()) {
                GuildChannel channel = event.getMentions().getChannels().get(0);
                if (channel.getType() == ChannelType.TEXT) {
                    TextChannel targetChannel = (TextChannel) channel;
                    Integer panelId = setupPanelIds.get(guildId);

                    if (panelId != null) {
                        DatabaseHandler.TicketPanelData panel = handler.getTicketPanel(panelId);
                        if (panel != null) {
                            // Send the ticket panel to the selected channel
                            sendSetupTicketPanel(targetChannel, panel, panelId, guildId);
                        }
                    }
                }
            }
            // Go to ticket step
            event.editMessage(new MessageEditBuilder().setComponents(buildTicketStep(guildId)).useComponentsV2().build()).queue();
        }
        // Verify config - role selection
        else if (componentId.equals("setup_verify_role")) {
            if (!event.getMentions().getRoles().isEmpty()) {
                Role role = event.getMentions().getRoles().get(0);
                handler.setJustVerifyButton(guildId, role.getId(), null, "Verify", "✅");
            }
            event.editMessage(new MessageEditBuilder().setComponents(buildVerifyStep(guildId)).useComponentsV2().build()).queue();
        }
    }

    // ==================== TICKET PANEL SENDING ====================

    private void sendSetupTicketPanel(TextChannel targetChannel, DatabaseHandler.TicketPanelData panel,
                                       int panelId, String guildId) {
        // Build the embed
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(panel.title != null ? panel.title : "🎫 " + t(guildId, "setup_wizard.ticket_panel_default_title"));
        embed.setDescription(panel.description != null ? handler.processLinebreaks(panel.description) :
                t(guildId, "setup_wizard.ticket_panel_default_desc"));
        embed.setColor(new Color(0x9B59B6));
        embed.setFooter("Ticket System • " + panel.name);

        // Create button
        Button ticketButton = Button.primary("create_ticket_" + panelId,
                panel.buttonLabel != null ? panel.buttonLabel : t(guildId, "tickets.create_button"))
                .withEmoji(Emoji.fromUnicode("🎫"));

        // Send the panel
        targetChannel.sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(ticketButton))
                .queue(message -> {
                    // Store the message ID for later editing
                    handler.updateTicketPanelMessageId(panelId, message.getId());
                    handler.updateTicketPanelChannels(panelId, panel.categoryId, targetChannel.getId(),
                            panel.supportRoleId, panel.pingRoleId);
                }, error -> System.err.println("Failed to send ticket panel: " + error.getMessage()));

        // Clean up temporary storage
        setupPanelIds.remove(guildId);
    }

    // ==================== BREADCRUMB HELPER METHODS ====================

    private TextDisplay buildBreadcrumb(String guildId, int step, String sectionName) {
        return TextDisplay.of(t(guildId, "setup_wizard.breadcrumb_step", step, TOTAL_STEPS, sectionName));
    }

    private TextDisplay buildBreadcrumbConfig(String guildId, String systemName) {
        return TextDisplay.of(t(guildId, "setup_wizard.breadcrumb_config", getSystemDisplayName(guildId, systemName)));
    }

    private TextDisplay buildBreadcrumbChoice(String guildId, String systemName) {
        return TextDisplay.of(t(guildId, "setup_wizard.breadcrumb_choice", getSystemDisplayName(guildId, systemName)));
    }

    private TextDisplay buildBreadcrumbComplete(String guildId) {
        return TextDisplay.of(t(guildId, "setup_wizard.breadcrumb_complete"));
    }

    // ==================== STATUS HELPER METHODS ====================

    private String getStatusEmoji(String guildId, String systemName) {
        Map<String, Boolean> statuses = handler.getGuildSystemsStatus(guildId);
        return statuses.getOrDefault(systemName, false) ? "✅" : "❌";
    }

    private boolean isSystemEnabled(String guildId, String systemName) {
        Map<String, Boolean> statuses = handler.getGuildSystemsStatus(guildId);
        return statuses.getOrDefault(systemName, false);
    }

    private Button buildToggleButton(String guildId, String systemName) {
        boolean enabled = isSystemEnabled(guildId, systemName);
        if (enabled) {
            return Button.success("setup_toggle:" + systemName, t(guildId, "general.enabled"))
                    .withEmoji(Emoji.fromFormatted("✅"));
        } else {
            return Button.danger("setup_toggle:" + systemName, t(guildId, "general.disabled"))
                    .withEmoji(Emoji.fromFormatted("❌"));
        }
    }

    // ==================== STEP BUILDERS ====================

    private Container buildLanguageStep(String guildId) {
        return Container.of(
                buildBreadcrumb(guildId, 1, t(guildId, "setup_wizard.step_name_language")),
                TextDisplay.of(t(guildId, "setup_wizard.step1_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step1_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.language_default") + "\n```"),
                ActionRow.of(
                        StringSelectMenu.create("setup_lang_select")
                                .addOption("English", "en", Emoji.fromUnicode("🇺🇸"))
                                .addOption("Deutsch", "de", Emoji.fromUnicode("🇩🇪"))
                                .build()
                )
        ).withAccentColor(0xF1C40F);
    }

    private Container buildLogChannelStep(String guildId) {
        return Container.of(
                buildBreadcrumb(guildId, 2, t(guildId, "setup_wizard.step_name_log_channel")),
                TextDisplay.of(t(guildId, "setup_wizard.step2_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step2_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.log_defaults") + "\n```"),
                ActionRow.of(
                        EntitySelectMenu.create("setup_log_select", EntitySelectMenu.SelectTarget.CHANNEL)
                                .setChannelTypes(EnumSet.of(ChannelType.TEXT))
                                .setPlaceholder(t(guildId, "setup_wizard.select_channel_placeholder"))
                                .build()
                ),
                ActionRow.of(Button.secondary("setup_skip_log", t(guildId, "setup_wizard.btn_skip")))
        ).withAccentColor(0xE67E22);
    }

    private Container buildModerationStep(String guildId) {
        String status = getStatusEmoji(guildId, "mod");
        return Container.of(
                buildBreadcrumb(guildId, 3, t(guildId, "setup_wizard.system_moderation")),
                TextDisplay.of(t(guildId, "setup_wizard.step_mod_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step_mod_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.current_status") + " " + status),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.mod_defaults") + "\n```"),
                ActionRow.of(
                        buildToggleButton(guildId, "mod"),
                        Button.primary("setup_next_mod", t(guildId, "setup_wizard.btn_next"))
                )
        ).withAccentColor(0xE74C3C);
    }

    private Container buildWarningStep(String guildId) {
        String status = getStatusEmoji(guildId, "warn");
        return Container.of(
                buildBreadcrumb(guildId, 4, t(guildId, "setup_wizard.system_warnings")),
                TextDisplay.of(t(guildId, "setup_wizard.step_warn_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step_warn_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.current_status") + " " + status),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.warn_defaults") + "\n```"),
                ActionRow.of(
                        buildToggleButton(guildId, "warn"),
                        Button.primary("setup_next_warn", t(guildId, "setup_wizard.btn_next"))
                )
        ).withAccentColor(0xE67E22);
    }

    private Container buildTicketStep(String guildId) {
        String status = getStatusEmoji(guildId, "ticket");
        String configuredInfo = "";
        String category = handler.getDefaultTicketCategory(guildId);
        String supportRole = handler.getDefaultTicketSupportRole(guildId);
        if (category != null || supportRole != null) {
            configuredInfo = "\n\n" + t(guildId, "setup_wizard.configured") + "\n";
            if (category != null) configuredInfo += "📁 " + t(guildId, "setup_wizard.category_set") + "\n";
            if (supportRole != null) configuredInfo += "👥 " + t(guildId, "setup_wizard.support_role_set");
        }

        // Check if panel was sent
        var panels = handler.getTicketPanels(guildId);
        if (!panels.isEmpty()) {
            configuredInfo += "\n🎫 " + t(guildId, "setup_wizard.ticket_panel_sent");
        }

        return Container.of(
                buildBreadcrumb(guildId, 5, t(guildId, "setup_wizard.system_tickets")),
                TextDisplay.of(t(guildId, "setup_wizard.step_ticket_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step_ticket_description") + configuredInfo),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.current_status") + " " + status),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.ticket_defaults") + "\n```"),
                ActionRow.of(
                        buildToggleButton(guildId, "ticket"),
                        Button.primary("setup_next_ticket", t(guildId, "setup_wizard.btn_next"))
                )
        ).withAccentColor(0x9B59B6);
    }

    private Container buildLevelingStep(String guildId) {
        String status = getStatusEmoji(guildId, "leveling");
        String channelInfo = "";
        DatabaseHandler.LevelSettingsData settings = handler.getLevelSettings(guildId);
        if (settings != null && settings.levelupChannelId != null && !settings.levelupChannelId.equals("current")) {
            channelInfo = "\n\n📢 " + t(guildId, "setup_wizard.levelup_channel_configured") + " <#" + settings.levelupChannelId + ">";
        }

        return Container.of(
                buildBreadcrumb(guildId, 6, t(guildId, "setup_wizard.system_leveling")),
                TextDisplay.of(t(guildId, "setup_wizard.step_leveling_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step_leveling_description") + channelInfo),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.current_status") + " " + status),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.leveling_defaults") + "\n```"),
                ActionRow.of(
                        buildToggleButton(guildId, "leveling"),
                        Button.primary("setup_next_leveling", t(guildId, "setup_wizard.btn_next"))
                )
        ).withAccentColor(0x3498DB);
    }

    private Container buildVerifyStep(String guildId) {
        String status = getStatusEmoji(guildId, "verify-button");
        String configInfo = "";
        var configs = handler.getVerifyButtonConfigs(guildId);
        if (configs != null && !configs.isEmpty()) {
            configInfo = "\n\n✅ " + t(guildId, "setup_wizard.verify_configured");
        }

        return Container.of(
                buildBreadcrumb(guildId, 7, t(guildId, "setup_wizard.system_verify")),
                TextDisplay.of(t(guildId, "setup_wizard.step_verify_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step_verify_description") + configInfo),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.current_status") + " " + status),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.verify_defaults") + "\n```"),
                ActionRow.of(
                        buildToggleButton(guildId, "verify-button"),
                        Button.primary("setup_next_verify", t(guildId, "setup_wizard.btn_next"))
                )
        ).withAccentColor(0x2ECC71);
    }

    private Container buildSelectRolesStep(String guildId) {
        String status = getStatusEmoji(guildId, "select-roles");
        return Container.of(
                buildBreadcrumb(guildId, 8, t(guildId, "setup_wizard.system_selectroles")),
                TextDisplay.of(t(guildId, "setup_wizard.step_selectroles_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step_selectroles_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.current_status") + " " + status),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.selectroles_defaults") + "\n```"),
                ActionRow.of(
                        buildToggleButton(guildId, "select-roles"),
                        Button.primary("setup_next_selectroles", t(guildId, "setup_wizard.btn_next"))
                )
        ).withAccentColor(0x1ABC9C);
    }

    private Container buildTempRoleStep(String guildId) {
        String status = getStatusEmoji(guildId, "temprole");
        return Container.of(
                buildBreadcrumb(guildId, 9, t(guildId, "setup_wizard.system_temprole")),
                TextDisplay.of(t(guildId, "setup_wizard.step_temprole_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step_temprole_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.current_status") + " " + status),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.temprole_defaults") + "\n```"),
                ActionRow.of(
                        buildToggleButton(guildId, "temprole"),
                        Button.primary("setup_next_temprole", t(guildId, "setup_wizard.btn_next"))
                )
        ).withAccentColor(0xF39C12);
    }

    private Container buildRoleEventStep(String guildId) {
        String status = getStatusEmoji(guildId, "role-event");
        return Container.of(
                buildBreadcrumb(guildId, 10, t(guildId, "setup_wizard.system_roleevent")),
                TextDisplay.of(t(guildId, "setup_wizard.step_roleevent_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step_roleevent_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.current_status") + " " + status),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.roleevent_defaults") + "\n```"),
                ActionRow.of(
                        buildToggleButton(guildId, "role-event"),
                        Button.primary("setup_next_roleevent", t(guildId, "setup_wizard.btn_next"))
                )
        ).withAccentColor(0x8E44AD);
    }

    private Container buildEmbedStep(String guildId) {
        String status = getStatusEmoji(guildId, "embed");
        return Container.of(
                buildBreadcrumb(guildId, 11, t(guildId, "setup_wizard.system_embed")),
                TextDisplay.of(t(guildId, "setup_wizard.step_embed_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step_embed_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.current_status") + " " + status),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.embed_defaults") + "\n```"),
                ActionRow.of(
                        buildToggleButton(guildId, "embed"),
                        Button.primary("setup_next_embed", t(guildId, "setup_wizard.btn_next"))
                )
        ).withAccentColor(0x16A085);
    }

    private Container buildRemindersStep(String guildId) {
        String status = getStatusEmoji(guildId, "reminders");
        return Container.of(
                buildBreadcrumb(guildId, 12, t(guildId, "setup_wizard.system_reminders")),
                TextDisplay.of(t(guildId, "setup_wizard.step_reminders_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step_reminders_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.current_status") + " " + status),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.reminders_defaults") + "\n```"),
                ActionRow.of(
                        buildToggleButton(guildId, "reminders"),
                        Button.primary("setup_next_reminders", t(guildId, "setup_wizard.btn_next"))
                )
        ).withAccentColor(0xD35400);
    }

    private Container buildStatsStep(String guildId) {
        String status = getStatusEmoji(guildId, "stats");
        return Container.of(
                buildBreadcrumb(guildId, 13, t(guildId, "setup_wizard.system_statistics")),
                TextDisplay.of(t(guildId, "setup_wizard.step_stats_title")),
                TextDisplay.of(t(guildId, "setup_wizard.step_stats_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.current_status") + " " + status),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.default_settings")),
                TextDisplay.of("```\n" + t(guildId, "setup_wizard.stats_defaults") + "\n```"),
                ActionRow.of(
                        buildToggleButton(guildId, "stats"),
                        Button.primary("setup_next_stats", t(guildId, "setup_wizard.btn_next"))
                )
        ).withAccentColor(0x27AE60);
    }

    private Container buildFinalStep(String guildId) {
        // Build summary of enabled systems
        Map<String, Boolean> statuses = handler.getGuildSystemsStatus(guildId);
        StringBuilder enabledSystems = new StringBuilder();
        StringBuilder disabledSystems = new StringBuilder();

        for (String sys : SYSTEMS) {
            String displayName = getSystemDisplayName(guildId, sys);
            if (statuses.getOrDefault(sys, false)) {
                enabledSystems.append("✅ ").append(displayName).append("\n");
            } else {
                disabledSystems.append("❌ ").append(displayName).append("\n");
            }
        }

        String summaryText = t(guildId, "setup_wizard.summary_enabled") + "\n" +
                (!enabledSystems.isEmpty() ? enabledSystems.toString() : t(guildId, "setup_wizard.none")) +
                "\n" + t(guildId, "setup_wizard.summary_disabled") + "\n" +
                (!disabledSystems.isEmpty() ? disabledSystems.toString() : t(guildId, "setup_wizard.none"));

        return Container.of(
                buildBreadcrumbComplete(guildId),
                TextDisplay.of(t(guildId, "setup_wizard.complete_title")),
                TextDisplay.of(t(guildId, "setup_wizard.complete_description")),
                TextDisplay.of("\n" + t(guildId, "setup_wizard.summary_title")),
                TextDisplay.of(summaryText),
                ActionRow.of(Button.primary("manage_systems", t(guildId, "setup_wizard.btn_manage_systems")))
        ).withAccentColor(0x2ECC71);
    }

    private String getSystemDisplayName(String guildId, String systemKey) {
        return switch (systemKey) {
            case "log-channel" -> t(guildId, "setup_wizard.system_logging");
            case "warn" -> t(guildId, "setup_wizard.system_warnings");
            case "ticket" -> t(guildId, "setup_wizard.system_tickets");
            case "mod" -> t(guildId, "setup_wizard.system_moderation");
            case "stats" -> t(guildId, "setup_wizard.system_statistics");
            case "verify-button" -> t(guildId, "setup_wizard.system_verify");
            case "select-roles" -> t(guildId, "setup_wizard.system_selectroles");
            case "temprole" -> t(guildId, "setup_wizard.system_temprole");
            case "role-event" -> t(guildId, "setup_wizard.system_roleevent");
            case "embed" -> t(guildId, "setup_wizard.system_embed");
            case "reminders" -> t(guildId, "setup_wizard.system_reminders");
            case "leveling" -> t(guildId, "setup_wizard.system_leveling");
            default -> systemKey;
        };
    }
}
