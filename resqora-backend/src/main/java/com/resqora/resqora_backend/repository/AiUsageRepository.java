package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.AiUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {
    // app-wide cap of one provider (e.g. all Gemini calls today)
    long countByProviderAndCreatedAtGreaterThanEqual(AiUsage.Provider provider, Instant since);

    // per-user limit of one kind (e.g. this user's chat messages today)
    long countByUserIdAndKindAndCreatedAtGreaterThanEqual(Long userId, AiUsage.Kind kind, Instant since);
}
