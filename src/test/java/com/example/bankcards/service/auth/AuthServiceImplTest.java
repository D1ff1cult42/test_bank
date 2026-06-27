package com.example.bankcards.service.auth;

import com.example.bankcards.dto.request.LoginRequest;
import com.example.bankcards.dto.request.RegisterRequest;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.InvalidCredentialsException;
import com.example.bankcards.exception.TokenException;
import com.example.bankcards.exception.UserAlreadyExistsException;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.security.record.AuthTokens;
import com.example.bankcards.security.JwtService;
import com.example.bankcards.service.auth.impl.AuthServiceImpl;
import com.nimbusds.jwt.JWTClaimsSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String EMAIL = "user@example.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @InjectMocks
    private AuthServiceImpl authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(EMAIL);
        user.setUsername("user");
        user.setActive(true);
        user.setTokenVersion(3);
    }

    private void stubTokenIssuing() {
        lenient().when(jwtService.generateAccessToken(any())).thenReturn("access");
        lenient().when(jwtService.generateRefreshToken(any())).thenReturn("refresh");
        lenient().when(jwtService.getAccessTokenExpiration()).thenReturn(Duration.ofMinutes(15));
        lenient().when(jwtService.getRefreshTokenExpiration()).thenReturn(Duration.ofDays(7));
    }

    @Test
    void register_createsUserAndIssuesTokens() {
        stubTokenIssuing();
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthTokens tokens = authService.register(new RegisterRequest(EMAIL, "password123", "user"));

        assertThat(tokens.accessToken()).isEqualTo("access");
        assertThat(tokens.refreshToken()).isEqualTo("refresh");
        verify(userRepository).save(argThat(u ->
                u.getEmail().equals(EMAIL)
                        && u.getUsername().equals("user")
                        && u.getPasswordHash().equals("hash")
                        && u.getRole() == User.Role.USER));
    }

    @Test
    void register_rejectsExistingEmail() {
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest(EMAIL, "password123", "user")))
                .isInstanceOf(UserAlreadyExistsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void login_authenticatesAndIssuesTokens() {
        stubTokenIssuing();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        AuthTokens tokens = authService.login(new LoginRequest(EMAIL, "password123"));

        assertThat(tokens.accessToken()).isEqualTo("access");
        assertThat(user.getLastLogin()).isNotNull();
        verify(authenticationManager).authenticate(any());
    }

    @Test
    void login_rejectsBadCredentials() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "wrong")))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void refreshToken_rotatesAndIncrementsTokenVersion() {
        stubTokenIssuing();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("userId", user.getId().toString())
                .claim("tokenVersion", 3)
                .build();
        when(jwtService.parseAndValidate("refresh-token", JwtService.TOKEN_TYPE_REFRESH)).thenReturn(claims);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.refreshToken("refresh-token");

        assertThat(user.getTokenVersion()).isEqualTo(4);
        verify(userRepository).save(user);
    }

    @Test
    void refreshToken_detectsReuseAndRevokesAllSessions() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("userId", user.getId().toString())
                .claim("tokenVersion", 1) // устаревшая версия → reuse
                .build();
        when(jwtService.parseAndValidate("old-token", JwtService.TOKEN_TYPE_REFRESH)).thenReturn(claims);
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.refreshToken("old-token"))
                .isInstanceOf(TokenException.class)
                .hasMessageContaining("revoked");
        assertThat(user.getTokenVersion()).isEqualTo(4); // версия поднята при обнаружении reuse
        verify(userRepository).save(user);
    }

    @Test
    void logoutAll_incrementsTokenVersion() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        authService.logoutAll(EMAIL);

        assertThat(user.getTokenVersion()).isEqualTo(4);
        verify(userRepository).save(user);
    }
}
