package com.resqora.resqora_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// UserDetailsServiceAutoConfiguration is excluded: login uses our own JWT filter, so Spring Security's
// default in-memory user (random password printed at startup) is never used. Skipping it saves startup work.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class ResqoraBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ResqoraBackendApplication.class, args);
	}

}
