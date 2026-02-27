package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;

public class HelpCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public HelpCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    // ==================== LANGUAGE HELPER METHODS ====================

    private String t(String guildId, String key) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            if (guildId != null) {
                return lang.get(guildId, key);
            }
            return lang.getTranslation(LanguageManager.DEFAULT_LANGUAGE, key);
        }
        return key;
    }

    private String t(String guildId, String key, Object... args) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            if (guildId != null) {
                return lang.get(guildId, key, args);
            }
            String translation = lang.getTranslation(LanguageManager.DEFAULT_LANGUAGE, key);
            try {
                return String.format(translation, args);
            } catch (Exception e) {
                return translation;
            }
        }
        try {
            return String.format(key, args);
        } catch (Exception e) {
            return key;
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("help")) {

            if (!event.isFromGuild() || event.getGuild() == null) {
                event.reply(t(null, "language.server_only")).setEphemeral(true).queue();
                return;
            }

            String guildId = event.getGuild().getId();
            Container container = buildHomePage(guildId);
            MessageCreateBuilder messageBuilder = new MessageCreateBuilder().setComponents(container);
            event.reply(messageBuilder.useComponentsV2().build()).queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String customId = event.getComponentId();

        if (customId.startsWith("help_")) {
            if (!event.isFromGuild() || event.getGuild() == null) return;

            String guildId = event.getGuild().getId();
            String page = customId.substring(5);

            Container container = buildPage(guildId, page);
            if (container != null) {
                MessageEditBuilder editBuilder = new MessageEditBuilder().setComponents(container);
                event.editMessage(editBuilder.useComponentsV2().build()).queue();
            }
        }
    }

    private Container buildPage(String guildId, String page) {
        return switch (page) {
            case "home" -> buildHomePage(guildId);
            case "overview" -> buildOverviewPage(guildId);
            case "systems" -> buildSystemsPage(guildId);
            case "setup" -> buildSetupPage(guildId);
            case "commands_1" -> buildCommandsPage1(guildId);
            case "commands_2" -> buildCommandsPage2(guildId);
            case "commands_3" -> buildCommandsPage3(guildId);
            case "commands_4" -> buildCommandsPage4(guildId);
            case "commands_5" -> buildCommandsPage5(guildId);
            case "leveling_guide" -> buildLevelingGuidePage(guildId);
            case "support_development" -> buildSupportPage(guildId);
            case "legal" -> buildLegalPage(guildId);
            case "rules_formatting" -> buildFormattingPage(guildId);
            default -> buildHomePage(guildId);
        };
    }

    // ==================== HOME PAGE ====================
    private Container buildHomePage(String guildId) {
        StringBuilder sections = new StringBuilder();
        sections.append("🏠 **").append(t(guildId, "help.overview")).append("** - ").append(t(guildId, "help.overview_desc")).append("\n");
        sections.append("⚙️ **").append(t(guildId, "help.systems")).append("** - ").append(t(guildId, "help.systems_desc")).append("\n");
        sections.append("📋 **").append(t(guildId, "help.setup")).append("** - ").append(t(guildId, "help.setup_desc")).append("\n");
        sections.append("📖 **").append(t(guildId, "help.commands")).append("** - ").append(t(guildId, "help.commands_desc")).append("\n");
        sections.append("📈 **").append(t(guildId, "help.leveling")).append("** - ").append(t(guildId, "help.leveling_desc")).append("\n");
        sections.append("🌐 **").append(t(guildId, "help.language")).append("** - ").append(t(guildId, "help.language_desc")).append("\n");
        sections.append("🎨 **").append(t(guildId, "help.formatting")).append("** - ").append(t(guildId, "help.formatting_desc")).append("\n");
        sections.append("📜 **").append(t(guildId, "help.legal")).append("** - ").append(t(guildId, "help.legal_desc")).append("\n");
        sections.append("💡 **").append(t(guildId, "help.support")).append("** - ").append(t(guildId, "help.support_desc"));

        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.title")),
                TextDisplay.of(t(guildId, "help.welcome")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## " + t(guildId, "help.sections")),
                TextDisplay.of(sections.toString()),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("*" + t(guildId, "help.note_rework") + "*"),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.primary("help_overview", t(guildId, "help.btn_overview")),
                        Button.primary("help_systems", t(guildId, "help.btn_systems")),
                        Button.primary("help_setup", t(guildId, "help.btn_setup")),
                        Button.primary("help_commands_1", t(guildId, "help.btn_commands"))
                ),
                ActionRow.of(
                        Button.primary("help_leveling_guide", t(guildId, "help.btn_leveling")),
                        Button.primary("help_rules_formatting", t(guildId, "help.btn_formatting")),
                        Button.primary("help_support_development", t(guildId, "help.btn_support")),
                        Button.primary("help_legal", t(guildId, "help.btn_legal"))
                )
        ).withAccentColor(0x3498DB); // Blue
    }

    // ==================== OVERVIEW PAGE ====================
    private Container buildOverviewPage(String guildId) {
        StringBuilder features = new StringBuilder();
        features.append("• ").append(t(guildId, "help.feature_logging")).append("\n");
        features.append("• ").append(t(guildId, "help.feature_moderation")).append("\n");
        features.append("• ").append(t(guildId, "help.feature_tickets")).append("\n");
        features.append("• ").append(t(guildId, "help.feature_statistics")).append("\n");
        features.append("• ").append(t(guildId, "help.feature_roles")).append("\n");
        features.append("• ").append(t(guildId, "help.feature_embeds")).append("\n");
        features.append("• ").append(t(guildId, "help.feature_leveling"));

        StringBuilder gettingStarted = new StringBuilder();
        gettingStarted.append(t(guildId, "help.step1")).append("\n");
        gettingStarted.append(t(guildId, "help.step2")).append("\n");
        gettingStarted.append(t(guildId, "help.step3"));

        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.overview_title")),
                TextDisplay.of(t(guildId, "help.overview_intro")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## " + t(guildId, "help.key_features")),
                TextDisplay.of(features.toString()),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## " + t(guildId, "help.getting_started")),
                TextDisplay.of(gettingStarted.toString()),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("help_home", t(guildId, "help.btn_home")),
                        Button.primary("help_systems", t(guildId, "help.btn_systems")),
                        Button.primary("help_setup", t(guildId, "help.btn_setup")),
                        Button.primary("help_commands_1", t(guildId, "help.btn_commands"))
                ),
                ActionRow.of(
                        Button.primary("help_leveling_guide", t(guildId, "help.btn_leveling")),
                        Button.primary("help_rules_formatting", t(guildId, "help.btn_formatting")),
                        Button.primary("help_support_development", t(guildId, "help.btn_support")),
                        Button.primary("help_legal", t(guildId, "help.btn_legal"))
                )
        ).withAccentColor(0x2ECC71); // Green
    }

    // ==================== SYSTEMS PAGE ====================
    private Container buildSystemsPage(String guildId) {
        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.systems_title")),
                TextDisplay.of(t(guildId, "help.systems_intro")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("### " + t(guildId, "help.system_moderation")),
                TextDisplay.of(t(guildId, "help.system_moderation_desc")),

                TextDisplay.of("### " + t(guildId, "help.system_warning")),
                TextDisplay.of(t(guildId, "help.system_warning_desc")),

                TextDisplay.of("### " + t(guildId, "help.system_ticket")),
                TextDisplay.of(t(guildId, "help.system_ticket_desc")),

                TextDisplay.of("### " + t(guildId, "help.system_logchannel")),
                TextDisplay.of(t(guildId, "help.system_logchannel_desc")),

                TextDisplay.of("### " + t(guildId, "help.system_statistics")),
                TextDisplay.of(t(guildId, "help.system_statistics_desc")),

                TextDisplay.of("### " + t(guildId, "help.system_leveling")),
                TextDisplay.of(t(guildId, "help.system_leveling_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("*" + t(guildId, "help.systems_footer") + "*"),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("help_home", t(guildId, "help.btn_home")),
                        Button.primary("help_overview", t(guildId, "help.btn_overview")),
                        Button.primary("help_setup", t(guildId, "help.btn_setup")),
                        Button.primary("help_commands_1", t(guildId, "help.btn_commands"))
                ),
                ActionRow.of(
                        Button.primary("help_leveling_guide", t(guildId, "help.btn_leveling")),
                        Button.primary("help_rules_formatting", t(guildId, "help.btn_formatting")),
                        Button.primary("help_support_development", t(guildId, "help.btn_support")),
                        Button.primary("help_legal", t(guildId, "help.btn_legal"))
                )
        ).withAccentColor(0xE67E22); // Orange
    }

    // ==================== SETUP PAGE ====================
    private Container buildSetupPage(String guildId) {
        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.setup_title")),
                TextDisplay.of(t(guildId, "help.setup_intro")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("### " + t(guildId, "help.setup_step1_title")),
                TextDisplay.of(t(guildId, "help.setup_step1_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.setup_step2_title")),
                TextDisplay.of(t(guildId, "help.setup_step2_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.setup_step3_title")),
                TextDisplay.of(t(guildId, "help.setup_step3_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.setup_step4_title")),
                TextDisplay.of(t(guildId, "help.setup_step4_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("*" + t(guildId, "help.setup_footer") + "*"),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("help_home", t(guildId, "help.btn_home")),
                        Button.primary("help_overview", t(guildId, "help.btn_overview")),
                        Button.primary("help_systems", t(guildId, "help.btn_systems")),
                        Button.primary("help_commands_1", t(guildId, "help.btn_commands"))
                ),
                ActionRow.of(
                        Button.primary("help_leveling_guide", t(guildId, "help.btn_leveling")),
                        Button.primary("help_rules_formatting", t(guildId, "help.btn_formatting")),
                        Button.primary("help_support_development", t(guildId, "help.btn_support")),
                        Button.primary("help_legal", t(guildId, "help.btn_legal"))
                )
        ).withAccentColor(0x00CED1); // Cyan
    }

    // ==================== COMMANDS PAGE 1 ====================
    private Container buildCommandsPage1(String guildId) {
        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.commands_page1_title")),
                TextDisplay.of(t(guildId, "help.commands_page1_subtitle")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("### " + t(guildId, "help.commands_mod_title")),
                TextDisplay.of(t(guildId, "help.commands_mod_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.commands_warn_title")),
                TextDisplay.of(t(guildId, "help.commands_warn_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.commands_log_title")),
                TextDisplay.of(t(guildId, "help.commands_log_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("*" + t(guildId, "help.commands_page1_footer") + "*"),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("help_home", t(guildId, "help.btn_home")),
                        Button.primary("help_commands_2", t(guildId, "help.btn_next"))
                ),
                ActionRow.of(
                        Button.primary("help_systems", t(guildId, "help.btn_systems")),
                        Button.primary("help_setup", t(guildId, "help.btn_setup"))
                )
        ).withAccentColor(0x9B59B6); // Magenta
    }

    // ==================== COMMANDS PAGE 2 ====================
    private Container buildCommandsPage2(String guildId) {
        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.commands_page2_title")),
                TextDisplay.of(t(guildId, "help.commands_page2_subtitle")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("### " + t(guildId, "help.commands_ticket_title")),
                TextDisplay.of(t(guildId, "help.commands_ticket_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.commands_stats_title")),
                TextDisplay.of(t(guildId, "help.commands_stats_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("*" + t(guildId, "help.commands_page2_footer") + "*"),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("help_commands_1", t(guildId, "help.btn_prev")),
                        Button.primary("help_commands_3", t(guildId, "help.btn_next"))
                ),
                ActionRow.of(
                        Button.secondary("help_home", t(guildId, "help.btn_home"))
                )
        ).withAccentColor(0x9B59B6); // Magenta
    }

    // ==================== COMMANDS PAGE 3 ====================
    private Container buildCommandsPage3(String guildId) {
        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.commands_page3_title")),
                TextDisplay.of(t(guildId, "help.commands_page3_subtitle")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("### " + t(guildId, "help.commands_selectroles_title")),
                TextDisplay.of(t(guildId, "help.commands_selectroles_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.commands_timedroles_title")),
                TextDisplay.of(t(guildId, "help.commands_timedroles_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.commands_verify_title")),
                TextDisplay.of(t(guildId, "help.commands_verify_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("*" + t(guildId, "help.commands_page3_footer") + "*"),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("help_commands_2", t(guildId, "help.btn_prev")),
                        Button.primary("help_commands_4", t(guildId, "help.btn_next"))
                ),
                ActionRow.of(
                        Button.secondary("help_home", t(guildId, "help.btn_home"))
                )
        ).withAccentColor(0x9B59B6); // Magenta
    }

    // ==================== COMMANDS PAGE 4 (LEVELING) ====================
    private Container buildCommandsPage4(String guildId) {
        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.commands_page4_title")),
                TextDisplay.of(t(guildId, "help.commands_page4_subtitle")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("### " + t(guildId, "help.commands_level_title")),
                TextDisplay.of(t(guildId, "help.commands_level_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.commands_reminder_title")),
                TextDisplay.of(t(guildId, "help.commands_reminder_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("*" + t(guildId, "help.commands_page4_footer") + "*"),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("help_commands_3", t(guildId, "help.btn_prev")),
                        Button.primary("help_commands_5", t(guildId, "help.btn_next"))
                ),
                ActionRow.of(
                        Button.secondary("help_home", t(guildId, "help.btn_home"))
                )
        ).withAccentColor(0x9B59B6); // Magenta
    }

    // ==================== COMMANDS PAGE 5 ====================
    private Container buildCommandsPage5(String guildId) {
        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.commands_page5_title")),
                TextDisplay.of(t(guildId, "help.commands_page5_subtitle")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("### " + t(guildId, "help.commands_embed_title")),
                TextDisplay.of(t(guildId, "help.commands_embed_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.commands_core_title")),
                TextDisplay.of(t(guildId, "help.commands_core_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("*" + t(guildId, "help.commands_page5_footer") + "*"),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("help_commands_4", t(guildId, "help.btn_prev")),
                        Button.secondary("help_home", t(guildId, "help.btn_home"))
                ),
                ActionRow.of(
                        Button.primary("help_rules_formatting", t(guildId, "help.btn_formatting"))
                )
        ).withAccentColor(0x9B59B6); // Magenta
    }

    // ==================== SUPPORT PAGE ====================
    private Container buildSupportPage(String guildId) {
        StringBuilder ways = new StringBuilder();
        ways.append("• ").append(t(guildId, "help.support_donate")).append("\n");
        ways.append("• ").append(t(guildId, "help.support_feedback")).append("\n");
        ways.append("• ").append(t(guildId, "help.support_spread"));

        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.support_title")),
                TextDisplay.of(t(guildId, "help.support_intro")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## " + t(guildId, "help.support_ways")),
                TextDisplay.of(ways.toString()),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of(t(guildId, "help.support_thanks")),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("help_home", t(guildId, "help.btn_home")),
                        Button.primary("help_overview", t(guildId, "help.btn_overview")),
                        Button.primary("help_systems", t(guildId, "help.btn_systems")),
                        Button.primary("help_setup", t(guildId, "help.btn_setup"))
                ),
                ActionRow.of(
                        Button.primary("help_leveling_guide", t(guildId, "help.btn_leveling")),
                        Button.primary("help_commands_1", t(guildId, "help.btn_commands")),
                        Button.primary("help_rules_formatting", t(guildId, "help.btn_formatting")),
                        Button.primary("help_legal", t(guildId, "help.btn_legal")),
                        Button.of(ButtonStyle.LINK, "https://ko-fi.com/gamingtoast27542", t(guildId, "help.btn_donate"))
                )
        ).withAccentColor(0xFF69B4); // Pink
    }

    // ==================== LEGAL PAGE ====================
    private Container buildLegalPage(String guildId) {
        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.legal_title")),
                TextDisplay.of(t(guildId, "help.legal_intro")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("### " + t(guildId, "help.legal_tos_title")),
                TextDisplay.of(t(guildId, "help.legal_tos_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.legal_privacy_title")),
                TextDisplay.of(t(guildId, "help.legal_privacy_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.legal_contact_title")),
                TextDisplay.of(t(guildId, "help.legal_contact_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("*" + t(guildId, "help.legal_footer") + "*"),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("help_home", t(guildId, "help.btn_home")),
                        Button.primary("help_overview", t(guildId, "help.btn_overview")),
                        Button.primary("help_systems", t(guildId, "help.btn_systems")),
                        Button.primary("help_setup", t(guildId, "help.btn_setup"))
                ),
                ActionRow.of(
                        Button.primary("help_leveling_guide", t(guildId, "help.btn_leveling")),
                        Button.primary("help_commands_1", t(guildId, "help.btn_commands")),
                        Button.primary("help_support_development", t(guildId, "help.btn_support")),
                        Button.of(ButtonStyle.LINK, "https://github.com/GamingToastEVE/Sloth", "📄 GitHub")
                )
        ).withAccentColor(0x808080); // Gray
    }

    // ==================== LEVELING GUIDE PAGE ====================
    private Container buildLevelingGuidePage(String guildId) {
        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.leveling_guide_title")),
                TextDisplay.of(t(guildId, "help.leveling_guide_intro")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## " + t(guildId, "help.leveling_how_it_works")),
                TextDisplay.of(t(guildId, "help.leveling_how_it_works_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.leveling_commands_title")),
                TextDisplay.of(t(guildId, "help.leveling_commands_desc")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("## " + t(guildId, "help.leveling_settings_title")),
                TextDisplay.of(t(guildId, "help.leveling_settings_intro")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.leveling_formula_title")),
                TextDisplay.of(t(guildId, "help.leveling_formula_desc")),

                TextDisplay.of("### " + t(guildId, "help.leveling_message_xp_title")),
                TextDisplay.of(t(guildId, "help.leveling_message_xp_desc")),

                TextDisplay.of("### " + t(guildId, "help.leveling_voice_xp_title")),
                TextDisplay.of(t(guildId, "help.leveling_voice_xp_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.leveling_reaction_xp_title")),
                TextDisplay.of(t(guildId, "help.leveling_reaction_xp_desc")),

                TextDisplay.of("### " + t(guildId, "help.leveling_notifications_title")),
                TextDisplay.of(t(guildId, "help.leveling_notifications_desc")),

                TextDisplay.of("### " + t(guildId, "help.leveling_rewards_title")),
                TextDisplay.of(t(guildId, "help.leveling_rewards_desc")),

                TextDisplay.of("### " + t(guildId, "help.leveling_exceptions_title")),
                TextDisplay.of(t(guildId, "help.leveling_exceptions_desc")),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("help_home", t(guildId, "help.btn_home")),
                        Button.primary("help_systems", t(guildId, "help.btn_systems")),
                        Button.primary("help_commands_4", t(guildId, "help.btn_commands"))
                )
        ).withAccentColor(0x9B59B6); // Purple
    }

    // ==================== FORMATTING PAGE ====================
    private Container buildFormattingPage(String guildId) {
        return Container.of(
                TextDisplay.of("# " + t(guildId, "help.formatting_title")),
                TextDisplay.of(t(guildId, "help.formatting_intro")),

                Separator.createDivider(Separator.Spacing.LARGE),

                TextDisplay.of("### " + t(guildId, "help.formatting_basic_title")),
                TextDisplay.of(t(guildId, "help.formatting_basic_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.formatting_code_title")),
                TextDisplay.of(t(guildId, "help.formatting_code_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.formatting_lists_title")),
                TextDisplay.of(t(guildId, "help.formatting_lists_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.formatting_links_title")),
                TextDisplay.of(t(guildId, "help.formatting_links_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.formatting_special_title")),
                TextDisplay.of(t(guildId, "help.formatting_special_desc")),

                Separator.createDivider(Separator.Spacing.SMALL),

                TextDisplay.of("### " + t(guildId, "help.formatting_tips_title")),
                TextDisplay.of(t(guildId, "help.formatting_tips_desc")),

                Separator.createDivider(Separator.Spacing.LARGE),

                ActionRow.of(
                        Button.secondary("help_home", t(guildId, "help.btn_home")),
                        Button.primary("help_overview", t(guildId, "help.btn_overview")),
                        Button.primary("help_systems", t(guildId, "help.btn_systems")),
                        Button.primary("help_setup", t(guildId, "help.btn_setup"))
                ),
                ActionRow.of(
                        Button.primary("help_leveling_guide", t(guildId, "help.btn_leveling")),
                        Button.primary("help_commands_1", t(guildId, "help.btn_commands")),
                        Button.primary("help_support_development", t(guildId, "help.btn_support")),
                        Button.primary("help_legal", t(guildId, "help.btn_legal"))
                )
        ).withAccentColor(0xFFD700); // Yellow
    }
}

