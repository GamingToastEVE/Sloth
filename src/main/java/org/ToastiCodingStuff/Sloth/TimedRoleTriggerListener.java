package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class TimedRoleTriggerListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public TimedRoleTriggerListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    // Trigger: Wenn ein User eine Rolle bekommt ("getrole")
    @Override
    public void onGuildMemberRoleAdd(@NotNull GuildMemberRoleAddEvent event) {
        // Wir prüfen für JEDE Rolle, die hinzugefügt wurde
        for (Role role : event.getRoles()) {
            processTrigger(event.getGuild(), event.getMember(), RoleEventType.ROLE_ADD, role.getId());
        }
    }

    // Trigger: Wenn einem User eine Rolle weggenommen wird ("removerole")
    @Override
    public void onGuildMemberRoleRemove(@NotNull GuildMemberRoleRemoveEvent event) {
        for (Role role : event.getRoles()) {
            processTrigger(event.getGuild(), event.getMember(), RoleEventType.ROLE_REMOVE, role.getId());
        }
    }

    /**
     * Die Hauptlogik: Prüft Bedingungen, vergibt Rollen und startet Timer.
     *
     * @param guild Der Server
     * @param member Der betroffene User
     * @param type Der Event-Typ (ROLE_ADD, ROLE_REMOVE etc.)
     * @param triggerEntityId Die ID des Auslösers (z.B. die ID der Rolle, die hinzugefügt wurde)
     */
    private void processTrigger(Guild guild, Member member, RoleEventType type, String triggerEntityId) {
        String guildId = guild.getId();

        // 1. Hole alle Regeln aus der DB für diesen Event-Typ
        List<DatabaseHandler.RoleEventData> events = handler.getRoleEventsByType(guildId, type);

        for (DatabaseHandler.RoleEventData eventConfig : events) {

            // 2. Prüfen, ob die Bedingung (trigger_data) passt
            // Bei Rollen-Events wollen wir oft wissen: "Wurde genau Rolle X hinzugefügt?"
            // Wir erwarten im JSON so etwas wie: {"trigger_role_id": "123456789"}
            if (!checkCondition(eventConfig.triggerData, triggerEntityId)) {
                continue; // Bedingung nicht erfüllt, nächstes Event prüfen
            }

            // 3. Die Ziel-Rolle (Reward/Punishment) finden
            Role targetRole = guild.getRoleById(eventConfig.roleId);
            if (targetRole == null) {
                System.out.println("RoleEvent " + eventConfig.id + ": Ziel-Rolle existiert nicht mehr.");
                continue;
            }

            // 4. Die Aktion ausführen (ADD oder REMOVE)
            if ("REMOVE".equalsIgnoreCase(eventConfig.actionType)) {
                // --- ROLLE ENTFERNEN ---
                guild.removeRoleFromMember(member, targetRole)
                        .reason("Auto-Trigger: " + eventConfig.name)
                        .queue();

                // Falls es einen Timer gab, löschen wir ihn (damit der Loop nicht meckert)
                handler.removeTimerManual(guildId, member.getId(), targetRole.getId());

            } else {
                // --- ROLLE GEBEN (Standard) ---
                guild.addRoleToMember(member, targetRole)
                        .reason("Auto-Trigger: " + eventConfig.name)
                        .queue();

                // 5. Timer starten (nur wenn Dauer > 0)
                if (eventConfig.durationSeconds > 0) {
                    // Prüfen auf Stacking-Strategie (REFRESH vs EXTEND)
                    // Hier vereinfacht: Wir überschreiben/starten neu (REFRESH)
                    // Für EXTEND müsstest du prüfen, ob schon ein Timer existiert und die Zeit addieren

                    // Alten Timer für gleiche Rolle/User löschen (Reset)
                    handler.removeTimerManual(guildId, member.getId(), targetRole.getId());
                }
            }
        }
    }

    /**
     * Hilfsmethode: Parst das JSON und vergleicht IDs.
     * Erwartet JSON Format: {"trigger_role_id": "12345"}
     * Wenn triggerData leer/null ist, feuert das Event IMMER (Global Trigger).
     */
    private boolean checkCondition(String json, String actualId) {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return true; // Keine Bedingung = Immer ausführen
        }

        // Simpler String-Check statt teurem JSON-Parser (reicht für einfache IDs)
        // Prüft, ob die tatsächliche ID im JSON-String vorkommt.
        // Für komplexere Logik solltest du eine Lib wie Jackson oder Gson nutzen.
        return json.contains(actualId);
    }
}
