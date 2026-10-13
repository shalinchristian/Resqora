package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.match.JobMatchRequest;
import com.resqora.resqora_backend.dto.match.JobMatchResponse;
import com.resqora.resqora_backend.service.JobMatchService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resumes")
public class JobMatchController {
    private final JobMatchService jobMatchService;

    public JobMatchController(JobMatchService jobMatchService) {
        this.jobMatchService = jobMatchService;
    }

    @PostMapping("/{resumeId}/match")
    public JobMatchResponse match(
            @PathVariable Long resumeId,
            @Valid @RequestBody JobMatchRequest request,
            Authentication authentication) {
        return jobMatchService.match(resumeId, request.jobDescription(), authentication);
    }
}
