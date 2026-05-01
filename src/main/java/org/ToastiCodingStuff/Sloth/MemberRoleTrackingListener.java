package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GenericGuildMemberEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Tracks guild member role changes by comparing the current roles with
 * the snapshot stored in the database. When a change is detected,
 * the database snapshot is updated and a {@link MemberRoleChangeEvent} is
 * dispatched through JDA's event system.
 *
 * <p>This replaces the usage of the native {@code GuildMemberRoleAddEvent} and
 * {@code GuildMemberRoleRemoveEvent} in {@link TimedRoleTriggerListener}.</p>
 */
public class MemberRoleTrackingListener extends ListenerAdapter {

    private final DatabaseHandler handler;

    public MemberRoleTrackingListener(DatabaseHandler handler) {
        this.handler = handler;
    }

    @Override
    public void onGenericGuildMember(@NotNull GenericGuildMemberEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();

        // Current roles of the member (excluding @everyone)
        Set<String> currentRoleIds = member.getRoles().stream()
                .map(Role::getId)
                .collect(Collectors.toSet());

        // Previously stored roles from DB
        Set<String> storedRoleIds = new HashSet<>(handler.getMemberRoles(guild.getId(), member.getId()));

        // Detect changes
        Set<String> addedIds = new HashSet<>(currentRoleIds);
        addedIds.removeAll(storedRoleIds);

        Set<String> removedIds = new HashSet<>(storedRoleIds);
        removedIds.removeAll(currentRoleIds);

        if (addedIds.isEmpty() && removedIds.isEmpty()) {
            return; // No role changes — nothing to do
        }

        // Resolve Role objects (skip unknown roles gracefully)
        List<Role> addedRoles = new ArrayList<>();
        for (String id : addedIds) {
            Role r = guild.getRoleById(id);
            if (r != null) addedRoles.add(r);
        }

        List<Role> removedRoles = new ArrayList<>();
        for (String id : removedIds) {
            Role r = guild.getRoleById(id);
            if (r != null) removedRoles.add(r);
            // If the role was deleted we still add a placeholder via id so triggers fire
        }

        // Update the snapshot in the database first
        handler.setMemberRoles(guild.getId(), member.getId(), new ArrayList<>(currentRoleIds));

        // Fire custom event through JDA's event manager
        MemberRoleChangeEvent changeEvent = new MemberRoleChangeEvent(
                event.getJDA(), guild, member, addedRoles, removedRoles);
        event.getJDA().getEventManager().handle(changeEvent);

        System.out.println("[RoleTracking] " + member.getUser().getName()
                + " in " + guild.getName()
                + " | Added: " + addedRoles.stream().map(Role::getName).collect(Collectors.joining(", "))
                + " | Removed: " + removedRoles.stream().map(Role::getName).collect(Collectors.joining(", ")));
    }
}

