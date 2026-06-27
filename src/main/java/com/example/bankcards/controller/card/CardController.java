package com.example.bankcards.controller.card;

import com.example.bankcards.dto.request.CreateCardRequest;
import com.example.bankcards.dto.request.TransferRequest;
import com.example.bankcards.dto.response.CardResponse;
import com.example.bankcards.dto.response.PageResponse;
import com.example.bankcards.entity.Card;
import com.example.bankcards.service.card.interfaces.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@Slf4j
@RequestMapping("/api/cards")
@RequiredArgsConstructor
@Tag(name = "Карты", description = "Управление картами, просмотр и переводы между своими картами")
public class CardController {

    private final CardService cardService;

    @Operation(summary = "Выпустить новую карту (только ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CardResponse> createCard(@Valid @RequestBody CreateCardRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(cardService.createCard(request));
    }

    @Operation(summary = "Список всех карт с поиском и пагинацией (только ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PageResponse<CardResponse> getAllCards(@RequestParam(required = false) Card.Status status,
                                                  @RequestParam(required = false) String search,
                                                  @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(cardService.getAllCards(status, search, pageable));
    }

    @Operation(summary = "Список карт текущего пользователя с поиском и пагинацией")
    @GetMapping("/my")
    public PageResponse<CardResponse> getMyCards(Authentication authentication,
                                                 @RequestParam(required = false) Card.Status status,
                                                 @RequestParam(required = false) String search,
                                                 @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(cardService.getOwnedCards(authentication.getName(), status, search, pageable));
    }

    @Operation(summary = "Получить одну из карт текущего пользователя")
    @GetMapping("/{id}")
    public CardResponse getCard(Authentication authentication, @PathVariable UUID id) {
        return cardService.getOwnedCard(authentication.getName(), id);
    }

    @Operation(summary = "Получить баланс одной из карт текущего пользователя")
    @GetMapping("/{id}/balance")
    public BigDecimal getBalance(Authentication authentication, @PathVariable UUID id) {
        return cardService.getBalance(authentication.getName(), id);
    }

    @Operation(summary = "Запросить блокировку одной из своих карт")
    @PostMapping("/{id}/block-request")
    public ResponseEntity<Void> requestBlock(Authentication authentication, @PathVariable UUID id) {
        cardService.requestBlock(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Перевести деньги между двумя своими картами")
    @PostMapping("/transfer")
    public ResponseEntity<Void> transfer(Authentication authentication,
                                         @Valid @RequestBody TransferRequest request) {
        cardService.transfer(authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Заблокировать карту (только ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/block")
    public ResponseEntity<Void> blockCard(@PathVariable UUID id) {
        cardService.blockCard(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Активировать карту (только ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/activate")
    public ResponseEntity<Void> activateCard(@PathVariable UUID id) {
        cardService.activateCard(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Удалить карту (только ADMIN)")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCard(@PathVariable UUID id) {
        cardService.deleteCard(id);
        return ResponseEntity.noContent().build();
    }
}
