package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class AutomodCommandListener extends ListenerAdapter {
    
    private final DatabaseHandler databaseHandler;
    
    public AutomodCommandListener(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
    }
    
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "automod-create":
                handleCreateRule(event);
                break;
            case "automod-list":
                handleListRules(event);
                break;
            case "automod-view":
                handleViewRule(event);
                break;
            case "automod-edit":
                handleEditRule(event);
                break;
            case "automod-toggle":
                handleToggleRule(event);
                break;
            case "automod-delete":
                handleDeleteRule(event);
                break;
        }
    }
    
    private void handleCreateRule(SlashCommandInteractionEvent event) {
        // Check permissions
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You need Administrator permissions to manage automod rules.").setEphemeral(true).queue();
            return;
        }
        
        String guildId = event.getGuild().getId();
        String name = event.getOption("name").getAsString();
        String ruleType = event.getOption("rule_type").getAsString();
        String action = event.getOption("action").getAsString();
        int threshold = event.getOption("threshold", 1, OptionMapping::getAsInt);
        
        OptionMapping durationOption = event.getOption("duration");
        Integer duration = durationOption != null ? durationOption.getAsInt() : null;
        
        OptionMapping whitelistOption = event.getOption("whitelist");
        String whitelist = whitelistOption != null ? whitelistOption.getAsString() : null;
        
        OptionMapping configOption = event.getOption("config");
        String config = configOption != null ? configOption.getAsString() : null;
        
        int ruleId = databaseHandler.createAutomodRule(guildId, name, ruleType, action, threshold, duration, whitelist, config);
        
        if (ruleId > 0) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Automod Rule Created")
                    .setDescription("Successfully created automod rule: **" + name + "**")
                    .addField("Rule ID", String.valueOf(ruleId), true)
                    .addField("Type", ruleType, true)
                    .addField("Action", action, true)
                    .addField("Threshold", String.valueOf(threshold), true)
                    .setColor(Color.GREEN)
                    .setTimestamp(Instant.now());
            
            if (duration != null) {
                embed.addField("Duration", duration + " minutes", true);
            }
            
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to create automod rule. Please try again.").setEphemeral(true).queue();
        }
    }
    
    private void handleListRules(SlashCommandInteractionEvent event) {
        // Check permissions
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permissions to view automod rules.").setEphemeral(true).queue();
            return;
        }
        
        String guildId = event.getGuild().getId();
        List<Map<String, Object>> rules = databaseHandler.getAutomodRules(guildId);
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🛡️ Automod Rules")
                .setColor(Color.BLUE)
                .setTimestamp(Instant.now());
        
        if (rules.isEmpty()) {
            embed.setDescription("No automod rules configured for this server.\nUse `/automod-create` to create your first rule.");
        } else {
            StringBuilder description = new StringBuilder();
            description.append("**").append(rules.size()).append(" rule(s) configured:**\n\n");
            
            for (Map<String, Object> rule : rules) {
                String status = (Boolean) rule.get("enabled") ? "🟢" : "🔴";
                description.append(status).append(" **").append(rule.get("name")).append("** (ID: ").append(rule.get("id")).append(")\n");
                description.append("   Type: `").append(rule.get("rule_type")).append("` | Action: `").append(rule.get("action")).append("`\n");
                description.append("   Threshold: ").append(rule.get("threshold"));
                
                if (rule.get("duration") != null) {
                    description.append(" | Duration: ").append(rule.get("duration")).append("m");
                }
                description.append("\n\n");
            }
            
            embed.setDescription(description.toString());
        }
        
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
    
    private void handleViewRule(SlashCommandInteractionEvent event) {
        // Check permissions
        if (!event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("❌ You need Manage Server permissions to view automod rules.").setEphemeral(true).queue();
            return;
        }
        
        String guildId = event.getGuild().getId();
        int ruleId = event.getOption("rule_id").getAsInt();
        
        Map<String, Object> rule = databaseHandler.getAutomodRule(ruleId, guildId);
        
        if (rule == null) {
            event.reply("❌ Automod rule not found or you don't have permission to view it.").setEphemeral(true).queue();
            return;
        }
        
        String status = (Boolean) rule.get("enabled") ? "🟢 Enabled" : "🔴 Disabled";
        
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🛡️ Automod Rule Details")
                .setDescription("**" + rule.get("name") + "** (ID: " + rule.get("id") + ")")
                .addField("Status", status, true)
                .addField("Type", (String) rule.get("rule_type"), true)
                .addField("Action", (String) rule.get("action"), true)
                .addField("Threshold", String.valueOf(rule.get("threshold")), true)
                .setColor((Boolean) rule.get("enabled") ? Color.GREEN : Color.RED)
                .setTimestamp(Instant.now())
                .setFooter("Created: " + rule.get("created_at") + " | Updated: " + rule.get("updated_at"));
        
        if (rule.get("duration") != null) {
            embed.addField("Duration", rule.get("duration") + " minutes", true);
        }
        
        if (rule.get("whitelist") != null && !rule.get("whitelist").toString().isEmpty()) {
            embed.addField("Whitelist", (String) rule.get("whitelist"), false);
        }
        
        if (rule.get("config") != null && !rule.get("config").toString().isEmpty()) {
            embed.addField("Configuration", (String) rule.get("config"), false);
        }
        
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }
    
    private void handleEditRule(SlashCommandInteractionEvent event) {
        // Check permissions
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You need Administrator permissions to edit automod rules.").setEphemeral(true).queue();
            return;
        }
        
        String guildId = event.getGuild().getId();
        int ruleId = event.getOption("rule_id").getAsInt();
        
        // Get current rule to verify it exists
        Map<String, Object> currentRule = databaseHandler.getAutomodRule(ruleId, guildId);
        if (currentRule == null) {
            event.reply("❌ Automod rule not found or you don't have permission to edit it.").setEphemeral(true).queue();
            return;
        }
        
        // Get new values (use current values if not provided)
        OptionMapping nameOption = event.getOption("name");
        String name = nameOption != null ? nameOption.getAsString() : (String) currentRule.get("name");
        
        OptionMapping ruleTypeOption = event.getOption("rule_type");
        String ruleType = ruleTypeOption != null ? ruleTypeOption.getAsString() : (String) currentRule.get("rule_type");
        
        OptionMapping actionOption = event.getOption("action");
        String action = actionOption != null ? actionOption.getAsString() : (String) currentRule.get("action");
        
        OptionMapping thresholdOption = event.getOption("threshold");
        int threshold = thresholdOption != null ? thresholdOption.getAsInt() : (Integer) currentRule.get("threshold");
        
        OptionMapping durationOption = event.getOption("duration");
        Integer duration = durationOption != null ? durationOption.getAsInt() : (Integer) currentRule.get("duration");
        
        OptionMapping whitelistOption = event.getOption("whitelist");
        String whitelist = whitelistOption != null ? whitelistOption.getAsString() : (String) currentRule.get("whitelist");
        
        OptionMapping configOption = event.getOption("config");
        String config = configOption != null ? configOption.getAsString() : (String) currentRule.get("config");
        
        boolean success = databaseHandler.updateAutomodRule(ruleId, guildId, name, ruleType, action, threshold, duration, whitelist, config);
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Automod Rule Updated")
                    .setDescription("Successfully updated automod rule: **" + name + "** (ID: " + ruleId + ")")
                    .addField("Type", ruleType, true)
                    .addField("Action", action, true)
                    .addField("Threshold", String.valueOf(threshold), true)
                    .setColor(Color.GREEN)
                    .setTimestamp(Instant.now());
            
            if (duration != null) {
                embed.addField("Duration", duration + " minutes", true);
            }
            
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to update automod rule. Please try again.").setEphemeral(true).queue();
        }
    }
    
    private void handleToggleRule(SlashCommandInteractionEvent event) {
        // Check permissions
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You need Administrator permissions to toggle automod rules.").setEphemeral(true).queue();
            return;
        }
        
        String guildId = event.getGuild().getId();
        int ruleId = event.getOption("rule_id").getAsInt();
        boolean enabled = event.getOption("enabled").getAsBoolean();
        
        boolean success = databaseHandler.toggleAutomodRule(ruleId, guildId, enabled);
        
        if (success) {
            String status = enabled ? "🟢 enabled" : "🔴 disabled";
            event.reply("✅ Automod rule (ID: " + ruleId + ") has been " + status + ".").setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to toggle automod rule. Rule not found or access denied.").setEphemeral(true).queue();
        }
    }
    
    private void handleDeleteRule(SlashCommandInteractionEvent event) {
        // Check permissions
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You need Administrator permissions to delete automod rules.").setEphemeral(true).queue();
            return;
        }
        
        String guildId = event.getGuild().getId();
        int ruleId = event.getOption("rule_id").getAsInt();
        
        // Get rule details for confirmation
        Map<String, Object> rule = databaseHandler.getAutomodRule(ruleId, guildId);
        if (rule == null) {
            event.reply("❌ Automod rule not found or you don't have permission to delete it.").setEphemeral(true).queue();
            return;
        }
        
        boolean success = databaseHandler.deleteAutomodRule(ruleId, guildId);
        
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🗑️ Automod Rule Deleted")
                    .setDescription("Successfully deleted automod rule: **" + rule.get("name") + "** (ID: " + ruleId + ")")
                    .setColor(Color.RED)
                    .setTimestamp(Instant.now());
            
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.reply("❌ Failed to delete automod rule. Please try again.").setEphemeral(true).queue();
        }
    }
}