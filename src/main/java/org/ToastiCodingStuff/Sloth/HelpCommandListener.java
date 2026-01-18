package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.awt.*;
import java.util.Objects;

public class HelpCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public HelpCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    private String getLang(String guildId) {
        return handler.getGuildLanguage(guildId);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("help")) {
            handler.insertOrUpdateGlobalStatistic("help");
            handleHelpCommand(event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String customId = event.getComponentId();
        
        if (customId.startsWith("help_")) {
            handleHelpNavigation(event, customId);
        }
    }

    private void handleHelpCommand(SlashCommandInteractionEvent event) {
        // Show the main help page
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String lang = getLang(guildId);
        showHelpPage(event, null, "home", lang);
    }

    private void handleHelpNavigation(ButtonInteractionEvent event, String customId) {
        // Extract page from custom ID (format: help_<page>)
        String page = customId.substring(5);
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String lang = getLang(guildId);
        showHelpPage(null, event, page, lang);
    }

    private void showHelpPage(SlashCommandInteractionEvent slashEvent, ButtonInteractionEvent buttonEvent, String page, String lang) {
        EmbedBuilder embed = new EmbedBuilder();
        ActionRow actionRow;
        ActionRow actionRow2;

        switch (page) {
            case "home":
                embed.setTitle(LocaleManager.getMessage(lang, "help.title"))
                        .setDescription(LocaleManager.getMessage(lang, "help.welcome"))
                        .setColor(Color.BLUE)
                        .setFooter(LocaleManager.getMessage(lang, "help.footer"));

                actionRow = ActionRow.of(
                        Button.primary("help_overview", LocaleManager.getMessage(lang, "button.overview")),
                        Button.primary("help_systems", LocaleManager.getMessage(lang, "button.systems")),
                        Button.primary("help_setup", LocaleManager.getMessage(lang, "button.setup")),
                        Button.primary("help_commands", LocaleManager.getMessage(lang, "button.commands"))
                );
                actionRow2 = ActionRow.of(
                        Button.primary("help_rules_formatting", LocaleManager.getMessage(lang, "button.formatting")),
                        Button.primary("help_support_developement", LocaleManager.getMessage(lang, "button.support")),
                        Button.primary("help_legal", LocaleManager.getMessage(lang, "button.legal"))
                );
                break;

            case "overview":
                embed.setTitle(LocaleManager.getMessage(lang, "help.overview.title"))
                        .setDescription(LocaleManager.getMessage(lang, "help.overview.description"))
                        .setColor(Color.GREEN)
                        .setFooter(LocaleManager.getMessage(lang, "help.footer"));

                actionRow = ActionRow.of(
                        Button.secondary("help_home", LocaleManager.getMessage(lang, "button.home")),
                        Button.primary("help_systems", LocaleManager.getMessage(lang, "button.systems")),
                        Button.primary("help_setup", LocaleManager.getMessage(lang, "button.setup")),
                        Button.primary("help_commands", LocaleManager.getMessage(lang, "button.commands"))
                );
                actionRow2 = ActionRow.of(
                        Button.primary("help_rules_formatting", LocaleManager.getMessage(lang, "button.formatting")),
                        Button.primary("help_support_developement", LocaleManager.getMessage(lang, "button.support")),
                        Button.primary("help_legal", LocaleManager.getMessage(lang, "button.legal"))
                );
                break;

            case "systems":
                embed.setTitle(LocaleManager.getMessage(lang, "help.systems.title"))
                        .setDescription(LocaleManager.getMessage(lang, "help.systems.description"))
                        .addField(LocaleManager.getMessage(lang, "help.systems.moderation.title"), 
                                LocaleManager.getMessage(lang, "help.systems.moderation.description"), false)
                        .addField(LocaleManager.getMessage(lang, "help.systems.warning.title"), 
                                LocaleManager.getMessage(lang, "help.systems.warning.description"), false)
                        .addField(LocaleManager.getMessage(lang, "help.systems.ticket.title"), 
                                LocaleManager.getMessage(lang, "help.systems.ticket.description"), false)
                        .addField(LocaleManager.getMessage(lang, "help.systems.log.title"), 
                                LocaleManager.getMessage(lang, "help.systems.log.description"), false)
                        .addField(LocaleManager.getMessage(lang, "help.systems.stats.title"),
                                LocaleManager.getMessage(lang, "help.systems.stats.description"), false)
                        .addField(LocaleManager.getMessage(lang, "help.systems.rules.title"), 
                                LocaleManager.getMessage(lang, "help.systems.rules.description"), false)
                        .addField(LocaleManager.getMessage(lang, "help.systems.verify.title"), 
                                LocaleManager.getMessage(lang, "help.systems.verify.description"), false)
                        .addField(LocaleManager.getMessage(lang, "help.systems.selectroles.title"), 
                                LocaleManager.getMessage(lang, "help.systems.selectroles.description"), false)
                        .addField(LocaleManager.getMessage(lang, "help.systems.timedroles.title"), 
                                LocaleManager.getMessage(lang, "help.systems.timedroles.description"), false)
                        .setColor(Color.ORANGE)
                        .setFooter(LocaleManager.getMessage(lang, "help.systems.footer"));

                actionRow = ActionRow.of(
                        Button.secondary("help_home", LocaleManager.getMessage(lang, "button.home")),
                        Button.primary("help_overview", LocaleManager.getMessage(lang, "button.overview")),
                        Button.primary("help_setup", LocaleManager.getMessage(lang, "button.setup")),
                        Button.primary("help_commands", LocaleManager.getMessage(lang, "button.commands"))
                );
                actionRow2 = ActionRow.of(
                        Button.primary("help_rules_formatting", LocaleManager.getMessage(lang, "button.formatting")),
                        Button.primary("help_support_developement", LocaleManager.getMessage(lang, "button.support")),
                        Button.primary("help_legal", LocaleManager.getMessage(lang, "button.legal"))
                );
                break;

            case "setup":
                embed.setTitle(LocaleManager.getMessage(lang, "help.setup.title"))
                        .setDescription(LocaleManager.getMessage(lang, "help.setup.description"))
                        .addField("**Step 1: Choose Systems to Configure**", 
                                "All systems are available to use:\n" +
                                "• Log Channel, Warning, Ticket, Moderation, Statistics\n" +
                                "• Configure only the ones you need", false)
                        .addField("**Step 2: Configure Systems**", 
                                "**Log Channel:** `/log-channel set #channel`\n" +
                                "**Warning System:** `/warn settings-set`\n" +
                                "**Ticket System:** `/ticket setup`\n" +
                                "**Moderation:** Ready to use with `/mod` commands!", false)
                        .addField("**Step 3: Create Panels (Optional)**", 
                                "**Ticket Panel:** `/ticket panel` - Creates user-friendly ticket creation\n" +
                                "Place in a public channel for easy access", false)
                        .addField("**Step 4: Set Permissions**", 
                                "• Ensure staff have appropriate Discord permissions\n" +
                                "• Bot needs Admin permissions for full functionality\n" +
                                "• Configure role-based access for tickets", false)
                        .addField("**Formatting Rules Embeds**", 
                                "Need help formatting your rules descriptions? Use Discord markdown!\n" +
                                "📝 Click the 🎨 Formatting button below for a complete guide.", false)
                        .setColor(Color.CYAN)
                        .setFooter(LocaleManager.getMessage(lang, "help.footer"));

                actionRow = ActionRow.of(
                        Button.secondary("help_home", LocaleManager.getMessage(lang, "button.home")),
                        Button.primary("help_overview", LocaleManager.getMessage(lang, "button.overview")),
                        Button.primary("help_systems", LocaleManager.getMessage(lang, "button.systems")),
                        Button.primary("help_commands", LocaleManager.getMessage(lang, "button.commands"))
                );
                actionRow2 = ActionRow.of(
                        Button.primary("help_rules_formatting", LocaleManager.getMessage(lang, "button.formatting")),
                        Button.primary("help_support_developement", LocaleManager.getMessage(lang, "button.support")),
                        Button.primary("help_legal", LocaleManager.getMessage(lang, "button.legal"))
                );
                break;

            case "commands":
                embed.setTitle(LocaleManager.getMessage(lang, "help.commands.title"))
                        .setDescription(LocaleManager.getMessage(lang, "help.commands.description"))
                        .addField("**Log Channel System**",
                                "`/log-channel set` - Configure logging channel\n" +
                                "`/log-channel get` - View current log channel", false)
                        .addField("**Warning System**",
                                "`/warn user` - Issue warning to user\n" +
                                "`/warn settings-set` - Configure warning thresholds\n" +
                                "`/warn settings-get` - View warning configuration", false)
                        .addField("**Ticket System**",
                                "`/ticket setup` - Configure ticket system\n" +
                                "`/ticket panel` - Create ticket creation panel\n" +
                                "`/ticket config` - Set custom title and description for ticket panel\n" +
                                "`/ticket close` - Close current ticket\n" +
                                "`/ticket assign` - Assign to staff member\n" +
                                "`/ticket priority` - Change ticket priority\n" +
                                "`/ticket info` - Get ticket information\n", false)
                        .addField("**Moderation System**",
                                "`/mod kick` `/mod ban` `/mod unban` - User management\n" +
                                "`/mod timeout` `/mod untimeout` - Temporary restrictions\n" +
                                "`/mod purge` - Delete multiple messages\n" +
                                "`/mod slowmode` - Set channel slowmode", false)
                        .addField("**Statistics System**",
                                "`/stats lifetime` - Lifetime server statistics\n" +
                                "`/stats today` - Today's server statistics\n" +
                                "`/stats week` - Weekly statistics\n" +
                                "`/stats date` - Statistics for specific date\n" +
                                "`/stats user` - View user information and statistics", false)
                        .addField("**Select Roles System**",
                                "`/select-roles add` - Add role to selection list\n" +
                                "`/select-roles remove` - Remove role from selection list\n" +
                                "`/select-roles send` - Send role selection interface\n" +
                                "*Supports reactions, dropdowns, and buttons*", false)
                        .addField("**Rules/Verification System**",
                                "`/rules add` - Create rules embeds with verification\n" +
                                "`/rules setup` - Display rules in current channel\n" +
                                "`/rules list` - List all rules embeds\n" +
                                "`/rules remove` - Remove a rules embed\n" +
                                "📝 *Need help formatting? Use the 🎨 Formatting button below!*", false)
                        .addField("**Verify Button System**",
                                "`/verify-button add` - Add verify button configuration (max 3)\n" +
                                "`/verify-button send` - Send verify button message\n" +
                                "`/verify-button remove` - Remove verify button from current channel", false)
                        .addField("**Timed Roles System**",
                                "`/my-roles` - View your active temporary roles and expiration times\n" +
                                "`/temprole add` - Assign a temporary role to a user for a specified duration\n" +
                                "`/temprole remove` - Remove a temporary role from a user\n" +
                                "`/role-event create` - Create automated role events based on triggers\n" +
                                "`/role-event list` - List and manage all role events", false)
                        .addField("**General Commands**",
                                "`/help` - Show this help system\n" +
                                "`/feedback` - Send feedback to the developer\n" +
                                "`/settings language` - Set bot language", false)
                        .setColor(Color.MAGENTA)
                        .setFooter(LocaleManager.getMessage(lang, "help.footer"));

                actionRow = ActionRow.of(
                        Button.secondary("help_home", LocaleManager.getMessage(lang, "button.home")),
                        Button.primary("help_overview", LocaleManager.getMessage(lang, "button.overview")),
                        Button.primary("help_systems", LocaleManager.getMessage(lang, "button.systems")),
                        Button.primary("help_setup", LocaleManager.getMessage(lang, "button.setup"))
                );
                actionRow2 = ActionRow.of(
                        Button.primary("help_rules_formatting", LocaleManager.getMessage(lang, "button.formatting")),
                        Button.primary("help_support_developement", LocaleManager.getMessage(lang, "button.support")),
                        Button.primary("help_legal", LocaleManager.getMessage(lang, "button.legal"))
                );
                break;

            case "support_developement":
                embed.setTitle(LocaleManager.getMessage(lang, "help.support.title"))
                        .setDescription(LocaleManager.getMessage(lang, "help.support.description"));
                embed.setColor(Color.PINK)
                        .setFooter(LocaleManager.getMessage(lang, "help.footer"));
                actionRow = ActionRow.of(
                        Button.secondary("help_home", LocaleManager.getMessage(lang, "button.home")),
                        Button.primary("help_overview", LocaleManager.getMessage(lang, "button.overview")),
                        Button.primary("help_systems", LocaleManager.getMessage(lang, "button.systems")),
                        Button.primary("help_setup", LocaleManager.getMessage(lang, "button.setup"))
                );
                actionRow2 = ActionRow.of(
                        Button.primary("help_commands", LocaleManager.getMessage(lang, "button.commands")),
                        Button.primary("help_rules_formatting", LocaleManager.getMessage(lang, "button.formatting")),
                        Button.primary("help_legal", LocaleManager.getMessage(lang, "button.legal")),
                        Button.link("https://ko-fi.com/gamingtoast27542", "☕ Donate")
                );
                break;

            case "legal":
                embed.setTitle(LocaleManager.getMessage(lang, "help.legal.title"))
                        .setDescription(LocaleManager.getMessage(lang, "help.legal.description"))
                        .addField("**📋 Terms of Service**", 
                                "By using Sloth Bot, you agree to our Terms of Service.\n" +
                                "**Key Points:**\n" +
                                "• Must be 16+ to use (Discord ToS compliance)\n" +
                                "• Use in accordance with Discord Guidelines\n" +
                                "• No misuse, harassment, or exploitation\n" +
                                "• Service provided \"as is\" without guarantees\n" +
                                "\n📄 **Full document:** `Terms of Service.md` in repository", false)
                        .addField("**🔒 Privacy Policy**", 
                                "We respect your privacy and follow GDPR compliance.\n" +
                                "**What we collect:**\n" +
                                "• Discord user/server IDs (necessary for functionality)\n" +
                                "• Command interactions and parameters\n" +
                                "• Technical logs for stability and security\n" +
                                "\n**Your rights:** Access, rectification, erasure, data portability\n" +
                                "\n📄 **Full document:** `privacy policy.md` in repository", false)
                        .addField("**📞 Contact Information**", 
                                "For questions about Terms of Service or Privacy Policy:\n" +
                                "• Discord: **gamingtoasti**\n" +
                                "• Support Server: https://discord.gg/dQT53fD8M5", false)
                        .setColor(Color.GRAY)
                        .setFooter(LocaleManager.getMessage(lang, "help.footer"));

                actionRow = ActionRow.of(
                        Button.secondary("help_home", LocaleManager.getMessage(lang, "button.home")),
                        Button.primary("help_overview", LocaleManager.getMessage(lang, "button.overview")),
                        Button.primary("help_systems", LocaleManager.getMessage(lang, "button.systems")),
                        Button.primary("help_setup", LocaleManager.getMessage(lang, "button.setup"))
                );
                actionRow2 = ActionRow.of(
                        Button.primary("help_commands", LocaleManager.getMessage(lang, "button.commands")),
                        Button.primary("help_legal", LocaleManager.getMessage(lang, "button.legal")),
                        Button.primary("help_support_developement", LocaleManager.getMessage(lang, "button.support")),
                        Button.link("https://github.com/GamingToastEVE/Sloth", "📄 View on GitHub")
                );
                break;

            case "rules_formatting":
                embed.setTitle(LocaleManager.getMessage(lang, "help.formatting.title"))
                        .setDescription(LocaleManager.getMessage(lang, "help.formatting.description"))
                        .addField("**Basic Text Formatting**", 
                                "• `**bold text**` → **bold text**\n" +
                                "• `*italic text*` → *italic text*\n" +
                                "• `__underlined text__` → __underlined text__\n" +
                                "• `~~strikethrough~~` → ~~strikethrough~~\n" +
                                "• `||spoiler text||` → ||spoiler text||", false)
                        .addField("**Code Formatting**", 
                                "• `` `inline code` `` → `inline code`\n" +
                                "• ```\\n```code block```\\n``` → Multi-line code blocks\n" +
                                "• ```\\n```language\\ncode```\\n``` → Syntax highlighted code", false)
                        .addField("**Lists and Structure**", 
                                "• `• Bullet point` → Bullet lists\n" +
                                "• `1. Numbered item` → Numbered lists\n" +
                                "• `> Quote text` → Block quotes\n" +
                                "• `>>> Multi-line quote` → Multi-line quotes", false)
                        .addField("**Links and Mentions**", 
                                "• `[Link Text](https://example.com)` → Clickable links\n" +
                                "• `<@userid>` → User mentions\n" +
                                "• `<#channelid>` → Channel mentions\n" +
                                "• `<@&roleid>` → Role mentions", false)
                        .addField("**Special Characters**", 
                                "• `:emoji_name:` → Discord emojis\n" +
                                "• `<:name:id>` → Custom server emojis\n" +
                                "• `\\n` → Line breaks in descriptions\n" +
                                "• `\\*` → Escape special characters", false)
                        .addField("**Tips for Rules Embeds**", 
                                "• **Titles**: Only support plain text (no formatting)\n" +
                                "• **Descriptions & Footers**: Support all Discord markdown\n" +
                                "• Use **bold** for rule headers\n" +
                                "• Use `code blocks` for examples\n" +
                                "• Keep descriptions under 4096 characters\n" +
                                "• Use line breaks (\\n) for better readability\n" +
                                "• Test formatting before publishing\n" +
                                "• Bot will warn if you use formatting in titles", false)
                        .setColor(Color.YELLOW)
                        .setFooter(LocaleManager.getMessage(lang, "help.footer"));

                actionRow = ActionRow.of(
                        Button.secondary("help_home", LocaleManager.getMessage(lang, "button.home")),
                        Button.primary("help_overview", LocaleManager.getMessage(lang, "button.overview")),
                        Button.primary("help_systems", LocaleManager.getMessage(lang, "button.systems")),
                        Button.primary("help_setup", LocaleManager.getMessage(lang, "button.setup"))
                );
                actionRow2 = ActionRow.of(
                        Button.primary("help_commands", LocaleManager.getMessage(lang, "button.commands")),
                        Button.primary("help_support_developement", LocaleManager.getMessage(lang, "button.support")),
                        Button.primary("help_legal", LocaleManager.getMessage(lang, "button.legal"))
                );
                break;

            default:
                // Fallback to home page
                showHelpPage(slashEvent, buttonEvent, "home", lang);
                return;
        }

        // Send the response
        if (slashEvent != null) {
            slashEvent.replyEmbeds(embed.build()).addComponents(actionRow, actionRow2).setEphemeral(false).queue();
        } else if (buttonEvent != null) {
            buttonEvent.editMessageEmbeds(embed.build()).setComponents(actionRow, actionRow2).queue();
        }
    }
}
