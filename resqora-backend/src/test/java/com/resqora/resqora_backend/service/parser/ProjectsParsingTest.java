package com.resqora.resqora_backend.service.parser;

import com.resqora.resqora_backend.model.analysis.AnalysisFinding;
import com.resqora.resqora_backend.model.analysis.AnalysisResult;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import com.resqora.resqora_backend.service.analysis.ResumeAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A project = a title line, then its description lines (with or without bullet glyphs, possibly wrapped).
class ProjectsParsingTest {
    private final ResumeParser parser = new ResumeParser();

    @Test
    void bulletsWithoutGlyphsAndWrappedLinesStayInTheirProject() {
        ParsedResume resume = parser.parse("""
                Test Student
                PROJECTS
                Chat App    2024
                Built a real-time chat app with WebSockets and Redis for 50 users
                at a time, with message history.
                Weather Bot
                Added a Telegram bot that answers forecast questions.
                """);
        assertEquals(List.of("Chat App", "Weather Bot"), resume.projects().stream().map(ProjectEntry::name).toList());
        assertEquals("Built a real-time chat app with WebSockets and Redis for 50 users at a time, with message history.",
                resume.projects().get(0).description());
    }

    @Test
    void onlyTheProjectWithoutDescriptionIsFlaggedOnce() {
        AnalysisResult result = new ResumeAnalyzer().analyze(parser.parse("""
                Test Student
                PROJECTS
                Portfolio | React
                • Built a personal site.
                Todo App | Vue
                """));
        List<AnalysisFinding> descriptionFindings = result.findings().stream()
                .filter(f -> f.message().contains("one-line description")).toList();
        assertEquals(1, descriptionFindings.size());
        assertEquals("Todo App", descriptionFindings.get(0).line());
        assertEquals(result.findings().size(), result.findings().stream().distinct().count());
    }

    @Test
    void sameProjectWrittenTwiceGivesOneFinding() {
        AnalysisResult result = new ResumeAnalyzer().analyze(parser.parse("""
                Test Student
                PROJECTS
                Todo App | Vue
                Todo App | Vue
                """));
        assertEquals(1, result.findings().stream().filter(f -> f.message().contains("\"Todo App\"")).count());
        assertTrue(result.projectsScore() < 20);
    }
}
