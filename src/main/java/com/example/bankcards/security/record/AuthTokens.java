package com.example.bankcards.security.record;

import java.time.Duration;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        Duration accessTtl,
        Duration refreshTtl
) {}
