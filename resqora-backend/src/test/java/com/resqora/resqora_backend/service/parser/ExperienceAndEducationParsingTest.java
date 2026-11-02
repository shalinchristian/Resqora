package com.resqora.resqora_backend.service.parser;

import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.service.analysis.ResumeAnalyzer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Header fields of experience/education entries when Word right-aligned the dates or location with a tab.
class ExperienceAndEducationParsingTest {
    private final ResumeParser parser = new ResumeParser();

    private ExperienceEntry firstJob(String header) {
        return parser.parse("Test Student\nEXPERIENCE\n" + header + "\n• Built things.").experience().get(0);
    }

    private EducationEntry firstSchool(String... lines) {
        return parser.parse("Test Student\nEDUCATION\n" + String.join("\n", lines)).education().get(0);
    }

    @Test
    void datesGluedToTheLastFieldAreSplitOff() {
        ExperienceEntry job = firstJob("TCS | Java Developer Apr 2021 – Present");
        assertEquals("TCS", job.company());
        assertEquals("Java Developer", job.title());
        assertEquals("Apr 2021 – Present", job.dates());

        ParsedResume resume = parser.parse("Test Student\nEXPERIENCE\nTCS | Java Developer Apr 2021 – Present\n• Built things.");
        assertFalse(new ResumeAnalyzer().analyze(resume).findings().stream()
                .anyMatch(f -> f.message().contains("start and end dates")));
    }

    @Test
    void dateFieldAnywhereIsTheDates() {
        assertEquals(new ExperienceEntry("TCS", "Java Developer", "2021 - 2023", null), firstJobNoBullets("TCS | 2021 - 2023 | Java Developer"));
        assertEquals(new ExperienceEntry("TCS", "Java Developer", "Apr 2021 – Present", "Mumbai"),
                firstJobNoBullets("TCS | Java Developer | Apr 2021 – Present | Mumbai"));
    }

    private ExperienceEntry firstJobNoBullets(String header) {
        return parser.parse("Test Student\nEXPERIENCE\n" + header).experience().get(0);
    }

    @Test
    void titleFirstIsSwappedOnlyWhenTheJobTitleWordIsClear() {
        // "Title | Company": first field has a job-title word, second does not -> swapped
        ExperienceEntry swapped = firstJob("Software Engineer Intern | Infosys May 2024 – Jul 2024");
        assertEquals("Infosys", swapped.company());
        assertEquals("Software Engineer Intern", swapped.title());
        // "Company | Title" stays
        assertEquals("Infosys", firstJob("Infosys | Software Engineer Intern").company());
        // no job-title word on either side, or on both: keep the default (company first)
        assertEquals("Acme", firstJob("Acme | Backend").company());
        assertEquals("Lead Consultants Ltd", firstJob("Lead Consultants Ltd | Data Analyst").company());
    }

    @Test
    void institutionLocationAndDegreeDatesAreSplitOff() {
        EducationEntry school = firstSchool("Gujarat University Ahmedabad, Gujarat",
                "Bachelor of Computer Applications (BCA) Jul 2022 – Jun 2025");
        assertEquals("Gujarat University", school.institution());
        assertEquals("Ahmedabad, Gujarat", school.location());
        assertEquals("Bachelor of Computer Applications (BCA)", school.degree());
        assertEquals("Jul 2022 – Jun 2025", school.dates());

        assertEquals(List.of("St. Xavier's College", "Ahmedabad"), nameAndLocation(firstSchool("St. Xavier's College, Ahmedabad", "BSc 2019")));
        assertEquals(List.of("University of Mumbai", "Mumbai, Maharashtra"),
                nameAndLocation(firstSchool("University of Mumbai Mumbai, Maharashtra", "BCom 2019")));
    }

    // "IIT Bombay, Mumbai" used to come out as "IIT" + "Bombay, Mumbai"
    @Test
    void acronymInstitutesAndCityAfterTheNameAreSplitCorrectly() {
        assertEquals(List.of("IIT Bombay", "Mumbai"), nameAndLocation(firstSchool("IIT Bombay, Mumbai", "B.Tech 2020 – 2024")));
        assertEquals(List.of("Indian Institute of Technology Bombay", "Mumbai"),
                nameAndLocation(firstSchool("Indian Institute of Technology Bombay, Mumbai", "B.Tech 2020 – 2024")));
        assertEquals(List.of("NIT Trichy", "Tiruchirappalli, Tamil Nadu"),
                nameAndLocation(firstSchool("NIT Trichy, Tiruchirappalli, Tamil Nadu", "B.Tech 2020 – 2024")));
        // a state after the comma means the whole tail is the place
        assertEquals(List.of("Gujarat University", "Ahmedabad, Gujarat"),
                nameAndLocation(firstSchool("Gujarat University Ahmedabad, Gujarat", "BCA 2022")));
    }

    @Test
    void institutionIsLeftAloneWhenTheRestDoesNotLookLikeALocation() {
        // no comma: could be part of the name, so nothing is guessed
        EducationEntry school = firstSchool("Nirma University Ahmedabad", "BTech 2020");
        assertEquals("Nirma University Ahmedabad", school.institution());
        assertEquals(null, school.location());
        assertEquals("Charotar University of Science and Technology",
                firstSchool("Charotar University of Science and Technology", "MCA 2027").institution());
    }

    private List<String> nameAndLocation(EducationEntry entry) {
        return List.of(entry.institution(), entry.location());
    }

    @Test
    void dateLineAfterTheBulletsBelongsToTheEntry() {
        ParsedResume resume = parser.parse("""
                Test Student
                EXPERIENCE
                Acme | Developer
                • Built an API.
                Jan 2023 – Dec 2023
                PROJECTS
                Chat App
                • Real-time chat.
                2024
                """);
        assertEquals(1, resume.experience().size());
        assertEquals("Jan 2023 – Dec 2023", resume.experience().get(0).dates());
        assertEquals("Built an API.", resume.experience().get(0).description());
        assertEquals(1, resume.projects().size());
    }

    @Test
    void tailwindCssKeepsItsFullName() {
        List<String> skills = parser.parse("Test Student\nSKILLS\nTailwind CSS, tailwindcss, React").skills();
        assertEquals(List.of("Tailwind CSS", "React"), skills);
        assertTrue(parser.parse("Test Student\nSKILLS\nTailwind").skills().contains("Tailwind CSS"));
    }
}
