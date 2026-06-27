package com.example.bankcards.service.card.interfaces;

import com.example.bankcards.dto.request.CreateCardRequest;
import com.example.bankcards.dto.request.TransferRequest;
import com.example.bankcards.dto.response.CardResponse;
import com.example.bankcards.entity.Card;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface CardService {

    // ADMIN
    CardResponse createCard(CreateCardRequest request);

    // USER
    Page<CardResponse> getOwnedCards(String email, Card.Status status, String search, Pageable pageable);

    // ADMIN
    Page<CardResponse> getAllCards(Card.Status status, String search, Pageable pageable);

    // USER
    CardResponse getOwnedCard(String email, UUID cardId);

    // ADMIN
    void blockCard(UUID cardId);

    // ADMIN
    void activateCard(UUID cardId);

    // USER
    void requestBlock(String email, UUID cardId);

    // ADMIN
    void deleteCard(UUID cardId);

    // USER
    void transfer(String email, TransferRequest request);

    // USER
    BigDecimal getBalance(String email, UUID cardId);
}
