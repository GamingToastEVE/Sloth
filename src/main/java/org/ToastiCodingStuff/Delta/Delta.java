package org.ToastiCodingStuff.Delta;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Delta {
    public static void main(String[] args) throws Exception {
        JDA api = JDABuilder.createDefault("MTE4NDc4NDYxNjM4NTY3OTQwMA.GC7HOm.8rEEoR_8QdM3Udcaa8ejB0HUv_7eLxYSRMBXxg")
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
                .build();
        api.awaitReady();
        api.addEventListener(new OnMessageListener());
        api.addEventListener(new setLogchannelSlashCommandListener());
        Guild guild = api.getGuildById("1169699077986988112");
        addGuildSlashCommands adder = new addGuildSlashCommands(guild);
        adder.addlogChannelCommands();
    }
}
