package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserNameIntegrationTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    private static String email() {
        return "name-" + UUID.randomUUID() + "@example.com";
    }

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void registerStoresTrimmedName() throws Exception {
        String email = email();
        register("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"  Asha Rao \"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Asha Rao"));
        assertEquals("Asha Rao", userRepository.findByEmail(email).orElseThrow().getName());
    }

    @Test
    void missingNameReturns400() throws Exception {
        register("{\"email\":\"" + email() + "\",\"password\":\"password123\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("name:")));
    }

    @Test
    void blankNameReturns400() throws Exception {
        register("{\"email\":\"" + email() + "\",\"password\":\"password123\",\"name\":\"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("name:")));
    }

    @Test
    void tooLongNameReturns400() throws Exception {
        register("{\"email\":\"" + email() + "\",\"password\":\"password123\",\"name\":\"" + "a".repeat(101) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("name:")));
    }

    @Test
    void loginAndMeReturnName() throws Exception {
        String email = email();
        register("{\"email\":\"" + email + "\",\"password\":\"password123\",\"name\":\"Asha Rao\"}")
                .andExpect(status().isCreated());

        String token = com.jayway.jsonpath.JsonPath.read(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.name").value("Asha Rao"))
                .andReturn().getResponse().getContentAsString(), "$.token");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Asha Rao"));
    }

    @Test
    void userWithoutNameGetsEmailAsNameInLoginAndMe() throws Exception {
        // simulates a user registered before names existed (name column is NULL)
        String email = email();
        User user = userRepository.save(new User(email, passwordEncoder.encode("password123")));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(email));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + jwtService.generateToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(email));
    }
}
