package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.jobs.JobRecommendationsResponse;
import com.resqora.resqora_backend.service.jobs.JobRecommendationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resumes")
public class JobRecommendationController {
    private final JobRecommendationService service;

    public JobRecommendationController(JobRecommendationService service) { this.service = service; }

    @GetMapping("/{resumeId}/job-recommendations")
    public JobRecommendationsResponse recommend(@PathVariable Long resumeId, Authentication authentication) {
        return service.recommend(resumeId, authentication);
    }
}
