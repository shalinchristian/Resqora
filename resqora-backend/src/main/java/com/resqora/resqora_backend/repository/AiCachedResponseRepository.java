package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.AiCachedResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiCachedResponseRepository extends JpaRepository<AiCachedResponse, Long> {
    Optional<AiCachedResponse> findByResumeIdAndActionAndJobDescriptionHash(
            Long resumeId, String action, String jobDescriptionHash);
}
