package org.ToastiCodingStuff.Sloth;

import java.awt.Color;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class ColorUtil {
    private static final Map<String, String> NAMED = new HashMap<>();
    static {
        NAMED.put("red", "#FF0000");
        NAMED.put("blue", "#0000FF");
        NAMED.put("green", "#008000");
        NAMED.put("yellow", "#FFFF00");
        NAMED.put("orange", "#FFA500");
        NAMED.put("pink", "#FFC0CB");
        NAMED.put("cyan", "#00FFFF");
        NAMED.put("magenta", "#FF00FF");
        NAMED.put("white", "#FFFFFF");
        NAMED.put("black", "#000000");
        NAMED.put("gray", "#808080");
        NAMED.put("grey", "#808080");
    }

    private ColorUtil() {}

    public static boolean isValid(String input) {
        if (input == null) return false;
        String c = input.trim();
        if (c.isEmpty()) return false;
        if (c.startsWith("#")) {
            return c.matches("^#[0-9A-Fa-f]{6}$");
        }
        if (c.matches("^[0-9A-Fa-f]{6}$")) {
            return true;
        }
        return NAMED.containsKey(c.toLowerCase(Locale.ROOT));
    }

    public static String normalizeToHex(String input) {
        if (input == null || input.trim().isEmpty()) return "#3498DB"; // default blue
        String c = input.trim();
        if (c.matches("^[0-9A-Fa-f]{6}$")) {
            return "#" + c.toUpperCase(Locale.ROOT);
        }
        if (c.startsWith("#") && c.matches("^#[0-9A-Fa-f]{6}$")) {
            return c.toUpperCase(Locale.ROOT);
        }
        String hex = NAMED.get(c.toLowerCase(Locale.ROOT));
        if (hex != null) return hex;
        // fallback
        return "#3498DB";
    }

    public static Color toAwt(String input) {
        String hex = normalizeToHex(input);
        return Color.decode(hex);
    }
}

