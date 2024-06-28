package org.ToastiCodingStuff.Delta;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class addGuildSlashCommands {
    private final Guild guild;

    public addGuildSlashCommands(Guild guild) {
        this.guild = guild;
    }

    public void addlogChannelCommands () {
        guild.updateCommands().addCommands(
                Commands.slash("set-log-channel", "Sets the log channel.")
                        .addOption(OptionType.CHANNEL, "logchannel", "Specified Channel will be log channel", true)
        ).queue();
    }
}
