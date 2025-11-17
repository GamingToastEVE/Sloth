package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EmbedEditorCommandListener extends ListenerAdapter {
    
    private final DatabaseHandler handler;
    private final Map<String, String> embedCreationContext = new HashMap<>();
    
    public EmbedEditorCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("❌ This command can only be used in a server!").setEphemeral(true).queue();
            return;
        }
        
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need the **Manage Server** permission to use this command!").setEphemeral(true).queue();
            return;
        }
        
        String guildId = event.getGuild().getId();
        
        switch (event.getName()) {
            case "create-embed":
                handler.insertOrUpdateGlobalStatistic("create-embed");
                handleCreateEmbed(event, guildId);
                break;
            case "edit-embed":
                handler.insertOrUpdateGlobalStatistic("edit-embed");
                handleEditEmbed(event, guildId);
                break;
            case "set-embed-author":
                handler.insertOrUpdateGlobalStatistic("set-embed-author");
                handleSetAuthor(event, guildId);
                break;
            case "set-embed-image":
                handler.insertOrUpdateGlobalStatistic("set-embed-image");
                handleSetImage(event, guildId);
                break;
            case "set-embed-thumbnail":
                handler.insertOrUpdateGlobalStatistic("set-embed-thumbnail");
                handleSetThumbnail(event, guildId);
                break;
            case "set-embed-timestamp":
                handler.insertOrUpdateGlobalStatistic("set-embed-timestamp");
                handleSetTimestamp(event, guildId);
                break;
            case "preview-embed":
                handler.insertOrUpdateGlobalStatistic("preview-embed");
                handlePreviewEmbed(event, guildId);
                break;
            case "send-embed":
                handler.insertOrUpdateGlobalStatistic("send-embed");
                handleSendEmbed(event, guildId);
                break;
            case "list-embeds":
                handler.insertOrUpdateGlobalStatistic("list-embeds");
                handleListEmbeds(event, guildId);
                break;
            case "delete-embed":
                handler.insertOrUpdateGlobalStatistic("delete-embed");
                handleDeleteEmbed(event, guildId);
                break;
            default:
                break;
        }
    }
    
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();
        
        if (modalId.equals("create_embed_basic")) {
            handleCreateEmbedBasicModal(event);
        } else if (modalId.equals("create_embed_advanced")) {
            handleCreateEmbedAdvancedModal(event);
        } else if (modalId.equals("edit_embed_basic")) {
            handleEditEmbedBasicModal(event);
        } else if (modalId.equals("edit_embed_advanced")) {
            handleEditEmbedAdvancedModal(event);
        }
    }
    
    private void handleCreateEmbed(SlashCommandInteractionEvent event, String guildId) {
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();
        
        // Check if embed already exists
        if (handler.embedExists(guildId, name)) {
            event.reply("❌ An embed with the name `" + name + "` already exists! Use `/edit-embed` to modify it.").setEphemeral(true).queue();
            return;
        }
        
        // Store context for modal
        embedCreationContext.put(event.getUser().getId() + "_name", name);
        embedCreationContext.put(event.getUser().getId() + "_guild", guildId);
        
        // Show modal for basic information
        TextInput title = TextInput.create("title", "Title", TextInputStyle.SHORT)
                .setPlaceholder("Enter embed title (optional)")
                .setRequired(false)
                .setMaxLength(256)
                .build();
        
        TextInput description = TextInput.create("description", "Description", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Enter embed description")
                .setRequired(true)
                .setMaxLength(4000)
                .build();
        
        TextInput color = TextInput.create("color", "Color", TextInputStyle.SHORT)
                .setPlaceholder("blue, red, green, #FF0000, etc.")
                .setRequired(false)
                .setMaxLength(32)
                .setValue("blue")
                .build();
        
        TextInput footer = TextInput.create("footer", "Footer", TextInputStyle.SHORT)
                .setPlaceholder("Enter footer text (optional)")
                .setRequired(false)
                .setMaxLength(2048)
                .build();
        
        Modal modal = Modal.create("create_embed_basic", "Create Embed: " + name)
                .addActionRows(
                        ActionRow.of(title),
                        ActionRow.of(description),
                        ActionRow.of(color),
                        ActionRow.of(footer)
                )
                .build();
        
        event.replyModal(modal).queue();
    }
    
    private void handleCreateEmbedBasicModal(ModalInteractionEvent event) {
        String userId = event.getUser().getId();
        String name = embedCreationContext.get(userId + "_name");
        String guildId = embedCreationContext.get(userId + "_guild");
        
        if (name == null || guildId == null) {
            event.reply("❌ Error: Context lost. Please try again.").setEphemeral(true).queue();
            return;
        }
        
        String title = event.getValue("title") != null ? event.getValue("title").getAsString() : null;
        String description = Objects.requireNonNull(event.getValue("description")).getAsString();
        String color = event.getValue("color") != null ? event.getValue("color").getAsString() : "blue";
        String footer = event.getValue("footer") != null ? event.getValue("footer").getAsString() : null;
        
        // Create embed with basic info
        boolean success = handler.createEmbed(guildId, name, title, description, footer, null, 
                                             color, null, null, null, null, null, "[]", false);
        
        // Clean up context
        embedCreationContext.remove(userId + "_name");
        embedCreationContext.remove(userId + "_guild");
        
        if (success) {
            // Show preview
            DatabaseHandler.EmbedData embedData = handler.getEmbed(guildId, name);
            if (embedData != null) {
                event.reply("✅ Successfully created embed `" + name + "`!\n\n**Preview:**")
                     .addEmbeds(embedData.toEmbedBuilder().build())
                     .setEphemeral(true)
                     .queue();
            } else {
                event.reply("✅ Successfully created embed `" + name + "`!").setEphemeral(true).queue();
            }
        } else {
            event.reply("❌ Failed to create embed. Please try again.").setEphemeral(true).queue();
        }
    }
    
    private void handleCreateEmbedAdvancedModal(ModalInteractionEvent event) {
        // Placeholder for future advanced options
        event.reply("This feature is not yet implemented.").setEphemeral(true).queue();
    }
    
    private void handleEditEmbed(SlashCommandInteractionEvent event, String guildId) {
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();
        
        // Check if embed exists
        DatabaseHandler.EmbedData embedData = handler.getEmbed(guildId, name);
        if (embedData == null) {
            event.reply("❌ No embed found with the name `" + name + "`. Use `/create-embed` to create one.").setEphemeral(true).queue();
            return;
        }
        
        // Store context for modal
        embedCreationContext.put(event.getUser().getId() + "_name", name);
        embedCreationContext.put(event.getUser().getId() + "_guild", guildId);
        
        // Show modal pre-filled with existing data
        TextInput title = TextInput.create("title", "Title", TextInputStyle.SHORT)
                .setPlaceholder("Enter embed title (optional)")
                .setRequired(false)
                .setMaxLength(256)
                .setValue(embedData.title != null ? embedData.title : "")
                .build();
        
        TextInput description = TextInput.create("description", "Description", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Enter embed description")
                .setRequired(true)
                .setMaxLength(4000)
                .setValue(embedData.description != null ? embedData.description : "")
                .build();
        
        TextInput color = TextInput.create("color", "Color", TextInputStyle.SHORT)
                .setPlaceholder("blue, red, green, #FF0000, etc.")
                .setRequired(false)
                .setMaxLength(32)
                .setValue(embedData.color != null ? embedData.color : "blue")
                .build();
        
        TextInput footer = TextInput.create("footer", "Footer", TextInputStyle.SHORT)
                .setPlaceholder("Enter footer text (optional)")
                .setRequired(false)
                .setMaxLength(2048)
                .setValue(embedData.footer != null ? embedData.footer : "")
                .build();
        
        Modal modal = Modal.create("edit_embed_basic", "Edit Embed: " + name)
                .addActionRows(
                        ActionRow.of(title),
                        ActionRow.of(description),
                        ActionRow.of(color),
                        ActionRow.of(footer)
                )
                .build();
        
        event.replyModal(modal).queue();
    }
    
    private void handleEditEmbedBasicModal(ModalInteractionEvent event) {
        String userId = event.getUser().getId();
        String name = embedCreationContext.get(userId + "_name");
        String guildId = embedCreationContext.get(userId + "_guild");
        
        if (name == null || guildId == null) {
            event.reply("❌ Error: Context lost. Please try again.").setEphemeral(true).queue();
            return;
        }
        
        String title = event.getValue("title") != null ? event.getValue("title").getAsString() : null;
        String description = Objects.requireNonNull(event.getValue("description")).getAsString();
        String color = event.getValue("color") != null ? event.getValue("color").getAsString() : "blue";
        String footer = event.getValue("footer") != null ? event.getValue("footer").getAsString() : null;
        
        // Get existing embed to preserve advanced fields
        DatabaseHandler.EmbedData existingEmbed = handler.getEmbed(guildId, name);
        
        // Update embed
        boolean success = handler.updateEmbed(guildId, name, title, description, footer, 
                                             existingEmbed != null ? existingEmbed.footerIconUrl : null,
                                             color,
                                             existingEmbed != null ? existingEmbed.authorName : null,
                                             existingEmbed != null ? existingEmbed.authorUrl : null,
                                             existingEmbed != null ? existingEmbed.authorIconUrl : null,
                                             existingEmbed != null ? existingEmbed.thumbnailUrl : null,
                                             existingEmbed != null ? existingEmbed.imageUrl : null,
                                             existingEmbed != null ? existingEmbed.fieldsJson : "[]",
                                             existingEmbed != null && existingEmbed.timestamp);
        
        // Clean up context
        embedCreationContext.remove(userId + "_name");
        embedCreationContext.remove(userId + "_guild");
        
        if (success) {
            // Show preview
            DatabaseHandler.EmbedData embedData = handler.getEmbed(guildId, name);
            if (embedData != null) {
                event.reply("✅ Successfully updated embed `" + name + "`!\n\n**Preview:**")
                     .addEmbeds(embedData.toEmbedBuilder().build())
                     .setEphemeral(true)
                     .queue();
            } else {
                event.reply("✅ Successfully updated embed `" + name + "`!").setEphemeral(true).queue();
            }
        } else {
            event.reply("❌ Failed to update embed. Please try again.").setEphemeral(true).queue();
        }
    }
    
    private void handleEditEmbedAdvancedModal(ModalInteractionEvent event) {
        // Placeholder for future advanced options
        event.reply("This feature is not yet implemented.").setEphemeral(true).queue();
    }
    
    private void handlePreviewEmbed(SlashCommandInteractionEvent event, String guildId) {
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();
        
        DatabaseHandler.EmbedData embedData = handler.getEmbed(guildId, name);
        if (embedData == null) {
            event.reply("❌ No embed found with the name `" + name + "`.").setEphemeral(true).queue();
            return;
        }
        
        EmbedBuilder preview = embedData.toEmbedBuilder();
        event.reply("**Preview of embed `" + name + "`:**")
             .addEmbeds(preview.build())
             .setEphemeral(true)
             .queue();
    }
    
    private void handleSendEmbed(SlashCommandInteractionEvent event, String guildId) {
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();
        TextChannel channel = event.getOption("channel") != null 
            ? event.getOption("channel").getAsChannel().asTextChannel()
            : event.getChannel().asTextChannel();
        
        DatabaseHandler.EmbedData embedData = handler.getEmbed(guildId, name);
        if (embedData == null) {
            event.reply("❌ No embed found with the name `" + name + "`.").setEphemeral(true).queue();
            return;
        }
        
        channel.sendMessageEmbeds(embedData.toEmbedBuilder().build()).queue(
            success -> {
                event.reply("✅ Successfully sent embed `" + name + "` to " + channel.getAsMention() + "!")
                     .setEphemeral(true)
                     .queue();
            },
            error -> {
                event.reply("❌ Failed to send embed. I may not have permission to send messages in that channel.")
                     .setEphemeral(true)
                     .queue();
            }
        );
    }
    
    private void handleListEmbeds(SlashCommandInteractionEvent event, String guildId) {
        List<DatabaseHandler.EmbedData> embeds = handler.getAllEmbeds(guildId);
        
        if (embeds.isEmpty()) {
            event.reply("❌ No embeds found! Use `/create-embed` to create one.").setEphemeral(true).queue();
            return;
        }
        
        StringBuilder embedList = new StringBuilder("📋 **Saved Embeds:**\n\n");
        for (DatabaseHandler.EmbedData embed : embeds) {
            embedList.append("• **").append(embed.name).append("**");
            if (embed.title != null && !embed.title.isEmpty()) {
                embedList.append(" - ").append(embed.title);
            }
            embedList.append("\n");
        }
        
        embedList.append("\nUse `/preview-embed` to preview an embed or `/send-embed` to send it to a channel.");
        
        event.reply(embedList.toString()).setEphemeral(true).queue();
    }
    
    private void handleDeleteEmbed(SlashCommandInteractionEvent event, String guildId) {
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();
        
        if (!handler.embedExists(guildId, name)) {
            event.reply("❌ No embed found with the name `" + name + "`.").setEphemeral(true).queue();
            return;
        }
        
        boolean success = handler.deleteEmbed(guildId, name);
        if (success) {
            event.reply("✅ Successfully deleted embed `" + name + "`!").setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to delete embed. Please try again.").setEphemeral(true).queue();
        }
    }
    
    private void handleSetAuthor(SlashCommandInteractionEvent event, String guildId) {
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();
        String authorName = Objects.requireNonNull(event.getOption("author-name")).getAsString();
        String authorUrl = event.getOption("author-url") != null ? event.getOption("author-url").getAsString() : null;
        String authorIconUrl = event.getOption("author-icon-url") != null ? event.getOption("author-icon-url").getAsString() : null;
        
        DatabaseHandler.EmbedData embedData = handler.getEmbed(guildId, name);
        if (embedData == null) {
            event.reply("❌ No embed found with the name `" + name + "`. Use `/create-embed` to create one.").setEphemeral(true).queue();
            return;
        }
        
        boolean success = handler.updateEmbed(guildId, name, embedData.title, embedData.description, 
                                             embedData.footer, embedData.footerIconUrl, embedData.color,
                                             authorName, authorUrl, authorIconUrl,
                                             embedData.thumbnailUrl, embedData.imageUrl, 
                                             embedData.fieldsJson, embedData.timestamp);
        
        if (success) {
            DatabaseHandler.EmbedData updatedEmbed = handler.getEmbed(guildId, name);
            event.reply("✅ Successfully set author for embed `" + name + "`!\n\n**Preview:**")
                 .addEmbeds(updatedEmbed.toEmbedBuilder().build())
                 .setEphemeral(true)
                 .queue();
        } else {
            event.reply("❌ Failed to update embed. Please try again.").setEphemeral(true).queue();
        }
    }
    
    private void handleSetImage(SlashCommandInteractionEvent event, String guildId) {
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();
        String imageUrl = Objects.requireNonNull(event.getOption("image-url")).getAsString();
        
        DatabaseHandler.EmbedData embedData = handler.getEmbed(guildId, name);
        if (embedData == null) {
            event.reply("❌ No embed found with the name `" + name + "`. Use `/create-embed` to create one.").setEphemeral(true).queue();
            return;
        }
        
        boolean success = handler.updateEmbed(guildId, name, embedData.title, embedData.description, 
                                             embedData.footer, embedData.footerIconUrl, embedData.color,
                                             embedData.authorName, embedData.authorUrl, embedData.authorIconUrl,
                                             embedData.thumbnailUrl, imageUrl, 
                                             embedData.fieldsJson, embedData.timestamp);
        
        if (success) {
            DatabaseHandler.EmbedData updatedEmbed = handler.getEmbed(guildId, name);
            event.reply("✅ Successfully set image for embed `" + name + "`!\n\n**Preview:**")
                 .addEmbeds(updatedEmbed.toEmbedBuilder().build())
                 .setEphemeral(true)
                 .queue();
        } else {
            event.reply("❌ Failed to update embed. Please try again.").setEphemeral(true).queue();
        }
    }
    
    private void handleSetThumbnail(SlashCommandInteractionEvent event, String guildId) {
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();
        String thumbnailUrl = Objects.requireNonNull(event.getOption("thumbnail-url")).getAsString();
        
        DatabaseHandler.EmbedData embedData = handler.getEmbed(guildId, name);
        if (embedData == null) {
            event.reply("❌ No embed found with the name `" + name + "`. Use `/create-embed` to create one.").setEphemeral(true).queue();
            return;
        }
        
        boolean success = handler.updateEmbed(guildId, name, embedData.title, embedData.description, 
                                             embedData.footer, embedData.footerIconUrl, embedData.color,
                                             embedData.authorName, embedData.authorUrl, embedData.authorIconUrl,
                                             thumbnailUrl, embedData.imageUrl, 
                                             embedData.fieldsJson, embedData.timestamp);
        
        if (success) {
            DatabaseHandler.EmbedData updatedEmbed = handler.getEmbed(guildId, name);
            event.reply("✅ Successfully set thumbnail for embed `" + name + "`!\n\n**Preview:**")
                 .addEmbeds(updatedEmbed.toEmbedBuilder().build())
                 .setEphemeral(true)
                 .queue();
        } else {
            event.reply("❌ Failed to update embed. Please try again.").setEphemeral(true).queue();
        }
    }
    
    private void handleSetTimestamp(SlashCommandInteractionEvent event, String guildId) {
        String name = Objects.requireNonNull(event.getOption("name")).getAsString();
        boolean enabled = Objects.requireNonNull(event.getOption("enabled")).getAsBoolean();
        
        DatabaseHandler.EmbedData embedData = handler.getEmbed(guildId, name);
        if (embedData == null) {
            event.reply("❌ No embed found with the name `" + name + "`. Use `/create-embed` to create one.").setEphemeral(true).queue();
            return;
        }
        
        boolean success = handler.updateEmbed(guildId, name, embedData.title, embedData.description, 
                                             embedData.footer, embedData.footerIconUrl, embedData.color,
                                             embedData.authorName, embedData.authorUrl, embedData.authorIconUrl,
                                             embedData.thumbnailUrl, embedData.imageUrl, 
                                             embedData.fieldsJson, enabled);
        
        if (success) {
            DatabaseHandler.EmbedData updatedEmbed = handler.getEmbed(guildId, name);
            event.reply("✅ Successfully " + (enabled ? "enabled" : "disabled") + " timestamp for embed `" + name + "`!\n\n**Preview:**")
                 .addEmbeds(updatedEmbed.toEmbedBuilder().build())
                 .setEphemeral(true)
                 .queue();
        } else {
            event.reply("❌ Failed to update embed. Please try again.").setEphemeral(true).queue();
        }
    }
}
