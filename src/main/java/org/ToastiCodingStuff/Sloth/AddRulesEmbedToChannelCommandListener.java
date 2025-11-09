package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class AddRulesEmbedToChannelCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;
    private HashMap<String, String> guildAndRoleIDs = new HashMap<>();

    public AddRulesEmbedToChannelCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    /**
     * Provides helpful information about Discord markdown formatting
     */
    private String getFormattingHelpText() {
        return "\n\n**ℹ️ Formatting Guide:**\n" +
               "• **bold text** - Use `**text**`\n" +
               "• *italic text* - Use `*text*`\n" +
               "• __underlined text__ - Use `__text__`\n" +
               "• ~~strikethrough~~ - Use `~~text~~`\n" +
               "• `inline code` - Use `` `text` ``\n" +
               "• [links](https://example.com) - Use `[text](url)`\n" +
               "\n*Note: Titles only support plain text, but descriptions and footers support all formatting.*";
    }

    /**
     * Validates if the text contains formatting characters and provides feedback
     */
    private String validateTextWithFormatting(String text, String fieldName, int maxLength) {
        if (text.length() > maxLength) {
            return "❌ " + fieldName + " must be " + maxLength + " characters or less!";
        }
        
        // Check if title contains formatting characters (since titles don't support formatting)
        if ("Title".equals(fieldName) && containsFormattingCharacters(text)) {
            return "⚠️ " + fieldName + " contains formatting characters. " +
                   "Discord embed titles only support plain text. " +
                   "Consider moving formatting to the description.";
        }
        
        return null; // No validation errors
    }

    /**
     * Checks if text contains Discord markdown formatting characters
     */
    private boolean containsFormattingCharacters(String text) {
        return text.contains("**") || text.contains("*") || text.contains("__") || 
               text.contains("~~") || text.contains("`") || text.contains("[");
    }

    @Override
    public void onModalInteraction (ModalInteractionEvent event) {
        // Get required parameters
        event.getModalId();
        if (!event.getModalId().equals("rules_modal_creator")) {
            return; // Not our modal
        }

        if (event.getGuild() == null) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }
        String title = Objects.requireNonNull(event.getValue("rules_title")).getAsString();
        String description = Objects.requireNonNull(event.getValue("rules_description")).getAsString();
        Role mentionRole = event.getGuild().getRoleById(guildAndRoleIDs.get(event.getGuild().getId()));

        // Get optional parameters
        String buttonEmoji = "✅";
        if (event.getValue("button_emoji") != null) {
            buttonEmoji = event.getValue("button_emoji").getAsString();
        }

        String buttonLabel = "Verify";
        if (event.getValue("button_label") != null) {
            buttonLabel = event.getValue("button_label").getAsString();
        }

        String color = "green";
        if (event.getValue("color") != null) {
            color = event.getValue("color").getAsString();
        }

        String footer = null;
        if (event.getValue("footer") != null) {
            footer = event.getValue("footer").getAsString();
        }

        // Validate inputs with formatting awareness
        String titleValidation = validateTextWithFormatting(title, "Title", 256);
        if (titleValidation != null) {
            event.reply(titleValidation + getFormattingHelpText()).setEphemeral(true).queue();
            return;
        }

        String descriptionValidation = validateTextWithFormatting(description, "Description", 4096);
        if (descriptionValidation != null) {
            event.reply(descriptionValidation).setEphemeral(true).queue();
            return;
        }

        if (buttonLabel.length() > 80) {
            event.reply("❌ Button label must be 80 characters or less!").setEphemeral(true).queue();
            return;
        }

        // Validate footer if provided
        if (footer != null && !footer.isEmpty()) {
            String footerValidation = validateTextWithFormatting(footer, "Footer", 2048);
            if (footerValidation != null) {
                event.reply(footerValidation).setEphemeral(true).queue();
                return;
            }
        }

        // Add to database
        boolean success = handler.addRulesEmbedToDatabase(
                Objects.requireNonNull(event.getGuild()).getId(),
                title,
                description,
                footer,
                color,
                mentionRole.getId(),
                buttonLabel,
                buttonEmoji
        );

        if (success) {
            String successMessage = "✅ Successfully added rules embed to the database!\n" +
                    "📋 **Title:** " + title + "\n" +
                    "🎭 **Role:** " + mentionRole.getAsMention() + "\n" +
                    "🔘 **Button:** " + buttonLabel + "\n" +
                    "Use `/setup-rules` in a channel to display the rules with verification buttons.";

            // Add formatting info if description or footer contains formatting
            if (containsFormattingCharacters(description) ||
                    (footer != null && containsFormattingCharacters(footer))) {
                successMessage += "\n\n✨ **Formatting detected!** Your embed will display with Discord markdown formatting.";
            }

            event.reply(successMessage).setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to add rules embed to database. Please try again or contact an administrator.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        switch (event.getName()) {
            case "add-rules-embed":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("add-rules-embed");
                handleAddRulesEmbedCommand(event, guildId);
                break;
            case "setup-rules":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("setup-rules");
                handleSetupRulesCommand(event, guildId);
                break;
            case "remove-rules-embed":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("remove-rules-embed");
                handleRemoveEmbedCommand(event, guildId);
                break;
            case "list-rules-embeds":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("list-rules-embeds");
                List<DatabaseHandler.RulesEmbedData> embeds = handler.getAllRulesEmbedDataFromDatabase(guildId);
                if (embeds.isEmpty()) {
                    event.reply("❌ No rules embeds found in the database! Use `/add-rules-embed` to create some first.").setEphemeral(true).queue();
                    return;
                }

                StringBuilder embedList = new StringBuilder("📋 **Current Rules Embeds in Database:**\n");
                for (DatabaseHandler.RulesEmbedData embedData : embeds) {
                    embedList.append("• ID: ").append(embedData.id)
                             .append(" | Title: ").append(embedData.title)
                             .append(" | Role ID: ").append(embedData.roleId)
                             .append("\n");
                }
                event.reply(embedList.toString()).setEphemeral(true).queue();
                break;
            default:
                break;
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String customId = event.getComponentId();
        
        if (customId.startsWith("rules_verify_")) {
            handleRulesVerificationButton(event, customId);
        }
    }

    private void handleAddRulesEmbedCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has permission to manage server
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need the **Manage Server** permission to use this command!").setEphemeral(true).queue();
            return;
        }

        // Check if guild already has maximum embeds
        if (handler.getNumberOfEmbedsInDataBase(guildId) >= 3) {
            event.reply("❌ This server already has the maximum number of rules embeds (3). Please remove one before adding a new one.").setEphemeral(true).queue();
            return;
        }

        if (event.getOption("role_to_give") == null) {
            event.reply("❌ You must specify a role to assign upon verification!").setEphemeral(true).queue();
            return;
        }
        String roleID = Objects.requireNonNull(event.getOption("role_to_give")).getAsString();
        setGuildAndRoleIDs(event.getGuild().getId(), roleID);

        TextInput title = TextInput.create("rules_title", "Title", TextInputStyle.SHORT)
                .setPlaceholder("Server Rules")
                .setRequired(true)
                .setMaxLength(256)
                .build();
        TextInput description = TextInput.create("rules_description", "Description", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Here you can type in the rules and use formatting!")
                .setRequired(true)
                .setMaxLength(4000)
                .build();

        TextInput footer = TextInput.create("rules_footer", "Footer", TextInputStyle.SHORT)
                .setPlaceholder("Footer text (optional)")
                .setRequired(false)
                .setMaxLength(2048)
                .build();

        TextInput buttonLabel = TextInput.create("rules_button_label", "Button Label", TextInputStyle.SHORT)
                .setPlaceholder("Verify")
                .setRequired(false)
                .setMaxLength(80)
                .build();

        TextInput buttonEmoji = TextInput.create("rules_button_emoji", "Button Emoji", TextInputStyle.SHORT)
                .setPlaceholder("✅")
                .setRequired(false)
                .setMaxLength(32)
                .build();

        Modal rulesModal = Modal.create("rules_modal_creator", "Create Custom Rules Embed")
                .addActionRows(
                        ActionRow.of(title),
                        ActionRow.of(description),
                        ActionRow.of(footer),
                        ActionRow.of(buttonLabel),
                        ActionRow.of(buttonEmoji)
                )
                .build();

        event.replyModal(rulesModal).queue();
    }

    private void handleSetupRulesCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has permission to manage server
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need the **Manage Server** permission to use this command!").setEphemeral(true).queue();
            return;
        }

        // Get rules embeds from database
        ArrayList<DatabaseHandler.RulesEmbedData> embedDataList = handler.getAllRulesEmbedDataFromDatabase(guildId);
        
        if (embedDataList.isEmpty()) {
            event.reply("❌ No rules embeds found in the database! Use `/add-rules-embed` to create some first.").setEphemeral(true).queue();
            return;
        }

        // Send each embed with its verification button
        for (int i = 0; i < embedDataList.size(); i++) {
            // Create button
            Button verifyButton;
            if (i == embedDataList.size() - 1) {
                if (embedDataList.get(i).buttonEmoji != null && !embedDataList.get(i).buttonEmoji.isEmpty()) {
                    try {
                        // Try to parse as custom emoji or unicode emoji
                        Emoji emoji = Emoji.fromFormatted(embedDataList.get(i).buttonEmoji);
                        verifyButton = Button.primary("rules_verify_" + embedDataList.get(i).id, embedDataList.get(i).buttonLabel).withEmoji(emoji);
                    } catch (Exception e) {
                        // If emoji parsing fails, create button without emoji
                        verifyButton = Button.primary("rules_verify_" + embedDataList.get(i).id, embedDataList.get(i).buttonLabel);
                    }
                } else {
                    verifyButton = Button.primary("rules_verify_" + embedDataList.get(i).id, embedDataList.get(i).buttonLabel);
                }

                // Send embed with button
                event.getChannel().sendMessageEmbeds(embedDataList.get(i).toEmbedBuilder().build())
                        .setComponents(ActionRow.of(verifyButton))
                        .queue();
            } else {
                // Send embed without button
                event.getChannel().sendMessageEmbeds(embedDataList.get(i).toEmbedBuilder().build()).queue();
            }

        }
        event.reply("✅ Successfully set up " + embedDataList.size() + " rules embed(s) in this channel!").setEphemeral(true).queue();
        //event.getHook().editOriginal("✅ Successfully set up " + embedDataList.size() + " rules embed(s) in this channel!").queue();
    }

    private void handleRemoveEmbedCommand(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has permission to manage server
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need the **Manage Server** permission to use this command!").setEphemeral(true).queue();
            return;
        }

        if (event.getOption("embed_id") == null) {
            event.reply("❌ You must specify the ID of the embed to remove!").setEphemeral(true).queue();
            return;
        }
        String embedId = Objects.requireNonNull(event.getOption("embed_id")).getAsString();

        boolean success = handler.removeRulesEmbedFromDatabase(guildId, embedId);
        if (success) {
            event.reply("✅ Successfully removed the rules embed with ID " + embedId + " from the database!").setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to remove the rules embed. Please ensure the ID is correct and try again.").setEphemeral(true).queue();
        }
    }

    private void handleRulesVerificationButton(ButtonInteractionEvent event, String customId) {
        try {
            // Extract embed ID from custom ID (format: rules_verify_<id>)
            String embedId = handler.getRoleIDFromRulesEmbed(event.getGuild().getId());
            System.out.println("Extracted embed ID: " + embedId);
            
            // Get the role
            Role verificationRole = Objects.requireNonNull(event.getGuild()).getRoleById(embedId);
            if (verificationRole == null) {
                event.reply("❌ Error: The verification role no longer exists. Please contact an administrator.").setEphemeral(true).queue();
                return;
            }
            
            Member member = event.getMember();
            if (member == null) {
                event.reply("❌ Error: Could not find your member information.").setEphemeral(true).queue();
                return;
            }
            
            // Check if user already has the role
            if (member.getRoles().contains(verificationRole)) {
                event.reply("✅ You already have the " + verificationRole.getName() + " role!").setEphemeral(true).queue();
                return;
            }
            
            // Add the role
            event.getGuild().addRoleToMember(member, verificationRole).queue(
                success -> {
                    // Track verification statistics
                    handler.incrementVerificationsPerformed(event.getGuild().getId());
                    event.reply("✅ Successfully verified! You have been given the " + verificationRole.getName() + " role.").setEphemeral(true).queue();
                },
                error -> {
                    event.reply("❌ Failed to assign role. The bot may not have permission to manage this role.").setEphemeral(true).queue();
                }
            );
            
        } catch (NumberFormatException e) {
            event.reply("❌ Error: Invalid button configuration.").setEphemeral(true).queue();
        } catch (Exception e) {
            event.reply("❌ An unexpected error occurred. Please try again later.").setEphemeral(true).queue();
            System.err.println("Error in rules verification button: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public HashMap<String, String> getGuildAndRoleIDs() {
        return guildAndRoleIDs;
    }

    public void setGuildAndRoleIDs(String guildId, String roleId) {
        HashMap<String, String> guildAndRoleIDs = getGuildAndRoleIDs();
        guildAndRoleIDs.put(guildId, roleId);
        this.guildAndRoleIDs = guildAndRoleIDs;
    }
}