package com.resqora.resqora_backend.service.extraction;

import com.resqora.resqora_backend.model.analysis.AnalysisResult;
import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import com.resqora.resqora_backend.service.analysis.ResumeAnalyzer;
import com.resqora.resqora_backend.service.parser.ResumeParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Bug: the same resume uploaded as PDF and as DOCX got different scores, and project bullets became projects.
class SameResumeBothFormatsTest {
    private final ResumeParser parser = new ResumeParser();
    private final ResumeAnalyzer analyzer = new ResumeAnalyzer();

    // Same text as the upload path: extractor, then the shared normaliser
    private static String pdfText(InputStream in) {
        return ResumeTextNormalizer.normalize(new PdfTextExtractor().extract(in));
    }

    private static String docxText(InputStream in) {
        return ResumeTextNormalizer.normalize(new DocxTextExtractor().extract(in));
    }

    private InputStream sample(String file) {
        return getClass().getResourceAsStream("/samples/" + file);
    }

    // 15_same_resume.docx: Word "List Bullet" paragraphs (the bullet lives in numbering.xml), tab-aligned dates.
    // 15_same_resume.pdf: that DOCX exported by LibreOffice: bullets come out as U+F0B7, long bullets wrap.
    @Test
    void wordDocxAndItsPdfExportGiveIdenticalScores() {
        String docx = docxText(sample("15_same_resume.docx"));
        String pdf = pdfText(sample("15_same_resume.pdf"));
        ParsedResume fromDocx = parser.parse(docx);
        ParsedResume fromPdf = parser.parse(pdf);

        assertEquals(fromDocx, fromPdf);
        AnalysisResult docxResult = analyzer.analyze(fromDocx, docx);
        AnalysisResult pdfResult = analyzer.analyze(fromPdf, pdf);
        assertEquals(docxResult, pdfResult);
        assertEquals(20, pdfResult.projectsScore());
        assertEquals(List.of("Resqora", "Nestify"), fromPdf.projects().stream().map(ProjectEntry::name).toList());
        assertEquals("Ahmedabad, Gujarat", fromPdf.location()); // contact line wrapped after "," in the PDF
        assertTrue(fromPdf.projects().get(0).description()
                .contains("Docker and deployed it live on Render")); // wrapped bullet joined, not a new project
    }

    // Right-aligned (tab) dates and locations in sample 15 end up glued to the text in both formats.
    @Test
    void sampleFifteenExperienceEducationAndSkillsFieldsAreSplitCorrectly() {
        for (String text : List.of(docxText(sample("15_same_resume.docx")), pdfText(sample("15_same_resume.pdf")))) {
            ParsedResume resume = parser.parse(text);
            assertEquals(List.of(new ExperienceEntry("Infolabz IT Services", "Web Developer Intern", "May 2024 – Jul 2024",
                    "Built 6 responsive client pages with React and Tailwind CSS, cutting page load time by 30%. "
                            + "Wrote REST endpoints in PHP and MySQL for a booking module used by 200+ customers.")),
                    resume.experience());
            assertEquals(List.of(
                    new EducationEntry("Charotar University of Science and Technology", "Master of Computer Applications (MCA)",
                            "Expected Jul 2027", "Coursework: Software Engineering, Operating Systems, Algorithms, Data Structures",
                            "Changa, Gujarat"),
                    new EducationEntry("Gujarat University", "Bachelor of Computer Applications (BCA)", "Jul 2022 – Jun 2025",
                            "CGPA: 8.6/10", "Ahmedabad, Gujarat")), resume.education());
            assertTrue(resume.skills().contains("Tailwind CSS"));
            AnalysisResult result = analyzer.analyze(resume, text);
            assertFalse(result.findings().stream().anyMatch(f -> f.message().contains("start and end dates")));
            assertEquals(96, result.overallScore());
        }
    }

    // Experience bullets with no glyph, dates on a line AFTER the bullets, and (PDF) a word split as "full-" / "stack".
    @Test
    void glyphlessExperienceParsesTheSameFromPdfAndDocx() throws Exception {
        ParsedResume fromDocx = parser.parse(docxText(new ByteArrayInputStream(docx(EXPERIENCE_DOCX))));
        ParsedResume fromPdf = parser.parse(pdfText(new ByteArrayInputStream(pdf(EXPERIENCE_PDF))));
        List<ExperienceEntry> expected = List.of(
                new ExperienceEntry("Acme Labs", "Software Developer Trainee", "Jan 2025 – Jun 2025",
                        "Built an internal support dashboard in React for a team of twelve people with a full-stack "
                                + "Spring Boot setup. Wrote unit tests with JUnit and raised coverage from 40% to 75%."),
                new ExperienceEntry("Northwind Traders", "Data Analyst", "Jul 2025 – Present",
                        "Cleaned monthly sales data with Python and pandas for the finance reports."));
        assertEquals(expected, fromDocx.experience());
        assertEquals(expected, fromPdf.experience());
        assertEquals(analyzer.analyze(fromDocx), analyzer.analyze(fromPdf));
        assertEquals(20, analyzer.analyze(fromPdf).experienceScore());
    }

    private static final List<String> EXPERIENCE_DOCX = List.of("Test Student", "student@example.com", "EXPERIENCE",
            "Software Developer Trainee | Acme Labs",
            "Built an internal support dashboard in React for a team of twelve people with a full-stack Spring Boot setup.",
            "Wrote unit tests with JUnit and raised coverage from 40% to 75%.",
            "Jan 2025 – Jun 2025",
            "Northwind Traders | Data Analyst",
            "Cleaned monthly sales data with Python and pandas for the finance reports.",
            "Jul 2025 – Present");
    private static final List<String> EXPERIENCE_PDF = List.of("Test Student", "student@example.com", "EXPERIENCE",
            "Software Developer Trainee | Acme Labs",
            "Built an internal support dashboard in React for a team of twelve people with a full-",
            "stack Spring Boot setup.",
            "Wrote unit tests with JUnit and raised coverage from 40% to 75%.",
            "Jan 2025 – Jun 2025",
            "Northwind Traders | Data Analyst",
            "Cleaned monthly sales data with Python and pandas for the finance reports.",
            "Jul 2025 – Present");

    @Test
    void projectsExampleParsesTheSameFromPdfAndDocx() throws Exception {
        ParsedResume fromPdf = parser.parse(pdfText(new ByteArrayInputStream(projectsPdf())));
        ParsedResume fromDocx = parser.parse(docxText(new ByteArrayInputStream(projectsDocx())));

        for (ParsedResume resume : List.of(fromPdf, fromDocx)) {
            assertEquals(2, resume.projects().size());
            ProjectEntry resqora = resume.projects().get(0);
            assertEquals("Resqora", resqora.name());
            assertTrue(resqora.description().startsWith("Built a full-stack AI resume analyzer"));
            assertTrue(resqora.description().endsWith("can only access their own resumes."));
            assertEquals("Nestify", resume.projects().get(1).name());
            assertTrue(resume.projects().get(1).description().endsWith("bedrooms and price."));

            AnalysisResult result = analyzer.analyze(resume);
            assertEquals(20, result.projectsScore());
            assertFalse(result.findings().stream().anyMatch(f -> f.message().contains("one-line description")));
        }
        assertEquals(fromPdf.projects(), fromDocx.projects());
        assertEquals(analyzer.analyze(fromPdf), analyzer.analyze(fromDocx));
    }

    private static final String RESQORA = "Resqora | Spring Boot, React, Tailwind CSS, PostgreSQL | Live    Oct 2026";
    private static final String NESTIFY = "Nestify | PHP, MySQL, JavaScript, jQuery    Jun 2025";
    private static final List<String> RESQORA_BULLETS = List.of(
            "Built a full-stack AI resume analyzer that parses PDF and DOCX resumes and scores them with a rule-based engine.",
            "Secured it with Spring Security and JWT, and added a Gemini AI summary and resume chat with per-user usage limits.",
            "Added job suggestions and resume-to-job matching, then containerized the Spring Boot backend with Docker and "
                    + "deployed it live on Render with a Neon PostgreSQL database.",
            "Designed the PostgreSQL schema with Flyway migrations, and added ownership checks so each user can only "
                    + "access their own resumes.");
    private static final String NESTIFY_BULLET = "Built a house rental marketplace with separate user and agent dashboards: "
            + "agents list, edit and remove properties with photos, and users search by location, bedrooms and price.";

    private static byte[] docx(List<String> lines) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            for (String line : lines) {
                document.createParagraph().createRun().setText(line);
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] pdf(List<String> lines) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                content.setLeading(12);
                content.newLineAtOffset(40, 740);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    // DOCX written as plain paragraphs: bullets with NO glyph at all (the hardest case)
    private static byte[] projectsDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<String> lines = new java.util.ArrayList<>(List.of("Test Student", "student@example.com", "PROJECTS", RESQORA));
            lines.addAll(RESQORA_BULLETS);
            lines.add(NESTIFY);
            lines.add(NESTIFY_BULLET);
            for (String line : lines) {
                document.createParagraph().createRun().setText(line);
            }
            document.write(out);
            return out.toByteArray();
        }
    }

    // PDF with "•" glyphs and every long bullet wrapped at ~95 characters, like a real page width
    private static byte[] projectsPdf() throws Exception {
        List<String> lines = new java.util.ArrayList<>(List.of("Test Student", "student@example.com", "PROJECTS", RESQORA));
        RESQORA_BULLETS.forEach(bullet -> lines.addAll(wrap("• " + bullet)));
        lines.add(NESTIFY);
        lines.addAll(wrap("• " + NESTIFY_BULLET));
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 9);
                content.setLeading(12);
                content.newLineAtOffset(40, 740);
                for (String line : lines) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private static List<String> wrap(String text) {
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() > 95) {
                lines.add(line.toString().trim());
                line.setLength(0);
            }
            line.append(word).append(' ');
        }
        lines.add(line.toString().trim());
        return lines;
    }
}
