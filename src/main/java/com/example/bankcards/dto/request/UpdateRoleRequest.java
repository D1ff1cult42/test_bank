package com.example.bankcards.dto.request;

import com.example.bankcards.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateRoleRequest", description = "Запрос на изменение роли пользователя")
public record UpdateRoleRequest(
        @Schema(description = "Новая роль пользователя", example = "ADMIN")
        @NotNull(message = "Role is required")
        User.Role role
) {}
