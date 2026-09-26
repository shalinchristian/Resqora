package com.resqora.resqora_backend.repository;

import com.resqora.resqora_backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class UserRepositoryTest {
    @Autowired
    private UserRepository userRepository;

    @Test
    void findsUserByEmail() {
        userRepository.save(new User("person@example.com", "hashed-password"));

        assertTrue(userRepository.findByEmail("person@example.com").isPresent());
        assertTrue(userRepository.existsByEmail("person@example.com"));
    }
}