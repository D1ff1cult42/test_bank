package com.example.bankcards.service.card;

import com.example.bankcards.dto.request.TransferRequest;
import com.example.bankcards.entity.Card;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.CardNotFoundException;
import com.example.bankcards.exception.CardOperationException;
import com.example.bankcards.repository.CardRepository;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.service.card.impl.CardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CardServiceImplTest {

    private static final String EMAIL = "user@example.com";

    @Mock
    private CardRepository cardRepository;
    @Mock
    private UserRepository userRepository;
    @InjectMocks
    private CardServiceImpl cardService;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setEmail(EMAIL);
        owner.setUsername("user");
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(owner));
    }

    private Card card(UUID id, BigDecimal balance, Card.Status status, LocalDate expiry) {
        Card c = new Card();
        c.setId(id);
        c.setOwner(owner);
        c.setBalance(balance);
        c.setStatus(status);
        c.setExpiryDate(expiry);
        c.setMaskedNumber("**** **** **** 1234");
        return c;
    }

    @Test
    void transfer_movesMoneyBetweenOwnCards() {
        UUID fromId = new UUID(0, 1);
        UUID toId = new UUID(0, 2);
        Card from = card(fromId, new BigDecimal("100.00"), Card.Status.ACTIVE, LocalDate.now().plusYears(1));
        Card to = card(toId, new BigDecimal("10.00"), Card.Status.ACTIVE, LocalDate.now().plusYears(1));

        when(cardRepository.findByIdAndOwnerIdForUpdate(fromId, owner.getId())).thenReturn(Optional.of(from));
        when(cardRepository.findByIdAndOwnerIdForUpdate(toId, owner.getId())).thenReturn(Optional.of(to));

        cardService.transfer(EMAIL, new TransferRequest(fromId, toId, new BigDecimal("30.00")));

        assertThat(from.getBalance()).isEqualByComparingTo("70.00");
        assertThat(to.getBalance()).isEqualByComparingTo("40.00");
    }

    @Test
    void transfer_failsOnInsufficientFunds() {
        UUID fromId = new UUID(0, 1);
        UUID toId = new UUID(0, 2);
        Card from = card(fromId, new BigDecimal("10.00"), Card.Status.ACTIVE, LocalDate.now().plusYears(1));
        Card to = card(toId, new BigDecimal("0.00"), Card.Status.ACTIVE, LocalDate.now().plusYears(1));

        when(cardRepository.findByIdAndOwnerIdForUpdate(fromId, owner.getId())).thenReturn(Optional.of(from));
        when(cardRepository.findByIdAndOwnerIdForUpdate(toId, owner.getId())).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> cardService.transfer(EMAIL, new TransferRequest(fromId, toId, new BigDecimal("50.00"))))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("Insufficient funds");
        assertThat(from.getBalance()).isEqualByComparingTo("10.00");
        assertThat(to.getBalance()).isEqualByComparingTo("0.00");
    }

    @Test
    void transfer_failsWhenSourceBlocked() {
        UUID fromId = new UUID(0, 1);
        UUID toId = new UUID(0, 2);
        Card from = card(fromId, new BigDecimal("100.00"), Card.Status.BLOCKED, LocalDate.now().plusYears(1));
        Card to = card(toId, new BigDecimal("0.00"), Card.Status.ACTIVE, LocalDate.now().plusYears(1));

        when(cardRepository.findByIdAndOwnerIdForUpdate(fromId, owner.getId())).thenReturn(Optional.of(from));
        when(cardRepository.findByIdAndOwnerIdForUpdate(toId, owner.getId())).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> cardService.transfer(EMAIL, new TransferRequest(fromId, toId, new BigDecimal("10.00"))))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void transfer_failsWhenTargetExpired() {
        UUID fromId = new UUID(0, 1);
        UUID toId = new UUID(0, 2);
        Card from = card(fromId, new BigDecimal("100.00"), Card.Status.ACTIVE, LocalDate.now().plusYears(1));
        Card to = card(toId, new BigDecimal("0.00"), Card.Status.ACTIVE, LocalDate.now().minusDays(1));

        when(cardRepository.findByIdAndOwnerIdForUpdate(fromId, owner.getId())).thenReturn(Optional.of(from));
        when(cardRepository.findByIdAndOwnerIdForUpdate(toId, owner.getId())).thenReturn(Optional.of(to));

        assertThatThrownBy(() -> cardService.transfer(EMAIL, new TransferRequest(fromId, toId, new BigDecimal("10.00"))))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void transfer_failsOnSameCard() {
        UUID id = new UUID(0, 1);
        assertThatThrownBy(() -> cardService.transfer(EMAIL, new TransferRequest(id, id, new BigDecimal("10.00"))))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("same card");
    }

    @Test
    void transfer_failsWhenCardNotOwned() {
        UUID fromId = new UUID(0, 1);
        UUID toId = new UUID(0, 2);
        // Меньший id блокируется первым — вернём пусто для него.
        when(cardRepository.findByIdAndOwnerIdForUpdate(fromId, owner.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.transfer(EMAIL, new TransferRequest(fromId, toId, new BigDecimal("10.00"))))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void getOwnedCard_throwsWhenNotOwned() {
        UUID cardId = UUID.randomUUID();
        when(cardRepository.findByIdAndOwnerIdAndDeletedFalse(cardId, owner.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cardService.getOwnedCard(EMAIL, cardId))
                .isInstanceOf(CardNotFoundException.class);
    }

    @Test
    void requestBlock_setsBlockedStatus() {
        UUID cardId = UUID.randomUUID();
        Card c = card(cardId, BigDecimal.ZERO, Card.Status.ACTIVE, LocalDate.now().plusYears(1));
        when(cardRepository.findByIdAndOwnerIdAndDeletedFalse(cardId, owner.getId())).thenReturn(Optional.of(c));

        cardService.requestBlock(EMAIL, cardId);

        assertThat(c.getStatus()).isEqualTo(Card.Status.BLOCKED);
    }

    @Test
    void activateCard_rejectsExpiredCard() {
        UUID cardId = UUID.randomUUID();
        Card c = card(cardId, BigDecimal.ZERO, Card.Status.BLOCKED, LocalDate.now().minusDays(1));
        when(cardRepository.findByIdAndDeletedFalse(cardId)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> cardService.activateCard(cardId))
                .isInstanceOf(CardOperationException.class)
                .hasMessageContaining("expired");
    }
}
