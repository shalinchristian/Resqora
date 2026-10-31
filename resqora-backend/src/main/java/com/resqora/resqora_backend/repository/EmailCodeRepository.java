package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.EmailCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EmailCodeRepository extends JpaRepository<EmailCode, Long> {
    Optional<EmailCode> findByUserIdAndPurpose(Long userId, EmailCode.Purpose purpose);

    @Modifying
    @Query("delete from EmailCode c where c.userId = :userId and c.purpose = :purpose")
    void deleteByUserIdAndPurpose(@Param("userId") Long userId, @Param("purpose") EmailCode.Purpose purpose);
}
