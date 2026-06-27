package com.example.bankcards.controller.auth;

import com.example.bankcards.controller.ControllerTestSecurityConfig;
import com.example.bankcards.dto.request.LoginRequest;
import com.example.bankcards.dto.request.RegisterRequest;
import com.example.bankcards.exception.InvalidCredentialsException;
import com.example.bankcards.exception.UserAlreadyExistsException;
import com.example.bankcards.security.CookieUtils;
import com.example.bankcards.security.JwtService;
import com.example.bankcards.security.record.AuthTokens;
import com.example.bankcards.service.auth.interfaces.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(ControllerTestSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockBean
    private AuthService authService;
    @MockBean
    private CookieUtils cookieUtils;
    @MockBean
    private JwtService jwtService;

    private AuthTokens tokens() {
        return new AuthTokens("access-jwt", "refresh-jwt", Duration.ofMinutes(15), Duration.ofDays(7));
    }

    @Test
    @WithAnonymousUser
    void register_returns201AndWritesCookies() throws Exception {
        when(authService.register(any(RegisterRequest.class))).thenReturn(tokens());

        var body = new RegisterRequest("user@gmail.com", "password123", "user");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").exists());

        verify(cookieUtils).writeAccessToken(any(), eq("access-jwt"), any());
        verify(cookieUtils).writeRefreshToken(any(), eq("refresh-jwt"), any());
    }

    @Test
    @WithAnonymousUser
    void register_invalidEmail_returns400() throws Exception {
        var body = new RegisterRequest("bad-email", "password123", "user");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Error"));
        verify(authService, never()).register(any());
    }

    @Test
    @WithAnonymousUser
    void register_duplicate_returns409() throws Exception {
        when(authService.register(any()))
                .thenThrow(new UserAlreadyExistsException("User with email user@gmail.com already exists"));

        var body = new RegisterRequest("user@gmail.com", "password123", "user");
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithAnonymousUser
    void login_returns200() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(tokens());

        var body = new LoginRequest("user@gmail.com", "password123");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
        verify(cookieUtils).writeAccessToken(any(), eq("access-jwt"), any());
    }

    @Test
    @WithAnonymousUser
    void login_invalidCredentials_returns401() throws Exception {
        when(authService.login(any())).thenThrow(new InvalidCredentialsException("Invalid email or password"));

        var body = new LoginRequest("user@gmail.com", "wrong");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid Credentials"));
    }

    @Test
    @WithAnonymousUser
    void refresh_withCookie_returns200() throws Exception {
        when(cookieUtils.readCookie(any(), eq(CookieUtils.REFRESH_TOKEN_COOKIE)))
                .thenReturn(Optional.of("refresh-jwt"));
        when(authService.refreshToken("refresh-jwt")).thenReturn(tokens());

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
        verify(authService).refreshToken("refresh-jwt");
    }

    @Test
    @WithAnonymousUser
    void refresh_withoutCookie_returns401() throws Exception {
        when(cookieUtils.readCookie(any(), eq(CookieUtils.REFRESH_TOKEN_COOKIE)))
                .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Token Error"));
        verify(authService, never()).refreshToken(any());
    }

    @Test
    @WithAnonymousUser
    void logout_returns204AndClearsCookies() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent());
        verify(cookieUtils).clearTokens(any());
    }

    @Test
    @WithMockUser(username = "user@gmail.com")
    void logoutAll_authenticated_returns204() throws Exception {
        mockMvc.perform(post("/api/auth/logout-all"))
                .andExpect(status().isNoContent());
        verify(authService).logoutAll("user@gmail.com");
        verify(cookieUtils).clearTokens(any());
    }
}
