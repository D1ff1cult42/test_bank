package com.example.bankcards.dto.response;

import com.example.bankcards.entity.Card;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Builder
@Schema(name = "CardResponse", description = "Представление карты с маскированным номером (полный номер наружу не отдаётся)")
public record CardResponse(
        @Schema(description = "Идентификатор карты", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID id,

        @Schema(description = "Маскированный номер карты", example = "**** **** **** 1234")
        String maskedNumber,

        @Schema(description = "Имя владельца карты", example = "42bratuha")
        String ownerUsername,

        @Schema(description = "Дата окончания срока действия", example = "2029-06-30")
        LocalDate expiryDate,

        @Schema(description = "Статус карты: ACTIVE — активна, BLOCKED — заблокирована, EXPIRED — истёк срок", example = "ACTIVE")
        Card.Status status,

        @Schema(description = "Текущий баланс карты", example = "100.00")
        BigDecimal balance
) {
    public static CardResponse from(Card card) {
        return CardResponse.builder()
                .id(card.getId())
                .maskedNumber(card.getMaskedNumber())
                .ownerUsername(card.getOwner().getUsername())
                .expiryDate(card.getExpiryDate())
                .status(card.getStatus())
                .balance(card.getBalance())
                .build();
    }
}
