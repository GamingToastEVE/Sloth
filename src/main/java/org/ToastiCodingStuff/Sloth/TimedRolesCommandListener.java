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

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String command = event.getName();
        String guildId = Objects.requireNonNull(event.getGuild()).getId();

        switch (command) {
            case "my-roles":
                // Jeder User darf das sehen
                handler.insertOrUpdateGlobalStatistic("my-roles");
                handleMyRoles(event, guildId);
                break;
            case "temprole":
                // Nur Admins/Mods dürfen das
                if (!event.getMember().hasPermission(Permission.MANAGE_ROLES)) {
                    event.reply("❌ Du hast keine Berechtigung, temporäre Rollen zu verwalten.").setEphemeral(true).queue();
                    return;
                }
                handler.insertOrUpdateGlobalStatistic("temprole");
                handleTempRoleManage(event, guildId);
                break;
        }
    }

    /**
     * Zeigt dem User seine eigenen aktiven temporären Rollen an.
     */
    private void handleMyRoles(SlashCommandInteractionEvent event, String guildId) {
        String userId = event.getUser().getId();
        List<DatabaseHandler.ActiveTimerData> timers = handler.getActiveTimersForUser(guildId, userId);

        if (timers.isEmpty()) {
            event.reply("Du hast aktuell keine zeitbegrenzten Rollen.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⏳ Deine temporären Rollen");
        embed.setColor(Color.ORANGE);
        embed.setDescription("Hier ist eine Übersicht deiner Rollen, die automatisch ablaufen:");

        StringBuilder content = new StringBuilder();
        Guild guild = event.getGuild();

        for (DatabaseHandler.ActiveTimerData timer : timers) {
            Role role = guild.getRoleById(timer.roleId);
            String roleName = (role != null) ? role.getAsMention() : "Gelöschte Rolle (" + timer.roleId + ")";

            // Discord Timestamp Format: <t:SECONDS:R> macht daraus "in 2 Tagen" oder "vor 5 Minuten"
            long unixSeconds = timer.expiresAt.getTime() / 1000;

            content.append("• ").append(roleName)
                    .append(" \n  Expires: <t:").append(unixSeconds).append(":R>") // Relativ (in X Minuten)
                    .append(" (<t:").append(unixSeconds).append(":f>)") // Absolut (Datum Uhrzeit)
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
            event.reply("❌ User nicht gefunden.").setEphemeral(true).queue();
            return;
        }

        if (subcommand.equals("add")) {
            // Dauer parsen (String input wie "24h", "30m" oder reine Zahl als Minuten)
            String durationStr = event.getOption("duration").getAsString();
            long seconds = parseDuration(durationStr);

            if (seconds < 0) {
                event.reply("❌ Ungültige Dauer. Nutze Formate wie `30m`, `24h`, `7d`.").setEphemeral(true).queue();
                return;
            }

            // Rolle vergeben
            event.getGuild().addRoleToMember(target, role).queue(
                    success -> {
                        // Timer in DB eintragen (EventID 0, da manuell)
                        handler.addActiveTimer(guildId, target.getId(), role.getId(), 0, seconds);

                        long unixExpiry = (System.currentTimeMillis() / 1000) + seconds;
                        event.reply("✅ Rolle " + role.getAsMention() + " an " + target.getAsMention() + " vergeben.\n" +
                                "Läuft ab: <t:" + unixExpiry + ":R>").queue();
                    },
                    error -> event.reply("❌ Fehler beim Vergeben der Rolle. Überprüfe meine Berechtigungen!").setEphemeral(true).queue()
            );

        } else if (subcommand.equals("remove")) {
            // Rolle entfernen & Timer löschen
            event.getGuild().removeRoleFromMember(target, role).queue(
                    success -> {
                        boolean deleted = handler.removeTimerManual(guildId, target.getId(), role.getId());
                        if (deleted) {
                            event.reply("✅ Rolle entfernt und Timer gestoppt.").queue();
                        } else {
                            event.reply("⚠️ Rolle entfernt, aber es wurde kein aktiver Timer in der Datenbank gefunden.").queue();
                        }
                    },
                    error -> event.reply("❌ Fehler beim Entfernen der Rolle.").setEphemeral(true).queue()
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
