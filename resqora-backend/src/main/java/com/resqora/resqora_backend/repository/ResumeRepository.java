package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
    // Spring Data builds the SQL from the method name
    List<Resume> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long countByUserId(Long userId);
}
