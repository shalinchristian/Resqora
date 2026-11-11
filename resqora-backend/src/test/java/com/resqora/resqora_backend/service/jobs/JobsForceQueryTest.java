package com.resqora.resqora_backend.service.jobs;

import com.resqora.resqora_backend.model.resume.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JobsForceQueryTest {
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC);
    static ParsedResume resume(String city, List<ExperienceEntry> experience) {
        return new ParsedResume("Private Name", "private@example.com", "9999999999", city,
                List.of("Java", "Spring Boot", "SQL", "Docker"), List.of(), experience, List.of(), List.of());
    }
    static ExperienceEntry work(String title, String dates) { return new ExperienceEntry("Private Co", title, dates, ""); }
    @Test void fresherUsesThreeSkillsAndIndianCityWithoutPersonalInformation() {
        var q = JobsForceQuery.from(resume("Pune, Maharashtra, India", List.of()), CLOCK);
        assertTrue(q.fresher()); assertNull(q.experienceYears()); assertEquals("Pune", q.city());
        assertEquals("Java Spring Boot SQL", q.text()); assertFalse(q.text().contains("Private"));
    }
    @Test void internshipOnlySetsInternshipFilter() {
        var q = JobsForceQuery.from(resume("Bengaluru", List.of(work("Software Intern", "Jan 2026 - Jun 2026"))), CLOCK);
        assertTrue(q.internship()); assertTrue(q.fresher());
    }
    @Test void latestDatedRoleWinsEvenWhenResumeIsUnsorted() {
        var q = JobsForceQuery.from(resume("Mumbai", List.of(work("Old Analyst", "2020-2022"),
                work("Java Developer", "Jan 2024 - Present"))), CLOCK);
        assertEquals("Java Developer", q.title()); assertFalse(q.fresher()); assertEquals(4.8, q.experienceYears());
    }
    @Test void overlappingEmploymentIsNotDoubleCounted() {
        var q = JobsForceQuery.from(resume("Chennai", List.of(work("Engineer", "Jan 2022 - Jan 2024"),
                work("Lead", "Jan 2023 - Jan 2025"))), CLOCK);
        assertEquals(3.0, q.experienceYears());
    }
    @Test void supportsNumericAndIsoMonthRanges() {
        var q = JobsForceQuery.from(resume("Gurgaon, India", List.of(work("Engineer", "01/2022 - 2024-07"))), CLOCK);
        assertEquals(2.5, q.experienceYears()); assertEquals("gurugram", JobsForceQuery.normalizeCity(q.city()));
    }
    @Test void foreignCityIsNotSentAsIndianCity() {
        assertNull(JobsForceQuery.from(resume("London, UK", List.of()), CLOCK).city());
    }
    @Test void unknownOrInvalidProfessionalDatesDoNotInventExperienceOrFresherStatus() {
        for (String dates : List.of("unknown", "2027 - 2028", "Dec 2024 - Jan 2023", "99/2020 - 01/2022")) {
            var q = JobsForceQuery.from(resume(null, List.of(work("Engineer", dates))), CLOCK);
            assertFalse(q.fresher(), dates); assertNull(q.experienceYears(), dates);
        }
    }
    @Test void underOneYearOfProfessionalExperienceIsFresherFriendly() {
        var q = JobsForceQuery.from(resume("Pune", List.of(work("Engineer", "Feb 2026 - Present"))), CLOCK);
        assertTrue(q.fresher()); assertNull(q.experienceYears());
    }
    @Test void queryIsLimitedToApiMaximumAndCityUsesWordBoundaries() {
        var q = JobsForceQuery.from(resume("Puneri, India", List.of(work("Engineer ".repeat(50), "2020 - 2024"))), CLOCK);
        assertEquals(200, q.text().length()); assertNull(q.city());
    }
}
