package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.util.ArrayList;
import java.util.List;

public class AddRulesEmbedToChannelCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public AddRulesEmbedToChannelCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        switch (event.getName()) {
            case "add-rules-embed":
                handleAddRulesEmbedCommand(event, guildId);
                break;
            case "setup-rules":
                handleSetupRulesCommand(event, guildId);
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

        // Get required parameters
        String title = event.getOption("title").getAsString();
        String description = event.getOption("description").getAsString();
        Role mentionRole = event.getOption("mention_role").getAsRole();
        String buttonLabel = event.getOption("button_label").getAsString();

        // Get optional parameters
        String buttonEmoji = null;
        if (event.getOption("button_emoji") != null) {
            buttonEmoji = event.getOption("button_emoji").getAsString();
        }
        
        String color = "green";
        if (event.getOption("color") != null) {
            color = event.getOption("color").getAsString();
        }
        
        String footer = null;
        if (event.getOption("footer") != null) {
            footer = event.getOption("footer").getAsString();
        }

        // Validate inputs
        if (title.length() > 256) {
            event.reply("❌ Title must be 256 characters or less!").setEphemeral(true).queue();
            return;
        }
        
        if (description.length() > 4096) {
            event.reply("❌ Description must be 4096 characters or less!").setEphemeral(true).queue();
            return;
        }
        
        if (buttonLabel.length() > 80) {
            event.reply("❌ Button label must be 80 characters or less!").setEphemeral(true).queue();
            return;
        }

        // Add to database
        boolean success = handler.addRulesEmbedToDatabase(
            guildId, 
            title, 
            description, 
            footer, 
            color, 
            mentionRole.getId(), 
            buttonLabel, 
            buttonEmoji
        );

        if (success) {
            event.reply("✅ Successfully added rules embed to the database!\n" +
                       "📋 **Title:** " + title + "\n" +
                       "🎭 **Role:** " + mentionRole.getAsMention() + "\n" +
                       "🔘 **Button:** " + buttonLabel + "\n" +
                       "Use `/setup-rules` in a channel to display the rules with verification buttons.").setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to add rules embed to database. Please try again or contact an administrator.").setEphemeral(true).queue();
        }
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

        event.deferReply().queue();

        // Send each embed with its verification button
        for (DatabaseHandler.RulesEmbedData embedData : embedDataList) {
            // Create button
            Button verifyButton;
            if (embedData.buttonEmoji != null && !embedData.buttonEmoji.isEmpty()) {
                try {
                    // Try to parse as custom emoji or unicode emoji
                    Emoji emoji = Emoji.fromFormatted(embedData.buttonEmoji);
                    verifyButton = Button.primary("rules_verify_" + embedData.id, embedData.buttonLabel).withEmoji(emoji);
                } catch (Exception e) {
                    // If emoji parsing fails, create button without emoji
                    verifyButton = Button.primary("rules_verify_" + embedData.id, embedData.buttonLabel);
                }
            } else {
                verifyButton = Button.primary("rules_verify_" + embedData.id, embedData.buttonLabel);
            }

            // Send embed with button
            event.getHook().sendMessageEmbeds(embedData.toEmbedBuilder().build())
                 .setComponents(ActionRow.of(verifyButton))
                 .queue();
        }

        event.getHook().editOriginal("✅ Successfully set up " + embedDataList.size() + " rules embed(s) in this channel!").queue();
    }

    private void handleRulesVerificationButton(ButtonInteractionEvent event, String customId) {
        try {
            // Extract embed ID from custom ID (format: rules_verify_<id>)
            int embedId = Integer.parseInt(customId.substring(13));
            String guildId = event.getGuild().getId();
            
            // Get the specific embed data
            ArrayList<DatabaseHandler.RulesEmbedData> embedDataList = handler.getAllRulesEmbedDataFromDatabase(guildId);
            DatabaseHandler.RulesEmbedData targetEmbed = null;
            
            for (DatabaseHandler.RulesEmbedData embedData : embedDataList) {
                if (embedData.id == embedId) {
                    targetEmbed = embedData;
                    break;
                }
            }
            
            if (targetEmbed == null || targetEmbed.roleId == null) {
                event.reply("❌ Error: Could not find the associated role for this verification button.").setEphemeral(true).queue();
                return;
            }
            
            // Get the role
            Role verificationRole = event.getGuild().getRoleById(targetEmbed.roleId);
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
}