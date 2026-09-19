package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.service.GreetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    private final GreetingService greetingService;
    public TestController(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    @GetMapping("/hello")
    public String hello(){
        return greetingService.getGreetings();
    }
}
