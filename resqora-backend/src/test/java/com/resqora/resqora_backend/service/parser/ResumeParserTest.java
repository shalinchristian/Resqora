package com.resqora.resqora_backend.service.parser;

import com.resqora.resqora_backend.model.resume.ParsedResume;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeParserTest {
    private final ResumeParser parser = new ResumeParser();

    @Test
    void parsesResumeSectionsAndContactInformation() {
        String text = """
                Alex Johnson
                alex.johnson@example.com
                +1 555-123-4567
                Location: Toronto, Canada

                TECHNICAL SKILLS
                Java, Spring Boot | PostgreSQL

                EDUCATION
                University of Toronto | BSc Computer Science | 2018

                PROFESSIONAL EXPERIENCE
                Acme Inc | Software Engineer | 2022-2024 | Built REST APIs

                PROJECT EXPERIENCE
                Resume Parser | Built a deterministic parser

                CERTIFICATIONS
                AWS Certified Developer
                """;

        ParsedResume result = parser.parse(text);

        assertEquals("Alex Johnson", result.name());
        assertEquals("alex.johnson@example.com", result.email());
        assertEquals("+1 555-123-4567", result.phone());
        assertEquals("Toronto, Canada", result.location());
        assertEquals(List.of("Java", "Spring Boot", "PostgreSQL"), result.skills());
        assertEquals("University of Toronto", result.education().get(0).institution());
        assertEquals("BSc Computer Science", result.education().get(0).degree());
        assertEquals("Acme Inc", result.experience().get(0).company());
        assertEquals("Software Engineer", result.experience().get(0).title());
        assertEquals("Built REST APIs", result.experience().get(0).description());
        assertEquals("Resume Parser", result.projects().get(0).name());
        assertEquals("AWS Certified Developer", result.certifications().get(0));
    }

    @Test
    void supportsCommonHeadingAliases() {
        String text = """
                Sam Lee

                SKILLS:
                Java

                WORK EXPERIENCE:
                Example Company | Developer

                PROJECTS:
                Portfolio Site | Personal project
                """;

        ParsedResume result = parser.parse(text);

        assertEquals(List.of("Java"), result.skills());
        assertEquals("Example Company", result.experience().get(0).company());
        assertEquals("Portfolio Site", result.projects().get(0).name());
    }

    @Test
    void leavesMissingSectionsEmpty() {
        ParsedResume result = parser.parse("Taylor Smith\ntaylor@example.com\n");

        assertEquals("Taylor Smith", result.name());
        assertEquals("taylor@example.com", result.email());
        assertEquals(List.of(), result.skills());
        assertEquals(List.of(), result.education());
        assertEquals(List.of(), result.experience());
        assertEquals(List.of(), result.projects());
        assertEquals(List.of(), result.certifications());
        assertNull(result.phone());
        assertNull(result.location());
    }

    @Test
    void handlesNullAndBlankText() {
        ParsedResume nullResult = parser.parse(null);
        ParsedResume blankResult = parser.parse("  \n  ");

        assertNull(nullResult.name());
        assertEquals(List.of(), nullResult.skills());
        assertNull(blankResult.email());
        assertEquals(List.of(), blankResult.experience());
    }

    @Test
    void parsesRealisticResumeWithoutSectionLeakage() {
        String text = """
                Jordan Patel
                Test City, Gujarat
                P: +91 90000 00000
                jordan.patel@example.com

                SKILLS
                • Languages: Java, C#, JavaScript
                • Core Java: OOP, Exception Handling, JDBC
                • Frontend: HTML, CSS, JavaScript
                • Backend: Java, ASP.NET, PHP
                • Database: MySQL, MongoDB, PostgreSql
                • Tools: Git, GitHub, IntelliJ IDEA, Postman

                EDUCATION
                Test University of Technology
                Test City, Gujarat
                Masters in Computer Application (MCA)
                Expected July 2027
                Major in Software Development; Computer Fundamentals.
                Cumulative GPA: 7.59/10

                UNIVERSITY PROJECTS
                Campus Inventory Tracker Apr 2026
                • Developed a backend application for motorcycle inventory.
                • Designed RESTful APIs with PostgreSQL.
                Property Manager Jun 2025
                • Built a property management platform.

                CERTIFICATIONS
                Cloud Developer Certificate
                Java Foundations Certificate

                ACTIVITIES
                Coding Club Volunteer
                Hackathon Organizer
                """;

        ParsedResume result = parser.parse(text);

        assertEquals("Jordan Patel", result.name());
        assertEquals("jordan.patel@example.com", result.email());
        assertEquals("+91 90000 00000", result.phone());
        assertEquals("Test City, Gujarat", result.location());
        assertTrue(result.skills().containsAll(List.of(
                "Java", "C#", "JavaScript", "OOP", "Exception Handling", "JDBC",
                "HTML", "CSS", "ASP.NET", "PHP", "MySQL", "MongoDB", "PostgreSQL", "Git",
                "GitHub", "IntelliJ IDEA", "Postman")));
        assertEquals(1, result.education().size());
        assertEquals("Test University of Technology", result.education().get(0).institution());
        assertEquals("Masters in Computer Application (MCA)", result.education().get(0).degree());
        assertTrue(result.education().get(0).details().contains("Cumulative GPA: 7.59/10"));
        assertEquals(2, result.projects().size());
        assertEquals("Campus Inventory Tracker", result.projects().get(0).name());
        assertTrue(result.projects().get(0).description().contains("Developed a backend application"));
        assertEquals("Property Manager", result.projects().get(1).name());
        assertEquals("Property Manager", result.projects().get(1).name());
        assertTrue(result.education().stream().noneMatch(entry -> entry.institution().contains("Campus Inventory")));
        assertEquals(List.of("Cloud Developer Certificate", "Java Foundations Certificate"), result.certifications());
        assertFalse(result.certifications().contains("Coding Club Volunteer"));
        assertFalse(result.certifications().contains("Hackathon Organizer"));
    }

    @Test
    void keepsUniversityProjectsAndCertificationsAsSeparateSections() {
        ParsedResume result = parser.parse("""
                Person Name
                UNIVERSITY PROJECTS
                Campus App 2024
                - Built a campus app
                CERTIFICATIONS
                Oracle Certified Associate
                """);

        assertEquals(1, result.projects().size());
        assertEquals("Campus App", result.projects().get(0).name());
        assertEquals(List.of("Oracle Certified Associate"), result.certifications());
        assertTrue(result.projects().get(0).description().contains("Built a campus app"));
    }

    @Test
    void keepsActivitiesOutOfCertifications() {
        ParsedResume result = parser.parse("""
                Person Name
                CERTIFICATES
                Google Cloud Certificate
                ACTIVITIES
                Debate Society
                """);

        assertEquals(List.of("Google Cloud Certificate"), result.certifications());
        assertFalse(result.certifications().contains("Debate Society"));
    }
}