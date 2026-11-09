package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// highlights/achievements/languages, field limits, unsupported PDF text, rate limits, POST /api/builder/parse
@SpringBootTest(properties = {"resqora.builder.max-exports-per-window=4", "resqora.builder.max-parses-per-window=3"})
@AutoConfigureMockMvc
class BuilderFixesIntegrationTest {
    private static final String FULL = """
            {"templateId":"classic","name":"Ananya Iyer","summary":"Data analyst.",
             "highlights":["Cut report time by 60%","", "Mentored 4 interns"],
             "skills":["SQL","Power BI"],
             "experience":[{"company":"Freshcart","title":"Data Analyst","dates":"2024 – Present",
                            "description":"Built dashboards"}],
             "certifications":["Google Data Analytics"],
             "achievements":["Smart India Hackathon finalist"],
             "languages":["English","Hindi"," ","Tamil"]EXTRA}
            """;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private JwtService jwtService;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = userRepository.save(new User("builder-fix-" + UUID.randomUUID() + "@example.com", "hash"));
        token = "Bearer " + jwtService.generateToken(user);
    }

    private ResultActions export(String json) throws Exception {
        return mockMvc.perform(post("/api/builder/export").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    @Test
    void extraSectionsAreExportedInPlaceInBothFormats() throws Exception {
        byte[] pdf = export(FULL.replace("EXTRA", ",\"format\":\"pdf\"")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        String text;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(document).replace("\r", "");
        }
        assertOrder(text);
        assertTrue(text.contains("• Mentored 4 interns"), text);
        assertTrue(text.contains("\nEnglish, Hindi, Tamil\n"), text);

        byte[] docx = export(FULL.replace("EXTRA", "")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(docx))) {
            String lines = document.getParagraphs().stream().map(p -> p.getText())
                    .collect(Collectors.joining("\n"));
            assertOrder(lines);
            assertTrue(lines.contains("\nEnglish, Hindi, Tamil"), lines);
        }
    }

    private static void assertOrder(String text) {
        String upper = text.toUpperCase();
        int previous = -1;
        for (String heading : new String[] {"SUMMARY", "HIGHLIGHTS", "EXPERIENCE", "SKILLS", "CERTIFICATIONS",
                "ACHIEVEMENTS", "LANGUAGES"}) {
            int index = upper.indexOf("\n" + heading + "\n");
            assertTrue(index > previous, heading + " out of order in:\n" + text);
            previous = index;
        }
    }

    @Test
    void tooManyItemsNamesTheSectionAndTheLimit() throws Exception {
        String items = IntStream.range(0, 21).mapToObj(i -> "\"Win " + i + "\"").collect(Collectors.joining(","));
        export("{\"templateId\":\"classic\",\"name\":\"A B\",\"highlights\":[" + items + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Highlights has too many items (21, max 20)")));
    }

    @Test
    void overlongItemNamesWhichOne() throws Exception {
        export("{\"templateId\":\"classic\",\"name\":\"A B\",\"achievements\":[\"ok\",\"" + "a".repeat(201) + "\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        containsString("Achievements item 2 is too long (201 characters, max 200)")));
        export("{\"templateId\":\"classic\",\"name\":\"A B\",\"languages\":[\"" + "a".repeat(201) + "\"]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Languages item 1 is too long")));
    }

    @Test
    void overlongEntryFieldNamesTheEntry() throws Exception {
        export("{\"templateId\":\"classic\",\"name\":\"A B\",\"experience\":[{\"title\":\"X\"},"
                + "{\"title\":\"Y\",\"description\":\"" + "a".repeat(4001) + "\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        containsString("Experience 2 description is too long (4001 characters, max 4000)")));
    }

    @Test
    void hindiInPdfIs422WithACodeButDocxWorks() throws Exception {
        String body = "{\"templateId\":\"classic\",\"name\":\"Ravi Kumar\",\"experience\":[{\"title\":\"Analyst\","
                + "\"dates\":\"मार्च 2017 – जून 2018\"}]%s}";
        export(body.formatted(",\"format\":\"pdf\""))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PDF_UNSUPPORTED_TEXT"))
                .andExpect(jsonPath("$.message").value(containsString("Devanagari")))
                .andExpect(jsonPath("$.message").value(containsString("Experience 1 dates")))
                .andExpect(jsonPath("$.message").value(containsString("DOCX")));
        export(body.formatted("")).andExpect(status().isOk());
    }

    @Test
    void emojiInPdfIs422() throws Exception {
        export("{\"templateId\":\"classic\",\"name\":\"Ravi Kumar\",\"highlights\":[\"Shipped 🚀\"],\"format\":\"pdf\"}")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(containsString("Highlights item 1")));
    }

    @Test
    void exportIsRateLimitedPerUser() throws Exception {
        String minimal = "{\"templateId\":\"classic\",\"name\":\"Ravi Kumar\"}";
        for (int i = 0; i < 4; i++) {
            export(minimal).andExpect(status().isOk());
        }
        export(minimal).andExpect(status().isTooManyRequests())
                .andExpect(r -> assertTrue(Long.parseLong(r.getResponse().getHeader("Retry-After")) > 0))
                .andExpect(jsonPath("$.message").value(containsString("Too many downloads")));
        // someone else is unaffected
        User other = userRepository.save(new User("builder-fix-" + UUID.randomUUID() + "@example.com", "hash"));
        mockMvc.perform(post("/api/builder/export").header("Authorization", "Bearer " + jwtService.generateToken(other))
                .contentType(MediaType.APPLICATION_JSON).content(minimal)).andExpect(status().isOk());
    }

    private MockMultipartFile sample(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/samples/" + name)) {
            return new MockMultipartFile("file", name, "application/octet-stream", in.readAllBytes());
        }
    }

    private ResultActions parse(MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/builder/parse").file(file).header("Authorization", token));
    }

    @Test
    void parseReturnsTheFormWithoutSavingAnything() throws Exception {
        parse(sample("06_bullets_wrapped.pdf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").isNotEmpty())
                .andExpect(jsonPath("$.experience.length()").value(greaterThan(0)));
        parse(sample("07_varied_dates.docx")).andExpect(status().isOk());
        assertEquals(0, resumeRepository.countByUserId(user.getId()));
    }

    @Test
    void parseKeepsUploadRules() throws Exception {
        parse(sample("04_scanned_image_only.pdf")).andExpect(status().isUnprocessableContent());
        parse(new MockMultipartFile("file", "resume.txt", "text/plain", "hello".getBytes()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only PDF and DOCX files are supported"));
        // third call of the window, then over the limit
        parse(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]))
                .andExpect(status().isBadRequest());
        parse(sample("06_bullets_wrapped.pdf")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(containsString("Too many files read")));
        mockMvc.perform(multipart("/api/builder/parse").file(sample("06_bullets_wrapped.pdf")))
                .andExpect(status().isUnauthorized());
    }
}
