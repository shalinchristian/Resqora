package com.resqora.resqora_backend.service.email;

// sends one plain email. Implemented with Resend, tests swap in a fake
public interface EmailSender {
    void send(String to, String subject, String text);
}
