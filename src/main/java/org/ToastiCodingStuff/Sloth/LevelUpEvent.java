package org.ToastiCodingStuff.Sloth;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.Event;

public class LevelUpEvent extends Event {

    private final Guild guild;
    private final Member member;
    private final int newLevel;
    private final int oldLevel;

    public LevelUpEvent(JDA api, Guild guild, Member member, int newLevel, int oldLevel) {
        super(api);
        this.guild = guild;
        this.member = member;
        this.newLevel = newLevel;
        this.oldLevel = oldLevel;
    }

    public Member getMember() {
        return member;
    }

    public Guild getGuild() {
        return guild;
    }

    public int getNewLevel() {
        return newLevel;
    }

    public int getOldLevel() {
        return oldLevel;
    }
}
