package com.example.bankcards.scheduler;

import com.example.bankcards.entity.Card;
import com.example.bankcards.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class CardExpiryScheduler {

    private final CardRepository cardRepository;

    @Scheduled(cron = "${card.expiry.cron:0 5 0 * * *}")
    @Transactional
    public void expireCards() {
        int updated = cardRepository.markExpired(LocalDate.now(), Card.Status.ACTIVE, Card.Status.EXPIRED);
        if (updated > 0) {
            log.info("Marked {} card(s) as EXPIRED", updated);
        }
    }
}
