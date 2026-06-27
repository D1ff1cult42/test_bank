package com.example.bankcards.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Builder
@Schema(name = "ErrorResponse", description = "Стандартное тело ответа об ошибке")
public record ErrorResponse(
    @Schema(description = "Человекочитаемое сообщение об ошибке", example = "Invalid credentials")
    String message,
    @Schema(description = "Тип или категория ошибки", example = "Unauthorized")
    String error,
    @Schema(description = "HTTP-статус ответа", example = "401")
    int status,
    @Schema(description = "Момент возникновения ошибки", example = "2026-06-28T12:00:00Z")
    Instant timestamp,
    @Schema(description = "Путь запроса, вызвавшего ошибку", example = "/api/auth/login")
    String path
){}


