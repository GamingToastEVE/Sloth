package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class TimedRoleTriggerListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public TimedRoleTriggerListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    // Trigger: Wenn ein User eine Rolle bekommt ("getrole")
    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        // Wir prüfen für JEDE Rolle, die hinzugefügt wurde
        for (Role role : event.getRoles()) {
            processTrigger(event.getGuild(), event.getMember(), RoleEventType.ROLE_ADD, role.getId());
        }
    }

    // Trigger: Wenn einem User eine Rolle weggenommen wird ("removerole")
    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        for (Role role : event.getRoles()) {
            processTrigger(event.getGuild(), event.getMember(), RoleEventType.ROLE_REMOVE, role.getId());
        }
    }

    // Trigger: Wenn ein User einem Server joint.
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        processTrigger(event.getGuild(), event.getMember(), RoleEventType.MEMBER_JOIN, "");
    }

    /**
     * Die Hauptlogik: Prüft Bedingungen, vergibt Rollen und startet Timer.
     *
     * @param guild Der Server
     * @param member Der betroffene User
     * @param type Der Event-Typ (ROLE_ADD, ROLE_REMOVE etc.)
     * @param triggerEntityId Die ID des Auslösers (z.B. die ID der Rolle, die hinzugefügt wurde)
     */
    /**
     * Die Hauptlogik: Prüft Bedingungen, vergibt Rollen und startet Timer.
     */
    private void processTrigger(Guild guild, Member member, RoleEventType type, String triggerEntityId) {
        String guildId = guild.getId();

        // 1. Hole alle Regeln aus der DB für diesen Event-Typ
        List<DatabaseHandler.RoleEventData> events = handler.getRoleEventsByType(guildId, type);

        for (DatabaseHandler.RoleEventData eventConfig : events) {

            // 2. Prüfen, ob die Bedingung (trigger_data) passt
            if (!checkCondition(eventConfig.triggerData, triggerEntityId)) {
                continue;
            }

            // 3. Die Ziel-Rolle (Reward/Punishment) finden
            Role targetRole = guild.getRoleById(eventConfig.roleId);
            if (targetRole == null) {
                // System.out.println("RoleEvent " + eventConfig.id + ": Ziel-Rolle existiert nicht mehr.");
                continue;
            }

            // 4. Logik-Verzweigung basierend auf der Dauer
            if (eventConfig.durationSeconds == 0) {
                if ("REMOVE".equalsIgnoreCase(eventConfig.actionType)) {
                    guild.removeRoleFromMember(member, targetRole)
                            .reason("Auto-Trigger (Permanent): " + eventConfig.name).queue();
                } else {
                    guild.addRoleToMember(member, targetRole)
                            .reason("Auto-Trigger (Permanent): " + eventConfig.name).queue();
                }
                handler.getActiveTimersForUser(guild.getId(), member.getId()).forEach(timer -> {
                    if (timer.roleId.equals(targetRole.getId()) && timer.sourceEventId == eventConfig.id) {
                        handler.removeTimer(timer.id);
                    }
                });
            } else {
                List<DatabaseHandler.ActiveTimerData> activeTimers = handler.getActiveTimersForUser(guildId, member.getId());
                if (eventConfig.stackType.equalsIgnoreCase("EXTEND")) {
                    for (DatabaseHandler.ActiveTimerData timer : activeTimers) {
                        if (timer.roleId.equals(targetRole.getId()) && timer.sourceEventId == eventConfig.id) {
                            handler.extendTimer(guildId, timer.userId, timer.roleId, eventConfig.durationSeconds);
                            return; // Timer verlängert, kein neues Hinzufügen
                        }
                    }
                } else {
                    for (DatabaseHandler.ActiveTimerData timer : activeTimers) {
                        if (timer.roleId.equals(targetRole.getId()) && timer.sourceEventId == eventConfig.id) {
                            handler.removeTimer(timer.id);
                        }
                    }
                }
                handler.addActiveTimer(guildId, member.getId(), targetRole.getId(), eventConfig.id, eventConfig.durationSeconds);
            }
        }
    }

    /**
     * Hilfsmethode: Parst das JSON und vergleicht IDs.
     * Supports multiple conditions:
     * - New format: {"trigger_role_ids": ["12345", "67890"]} - matches if actualId is in the array
     * - Legacy format: {"trigger_role_id": "12345"} - matches if actualId equals the value
     * Wenn triggerData leer/null ist, feuert das Event IMMER (Global Trigger).
     */
    private boolean checkCondition(String json, String actualId) {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return true;
        }

        try {
            JSONObject jsonObj = new JSONObject(json);
            
            // Check for new format with multiple trigger roles
            if (jsonObj.has("trigger_role_ids")) {
                JSONArray roleIds = jsonObj.getJSONArray("trigger_role_ids");
                for (int i = 0; i < roleIds.length(); i++) {
                    if (roleIds.getString(i).equals(actualId)) {
                        return true;
                    }
                }
                return false;
            }
            
            // Check for legacy single trigger role format
            if (jsonObj.has("trigger_role_id")) {
                return jsonObj.getString("trigger_role_id").equals(actualId);
            }
            
            // Fallback: simple string contains check for other condition types
            return json.contains(actualId);
        } catch (Exception e) {
            // If JSON parsing fails, fallback to simple contains check
            return json.contains(actualId);
        }
    }
}
