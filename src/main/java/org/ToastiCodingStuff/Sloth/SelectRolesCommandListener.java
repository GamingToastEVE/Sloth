package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class SelectRolesCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public SelectRolesCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        switch (event.getName()) {
            case "create-select-roles":
                handleCreateSelectRoles(event, guildId);
                break;
            case "add-select-role":
                handleAddSelectRole(event, guildId);
                break;
            case "remove-select-role":
                handleRemoveSelectRole(event, guildId);
                break;
            case "delete-select-roles-message":
                handleDeleteSelectRolesMessage(event, guildId);
                break;
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String customId = event.getComponentId();
        
        if (customId.startsWith("select_role_")) {
            handleRoleButtonClick(event);
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String customId = event.getComponentId();
        
        if (customId.startsWith("select_role_menu_")) {
            handleRoleDropdownSelect(event);
        }
    }

    private void handleCreateSelectRoles(SlashCommandInteractionEvent event, String guildId) {
        // Check permissions
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String type = event.getOption("type").getAsString().toLowerCase();
        String title = event.getOption("title") != null ? event.getOption("title").getAsString() : "Select Your Roles";
        String description = event.getOption("description") != null ? event.getOption("description").getAsString() : "Click below to select your roles.";
        boolean ephemeral = event.getOption("ephemeral") != null && event.getOption("ephemeral").getAsBoolean();

        // Validate type
        if (!type.equals("reactions") && !type.equals("dropdown") && !type.equals("buttons")) {
            event.reply("❌ Invalid type. Must be 'reactions', 'dropdown', or 'buttons'.").setEphemeral(true).queue();
            return;
        }

        // Create embed
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(title);
        embed.setDescription(description);
        embed.setColor(Color.BLUE);
        embed.setFooter("Use /add-select-role to add role options");

        // Send message and store in database
        event.getChannel().sendMessageEmbeds(embed.build()).queue(message -> {
            handler.createSelectRolesMessage(guildId, message.getId(), event.getChannel().getId(), 
                                            type, title, description, ephemeral);
            event.reply("✅ Select roles message created! Message ID: " + message.getId() + 
                       "\nUse `/add-select-role message_id:" + message.getId() + "` to add role options.")
                .setEphemeral(true).queue();
        });
    }

    private void handleAddSelectRole(SlashCommandInteractionEvent event, String guildId) {
        // Check permissions
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to use this command.").setEphemeral(true).queue();
            return;
        }

        long messageId = event.getOption("message_id").getAsLong();
        Role role = event.getOption("role").getAsRole();
        String label = event.getOption("label") != null ? event.getOption("label").getAsString() : role.getName();
        String description = event.getOption("description") != null ? event.getOption("description").getAsString() : null;
        String emoji = event.getOption("emoji") != null ? event.getOption("emoji").getAsString() : null;

        // Check if message exists
        DatabaseHandler.SelectRolesMessageData messageData = handler.getSelectRolesMessage(String.valueOf(messageId));
        if (messageData == null) {
            event.reply("❌ Select roles message not found with ID: " + messageId).setEphemeral(true).queue();
            return;
        }

        // Add role option to database
        boolean success = handler.addSelectRoleOption(String.valueOf(messageId), role.getId(), label, description, emoji);
        
        if (success) {
            // Update the message with new components
            updateSelectRolesMessage(event.getChannel().retrieveMessageById(messageId), messageData);
            event.reply("✅ Role " + role.getAsMention() + " added to select roles message!").setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to add role option.").setEphemeral(true).queue();
        }
    }

    private void handleRemoveSelectRole(SlashCommandInteractionEvent event, String guildId) {
        // Check permissions
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to use this command.").setEphemeral(true).queue();
            return;
        }

        long messageId = event.getOption("message_id").getAsLong();
        Role role = event.getOption("role").getAsRole();

        // Check if message exists
        DatabaseHandler.SelectRolesMessageData messageData = handler.getSelectRolesMessage(String.valueOf(messageId));
        if (messageData == null) {
            event.reply("❌ Select roles message not found with ID: " + messageId).setEphemeral(true).queue();
            return;
        }

        // Remove role option from database
        boolean success = handler.removeSelectRoleOption(String.valueOf(messageId), role.getId());
        
        if (success) {
            // Update the message with new components
            updateSelectRolesMessage(event.getChannel().retrieveMessageById(messageId), messageData);
            event.reply("✅ Role " + role.getAsMention() + " removed from select roles message!").setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to remove role option. It may not exist in this message.").setEphemeral(true).queue();
        }
    }

    private void handleDeleteSelectRolesMessage(SlashCommandInteractionEvent event, String guildId) {
        // Check permissions
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to use this command.").setEphemeral(true).queue();
            return;
        }

        long messageId = event.getOption("message_id").getAsLong();

        // Check if message exists
        DatabaseHandler.SelectRolesMessageData messageData = handler.getSelectRolesMessage(String.valueOf(messageId));
        if (messageData == null) {
            event.reply("❌ Select roles message not found with ID: " + messageId).setEphemeral(true).queue();
            return;
        }

        // Delete from database
        boolean success = handler.deleteSelectRolesMessage(String.valueOf(messageId));
        
        if (success) {
            // Try to delete the actual message
            event.getChannel().retrieveMessageById(messageId).queue(
                message -> message.delete().queue(),
                error -> {} // Message may already be deleted
            );
            event.reply("✅ Select roles message deleted!").setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to delete select roles message.").setEphemeral(true).queue();
        }
    }

    private void updateSelectRolesMessage(net.dv8tion.jda.api.requests.RestAction<Message> messageAction, 
                                         DatabaseHandler.SelectRolesMessageData messageData) {
        messageAction.queue(message -> {
            List<DatabaseHandler.SelectRoleOption> options = handler.getSelectRoleOptions(messageData.messageId);
            
            if (options.isEmpty()) {
                // No options yet, keep the original message
                return;
            }

            List<ActionRow> actionRows = new ArrayList<>();
            
            if (messageData.type.equals("buttons")) {
                // Create buttons (max 5 per row, max 5 rows)
                List<Button> buttons = new ArrayList<>();
                for (DatabaseHandler.SelectRoleOption option : options) {
                    Button button = Button.primary("select_role_" + option.roleId, option.label);
                    if (option.emoji != null) {
                        try {
                            button = button.withEmoji(Emoji.fromFormatted(option.emoji));
                        } catch (Exception e) {
                            // Invalid emoji, skip it
                        }
                    }
                    buttons.add(button);
                    
                    // Add row when we have 5 buttons
                    if (buttons.size() == 5) {
                        actionRows.add(ActionRow.of(buttons));
                        buttons = new ArrayList<>();
                    }
                    
                    // Max 5 rows
                    if (actionRows.size() == 5) {
                        break;
                    }
                }
                
                // Add remaining buttons
                if (!buttons.isEmpty() && actionRows.size() < 5) {
                    actionRows.add(ActionRow.of(buttons));
                }
                
            } else if (messageData.type.equals("dropdown")) {
                // Create dropdown menu (max 25 options)
                StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("select_role_menu_" + messageData.messageId)
                    .setPlaceholder("Choose your roles");
                
                int count = 0;
                for (DatabaseHandler.SelectRoleOption option : options) {
                    menuBuilder.addOption(option.label, option.roleId, 
                                        option.description != null ? option.description : "");
                    count++;
                    if (count >= 25) break; // Discord limit
                }
                
                menuBuilder.setMinValues(0);
                menuBuilder.setMaxValues(Math.min(options.size(), 25));
                
                actionRows.add(ActionRow.of(menuBuilder.build()));
            }
            // For reactions type, we don't add components - reactions are added manually

            if (!actionRows.isEmpty()) {
                message.editMessageComponents(actionRows).queue();
            }
        });
    }

    private void handleRoleButtonClick(ButtonInteractionEvent event) {
        String roleId = event.getComponentId().substring("select_role_".length());
        Role role = event.getGuild().getRoleById(roleId);
        
        if (role == null) {
            event.reply("❌ Role not found. It may have been deleted.").setEphemeral(true).queue();
            return;
        }

        // Check if message is ephemeral
        DatabaseHandler.SelectRolesMessageData messageData = handler.getSelectRolesMessage(event.getMessage().getId());
        boolean ephemeral = messageData != null && messageData.ephemeral;

        // Toggle role
        if (event.getMember().getRoles().contains(role)) {
            event.getGuild().removeRoleFromMember(event.getMember(), role).queue(
                success -> event.reply("✅ Removed role: " + role.getAsMention()).setEphemeral(ephemeral).queue(),
                error -> event.reply("❌ Failed to remove role. I may not have permission.").setEphemeral(true).queue()
            );
        } else {
            event.getGuild().addRoleToMember(event.getMember(), role).queue(
                success -> event.reply("✅ Added role: " + role.getAsMention()).setEphemeral(ephemeral).queue(),
                error -> event.reply("❌ Failed to add role. I may not have permission.").setEphemeral(true).queue()
            );
        }
    }

    private void handleRoleDropdownSelect(StringSelectInteractionEvent event) {
        List<String> selectedRoleIds = event.getValues();
        List<Role> allRoles = handler.getSelectRoleOptions(event.getMessage().getId()).stream()
            .map(option -> event.getGuild().getRoleById(option.roleId))
            .filter(role -> role != null)
            .toList();

        // Check if message is ephemeral
        DatabaseHandler.SelectRolesMessageData messageData = handler.getSelectRolesMessage(event.getMessage().getId());
        boolean ephemeral = messageData != null && messageData.ephemeral;

        List<Role> selectedRoles = selectedRoleIds.stream()
            .map(id -> event.getGuild().getRoleById(id))
            .filter(role -> role != null)
            .toList();

        // Remove roles that are not selected
        for (Role role : allRoles) {
            if (event.getMember().getRoles().contains(role) && !selectedRoles.contains(role)) {
                event.getGuild().removeRoleFromMember(event.getMember(), role).queue();
            }
        }

        // Add selected roles
        for (Role role : selectedRoles) {
            if (!event.getMember().getRoles().contains(role)) {
                event.getGuild().addRoleToMember(event.getMember(), role).queue();
            }
        }

        event.reply("✅ Roles updated!").setEphemeral(ephemeral).queue();
    }
}
