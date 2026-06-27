package com.example.bankcards.service.auth.impl;

import com.example.bankcards.dto.request.LoginRequest;
import com.example.bankcards.dto.request.RegisterRequest;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.InvalidCredentialsException;
import com.example.bankcards.exception.TokenException;
import com.example.bankcards.exception.UserAlreadyExistsException;
import com.example.bankcards.repository.UserRepository;
import com.example.bankcards.security.record.AuthTokens;
import com.example.bankcards.security.JwtService;
import com.example.bankcards.service.auth.interfaces.AuthService;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Override
    @Transactional
    public AuthTokens register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            log.warn("Registration attempt for existing email: {}", request.email());
            throw new UserAlreadyExistsException("User with email " + request.email() + " already exists");
        }

        User user = new User();
        user.setEmail(request.email());
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setActive(true);
        user.setRole(User.Role.USER);

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthTokens login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (AuthenticationException e) {
            log.warn("Failed login attempt for user: {}", request.email());
            throw new InvalidCredentialsException("Invalid email or password");
        }

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        user.setLastLogin(Instant.now());
        userRepository.save(user);
        log.info("User logged in successfully: {}", user.getEmail());

        return issueTokens(user);
    }

    @Override
    @Transactional
    public AuthTokens refreshToken(String refreshTokenStr) {
        JWTClaimsSet claims = jwtService.parseAndValidate(refreshTokenStr, JwtService.TOKEN_TYPE_REFRESH);

        UUID userId = UUID.fromString(getRequiredClaim(claims, "userId"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new TokenException("User not found"));

        if (!user.isActive()) {
            throw new TokenException("User account is deactivated");
        }

        int tokenVersion = getTokenVersion(claims);
        if (tokenVersion != user.getTokenVersion()) {
            log.warn("Refresh token reuse/mismatch detected for user {}: token={}, current={}. Revoking all sessions.",
                    user.getEmail(), tokenVersion, user.getTokenVersion());
            user.setTokenVersion(user.getTokenVersion() + 1);
            userRepository.save(user);
            throw new TokenException("Refresh token has been revoked");
        }

        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);

        log.info("Tokens refreshed (rotated) for user: {}", user.getEmail());
        return issueTokens(user);
    }

    @Override
    @Transactional
    public void logoutAll(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new InvalidCredentialsException("User not found"));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        log.info("All sessions revoked for user: {}", email);
    }

    private AuthTokens issueTokens(User user) {
        return new AuthTokens(
                jwtService.generateAccessToken(user),
                jwtService.generateRefreshToken(user),
                jwtService.getAccessTokenExpiration(),
                jwtService.getRefreshTokenExpiration()
        );
    }

    private String getRequiredClaim(JWTClaimsSet claims, String name) {
        try {
            String value = claims.getStringClaim(name);
            if (value == null) {
                throw new TokenException("Missing claim: " + name);
            }
            return value;
        } catch (java.text.ParseException e) {
            throw new TokenException("Invalid claim: " + name);
        }
    }

    private int getTokenVersion(JWTClaimsSet claims) {
        try {
            Object value = claims.getClaim("tokenVersion");
            if (value == null) {
                throw new TokenException("Missing claim: tokenVersion");
            }
            return ((Number) value).intValue();
        } catch (ClassCastException e) {
            throw new TokenException("Invalid claim: tokenVersion");
        }
    }
}
