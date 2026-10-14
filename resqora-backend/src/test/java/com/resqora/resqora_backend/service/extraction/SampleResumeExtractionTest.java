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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Slice 12: Big B's hard-case resumes (src/test/resources/samples), extracted + parsed end to end.
class SampleResumeExtractionTest {
    private final ResumeParser parser = new ResumeParser();
    private final ResumeAnalyzer analyzer = new ResumeAnalyzer();

    private String text(String file) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/samples/" + file)) {
            return file.endsWith(".pdf") ? new PdfTextExtractor().extract(in) : new DocxTextExtractor().extract(in);
        }
    }

    private ParsedResume parse(String file) throws Exception {
        return parser.parse(text(file));
    }

    @Test
    void cleanBaselineStillParsesFully() throws Exception {
        ParsedResume resume = parse("01_baseline.pdf");
        assertEquals(10, resume.skills().size());
        assertEquals(2, resume.experience().size());
        assertEquals(2, resume.projects().size());
        assertEquals(1, resume.education().size());
        assertEquals(100, analyzer.analyze(resume).overallScore());
    }

    @Test
    void twoColumnPdfIsReadColumnByColumn() throws Exception {
        ParsedResume resume = parse("02_two_column.pdf");
        assertEquals(List.of("Python", "Django", "PostgreSQL", "Docker", "React", "Git"), resume.skills());
        assertEquals(2, resume.experience().size());
        assertEquals("Flipkart", resume.experience().get(0).company());
        assertEquals("IIT Madras", resume.education().get(0).institution());
        assertEquals(2, resume.projects().size());
    }

    @Test
    void docxTablesAreReadInDocumentOrder() throws Exception {
        ParsedResume resume = parse("03_docx_tables.docx");
        assertEquals(List.of("Java", "Kotlin", "SQL", "Spring Boot", "Android", "JUnit"), resume.skills());
        assertEquals(2, resume.experience().size());
        assertEquals("Android Developer", resume.experience().get(0).title());
        assertEquals("Anna University", resume.education().get(0).institution());
        assertEquals("Chennai, Tamil Nadu", resume.location());
    }

    @Test
    void scannedPdfHasNoText() throws Exception {
        assertTrue(text("04_scanned_image_only.pdf").isBlank());
    }

    @Test
    void headingVariantsAreRecognised() throws Exception {
        ParsedResume resume = parse("05_unusual_headings.pdf");
        assertEquals(7, resume.skills().size());
        assertEquals(2, resume.experience().size());
        assertEquals(1, resume.projects().size());
        assertEquals("NIT Trichy", resume.education().get(0).institution());
        assertEquals(1, resume.certifications().size());
    }

    @Test
    void wrappedBulletLinesStayInTheirEntry() throws Exception {
        ParsedResume resume = parse("06_bullets_wrapped.pdf");
        assertEquals(2, resume.experience().size());
        assertTrue(resume.experience().get(0).description().endsWith("hottest MySQL queries."));
        assertEquals(1, resume.projects().size());
        ParsedResume longResume = parse("11_long_3page.pdf");
        assertEquals(4, longResume.experience().size());
        assertEquals(5, longResume.projects().size());
    }

    @Test
    void messyAndCleanResumesScoreDifferently() throws Exception {
        assertNotEquals(analyzer.analyze(parse("01_baseline.pdf")).overallScore(),
                analyzer.analyze(parse("06_bullets_wrapped.pdf")).overallScore());
    }

    @Test
    void variedDateFormatsAreRecognised() throws Exception {
        ParsedResume resume = parse("07_varied_dates.docx");
        assertEquals("Sept 2013 – June 2016", resume.education().get(0).dates());
        assertEquals("Inventory API", resume.projects().get(0).name());
    }

    @Test
    void multipleEducationEntriesAreSplit() throws Exception {
        ParsedResume resume = parse("08_multi_education.pdf");
        assertEquals(2, resume.education().size());
        assertEquals("MSc in Data Science", resume.education().get(0).degree());
        assertEquals("University of Kerala", resume.education().get(1).institution());
        assertEquals(2, parse("11_long_3page.pdf").education().size());
    }

    @Test
    void yearRangeIsNotThePhoneNumber() throws Exception {
        assertEquals("080-2345-6789", parse("09_phone_year.pdf").phone());
    }

    @Test
    void andListsAreSplitAndSoftSkillsDoNotScore() throws Exception {
        ParsedResume resume = parse("10_tricky_skills.docx");
        assertTrue(resume.skills().containsAll(List.of("Node.js", "Express", "C", "C++", "C#", "Go", "R")));
        assertFalse(resume.skills().contains("Java"));
        assertEquals("Bengaluru, Karnataka", resume.location());
    }

    @Test
    void findingsPointToTheLineToFix() throws Exception {
        AnalysisResult result = analyzer.analyze(parse("06_bullets_wrapped.pdf"));
        assertTrue(result.findings().stream().anyMatch(finding ->
                "Capgemini | Software Engineer | Aug 2018 - Jan 2021".equals(finding.line())));
    }

    @Test
    void singleColumnPdfHasNoGutter() throws Exception {
        assertNull(PdfTextExtractor.findGutter(List.of(), 595));
    }
}
