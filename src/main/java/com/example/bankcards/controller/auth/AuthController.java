package com.example.bankcards.controller.auth;

import com.example.bankcards.dto.request.LoginRequest;
import com.example.bankcards.dto.request.RegisterRequest;
import com.example.bankcards.dto.response.AuthResponse;
import com.example.bankcards.dto.response.ErrorResponse;
import com.example.bankcards.exception.TokenException;
import com.example.bankcards.security.record.AuthTokens;
import com.example.bankcards.security.CookieUtils;
import com.example.bankcards.service.auth.interfaces.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@Slf4j
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Аутентификация", description = "Регистрация, вход, обновление токенов и выход")
public class AuthController {

    private final AuthService authService;
    private final CookieUtils cookieUtils;

    @Operation(summary = "Регистрация нового пользователя",
            description = "Регистрирует пользователя с ролью USER. Access/refresh токены возвращаются в HttpOnly cookie.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Пользователь успешно зарегистрирован",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = AuthResponse.class))),
                    @ApiResponse(responseCode = "409", description = "Пользователь уже существует",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Некорректные входные данные",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest registerRequest,
                                                 HttpServletResponse response) {
        log.info("Received registration request for email: {}", registerRequest.email());
        AuthTokens tokens = authService.register(registerRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(writeCookies(response, tokens));
    }

    @Operation(summary = "Вход пользователя",
            description = "Аутентифицирует пользователя. Access/refresh токены возвращаются в HttpOnly cookie.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Успешный вход",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = AuthResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Неверные учётные данные",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class))),
                    @ApiResponse(responseCode = "400", description = "Некорректные входные данные",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        log.info("Received login request for email: {}", request.email());
        AuthTokens tokens = authService.login(request);
        return ResponseEntity.ok(writeCookies(response, tokens));
    }

    @Operation(summary = "Обновление токенов",
            description = "Выдаёт новую пару токенов по refresh-токену из HttpOnly cookie (с ротацией).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Токены успешно обновлены",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = AuthResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Невалидный или истёкший refresh-токен",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtils.readCookie(request, CookieUtils.REFRESH_TOKEN_COOKIE)
                .orElseThrow(() -> new TokenException("Refresh token cookie is missing"));
        AuthTokens tokens = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(writeCookies(response, tokens));
    }

    @Operation(summary = "Выход",
            description = "Очищает cookie с токенами на стороне клиента.",
            responses = @ApiResponse(responseCode = "204", description = "Выход выполнен"))
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        cookieUtils.clearTokens(response);
        log.info("User logged out");
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Выход со всех устройств",
            description = "Отзывает все refresh-токены пользователя за счёт инкремента версии токена.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Все сессии отозваны"),
                    @ApiResponse(responseCode = "401", description = "Не аутентифицирован",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = ErrorResponse.class)))
            })
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication, HttpServletResponse response) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        authService.logoutAll(authentication.getName());
        cookieUtils.clearTokens(response);
        log.info("User logged out from all sessions: {}", authentication.getName());
        return ResponseEntity.noContent().build();
    }

    private AuthResponse writeCookies(HttpServletResponse response, AuthTokens tokens) {
        cookieUtils.writeAccessToken(response, tokens.accessToken(), tokens.accessTtl());
        cookieUtils.writeRefreshToken(response, tokens.refreshToken(), tokens.refreshTtl());
        Instant expiresAt = Instant.now().plus(tokens.accessTtl());
        return AuthResponse.builder()
                .tokenType("Bearer")
                .expiresAt(expiresAt)
                .build();
    }
}
