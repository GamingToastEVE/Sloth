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

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("verify-button")) {
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            return;
        }

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
            handleJustVerifyButtonClick(event);
        }
    }

    private void handleJustVerifyButtonList(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        java.util.List<DatabaseHandler.VerifyButtonData> configs = handler.getVerifyButtonConfigs(guildId);

        if (configs.isEmpty()) {
            event.reply("❌ No verify buttons configured for this server.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🔘 Verify Button Configurations");
        embed.setColor(Color.BLUE);

        for (DatabaseHandler.VerifyButtonData config : configs) {
            StringBuilder desc = new StringBuilder();

            // Get Roles
            Role giveRole = event.getGuild().getRoleById(config.roleToGiveId);
            String giveRoleMention = giveRole != null ? giveRole.getAsMention() : "Deleted Role (" + config.roleToGiveId + ")";
            desc.append("**Role to Give:** ").append(giveRoleMention).append("\n");

            if (config.roleToRemoveId != null) {
                Role removeRole = event.getGuild().getRoleById(config.roleToRemoveId);
                String removeRoleMention = removeRole != null ? removeRole.getAsMention() : "Deleted Role (" + config.roleToRemoveId + ")";
                desc.append("**Role to Remove:** ").append(removeRoleMention).append("\n");
            } else {
                desc.append("**Role to Remove:** None\n");
            }

            // Display Button Info
            String emoji = config.buttonEmoji != null ? config.buttonEmoji + " " : "";
            desc.append("**Button:** ").append(emoji).append(config.buttonLabel).append("\n");

            embed.addField("Configuration", desc.toString(), false);
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleJustVerifyButtonClick(ButtonInteractionEvent event) {
        if (!event.getComponentId().equals("just_verify")) {
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
            event.reply("❌ No verify button configured for this server. Please use `/verify-button add` first.").setEphemeral(true).queue();
            return;
        }

        Button button = handler.createJustVerifyButton(roleToGiveID, roleToRemoveID, buttonLabel, buttonEmoji);

        event.getChannel().sendMessage("Click the button below to verify!").addComponents(
                ActionRow.of(button)
        ).queue();
        event.reply("✅ Verify button sent!").setEphemeral(true).queue();
    }

    private void handleJustVerifyButtonCommand(SlashCommandInteractionEvent event) {
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

        event.reply("✅ Verify button configuration added!").setEphemeral(true).queue();
    }

    private void handleJustVerifyButtonRemove(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        // Check if user has manage server permission
        if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permission to use this command.").setEphemeral(true).queue();
            return;
        }

        handler.removeJustVerifyButton(guildId);

        event.reply("✅ Verify button configuration removed!").setEphemeral(true).queue();
    }
}
