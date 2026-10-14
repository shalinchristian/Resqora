package com.resqora.resqora_backend.service.match;

import com.resqora.resqora_backend.dto.match.JobMatchResponse;
import com.resqora.resqora_backend.service.skills.SkillDictionary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// rule-based (no AI) job description matcher.
// 1. find known skills in the JD using the shared skill dictionary (skills.txt)
// 2. find the same skills in the resume's parsed skills
// 3. score = matched / skills found in JD * 100
@Component
public class JobMatcher {

    private final SkillDictionary skills = SkillDictionary.getDefault();

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

    // canonical skill names found in the text, in dictionary order, no duplicates
    public Set<String> extractSkills(String text) {
        Set<String> found = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        found.addAll(skills.findIn(text));
        return found;
    }
}
