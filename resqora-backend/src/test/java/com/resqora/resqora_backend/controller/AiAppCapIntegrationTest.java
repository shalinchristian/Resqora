package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.entity.AiUsage;
import com.resqora.resqora_backend.entity.Resume;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.AiUsageRepository;
import com.resqora.resqora_backend.repository.ResumeRepository;
import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// App-wide cap: once all users together reach resqora.ai.daily-cap real AI calls today,
// new AI calls get 503 even for users who still have personal quota left.
@SpringBootTest(properties = "resqora.ai.daily-cap=" + AiAppCapIntegrationTest.CAP)
@AutoConfigureMockMvc
@Import(AiIntegrationTest.FakeAiConfig.class)
class AiAppCapIntegrationTest {
    static final int CAP = 300;

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private ResumeRepository resumeRepository;
    @Autowired private AiUsageRepository usageRepository;
    @Autowired private JwtService jwtService;
    @Autowired private AiIntegrationTest.FakeAiClient fakeAi;

    private ResultActions ai(Resume resume, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/resumes/" + resume.getId() + "/chat")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void appWideCapReturns503ForChatAndNewSummaries() throws Exception {
        fakeAi.configured = true;
        fakeAi.fail = false;
        fakeAi.reply = "ok";

        User user = userRepository.save(new User("cap-" + UUID.randomUUID() + "@example.com", "hash"));
        Resume resume = resumeRepository.save(new Resume(user, "cv.pdf", "PDF", "Alex\nSKILLS\nJava"));
        String token = "Bearer " + jwtService.generateToken(user);

        // one real call first
        ai(resume, token, "{\"message\":\"hi\"}").andExpect(status().isOk());

        // other users use up the rest of today's app-wide budget
        var startOfToday = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        long usedByEveryone = usageRepository.countByCreatedAtGreaterThanEqual(startOfToday);
        User others = userRepository.save(new User("cap-others-" + UUID.randomUUID() + "@example.com", "hash"));
        List<AiUsage> rows = new ArrayList<>();
        for (long i = usedByEveryone; i < CAP; i++) {
            rows.add(new AiUsage(others));
        }
        usageRepository.saveAll(rows);
        int callsBefore = fakeAi.calls;

        // our user has only used 1 of 10, but the app cap is reached
        ai(resume, token, "{\"message\":\"hi again\"}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message").value("AI service not available right now"));
        assertEquals(callsBefore, fakeAi.calls);

        // a summary that isn't saved yet is refused too
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/resumes/" + resume.getId() + "/summary").header("Authorization", token))
                .andExpect(status().isServiceUnavailable());
        assertEquals(callsBefore, fakeAi.calls);
    }

    @Test
    void userLimitIs429EvenWhenAppHasBudget() throws Exception {
        fakeAi.configured = true;
        fakeAi.fail = false;
        fakeAi.reply = "ok";
        User user = userRepository.save(new User("cap-user-" + UUID.randomUUID() + "@example.com", "hash"));
        Resume resume = resumeRepository.save(new Resume(user, "cv.pdf", "PDF", "Alex\nSKILLS\nJava"));
        List<AiUsage> rows = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            rows.add(new AiUsage(user));
        }
        usageRepository.saveAll(rows);

        ai(resume, "Bearer " + jwtService.generateToken(user), "{\"message\":\"hi\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Daily AI limit reached"));
    }
}
