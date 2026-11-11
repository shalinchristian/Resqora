package com.resqora.resqora_backend.service.jobs;

import com.resqora.resqora_backend.dto.jobs.*;
import com.resqora.resqora_backend.model.resume.*;
import com.resqora.resqora_backend.service.ResumeService;
import com.resqora.resqora_backend.service.match.JobMatcher;
import com.resqora.resqora_backend.exception.ResumeNotFoundException;
import org.junit.jupiter.api.*;
import org.springframework.security.core.Authentication;
import java.time.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JobRecommendationServiceTest {
    ResumeService resumes = mock(ResumeService.class);
    JobsForceClient client = mock(JobsForceClient.class);
    JobSuggestionService jobicy = mock(JobSuggestionService.class);
    Authentication auth = mock(Authentication.class);
    JobRecommendationCache cache;
    JobRecommendationService service;
    ParsedResume resume = JobsForceQueryTest.resume("Pune", List.of(JobsForceQueryTest.work("Java Developer", "2022 - 2024")));
    JobsForceQuery query = JobsForceQuery.from(resume, JobsForceQueryTest.CLOCK);
    @BeforeEach void setup() {
        cache = new JobRecommendationCache(JobsForceQueryTest.CLOCK);
        service = new JobRecommendationService(resumes, client, jobicy, new JobMatcher(), cache, JobsForceQueryTest.CLOCK);
        when(resumes.getParsedResume(1L, auth)).thenReturn(resume);
        when(jobicy.suggest(1L, auth)).thenReturn(new JobSuggestionsResponse(List.of(), false));
    }
    @AfterEach void stop() { cache.close(); }
    JobsForcePosting job(String title, String company, String city, String skills, String url, int age) {
        return new JobsForcePosting(title, company, city, city, skills, url, null, LocalDate.now(JobsForceQueryTest.CLOCK).minusDays(age));
    }
    @Test void capsAtFiveAndNeverPadsWithWeakMatches() {
        List<JobsForcePosting> jobs = IntStream.range(0, 9).mapToObj(i -> job("Java Developer", "Company " + i,
                "Pune", "Java Spring Boot SQL", "https://example.com/" + i, i)).collect(Collectors.toList());
        jobs.add(job("Nurse", "Unrelated", "Pune", "patient care", "https://example.com/weak", 0));
        var result = service.rank(resume, query, jobs, List.of());
        assertEquals(5, result.jobs().size()); assertEquals("Company 0", result.jobs().getFirst().hiringName());
        assertEquals("Company 4", result.jobs().getLast().hiringName());
    }
    @Test void returnsFewerWhenOnlyOneStrongMatchExists() {
        var result = service.rank(resume, query, List.of(job("Nurse", "Weak", "Pune", "Care", "https://x.com/1", 0),
                job("Java Developer", "Strong", "Pune", "Java", "https://x.com/2", 0)), List.of());
        assertEquals(1, result.jobs().size()); assertEquals("Strong", result.jobs().getFirst().hiringName());
    }
    @Test void inclusiveFortyPointThresholdAndNoLocationOnlyMatches() {
        ParsedResume fresher = JobsForceQueryTest.resume("Pune", List.of());
        var q = JobsForceQuery.from(fresher, JobsForceQueryTest.CLOCK);
        // 50% of required skills => 30 points; Pune +5; posted today +5 => exactly 40.
        var result = service.rank(fresher, q, List.of(job("Developer", "At threshold", "Pune", "Java Python", "https://x.com/1", 0),
                job("Developer", "Below threshold", "Pune", "Java Python", "https://x.com/2", 1),
                job("Nurse", "Location only", "Pune", "Care", "https://x.com/3", 0)), List.of());
        assertEquals(1, result.jobs().size()); assertEquals("At threshold", result.jobs().getFirst().hiringName());
    }
    @Test void titleThenLocationThenRecencyImproveRank() {
        var jobs = List.of(job("Java Developer", "Old", "Pune", "Java", "https://x.com/1", 40),
                job("Java Developer", "Fresh", "Pune", "Java", "https://x.com/2", 0),
                job("Java Developer", "Other city", "Delhi", "Java", "https://x.com/3", 0),
                job("Engineer", "Other title", "Pune", "Java", "https://x.com/4", 0));
        assertEquals(List.of("Fresh", "Other city", "Old", "Other title"), service.rank(resume, query, jobs, List.of()).jobs()
                .stream().map(JobRecommendation::hiringName).toList());
    }
    @Test void dedupesNormalizedTitleCompanyAndBothUrlsAgainstJobicyAndWithinResults() {
        List<JobSuggestion> existing = List.of(new JobSuggestion("java-developer", "Acme & Co", "Pune", 100, List.of(),
                "https://www.example.com/shared/?utm_source=jobicy", "Jobicy"));
        var jobs = List.of(job("Java Developer", "ACME CO", "Pune", "Java", "https://x.com/identity", 0),
                job("Other Role", "Other", "Pune", "Java", "http://example.com/shared#fragment", 0),
                new JobsForcePosting("Other", "Other 2", "Pune", "Pune", "Java", null,
                        "https://example.com/shared", LocalDate.now(JobsForceQueryTest.CLOCK)),
                job("Java Developer", "Kept", "Pune", "Java", "https://x.com/kept", 0),
                job("Java Developer", "Kept", "Pune", "Java", "https://x.com/duplicate", 0));
        assertEquals(1, service.rank(resume, query, jobs, existing).jobs().size());
    }
    @Test void dedupeOnlyUsesFiveJobicyJobsActuallyReturned() {
        List<JobSuggestion> six = IntStream.range(0, 6).mapToObj(i -> new JobSuggestion("Java Developer", "Company " + i,
                "Pune", 100, List.of(), "https://x.com/" + i, "Jobicy")).toList();
        var result = service.rank(resume, query, List.of(job("Java Developer", "Company 5", "Pune", "Java", "https://x.com/5", 0)), six);
        assertEquals(1, result.jobs().size());
    }
    @Test void filtersMissingUrlsAndProducesGroundedSingleLineWhyAndExactAttribution() {
        var jobs = List.of(job("Java Developer", "No URL", "Pune", "Java", null, 0),
                job("Java Developer", "  Confidential client & recruiter  ", "Pune", "Java SQL", "https://x.com/ok", 0),
                job("Java Developer", null, "Delhi", "Java", "https://x.com/null", 0));
        var result = service.rank(resume, query, jobs, List.of()).jobs();
        assertEquals(2, result.size()); assertEquals("  Confidential client & recruiter  ", result.getFirst().hiringName());
        assertNull(result.getLast().hiringName()); assertEquals("JobsForce India", result.getFirst().source());
        assertTrue(result.getFirst().why().contains("Java")); assertTrue(result.getFirst().why().contains("Pune"));
        assertTrue(result.getFirst().why().contains("latest role")); assertFalse(result.getFirst().why().contains("\n"));
    }
    @Test void failuresReturnEmptyWithoutCallingOrChangingJobicy() {
        when(client.search(any())).thenThrow(new IllegalStateException("unavailable"));
        assertTrue(service.recommend(1L, auth).jobs().isEmpty()); assertTrue(service.recommend(1L, auth).jobs().isEmpty());
        verify(client, times(1)).search(any()); verifyNoInteractions(jobicy);
    }
    @Test void ownershipCheckPrecedesEvenPopulatedCache() {
        when(client.search(any())).thenReturn(List.of(job("Java Developer", "A", "Pune", "Java", "https://x.com/1", 0)));
        assertEquals(1, service.recommend(1L, auth).jobs().size());
        when(resumes.getParsedResume(1L, auth)).thenThrow(new ResumeNotFoundException());
        assertThrows(ResumeNotFoundException.class, () -> service.recommend(1L, auth)); verify(client, times(1)).search(any());
    }
    @Test void cacheDoesNotFreezeDedupeWhenJobicyListChanges() {
        var posting = job("Java Developer", "A", "Pune", "Java", "https://x.com/1", 0);
        when(client.search(any())).thenReturn(List.of(posting));
        assertEquals(1, service.recommend(1L, auth).jobs().size());
        when(jobicy.suggest(1L, auth)).thenReturn(new JobSuggestionsResponse(List.of(
                new JobSuggestion("Java Developer", "A", "Pune", 100, List.of(), "https://x.com/1", "Jobicy")), true));
        assertTrue(service.recommend(1L, auth).jobs().isEmpty()); verify(client, times(1)).search(any());
    }
    @Test void jobicyFailureDoesNotSuppressStrongJobsForceMatches() {
        when(client.search(any())).thenReturn(List.of(job("Java Developer", "A", "Pune", "Java", "https://x.com/1", 0)));
        when(jobicy.suggest(1L, auth)).thenThrow(new IllegalStateException());
        assertEquals(1, service.recommend(1L, auth).jobs().size());
    }
}
