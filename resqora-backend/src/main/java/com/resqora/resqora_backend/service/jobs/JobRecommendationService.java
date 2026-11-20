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
import java.util.concurrent.*;
import java.util.stream.Collectors;
import jakarta.annotation.PreDestroy;

@Service
public class JobRecommendationService {
    static final int MAX_RESULTS = 5;
    static final double MIN_MATCH_SCORE = 40.0;
    // full listings read per uncached search, in the provider's relevance order. the search summaries
    // ("Our client is looking for a Junior Software Developer...") hardly ever mention a skill
    static final int DETAIL_LIMIT = 12;
    static final Duration DETAIL_WAIT = Duration.ofSeconds(12);
    private static final Logger LOG = LoggerFactory.getLogger(JobRecommendationService.class);
    private final ResumeService resumes;
    private final JobsForceClient client;
    private final JobSuggestionService jobicy;
    private final JobMatcher matcher;
    private final JobRecommendationCache cache;
    private final Clock clock;
    private final ExecutorService details = Executors.newFixedThreadPool(6, task -> {
        Thread thread = new Thread(task, "jobsforce-details"); thread.setDaemon(true); return thread;
    });
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
        // check ownership before touching the cache or fallback, even on a cache hit
        ParsedResume resume = resumes.getParsedResume(id, auth);
        JobsForceQuery query = JobsForceQuery.from(resume, clock);
        if (query.text().isBlank()) return new JobRecommendationsResponse(List.of());
        List<JobsForcePosting> candidates = cache.getOrLoad(id, () -> {
            try { return withDetails(client.search(query)); }
            catch (RuntimeException failure) {
                // don't log bodies, queries or exception messages, they can have resume info in them
                LOG.warn("JobsForce recommendations unavailable ({})", failure.getClass().getSimpleName());
                throw failure;
            }
        });
        if (candidates.isEmpty()) return new JobRecommendationsResponse(List.of());
        List<JobSuggestion> suggestions = List.of();
        try {
            // same capped five the existing endpoint returns, same cache and ranking
            suggestions = jobicy.suggest(id, auth).jobs();
        } catch (RuntimeException failure) {
            LOG.warn("Jobicy dedupe list unavailable ({})", failure.getClass().getSimpleName());
        }
        return rank(resume, query, candidates, suggestions);
    }
    /** adds the full listing text to the first DETAIL_LIMIT postings. if one fails or is slow it keeps its summary */
    List<JobsForcePosting> withDetails(List<JobsForcePosting> postings) {
        List<JobsForcePosting> result = new ArrayList<>(postings);
        List<Integer> indexes = new ArrayList<>();
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < result.size() && tasks.size() < DETAIL_LIMIT; i++) {
            String id = result.get(i).id();
            if (id == null) continue;
            indexes.add(i); tasks.add(() -> client.details(id));
        }
        if (tasks.isEmpty()) return result;
        try {
            List<Future<String>> done = details.invokeAll(tasks, DETAIL_WAIT.toMillis(), TimeUnit.MILLISECONDS);
            int failed = 0;
            for (int i = 0; i < done.size(); i++) {
                try { result.set(indexes.get(i), result.get(indexes.get(i)).withDetails(done.get(i).get())); }
                catch (ExecutionException | CancellationException failure) { failed++; }
            }
            if (failed > 0) LOG.warn("JobsForce details unavailable for {} of {} jobs", failed, done.size());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return List.copyOf(result);
    }
    @PreDestroy void stop() { details.shutdownNow(); }
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
            long age = job.postedAt() == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(job.postedAt(), LocalDate.now(clock));
            double recency = age < 0 || age > 30 ? 0 : 1 - age / 30.0;
            double score = 0.60 * match.matchScore() + 30 * title + 5 * recency;
            if (score < MIN_MATCH_SCORE || (match.matchedSkills().isEmpty() && title < 0.5)) continue;
            List<String> reasons = new ArrayList<>();
            if (!match.matchedSkills().isEmpty()) reasons.add("Matches " + String.join(", ", match.matchedSkills().stream().limit(3).toList()));
            if (title >= 0.5) reasons.add("aligns with your latest role");
            ranked.add(new Ranked(new JobRecommendation(job.title(), job.hiringName(), job.location(),
                    String.join("; ", reasons) + ".", url, apply,
                    job.postedAt() == null ? null : job.postedAt().toString(), "JobsForce India"), score));
        }
        ranked.sort(Comparator.comparingDouble(Ranked::score).reversed()
                .thenComparing(r -> r.job().postedAt(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r -> r.job().title()));
        List<JobRecommendation> result = new ArrayList<>();
        // for JobsForce the location is part of the identity. one agency ("JobsForce India (recruiting for a
        // confidential client)") posts the same title in lots of cities and those are separate openings.
        // against Jobicy, title + company is enough to remove cross-listings (the two sources write places differently)
        Set<String> picked = new HashSet<>();
        for (Ranked item : ranked) {
            JobRecommendation job = item.job();
            String url = normalizedUrl(job.url()), apply = normalizedUrl(job.applyUrl());
            String identity = identity(job.title(), job.hiringName());
            String placed = identity == null ? null : identity + "|" + normalize(Objects.requireNonNullElse(job.location(), ""));
            if ((url != null && urls.contains(url)) || (apply != null && urls.contains(apply))
                    || (identity != null && identities.contains(identity))
                    || (placed != null && picked.contains(placed))) continue;
            add(urls, url); add(urls, apply); add(picked, placed);
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
