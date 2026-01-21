package org.ToastiCodingStuff.Sloth;

import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multi-language support manager for the bot.
 * Handles loading and retrieving translations for different languages.
 */
public class LanguageManager {

    private static LanguageManager instance;
    private final DatabaseHandler handler;

    // Cache for loaded language files: languageCode -> translations
    private final Map<String, JSONObject> languageCache = new HashMap<>();

    // Cache for guild language preferences: guildId -> languageCode
    private final Map<String, String> guildLanguageCache = new ConcurrentHashMap<>();

    // Available languages
    public static final String ENGLISH = "en";
    public static final String GERMAN = "de";
    public static final String DEFAULT_LANGUAGE = ENGLISH;

    private static final String[] AVAILABLE_LANGUAGES = {ENGLISH, GERMAN};

    public LanguageManager(DatabaseHandler handler) {
        this.handler = handler;
        instance = this;
        loadAllLanguages();
    }

    public static LanguageManager getInstance() {
        return instance;
    }

    /**
     * Load all available language files
     */
    private void loadAllLanguages() {
        for (String lang : AVAILABLE_LANGUAGES) {
            try {
                JSONObject translations = loadLanguageFile(lang);
                if (translations != null) {
                    languageCache.put(lang, translations);
                    System.out.println("[Language] Loaded language: " + lang + " (" + translations.length() + " keys)");
                }
            } catch (Exception e) {
                System.err.println("[Language] Failed to load language file for: " + lang);
                e.printStackTrace();
            }
        }
    }

    /**
     * Load a language file from resources
     */
    private JSONObject loadLanguageFile(String languageCode) {
        String resourcePath = "/lang/" + languageCode + ".json";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                System.err.println("[Language] Language file not found: " + resourcePath);
                return null;
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return new JSONObject(content);
        } catch (Exception e) {
            System.err.println("[Language] Error loading language file: " + resourcePath);
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Get the language code for a guild
     */
    public String getGuildLanguage(String guildId) {
        // Check cache first
        if (guildLanguageCache.containsKey(guildId)) {
            return guildLanguageCache.get(guildId);
        }

        // Load from database
        String language = handler.getGuildLanguage(guildId);
        if (language == null || language.isEmpty()) {
            language = DEFAULT_LANGUAGE;
        }

        // Cache the result
        guildLanguageCache.put(guildId, language);
        return language;
    }

    /**
     * Set the language for a guild
     */
    public boolean setGuildLanguage(String guildId, String languageCode) {
        if (!isValidLanguage(languageCode)) {
            return false;
        }

        boolean success = handler.updateGuildLanguage(guildId, languageCode);
        if (success) {
            guildLanguageCache.put(guildId, languageCode);
        }
        return success;
    }

    /**
     * Check if a language code is valid
     */
    public boolean isValidLanguage(String languageCode) {
        for (String lang : AVAILABLE_LANGUAGES) {
            if (lang.equals(languageCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get available languages
     */
    public String[] getAvailableLanguages() {
        return AVAILABLE_LANGUAGES;
    }

    /**
     * Get a translation for a key in the guild's language
     */
    public String get(String guildId, String key) {
        String language = getGuildLanguage(guildId);
        return getTranslation(language, key);
    }

    /**
     * Get a translation with placeholder replacements
     */
    public String get(String guildId, String key, Object... args) {
        String translation = get(guildId, key);
        try {
            return String.format(translation, args);
        } catch (Exception e) {
            return translation;
        }
    }

    /**
     * Get a translation for a specific language
     */
    public String getTranslation(String languageCode, String key) {
        JSONObject translations = languageCache.get(languageCode);

        // Fallback to default language if not found
        if (translations == null) {
            translations = languageCache.get(DEFAULT_LANGUAGE);
        }

        if (translations == null) {
            return key; // Return key as fallback
        }

        // Support nested keys with dot notation: "leveling.xp_gained"
        String[] parts = key.split("\\.");
        Object current = translations;

        for (String part : parts) {
            if (current instanceof JSONObject) {
                if (((JSONObject) current).has(part)) {
                    current = ((JSONObject) current).get(part);
                } else {
                    // Key not found, try fallback language
                    if (!languageCode.equals(DEFAULT_LANGUAGE)) {
                        return getTranslation(DEFAULT_LANGUAGE, key);
                    }
                    return key;
                }
            } else {
                return key;
            }
        }

        if (current instanceof String) {
            return (String) current;
        }

        return key;
    }

    /**
     * Get the display name of a language
     */
    public String getLanguageDisplayName(String languageCode) {
        return switch (languageCode) {
            case ENGLISH -> "English";
            case GERMAN -> "Deutsch";
            default -> languageCode;
        };
    }

    /**
     * Get the flag emoji for a language
     */
    public String getLanguageFlag(String languageCode) {
        return switch (languageCode) {
            case ENGLISH -> "🇬🇧";
            case GERMAN -> "🇩🇪";
            default -> "🌐";
        };
    }

    /**
     * Clear the guild language cache (useful when settings are changed externally)
     */
    public void clearCache(String guildId) {
        guildLanguageCache.remove(guildId);
    }

    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        guildLanguageCache.clear();
    }
}

