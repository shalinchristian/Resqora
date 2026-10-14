package com.resqora.resqora_backend.controller;

import com.resqora.resqora_backend.dto.MeResponse;
import com.resqora.resqora_backend.exception.AuthenticatedUserNotFoundException;
import com.resqora.resqora_backend.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthControllerTest {
    private final AuthService authService = mock(AuthService.class);
    private final AuthController authController = new AuthController(authService);

    @Test
    void returnsAuthenticatedUserDetails() {
        when(authService.me(42L)).thenReturn(new MeResponse(42L, "user@example.com", "Asha Rao"));

        assertEquals(new MeResponse(42L, "user@example.com", "Asha Rao"), authController.me(authenticationFor(42L)));
    }

    @Test
    void missingUserIsPassedOnAsException() {
        // GlobalExceptionHandler turns this into 401 with the standard error body
        when(authService.me(42L)).thenThrow(new AuthenticatedUserNotFoundException());

        assertThrows(AuthenticatedUserNotFoundException.class, () -> authController.me(authenticationFor(42L)));
    }

    private Authentication authenticationFor(Long userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }
}
