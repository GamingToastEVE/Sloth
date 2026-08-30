package org.ToastiCodingStuff.Sloth;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every language key the code asks for has to exist in both language files.
 * <p>
 * LanguageManager returns the key itself when it cannot resolve it, so a typo shows up
 * as raw text like "leveling.breadcrumb_main" in the user interface and nowhere else -
 * no exception, no log line. A whole system once shipped with a stray underscore in its
 * prefix, which made all 100 of its strings render as keys.
 */
class LanguageKeyUsageTest {

    /** Matches t(anything, "section.key") - the shape every translation call has. */
    private static final Pattern CALL =
            Pattern.compile("\\bt\\s*\\(\\s*[^,()]*(?:\\([^()]*\\))?[^,]*,\\s*\"([a-z][a-zA-Z0-9_]*\\.[a-zA-Z0-9_]+)\"");

    private static final Path SOURCE_DIR = Path.of("src", "main", "java");

    private JSONObject load(String name) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("lang/" + name)) {
            assertNotNull(in, "lang/" + name + " not found on the test classpath");
            return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private boolean resolves(JSONObject root, String dottedKey) {
        String[] parts = dottedKey.split("\\.");
        Object current = root;
        for (String part : parts) {
            if (!(current instanceof JSONObject) || !((JSONObject) current).has(part)) {
                return false;
            }
            current = ((JSONObject) current).get(part);
        }
        return !(current instanceof JSONObject);
    }

    private List<String> usedKeys() throws IOException {
        List<String> keys = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_DIR)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    Matcher m = CALL.matcher(Files.readString(p, StandardCharsets.UTF_8));
                    while (m.find()) {
                        keys.add(m.group(1));
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        return keys;
    }

    @Test
    void everyKeyUsedInTheCodeExistsInBothLanguages() throws IOException {
        // Only meaningful when run from the project directory
        assumeTrue(Files.isDirectory(SOURCE_DIR), "source directory not reachable from the test working directory");

        TreeSet<String> used = new TreeSet<>(usedKeys());
        assertTrue(used.size() > 100, "the scan found only " + used.size()
                + " keys, which means the pattern stopped matching rather than that the code stopped using keys");

        for (String lang : List.of("en.json", "de.json")) {
            JSONObject root = load(lang);
            TreeSet<String> missing = new TreeSet<>();
            for (String key : used) {
                if (!resolves(root, key)) {
                    missing.add(key);
                }
            }
            assertTrue(missing.isEmpty(),
                    missing.size() + " key(s) used in the code are missing from " + lang + ": " + missing);
        }
    }
}
