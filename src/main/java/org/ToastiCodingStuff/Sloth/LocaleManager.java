package org.ToastiCodingStuff.Sloth;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Manages localization for the Sloth Discord bot.
 * Supports English (en) and German (de) languages.
 */
public class LocaleManager {
    
    private static final String BUNDLE_NAME = "messages";
    private static final Map<String, ResourceBundle> bundles = new HashMap<>();
    private static final String DEFAULT_LANGUAGE = "en";
    
    // Supported languages
    public static final String ENGLISH = "en";
    public static final String GERMAN = "de";
    
    static {
        // Pre-load bundles for supported languages
        loadBundle(ENGLISH);
        loadBundle(GERMAN);
    }
    
    /**
     * Load a resource bundle for the given language code
     */
    private static void loadBundle(String languageCode) {
        try {
            Locale locale = new Locale(languageCode);
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
            bundles.put(languageCode, bundle);
        } catch (MissingResourceException e) {
            System.err.println("Could not load resource bundle for language: " + languageCode + " - " + e.getMessage());
        }
    }
    
    /**
     * Get a localized message for the given key and language
     * @param languageCode The language code (en or de)
     * @param key The message key
     * @return The localized message, or the key if not found
     */
    public static String getMessage(String languageCode, String key) {
        ResourceBundle bundle = bundles.get(languageCode);
        if (bundle == null) {
            bundle = bundles.get(DEFAULT_LANGUAGE);
        }
        
        try {
            if (bundle != null) {
                return bundle.getString(key);
            }
        } catch (MissingResourceException e) {
            // Try default language
            try {
                ResourceBundle defaultBundle = bundles.get(DEFAULT_LANGUAGE);
                if (defaultBundle != null) {
                    return defaultBundle.getString(key);
                }
            } catch (MissingResourceException e2) {
                // Key not found in any bundle
            }
        }
        
        return key; // Return key as fallback
    }
    
    /**
     * Get a localized message with parameter substitution
     * @param languageCode The language code (en or de)
     * @param key The message key
     * @param args The arguments to substitute into the message
     * @return The localized message with substituted parameters
     */
    public static String getMessage(String languageCode, String key, Object... args) {
        String pattern = getMessage(languageCode, key);
        try {
            return MessageFormat.format(pattern, args);
        } catch (IllegalArgumentException e) {
            return pattern;
        }
    }
    
    /**
     * Check if a language code is supported
     * @param languageCode The language code to check
     * @return true if the language is supported
     */
    public static boolean isSupported(String languageCode) {
        return ENGLISH.equalsIgnoreCase(languageCode) || GERMAN.equalsIgnoreCase(languageCode);
    }
    
    /**
     * Get the display name for a language code
     * @param languageCode The language code
     * @param displayLanguage The language to display the name in
     * @return The display name of the language
     */
    public static String getLanguageDisplayName(String languageCode, String displayLanguage) {
        if (ENGLISH.equalsIgnoreCase(languageCode)) {
            return getMessage(displayLanguage, "settings.language.english");
        } else if (GERMAN.equalsIgnoreCase(languageCode)) {
            return getMessage(displayLanguage, "settings.language.german");
        }
        return languageCode;
    }
    
    /**
     * Normalize a language code to lowercase
     * @param languageCode The language code to normalize
     * @return The normalized language code, or default if invalid
     */
    public static String normalizeLanguageCode(String languageCode) {
        if (languageCode == null) {
            return DEFAULT_LANGUAGE;
        }
        String normalized = languageCode.toLowerCase().trim();
        if (isSupported(normalized)) {
            return normalized;
        }
        return DEFAULT_LANGUAGE;
    }
}
