package com.resqora.resqora_backend.service.extraction;

import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.service.parser.ResumeParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class BuilderSamplesParserTest {
    private static final Path SAMPLES = Path.of("src/test/resources/builder-samples");
    private final ResumeParser parser = new ResumeParser();
    // Every file in the builder regression fixture directory participates, not just one preferred format.
    static Stream<Path> samples() throws Exception {
        try (var paths = Files.list(SAMPLES)) {
            var files = paths.filter(p -> p.toString().matches(".*\\.(pdf|docx)$")).sorted().toList();
            assertEquals(10, files.size());
            return files.stream();
        }
    }
    private ParsedResume parse(Path file) throws Exception {
        try (var input = Files.newInputStream(file)) {
            String text = file.toString().endsWith(".pdf") ? new PdfTextExtractor().extract(input)
                    : new DocxTextExtractor().extract(input);
            return parser.parse(ResumeTextNormalizer.normalize(text));
        }
    }
    @ParameterizedTest(name = "{0}")
    @MethodSource("samples")
    void everySampleHasExactlyItsTwoJobsAndActualSchoolsInBothFormats(Path file) throws Exception {
        var resume = parse(file);
        String name = file.getFileName().toString();
        String[][] jobs;
        String[][] schools;
        if (name.startsWith("campus")) {
            jobs = new String[][] {{"Web Development Intern", "Techvantage Labs, Kochi", "May 2025 – Jul 2025"},
                    {"Technical Lead", "IEEE Student Branch, Rajagiri School of Engineering", "Aug 2024 – Present"}};
            schools = new String[][] {{"Rajagiri School of Engineering and Technology (APJ Abdul Kalam Technological University)",
                    "B.Tech in Computer Science and Engineering", "2022 – 2026", "Kochi"},
                    {"Bhavan's Adarsha Vidyalaya", "Class XII (CBSE)", "2022", "Kochi"}};
        } else if (name.startsWith("centered")) {
            jobs = new String[][] {{"QA Engineer", "Moneyleaf Fintech Pvt. Ltd., Bengaluru", "Aug 2024 – Present"},
                    {"QA Trainee", "Brightpath Software Services, Mysuru", "Jan 2024 – Jul 2024"}};
            schools = new String[][] {{"Christ University", "B.Sc. in Computer Science", "2021 – 2024", "Bengaluru"}};
        } else if (name.startsWith("classic")) {
            jobs = new String[][] {{"Software Engineer", "Paynest Technologies, Mumbai", "Jul 2024 – Present"},
                    {"Software Engineering Intern", "ShipKart Logistics, Pune", "Jan 2024 – Jun 2024"}};
            schools = new String[][] {{"St. Francis Institute of Technology, University of Mumbai", "B.E. in Computer Engineering",
                    "2020 – 2024", "Mumbai"}};
        } else if (name.startsWith("combination")) {
            jobs = new String[][] {{"Data Analyst", "Freshcart Retail Pvt. Ltd., Gurugram", "Jun 2024 – Present"},
                    {"Business Analyst Intern", "Insightly Analytics, Noida", "Jan 2024 – May 2024"}};
            schools = new String[][] {{"Guru Gobind Singh Indraprastha University", "B.Tech in Information Technology",
                    "2020 – 2024", "New Delhi"}};
        } else {
            assertTrue(name.startsWith("header-band"));
            jobs = new String[][] {{"Frontend Developer", "Cloudledger Software, Ahmedabad", "Jun 2024 – Present"},
                    {"Frontend Intern", "Pixelcraft Studio, Vadodara", "Dec 2023 – May 2024"}};
            schools = new String[][] {{"Nirma University, Institute of Technology", "B.Tech in Computer Engineering",
                    "2020 – 2024", "Ahmedabad"}};
        }
        assertEquals(jobs.length, resume.experience().size());
        for (int i = 0; i < jobs.length; i++) {
            var job = resume.experience().get(i);
            assertEquals(List.of(jobs[i]), List.of(job.title(), job.company(), job.dates()));
            assertNotNull(job.description());
        }
        assertEquals(schools.length, resume.education().size());
        for (int i = 0; i < schools.length; i++) {
            var school = resume.education().get(i);
            assertEquals(List.of(schools[i]), List.of(school.institution(), school.degree(), school.dates(), school.location()));
        }
        String counterpart = name.endsWith("pdf") ? name.replaceFirst("\\.pdf$", ".docx") : name.replaceFirst("\\.docx$", ".pdf");
        var other = parse(SAMPLES.resolve(counterpart));
        assertEquals(other.experience(), resume.experience());
        assertEquals(other.education(), resume.education());
    }
}
