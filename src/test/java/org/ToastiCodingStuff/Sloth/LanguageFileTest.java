package org.ToastiCodingStuff.Sloth;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the language resources: editing them by hand easily produces a trailing comma
 * or a key that exists in only one language, and neither shows up until the bot runs.
 */
class LanguageFileTest {

    private JSONObject load(String name) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("lang/" + name)) {
            assertNotNull(in, "lang/" + name + " not found on the test classpath");
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    /** Flatten to dotted paths so nested sections are compared too. */
    private void collectKeys(JSONObject object, String prefix, List<String> out) {
        for (String key : object.keySet()) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = object.get(key);
            if (value instanceof JSONObject) {
                collectKeys((JSONObject) value, path, out);
            } else {
                out.add(path);
            }
        }
    }

    private List<String> keysOf(String name) throws IOException {
        List<String> keys = new ArrayList<>();
        collectKeys(load(name), "", keys);
        return keys;
    }

    @Test
    void bothLanguageFilesAreValidJson() throws IOException {
        assertTrue(load("en.json").length() > 0);
        assertTrue(load("de.json").length() > 0);
    }

    @Test
    void englishAndGermanDefineTheSameKeys() throws IOException {
        TreeSet<String> english = new TreeSet<>(keysOf("en.json"));
        TreeSet<String> german = new TreeSet<>(keysOf("de.json"));

        TreeSet<String> onlyEnglish = new TreeSet<>(english);
        onlyEnglish.removeAll(german);
        TreeSet<String> onlyGerman = new TreeSet<>(german);
        onlyGerman.removeAll(english);

        assertTrue(onlyEnglish.isEmpty() && onlyGerman.isEmpty(),
                "only in en.json: " + onlyEnglish + "\nonly in de.json: " + onlyGerman);
    }

    @Test
    void ticketSetupKeysUsedByTheWizardExist() throws IOException {
        for (String name : List.of("en.json", "de.json")) {
            JSONObject wizard = load(name).getJSONObject("setup_wizard");
            assertTrue(wizard.has("btn_ticket_config_next"), name + " is missing btn_ticket_config_next");
        }
    }
}
