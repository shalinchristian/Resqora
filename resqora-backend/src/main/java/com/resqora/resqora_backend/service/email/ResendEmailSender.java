package com.resqora.resqora_backend.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

// Sends email through Resend's HTTP API (https://api.resend.com/emails).
// We use HTTP instead of SMTP because Render's free plan blocks outgoing SMTP ports.
// Without RESEND_API_KEY nothing is sent; that is only meant for local development.
@Component
public class ResendEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(ResendEmailSender.class);

    private final RestClient restClient;
    private final String apiKey;
    private final String from;
    private final boolean logEmails;

    public ResendEmailSender(
            @Value("${resqora.email.resend-api-key:}") String apiKey,
            @Value("${resqora.email.from:Resqora <noreply@resqora.xyz>}") String from,
            @Value("${resqora.email.resend-base-url:https://api.resend.com}") String baseUrl,
            @Value("${resqora.email.log-emails:false}") boolean logEmails) {
        this.apiKey = apiKey;
        this.from = from;
        this.logEmails = logEmails;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public void send(String to, String subject, String text) {
        if (apiKey == null || apiKey.isBlank()) {
            // local development only: never turn this on in production, the email contains the code
            if (logEmails) {
                log.info("Email not sent (no RESEND_API_KEY). To: {} | {} | {}", to, subject, text);
            } else {
                log.warn("Email not sent: RESEND_API_KEY is not set");
            }
            return;
        }
        restClient.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("from", from, "to", List.of(to), "subject", subject, "text", text))
                .retrieve()
                .toBodilessEntity();
    }
}
