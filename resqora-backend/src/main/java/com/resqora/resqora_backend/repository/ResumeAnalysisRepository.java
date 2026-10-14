package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.ResumeAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ResumeAnalysisRepository extends JpaRepository<ResumeAnalysis, Long> {
    Optional<ResumeAnalysis> findByResumeId(Long resumeId);

    // one query for many resumes (avoids one query per resume in the list)
    List<ResumeAnalysis> findByResumeIdIn(Collection<Long> resumeIds);
}
