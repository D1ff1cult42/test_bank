package com.example.bankcards.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Builder
@Schema(name = "AuthResponse", description = "Результат аутентификации. Сами токены передаются в HttpOnly cookie.")
public record AuthResponse(
    @Schema(description = "Тип токена", example = "Bearer")
    String tokenType,
    @Schema(description = "Время истечения access-токена", example = "2026-06-26T12:15:00.000+00:00")
    Instant expiresAt
)
{}
