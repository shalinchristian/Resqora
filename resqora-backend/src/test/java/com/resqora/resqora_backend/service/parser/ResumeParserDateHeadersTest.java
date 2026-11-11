package com.resqora.resqora_backend.service.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ResumeParserDateHeadersTest {
    private final ResumeParser parser = new ResumeParser();

    @ParameterizedTest
    @ValueSource(strings = {"Jun 2024 – Present", "June 2024 - Current", "January 2024 — Now",
            "Sept 2023 to Till date", "09/2023 - 06/2024", "2020 – 2024", "2020 to Current",
            "december 2023 — present", "Jul 2022 - July 2024"})
    void completeTrailingDateRangeIsNeverSplitIntoACompany(String dates) {
        for (String header : new String[] {"Data Analyst   " + dates + "\nAcme Labs",
                "Acme Labs   " + dates + "\nData Analyst", "Data Analyst | Acme Labs | " + dates,
                "Data Analyst at Acme Labs, " + dates, "Data Analyst — Acme Labs — " + dates}) {
            var result = parser.parse("Student\nEXPERIENCE\n" + header + "\n• Built 3 reports.");
            assertEquals(1, result.experience().size(), header);
            var job = result.experience().getFirst();
            assertEquals("Data Analyst", job.title(), header);
            assertEquals("Acme Labs", job.company(), header);
            assertEquals(dates, job.dates(), header);
            assertEquals("Built 3 reports.", job.description());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"BSc Computer Science   Jun 2020 – May 2024\nAcme University\nPune, India",
            "Acme University   Jun 2020 – May 2024\nBSc Computer Science\nPune, India",
            "BSc Computer Science | Acme University | Jun 2020 – May 2024 | Pune, India",
            "Acme University | BSc Computer Science | Jun 2020 – May 2024 | Pune, India",
            "BSc Computer Science at Acme University, Pune, India, Jun 2020 – May 2024",
            "Acme University, Pune, India — BSc Computer Science — Jun 2020 – May 2024"})
    void schoolHeaderOrdersKeepInstitutionDegreeDatesAndLocation(String header) {
        var result = parser.parse("Student\nEDUCATION\n" + header);
        assertEquals(1, result.education().size());
        var school = result.education().getFirst();
        assertEquals("Acme University", school.institution());
        assertEquals("BSc Computer Science", school.degree());
        assertEquals("Jun 2020 – May 2024", school.dates());
        assertEquals("Pune, India", school.location());
        assertNull(school.details());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Jun 2024 – Present", "June 2024 - Current", "January 2024 — Now",
            "Sept 2023 to Till date", "09/2023 - 06/2024", "2020 – 2024", "2020 to Current",
            "december 2023 — present", "Jul 2022 - July 2024"})
    void schoolsUseTheSameDateGrammarAndKeepFollowingDetails(String dates) {
        for (String header : new String[] {"BSc Computer Science " + dates + "\nAcme University",
                "Acme University " + dates + "\nBSc Computer Science",
                "BSc Computer Science | Acme University | " + dates,
                "BSc Computer Science at Acme University, " + dates}) {
            var result = parser.parse("Student\nEDUCATION\n" + header + "\nPune, India\n• GPA: 8.4/10");
            assertEquals(1, result.education().size(), header);
            var school = result.education().getFirst();
            assertEquals("Acme University", school.institution(), header);
            assertEquals("BSc Computer Science", school.degree(), header);
            assertEquals(dates, school.dates(), header);
            assertEquals("Pune, India", school.location(), header);
            assertEquals("GPA: 8.4/10", school.details(), header);
        }
    }

    @Test
    void explicitAtOrderDoesNotNeedATitleKeywordAndEmptyPipeTitleIsPreserved() {
        var at = parser.parse("Student\nEXPERIENCE\nCustomer Success at Acme Labs, 2020 - Present");
        assertEquals("Customer Success", at.experience().getFirst().title());
        assertEquals("Acme Labs", at.experience().getFirst().company());
        var empty = parser.parse("Student\nEXPERIENCE\nAcme Labs | | 2020 - Present | Built APIs");
        assertNull(empty.experience().getFirst().title());
        assertEquals("Built APIs", empty.experience().getFirst().description());
    }

    @Test
    void consecutiveStackedJobsDoNotMergeOrLoseTheCompany() {
        var result = parser.parse("Student\nEXPERIENCE\nData Analyst Jun 2024 – Present\nAcme Labs\n"
                + "• Built reports.\nNorthwind Ltd. Jan 2023 - May 2024\nSoftware Engineer\n• Shipped APIs.");
        assertEquals(2, result.experience().size());
        assertEquals("Acme Labs", result.experience().get(0).company());
        assertEquals("Northwind Ltd.", result.experience().get(1).company());
        assertEquals("Software Engineer", result.experience().get(1).title());
    }
}
