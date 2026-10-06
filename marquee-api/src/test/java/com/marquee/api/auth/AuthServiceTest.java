package com.marquee.api.auth;

import com.marquee.api.security.JwtService;
import com.marquee.api.user.Role;
import com.marquee.api.user.User;
import com.marquee.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerRejectsDuplicateEmails() throws Exception {
        User existing = new User("dup@example.com", "encoded");
        setId(existing, 1L);
        when(userRepository.findByEmail("dup@example.com")).thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> authService.register(new RegisterRequest("dup@example.com", "secret123")));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertEquals("User already exists", ex.getReason());
    }

    @Test
    void loginReturnsTokensForAuthenticatedUser() throws Exception {
        User user = new User("user@example.com", "encoded");
        setId(user, 2L);
        user.setRole(Role.USER);

        when(authenticationManager.authenticate(any())).thenReturn(null);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");

        AuthResponse response = authService.login(new LoginRequest("user@example.com", "secret123"));

        assertEquals("access-token", response.accessToken());
        assertEquals("refresh-token", response.refreshToken());
        assertEquals("user@example.com", response.email());
        verify(userRepository).save(user);
    }

    private static void setId(Object target, Long id) throws Exception {
        Field field = target.getClass().getDeclaredField("id");
        field.setAccessible(true);
        field.set(target, id);
    }
}
