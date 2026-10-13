package com.resqora.resqora_backend.service.match;

import com.resqora.resqora_backend.dto.match.JobMatchResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// Rule-based (no AI) job description matcher.
// 1. Find known skills in the job description using a fixed skill dictionary.
// 2. Find the same skills in the resume's parsed skills.
// 3. score = matched / skills found in job description * 100
@Component
public class JobMatcher {

    // alias (lower case) -> canonical display name
    private static final Map<String, String> SKILLS = new LinkedHashMap<>();

    static {
        for (String skill : List.of(
                "Java", "Python", "JavaScript", "TypeScript", "C++", "C#", "Kotlin", "PHP", "Ruby", "Swift",
                "Scala", "Rust", "SQL", "HTML", "CSS", "Bash",
                "Spring Boot", "Spring", "Spring Security", "Hibernate", "JPA", "Maven", "Gradle", "JUnit",
                "Mockito", "React", "Angular", "Vue", "Node.js", "Express", "Next.js", "Django", "Flask",
                "FastAPI", ".NET", "Tailwind", "Bootstrap", "Redux",
                "PostgreSQL", "MySQL", "MongoDB", "Redis", "Oracle", "SQLite", "Elasticsearch",
                "Docker", "Kubernetes", "AWS", "Azure", "GCP", "Jenkins", "Git", "GitHub", "GitLab",
                "Linux", "CI/CD", "Terraform", "Kafka", "RabbitMQ", "Microservices", "REST", "GraphQL",
                "JWT", "OAuth", "Postman", "Swagger", "Agile", "Scrum",
                "Machine Learning", "Deep Learning", "TensorFlow", "PyTorch", "Pandas", "NumPy", "Power BI",
                "Excel", "Figma")) {
            SKILLS.put(skill.toLowerCase(Locale.ROOT), skill);
        }
        // a few common spellings mapped to one name (same idea as ResumeParser.canonicalizeSkill)
        SKILLS.put("postgres", "PostgreSQL");
        SKILLS.put("postgres sql", "PostgreSQL");
        SKILLS.put("nodejs", "Node.js");
        SKILLS.put("node", "Node.js");
        SKILLS.put("reactjs", "React");
        SKILLS.put("react.js", "React");
        SKILLS.put("restful", "REST");
        SKILLS.put("rest api", "REST");
        SKILLS.put("springboot", "Spring Boot");
        SKILLS.put("k8s", "Kubernetes");
        SKILLS.put("golang", "Go");
    }

    private static final Map<String, Pattern> PATTERNS = new LinkedHashMap<>();

    static {
        // whole-word match: "Java" must not match inside "JavaScript"
        SKILLS.keySet().forEach(alias -> PATTERNS.put(alias, Pattern.compile(
                "(?<![\\p{Alnum}+#.])" + Pattern.quote(alias) + "(?![\\p{Alnum}+#]|\\.\\p{Alnum})",
                Pattern.CASE_INSENSITIVE)));
    }

    public JobMatchResponse match(List<String> resumeSkills, String jobDescription) {
        Set<String> jobSkills = extractSkills(jobDescription);
        if (jobSkills.isEmpty()) {
            // no recognisable skills in the job description: nothing to compare against
            return new JobMatchResponse(0, List.of(), List.of());
        }

        Set<String> resumeSkillNames = new LinkedHashSet<>();
        for (String skill : resumeSkills) {
            resumeSkillNames.add(skill.toLowerCase(Locale.ROOT));
        }
        extractSkills(String.join(", ", resumeSkills))
                .forEach(skill -> resumeSkillNames.add(skill.toLowerCase(Locale.ROOT)));

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String skill : jobSkills) {
            if (resumeSkillNames.contains(skill.toLowerCase(Locale.ROOT))) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }

        int score = (int) Math.round(matched.size() * 100.0 / jobSkills.size());
        return new JobMatchResponse(score, List.copyOf(matched), List.copyOf(missing));
    }

    // Returns canonical skill names found in the text, in dictionary order, without duplicates.
    Set<String> extractSkills(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        PATTERNS.forEach((alias, pattern) -> {
            if (pattern.matcher(text).find()) {
                found.add(SKILLS.get(alias));
            }
        });
        return found;
    }
}
