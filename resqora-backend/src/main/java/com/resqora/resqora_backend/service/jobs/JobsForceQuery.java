package com.resqora.resqora_backend.service.jobs;

import com.resqora.resqora_backend.model.resume.ExperienceEntry;
import com.resqora.resqora_backend.model.resume.ParsedResume;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/** Sends role/skills/city only, never names, contact details or the resume text. */
public record JobsForceQuery(String title, List<String> skills, String city, boolean fresher,
        boolean internship, Double experienceYears) {
    private static final Pattern DATE = Pattern.compile(
            "(?i)(?:([a-z]{3,9})\\.?\\s+|(?:(\\d{1,2})/))?((?:19|20)\\d{2})(?:-(\\d{2})(?!\\d))?|present|current|ongoing|now");
    // Deliberately conservative: an unknown/foreign location is omitted rather than sent as an Indian city.
    private static final List<String> CITIES = List.of("New Delhi", "Navi Mumbai", "Greater Noida",
            "Bengaluru", "Bangalore", "Mumbai", "Bombay", "Delhi", "Hyderabad", "Chennai", "Madras",
            "Pune", "Kolkata", "Calcutta", "Ahmedabad", "Gurugram", "Gurgaon", "Noida", "Bhubaneswar",
            "Chandigarh", "Mohali", "Jaipur", "Indore", "Kochi", "Cochin", "Thiruvananthapuram",
            "Trivandrum", "Coimbatore", "Lucknow", "Surat", "Vadodara", "Rajkot", "Nagpur", "Nashik",
            "Patna", "Ranchi", "Bhopal", "Visakhapatnam", "Vijayawada", "Mysuru", "Mysore", "Mangalore",
            "Mangaluru", "Guwahati", "Raipur", "Dehradun", "Jodhpur", "Udaipur", "Madurai", "Trichy",
            "Salem", "Tiruppur", "Thane", "Kalyan", "Faridabad", "Ghaziabad", "Meerut", "Kanpur",
            "Agra", "Varanasi", "Prayagraj", "Allahabad", "Amritsar", "Ludhiana", "Jalandhar", "Changa");

    private record Span(long start, long end) { }

    public static JobsForceQuery from(ParsedResume resume, Clock clock) {
        YearMonth now = YearMonth.now(clock);
        ExperienceEntry latest = resume.experience().stream()
                .filter(e -> e.title() != null && !e.title().isBlank())
                .max(Comparator.comparingLong((ExperienceEntry e) -> span(e.dates(), now)
                                .map(Span::end).orElse(-1L))
                        .thenComparingLong(e -> span(e.dates(), now).map(Span::start).orElse(-1L)))
                .orElse(null);
        String title = latest == null ? null : latest.title().strip();
        List<String> skills = resume.skills().stream().filter(Objects::nonNull).map(String::strip)
                .filter(s -> !s.isBlank()).distinct().limit(3).toList();
        String city = city(resume.location());
        List<ExperienceEntry> professional = resume.experience().stream()
                .filter(e -> !isIntern(e.title())).toList();
        boolean internship = !resume.experience().isEmpty() && professional.isEmpty();
        List<Span> spans = professional.stream().map(e -> span(e.dates(), now)).flatMap(Optional::stream)
                .sorted(Comparator.comparingLong(Span::start)).toList();
        long months = 0, end = Long.MIN_VALUE;
        for (Span s : spans) {
            months += Math.max(0, s.end() - Math.max(s.start(), end));
            end = Math.max(end, s.end());
        }
        // Unknown professional dates must not silently turn an experienced candidate into a fresher.
        boolean unknown = professional.stream().anyMatch(e -> span(e.dates(), now).isEmpty());
        boolean fresher = !unknown && months < 12;
        Double years = fresher || unknown ? null : Math.min(50.0, Math.floor(months / 12.0 * 10) / 10.0);
        return new JobsForceQuery(title, skills, city, fresher, internship, years);
    }

    public String text() {
        List<String> parts = new ArrayList<>();
        if (title != null) parts.add(title);
        parts.addAll(skills);
        String q = String.join(" ", parts).replaceAll("[\\r\\n]+", " ");
        return q.substring(0, Math.min(200, q.length()));
    }

    private static boolean isIntern(String title) {
        return title != null && Pattern.compile("(?i)\\bintern(?:ship)?\\b").matcher(title).find();
    }

    static String city(String location) {
        if (location == null) return null;
        return CITIES.stream().filter(c -> Pattern.compile("(?i)(?<![a-z])" + Pattern.quote(c) + "(?![a-z])")
                .matcher(location).find()).findFirst().orElse(null);
    }

    static String normalizeCity(String value) {
        if (value == null) return "";
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "bangalore" -> "bengaluru"; case "bombay" -> "mumbai";
            case "calcutta" -> "kolkata"; case "madras" -> "chennai";
            case "gurgaon" -> "gurugram"; case "cochin" -> "kochi";
            default -> value.toLowerCase(Locale.ROOT);
        };
    }

    private static Optional<Span> span(String text, YearMonth now) {
        if (text == null) return Optional.empty();
        Matcher m = DATE.matcher(text);
        List<YearMonth> dates = new ArrayList<>();
        while (m.find()) {
            if (m.group(3) == null) { dates.add(now); continue; }
            try {
                int month = 1;
                if (m.group(1) != null) {
                    month = Month.from(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
                            .parse(m.group(1).substring(0, 3).toLowerCase(Locale.ROOT)
                                    .transform(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1)))).getValue();
                } else if (m.group(2) != null) month = Integer.parseInt(m.group(2));
                else if (m.group(4) != null) month = Integer.parseInt(m.group(4));
                dates.add(YearMonth.of(Integer.parseInt(m.group(3)), month));
            } catch (DateTimeException | NumberFormatException ignored) { return Optional.empty(); }
        }
        if (dates.size() != 2) return Optional.empty();
        long start = dates.get(0).getLong(java.time.temporal.ChronoField.PROLEPTIC_MONTH);
        long end = Math.min(dates.get(1).getLong(java.time.temporal.ChronoField.PROLEPTIC_MONTH),
                now.getLong(java.time.temporal.ChronoField.PROLEPTIC_MONTH));
        return end > start ? Optional.of(new Span(start, end)) : Optional.empty();
    }
}
