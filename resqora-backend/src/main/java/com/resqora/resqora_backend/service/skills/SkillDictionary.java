package com.resqora.resqora_backend.service.skills;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

// The one skill list used by ResumeParser, ResumeAnalyzer (via parsed skills) and JobMatcher.
// Loaded once from src/main/resources/skills.txt, so adding a skill = editing a text file, no code change.
public final class SkillDictionary {

    private static final SkillDictionary DEFAULT = load("/skills.txt");

    // alias (lower case) -> canonical display name
    private final Map<String, String> canonicalByAlias = new LinkedHashMap<>();
    // alias -> whole-word pattern
    private final Map<String, Pattern> patterns = new LinkedHashMap<>();

    public static SkillDictionary getDefault() {
        return DEFAULT;
    }

    static SkillDictionary load(String resource) {
        try (InputStream in = SkillDictionary.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Skill dictionary not found: " + resource);
            }
            SkillDictionary dictionary = new SkillDictionary();
            new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .forEach(dictionary::addLine);
            return dictionary;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addLine(String line) {
        String[] names = line.split("\\|");
        String canonical = names[0].trim();
        for (String name : names) {
            String alias = normalize(name);
            if (alias.isEmpty() || canonicalByAlias.containsKey(alias)) {
                continue;
            }
            canonicalByAlias.put(alias, canonical);
            patterns.put(alias, wholeWord(name.trim()));
        }
    }

    // Whole-word match. Letters, digits, '+', '#' and '.' count as part of a word, so
    // "Java" does not match inside "JavaScript", "C" not inside "C++"/"C#", "node" not inside "Node.js".
    // Very short names (C, R, Go, JS, AI) are case-sensitive so ordinary words like "go" don't count.
    private static Pattern wholeWord(String name) {
        String body = Pattern.quote(name).replace(" ", "\\E[\\s-]+\\Q");
        int flags = name.length() <= 2 ? 0 : Pattern.CASE_INSENSITIVE;
        return Pattern.compile("(?<![\\p{Alnum}+#.])" + body + "(?![\\p{Alnum}+#]|\\.\\p{Alnum})", flags);
    }

    // lower case, single spaces: "Spring  Boot " -> "spring boot"
    public static String normalize(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    // Exact lookup for one skill as written in a resume, e.g. "springboot" -> "Spring Boot".
    public Optional<String> canonical(String skill) {
        return Optional.ofNullable(canonicalByAlias.get(normalize(skill)));
    }

    // All known skills mentioned anywhere in free text, in dictionary order, no duplicates.
    public Set<String> findIn(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        patterns.forEach((alias, pattern) -> {
            if (pattern.matcher(text).find()) {
                found.add(canonicalByAlias.get(alias));
            }
        });
        return found;
    }

    public int size() {
        return Set.copyOf(canonicalByAlias.values()).size();
    }
}
