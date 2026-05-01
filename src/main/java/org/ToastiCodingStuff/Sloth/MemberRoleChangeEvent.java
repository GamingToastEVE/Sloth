package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.Event;

import java.util.List;

/**
 * Custom event fired when a guild member's roles change.
 * This event is based on internal role snapshots stored in the database,
 * replacing the native JDA GuildMemberRoleAddEvent / GuildMemberRoleRemoveEvent.
 */
public class MemberRoleChangeEvent extends Event {

    public enum ChangeType { ADDED, REMOVED }

    private final Guild guild;
    private final Member member;
    private final List<Role> addedRoles;
    private final List<Role> removedRoles;

    public MemberRoleChangeEvent(JDA api, Guild guild, Member member,
                                  List<Role> addedRoles, List<Role> removedRoles) {
        super(api);
        this.guild = guild;
        this.member = member;
        this.addedRoles = addedRoles;
        this.removedRoles = removedRoles;
    }

    public Guild getGuild() {
        return guild;
    }

    public Member getMember() {
        return member;
    }

    /** Roles that were added since the last snapshot. */
    public List<Role> getAddedRoles() {
        return addedRoles;
    }

    /** Roles that were removed since the last snapshot. */
    public List<Role> getRemovedRoles() {
        return removedRoles;
    }

    public boolean hasAddedRoles() {
        return !addedRoles.isEmpty();
    }

    public boolean hasRemovedRoles() {
        return !removedRoles.isEmpty();
    }
}

