package com.resqora.resqora_backend.service.email;

import com.resqora.resqora_backend.entity.EmailCode.Purpose;
import com.resqora.resqora_backend.service.email.CodeEmailTemplate.CodeEmail;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodeEmailTemplateTest {
    private final CodeEmailTemplate template = new CodeEmailTemplate();

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    @Test
    void verificationEmailFillsEveryPlaceholder() {
        CodeEmail email = template.render(Purpose.VERIFY_EMAIL, "123456", "Asha");
        assertEquals("Your Resqora verification code: 123456", email.subject());
        assertFalse(email.html().contains("{{"));
        assertEquals(2, count(email.html(), "123456")); // inbox preview line + code box
        assertTrue(email.html().contains("Hi Asha, use this code to finish creating your Resqora account."));
        assertTrue(email.html().contains("<title>Verify your email</title>"));
        assertTrue(email.html().contains("If you didn&#39;t sign up for Resqora, you can ignore this email."));
        assertTrue(email.text().startsWith("Verify your email\n\nHi Asha, use this code to finish creating your Resqora account."));
        assertTrue(email.text().contains("123456"));
        assertTrue(email.text().contains("If you didn't sign up for Resqora, you can ignore this email."));
    }

    @Test
    void resetEmailUsesResetWording() {
        CodeEmail email = template.render(Purpose.RESET_PASSWORD, "654321", "Asha");
        assertEquals("Your Resqora password reset code: 654321", email.subject());
        assertTrue(email.html().contains("Reset your password"));
        assertTrue(email.html().contains("use this code to reset your Resqora password."));
        assertTrue(email.text().contains("Your password stays the same."));
    }

    @Test
    void missingNameBecomesThere() {
        assertTrue(template.render(Purpose.VERIFY_EMAIL, "123456", null).html().contains("Hi there,"));
        assertTrue(template.render(Purpose.VERIFY_EMAIL, "123456", "  ").text().contains("Hi there,"));
    }

    @Test
    void nameIsEscapedAndNeverTreatedAsAPlaceholder() {
        CodeEmail email = template.render(Purpose.VERIFY_EMAIL, "123456", "<b>Eve</b> & {{CODE}}");
        assertTrue(email.html().contains("Hi &lt;b&gt;Eve&lt;/b&gt; &amp; {{CODE}},"));
        assertFalse(email.html().contains("<b>Eve"));
        assertEquals(2, count(email.html(), "123456"));
    }
}
