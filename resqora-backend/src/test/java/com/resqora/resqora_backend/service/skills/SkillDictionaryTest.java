package com.resqora.resqora_backend.service.skills;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillDictionaryTest {
    private final SkillDictionary skills = SkillDictionary.getDefault();

    @Test
    void loadsAboutTwoHundredSkillsFromFile() {
        assertTrue(skills.size() >= 190, "only " + skills.size());
    }

    @Test
    void aliasesMapToOneCanonicalName() {
        assertEquals("Spring Boot", skills.canonical("springboot").orElseThrow());
        assertEquals("Spring Boot", skills.canonical("  Spring   boot ").orElseThrow());
        assertEquals("JavaScript", skills.canonical("js").orElseThrow());
        assertEquals("Node.js", skills.canonical("nodejs").orElseThrow());
        assertEquals("Node.js", skills.canonical("node.js").orElseThrow());
        assertFalse(skills.canonical("Underwater basket weaving").isPresent());
    }

    @Test
    void javaDoesNotMatchInsideJavaScript() {
        Set<String> found = skills.findIn("Frontend developer with JavaScript and TypeScript");
        assertTrue(found.contains("JavaScript"));
        assertFalse(found.contains("Java"));
    }

    @Test
    void cFamilyIsKeptApart() {
        assertEquals(Set.of("C++"), skills.findIn("Strong C++ skills"));
        assertEquals(Set.of("C#"), skills.findIn("Experience with C# required"));
        assertEquals(Set.of("C", "Python"), skills.findIn("Languages: C, Python"));
    }

    @Test
    void shortNamesAreCaseSensitiveSoCommonWordsDontMatch() {
        assertFalse(skills.findIn("ready to go and learn").contains("Go"));
        assertTrue(skills.findIn("Backend in Go and Rust").contains("Go"));
    }

    @Test
    void multiWordAliasesMatchInText() {
        Set<String> found = skills.findIn("Built REST APIs with spring boot on node js and Postgres");
        assertTrue(found.containsAll(Set.of("Spring Boot", "Node.js", "PostgreSQL", "REST")), found.toString());
    }
}
