package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
}