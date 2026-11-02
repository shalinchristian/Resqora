package com.resqora.resqora_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// excluded UserDetailsServiceAutoConfiguration since login goes through my own JWT filter.
// spring's default in-memory user (the random password it prints at startup) was never used anyway
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ResqoraBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ResqoraBackendApplication.class, args);
	}

}
