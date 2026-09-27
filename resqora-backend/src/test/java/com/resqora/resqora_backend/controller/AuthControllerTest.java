package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.MeResponse;
import com.resqora.resqora_backend.entity.User;
import com.resqora.resqora_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AuthController authController = new AuthController(null, userRepository);

    @Test
    void returnsAuthenticatedUserDetails() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(42L);
        when(user.getEmail()).thenReturn("user@example.com");
        when(userRepository.findById(42L)).thenReturn(Optional.of(user));

        ResponseEntity<MeResponse> response = authController.me(authenticationFor(42L));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new MeResponse(42L, "user@example.com"), response.getBody());
    }

    @Test
    void returnsNotFoundWhenAuthenticatedUserDoesNotExist() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        ResponseEntity<MeResponse> response = authController.me(authenticationFor(42L));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    private Authentication authenticationFor(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}