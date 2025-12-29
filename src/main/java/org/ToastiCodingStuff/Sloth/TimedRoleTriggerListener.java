package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimedRoleTriggerListener extends ListenerAdapter {

    private final DatabaseHandler handler;
    private final JDA api;

    public TimedRoleTriggerListener(DatabaseHandler handler, JDA api) {
        this.handler = handler;
        this.api = api;
        startScheduler();
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

    private void startScheduler() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkMessageCounts();
            } catch (Exception e) {
                // Fehler fangen, damit der Scheduler nicht abstürzt
                e.printStackTrace();
            }
        }, 0, 5, TimeUnit.MINUTES); // Startet sofort, wiederholt alle 5 Min
    }

    private void checkMessageCounts() {
        api.getGuilds().forEach(guild -> {
            List<DatabaseHandler.RoleEventData> events = handler.getRoleEventsByType(guild.getId(), RoleEventType.MESSAGE_THRESHOLD);
            if (events.isEmpty()) {
                return; // Keine Events dieses Typs auf dem Server
            }
            for (DatabaseHandler.RoleEventData configData : events) {
                JSONObject config = new JSONObject(configData);
                if (config.has("message_threshold")) {
                    guild.getMembers().forEach(member -> {
                        List<DatabaseHandler.ActiveTimerData> activeTimers = handler.getActiveTimersForUser(guild.getId(), member.getId());
                        boolean hasActiveTimer = activeTimers.stream().anyMatch(timer ->
                                timer.sourceEventId == configData.id);
                        if (hasActiveTimer) {
                            return; // Timer bereits aktiv, überspringen
                        }
                        processTrigger(guild, member, RoleEventType.MESSAGE_THRESHOLD, "");
                    });

                }
            }
        });
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
            if (!checkCondition(eventConfig.triggerData, triggerEntityId, member, guild)) {
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
                if (eventConfig.instant) {
                    if ("REMOVE".equalsIgnoreCase(eventConfig.actionType)) {
                        guild.removeRoleFromMember(member, targetRole)
                                .reason("Auto-Trigger (Instant): " + eventConfig.name).queue();
                    } else {
                        guild.addRoleToMember(member, targetRole)
                                .reason("Auto-Trigger (Instant): " + eventConfig.name).queue();
                    }
                }
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
     * - Required roles: {"required_role_ids": ["12345", "67890"]} - member must have ALL these roles
     * - Threshold: {"threshold": 3} - for WARN_THRESHOLD events
     * All conditions in the JSON must be satisfied (AND logic).
     * Wenn triggerData leer/null ist, feuert das Event IMMER (Global Trigger).
     */
    private boolean checkCondition(String json, String actualId, Member member, Guild guild) {
        if (json == null || json.isEmpty() || json.equals("{}")) {
            return true;
        }

        try {
            JSONObject jsonObj = new JSONObject(json);
            
            // Check for trigger_role_ids (multiple trigger roles - OR logic within this condition)
            // If trigger_role_ids is present, the actualId must match at least one of them
            if (jsonObj.has("trigger_role_ids")) {
                JSONArray roleIds = jsonObj.getJSONArray("trigger_role_ids");
                // Empty array means no trigger roles specified - skip this check
                if (!roleIds.isEmpty()) {
                    boolean found = false;
                    for (int i = 0; i < roleIds.length(); i++) {
                        if (!roleIds.getString(i).equals(actualId)) {
                            return false;
                        } else {
                            found = true;
                        }
                    }
                    if (!found) {
                        return false;
                    }
                }
            }
            
            // Check for legacy single trigger role format
            if (jsonObj.has("trigger_role_id")) {
                if (!jsonObj.getString("trigger_role_id").equals(actualId)) {
                    return false;
                }
            }
            
            // Check for required_role_ids - member must have ALL these roles (AND logic)
            if (jsonObj.has("required_role_ids") && member != null) {
                JSONArray requiredRoleIds = jsonObj.getJSONArray("required_role_ids");
                for (int i = 0; i < requiredRoleIds.length(); i++) {
                    String requiredRoleId = requiredRoleIds.getString(i);
                    Role requiredRole = guild.getRoleById(requiredRoleId);
                    if (requiredRole == null || !member.getRoles().contains(requiredRole)) {
                        return false;
                    }
                }
            }

            if (jsonObj.has("messages_sent_threshold") && member != null) {
                int threshold = jsonObj.getInt("messages_sent_threshold");
                String date = handler.getCurrentDate();
                LocalDateTime dateTime = LocalDateTime.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                if (jsonObj.has("time_window")) {
                    long seconds  = jsonObj.getLong("messages_sent_threshold_seconds");
                    dateTime = dateTime.minusSeconds(seconds);
                }
                int messagesSent = handler.getMessagesSentByDate(guild.getId(), member.getId(), dateTime.toString());

                return messagesSent <= threshold;

            }
            
            // All conditions passed
            return true;
        } catch (JSONException e) {
            // If JSON parsing fails, fallback to simple contains check
            System.err.println("Failed to parse trigger_data JSON: " + e.getMessage());
            return json.contains(actualId);
        }
    }
    
    /**
     * Overloaded method for backward compatibility
     */
    private boolean checkCondition(String json, String actualId) {
        return checkCondition(json, actualId, null, null);
    }
}
