package com.resqora.resqora_backend.service.email;

// Sends one email with a plain-text and an HTML version. Implemented with Resend; tests swap in a fake.
public interface EmailSender {
    void send(String to, String subject, String text, String html);
}
