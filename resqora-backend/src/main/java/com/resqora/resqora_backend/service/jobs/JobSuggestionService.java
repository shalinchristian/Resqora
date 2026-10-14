package com.resqora.resqora_backend.service.jobs;

import com.resqora.resqora_backend.dto.jobs.JobSuggestion;
import com.resqora.resqora_backend.dto.jobs.JobSuggestionsResponse;
import com.resqora.resqora_backend.dto.match.JobMatchResponse;
import com.resqora.resqora_backend.entity.JobSuggestionCache;
import com.resqora.resqora_backend.exception.JobSuggestionsUnavailableException;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.repository.JobSuggestionCacheRepository;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.service.ResumeService;
import com.resqora.resqora_backend.service.match.JobMatcher;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class JobSuggestionService {
    static final Duration CACHE_TTL = Duration.ofHours(12);
    static final int MAX_SEARCH_SKILLS = 3;
    static final int MAX_RESULTS = 10;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ResumeService resumeService;
    private final ResumeRepository resumeRepository;
    private final JobSuggestionCacheRepository cacheRepository;
    private final JobSearchClient jobSearchClient;
    private final JobMatcher jobMatcher;

    public JobSuggestionService(
            ResumeService resumeService,
            ResumeRepository resumeRepository,
            JobSuggestionCacheRepository cacheRepository,
            JobSearchClient jobSearchClient,
            JobMatcher jobMatcher) {
        this.resumeService = resumeService;
        this.resumeRepository = resumeRepository;
        this.cacheRepository = cacheRepository;
        this.jobSearchClient = jobSearchClient;
        this.jobMatcher = jobMatcher;
    }

    public JobSuggestionsResponse suggest(Long resumeId, Authentication authentication) {
        // ownership check (404) happens inside ResumeService
        ParsedResume parsedResume = resumeService.getParsedResume(resumeId, authentication);

        // 1. fresh cached result? return it without calling the job site
        Optional<JobSuggestionCache> cached = cacheRepository.findByResumeId(resumeId);
        if (cached.isPresent() && cached.get().getCreatedAt().isAfter(Instant.now().minus(CACHE_TTL))) {
            return new JobSuggestionsResponse(readJobs(cached.get().getResponseJson()), true);
        }

        // 2. search keywords = first few resume skills that the skill dictionary recognises
        // not configured -> 503 for every resume (checked first, so a resume without skills can't hide it)
        if (!jobSearchClient.isConfigured()) {
            throw new JobSuggestionsUnavailableException();
        }
        List<String> searchSkills = searchSkills(parsedResume.skills());
        if (searchSkills.isEmpty()) {
            return new JobSuggestionsResponse(List.of(), false);
        }
        List<JobPosting> postings;
        try {
            postings = jobSearchClient.search(searchSkills, MAX_RESULTS);
        } catch (RuntimeException exception) {
            throw new JobSuggestionsUnavailableException(exception);
        }

        // 3. score every job with the same rule-based matcher as POST /match, best first
        List<JobSuggestion> jobs = postings.stream()
                .map(posting -> toSuggestion(posting, parsedResume.skills()))
                .sorted(Comparator.comparingInt(JobSuggestion::matchScore).reversed())
                .toList();

        String json = JSON.writeValueAsString(jobs);
        if (cached.isPresent()) {
            cached.get().refresh(json);
            cacheRepository.save(cached.get());
        } else {
            cacheRepository.save(new JobSuggestionCache(resumeRepository.getReferenceById(resumeId), json));
        }
        return new JobSuggestionsResponse(jobs, false);
    }

    private List<String> searchSkills(List<String> resumeSkills) {
        Set<String> known = new LinkedHashSet<>();
        for (String skill : resumeSkills) {
            known.addAll(jobMatcher.extractSkills(skill));
            if (known.size() >= MAX_SEARCH_SKILLS) {
                break;
            }
        }
        return known.stream().limit(MAX_SEARCH_SKILLS).toList();
    }

    private JobSuggestion toSuggestion(JobPosting posting, List<String> resumeSkills) {
        String text = (posting.title() == null ? "" : posting.title()) + " "
                + (posting.description() == null ? "" : posting.description());
        JobMatchResponse match = jobMatcher.match(resumeSkills, text);
        return new JobSuggestion(posting.title(), posting.company(), posting.location(),
                match.matchScore(), match.matchedSkills(), posting.url());
    }

    private List<JobSuggestion> readJobs(String json) {
        return JSON.readValue(json, new TypeReference<List<JobSuggestion>>() { });
    }
}
