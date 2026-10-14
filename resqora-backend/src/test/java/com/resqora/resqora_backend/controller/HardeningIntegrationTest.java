package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Slice 11: name PATCH, 401 for a deleted user, login limiter, password rule, error-body audit
@SpringBootTest
@AutoConfigureMockMvc
class HardeningIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    private static String email() {
        return "hard-" + UUID.randomUUID() + "@example.com";
    }

    private String tokenFor(User user) {
        return "Bearer " + jwtService.generateToken(user);
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"));
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"Ravi\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void patchMeSetsTrimmedNameForOldAccount() throws Exception {
        User user = userRepository.save(new User(email(), "hash"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", tokenFor(user)))
                .andExpect(jsonPath("$.name").value(nullValue()));

        mockMvc.perform(patch("/api/auth/me").header("Authorization", tokenFor(user))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"  Meera K  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.email").value(user.getEmail()))
                .andExpect(jsonPath("$.name").value("Meera K"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", tokenFor(user)))
                .andExpect(jsonPath("$.name").value("Meera K"));
    }

    @Test
    void patchMeRejectsBlankAndTooLongNames() throws Exception {
        User user = userRepository.save(new User(email(), "hash"));
        for (String name : new String[] {"   ", "a".repeat(51)}) {
            mockMvc.perform(patch("/api/auth/me").header("Authorization", tokenFor(user))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.message").value(containsString("name:")));
        }
    }

    @Test
    void patchMeWithoutTokenIs401() throws Exception {
        mockMvc.perform(patch("/api/auth/me").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meForDeletedUserIs401WithErrorBody() throws Exception {
        User user = userRepository.save(new User(email(), "hash"));
        String token = tokenFor(user);
        userRepository.delete(user);

        mockMvc.perform(get("/api/auth/me").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication required: missing or invalid token"));
    }

    @Test
    void sixthFailedLoginForSameEmailIs429EvenWithRightPassword() throws Exception {
        String email = email();
        register(email);
        for (int i = 0; i < 5; i++) {
            login(email, "wrongpass1", "10.0.0.1").andExpect(status().isUnauthorized());
        }
        // blocked now, even from another IP and with the correct password
        login(email, "password123", "10.0.0.2")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Too many login attempts, try again later"));
    }

    @Test
    void successfulLoginResetsEmailCounter() throws Exception {
        String email = email();
        register(email);
        for (int i = 0; i < 4; i++) {
            login(email, "wrongpass1", "10.0.1.1").andExpect(status().isUnauthorized());
        }
        login(email, "password123", "10.0.1.1").andExpect(status().isOk());
        for (int i = 0; i < 4; i++) {
            login(email, "wrongpass1", "10.0.1.1").andExpect(status().isUnauthorized());
        }
        login(email, "password123", "10.0.1.1").andExpect(status().isOk());
    }

    @Test
    void oneIpTryingManyEmailsIsBlockedAfter20Failures() throws Exception {
        for (int i = 0; i < 20; i++) {
            login(email(), "wrongpass1", "10.0.2.1").andExpect(status().isUnauthorized());
        }
        login(email(), "wrongpass1", "10.0.2.1").andExpect(status().isTooManyRequests());
        // other IPs are not affected
        login(email(), "wrongpass1", "10.0.2.2").andExpect(status().isUnauthorized());
    }

    @Test
    void registerRejectsWeakPasswords() throws Exception {
        for (String password : new String[] {"abcdefgh", "12345678", "abc123"}) {
            mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"" + email() + "\",\"password\":\"" + password + "\",\"name\":\"X\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("password:")));
        }
    }

    @Test
    void wrongMethodAndBadIdUseStandardErrorBody() throws Exception {
        User user = userRepository.save(new User(email(), "hash"));
        mockMvc.perform(put("/api/auth/me").header("Authorization", tokenFor(user)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mockMvc.perform(get("/api/resumes/abc/analysis").header("Authorization", tokenFor(user)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid value for resumeId"));
        mockMvc.perform(get("/api/nope").header("Authorization", tokenFor(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }
}
