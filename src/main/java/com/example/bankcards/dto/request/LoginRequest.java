package com.example.bankcards.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Запрос на вход пользователя в систему")
public record LoginRequest(
    @Schema(description = "Email пользователя", example = "user@gmail.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @Schema(description = "Пароль пользователя", example = "strongpassword123")
    @NotBlank(message = "Password is required")
    String password
    )
{}

