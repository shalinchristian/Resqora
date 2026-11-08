package com.resqora.resqora_backend.service.builder;

import com.resqora.resqora_backend.dto.builder.BuilderExportRequest;
import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeDocxExporterTest {
    private final ResumeDocxExporter exporter = new ResumeDocxExporter();

    private static BuilderExportRequest request(String templateId) {
        return new BuilderExportRequest(templateId, "Rohan Kulkarni", "rohan@example.com", "+91 98765 43210",
                "Pune", "Backend developer who likes small, well-tested services.",
                List.of("Java", "Spring Boot"),
                List.of(new ExperienceEntry("Acme Labs", "Software Engineer", "2024 – Present",
                        "Built the billing API\n\n  Cut response time by 40%  \n")),
                List.of(new EducationEntry("COEP Technological University", "B.Tech Computer Engineering",
                        "2020 – 2024", "CGPA 8.5", "Pune")),
                List.of(new ProjectEntry("Chess Clock", "Android app\nUsed by the college chess club")),
                List.of("AWS Cloud Practitioner"));
    }

    private static XWPFDocument open(byte[] bytes) throws IOException {
        return new XWPFDocument(new ByteArrayInputStream(bytes));
    }

    private static String text(byte[] bytes) throws IOException {
        // paragraph text as stored (POI's extractor upper-cases small caps, ATS parsers read the stored text)
        try (XWPFDocument document = open(bytes)) {
            StringBuilder text = new StringBuilder();
            document.getParagraphs().forEach(p -> text.append(p.getText()).append('\n'));
            return text.toString();
        }
    }

    private static int indexOfLine(String text, String line) {
        // headings are small caps, which POI reports in upper case
        int index = ("\n" + text.toUpperCase()).indexOf("\n" + line.toUpperCase() + "\n");
        assertTrue(index >= 0, "missing line '" + line + "' in:\n" + text);
        return index;
    }

    @ParameterizedTest
    @ValueSource(strings = {"classic", "combination", "campus", "centered", "header-band"})
    void everyTemplateWritesAValidSingleColumnDocx(String templateId) throws Exception {
        byte[] bytes = exporter.export(request(templateId));
        assertEquals('P', bytes[0]);
        assertEquals('K', bytes[1]);
        try (XWPFDocument document = open(bytes)) {
            assertTrue(document.getTables().isEmpty(), "no tables");
            assertTrue(document.getAllPictures().isEmpty(), "no pictures");
            String xml = document.getDocument().xmlText();
            assertFalse(xml.contains("w:drawing") || xml.contains("w:pict") || xml.contains("txbxContent"),
                    "no drawings, shapes or text boxes");
            assertFalse(xml.contains("w:cols w:num"), "one column");
        }
        String text = text(bytes);
        assertTrue(text.startsWith("Rohan Kulkarni"));
        assertTrue(text.contains("rohan@example.com  ·  +91 98765 43210  ·  Pune"));
        // title and dates share one line via a tab, company on the next line
        assertTrue(text.contains("Software Engineer\t2024 – Present\nAcme Labs\n"));
        assertTrue(text.contains("COEP Technological University\t2020 – 2024\nB.Tech Computer Engineering\tPune\n"));
    }

    @Test
    void classicOrderIsExperienceEducationProjectsSkills() throws Exception {
        String text = text(exporter.export(request("classic")));
        int summary = indexOfLine(text, "Summary");
        int experience = indexOfLine(text, "Experience");
        int education = indexOfLine(text, "Education");
        int projects = indexOfLine(text, "Projects");
        int skills = indexOfLine(text, "Skills");
        int certifications = indexOfLine(text, "Certifications");
        assertTrue(summary < experience && experience < education && education < projects
                && projects < skills && skills < certifications);
    }

    @Test
    void combinationPutsSkillsBeforeExperience() throws Exception {
        String text = text(exporter.export(request("combination")));
        assertTrue(indexOfLine(text, "Skills") < indexOfLine(text, "Experience"));
        assertTrue(indexOfLine(text, "Projects") < indexOfLine(text, "Education"));
    }

    @Test
    void campusPutsEducationBeforeExperience() throws Exception {
        String text = text(exporter.export(request("campus")));
        assertTrue(indexOfLine(text, "Education") < indexOfLine(text, "Experience"));
        assertTrue(indexOfLine(text, "Projects") < indexOfLine(text, "Experience"));
    }

    @Test
    void writesOnlyWhatWasSent() throws Exception {
        BuilderExportRequest minimal = new BuilderExportRequest("classic", "Asha Nair", null, null, null, null,
                List.of("Python"), null, null, null, null);
        String text = text(exporter.export(minimal));
        assertEquals("Asha Nair\nSKILLS\nPython\n", text);
        assertFalse(text.contains("Java"));
        assertFalse(text.toUpperCase().contains("EXPERIENCE"));
        assertFalse(text.toUpperCase().contains("SUMMARY"));

        String full = text(exporter.export(request("classic")));
        assertFalse(full.contains("Kotlin"));
        assertTrue(full.contains("Java, Spring Boot"));
    }

    @Test
    void descriptionLinesBecomeTrimmedBullets() throws Exception {
        try (XWPFDocument document = open(exporter.export(request("classic")))) {
            List<String> bullets = document.getParagraphs().stream()
                    .filter(p -> p.getNumID() != null).map(XWPFParagraph::getText).toList();
            assertEquals(List.of("Built the billing API", "Cut response time by 40%", "CGPA 8.5",
                    "Android app", "Used by the college chess club", "AWS Cloud Practitioner"), bullets);
        }
        assertEquals(List.of("one", "two"), ResumeDocxExporter.bullets("• one\n\n - two \n"));
    }

    @Test
    void usesTheLockedTypeScale() throws Exception {
        try (XWPFDocument document = open(exporter.export(request("classic")))) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            XWPFRun name = paragraphs.get(0).getRuns().get(0);
            assertEquals(24.0, name.getFontSizeAsDouble());
            assertTrue(name.isBold());

            XWPFParagraph heading = paragraphs.stream().filter(p -> p.getText().equalsIgnoreCase("Experience")).findFirst().orElseThrow();
            XWPFRun headingRun = heading.getRuns().get(0);
            assertEquals(12.0, headingRun.getFontSizeAsDouble());
            assertTrue(headingRun.isBold());
            assertTrue(headingRun.isSmallCaps());
            assertEquals(120, heading.getSpacingAfter());
            assertTrue(heading.getCTP().getPPr().getPBdr().isSetBottom(), "rule under heading");

            XWPFParagraph title = paragraphs.stream().filter(p -> p.getText().startsWith("Software Engineer")).findFirst().orElseThrow();
            assertTrue(title.getRuns().get(0).isBold());
            assertEquals(11.0, title.getRuns().get(0).getFontSizeAsDouble());
            assertEquals(1, title.getCTP().getPPr().getTabs().sizeOfTabArray());

            assertTrue(paragraphs.stream().filter(p -> p.getText().equals("Acme Labs")).findFirst().orElseThrow()
                    .getRuns().get(0).isItalic());
            assertEquals("Calibri", name.getFontFamily());

            var margins = document.getDocument().getBody().getSectPr().getPgMar();
            assertEquals(720L, ((java.math.BigInteger) margins.getLeft()).longValue());
        }
    }

    @Test
    void centeredCentersOnlyNameAndContactAndUsesSerif() throws Exception {
        try (XWPFDocument document = open(exporter.export(request("centered")))) {
            List<XWPFParagraph> paragraphs = document.getParagraphs();
            assertEquals(ParagraphAlignment.CENTER, paragraphs.get(0).getAlignment());
            assertEquals(ParagraphAlignment.CENTER, paragraphs.get(1).getAlignment());
            for (XWPFParagraph paragraph : paragraphs.subList(2, paragraphs.size())) {
                assertFalse(paragraph.getAlignment() == ParagraphAlignment.CENTER, paragraph.getText());
            }
            assertEquals("Times New Roman", paragraphs.get(0).getRuns().get(0).getFontFamily());
        }
        try (XWPFDocument classic = open(exporter.export(request("classic")))) {
            assertEquals(ParagraphAlignment.LEFT, classic.getParagraphs().get(0).getAlignment());
        }
    }

    @Test
    void headerBandIsShadedRealTextWithNoPictures() throws Exception {
        try (XWPFDocument document = open(exporter.export(request("header-band")))) {
            XWPFParagraph band = document.getParagraphs().get(0);
            assertTrue(band.getCTP().getPPr().isSetShd(), "header paragraph is shaded");
            assertTrue(band.getText().contains("Rohan Kulkarni"));
            assertTrue(band.getText().contains("rohan@example.com"));
            assertTrue(document.getAllPictures().isEmpty());
            assertTrue(document.getTables().isEmpty());
            for (XWPFRun run : band.getRuns()) {
                assertTrue(run.getEmbeddedPictures().isEmpty());
            }
        }
    }

    @Test
    void unknownTemplateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> exporter.export(request("fancy")));
    }

    @Test
    void fileNameIsSafe() {
        assertEquals("aaron-dsouza-resume.docx", ResumeDocxExporter.fileName("Aaron D'Souza"));
        assertEquals("priya-sharma-resume.docx", ResumeDocxExporter.fileName("  Priya  Sharma "));
        assertEquals("resume.docx", ResumeDocxExporter.fileName("\"/../\""));
        assertEquals("resume.docx", ResumeDocxExporter.fileName("प्रिया"));
    }

    @Test
    void samplesExportWithEverySection() throws Exception {
        for (Map.Entry<String, BuilderExportRequest> sample : BuilderSamples.all().entrySet()) {
            byte[] bytes = exporter.export(sample.getValue());
            String text = text(bytes);
            for (String section : List.of("Summary", "Experience", "Education", "Projects", "Skills", "Certifications")) {
                indexOfLine(text, section);
            }
            // set -Dbuilder.samples.dir=... to regenerate the picker sample files
            String dir = System.getProperty("builder.samples.dir");
            if (dir != null) {
                Files.createDirectories(Path.of(dir));
                Files.write(Path.of(dir, sample.getKey()), bytes);
            }
        }
    }
}
