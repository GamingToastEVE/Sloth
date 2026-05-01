package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;

public class JustVerifyButtonCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public JustVerifyButtonCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    // ==================== LANGUAGE HELPER METHODS ====================

    private String t(String guildId, String key) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            return lang.get(guildId, key);
        }
        return key;
    }

    private String t(String guildId, String key, Object... args) {
        LanguageManager lang = LanguageManager.getInstance();
        if (lang != null) {
            return lang.get(guildId, key, args);
        }
        try {
            return String.format(key, args);
        } catch (Exception e) {
            return key;
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("verify-button")) {
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            return;
        }

        event.deferReply().setEphemeral(true).queue();

        switch (subcommand) {
            case "add":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("verify-button-add");
                handleJustVerifyButtonCommand(event);
                break;
            case "remove":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("verify-button-remove");
                handleJustVerifyButtonRemove(event);
                break;
            case "send":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("verify-button-send");
                handleSendJustVerifyButtonCommand(event);
                break;
            case "list":
                if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {return;}
                handler.insertOrUpdateGlobalStatistic("verify-button-list");
                handleJustVerifyButtonList(event);
                break;
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getComponentId().equals("just_verify")) {
            event.deferReply().setEphemeral(true).queue();
            handleJustVerifyButtonClick(event);
        }
    }

    private void handleJustVerifyButtonList(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        java.util.List<DatabaseHandler.VerifyButtonData> configs = handler.getVerifyButtonConfigs(guildId);

        if (configs.isEmpty()) {
            event.reply(t(guildId, "verify_button.no_configs")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(t(guildId, "verify_button.list_title"));
        embed.setColor(Color.BLUE);

        for (DatabaseHandler.VerifyButtonData config : configs) {
            StringBuilder desc = new StringBuilder();

            // Get Roles
            Role giveRole = event.getGuild().getRoleById(config.roleToGiveId);
            String giveRoleMention = giveRole != null ? giveRole.getAsMention() : t(guildId, "verify_button.deleted_role", config.roleToGiveId);
            desc.append(t(guildId, "verify_button.role_to_give", giveRoleMention)).append("\n");

            if (config.roleToRemoveId != null) {
                Role removeRole = event.getGuild().getRoleById(config.roleToRemoveId);
                String removeRoleMention = removeRole != null ? removeRole.getAsMention() : t(guildId, "verify_button.deleted_role", config.roleToRemoveId);
                desc.append(t(guildId, "verify_button.role_to_remove", removeRoleMention)).append("\n");
            } else {
                desc.append(t(guildId, "verify_button.role_to_remove", t(guildId, "verify_button.none"))).append("\n");
            }

            // Display Button Info
            String emoji = config.buttonEmoji != null ? config.buttonEmoji + " " : "";
            desc.append(t(guildId, "verify_button.button_info", emoji + config.buttonLabel)).append("\n");

            embed.addField(t(guildId, "verify_button.config_field"), desc.toString(), false);
        }

        event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleJustVerifyButtonClick(ButtonInteractionEvent event) {
        if (!event.getComponentId().equals("just_verify")) {
            String gId = event.getGuild().getId();
            event.getHook().sendMessage(t(gId, "verify_button.invalid_config")).setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String roleToGiveID = handler.getJustVerifyButtonRoleToGiveID(guildId);
        String roleToRemoveID = handler.getJustVerifyButtonRoleToRemoveID(guildId);

        Role roleToGive = null;
        Role roleToRemove = null;

        if (roleToGiveID != null) {
            roleToGive = event.getGuild().getRoleById(roleToGiveID);
        }
        if (roleToRemoveID != null) {
            roleToRemove = event.getGuild().getRoleById(roleToRemoveID);
        }

        if (event.getMember().getRoles().contains(roleToGive)) {
            event.getHook().sendMessage(t(guildId, "verify.already_verified")).setEphemeral(true).queue();
            return;
        }

        // Give the role
        if (roleToGiveID != null) {
            event.getGuild().addRoleToMember(event.getMember(), roleToGive).queue();
        }

        // Remove the role if specified
        if (roleToRemove != null) {
            event.getGuild().removeRoleFromMember(event.getMember(), roleToRemove).queue();
        }

        handler.incrementVerificationsPerformed(guildId);

        event.getHook().sendMessage(t(guildId, "verify.success")).setEphemeral(true).queue();
    }

    private void handleSendJustVerifyButtonCommand(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        // Check if user has manage server permission
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.getHook().sendMessage(t(guildId, "verify_button.no_permission")).setEphemeral(true).queue();
            return;
        }
        String roleToGiveID = handler.getJustVerifyButtonRoleToGiveID(guildId);
        String roleToRemoveID = handler.getJustVerifyButtonRoleToRemoveID(guildId);
        String buttonLabel = handler.getJustVerifyButtonLabel(guildId);
        String buttonEmoji = handler.getJustVerifyButtonEmojiID(guildId);

        if (roleToGiveID == null) {
            event.getHook().sendMessage(t(guildId, "verify_button.not_configured")).setEphemeral(true).queue();
            return;
        }

        Button button = handler.createJustVerifyButton(roleToGiveID, roleToRemoveID, buttonLabel, buttonEmoji);

        event.getChannel().sendMessage(t(guildId, "verify_button.click_to_verify")).addComponents(
                ActionRow.of(button)
        ).queue();
        event.getHook().sendMessage(t(guildId, "verify_button.sent_success")).setEphemeral(true).queue();
    }

    private void handleJustVerifyButtonCommand(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        // Check if user has manage server permission
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.getHook().sendMessage(t(guildId, "verify_button.no_permission")).setEphemeral(true).queue();
            return;
        }

        String roleToGiveID = event.getOption("role-to-give") != null ? event.getOption("role-to-give").getAsRole().getId() : null;
        String roleToRemoveID = event.getOption("role-to-remove") != null ? event.getOption("role-to-remove").getAsRole().getId() : null;
        String buttonLabel = event.getOption("button-label") != null ? event.getOption("button-label").getAsString() : "Verify!";
        String buttonEmoji = event.getOption("button-emoji") != null ? event.getOption("button-emoji").getAsString() : null;

        handler.setJustVerifyButton(guildId, roleToGiveID, roleToRemoveID, buttonLabel, buttonEmoji);

        event.getHook().sendMessage(t(guildId, "verify_button.add_success")).setEphemeral(true).queue();
    }

    private void handleJustVerifyButtonRemove(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        // Check if user has manage server permission
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.getHook().sendMessage(t(guildId, "verify_button.no_permission")).setEphemeral(true).queue();
            return;
        }

        handler.removeJustVerifyButton(guildId);

        event.getHook().sendMessage(t(guildId, "verify_button.remove_success")).setEphemeral(true).queue();
    }
}
