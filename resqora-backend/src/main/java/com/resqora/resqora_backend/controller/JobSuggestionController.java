package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.jobs.JobSuggestionsResponse;
import com.resqora.resqora_backend.service.jobs.JobSuggestionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes")
public class JobSuggestionController {
    private final JobSuggestionService jobSuggestionService;

    public JobSuggestionController(JobSuggestionService jobSuggestionService) {
        this.jobSuggestionService = jobSuggestionService;
    }

    @GetMapping("/{resumeId}/jobs")
    public JobSuggestionsResponse suggestJobs(@PathVariable Long resumeId, Authentication authentication) {
        return jobSuggestionService.suggest(resumeId, authentication);
    }
}
