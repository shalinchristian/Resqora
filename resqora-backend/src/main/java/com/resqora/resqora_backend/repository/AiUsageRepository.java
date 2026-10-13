package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.AiUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {
    long countByCreatedAtGreaterThanEqual(Instant since);

    long countByUserIdAndCreatedAtGreaterThanEqual(Long userId, Instant since);
}
