package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.Color;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class TimedRolesCommandListener extends ListenerAdapter implements SlashCommandHandler {

    private final DatabaseHandler handler;

    public TimedRolesCommandListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public String[] getHandledCommands() {
        return new String[]{"my-roles", "temprole"};
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
    public void handleSlashCommand(SlashCommandInteractionEvent event) {
        String command = event.getName();

        switch (command) {
            case "my-roles":
                String guildId = Objects.requireNonNull(event.getGuild()).getId();
                // Jeder User darf das sehen
                handler.insertOrUpdateGlobalStatistic("my-roles");
                handleMyRoles(event, guildId);
                break;
            case "temprole":
                String guildId2 = Objects.requireNonNull(event.getGuild()).getId();
                // Nur Admins/Mods dürfen das
                if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
                    event.reply(t(guildId2, "general.permission_denied")).setEphemeral(true).queue();
                    return;
                }
                handler.insertOrUpdateGlobalStatistic("temprole");
                handleTempRoleManage(event, guildId2);
                break;
        }
    }

    /**
     * Zeigt dem User seine eigenen aktiven temporären Rollen an.
     */
    private void handleMyRoles(SlashCommandInteractionEvent event, String guildId) {
        String oderId = event.getUser().getId();
        List<DatabaseHandler.ActiveTimerData> timers = handler.getActiveTimersForUser(guildId, oderId);

        if (timers.isEmpty()) {
            event.reply(t(guildId, "temproles.no_roles")).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⏳ " + t(guildId, "temproles.title"));
        embed.setColor(Color.ORANGE);
        embed.setDescription(t(guildId, "temproles.description"));

        StringBuilder content = new StringBuilder();
        Guild guild = event.getGuild();

        for (DatabaseHandler.ActiveTimerData timer : timers) {
            Role role = guild.getRoleById(timer.roleId);
            String roleName = (role != null) ? role.getAsMention() : t(guildId, "verify_button.deleted_role", timer.roleId);

            // Discord Timestamp Format: <t:SECONDS:R> macht daraus "in 2 Tagen" oder "vor 5 Minuten"
            long unixSeconds = timer.expiresAt.getTime() / 1000;

            content.append("• ").append(roleName)
                    .append(" \n  ").append(t(guildId, "temproles.expires")).append(": <t:").append(unixSeconds).append(":R>")
                    .append(" (<t:").append(unixSeconds).append(":f>)")
                    .append("\n\n");
        }

        embed.setDescription(content.toString());
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    /**
     * Admin-Command zum manuellen Hinzufügen/Entfernen.
     * Subcommands: /temprole add user role duration
     * /temprole remove user role
     */
    private void handleTempRoleManage(SlashCommandInteractionEvent event, String guildId) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        Member target = event.getOption("user").getAsMember();
        Role role = event.getOption("role").getAsRole();

        if (target == null) {
            event.reply(t(guildId, "moderation.user_not_found")).setEphemeral(true).queue();
            return;
        }

        if (subcommand.equals("add")) {
            // Dauer parsen (String input wie "24h", "30m" oder reine Zahl als Minuten)
            String durationStr = event.getOption("duration").getAsString();
            long seconds = parseDuration(durationStr);

            if (seconds < 0) {
                event.reply("❌ " + t(guildId, "temproles.invalid_duration")).setEphemeral(true).queue();
                return;
            }

            // Rolle vergeben
            event.getGuild().addRoleToMember(target, role).queue(
                    success -> {
                        // Timer in DB eintragen (EventID 0, da manuell)
                        handler.addActiveTimer(guildId, target.getId(), role.getId(), 0, seconds);

                        long unixExpiry = (System.currentTimeMillis() / 1000) + seconds;
                        String msg = "✅ " + t(guildId, "temproles.add_success", role.getAsMention(), target.getAsMention(), "<t:" + unixExpiry + ":R>");
                        event.reply(msg).queue();
                    },
                    error -> event.reply("❌ " + t(guildId, "temproles.error_assign")).setEphemeral(true).queue()
            );

        } else if (subcommand.equals("remove")) {
            // Rolle entfernen & Timer löschen
            event.getGuild().removeRoleFromMember(target, role).queue(
                    success -> {
                        boolean deleted = handler.removeTimerManual(guildId, target.getId(), role.getId());
                        if (deleted) {
                            event.reply("✅ " + t(guildId, "temproles.remove_success")).queue();
                        } else {
                            event.reply("⚠️ " + t(guildId, "temproles.remove_no_timer")).queue();
                        }
                    },
                    error -> event.reply("❌ " + t(guildId, "temproles.error_remove")).setEphemeral(true).queue()
            );
        }
    }

    /**
     * Hilfsfunktion: Wandelt "1h", "30m", "1d" in Sekunden um.
     */
    private long parseDuration(String input) {
        try {
            input = input.toLowerCase().trim();
            if (input.endsWith("d")) {
                return TimeUnit.DAYS.toSeconds(Long.parseLong(input.replace("d", "")));
            } else if (input.endsWith("h")) {
                return TimeUnit.HOURS.toSeconds(Long.parseLong(input.replace("h", "")));
            } else if (input.endsWith("m")) {
                return TimeUnit.MINUTES.toSeconds(Long.parseLong(input.replace("m", "")));
            } else if (input.endsWith("s")) {
                return Long.parseLong(input.replace("s", ""));
            } else {
                // Fallback: Wenn nur eine Zahl, nehmen wir Minuten an
                return TimeUnit.MINUTES.toSeconds(Long.parseLong(input));
            }
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
