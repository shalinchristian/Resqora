package com.resqora.resqora_backend.service;

import org.springframework.stereotype.Service;

@Service
public class GreetingService {
    public String getGreetings(){
        return "Welcome to Resqora!";
    }
}
