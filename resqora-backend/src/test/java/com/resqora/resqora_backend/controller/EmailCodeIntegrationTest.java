package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.repository.UserRepository;
import com.resqora.resqora_backend.service.email.EmailSender;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "resqora.email.require-verification=true",
        "resqora.email.resend-cooldown-seconds=0",
        "resqora.email.max-code-requests-per-ip=1000",
        "resqora.demo.enabled=true"})
@AutoConfigureMockMvc
class EmailCodeIntegrationTest {
    // records emails instead of calling Resend
    static class FakeEmailSender implements EmailSender {
        final Map<String, String> lastCode = new ConcurrentHashMap<>();
        final Map<String, Integer> sent = new ConcurrentHashMap<>();

        @Override
        public void send(String to, String subject, String text, String html) {
            Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(text);
            assertTrue(m.find(), "email must contain a 6-digit code");
            lastCode.put(to, m.group(1));
            sent.merge(to, 1, Integer::sum);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        FakeEmailSender fakeEmailSender() {
            return new FakeEmailSender();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private FakeEmailSender emails;
    @Autowired private UserRepository userRepository;

    private ResultActions postJson(String url, String json) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void register(String email, String password) throws Exception {
        postJson("/api/auth/register", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"name\":\"Asha\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    private ResultActions login(String email, String password) throws Exception {
        return postJson("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    @Test
    void signUpSendsCodeAndLoginWaitsForVerification() throws Exception {
        register("v1@example.com", "password123");
        String code = emails.lastCode.get("v1@example.com");
        assertFalse(userRepository.findByEmail("v1@example.com").orElseThrow().isEmailVerified());

        login("v1@example.com", "password123").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Email not verified"));
        // wrong password still says 401, so 403 only appears to someone who knows the password
        login("v1@example.com", "wrongpass1").andExpect(status().isUnauthorized());

        postJson("/api/auth/verify-email", "{\"email\":\"V1@Example.com \",\"code\":\"" + code + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.name").value("Asha"));
        login("v1@example.com", "password123").andExpect(status().isOk());

        // a code works only once
        postJson("/api/auth/verify-email", "{\"email\":\"v1@example.com\",\"code\":\"" + code + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired code"));
        // verified email can't be registered again
        postJson("/api/auth/register", "{\"email\":\"v1@example.com\",\"password\":\"password123\",\"name\":\"X\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void fiveWrongCodesKillTheCode() throws Exception {
        register("v2@example.com", "password123");
        String code = emails.lastCode.get("v2@example.com");
        for (int i = 0; i < 5; i++) {
            postJson("/api/auth/verify-email", "{\"email\":\"v2@example.com\",\"code\":\"" + wrong(code) + "\"}")
                    .andExpect(status().isBadRequest());
        }
        // even the right code is refused now; a new one is needed
        postJson("/api/auth/verify-email", "{\"email\":\"v2@example.com\",\"code\":\"" + code + "\"}")
                .andExpect(status().isBadRequest());

        postJson("/api/auth/resend-code", "{\"email\":\"v2@example.com\"}").andExpect(status().isAccepted());
        String fresh = emails.lastCode.get("v2@example.com");
        postJson("/api/auth/verify-email", "{\"email\":\"v2@example.com\",\"code\":\"" + fresh + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void resendReplacesTheOldCode() throws Exception {
        register("v3@example.com", "password123");
        String first = emails.lastCode.get("v3@example.com");
        postJson("/api/auth/resend-code", "{\"email\":\"v3@example.com\"}").andExpect(status().isAccepted());
        String second = emails.lastCode.get("v3@example.com");
        if (!first.equals(second)) {
            postJson("/api/auth/verify-email", "{\"email\":\"v3@example.com\",\"code\":\"" + first + "\"}")
                    .andExpect(status().isBadRequest());
        }
        postJson("/api/auth/verify-email", "{\"email\":\"v3@example.com\",\"code\":\"" + second + "\"}")
                .andExpect(status().isOk());
    }

    @Test
    void unverifiedEmailCanBeRegisteredAgainByTheRealOwner() throws Exception {
        register("v4@example.com", "password123");
        register("v4@example.com", "otherpass456");
        String code = emails.lastCode.get("v4@example.com");
        postJson("/api/auth/verify-email", "{\"email\":\"v4@example.com\",\"code\":\"" + code + "\"}")
                .andExpect(status().isOk());
        login("v4@example.com", "password123").andExpect(status().isUnauthorized());
        login("v4@example.com", "otherpass456").andExpect(status().isOk());
    }

    @Test
    void forgotAndResetPassword() throws Exception {
        register("r1@example.com", "password123");
        postJson("/api/auth/verify-email",
                "{\"email\":\"r1@example.com\",\"code\":\"" + emails.lastCode.get("r1@example.com") + "\"}")
                .andExpect(status().isOk());

        postJson("/api/auth/forgot-password", "{\"email\":\"r1@example.com\"}").andExpect(status().isAccepted());
        String code = emails.lastCode.get("r1@example.com");

        postJson("/api/auth/reset-password",
                "{\"email\":\"r1@example.com\",\"code\":\"" + wrong(code) + "\",\"newPassword\":\"newpass789\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/auth/reset-password",
                "{\"email\":\"r1@example.com\",\"code\":\"" + code + "\",\"newPassword\":\"short\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/auth/reset-password",
                "{\"email\":\"r1@example.com\",\"code\":\"" + code + "\",\"newPassword\":\"newpass789\"}")
                .andExpect(status().isNoContent());

        login("r1@example.com", "password123").andExpect(status().isUnauthorized());
        login("r1@example.com", "newpass789").andExpect(status().isOk());
        // used code can't reset again
        postJson("/api/auth/reset-password",
                "{\"email\":\"r1@example.com\",\"code\":\"" + code + "\",\"newPassword\":\"another123\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void resetAlsoVerifiesAnUnverifiedAccount() throws Exception {
        register("r2@example.com", "password123");
        postJson("/api/auth/forgot-password", "{\"email\":\"r2@example.com\"}").andExpect(status().isAccepted());
        // newest email is the reset code
        postJson("/api/auth/reset-password", "{\"email\":\"r2@example.com\",\"code\":\""
                + emails.lastCode.get("r2@example.com") + "\",\"newPassword\":\"newpass789\"}")
                .andExpect(status().isNoContent());
        login("r2@example.com", "newpass789").andExpect(status().isOk());
    }

    @Test
    void forgotPasswordGivesSameAnswerForUnknownEmailAndSendsNothing() throws Exception {
        postJson("/api/auth/forgot-password", "{\"email\":\"ghost@example.com\"}").andExpect(status().isAccepted());
        postJson("/api/auth/resend-code", "{\"email\":\"ghost@example.com\"}").andExpect(status().isAccepted());
        assertNull(emails.sent.get("ghost@example.com"));
        postJson("/api/auth/reset-password",
                "{\"email\":\"ghost@example.com\",\"code\":\"123456\",\"newPassword\":\"newpass789\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid or expired code"));
    }

    @Test
    void demoAccountStaysVerifiedAndCannotBeReset() throws Exception {
        var demo = userRepository.findByEmail("demo@resqora.app").orElseThrow();
        assertTrue(demo.isEmailVerified());
        postJson("/api/auth/forgot-password", "{\"email\":\"demo@resqora.app\"}").andExpect(status().isAccepted());
        assertNull(emails.sent.get("demo@resqora.app"));
    }

    @Test
    void badCodeFormatIs400() throws Exception {
        postJson("/api/auth/verify-email", "{\"email\":\"v1@example.com\",\"code\":\"12ab\"}")
                .andExpect(status().isBadRequest());
    }
}
