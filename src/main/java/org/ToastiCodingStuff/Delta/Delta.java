package org.ToastiCodingStuff.Delta;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Delta {
    public static void main(String[] args) throws Exception {
        Dotenv dotenv = Dotenv.load();
        JDA api = JDABuilder.createDefault(dotenv.get("TOKEN"))
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                .build();
        api.awaitReady();
        //api.addEventListener(new OnMessageListener());
        api.addEventListener(new logchannelSlashCommandListener());
        Guild guild = api.getGuildById("1169699077986988112");
        addGuildSlashCommands adder = new addGuildSlashCommands(guild);
        adder.addlogChannelCommands();
    }
}
