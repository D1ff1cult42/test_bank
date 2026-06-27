package com.example.bankcards.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "TransferRequest", description = "Перевод средств между двумя картами одного владельца")
public record TransferRequest(

        @NotNull(message = "Source card id is required")
        @Schema(description = "Id карты списания (откуда переводим)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID fromCardId,

        @NotNull(message = "Target card id is required")
        @Schema(description = "Id карты зачисления (куда переводим)", example = "9c858901-8a57-4791-81fe-4c455b099bc9")
        UUID toCardId,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be positive")
        @Schema(description = "Сумма перевода, должна быть положительной", example = "100.00")
        BigDecimal amount
) {}
