package org.ToastiCodingStuff.Delta;

import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.concurrent.TimeUnit;

public class BanCommand extends ListenerAdapter {

    private final DatabaseHandler handler;

    public BanCommand(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onSlashCommandInteraction(net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent event) {
        if (event.getName().equals("ban")) {
            if (event.getMember().hasPermission(net.dv8tion.jda.api.Permission.BAN_MEMBERS)) {
                if (event.getOption("user") != null) {
                    net.dv8tion.jda.api.entities.Member member = event.getOption("user").getAsMember();
                    if (member != null) {
                        String guildId = event.getGuild().getId();
                        member.ban(0, TimeUnit.SECONDS).queue(); // 0 means no delete messages
                        event.reply("Banned " + member.getEffectiveName()).queue();
                        
                        // Update statistics for bans performed
                        handler.incrementBansPerformed(guildId);
                    } else {
                        event.reply("User not found.").setEphemeral(true).queue();
                    }
                } else {
                    event.reply("Please specify a user to ban.").setEphemeral(true).queue();
                }
            } else {
                event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            }
        }
    }
}
