package com.resqora.resqora_backend.service.jobs;

import java.util.List;

// Anything that can search job ads. AdzunaJobSearchClient is the real one; tests use a fake.
public interface JobSearchClient {
    boolean isConfigured();

    // keywords: jobs matching ANY of them; throws a RuntimeException on any failure
    List<JobPosting> search(List<String> keywords, int maxResults);
}
