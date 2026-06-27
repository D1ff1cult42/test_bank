package com.example.bankcards.dto.response;

import com.example.bankcards.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
@Schema(name = "UserResponse", description = "Представление пользователя для административных операций")
public record UserResponse(
        @Schema(description = "Идентификатор пользователя", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,
        @Schema(description = "Email пользователя", example = "user@gmail.com")
        String email,
        @Schema(description = "Имя пользователя", example = "42bratuha")
        String username,
        @Schema(description = "Роль пользователя", example = "USER")
        User.Role role,
        @Schema(description = "Активен ли пользователь (false — заблокирован)", example = "true")
        boolean active,
        @Schema(description = "Дата и время создания учётной записи", example = "2026-06-28T12:00:00Z")
        Instant createdAt,
        @Schema(description = "Дата и время последнего входа", example = "2026-06-28T12:30:00Z")
        Instant lastLogin
) {
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .role(user.getRole())
                .active(user.isActive())
                .createdAt(user.getCreatedAt())
                .lastLogin(user.getLastLogin())
                .build();
    }
}
