package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// resqora.ai.daily-limit is set from RESQORA_AI_USER_DAILY_LIMIT in application.properties
@SpringBootTest(properties = {"resqora.ai.daily-limit=3", "resqora.timezone=UTC"})
@AutoConfigureMockMvc
class AiUserLimitConfigIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    @Test
    void usageReportsConfiguredLimit() throws Exception {
        User user = userRepository.save(new User("limit-" + UUID.randomUUID() + "@example.com", "hash"));
        mockMvc.perform(get("/api/ai/usage").header("Authorization", "Bearer " + jwtService.generateToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(3))
                .andExpect(jsonPath("$.remainingToday").value(3));
    }
}
