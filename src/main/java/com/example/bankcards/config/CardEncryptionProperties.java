package com.example.bankcards.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CardEncryptionProperties {

    @Getter
    private static volatile String key;

    @Value("${card.encryption.key:}")
    private String configuredKey;

    @PostConstruct
    public void init() {
        key = configuredKey;
        if (key == null || key.isBlank()) {
            log.warn("card.encryption.key is not set — card numbers cannot be encrypted/decrypted.");
        }
    }

}
