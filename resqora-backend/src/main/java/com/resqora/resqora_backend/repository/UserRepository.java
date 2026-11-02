package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // only active accounts: a deleted account keeps its email but can't log in, reset or verify
    @Query("select u from User u where u.activeEmail = :email")
    Optional<User> findByEmail(@Param("email") String email);

    @Query("select count(u) > 0 from User u where u.id = :id and u.status = 'ACTIVE'")
    boolean existsActiveById(@Param("id") Long id);

    // includes deleted accounts (for records and tests)
    @Query("select u from User u where u.email = :email order by u.id")
    java.util.List<User> findAllByEmailIncludingDeleted(@Param("email") String email);

    // SELECT ... FOR UPDATE on the user row: serialises one user's uploads so the 5-slot check is atomic
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);
}