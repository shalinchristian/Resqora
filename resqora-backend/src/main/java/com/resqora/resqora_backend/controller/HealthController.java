package com.resqora.resqora_backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// cheap public check to see if the server is up. no login, no DB call, so it answers as soon as the app starts.
// the frontend and an uptime pinger hit this to wake render's free instance from sleep
@RestController
public class HealthController {
    private static final Map<String, String> UP = Map.of("status", "UP");

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return UP;
    }
}
