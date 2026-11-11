package com.resqora.resqora_backend.service.jobs;

import com.resqora.resqora_backend.dto.jobs.*;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.service.ResumeService;
import com.resqora.resqora_backend.service.match.JobMatcher;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class JobRecommendationService {
    static final int MAX_RESULTS = 5;
    static final double MIN_MATCH_SCORE = 40.0;
    private static final Logger LOG = LoggerFactory.getLogger(JobRecommendationService.class);
    private final ResumeService resumes;
    private final JobsForceClient client;
    private final JobSuggestionService jobicy;
    private final JobMatcher matcher;
    private final JobRecommendationCache cache;
    private final Clock clock;
    @Autowired
    public JobRecommendationService(ResumeService resumes, JobsForceClient client, JobSuggestionService jobicy,
            JobMatcher matcher, JobRecommendationCache cache) {
        this(resumes, client, jobicy, matcher, cache, Clock.systemUTC());
    }
    JobRecommendationService(ResumeService resumes, JobsForceClient client, JobSuggestionService jobicy,
            JobMatcher matcher, JobRecommendationCache cache, Clock clock) {
        this.resumes = resumes; this.client = client; this.jobicy = jobicy;
        this.matcher = matcher; this.cache = cache; this.clock = clock;
    }
    public JobRecommendationsResponse recommend(Long id, Authentication auth) {
        // Ownership is checked before any cache access/fallback, including on a cache hit.
        ParsedResume resume = resumes.getParsedResume(id, auth);
        JobsForceQuery query = JobsForceQuery.from(resume, clock);
        if (query.text().isBlank()) return new JobRecommendationsResponse(List.of());
        List<JobsForcePosting> candidates = cache.getOrLoad(id, () -> {
            try { return client.search(query); }
            catch (RuntimeException failure) {
                // No bodies, queries or exception messages: those can contain resume information.
                LOG.warn("JobsForce recommendations unavailable ({})", failure.getClass().getSimpleName());
                throw failure;
            }
        });
        if (candidates.isEmpty()) return new JobRecommendationsResponse(List.of());
        List<JobSuggestion> suggestions = List.of();
        try {
            // Exactly the capped five returned by the existing endpoint, with its unchanged cache/ranking.
            suggestions = jobicy.suggest(id, auth).jobs();
        } catch (RuntimeException failure) {
            LOG.warn("Jobicy dedupe list unavailable ({})", failure.getClass().getSimpleName());
        }
        return rank(resume, query, candidates, suggestions);
    }
    JobRecommendationsResponse rank(ParsedResume resume, JobsForceQuery query,
            List<JobsForcePosting> postings, List<JobSuggestion> existing) {
        Set<String> urls = new HashSet<>(), identities = new HashSet<>();
        existing.stream().limit(MAX_RESULTS).forEach(job -> {
            add(urls, normalizedUrl(job.url())); add(identities, identity(job.title(), job.company()));
        });
        record Ranked(JobRecommendation job, double score) { }
        List<Ranked> ranked = new ArrayList<>();
        for (JobsForcePosting job : postings) {
            if (job.title() == null || job.title().isBlank()) continue;
            String url = JobsForceClient.validUrl(job.url()), apply = JobsForceClient.validUrl(job.applyUrl());
            if (url == null && apply == null) continue;
            var match = matcher.match(resume.skills(), job.title() + " " + job.summary());
            double title = similarity(query.title(), job.title());
            boolean city = query.city() != null && JobsForceQuery.normalizeCity(query.city())
                    .equals(JobsForceQuery.normalizeCity(job.city()));
            long age = job.postedAt() == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(job.postedAt(), LocalDate.now(clock));
            double recency = age < 0 || age > 30 ? 0 : 1 - age / 30.0;
            double score = 0.60 * match.matchScore() + 30 * title + (city ? 5 : 0) + 5 * recency;
            if (score < MIN_MATCH_SCORE || (match.matchedSkills().isEmpty() && title < 0.5)) continue;
            List<String> reasons = new ArrayList<>();
            if (!match.matchedSkills().isEmpty()) reasons.add("Matches " + String.join(", ", match.matchedSkills().stream().limit(3).toList()));
            if (title >= 0.5) reasons.add("aligns with your latest role");
            if (city) reasons.add("in " + query.city());
            ranked.add(new Ranked(new JobRecommendation(job.title(), job.hiringName(), job.location(),
                    String.join("; ", reasons) + ".", url, apply,
                    job.postedAt() == null ? null : job.postedAt().toString(), "JobsForce India"), score));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed()
                .thenComparing(r -> r.job().postedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r -> r.job().title()));
        List<JobRecommendation> result = new ArrayList<>();
        for (Ranked item : ranked) {
            JobRecommendation job = item.job();
            String url = normalizedUrl(job.url()), apply = normalizedUrl(job.applyUrl());
            String identity = identity(job.title(), job.hiringName());
            if ((url != null && urls.contains(url)) || (apply != null && urls.contains(apply))
                    || (identity != null && identities.contains(identity))) continue;
            add(urls, url); add(urls, apply); add(identities, identity);
            result.add(job);
            if (result.size() == MAX_RESULTS) break;
        }
        return new JobRecommendationsResponse(result);
    }
    private static void add(Set<String> set, String value) { if (value != null) set.add(value); }
    private static String identity(String title, String company) {
        if (title == null || company == null || company.isBlank()) return null;
        return normalize(title) + "|" + normalize(company);
    }
    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }
    private static double similarity(String left, String right) {
        if (left == null || right == null) return 0;
        Set<String> a = tokens(left), b = tokens(right);
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(a); intersection.retainAll(b);
        Set<String> union = new HashSet<>(a); union.addAll(b);
        return intersection.size() / (double) union.size();
    }
    private static Set<String> tokens(String text) {
        return Arrays.stream(normalize(text).split(" "))
                .filter(s -> !Set.of("senior", "junior", "lead", "intern", "internship", "the", "and", "a").contains(s))
                .filter(s -> !s.isBlank()).collect(Collectors.toSet());
    }
    static String normalizedUrl(String text) {
        String valid = JobsForceClient.validUrl(text);
        if (valid == null) return null;
        URI uri = URI.create(valid).normalize();
        String query = uri.getRawQuery() == null ? "" : Arrays.stream(uri.getRawQuery().split("&"))
                .filter(q -> !q.toLowerCase(Locale.ROOT).matches("(?:utm_[^=]*|fbclid|gclid)=.*"))
                .sorted().collect(Collectors.joining("&"));
        int port = uri.getPort();
        String authority = uri.getHost().toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "")
                + (port == -1 || port == 80 || port == 443 ? "" : ":" + port);
        return authority + Objects.requireNonNullElse(uri.getRawPath(), "").replaceAll("/+$", "")
                + (query.isBlank() ? "" : "?" + query);
    }
}
