package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.awt.*;

public class HelpCommandListener extends ListenerAdapter {

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("help")) {
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
        showHelpPage(event, null, "home");
    }

    private void handleHelpNavigation(ButtonInteractionEvent event, String customId) {
        // Extract page from custom ID (format: help_<page>)
        String page = customId.substring(5);
        showHelpPage(null, event, page);
    }

    private void showHelpPage(SlashCommandInteractionEvent slashEvent, ButtonInteractionEvent buttonEvent, String page) {
        EmbedBuilder embed = new EmbedBuilder();
        ActionRow actionRow;

        switch (page) {
            case "home":
                embed.setTitle("🤖 Sloth Bot - Help & Wiki")
                        .setDescription("Welcome to Sloth! I'm a comprehensive Discord moderation and management bot.\n\n" +
                                "**Available Help Sections:**\n" +
                                "🏠 **Overview** - Learn about Sloth's features\n" +
                                "⚙️ **Systems** - Available modular systems\n" +
                                "📋 **Setup** - How to configure systems\n" +
                                "📖 **Commands** - Complete command reference\n" +
                                "📜 **Legal** - Terms of Service and Privacy Policy")
                        .addField("📂 **Source Code**", "https://github.com/GamingToastEVE/Delta", false)
                        .setColor(Color.BLUE)
                        .setFooter("Use the buttons below to navigate");

                actionRow = ActionRow.of(
                        Button.primary("help_overview", "🏠 Overview"),
                        Button.primary("help_systems", "⚙️ Systems"),
                        Button.primary("help_setup", "📋 Setup"),
                        Button.primary("help_commands", "📖 Commands"),
                        Button.primary("help_legal", "📜 Legal")
                );
                break;

            case "overview":
                embed.setTitle("🏠 Sloth Bot - Overview")
                        .setDescription("Sloth is designed to help server administrators manage their communities effectively.\n\n" +
                                "**Key Features:**\n" +
                                "• **Modular Design** - Only activate the systems you need\n" +
                                "• **Comprehensive Logging** - Track all server activities\n" +
                                "• **Advanced Moderation** - Powerful tools for maintaining order\n" +
                                "• **Ticket System** - Professional support channel management\n" +
                                "• **Statistics Tracking** - Monitor server engagement\n\n" +
                                "**Getting Started:**\n" +
                                "1. Use `/add-system` to activate desired systems\n" +
                                "2. Configure each system using setup commands\n" +
                                "3. Start managing your server more effectively!")
                        .setColor(Color.GREEN)
                        .setFooter("Navigate using buttons below");

                actionRow = ActionRow.of(
                        Button.secondary("help_home", "🏠 Home"),
                        Button.primary("help_systems", "⚙️ Systems"),
                        Button.primary("help_setup", "📋 Setup"),
                        Button.primary("help_commands", "📖 Commands"),
                        Button.primary("help_legal", "📜 Legal")
                );
                break;

            case "systems":
                embed.setTitle("⚙️ Available Systems")
                        .setDescription("Sloth offers several modular systems that can be independently activated:\n\n")
                        .addField("🛡️ **Moderation System**", 
                                "• Kick, ban, timeout users\n" +
                                "• Message purging and slowmode\n" +
                                "• Comprehensive moderation logging", false)
                        .addField("⚠️ **Warning System**", 
                                "• Issue warnings with severity levels\n" +
                                "• Automatic actions on thresholds\n" +
                                "• Warning history tracking", false)
                        .addField("🎫 **Ticket System**", 
                                "• Professional support channels\n" +
                                "• Staff assignment and priorities\n" +
                                "• Transcript generation", false)
                        .addField("📝 **Log Channel System**", 
                                "• Dedicated logging channels\n" +
                                "• Track server events\n" +
                                "• Comprehensive audit trail", false)
                        .addField("📊 **Statistics System**", 
                                "• Server activity tracking\n" +
                                "• Daily and weekly reports\n" +
                                "• Engagement metrics", false)
                        .setColor(Color.ORANGE)
                        .setFooter("Use /add-system to activate any system");

                actionRow = ActionRow.of(
                        Button.secondary("help_home", "🏠 Home"),
                        Button.primary("help_overview", "🏠 Overview"),
                        Button.primary("help_setup", "📋 Setup"),
                        Button.primary("help_commands", "📖 Commands"),
                        Button.primary("help_legal", "📜 Legal")
                );
                break;

            case "setup":
                embed.setTitle("📋 System Setup Guide")
                        .setDescription("Follow these steps to configure Sloth for your server:\n\n")
                        .addField("**Step 1: Activate Systems**", 
                                "Use `/add-system` to activate the systems you want:\n" +
                                "• Choose from: Log Channel, Warning, Ticket, Moderation\n" +
                                "• Each system adds its own commands", false)
                        .addField("**Step 2: Configure Systems**", 
                                "**Log Channel:** `/set-log-channel #channel`\n" +
                                "**Warning System:** `/set-warn-settings`\n" +
                                "**Ticket System:** `/ticket-setup`\n" +
                                "**Moderation:** Ready to use after activation!", false)
                        .addField("**Step 3: Create Panels (Optional)**", 
                                "**Ticket Panel:** `/ticket-panel` - Creates user-friendly ticket creation\n" +
                                "Place in a public channel for easy access", false)
                        .addField("**Step 4: Set Permissions**", 
                                "• Ensure staff have appropriate Discord permissions\n" +
                                "• Bot needs Admin permissions for full functionality\n" +
                                "• Configure role-based access for tickets", false)
                        .setColor(Color.CYAN)
                        .setFooter("Need help? Create a support ticket!");

                actionRow = ActionRow.of(
                        Button.secondary("help_home", "🏠 Home"),
                        Button.primary("help_overview", "🏠 Overview"),
                        Button.primary("help_systems", "⚙️ Systems"),
                        Button.primary("help_commands", "📖 Commands"),
                        Button.primary("help_legal", "📜 Legal")
                );
                break;

            case "commands":
                embed.setTitle("📖 Command Reference")
                        .setDescription("Complete list of available commands by system:\n\n")
                        .addField("**System Management**", 
                                "`/add-system` - Activate a system for your server\n" +
                                "`/help` - Show this help system", false)
                        .addField("**Log Channel System**", 
                                "`/set-log-channel` - Configure logging channel\n" +
                                "`/get-log-channel` - View current log channel", false)
                        .addField("**Warning System**", 
                                "`/warn` - Issue warning to user\n" +
                                "`/set-warn-settings` - Configure warning thresholds\n" +
                                "`/get-warn-settings` - View warning configuration", false)
                        .addField("**Ticket System**", 
                                "`/ticket-setup` - Configure ticket system\n" +
                                "`/ticket-panel` - Create ticket creation panel\n" +
                                "`/close-ticket` - Close current ticket\n" +
                                "`/assign-ticket` - Assign to staff member\n" +
                                "`/ticket-info` - Get ticket information\n" +
                                "`/ticket-transcript` - Generate transcript", false)
                        .addField("**Moderation System**", 
                                "`/kick` `/ban` `/unban` - User management\n" +
                                "`/timeout` `/untimeout` - Temporary restrictions\n" +
                                "`/purge` - Delete multiple messages\n" +
                                "`/slowmode` - Set channel slowmode", false)
                        .addField("**Statistics System**", 
                                "`/stats-today` - Today's server statistics\n" +
                                "`/stats-week` - Weekly statistics\n" +
                                "`/stats-date` - Statistics for specific date", false)
                        .setColor(Color.MAGENTA)
                        .setFooter("All commands require appropriate permissions");

                actionRow = ActionRow.of(
                        Button.secondary("help_home", "🏠 Home"),
                        Button.primary("help_overview", "🏠 Overview"),
                        Button.primary("help_systems", "⚙️ Systems"),
                        Button.primary("help_setup", "📋 Setup"),
                        Button.primary("help_legal", "📜 Legal")
                );
                break;

            case "legal":
                embed.setTitle("📜 Legal Information")
                        .setDescription("Important legal documents and policies for using Sloth Bot:\n\n")
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
                        .setFooter("Last updated: 30.8.25 • Navigate using buttons below");

                actionRow = ActionRow.of(
                        Button.secondary("help_home", "🏠 Home"),
                        Button.primary("help_overview", "🏠 Overview"),
                        Button.primary("help_systems", "⚙️ Systems"),
                        Button.primary("help_setup", "📋 Setup"),
                        Button.primary("help_commands", "📖 Commands")
                );
                break;

            default:
                // Fallback to home page
                showHelpPage(slashEvent, buttonEvent, "home");
                return;
        }

        // Send the response
        if (slashEvent != null) {
            slashEvent.replyEmbeds(embed.build()).addComponents(actionRow).setEphemeral(false).queue();
        } else if (buttonEvent != null) {
            buttonEvent.editMessageEmbeds(embed.build()).setComponents(actionRow).queue();
        }
    }
}