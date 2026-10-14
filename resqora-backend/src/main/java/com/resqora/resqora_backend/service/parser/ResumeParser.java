package com.resqora.resqora_backend.service.parser;

import com.resqora.resqora_backend.model.resume.EducationEntry;
import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.model.resume.ProjectEntry;
import com.resqora.resqora_backend.service.skills.SkillDictionary;
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
    // Month names in English (incl. "Sept") and Hindi, so "मार्च 2017" is a date too
    private static final String MONTH = "(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|"
            + "aug(?:ust)?|sept?(?:ember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?|"
            + "जनवरी|फ़रवरी|फरवरी|मार्च|अप्रैल|मई|जून|जुलाई|अगस्त|सितंबर|सितम्बर|अक्टूबर|अक्तूबर|नवंबर|नवम्बर|दिसंबर|दिसम्बर)";
    // one point in time: "Mar 2021", "Mar '19", "03/2021", "1/2016", "2018-06", "2019"
    private static final String DATE_POINT = "(?:(?<![A-Za-z])" + MONTH + "\\.?\\s+(?:(?:19|20)\\d{2}|'\\d{2})"
            + "|(?<!\\d)\\d{1,2}/(?:19|20)\\d{2}(?!\\d)"
            + "|(?<!\\d)(?:19|20)\\d{2}-(?:0[1-9]|1[0-2])(?!\\d)"
            + "|(?<!\\d)(?:19|20)\\d{2}(?!\\d))";
    private static final String DATE_END = "(?:" + DATE_POINT + "|present|current|now|ongoing|till date|to date)";
    private static final Pattern DATE_PATTERN = Pattern.compile(
            "(?iu)(?:expected\\s+)?" + DATE_POINT + "(?:\\s*(?:-|–|—|to|till)\\s*" + DATE_END + ")?");
    private static final Pattern YEAR_RANGE_PATTERN = Pattern.compile("(?:19|20)\\d{2}\\s*[-–]\\s*(?:19|20)\\d{2}");
    private static final Pattern PHONE_LABEL_PATTERN = Pattern.compile("(?i)\\b(?:phone|mobile|mob|tel|ph|cell|contact)\\b");
    private static final Pattern DEGREE_PATTERN = Pattern.compile(
            "(?i)(?<![A-Za-z])(?:master(?:s)?|mca|mba|bachelor(?:s)?|bsc|b\\.sc|msc|m\\.sc|btech|b\\.tech|mtech|m\\.tech|"
                    + "be|b\\.e|ms|m\\.s|bca|bcom|b\\.com|ba|ma|phd|ph\\.d|doctorate|associate|diploma)(?![A-Za-z])");
    private static final Pattern INSTITUTION_PATTERN = Pattern.compile(
            "(?i)\\b(?:university|institute|college|school|academy|iit|nit|iiit|iisc|polytechnic)\\b");

    // Bullet glyphs seen in real resumes: - * • ▪ ◦ ➤ ► ▸ ‣ ⁃ ○ ● ■ □ ◆ ❖ ✓ ✔ → » – · and "1." / "a)" numbering
    private static final String BULLET = "(?:[-*•▪◦➤►▸▹‣⁃○●■□◆◇❖✓✔✗→»–·]|\\d{1,2}[.)](?=\\s)|[a-z][)](?=\\s))";
    private static final Pattern BULLET_LINE = Pattern.compile("^\\s*" + BULLET + "\\s+.*$");
    private static final Pattern BULLET_PREFIX = Pattern.compile("^\\s*" + BULLET + "\\s*");
    // " — " (or " – " between non-date parts) used instead of "|" in an entry header
    private static final Pattern DASH_FIELD_SEPARATOR = Pattern.compile("\\s+[—–]\\s+");

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
                extractPhone(lines, contactLines),
                extractLocation(contactLines),
                parseSkills(sections.get(Section.SKILLS)),
                parseEducation(sections.get(Section.EDUCATION)),
                parseExperience(sections.get(Section.EXPERIENCE)),
                parseProjects(sections.get(Section.PROJECTS)),
                cleanLines(sections.get(Section.CERTIFICATIONS)));
    }

    private static Map<String, Section> buildHeadings() {
        Map<String, Section> headings = new LinkedHashMap<>();
        addHeadings(headings, Section.CONTACT, "CONTACT", "PERSONAL INFORMATION", "CONTACT DETAILS",
                "PERSONAL DETAILS", "CONTACT INFORMATION");
        addHeadings(headings, Section.SUMMARY, "SUMMARY", "PROFILE", "OBJECTIVE", "ABOUT ME", "ABOUT",
                "CAREER OBJECTIVE", "PROFESSIONAL SUMMARY", "CAREER SUMMARY", "PROFILE SUMMARY");
        addHeadings(headings, Section.SKILLS, "SKILLS", "TECHNICAL SKILLS", "TECHNICAL SKILL", "TECH STACK",
                "TECHNOLOGIES", "CORE SKILLS", "KEY SKILLS", "SKILLS AND TOOLS", "TECHNICAL PROFICIENCIES",
                "CORE COMPETENCIES", "TOOLS AND TECHNOLOGIES", "SKILL SET", "SKILLSET");
        addHeadings(headings, Section.EDUCATION, "EDUCATION", "EDUCATIONAL QUALIFICATIONS", "ACADEMIC BACKGROUND",
                "ACADEMICS", "EDUCATION AND TRAINING", "QUALIFICATIONS", "EDUCATIONAL BACKGROUND",
                "ACADEMIC QUALIFICATIONS");
        addHeadings(headings, Section.EXPERIENCE, "EXPERIENCE", "WORK EXPERIENCE", "PROFESSIONAL EXPERIENCE",
                "EMPLOYMENT", "WORK HISTORY", "EMPLOYMENT HISTORY", "CAREER HISTORY", "EXPERIENCE SUMMARY",
                "RELEVANT EXPERIENCE", "INTERNSHIPS", "INTERNSHIP", "INTERNSHIP EXPERIENCE");
        addHeadings(headings, Section.PROJECTS, "PROJECTS", "PROJECT EXPERIENCE", "UNIVERSITY PROJECTS",
                "ACADEMIC PROJECTS", "PERSONAL PROJECTS", "KEY PROJECTS", "SIDE PROJECTS", "SELECTED PROJECTS");
        addHeadings(headings, Section.CERTIFICATIONS, "CERTIFICATIONS", "CERTIFICATES", "CERTIFICATIONS AND AWARDS",
                "LICENSES AND CERTIFICATIONS", "COURSES AND CERTIFICATIONS");
        addHeadings(headings, Section.ACTIVITIES, "ACTIVITIES", "EXTRACURRICULAR ACTIVITIES");
        addHeadings(headings, Section.ACHIEVEMENTS, "ACHIEVEMENTS", "AWARDS", "HONORS AND AWARDS", "AWARDS AND ACHIEVEMENTS");
        addHeadings(headings, Section.LANGUAGES, "LANGUAGES");
        addHeadings(headings, Section.INTERESTS, "INTERESTS", "HOBBIES");
        return Map.copyOf(headings);
    }

    // Words that may appear in a heading. A short line made only of these words is a heading;
    // its first keyword picks the section ("Key Technical Skills", "Relevant Work Experience").
    private static final Map<String, Section> HEADING_KEYWORDS = Map.ofEntries(
            Map.entry("EXPERIENCE", Section.EXPERIENCE), Map.entry("EMPLOYMENT", Section.EXPERIENCE),
            Map.entry("HISTORY", Section.EXPERIENCE), Map.entry("INTERNSHIPS", Section.EXPERIENCE),
            Map.entry("SKILLS", Section.SKILLS), Map.entry("STACK", Section.SKILLS),
            Map.entry("TECHNOLOGIES", Section.SKILLS), Map.entry("COMPETENCIES", Section.SKILLS),
            Map.entry("PROFICIENCIES", Section.SKILLS),
            Map.entry("EDUCATION", Section.EDUCATION), Map.entry("QUALIFICATIONS", Section.EDUCATION),
            Map.entry("PROJECTS", Section.PROJECTS),
            Map.entry("CERTIFICATIONS", Section.CERTIFICATIONS), Map.entry("CERTIFICATES", Section.CERTIFICATIONS),
            Map.entry("SUMMARY", Section.SUMMARY), Map.entry("OBJECTIVE", Section.SUMMARY),
            Map.entry("ACHIEVEMENTS", Section.ACHIEVEMENTS), Map.entry("AWARDS", Section.ACHIEVEMENTS),
            Map.entry("LANGUAGES", Section.LANGUAGES), Map.entry("INTERESTS", Section.INTERESTS));
    private static final java.util.Set<String> HEADING_MODIFIERS = java.util.Set.of(
            "KEY", "CORE", "TECHNICAL", "TECH", "PERSONAL", "SIDE", "ACADEMIC", "PROFESSIONAL", "WORK", "CAREER",
            "RELEVANT", "SELECTED", "AND", "TOOLS", "TRAINING", "HONORS", "HONOURS", "OF", "MY", "OTHER",
            "SOFTWARE", "INDUSTRY", "EDUCATIONAL", "COURSES", "LICENSES", "NOTABLE", "MAJOR", "RECENT", "SPOKEN");

    private static Section headingOf(String line) {
        String cleaned = BULLET_PREFIX.matcher(line).replaceFirst("").trim();
        if (cleaned.isEmpty() || cleaned.length() > 40 || cleaned.matches(".*[|,;0-9].*")
                || (cleaned.indexOf(':') >= 0 && cleaned.indexOf(':') < cleaned.length() - 1)) {
            return null;
        }
        String normalized = normalizeHeadingText(cleaned);
        Section exact = HEADINGS.get(normalized);
        if (exact != null) {
            return exact;
        }
        String[] words = normalized.split(" ");
        if (words.length > 4) {
            return null;
        }
        Section section = null;
        for (String word : words) {
            Section keyword = HEADING_KEYWORDS.get(word);
            if (keyword == null && !HEADING_MODIFIERS.contains(word)) {
                return null;
            }
            if (section == null) {
                section = keyword;
            }
        }
        return section;
    }

    private static String normalizeHeadingText(String cleaned) {
        return cleaned
                .replaceFirst("[:：]$", "")
                .replace("&", " AND ")
                .replaceAll("[^A-Za-z ]", "")
                .trim()
                .replaceAll("\\s+", " ")
                .toUpperCase(Locale.ROOT);
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
            Section heading = headingOf(line);
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
            if (headingOf(line) != null) {
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
        List<String> fields = new ArrayList<>();
        for (String line : lines) {
            // "|", wide gaps and emoji icons ("📍 Jaipur, Rajasthan") all separate contact fields
            fields.addAll(Arrays.asList(cleanLine(line).split("\\s*\\|\\s*|\\s{2,}|[\\p{So}\\x{FE0F}]+")));
        }
        for (String field : fields) {
            String cleanLine = field.trim();
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

    // Prefer a number on a "Phone:/Mobile:" line, then the contact block, then anywhere.
    // A candidate needs 10-15 digits and must not be a year range like "2015 - 2024".
    private String extractPhone(List<String> allLines, List<String> contactLines) {
        String labeled = null;
        String contact = null;
        String anywhere = null;
        java.util.Set<String> contactSet = new java.util.HashSet<>(contactLines);
        for (String line : allLines) {
            Matcher matcher = PHONE_PATTERN.matcher(line);
            while (matcher.find()) {
                String candidate = matcher.group().trim();
                int digits = candidate.replaceAll("\\D", "").length();
                if (digits < 10 || digits > 15 || YEAR_RANGE_PATTERN.matcher(candidate).find()) {
                    continue;
                }
                if (labeled == null && PHONE_LABEL_PATTERN.matcher(line).find()) {
                    labeled = candidate;
                }
                if (contact == null && contactSet.contains(line)) {
                    contact = candidate;
                }
                if (anywhere == null) {
                    anywhere = candidate;
                }
            }
        }
        return labeled != null ? labeled : contact != null ? contact : anywhere;
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
            for (String skill : normalizedLine.split("\\s*[,;|•·▪◦]\\s*|\\s+/\\s+|\\s+and\\s+|\\s+&\\s+")) {
                String cleanSkill = canonicalizeSkill(cleanLine(skill));
                if (!cleanSkill.isBlank()) {
                    uniqueSkills.putIfAbsent(cleanSkill.toLowerCase(Locale.ROOT), cleanSkill);
                }
            }
        }
        return List.copyOf(uniqueSkills.values());
    }

    private String canonicalizeSkill(String skill) {
        // "springboot", "Spring boot" and "spring-boot" all become "Spring Boot" (skills.txt),
        // so the analysis counts them once. Unknown skills are kept as written.
        return SkillDictionary.getDefault().canonical(skill).orElse(skill);
    }

    // Splits the section into one entry per school: a pipe line is a whole entry; otherwise a new entry
    // starts at an institution-looking line after an entry that already has dates, or at a second degree line.
    private List<EducationEntry> parseEducation(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }

        List<EducationEntry> entries = new ArrayList<>();
        List<String> block = new ArrayList<>();
        boolean blockHasDegree = false;
        boolean blockHasDate = false;
        for (String rawLine : lines) {
            String line = cleanLine(rawLine);
            if (line.contains("|")) {
                flushEducation(block, entries);
                blockHasDegree = blockHasDate = false;
                List<String> fields = splitFields(line);
                entries.add(new EducationEntry(fieldOrNull(fields, 0), fieldOrNull(fields, 1),
                        fieldOrNull(fields, 2), fieldOrNull(fields, 3)));
                continue;
            }
            boolean degree = DEGREE_PATTERN.matcher(line).find();
            boolean date = containsDate(line);
            boolean institution = !degree && !date && looksLikeInstitution(line);
            if (!block.isEmpty() && ((institution && blockHasDate) || (degree && blockHasDegree))) {
                flushEducation(block, entries);
                blockHasDegree = blockHasDate = false;
            }
            block.add(line);
            blockHasDegree |= degree;
            blockHasDate |= date;
        }
        flushEducation(block, entries);
        return List.copyOf(entries);
    }

    private boolean looksLikeInstitution(String line) {
        if (line.matches(".*\\d.*")) {
            return false;
        }
        if (INSTITUTION_PATTERN.matcher(line).find()) {
            return true;
        }
        if (line.matches(".*[;:.()/].*") || line.split("\\s+").length > 5) {
            return false;
        }
        for (String word : line.split("\\s+")) {
            if (!word.isEmpty() && Character.isLowerCase(word.charAt(0))
                    && !word.matches("of|and|the|for|in|de|at")) {
                return false;
            }
        }
        return true;
    }

    private void flushEducation(List<String> block, List<EducationEntry> entries) {
        if (block.isEmpty()) {
            return;
        }
        int degreeIndex = firstIndexMatching(block, DEGREE_PATTERN);
        int institutionIndex = -1;
        for (int index = 0; index < block.size(); index++) {
            if (index != degreeIndex && !containsDate(block.get(index)) && looksLikeInstitution(block.get(index))) {
                institutionIndex = index;
                break;
            }
        }
        String institution = institutionIndex >= 0 ? block.get(institutionIndex) : null;
        String degree = degreeIndex >= 0 ? block.get(degreeIndex) : null;
        String dates = firstMatch(DATE_PATTERN, String.join(" ", block));
        List<String> details = new ArrayList<>();
        for (int index = 0; index < block.size(); index++) {
            String line = block.get(index);
            if (index != institutionIndex && index != degreeIndex && !line.equals(dates)) {
                details.add(line);
            }
        }
        entries.add(new EducationEntry(institution, degree, dates, details.isEmpty() ? null : String.join(" ", details)));
        block.clear();
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
        String previousText = null; // last line added to a description (bullet or its wrapped continuation)
        for (String rawLine : lines) {
            boolean bullet = isBullet(rawLine);
            String line = cleanLine(rawLine);
            if (bullet && current != null) {
                current = current.withDescription(append(current.description(), line));
                previousText = line;
            } else if (!bullet && current != null && previousText != null && isContinuation(previousText, line)) {
                // a long bullet wrapped onto the next line: same bullet, not a new entry
                current = current.withDescription(append(current.description(), line));
                previousText = line;
            } else if (!bullet && current != null && current.dates() == null && current.description() == null
                    && DATE_PATTERN.matcher(line).matches()) {
                // dates on their own line under the header ("TCS | Java Developer" / "Apr 2021 - Present")
                current = new HeaderEntry(current.header(), current.title(), line, null);
            } else if (!bullet) {
                if (current != null) {
                    entries.add(current);
                }
                List<String> fields = pipeFields ? headerFields(line) : splitFields(line);
                boolean piped = pipeFields && fields.size() > 1;
                String header = fields.get(0);
                String title = piped ? fieldOrNull(fields, 1) : null;
                String dates = piped ? fieldOrNull(fields, 2) : firstMatch(DATE_PATTERN, line);
                String description = piped ? fieldOrNull(fields, 3) : null;
                if (!piped) {
                    header = removeTrailingDate(header);
                }
                current = new HeaderEntry(header, title, dates, description);
                previousText = null;
            } else {
                current = new HeaderEntry(null, null, null, line);
                previousText = line;
            }
        }
        if (current != null) {
            entries.add(current);
        }
        return entries;
    }

    // A wrapped line starts lower-case, or follows a line that did not end a sentence,
    // and it is not itself an entry header (no "|" fields, no dates).
    private boolean isContinuation(String previous, String line) {
        if (line.contains("|")) {
            return false;
        }
        if (Character.isLowerCase(line.codePointAt(0))) {
            return true;
        }
        return !previous.matches(".*[.!?;:]$") && !containsDate(line);
    }

    private String removeTrailingDate(String line) {
        Matcher matcher = Pattern.compile("\\(?\\s*(?:" + DATE_PATTERN.pattern() + ")\\s*\\)?$").matcher(line);
        return matcher.find() && matcher.start() > 0
                ? line.substring(0, matcher.start()).replaceFirst("[\\s,–-]+$", "").trim()
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
        return BULLET_LINE.matcher(line).matches();
    }

    private String cleanLine(String line) {
        return BULLET_PREFIX.matcher(line).replaceFirst("").trim();
    }

    // Header fields split on "|", else on " — " / " – " (keeping a "May 2022 – Present" range together)
    private List<String> headerFields(String line) {
        if (line.contains("|")) {
            return splitFields(line);
        }
        String[] parts = DASH_FIELD_SEPARATOR.split(line);
        if (parts.length < 2) {
            return List.of(cleanLine(line));
        }
        List<String> fields = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            int last = fields.size() - 1;
            if (last >= 0 && isDateOnly(fields.get(last)) && isDateEndOnly(trimmed)) {
                fields.set(last, fields.get(last) + " – " + trimmed);
            } else {
                fields.add(trimmed);
            }
        }
        if (fields.size() == 2 && containsDate(fields.get(1)) && !containsDate(fields.get(0))) {
            fields.add(1, ""); // "Infosys – Jul 2020 – Apr 2022": company and dates, no title
        }
        return fields.size() > 1 ? fields : List.of(cleanLine(line));
    }

    private static final Pattern DATE_POINT_ONLY = Pattern.compile("(?iu)^(?:expected\\s+)?" + DATE_POINT + "$");
    private static final Pattern DATE_END_ONLY = Pattern.compile("(?iu)^" + DATE_END + "$");

    private boolean isDateOnly(String field) {
        return DATE_POINT_ONLY.matcher(field.trim()).matches();
    }

    private boolean isDateEndOnly(String field) {
        return DATE_END_ONLY.matcher(field.trim()).matches();
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