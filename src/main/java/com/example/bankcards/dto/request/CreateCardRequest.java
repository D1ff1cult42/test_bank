package com.example.bankcards.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "CreateCardRequest", description = "Запрос на выпуск новой карты для пользователя")
public record CreateCardRequest(

        @NotNull(message = "Owner id is required")
        @Schema(description = "Id пользователя — будущего владельца карты", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID ownerId,

        @PositiveOrZero(message = "Initial balance must be zero or positive")
        @Schema(description = "Начальный баланс карты (необязательно, по умолчанию 0)", example = "0.00")
        BigDecimal initialBalance
) {}
