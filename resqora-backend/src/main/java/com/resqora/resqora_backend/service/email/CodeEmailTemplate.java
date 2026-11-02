package com.resqora.resqora_backend.service.email;

import com.resqora.resqora_backend.entity.EmailCode.Purpose;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds the verification / password-reset email from the frontend's template
 * (src/main/resources/email/code-email.html, placeholders {{TITLE}} {{INTRO}} {{IGNORE}} {{CODE}} {{NAME}}).
 *
 * Why this way:
 * - the template is read once at start-up, not on every email;
 * - every value is HTML-escaped, so a user named "<script>" can't inject markup into the email;
 * - placeholders are filled in ONE pass over the template, so a name that contains "{{CODE}}" stays text
 *   and is never replaced by the code;
 * - the plain-text version uses the same wording as the HTML one.
 */
@Component
public class CodeEmailTemplate {
    static final String RESOURCE = "/email/code-email.html";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z]+)}}");

    private final String html;

    public CodeEmailTemplate() {
        try (InputStream in = CodeEmailTemplate.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Email template not found: " + RESOURCE);
            }
            this.html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record CodeEmail(String subject, String text, String html) {
    }

    public CodeEmail render(Purpose purpose, String code, String name) {
        boolean verify = purpose == Purpose.VERIFY_EMAIL;
        String title = verify ? "Verify your email" : "Reset your password";
        String intro = verify ? "use this code to finish creating your Resqora account."
                : "use this code to reset your Resqora password.";
        String ignore = verify ? "If you didn't sign up for Resqora, you can ignore this email."
                : "If you didn't ask to reset your password, you can ignore this email. Your password stays the same.";
        String greetingName = name == null || name.isBlank() ? "there" : name.strip();
        String subject = (verify ? "Your Resqora verification code: " : "Your Resqora password reset code: ") + code;

        Map<String, String> values = Map.of("TITLE", title, "INTRO", intro, "IGNORE", ignore,
                "CODE", code, "NAME", greetingName);
        // "10 minutes" is written in the template itself (not a placeholder), so the text says the same.
        String text = title + "\n\n"
                + "Hi " + greetingName + ", " + intro + "\n\n"
                + code + "\n"
                + "Expires in 10 minutes\n\n"
                + "Enter this code on the Resqora page you came from. "
                + "Never share it with anyone. Resqora will never ask you for it.\n\n"
                + ignore + "\n\n"
                + "Sent by Resqora (https://resqora.xyz), the resume analysis platform.\n"
                + "This is an automated message. Replies aren't monitored.\n";
        return new CodeEmail(subject, text, fill(values));
    }

    private String fill(Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(html);
        StringBuilder out = new StringBuilder(html.length() + 200);
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            if (value == null) {
                throw new IllegalStateException("Unknown placeholder in email template: " + matcher.group());
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(HtmlUtils.htmlEscape(value, "UTF-8")));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
