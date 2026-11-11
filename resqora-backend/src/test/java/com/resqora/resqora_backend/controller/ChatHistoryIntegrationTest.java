package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.exception.AiUnavailableException;
import com.resqora.resqora_backend.repository.AiUsageRepository;
import com.resqora.resqora_backend.repository.ChatHistoryRepository;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.AuthService;
import com.resqora.resqora_backend.service.JwtService;
import com.resqora.resqora_backend.service.ai.AiClient;
import com.resqora.resqora_backend.service.ai.ChatHistoryService;
import com.resqora.resqora_backend.service.ai.GroqClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ChatHistoryIntegrationTest {
    static class FakeAi implements AiClient, GroqClient {
        boolean fail;
        String reply = "Answer";
        String prompt;
        @Override public boolean isConfigured() { return true; }
        @Override public String generateText(String rules, String prompt) {
            this.prompt = prompt;
            if (fail) throw new AiUnavailableException();
            return reply;
        }
    }
    @TestConfiguration
    static class Config {
        @Bean @Primary FakeAi historyFakeAi() { return new FakeAi(); }
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ResumeRepository resumes;
    @Autowired ChatHistoryRepository history;
    @Autowired AiUsageRepository usage;
    @Autowired JwtService jwt;
    @Autowired AuthService auth;
    @Autowired PasswordEncoder passwords;
    @Autowired ChatHistoryService service;
    @Autowired FakeAi ai;
    User user;
    User other;
    Resume resume;
    Resume second;
    String token;
    String otherToken;
    static final String DASH = "/api/chat";

    @BeforeEach void setup() {
        user = users.save(new User("history-" + UUID.randomUUID() + "@example.com", passwords.encode("LocalTest123!")));
        other = users.save(new User("history-other-" + UUID.randomUUID() + "@example.com", "hash"));
        resume = resumes.save(new Resume(user, "cv.pdf", "PDF", "Java backend developer. Skills: Spring Boot and SQL."));
        second = resumes.save(new Resume(user, "cv2.pdf", "PDF", "Python data analyst. Skills: SQL."));
        token = "Bearer " + jwt.generateToken(user);
        otherToken = "Bearer " + jwt.generateToken(other);
        ai.fail = false;
        ai.reply = "Answer";
    }
    String resumePath(Resume r) { return "/api/resumes/" + r.getId() + "/chat"; }
    ResultActions send(String path, String auth, String message) throws Exception {
        return mvc.perform(post(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + message + "\"}"));
    }
    ResultActions read(String path, String auth) throws Exception {
        return mvc.perform(get(path + "/history").header("Authorization", auth));
    }
    ResultActions clear(String path, String auth) throws Exception {
        return mvc.perform(delete(path + "/history").header("Authorization", auth));
    }

    @Test void emptyHistoriesHaveExactEnvelope() throws Exception {
        read(DASH, token).andExpect(status().isOk()).andExpect(content().json("{\"messages\":[]}"));
        read(resumePath(resume), token).andExpect(status().isOk()).andExpect(content().json("{\"messages\":[]}"));
    }

    @Test void successfulChatsSaveExactJsonFieldsAndRolesOldestFirst() throws Exception {
        for (String path : List.of(DASH, resumePath(resume))) {
            send(path, token, "Question").andExpect(status().isOk());
            String body = read(path, token).andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$.messages.length()").value(2))
                    .andExpect(jsonPath("$.messages[0].length()").value(3))
                    .andExpect(jsonPath("$.messages[0].role").value("user"))
                    .andExpect(jsonPath("$.messages[0].content").value("Question"))
                    .andExpect(jsonPath("$.messages[0].text").doesNotExist())
                    .andExpect(jsonPath("$.messages[1].role").value("assistant"))
                    .andExpect(jsonPath("$.messages[1].content").value("Answer"))
                    .andExpect(jsonPath("$.messages[1].text").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            var json = tools.jackson.databind.json.JsonMapper.builder().build().readTree(body);
            assertNotNull(OffsetDateTime.parse(json.get("messages").get(0).get("createdAt").asText()));
        }
    }

    @Test void savesOnlyNewestTwentyMessagesPerScope() throws Exception {
        for (String path : List.of(DASH, resumePath(resume))) {
            for (int i = 0; i < 12; i++) {
                ai.reply = "a" + i;
                send(path, token, "q" + i).andExpect(status().isOk());
            }
            read(path, token).andExpect(jsonPath("$.messages.length()").value(20))
                    .andExpect(jsonPath("$.messages[0].content").value("q2"))
                    .andExpect(jsonPath("$.messages[1].content").value("a2"))
                    .andExpect(jsonPath("$.messages[18].content").value("q11"))
                    .andExpect(jsonPath("$.messages[19].content").value("a11"));
        }
    }

    @Test void dashboardAndEachResumeAreSeparateAndUserIsolated() throws Exception {
        send(DASH, token, "dashboard").andExpect(status().isOk());
        send(resumePath(resume), token, "first resume").andExpect(status().isOk());
        send(resumePath(second), token, "second resume").andExpect(status().isOk());
        send(DASH, otherToken, "other user").andExpect(status().isOk());
        for (String[] pair : new String[][] {{DASH,"dashboard"},{resumePath(resume),"first resume"},
                {resumePath(second),"second resume"}}) {
            read(pair[0], token).andExpect(jsonPath("$.messages.length()").value(2))
                    .andExpect(jsonPath("$.messages[0].content").value(pair[1]));
        }
        read(DASH, otherToken).andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].content").value("other user"));
    }

    @Test void foreignMissingAndDeletedResumesAre404ForGetAndDelete() throws Exception {
        send(resumePath(resume), token, "private").andExpect(status().isOk());
        for (String path : List.of(resumePath(resume), "/api/resumes/9223372036854775807/chat")) {
            read(path, otherToken).andExpect(status().isNotFound());
            clear(path, otherToken).andExpect(status().isNotFound());
        }
        mvc.perform(delete("/api/resumes/" + resume.getId()).header("Authorization", token))
                .andExpect(status().isNoContent());
        read(resumePath(resume), token).andExpect(status().isNotFound());
        clear(resumePath(resume), token).andExpect(status().isNotFound());
    }

    @Test void jwtIsRequiredOnAllHistoryRoutes() throws Exception {
        for (String path : List.of(DASH, resumePath(resume))) {
            mvc.perform(get(path + "/history")).andExpect(status().isUnauthorized());
            mvc.perform(delete(path + "/history")).andExpect(status().isUnauthorized());
        }
    }

    @Test void clearIsIdempotentAndTouchesOnlyRequestedUserAndScope() throws Exception {
        for (String path : List.of(DASH, resumePath(resume), resumePath(second))) {
            send(path, token, "keep").andExpect(status().isOk());
        }
        send(DASH, otherToken, "other").andExpect(status().isOk());
        clear(resumePath(resume), token).andExpect(status().isNoContent()).andExpect(content().string(""));
        clear(resumePath(resume), token).andExpect(status().isNoContent());
        read(resumePath(resume), token).andExpect(jsonPath("$.messages.length()").value(0));
        read(DASH, token).andExpect(jsonPath("$.messages.length()").value(2));
        read(resumePath(second), token).andExpect(jsonPath("$.messages.length()").value(2));
        clear(DASH, token).andExpect(status().isNoContent());
        read(DASH, token).andExpect(jsonPath("$.messages.length()").value(0));
        read(DASH, otherToken).andExpect(jsonPath("$.messages[0].content").value("other"));
        read(resumePath(second), token).andExpect(jsonPath("$.messages.length()").value(2));
    }

    @Test void readingAndClearingAreFreeAndWorkWithAiDisabled() throws Exception {
        send(DASH, token, "saved").andExpect(status().isOk());
        long before = usage.count();
        user.setAiEnabled(false);
        users.saveAndFlush(user);
        for (String path : List.of(DASH, resumePath(resume))) {
            read(path, token).andExpect(status().isOk());
            clear(path, token).andExpect(status().isNoContent());
        }
        assertEquals(before, usage.count());
    }

    @Test void failedCallsAndBlankRepliesNeverSaveAnOrphanTurn() throws Exception {
        for (String path : List.of(DASH, resumePath(resume))) {
            send(path, token, "previous").andExpect(status().isOk());
            long before = usage.count();
            ai.fail = true;
            send(path, token, "failed").andExpect(status().isServiceUnavailable());
            ai.fail = false;
            ai.reply = "   ";
            send(path, token, "blank").andExpect(status().isServiceUnavailable());
            ai.reply = "Answer";
            read(path, token).andExpect(jsonPath("$.messages.length()").value(2))
                    .andExpect(jsonPath("$.messages[0].content").value("previous"));
            assertEquals(before, usage.count());
        }
    }

    @Test void limitRejectedCallsSaveNothingAndHistoryStillLoads() throws Exception {
        for (String path : List.of(DASH, resumePath(resume))) {
            for (int i = 0; i < 20; i++) send(path, token, "q" + i).andExpect(status().isOk());
            send(path, token, "rejected").andExpect(status().isTooManyRequests());
            read(path, token).andExpect(status().isOk()).andExpect(jsonPath("$.messages.length()").value(20))
                    .andExpect(jsonPath("$.messages[18].content").value("q19"));
        }
    }

    @Test void resumeDeletionCascadesWithoutClearingOtherHistory() throws Exception {
        send(resumePath(resume), token, "first").andExpect(status().isOk());
        send(resumePath(second), token, "second").andExpect(status().isOk());
        send(DASH, token, "dashboard").andExpect(status().isOk());
        long before = history.count();
        mvc.perform(delete("/api/resumes/" + resume.getId()).header("Authorization", token))
                .andExpect(status().isNoContent());
        assertEquals(before - 2, history.count());
        read(resumePath(second), token).andExpect(jsonPath("$.messages.length()").value(2));
        read(DASH, token).andExpect(jsonPath("$.messages.length()").value(2));
    }

    @Test void softDeleteRemovesBothScopesButKeepsUsageAndOtherUserHistory() throws Exception {
        send(DASH, token, "dashboard").andExpect(status().isOk());
        send(resumePath(resume), token, "resume").andExpect(status().isOk());
        send(DASH, otherToken, "other").andExpect(status().isOk());
        long before = history.count();
        long usageBefore = usage.count();
        auth.deleteAccount(user.getId(), "LocalTest123!");
        assertTrue(users.findById(user.getId()).orElseThrow().isDeleted());
        assertEquals(before - 4, history.count());
        assertEquals(usageBefore, usage.count());
        read(DASH, token).andExpect(status().isUnauthorized());
        read(DASH, otherToken).andExpect(jsonPath("$.messages[0].content").value("other"));
    }

    @Test void storedHistoryDoesNotSilentlyChangeAiPromptContext() throws Exception {
        for (String path : List.of(DASH, resumePath(resume))) {
            send(path, token, "stored-only-marker").andExpect(status().isOk());
            mvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"next\",\"history\":[{\"role\":\"model\",\"text\":\"browser-context-marker\"}]}"))
                    .andExpect(status().isOk());
            assertTrue(ai.prompt.contains("browser-context-marker"));
            assertFalse(ai.prompt.contains("stored-only-marker"));
        }
    }

    @Test void deleteCorsPreflightIsAllowed() throws Exception {
        for (String path : List.of(DASH, resumePath(resume))) {
            mvc.perform(options(path + "/history").header("Origin", "http://localhost:5173")
                    .header("Access-Control-Request-Method", "DELETE")
                    .header("Access-Control-Request-Headers", "Authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Methods", org.hamcrest.Matchers.containsString("DELETE")));
        }
    }

    @Test void concurrentSavesKeepWholePairsAndBoundedHistory() throws Exception {
        var authentication = new UsernamePasswordAuthenticationToken(user.getId(), null, List.of());
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Future<?>> work = new ArrayList<>();
            for (int i = 0; i < 18; i++) {
                int turn = i;
                work.add(executor.submit(() -> service.saveReply(null, "q" + turn, "a" + turn, authentication)));
            }
            for (var future : work) future.get(30, TimeUnit.SECONDS);
        }
        var messages = service.history(null, authentication).messages();
        assertEquals(20, messages.size());
        for (int i = 0; i < messages.size(); i += 2) {
            assertEquals("user", messages.get(i).role());
            assertEquals("assistant", messages.get(i + 1).role());
            assertEquals(messages.get(i).content().substring(1), messages.get(i + 1).content().substring(1));
        }
    }
}
