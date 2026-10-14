package com.resqora.resqora_backend.service.extraction;

import com.resqora.resqora_backend.model.analysis.AnalysisResult;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.service.analysis.ResumeAnalyzer;
import com.resqora.resqora_backend.service.parser.ResumeParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Slice 14: Big B's 3 unseen layouts (12-14). Before this slice they scored 21, 66 and 21.
class Slice14ExtractionTest {
    private final ResumeParser parser = new ResumeParser();
    private final ResumeAnalyzer analyzer = new ResumeAnalyzer();

    private String text(String file) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/samples/" + file)) {
            return file.endsWith(".pdf") ? new PdfTextExtractor().extract(in) : new DocxTextExtractor().extract(in);
        }
    }

    @Test
    void threeColumnPdfIsReadColumnByColumn() throws Exception {
        String text = text("12_three_column.pdf");
        assertFalse(text.contains("CONTACT EXPERIENCE EDUCATION"));
        ParsedResume resume = parser.parse(text);
        assertEquals(List.of("Java", "Spring Boot", "Angular", "MySQL", "AWS", "Jenkins"), resume.skills());
        assertEquals("Kolkata, West Bengal", resume.location());
        assertEquals(2, resume.experience().size());
        assertEquals("TCS", resume.experience().get(0).company());
        assertEquals("Java Developer", resume.experience().get(0).title());
        assertEquals("Apr 2021 - Present", resume.experience().get(0).dates());
        assertEquals("Jadavpur University", resume.education().get(0).institution());
        assertEquals(2, resume.projects().size());
        AnalysisResult result = analyzer.analyze(resume, text);
        assertTrue(result.overallScore() >= 85, "score " + result.overallScore());
        assertNotEquals(ResumeAnalyzer.LAYOUT_CATEGORY, result.findings().get(0).category());
    }

    @Test
    void capsEmojiResumeHandlesArrowBulletsInlineDotsAndDashFields() throws Exception {
        ParsedResume resume = parser.parse(text("13_caps_emoji.pdf"));
        assertEquals(List.of("React", "TypeScript", "Redux", "Jest", "Figma", "CSS"), resume.skills());
        assertEquals("Jaipur, Rajasthan", resume.location());
        assertEquals(2, resume.experience().size());
        assertEquals("Zomato", resume.experience().get(0).company());
        assertEquals("Frontend Engineer", resume.experience().get(0).title());
        assertEquals("May 2022 – Present", resume.experience().get(0).dates());
        assertTrue(resume.experience().get(0).description().startsWith("Rebuilt the menu editor"));
        assertEquals(1, resume.projects().size());
        assertEquals("ESLint plugin for ARIA checks.", resume.projects().get(0).description());
        assertTrue(analyzer.analyze(resume).overallScore() >= 85);
    }

    @Test
    void nestedDocxTablesAndTextBoxAreRead() throws Exception {
        String text = text("14_nested_tables_textbox.docx");
        assertFalse(text.contains("("), "text-box wrapper must not add brackets");
        ParsedResume resume = parser.parse(text);
        assertEquals("Farhan Qureshi", resume.name());
        assertEquals(List.of("Python", "Flask", "Docker", "PostgreSQL", "Git"), resume.skills());
        assertEquals("MANIT Bhopal", resume.education().get(0).institution());
        assertEquals(2, resume.experience().size());
        assertEquals("Byju's", resume.experience().get(0).company());
        assertEquals("Built Flask APIs for quizzes.", resume.experience().get(0).description());
        assertEquals(1, resume.projects().size());
        assertTrue(analyzer.analyze(resume).overallScore() >= 85);
    }

    @Test
    void extraBulletSymbolsStartBullets() {
        String text = String.join("\n", "Asha Rao", "asha@x.com", "EXPERIENCE", "Acme | Engineer | 2020 - 2022",
                "▪ one", "✓ two", "◦ three", "– four", "• five", "► six", "1. seven");
        ParsedResume resume = parser.parse(text);
        assertEquals(1, resume.experience().size());
        assertEquals("one two three four five six seven", resume.experience().get(0).description());
    }

    @Test
    void columnGutterFinderReturnsNoGuttersForEmptyPage() {
        assertTrue(PdfTextExtractor.findGutters(List.of(), 595).isEmpty());
    }

    @Test
    void unreadableLayoutWarningComesFirst() {
        String longText = "word ".repeat(200);
        ParsedResume empty = parser.parse("Name\n" + longText);
        AnalysisResult result = analyzer.analyze(empty, longText);
        assertEquals(ResumeAnalyzer.LAYOUT_CATEGORY, result.findings().get(0).category());
        assertEquals(ResumeAnalyzer.UNREADABLE_LAYOUT_MESSAGE, result.findings().get(0).message());
        // same score as without the check: it only adds a warning
        assertEquals(analyzer.analyze(empty).overallScore(), result.overallScore());
    }

    @Test
    void shortOrWellParsedResumesGetNoLayoutWarning() throws Exception {
        ParsedResume tiny = parser.parse("Asha\nasha@x.com");
        assertTrue(analyzer.analyze(tiny, "Asha asha@x.com").findings().stream()
                .noneMatch(f -> ResumeAnalyzer.LAYOUT_CATEGORY.equals(f.category())));
        for (String file : List.of("01_baseline.pdf", "02_two_column.pdf", "03_docx_tables.docx", "11_long_3page.pdf")) {
            String text = text(file);
            assertTrue(analyzer.analyze(parser.parse(text), text).findings().stream()
                    .noneMatch(f -> ResumeAnalyzer.LAYOUT_CATEGORY.equals(f.category())), file);
        }
    }
}
