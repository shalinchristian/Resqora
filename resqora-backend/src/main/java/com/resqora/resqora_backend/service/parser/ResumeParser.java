package com.resqora.resqora_backend.service.parser;

import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ResumeParser {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\+?\\d[\\d ()-]{7,}\\d)(?!\\d)");
    private static final Pattern LABELED_LOCATION_PATTERN = Pattern.compile(
            "(?i)^(?:location|address|based in)\\s*[:|-]\\s*(.+)$");
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?i)(?:expected\\s+)?(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|"
                    + "jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?|"
                    + "present)\\s+\\d{4}|\\b20\\d{2}(?:\\s*[-–]\\s*(?:20\\d{2}|present))?\\b");
    private static final Pattern DEGREE_PATTERN = Pattern.compile(
            "(?i)\\b(?:master(?:s)?|mca|mba|bachelor(?:s)?|bsc|b\\.sc|btech|b\\.tech|"
                    + "be|b\\.e|phd|doctorate|associate|diploma)\\b");

    private static final Map<String, Section> HEADINGS = buildHeadings();

    public ParsedResume parse(String extractedText) {
        if (extractedText == null || extractedText.isBlank()) {
            return emptyResume();
        }

        List<String> lines = extractedText.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
        Map<Section, List<String>> sections = splitSections(lines);
        List<String> contactLines = contactLines(lines, sections.get(Section.CONTACT));

        return new ParsedResume(
                extractName(contactLines),
                extractFirst(EMAIL_PATTERN, extractedText),
                extractFirst(PHONE_PATTERN, extractedText),
                extractLocation(contactLines),
                parseSkills(sections.get(Section.SKILLS)),
                parseEducation(sections.get(Section.EDUCATION)),
                parseExperience(sections.get(Section.EXPERIENCE)),
                parseProjects(sections.get(Section.PROJECTS)),
                cleanLines(sections.get(Section.CERTIFICATIONS)));
    }

    private static Map<String, Section> buildHeadings() {
        Map<String, Section> headings = new LinkedHashMap<>();
        addHeadings(headings, Section.CONTACT, "CONTACT", "PERSONAL INFORMATION");
        addHeadings(headings, Section.SUMMARY, "SUMMARY", "PROFILE", "OBJECTIVE");
        addHeadings(headings, Section.SKILLS, "SKILLS", "TECHNICAL SKILLS", "TECHNICAL SKILL");
        addHeadings(headings, Section.EDUCATION, "EDUCATION", "EDUCATIONAL QUALIFICATIONS");
        addHeadings(headings, Section.EXPERIENCE, "EXPERIENCE", "WORK EXPERIENCE",
                "PROFESSIONAL EXPERIENCE", "EMPLOYMENT");
        addHeadings(headings, Section.PROJECTS, "PROJECTS", "PROJECT EXPERIENCE",
                "UNIVERSITY PROJECTS", "ACADEMIC PROJECTS");
        addHeadings(headings, Section.CERTIFICATIONS, "CERTIFICATIONS", "CERTIFICATES");
        addHeadings(headings, Section.ACTIVITIES, "ACTIVITIES", "EXTRACURRICULAR ACTIVITIES");
        addHeadings(headings, Section.ACHIEVEMENTS, "ACHIEVEMENTS");
        addHeadings(headings, Section.LANGUAGES, "LANGUAGES");
        addHeadings(headings, Section.INTERESTS, "INTERESTS");
        return Map.copyOf(headings);
    }

    private static void addHeadings(Map<String, Section> headings, Section section, String... names) {
        for (String name : names) {
            headings.put(name, section);
        }
    }

    private Map<Section, List<String>> splitSections(List<String> lines) {
        Map<Section, List<String>> sections = new EnumMap<>(Section.class);
        Section currentSection = null;

        for (String line : lines) {
            Section heading = HEADINGS.get(normalizeHeading(line));
            if (heading != null) {
                currentSection = heading;
            } else if (currentSection != null) {
                sections.computeIfAbsent(currentSection, ignored -> new ArrayList<>()).add(line);
            }
        }

        return sections;
    }

    private List<String> contactLines(List<String> lines, List<String> contactSection) {
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            if (HEADINGS.containsKey(normalizeHeading(line))) {
                break;
            }
            result.add(line);
        }
        if (contactSection != null) {
            result.addAll(contactSection);
        }
        return result;
    }

    private String extractName(List<String> lines) {
        for (String line : lines) {
            String cleanLine = cleanLine(line);
            if (EMAIL_PATTERN.matcher(cleanLine).find()
                    || PHONE_PATTERN.matcher(cleanLine).find()
                    || LABELED_LOCATION_PATTERN.matcher(cleanLine).matches()
                    || cleanLine.matches("(?i)^(?:p|phone|mobile|tel)\\s*:.*$")) {
                continue;
            }
            if (!cleanLine.contains(",") || !cleanLine.matches(".*\\d.*")) {
                return cleanLine;
            }
        }
        return null;
    }

    private String extractLocation(List<String> lines) {
        for (String line : lines) {
            String cleanLine = cleanLine(line);
            Matcher labeledMatcher = LABELED_LOCATION_PATTERN.matcher(cleanLine);
            if (labeledMatcher.matches()) {
                return labeledMatcher.group(1).trim();
            }
            if (cleanLine.matches("^[A-Za-z .'-]+,\\s*[A-Za-z .'-]+$")) {
                return cleanLine;
            }
        }
        return null;
    }

    private String extractFirst(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group().trim() : null;
    }

    private List<String> parseSkills(List<String> lines) {
        if (lines == null) {
            return List.of();
        }

        Map<String, String> uniqueSkills = new LinkedHashMap<>();
        for (String line : lines) {
            String normalizedLine = cleanLine(line);
            int colonIndex = normalizedLine.indexOf(':');
            if (colonIndex >= 0) {
                normalizedLine = normalizedLine.substring(colonIndex + 1);
            }
            for (String skill : normalizedLine.split("[,;|]")) {
                String cleanSkill = canonicalizeSkill(cleanLine(skill));
                if (!cleanSkill.isBlank()) {
                    uniqueSkills.putIfAbsent(cleanSkill.toLowerCase(Locale.ROOT), cleanSkill);
                }
            }
        }
        return List.copyOf(uniqueSkills.values());
    }

    private String canonicalizeSkill(String skill) {
        return switch (skill.toLowerCase(Locale.ROOT)) {
            case "postgresql", "postgres sql" -> "PostgreSQL";
            default -> skill;
        };
    }

    private List<EducationEntry> parseEducation(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }

        List<String> cleanLines = lines.stream().map(this::cleanLine).toList();
        if (cleanLines.get(0).contains("|")) {
            List<String> fields = splitFields(cleanLines.get(0));
            return List.of(new EducationEntry(
                    fieldOrNull(fields, 0),
                    fieldOrNull(fields, 1),
                    fieldOrNull(fields, 2),
                    fieldOrNull(fields, 3)));
        }
        int degreeIndex = firstIndexMatching(cleanLines, DEGREE_PATTERN);
        String institution = degreeIndex > 0 ? cleanLines.get(0) : null;
        String degree = degreeIndex >= 0 ? cleanLines.get(degreeIndex) : null;
        String dates = firstMatch(DATE_PATTERN, String.join(" ", cleanLines));
        List<String> details = new ArrayList<>();
        for (int index = 0; index < cleanLines.size(); index++) {
            if (index != 0 && index != degreeIndex && !containsDate(cleanLines.get(index))) {
                details.add(cleanLines.get(index));
            }
        }
        return List.of(new EducationEntry(institution, degree, dates, String.join(" ", details)));
    }

    private List<ExperienceEntry> parseExperience(List<String> lines) {
        return parseHeaderAndBullets(lines, true).stream()
                .map(entry -> new ExperienceEntry(entry.header(), entry.title(), entry.dates(), entry.description()))
                .toList();
    }

    private List<ProjectEntry> parseProjects(List<String> lines) {
        return parseHeaderAndBullets(lines, false).stream()
                .map(entry -> new ProjectEntry(entry.header(), entry.description()))
                .toList();
    }

    private List<HeaderEntry> parseHeaderAndBullets(List<String> lines, boolean pipeFields) {
        if (lines == null) {
            return List.of();
        }

        List<HeaderEntry> entries = new ArrayList<>();
        HeaderEntry current = null;
        for (String rawLine : lines) {
            boolean bullet = isBullet(rawLine);
            String line = cleanLine(rawLine);
            if (bullet && current != null) {
                current = current.withDescription(append(current.description(), line));
            } else if (!bullet) {
                if (current != null) {
                    entries.add(current);
                }
                List<String> fields = splitFields(line);
                String header = fields.get(0);
                String title = pipeFields ? fieldOrNull(fields, 1) : null;
                String dates = pipeFields ? fieldOrNull(fields, 2) : firstMatch(DATE_PATTERN, line);
                String description = pipeFields ? fieldOrNull(fields, 3) : null;
                if (!pipeFields) {
                    header = removeTrailingDate(header);
                }
                current = new HeaderEntry(header, title, dates, description);
            } else if (current == null) {
                current = new HeaderEntry(null, null, null, line);
            }
        }
        if (current != null) {
            entries.add(current);
        }
        return entries;
    }

    private String removeTrailingDate(String line) {
        Matcher matcher = DATE_PATTERN.matcher(line);
        return matcher.find() && matcher.end() == line.length()
                ? line.substring(0, matcher.start()).trim()
                : line;
    }

    private boolean containsDate(String line) {
        return DATE_PATTERN.matcher(line).find();
    }

    private int firstIndexMatching(List<String> lines, Pattern pattern) {
        for (int index = 0; index < lines.size(); index++) {
            if (pattern.matcher(lines.get(index)).find()) {
                return index;
            }
        }
        return -1;
    }

    private String firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group().trim() : null;
    }

    private List<String> splitFields(String line) {
        return Arrays.stream(line.split("\\s*\\|\\s*"))
                .map(this::cleanLine)
                .toList();
    }

    private List<String> cleanLines(List<String> lines) {
        return lines == null ? List.of() : lines.stream().map(this::cleanLine).toList();
    }

    private String fieldOrNull(List<String> fields, int index) {
        return fields.size() > index && !fields.get(index).isBlank() ? fields.get(index) : null;
    }

    private String append(String existing, String addition) {
        return existing == null || existing.isBlank() ? addition : existing + " " + addition;
    }

    private boolean isBullet(String line) {
        return line.matches("^\\s*[-*•▪◦]\\s+.*$");
    }

    private String cleanLine(String line) {
        return line.replaceFirst("^\\s*[-*•▪◦]\\s*", "").trim();
    }

    private String normalizeHeading(String line) {
        return cleanLine(line)
                .replaceFirst("[:：]$", "")
                .replaceAll("[^A-Za-z ]", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
    }

    private ParsedResume emptyResume() {
        return new ParsedResume(null, null, null, null, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private record HeaderEntry(String header, String title, String dates, String description) {
        private HeaderEntry withDescription(String newDescription) {
            return new HeaderEntry(header, title, dates, newDescription);
        }
    }

    private enum Section {
        CONTACT, SUMMARY, SKILLS, EDUCATION, EXPERIENCE, PROJECTS, CERTIFICATIONS,
        ACTIVITIES, ACHIEVEMENTS, LANGUAGES, INTERESTS
    }
}