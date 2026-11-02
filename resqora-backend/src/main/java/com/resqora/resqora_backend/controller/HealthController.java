package com.resqora.resqora_backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Public, cheap "is the server up?" check. No login and no database call, so it answers as soon as the app has started.
// The frontend and an uptime pinger call it to wake the server from Render's free-plan sleep.
@RestController
public class HealthController {
    private static final Map<String, String> UP = Map.of("status", "UP");

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return UP;
    }
}
