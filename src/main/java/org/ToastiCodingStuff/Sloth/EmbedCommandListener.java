package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class EmbedCommandListener extends ListenerAdapter {
    
    private final DatabaseHandler handler;

    public EmbedCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        switch (event.getName()) {
            case "embed-create":
                handleEmbedCreate(event, guildId);
                break;
            case "embed-send":
                handleEmbedSend(event, guildId);
                break;
            case "embed-list":
                handleEmbedList(event, guildId);
                break;
            case "embed-edit":
                handleEmbedEdit(event, guildId);
                break;
            case "embed-delete":
                handleEmbedDelete(event, guildId);
                break;
        }
    }

    private void handleEmbedCreate(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has manage messages permission
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to create embed templates.").setEphemeral(true).queue();
            return;
        }

        String name = event.getOption("name").getAsString();
        String title = event.getOption("title").getAsString();
        String description = event.getOption("description") != null ? event.getOption("description").getAsString() : null;
        String color = event.getOption("color") != null ? event.getOption("color").getAsString() : null;
        String footer = event.getOption("footer") != null ? event.getOption("footer").getAsString() : null;
        String userId = event.getUser().getId();

        // Validate color if provided
        if (color != null && !isValidHexColor(color)) {
            event.reply("❌ Invalid color format. Please use hex color format like #FF0000").setEphemeral(true).queue();
            return;
        }

        boolean success = handler.createEmbedTemplate(guildId, userId, name, title, description, color, footer);
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Embed Template Created")
                    .setDescription("Embed template **" + name + "** has been created successfully!")
                    .addField("Template Name", name, true)
                    .addField("Title", title, true)
                    .setColor(Color.GREEN)
                    .setTimestamp(java.time.Instant.now());
            
            if (description != null) {
                embed.addField("Description", description.length() > 100 ? description.substring(0, 100) + "..." : description, false);
            }
            
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to create embed template. A template with this name might already exist.").setEphemeral(true).queue();
        }
    }

    private void handleEmbedSend(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has manage messages permission
        if (!event.getMember().hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply("❌ You need Manage Messages permission to send embed templates.").setEphemeral(true).queue();
            return;
        }

        String name = event.getOption("name").getAsString();
        TextChannel targetChannel = event.getOption("channel") != null ? 
            event.getOption("channel").getAsChannel().asTextChannel() : 
            event.getChannel().asTextChannel();

        Map<String, String> template = handler.getEmbedTemplate(guildId, name);
        
        if (template == null) {
            event.reply("❌ Embed template **" + name + "** not found.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(template.get("title"));
        
        if (template.get("description") != null) {
            embed.setDescription(template.get("description"));
        }
        
        if (template.get("color") != null) {
            try {
                Color color = Color.decode(template.get("color"));
                embed.setColor(color);
            } catch (NumberFormatException e) {
                embed.setColor(Color.BLUE);
            }
        } else {
            embed.setColor(Color.BLUE);
        }
        
        if (template.get("footer") != null) {
            embed.setFooter(template.get("footer"));
        }
        
        embed.setTimestamp(java.time.Instant.now());

        targetChannel.sendMessageEmbeds(embed.build()).queue(
            success -> event.reply("✅ Embed sent successfully to " + targetChannel.getAsMention()).setEphemeral(true).queue(),
            error -> event.reply("❌ Failed to send embed to " + targetChannel.getAsMention()).setEphemeral(true).queue()
        );
    }

    private void handleEmbedList(SlashCommandInteractionEvent event, String guildId) {
        List<String> templates = handler.listEmbedTemplates(guildId);
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📋 Embed Templates")
                .setColor(Color.BLUE)
                .setTimestamp(java.time.Instant.now());
        
        if (templates.isEmpty()) {
            embed.setDescription("No embed templates found for this server.\nUse `/embed-create` to create your first template!");
        } else {
            StringBuilder templateList = new StringBuilder();
            for (String template : templates) {
                templateList.append("• ").append(template).append("\n");
            }
            embed.setDescription(templateList.toString());
        }
        
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleEmbedEdit(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has manage server permission
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to edit embed templates.").setEphemeral(true).queue();
            return;
        }

        String name = event.getOption("name").getAsString();
        String title = event.getOption("title") != null ? event.getOption("title").getAsString() : null;
        String description = event.getOption("description") != null ? event.getOption("description").getAsString() : null;
        String color = event.getOption("color") != null ? event.getOption("color").getAsString() : null;
        String footer = event.getOption("footer") != null ? event.getOption("footer").getAsString() : null;

        // Check if template exists
        Map<String, String> existing = handler.getEmbedTemplate(guildId, name);
        if (existing == null) {
            event.reply("❌ Embed template **" + name + "** not found.").setEphemeral(true).queue();
            return;
        }

        // Validate color if provided
        if (color != null && !isValidHexColor(color)) {
            event.reply("❌ Invalid color format. Please use hex color format like #FF0000").setEphemeral(true).queue();
            return;
        }

        boolean success = handler.updateEmbedTemplate(guildId, name, title, description, color, footer);
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Embed Template Updated")
                    .setDescription("Embed template **" + name + "** has been updated successfully!")
                    .addField("Template Name", name, true)
                    .setColor(Color.GREEN)
                    .setTimestamp(java.time.Instant.now());
            
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to update embed template. No changes were made.").setEphemeral(true).queue();
        }
    }

    private void handleEmbedDelete(SlashCommandInteractionEvent event, String guildId) {
        // Check if user has manage server permission
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to delete embed templates.").setEphemeral(true).queue();
            return;
        }

        String name = event.getOption("name").getAsString();
        
        // Check if template exists
        Map<String, String> existing = handler.getEmbedTemplate(guildId, name);
        if (existing == null) {
            event.reply("❌ Embed template **" + name + "** not found.").setEphemeral(true).queue();
            return;
        }

        boolean success = handler.deleteEmbedTemplate(guildId, name);
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Embed Template Deleted")
                    .setDescription("Embed template **" + name + "** has been deleted successfully!")
                    .setColor(Color.RED)
                    .setTimestamp(java.time.Instant.now());
            
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to delete embed template.").setEphemeral(true).queue();
        }
    }

    private boolean isValidHexColor(String color) {
        if (color == null) return false;
        if (!color.startsWith("#")) return false;
        if (color.length() != 7) return false;
        
        try {
            Integer.parseInt(color.substring(1), 16);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}