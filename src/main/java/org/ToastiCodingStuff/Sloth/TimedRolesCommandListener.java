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

public class TimedRolesCommandListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public TimedRolesCommandListener(DatabaseHandler handler) {
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

        LanguageManager lang = LanguageManager.getInstance();
        boolean isGerman = lang != null && lang.getGuildLanguage(guildId).equals("de");

        if (timers.isEmpty()) {
            String msg = isGerman ? "Du hast aktuell keine zeitbegrenzten Rollen." : "You don't have any timed roles.";
            event.reply(msg).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(isGerman ? "⏳ Deine temporären Rollen" : "⏳ Your Temporary Roles");
        embed.setColor(Color.ORANGE);
        embed.setDescription(isGerman ? "Hier ist eine Übersicht deiner Rollen, die automatisch ablaufen:" : "Here's an overview of your roles that will expire automatically:");

        StringBuilder content = new StringBuilder();
        Guild guild = event.getGuild();

        for (DatabaseHandler.ActiveTimerData timer : timers) {
            Role role = guild.getRoleById(timer.roleId);
            String roleName = (role != null) ? role.getAsMention() : (isGerman ? "Gelöschte Rolle (" : "Deleted Role (") + timer.roleId + ")";

            // Discord Timestamp Format: <t:SECONDS:R> macht daraus "in 2 Tagen" oder "vor 5 Minuten"
            long unixSeconds = timer.expiresAt.getTime() / 1000;

            content.append("• ").append(roleName)
                    .append(" \n  ").append(isGerman ? "Läuft ab" : "Expires").append(": <t:").append(unixSeconds).append(":R>")
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

        LanguageManager lang = LanguageManager.getInstance();
        boolean isGerman = lang != null && lang.getGuildLanguage(guildId).equals("de");

        if (target == null) {
            String msg = isGerman ? "❌ Benutzer nicht gefunden." : "❌ User not found.";
            event.reply(msg).setEphemeral(true).queue();
            return;
        }

        if (subcommand.equals("add")) {
            // Dauer parsen (String input wie "24h", "30m" oder reine Zahl als Minuten)
            String durationStr = event.getOption("duration").getAsString();
            long seconds = parseDuration(durationStr);

            if (seconds < 0) {
                String msg = isGerman ? "❌ Ungültige Dauer. Nutze Formate wie `30m`, `24h`, `7d`." : "❌ Invalid duration. Use formats like `30m`, `24h`, `7d`.";
                event.reply(msg).setEphemeral(true).queue();
                return;
            }

            // Rolle vergeben
            event.getGuild().addRoleToMember(target, role).queue(
                    success -> {
                        // Timer in DB eintragen (EventID 0, da manuell)
                        handler.addActiveTimer(guildId, target.getId(), role.getId(), 0, seconds);

                        long unixExpiry = (System.currentTimeMillis() / 1000) + seconds;
                        String msg = isGerman
                                ? "✅ Rolle " + role.getAsMention() + " an " + target.getAsMention() + " vergeben.\nLäuft ab: <t:" + unixExpiry + ":R>"
                                : "✅ Role " + role.getAsMention() + " assigned to " + target.getAsMention() + ".\nExpires: <t:" + unixExpiry + ":R>";
                        event.reply(msg).queue();
                    },
                    error -> {
                        String msg = isGerman ? "❌ Fehler beim Vergeben der Rolle. Überprüfe meine Berechtigungen!" : "❌ Error assigning role. Check my permissions!";
                        event.reply(msg).setEphemeral(true).queue();
                    }
            );

        } else if (subcommand.equals("remove")) {
            // Rolle entfernen & Timer löschen
            event.getGuild().removeRoleFromMember(target, role).queue(
                    success -> {
                        boolean deleted = handler.removeTimerManual(guildId, target.getId(), role.getId());
                        if (deleted) {
                            String msg = isGerman ? "✅ Rolle entfernt und Timer gestoppt." : "✅ Role removed and timer stopped.";
                            event.reply(msg).queue();
                        } else {
                            String msg = isGerman ? "⚠️ Rolle entfernt, aber es wurde kein aktiver Timer in der Datenbank gefunden." : "⚠️ Role removed, but no active timer was found in the database.";
                            event.reply(msg).queue();
                        }
                    },
                    error -> {
                        String msg = isGerman ? "❌ Fehler beim Entfernen der Rolle." : "❌ Error removing role.";
                        event.reply(msg).setEphemeral(true).queue();
                    }
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
