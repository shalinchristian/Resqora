package com.resqora.resqora_backend.service.email;

// sends one email with a plain text and an HTML version. Resend in the real app, tests use a fake
public interface EmailSender {
    void send(String to, String subject, String text, String html);
}
