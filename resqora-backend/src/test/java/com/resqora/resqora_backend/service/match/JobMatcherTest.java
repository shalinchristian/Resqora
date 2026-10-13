package com.resqora.resqora_backend.service.match;

import com.resqora.resqora_backend.dto.match.JobMatchResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobMatcherTest {
    private final JobMatcher jobMatcher = new JobMatcher();

    @Test
    void scoresMatchedOverJobSkills() {
        JobMatchResponse response = jobMatcher.match(
                List.of("Java", "Spring Boot", "PostgreSQL"),
                "We need a Java developer with Spring Boot, Postgres and Docker experience.");

        assertEquals(List.of("Java", "Spring Boot", "Spring", "PostgreSQL"), response.matchedSkills());
        assertEquals(List.of("Docker"), response.missingSkills());
        assertEquals(80, response.matchScore());
    }

    @Test
    void returnsZeroWithEmptyListsWhenJobHasNoKnownSkills() {
        JobMatchResponse response = jobMatcher.match(List.of("Java"), "Friendly team player, good communication.");

        assertEquals(0, response.matchScore());
        assertTrue(response.matchedSkills().isEmpty());
        assertTrue(response.missingSkills().isEmpty());
    }

    @Test
    void wholeWordsOnly() {
        Set<String> skills = jobMatcher.extractSkills("Strong JavaScript and Node.js. Nice to have: C++.");

        assertTrue(skills.contains("JavaScript"));
        assertTrue(skills.contains("Node.js"));
        assertTrue(skills.contains("C++"));
        assertFalse(skills.contains("Java"));
    }

    @Test
    void isCaseInsensitiveAndDeterministic() {
        JobMatchResponse first = jobMatcher.match(List.of("react"), "REACT and TypeScript");
        JobMatchResponse second = jobMatcher.match(List.of("react"), "REACT and TypeScript");

        assertEquals(first, second);
        assertEquals(List.of("React"), first.matchedSkills());
        assertEquals(List.of("TypeScript"), first.missingSkills());
        assertEquals(50, first.matchScore());
    }
}
