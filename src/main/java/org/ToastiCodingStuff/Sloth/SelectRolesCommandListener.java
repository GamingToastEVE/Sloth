package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class SelectRolesCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public SelectRolesCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "create-select-roles":
                handler.insertOrUpdateGlobalStatistic("create-select-roles");
                handleCreateSelectRoles(event);
                break;
            case "add-select-role":
                handler.insertOrUpdateGlobalStatistic("add-select-role");
                handleAddSelectRole(event);
                break;
            case "remove-select-role":
                handler.insertOrUpdateGlobalStatistic("remove-select-role");
                handleRemoveSelectRole(event);
                break;
            case "delete-select-roles-message":
                handler.insertOrUpdateGlobalStatistic("delete-select-roles-message");
                handleDeleteSelectRolesMessage(event);
                break;
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getButton().getId() != null && event.getButton().getId().startsWith("select_role_")) {
            handleRoleButtonClick(event);
        }
    }

    private void handleCreateSelectRoles(SlashCommandInteractionEvent event) {
        // Check permissions
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String title = event.getOption("title") != null ? event.getOption("title").getAsString() : "Select Your Roles";
        String description = event.getOption("description") != null ? event.getOption("description").getAsString() : "Click the buttons below to get or remove roles.";

        // Create database entry
        int dbId = handler.createSelectRolesMessage(
            event.getGuild().getId(),
            event.getChannel().getId(),
            title,
            description
        );

        if (dbId == -1) {
            event.reply("❌ Failed to create select roles message in database.").setEphemeral(true).queue();
            return;
        }

        // Create and send the embed
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(title);
        embed.setDescription(description);
        embed.setColor(Color.BLUE);
        embed.setFooter("Message ID: " + dbId);

        event.reply("✅ Select roles message created! Use `/add-select-role` with message ID **" + dbId + "** to add role options.").setEphemeral(true).queue();

        // Send the actual message
        event.getChannel().sendMessageEmbeds(embed.build()).queue(message -> {
            handler.updateSelectRolesMessageId(dbId, message.getId());
        });
    }

    private void handleAddSelectRole(SlashCommandInteractionEvent event) {
        // Check permissions
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to use this command.").setEphemeral(true).queue();
            return;
        }

        int messageId = event.getOption("message_id").getAsInt();
        Role role = event.getOption("role").getAsRole();
        String label = event.getOption("label") != null ? event.getOption("label").getAsString() : role.getName();
        String roleDescription = event.getOption("description") != null ? event.getOption("description").getAsString() : null;

        // Get the message from database
        DatabaseHandler.SelectRolesMessage msg = handler.getSelectRolesMessage(messageId);
        if (msg == null) {
            event.reply("❌ Select roles message with ID " + messageId + " not found.").setEphemeral(true).queue();
            return;
        }

        // Add role option to database
        int optionId = handler.addSelectRoleOption(messageId, role.getId(), label, roleDescription);
        if (optionId == -1) {
            event.reply("❌ Failed to add role option to database.").setEphemeral(true).queue();
            return;
        }

        // Update the message
        updateSelectRolesMessageWithJDA(event.getJDA(), msg);

        event.reply("✅ Added role option **" + label + "** (" + role.getAsMention() + ") to message ID " + messageId).setEphemeral(true).queue();
    }

    private void handleRemoveSelectRole(SlashCommandInteractionEvent event) {
        // Check permissions
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to use this command.").setEphemeral(true).queue();
            return;
        }

        int messageId = event.getOption("message_id").getAsInt();
        Role role = event.getOption("role").getAsRole();

        // Get the message from database
        DatabaseHandler.SelectRolesMessage msg = handler.getSelectRolesMessage(messageId);
        if (msg == null) {
            event.reply("❌ Select roles message with ID " + messageId + " not found.").setEphemeral(true).queue();
            return;
        }

        // Remove role option from database
        handler.removeSelectRoleOption(messageId, role.getId());

        // Update the message
        updateSelectRolesMessageWithJDA(event.getJDA(), msg);

        event.reply("✅ Removed role option for " + role.getAsMention() + " from message ID " + messageId).setEphemeral(true).queue();
    }

    private void handleDeleteSelectRolesMessage(SlashCommandInteractionEvent event) {
        // Check permissions
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to use this command.").setEphemeral(true).queue();
            return;
        }

        int messageId = event.getOption("message_id").getAsInt();

        // Get the message from database
        DatabaseHandler.SelectRolesMessage msg = handler.getSelectRolesMessage(messageId);
        if (msg == null) {
            event.reply("❌ Select roles message with ID " + messageId + " not found.").setEphemeral(true).queue();
            return;
        }

        // Try to delete the Discord message
        if (msg.messageId != null) {
            try {
                TextChannel channel = event.getGuild().getTextChannelById(msg.channelId);
                if (channel != null) {
                    channel.deleteMessageById(msg.messageId).queue(
                        success -> {},
                        error -> System.err.println("Failed to delete Discord message: " + error.getMessage())
                    );
                }
            } catch (Exception e) {
                System.err.println("Error deleting Discord message: " + e.getMessage());
            }
        }

        // Delete from database (this will also delete all options due to CASCADE)
        handler.deleteSelectRolesMessage(messageId);

        event.reply("✅ Deleted select roles message with ID " + messageId).setEphemeral(true).queue();
    }

    private void handleRoleButtonClick(ButtonInteractionEvent event) {
        String buttonId = event.getButton().getId();
        String roleId = buttonId.substring("select_role_".length());

        Role role = event.getGuild().getRoleById(roleId);
        if (role == null) {
            event.reply("❌ Role not found.").setEphemeral(true).queue();
            return;
        }

        // Check if user already has the role
        if (event.getMember().getRoles().contains(role)) {
            // Remove the role
            event.getGuild().removeRoleFromMember(event.getMember(), role).queue(
                success -> event.reply("✅ Removed role: " + role.getName()).setEphemeral(true).queue(),
                error -> event.reply("❌ Failed to remove role: " + error.getMessage()).setEphemeral(true).queue()
            );
        } else {
            // Add the role
            event.getGuild().addRoleToMember(event.getMember(), role).queue(
                success -> event.reply("✅ Added role: " + role.getName()).setEphemeral(true).queue(),
                error -> event.reply("❌ Failed to add role: " + error.getMessage()).setEphemeral(true).queue()
            );
        }
    }

    private void updateSelectRolesMessage(DatabaseHandler.SelectRolesMessage msg) {
        // This is just a stub - actual update happens in updateSelectRolesMessageWithJDA
        System.out.println("Message update queued for message ID: " + msg.id);
    }

    /**
     * Update a select roles message with current options
     */
    private void updateSelectRolesMessageWithJDA(net.dv8tion.jda.api.JDA jda, DatabaseHandler.SelectRolesMessage msg) {
        if (msg.messageId == null) {
            System.err.println("Cannot update message - Discord message ID is null");
            return;
        }

        try {
            TextChannel channel = jda.getGuildById(msg.guildId).getTextChannelById(msg.channelId);
            if (channel == null) {
                System.err.println("Channel not found: " + msg.channelId);
                return;
            }

            channel.retrieveMessageById(msg.messageId).queue(message -> {
                // Get all role options
                List<DatabaseHandler.SelectRoleOption> options = handler.getSelectRoleOptions(msg.id);

                // Build embed
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle(msg.title);
                embed.setDescription(msg.description);
                embed.setColor(Color.BLUE);
                embed.setFooter("Message ID: " + msg.id);

                // Add fields for each role option
                for (DatabaseHandler.SelectRoleOption option : options) {
                    String fieldValue = option.description != null ? option.description : "Click the button below to get this role";
                    embed.addField(option.label, fieldValue, false);
                }

                // Build buttons
                List<Button> buttons = new ArrayList<>();
                for (DatabaseHandler.SelectRoleOption option : options) {
                    buttons.add(Button.primary("select_role_" + option.roleId, option.label));
                }

                // Update message with up to 5 buttons per row (Discord limit)
                if (buttons.isEmpty()) {
                    message.editMessageEmbeds(embed.build()).setComponents().queue();
                } else if (buttons.size() <= 5) {
                    message.editMessageEmbeds(embed.build()).setActionRow(buttons).queue();
                } else {
                    // Split into multiple rows if more than 5 buttons
                    List<List<Button>> rows = new ArrayList<>();
                    for (int i = 0; i < buttons.size(); i += 5) {
                        rows.add(buttons.subList(i, Math.min(i + 5, buttons.size())));
                    }
                    var actionRows = rows.stream()
                        .map(net.dv8tion.jda.api.interactions.components.ActionRow::of)
                        .toArray(net.dv8tion.jda.api.interactions.components.ActionRow[]::new);
                    message.editMessageEmbeds(embed.build()).setComponents(actionRows).queue();
                }
            }, error -> {
                System.err.println("Failed to retrieve message: " + error.getMessage());
            });
        } catch (Exception e) {
            System.err.println("Error updating select roles message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
