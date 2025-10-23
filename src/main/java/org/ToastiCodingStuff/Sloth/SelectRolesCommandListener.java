package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;

import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SelectRolesCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public SelectRolesCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("remove-select-role")) {
            if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
            handler.insertOrUpdateGlobalStatistic("remove-select-role");
            handleRemoveSelectRole(event, Objects.requireNonNull(event.getOption("role")).getAsRole());
        }
        if (event.getName().equals("add-select-role")) {
            if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
            handler.insertOrUpdateGlobalStatistic("add-select-role");
            handleAddSelectRole(event);
        }
        if (event.getName().equals("send-select-roles")) {
            if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
            handler.insertOrUpdateGlobalStatistic("send-select-roles");
            handleSendSelectRole(event);
        }
    }

    @Override
    public void onMessageReactionAdd (net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent event) {
        if (event.getUser().isBot()) {
            return;
        }
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String emoji = event.getReaction().getEmoji().getFormatted();
        String roleId = handler.getRoleSelectRoleIDByEmoji(guildId, emoji);
        if (roleId != null) {
            Role role = event.getGuild().getRoleById(roleId);
            if (role != null) {
                Objects.requireNonNull(event.getMember()).getGuild().addRoleToMember(event.getMember(), role).queue();
            }
        }
    }

    @Override
    public void onMessageReactionRemove (MessageReactionRemoveEvent event) {
        if (event.getUser().isBot()) {
            return;
        }
        String guildId = Objects.requireNonNull(event.getGuild()).getId();
        String emoji = event.getReaction().getEmoji().getFormatted();
        String roleId = handler.getRoleSelectRoleIDByEmoji(guildId, emoji);
        if (roleId != null) {
            Role role = event.getGuild().getRoleById(roleId);
            if (role != null) {
                Objects.requireNonNull(event.getMember()).getGuild().removeRoleFromMember(event.getMember(), role).queue();

            }
        }
    }

    @Override
    public void onButtonInteraction (net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        if (event.getButton().getId().equals("send_select_roles_reaction")) {
            handleSendSelectRolesReaction(event.getGuild(), event.getChannel());
            event.reply("Sent reaction role selection message!").setEphemeral(true).queue();
        } else  if (event.getButton().getId().equals("send_select_roles_dropdown")) {
            handleSendSelectRolesDropdown(event.getGuild(), event.getChannel());
            event.reply("Sent dropdown role selection message!").setEphemeral(true).queue();
        } else  if (event.getButton().getId().equals("send_select_roles_buttons")) {
            handleSendSelectRolesButtons(event.getGuild(), event.getChannel());
            event.reply("Sent button role selection message!").setEphemeral(true).queue();
        } else {
            if (event.getButton().getId().startsWith("role_select_button_")) {
                String selectId = event.getButton().getId().replace("role_select_button_", "");
                String guildId = Objects.requireNonNull(event.getGuild()).getId();
                List<String> roleId = handler.getAllRoleSelectForGuild(event.getGuild().getId());
                for (String roleInfo : roleId) {
                    String currentSelectId = String.valueOf(handler.getRoleSelectID(guildId, roleInfo));
                    if (currentSelectId.equals(selectId)) {
                        Role role = event.getGuild().getRoleById(roleInfo);
                        if (role != null) {
                            if (Objects.requireNonNull(event.getMember()).getRoles().contains(role)) {
                                event.getGuild().removeRoleFromMember(event.getMember(), role).queue();
                                event.reply("Removed role " + role.getAsMention() + ".").setEphemeral(true).queue();
                            } else {
                                event.getGuild().addRoleToMember(event.getMember(), role).queue();
                                event.reply("Added role " + role.getAsMention() + ".").setEphemeral(true).queue();
                            }
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onStringSelectInteraction (StringSelectInteractionEvent event) {
        if (event.getSelectMenu().getId().equals("role_select_dropdown")) {
            String guildId = Objects.requireNonNull(event.getGuild()).getId();
            List<String> selectedRoleIds = event.getValues();

            // Add selected roles
            for (String roleId : selectedRoleIds) {
                Role role = event.getGuild().getRoleById(roleId);
                if (role != null && !Objects.requireNonNull(event.getMember()).getRoles().contains(role)) {
                    event.getGuild().addRoleToMember(event.getMember(), role).queue();
                    event.reply("Added role " + role.getAsMention() + ".").setEphemeral(true).queue();
                }
                if (role != null && Objects.requireNonNull(event.getMember()).getRoles().contains(role)) {
                    event.getGuild().removeRoleFromMember(event.getMember(), role).queue();
                    event.reply("Removed role " + role.getAsMention() + ".").setEphemeral(true).queue();
                }
            }
        }
    }

    private void handleRemoveSelectRole(SlashCommandInteractionEvent event, Role role) {
        handler.removeRoleSelectFromGuild(Objects.requireNonNull(event.getGuild()).getId(), role.getId());
        event.reply("Removed role " + role.getAsMention() + " from the select role list.").setEphemeral(true).queue();
    }

    private void handleAddSelectRole(SlashCommandInteractionEvent event) {
        Role role = Objects.requireNonNull(event.getOption("role")).getAsRole();
        String description = "No description provided.";
        String emoji = "✅";
        if (event.getOption("description") != null) {
            description = Objects.requireNonNull(event.getOption("description")).getAsString();
        }
        if (event.getOption("emoji") != null) {
            emoji = Objects.requireNonNull(event.getOption("emoji").getAsString());
        }
        handler.addRoleSelectToGuild(Objects.requireNonNull(event.getGuild()).getId(), role.getId(), description, emoji);
        event.reply("Added role " + role.getAsMention() + "with emoji " + emoji + " and description: " + description + " to the select role) list.").setEphemeral(true).queue();
    }

    private void handleSendSelectRole(SlashCommandInteractionEvent event) {
        // Send Section Menu to determine the type of role selection to send
        handler.insertOrUpdateGlobalStatistic("send_select_roles_reaction");
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Select Role Selection Type");
        embedBuilder.setDescription("Please choose the type of role selection you want to send:");
        event.replyEmbeds(embedBuilder.build()).setEphemeral(true)
                .addActionRow(
                        net.dv8tion.jda.api.interactions.components.buttons.Button.primary("send_select_roles_reaction", "Reaction Roles"),
                        net.dv8tion.jda.api.interactions.components.buttons.Button.primary("send_select_roles_dropdown", "Dropdown Menu"),
                        net.dv8tion.jda.api.interactions.components.buttons.Button.primary("send_select_roles_buttons", "Buttons")
                ).queue();
    }

    private void handleSendSelectRolesReaction (Guild guild, Channel channel) {
        if (!channel.getType().isMessage()) {
            return;
        }
        MessageChannel mChannel = guild.getTextChannelById(channel.getId());
        List<String> roleList = handler.getAllRoleSelectForGuild(Objects.requireNonNull(guild.getId()));
        List<String> emojiList = new ArrayList<>();
        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Select Your Roles");
        StringBuilder description = new StringBuilder("React with the corresponding emoji to get the role:\n");
        embedBuilder.setDescription(description);
        for (String roleInfo : roleList) {
            Role role = guild.getRoleById(roleInfo);
            if (role != null) {
                String roleDescription = handler.getRoleSelectDescription(Objects.requireNonNull(guild.getId()), role.getId());
                String roleEmoji = handler.getRoleSelectEmoji(Objects.requireNonNull(guild.getId()), role.getId());
                emojiList.add(roleEmoji);
                embedBuilder.addField(roleEmoji + " " + role.getName(), roleDescription, false);
            }
        }
        assert mChannel != null;
        Message message = mChannel.sendMessageEmbeds(embedBuilder.build()).complete();
        for (String emoji : emojiList) {
            Emoji emj = Emoji.fromFormatted(emoji);
            System.out.println(emj + "; " + emj.getName());
            message.addReaction(Emoji.fromFormatted(emoji)).queue();
        }
    }

    private void handleSendSelectRolesDropdown (Guild guild, Channel channel) {
        if (!channel.getType().isMessage()) {
            return;
        }
        MessageChannel mChannel = guild.getTextChannelById(channel.getId());
        List<String> roleList = handler.getAllRoleSelectForGuild(Objects.requireNonNull(guild.getId()));

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Select Your Roles");
        embedBuilder.setDescription("Use the dropdown menu below to select your roles:");

        net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu.Builder menuBuilder =
            net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu.create("role_select_dropdown")
                .setPlaceholder("Choose your roles")
                .setMinValues(0)
                .setMaxValues(Math.min(roleList.size(), 25));

        for (String roleInfo : roleList) {
            Role role = guild.getRoleById(roleInfo);
            if (role != null) {
                String roleDescription = handler.getRoleSelectDescription(Objects.requireNonNull(guild.getId()), role.getId());
                String roleEmoji = handler.getRoleSelectEmoji(Objects.requireNonNull(guild.getId()), role.getId());
                menuBuilder.addOption(role.getName(), role.getId(), roleDescription, Emoji.fromFormatted(roleEmoji));
            }
        }

        assert mChannel != null;
        mChannel.sendMessageEmbeds(embedBuilder.build())
            .addActionRow(menuBuilder.build())
            .queue();
    }

    private void handleSendSelectRolesButtons (Guild guild, Channel channel) {
        if (!channel.getType().isMessage()) {
            return;
        }
        MessageChannel mChannel = guild.getTextChannelById(channel.getId());
        List<String> roleList = handler.getAllRoleSelectForGuild(Objects.requireNonNull(guild.getId()));

        EmbedBuilder embedBuilder = new EmbedBuilder();
        embedBuilder.setTitle("Select Your Roles");
        embedBuilder.setDescription("Click the buttons below to toggle your roles:");

        List<net.dv8tion.jda.api.interactions.components.buttons.Button> buttons = new ArrayList<>();

        for (String roleInfo : roleList) {
            Role role = guild.getRoleById(roleInfo);
            if (role != null) {
                String roleDescription = handler.getRoleSelectDescription(Objects.requireNonNull(guild.getId()), role.getId());
                String roleEmoji = handler.getRoleSelectEmoji(Objects.requireNonNull(guild.getId()), role.getId());
                buttons.add(net.dv8tion.jda.api.interactions.components.buttons.Button.primary(
                        "role_select_button_" + handler.getRoleSelectID(Objects.requireNonNull(guild.getId()), role.getId()),
                        role.getName()
                ).withEmoji(Emoji.fromFormatted(roleEmoji)));

                embedBuilder.addField(roleEmoji + " " + role.getName(), roleDescription, false);
            }

            if (buttons.size() == 25) break;
        }

        assert mChannel != null;
        MessageCreateAction message = mChannel.sendMessageEmbeds(embedBuilder.build());
        for (int i = 0; i < buttons.size(); i += 5) {
            int end = Math.min(i + 5, buttons.size());
            for (int j = 0; j < end; j += 5) {
                message.addActionRow(buttons.get(i + j));
            }
        }
        message.queue();
    }
}
