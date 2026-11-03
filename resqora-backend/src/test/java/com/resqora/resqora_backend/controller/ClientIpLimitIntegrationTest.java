package com.resqora.resqora_backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Per-IP limits must count each visitor separately even though, on Render, every request
// comes from the same proxy address (10.x). Here the limit is 1 code request per IP.
@SpringBootTest(properties = {"resqora.email.max-code-requests-per-ip=1"})
@AutoConfigureMockMvc
class ClientIpLimitIntegrationTest {
    @Autowired private MockMvc mockMvc;

    private ResultActions forgot(String visitorIp) throws Exception {
        return mockMvc.perform(post("/api/auth/forgot-password")
                .with(request -> { request.setRemoteAddr("10.21.157.68"); return request; })
                .header("CF-Connecting-IP", visitorIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"nobody-" + visitorIp.replace(':', '-') + "@example.com\"}"));
    }

    @Test
    void oneVisitorHittingTheLimitDoesNotBlockAnother() throws Exception {
        forgot("203.0.113.10").andExpect(status().isAccepted());
        forgot("203.0.113.10").andExpect(status().isTooManyRequests());
        forgot("203.0.113.11").andExpect(status().isAccepted());
    }
}
