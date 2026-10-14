package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.JobSuggestionCache;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobSuggestionCacheRepository extends JpaRepository<JobSuggestionCache, Long> {
    Optional<JobSuggestionCache> findByResumeId(Long resumeId);
}
