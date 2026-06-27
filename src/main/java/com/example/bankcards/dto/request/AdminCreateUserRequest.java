package com.example.bankcards.dto.request;

import com.example.bankcards.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(name = "AdminCreateUserRequest", description = "Запрос администратора на создание пользователя с заданной ролью")
public record AdminCreateUserRequest(

        @Schema(description = "Email пользователя (уникальный)", example = "user@gmail.com")
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @Schema(description = "Имя пользователя, от 2 до 50 символов", example = "42bratuha")
        @NotBlank(message = "Username is required")
        @Size(min = 2, max = 50, message = "Username must be between 2 and 50 characters")
        String username,

        @Schema(description = "Пароль, не менее 8 символов", example = "strongpassword123")
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @Schema(description = "Роль пользователя", example = "USER")
        @NotNull(message = "Role is required")
        User.Role role
) {}
