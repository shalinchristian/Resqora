package com.resqora.resqora_backend.dto.jobs;

import java.util.List;

public record JobRecommendationsResponse(List<JobRecommendation> jobs) {
    public JobRecommendationsResponse { jobs = List.copyOf(jobs); }
}
