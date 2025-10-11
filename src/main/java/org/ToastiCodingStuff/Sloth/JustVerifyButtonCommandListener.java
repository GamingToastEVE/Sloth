package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class JustVerifyButtonCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public JustVerifyButtonCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("add-just-verify-button")) {
            handler.insertOrUpdateGlobalStatistic("add-just-verify-button");
            handleJustVerifyButtonCommand(event);
        }
        else if (event.getName().equals("remove-verify-button")) {
            handler.insertOrUpdateGlobalStatistic("remove-verify-button");
            handleJustVerifyButtonRemove(event);
        } else if (event.getName().equals("send-just-verify-button")) {
            handler.insertOrUpdateGlobalStatistic("send-just-verify-button");
            handleSendJustVerifyButtonCommand(event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getButton().getId().equals("just_verify")) {
            handleJustVerifyButtonClick(event);
        }
    }

    private void handleJustVerifyButtonClick(ButtonInteractionEvent event) {
        if (!event.getButton().getId().equals("just_verify")) {
            event.reply("❌ Invalid button configuration.").setEphemeral(true).queue();
            return;
        }

        String roleToGiveID = handler.getJustVerifyButtonRoleToGiveID(event.getGuild().getId());
        String roleToRemoveID = handler.getJustVerifyButtonRoleToRemoveID(event.getGuild().getId());

        Role roleToGive = null;
        Role roleToRemove = null;

        if (roleToGiveID != null) {
            roleToGive = event.getGuild().getRoleById(roleToGiveID);
        }
        if (roleToRemoveID != null) {
            roleToRemove = event.getGuild().getRoleById(roleToRemoveID);
        }

        if (event.getMember().getRoles().contains(roleToGive)) {
            event.reply("❌ You are already verified!").setEphemeral(true).queue();
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

        handler.incrementVerificationsPerformed(event.getGuild().getId());

        event.reply("✅ You have been verified!").setEphemeral(true).queue();
    }

    private void handleSendJustVerifyButtonCommand(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("send-just-verify-button")) {
            return; // Ignore other commands
        }

        String guildId = event.getGuild().getId();

        // Check if user has manage server permission
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to use this command.").setEphemeral(true).queue();
            return;
        }
        String roleToGiveID = handler.getJustVerifyButtonRoleToGiveID(guildId);
        String roleToRemoveID = handler.getJustVerifyButtonRoleToRemoveID(guildId);
        String buttonLabel = handler.getJustVerifyButtonLabel(guildId);
        String buttonEmoji = handler.getJustVerifyButtonEmojiID(guildId);

        if (roleToGiveID == null) {
            event.reply("❌ No Just Verify button configured for this server. Please set it up first.").setEphemeral(true).queue();
            return;
        }

        event.getChannel().sendMessage("Click the button below to verify!").setActionRow(
            handler.createJustVerifyButton(roleToGiveID, roleToRemoveID, buttonLabel, buttonEmoji)
        ).queue();
        event.reply("✅ Just Verify button sent!").setEphemeral(true).queue();
    }

    private void handleJustVerifyButtonCommand(SlashCommandInteractionEvent event) {

        if (!event.getName().equals("add-just-verify-button")) {
            return; // Ignore other commands
        }

        String guildId = event.getGuild().getId();

        // Check if user has manage server permission
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String roleToGiveID = event.getOption("role-to-give") != null ? event.getOption("role-to-give").getAsRole().getId() : null;
        String roleToRemoveID = event.getOption("role-to-remove") != null ? event.getOption("role-to-remove").getAsRole().getId() : null;
        String buttonLabel = event.getOption("button-label") != null ? event.getOption("button-label").getAsString() : "Verify!";
        String buttonEmoji = event.getOption("button-emoji") != null ? event.getOption("button-emoji").getAsString() : null;

        handler.setJustVerifyButton(guildId, roleToGiveID, roleToRemoveID, buttonLabel, buttonEmoji);

        event.reply("✅ Just Verify button added!").setEphemeral(true).queue();
    }

    private void handleJustVerifyButtonRemove(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("remove-just-verify-button")) {
            return; // Ignore other commands
        }

        String guildId = event.getGuild().getId();

        // Check if user has manage server permission
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to use this command.").setEphemeral(true).queue();
            return;
        }

        handler.removeJustVerifyButton(guildId);

        event.reply("✅ Just Verify button removed!").setEphemeral(true).queue();
    }
}
