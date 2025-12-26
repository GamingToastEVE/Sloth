package org.ToastiCodingStuff.Sloth;

public enum RoleEventType {
    // Member Status
    MEMBER_JOIN,
    /*
    MEMBER_BOOST,
    MEMBER_UNBOOST,
    */
    // Moderation & Warns
    WARN_THRESHOLD,
    WARN_EXPIRED,

    /* Voice Activity
    VOICE_JOIN,
    VOICE_LEAVE,

    /* Interaction
    REACTION_ADD,
    MESSAGE_SPAM,

    // Custom
    MESSAGE_THRESHOLD, */
    ROLE_REMOVE,
    ROLE_ADD;

    /**
     * Sichere Methode, um einen String aus der Datenbank in ein Enum zu wandeln.
     * Gibt null zurück, wenn der Typ nicht existiert (statt zu crashen).
     */
    public static RoleEventType fromString(String text) {
        if (text == null) return null;
        try {
            return RoleEventType.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Unknown RoleEventType in Database: " + text);
            return null;
        }
    }
}
