package com.resqora.resqora_backend.service.parser;

import com.resqora.resqora_backend.model.analysis.AnalysisResult;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.service.analysis.ResumeAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeParserSlice12Test {
    private final ResumeParser parser = new ResumeParser();

    @Test
    void titleLineContainingProjectIsNotAHeading() {
        ParsedResume resume = parser.parse("""
                Asha Rao
                Work History
                Acme | Project Manager | 2020 - 2022
                • Ran 3 teams.
                Key Skills
                Java, Go
                """);
        assertEquals(1, resume.experience().size());
        assertEquals("Project Manager", resume.experience().get(0).title());
        assertEquals(List.of("Java", "Go"), resume.skills());
    }

    @Test
    void softSkillsAreListedButNotScored() {
        ResumeAnalyzer analyzer = new ResumeAnalyzer();
        AnalysisResult withSoft = analyzer.analyze(parser.parse("A B\nSKILLS\nJava, Go, SQL, Communication, Leadership"));
        AnalysisResult without = analyzer.analyze(parser.parse("A B\nSKILLS\nJava, Go, SQL"));
        assertEquals(without.skillsScore(), withSoft.skillsScore());
    }

    @Test
    void entryWithoutDatesGetsALineFinding() {
        AnalysisResult result = new ResumeAnalyzer().analyze(parser.parse("A B\nEXPERIENCE\nAcme | Developer\n• Built 2 APIs."));
        assertTrue(result.findings().stream().anyMatch(f -> "Acme | Developer".equals(f.line())
                && f.message().contains("dates")));
    }
}
