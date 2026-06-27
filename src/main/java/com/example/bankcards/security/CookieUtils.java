package com.example.bankcards.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CookieUtils {

    public static final String ACCESS_TOKEN_COOKIE = "access_token";
    public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

    private static final String ACCESS_PATH = "/";
    private static final String REFRESH_PATH = "/api/auth";

    @Value("${auth.cookie.secure:true}")
    private boolean secure;

    @Value("${auth.cookie.same-site:Strict}")
    private String sameSite;

    public void writeAccessToken(HttpServletResponse response, String token, Duration ttl) {
        addCookie(response, ACCESS_TOKEN_COOKIE, token, ACCESS_PATH, ttl);
    }

    public void writeRefreshToken(HttpServletResponse response, String token, Duration ttl) {
        addCookie(response, REFRESH_TOKEN_COOKIE, token, REFRESH_PATH, ttl);
    }

    public void clearTokens(HttpServletResponse response) {
        addCookie(response, ACCESS_TOKEN_COOKIE, "", ACCESS_PATH, Duration.ZERO);
        addCookie(response, REFRESH_TOKEN_COOKIE, "", REFRESH_PATH, Duration.ZERO);
    }

    public Optional<String> readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    private void addCookie(HttpServletResponse response, String name, String value, String path, Duration ttl) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(ttl)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }
}
