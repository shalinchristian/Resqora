package com.resqora.resqora_backend.service.builder;

import com.resqora.resqora_backend.dto.builder.BuilderExportRequest;
import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumePdfExporterTest {
    private final ResumePdfExporter exporter = new ResumePdfExporter();

    private static BuilderExportRequest request(String templateId) {
        return new BuilderExportRequest(templateId, "Rohan Kulkarni", "Backend Developer | Java, Spring Boot",
                "rohan@example.com", "+91 98765 43210", "Pune", "linkedin.com/in/rohan-kulkarni",
                "https://github.com/rohank", "Backend developer who likes small, well-tested services.",
                List.of("Java", "Spring Boot"),
                List.of(new ExperienceEntry("Acme Labs", "Software Engineer", "Jan 2024 – Present",
                        "Built the billing API for ₹2 crore of monthly payouts\n\n  Cut response time by 40%  \n")),
                List.of(new EducationEntry("COEP Technological University", "B.Tech Computer Engineering",
                        "2020 – 2024", "CGPA 8.5", "Pune")),
                List.of(new ProjectEntry("Chess Clock", "Android app\nUsed by the college’s chess club")),
                List.of("AWS Cloud Practitioner"), "pdf");
    }

    private static String text(PDDocument document) throws Exception {
        return new PDFTextStripper().getText(document);
    }

    private static int indexOf(String text, String heading) {
        int index = text.toUpperCase().indexOf(heading.toUpperCase());
        assertTrue(index >= 0, "missing '" + heading + "' in:\n" + text);
        return index;
    }

    @ParameterizedTest
    @ValueSource(strings = {"classic", "combination", "campus", "centered", "header-band"})
    void everyTemplateWritesARealTextPdf(String templateId) throws Exception {
        byte[] bytes = exporter.export(request(templateId));
        assertEquals("%PDF-", new String(bytes, 0, 5));
        try (PDDocument document = Loader.loadPDF(bytes)) {
            assertEquals(1, document.getNumberOfPages());
            assertEquals(595.0f, document.getPage(0).getMediaBox().getWidth(), 1f);
            String text = text(document);
            assertTrue(text.startsWith("Rohan Kulkarni"), text);
            assertTrue(text.contains("Backend Developer | Java, Spring Boot"));
            assertTrue(text.replaceAll(" +", " ").contains("rohan@example.com · +91 98765 43210 · Pune · "
                    + "linkedin.com/in/rohan-kulkarni · https://github.com/rohank"), text);
            for (String heading : List.of("Summary", "Experience", "Education", "Projects", "Skills", "Certifications")) {
                indexOf(text, heading);
            }
            // bullets, unicode (– ’ ₹ ·) and the trimmed description lines survive as text
            assertTrue(text.contains("• Built the billing API for ₹2 crore of monthly payouts"), text);
            assertTrue(text.contains("• Cut response time by 40%"), text);
            assertTrue(text.contains("Used by the college’s chess club"), text);
            assertTrue(text.contains("Jan 2024 – Present"), text);
            assertFalse(text.contains("?"), "no replaced characters");
            // no images anywhere: header band is a filled rectangle behind real text
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                for (var name : resources.getXObjectNames()) {
                    PDXObject object = resources.getXObject(name);
                    assertFalse(object instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject);
                }
            }
        }
    }

    @Test
    void fontsAreEmbeddedAndMatchTheTemplate() throws Exception {
        try (PDDocument classic = Loader.loadPDF(exporter.export(request("classic")));
             PDDocument centered = Loader.loadPDF(exporter.export(request("centered")))) {
            List<String> classicFonts = fonts(classic);
            assertTrue(classicFonts.stream().allMatch(f -> f.contains("Carlito")), classicFonts.toString());
            List<String> centeredFonts = fonts(centered);
            assertTrue(centeredFonts.stream().anyMatch(f -> f.contains("LiberationSerif")), centeredFonts.toString());
            for (PDDocument document : List.of(classic, centered)) {
                for (var name : document.getPage(0).getResources().getFontNames()) {
                    PDFont font = document.getPage(0).getResources().getFont(name);
                    assertTrue(font.isEmbedded(), font.getName());
                }
            }
        }
    }

    private static List<String> fonts(PDDocument document) throws Exception {
        List<String> names = new ArrayList<>();
        for (var name : document.getPage(0).getResources().getFontNames()) {
            names.add(document.getPage(0).getResources().getFont(name).getName());
        }
        return names;
    }

    @Test
    void sectionOrderFollowsTheTemplate() throws Exception {
        try (PDDocument combination = Loader.loadPDF(exporter.export(request("combination")));
             PDDocument campus = Loader.loadPDF(exporter.export(request("campus")));
             PDDocument classic = Loader.loadPDF(exporter.export(request("classic")))) {
            String c = text(combination);
            assertTrue(indexOf(c, "\nSkills") < indexOf(c, "\nExperience"));
            String p = text(campus);
            assertTrue(indexOf(p, "\nEducation") < indexOf(p, "\nExperience"));
            String k = text(classic);
            assertTrue(indexOf(k, "\nExperience") < indexOf(k, "\nEducation"));
            assertTrue(indexOf(k, "\nProjects") < indexOf(k, "\nSkills"));
        }
    }

    @Test
    void writesOnlyWhatWasSent() throws Exception {
        BuilderExportRequest minimal = new BuilderExportRequest("header-band", "Asha Nair", null, null, null, null,
                null, null, null, List.of("Python"), null, null, null, null, "pdf");
        try (PDDocument document = Loader.loadPDF(exporter.export(minimal))) {
            String text = text(document);
            assertEquals("Asha Nair\nSKILLS\nPython", text.strip().replace("\r", ""));
        }
    }

    @Test
    void longContentFlowsOntoASecondPage() throws Exception {
        List<ExperienceEntry> jobs = IntStream.range(1, 13).mapToObj(i -> new ExperienceEntry("Company " + i,
                "Engineer " + i, "2020 – 2021", "First achievement of job " + i + " with enough words to wrap onto "
                + "a second line when the page is narrow enough for that to happen naturally\nSecond achievement "
                + i + "\nThird achievement " + i)).toList();
        String longUrl = "https://example.com/" + "a".repeat(300);
        BuilderExportRequest big = new BuilderExportRequest("classic", "Overflow Test", null, "o@example.com", null,
                null, longUrl, null, "Summary", List.of("Java"), jobs, null, null, null, "pdf");
        try (PDDocument document = Loader.loadPDF(exporter.export(big))) {
            assertTrue(document.getNumberOfPages() >= 2);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            String second = stripper.getText(document);
            assertTrue(second.contains("Third achievement 12"), second);
            String all = text(document);
            for (int i = 1; i <= 12; i++) {
                assertTrue(all.contains("Engineer " + i + " "), "job " + i);
            }
            // the long link wraps instead of running off the page
            assertTrue(all.replaceAll("\\s", "").contains(longUrl), "long URL kept whole across lines");
        }
    }

    @Test
    void unknownTemplateIsRejected() {
        BuilderExportRequest bad = new BuilderExportRequest("fancy", "X", null, null, null, null, null, null, null,
                null, null, null, null, null, "pdf");
        assertThrows(IllegalArgumentException.class, () -> exporter.export(bad));
    }

    @Test
    void samplesFitOnOnePage() throws Exception {
        for (Map.Entry<String, BuilderExportRequest> sample : BuilderSamples.all().entrySet()) {
            byte[] bytes = exporter.export(sample.getValue());
            try (PDDocument document = Loader.loadPDF(bytes)) {
                assertEquals(1, document.getNumberOfPages(), sample.getKey());
                assertTrue(text(document).startsWith(sample.getValue().name()));
            }
            // set -Dbuilder.samples.dir=... to regenerate the picker sample files
            String dir = System.getProperty("builder.samples.dir");
            if (dir != null) {
                Files.createDirectories(Path.of(dir));
                Files.write(Path.of(dir, sample.getKey().replace(".docx", ".pdf")), bytes);
            }
        }
    }

    private static BuilderExportRequest everySection(String templateId) {
        return new BuilderExportRequest(templateId, "Meera Joshi", null, "meera@example.com", null, null, null, null,
                "Analyst.", List.of("SQL"),
                List.of(new ExperienceEntry("Freshcart", "Data Analyst", "2024", "Built dashboards")),
                List.of(new EducationEntry("IIT Madras", "B.Tech", "2020 – 2024", null)),
                List.of(new ProjectEntry("Churn", "Model")), List.of("AWS"), "pdf",
                List.of("Cut report time by 60%"), List.of("Hackathon finalist"), List.of("English", "Hindi"));
    }

    static final Map<String, List<String>> ORDER = Map.of(
            "classic", List.of("SUMMARY", "HIGHLIGHTS", "EXPERIENCE", "EDUCATION", "PROJECTS", "SKILLS",
                    "CERTIFICATIONS", "ACHIEVEMENTS", "LANGUAGES"),
            "centered", List.of("SUMMARY", "HIGHLIGHTS", "EXPERIENCE", "EDUCATION", "PROJECTS", "SKILLS",
                    "CERTIFICATIONS", "ACHIEVEMENTS", "LANGUAGES"),
            "header-band", List.of("SUMMARY", "HIGHLIGHTS", "EXPERIENCE", "EDUCATION", "PROJECTS", "SKILLS",
                    "CERTIFICATIONS", "ACHIEVEMENTS", "LANGUAGES"),
            "combination", List.of("SUMMARY", "SKILLS", "HIGHLIGHTS", "EXPERIENCE", "PROJECTS", "EDUCATION",
                    "CERTIFICATIONS", "ACHIEVEMENTS", "LANGUAGES"),
            "campus", List.of("SUMMARY", "HIGHLIGHTS", "EDUCATION", "PROJECTS", "EXPERIENCE", "SKILLS",
                    "CERTIFICATIONS", "ACHIEVEMENTS", "LANGUAGES"));

    @ParameterizedTest
    @ValueSource(strings = {"classic", "combination", "campus", "centered", "header-band"})
    void everySectionInFrostsOrder(String templateId) throws Exception {
        try (PDDocument document = Loader.loadPDF(exporter.export(everySection(templateId)))) {
            String text = text(document).replace("\r", "");
            List<String> headings = text.lines().map(String::strip).map(String::toUpperCase)
                    .filter(ORDER.get("classic")::contains).toList();
            assertEquals(ORDER.get(templateId), headings, text);
            assertTrue(text.contains("• Cut report time by 60%"), text);
            assertTrue(text.contains("• Hackathon finalist"), text);
            assertTrue(text.contains("\nEnglish, Hindi\n"), text);
        }
    }

    @Test
    void bulletsAndTitlesStayTogetherAcrossPageBreaks() throws Exception {
        // vary the amount of content so the page break lands at many different points
        for (int jobs = 6; jobs <= 14; jobs++) {
            for (int words = 18; words <= 30; words += 4) {
                int n = jobs;
                String filler = " lorem".repeat(words);
                List<ExperienceEntry> entries = IntStream.rangeClosed(1, n).mapToObj(j -> new ExperienceEntry(
                        "Company" + j, "Engineer" + j, "2020", "B" + j + "a" + filler + "\nB" + j + "b" + filler
                        + "\nB" + j + "c" + filler)).toList();
                BuilderExportRequest request = new BuilderExportRequest("classic", "Page Break", null, null, null,
                        null, null, null, null, null, entries, null, null, null, "pdf");
                try (PDDocument document = Loader.loadPDF(exporter.export(request))) {
                    for (int page = 2; page <= document.getNumberOfPages(); page++) {
                        List<String> before = pageLines(document, page - 1);
                        List<String> after = pageLines(document, page);
                        String last = before.get(before.size() - 1);
                        String first = after.get(0);
                        String where = jobs + " jobs, " + words + " words, page " + page + ": " + last + " | " + first;
                        // a page starts with a new bullet or a new entry, never the middle of a bullet
                        assertTrue(first.startsWith("•") || first.startsWith("Engineer"), where);
                        // a title or company line never ends a page on its own
                        assertFalse(last.startsWith("Engineer") || last.startsWith("Company"), where);
                    }
                }
            }
        }
    }

    private static List<String> pageLines(PDDocument document, int page) throws Exception {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(document).lines().map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    @Test
    void textTheFontsCannotShowIsRefusedNotPrintedAsQuestionMarks() {
        BuilderExportRequest hindi = new BuilderExportRequest("classic", "रवि कुमार", null, null, null, null, null,
                null, null, null, null, null, null, null, "pdf");
        var error = assertThrows(com.resqora.resqora_backend.exception.PdfTextNotSupportedException.class,
                () -> exporter.export(hindi));
        assertTrue(error.getMessage().contains("Devanagari (Hindi/Marathi) text"), error.getMessage());
        assertTrue(error.getMessage().contains("Name: \"रवि कुमार\""), error.getMessage());
        // invisible joiners and variation selectors are dropped, not refused
        BuilderExportRequest invisible = new BuilderExportRequest("classic", "Asha\u200D Nair\uFE0F", null, null,
                null, null, null, null, null, null, null, null, null, null, "pdf");
        assertEquals("%PDF-", new String(exporter.export(invisible), 0, 5));
    }
}
