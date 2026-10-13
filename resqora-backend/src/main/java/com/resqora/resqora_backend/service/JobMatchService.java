package com.resqora.resqora_backend.service;

import com.resqora.resqora_backend.dto.match.JobMatchResponse;
import com.resqora.resqora_backend.model.resume.ParsedResume;
import com.resqora.resqora_backend.service.match.JobMatcher;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class JobMatchService {
    private final ResumeService resumeService;
    private final JobMatcher jobMatcher;

    public JobMatchService(ResumeService resumeService, JobMatcher jobMatcher) {
        this.resumeService = resumeService;
        this.jobMatcher = jobMatcher;
    }

    public JobMatchResponse match(Long resumeId, String jobDescription, Authentication authentication) {
        // getParsedResume already checks that the resume belongs to the logged-in user (404 otherwise)
        ParsedResume parsedResume = resumeService.getParsedResume(resumeId, authentication);
        return jobMatcher.match(parsedResume.skills(), jobDescription);
    }
}
