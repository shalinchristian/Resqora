package com.resqora.resqora_backend.service.jobs;

import java.util.List;

// anything that can search job ads. JobicyJobSearchClient is the real one, tests use a fake
public interface JobSearchClient {
    boolean isConfigured();

    // returns jobs matching any of the keywords. throws a RuntimeException if anything fails
    List<JobPosting> search(List<String> keywords, int maxResults);
}
