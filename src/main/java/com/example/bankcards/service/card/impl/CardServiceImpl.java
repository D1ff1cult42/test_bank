package com.example.bankcards.service.card.impl;

import com.example.bankcards.dto.request.CreateCardRequest;
import com.example.bankcards.dto.request.TransferRequest;
import com.example.bankcards.dto.response.CardResponse;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.CardNotFoundException;
import com.example.bankcards.exception.CardOperationException;
import com.example.bankcards.exception.InvalidCredentialsException;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.card.interfaces.CardService;
import com.example.bankcards.util.CardNumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CardServiceImpl implements CardService {
    @Value("${card.validity-years}")
    private int cardValidityYears;
    @Value("${card.number-generation-attempts}")
    private int maxGenerationAttempts;

    private final CardRepository cardRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CardResponse createCard(CreateCardRequest request) {
        User owner = userRepository.findById(request.ownerId())
                .orElseThrow(() -> new CardOperationException("Owner not found: " + request.ownerId()));

        String number = generateUniqueNumber();

        Card card = new Card();
        card.setCardNumber(number);
        card.setCardNumberHash(CardNumberUtils.hash(number));
        card.setMaskedNumber(CardNumberUtils.mask(number));
        card.setOwner(owner);
        card.setExpiryDate(LocalDate.now().plusYears(cardValidityYears));
        card.setStatus(Card.Status.ACTIVE);
        card.setBalance(request.initialBalance() != null ? request.initialBalance() : BigDecimal.ZERO);

        card = cardRepository.save(card);
        log.info("Card {} issued for user {}", card.getId(), owner.getId());
        return CardResponse.from(card);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CardResponse> getOwnedCards(String email, Card.Status status, String search, Pageable pageable) {
        UUID ownerId = currentUser(email).getId();
        return cardRepository.searchOwned(ownerId, status, normalize(search), pageable)
                .map(CardResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CardResponse> getAllCards(Card.Status status, String search, Pageable pageable) {
        return cardRepository.searchAll(status, normalize(search), pageable)
                .map(CardResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public CardResponse getOwnedCard(String email, UUID cardId) {
        return CardResponse.from(getOwnedCardEntity(email, cardId));
    }

    @Override
    @Transactional
    public void blockCard(UUID cardId) {
        Card card = getActiveCardEntity(cardId);
        card.setStatus(Card.Status.BLOCKED);
        log.info("Card {} blocked by admin", cardId);
    }

    @Override
    @Transactional
    public void activateCard(UUID cardId) {
        Card card = getActiveCardEntity(cardId);
        if (card.isExpired()) {
            throw new CardOperationException("Cannot activate an expired card");
        }
        card.setStatus(Card.Status.ACTIVE);
        log.info("Card {} activated by admin", cardId);
    }

    @Override
    @Transactional
    public void requestBlock(String email, UUID cardId) {
        Card card = getOwnedCardEntity(email, cardId);
        card.setStatus(Card.Status.BLOCKED);
        log.info("Card {} blocked by owner request", cardId);
    }

    @Override
    @Transactional
    public void deleteCard(UUID cardId) {
        Card card = getActiveCardEntity(cardId);
        card.setDeleted(true);
        log.info("Card {} soft-deleted by admin", cardId);
    }

    @Override
    @Transactional
    public void transfer(String email, TransferRequest request) {
        if (request.fromCardId().equals(request.toCardId())) {
            throw new CardOperationException("Cannot transfer to the same card");
        }

        UUID ownerId = currentUser(email).getId();
        boolean fromFirst = request.fromCardId().compareTo(request.toCardId()) < 0;
        Card first = lockOwnedCard(ownerId, fromFirst ? request.fromCardId() : request.toCardId());
        Card second = lockOwnedCard(ownerId, fromFirst ? request.toCardId() : request.fromCardId());

        Card from = fromFirst ? first : second;
        Card to = fromFirst ? second : first;

        requireOperational(from, "Source");
        requireOperational(to, "Target");

        if (from.getBalance().compareTo(request.amount()) < 0) {
            throw new CardOperationException("Insufficient funds on the source card");
        }

        from.setBalance(from.getBalance().subtract(request.amount()));
        to.setBalance(to.getBalance().add(request.amount()));
        log.info("Transferred {} from card {} to card {}", request.amount(), from.getId(), to.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getBalance(String email, UUID cardId) {
        return getOwnedCardEntity(email, cardId).getBalance();
    }

    private String generateUniqueNumber() {
        for (int i = 0; i < maxGenerationAttempts; i++) {
            String number = CardNumberUtils.generate();
            if (!cardRepository.existsByCardNumberHash(CardNumberUtils.hash(number))) {
                return number;
            }
        }
        throw new CardOperationException("Failed to generate a unique card number, please retry");
    }

    private User currentUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
    }

    // 404 - for other user card
    private Card getOwnedCardEntity(String email, UUID cardId) {
        UUID ownerId = currentUser(email).getId();
        return cardRepository.findByIdAndOwnerIdAndDeletedFalse(cardId, ownerId)
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));
    }

    // 404 - for other user card
    private Card lockOwnedCard(UUID ownerId, UUID cardId) {
        return cardRepository.findByIdAndOwnerIdForUpdate(cardId, ownerId)
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));
    }

    private Card getActiveCardEntity(UUID cardId) {
        return cardRepository.findByIdAndDeletedFalse(cardId)
                .orElseThrow(() -> new CardNotFoundException("Card not found: " + cardId));
    }

    private void requireOperational(Card card, String role) {
        if (card.isExpired()) {
            throw new CardOperationException(role + " card has expired");
        }
        if (card.getStatus() != Card.Status.ACTIVE) {
            throw new CardOperationException(role + " card is not active");
        }
    }

    private String normalize(String search) {
        return (search == null || search.isBlank()) ? null : search.trim();
    }
}
