package com.example.bankcards.security;

import com.example.bankcards.config.RsaKeyProvider;
import com.example.bankcards.entity.User;
import com.example.bankcards.exception.TokenException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class JwtService {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    @Getter
    @Value("${auth.access-token-expiration}")
    private Duration accessTokenExpiration;

    @Getter
    @Value("${auth.refresh-token-expiration}")
    private Duration refreshTokenExpiration;

    @Value("${auth.issuer}")
    private String issuer;

    private final RsaKeyProvider rsaKeyProvider;
    private RSASSASigner signer;
    private JWSVerifier verifier;

    @PostConstruct
    public void init() {
        signer = new RSASSASigner(rsaKeyProvider.getPrivateKey());
        verifier = new RSASSAVerifier(rsaKeyProvider.getPublicKey());
    }

    public String generateAccessToken(User user) {
        JWTClaimsSet claims = baseClaims(user, accessTokenExpiration)
                .claim("type", TOKEN_TYPE_ACCESS)
                .claim("role", user.getRole().name())
                .claim("userId", user.getId().toString())
                .build();
        return sign(claims);
    }

    public String generateRefreshToken(User user) {
        JWTClaimsSet claims = baseClaims(user, refreshTokenExpiration)
                .claim("type", TOKEN_TYPE_REFRESH)
                .claim("userId", user.getId().toString())
                .claim("tokenVersion", user.getTokenVersion())
                .build();
        return sign(claims);
    }

    public JWTClaimsSet parseAndValidate(String token, String expectedType) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                throw new TokenException("Invalid token signature");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            Date expiration = claims.getExpirationTime();
            if (expiration == null || expiration.before(new Date())) {
                throw new TokenException("Token has expired");
            }

            if (!expectedType.equals(claims.getStringClaim("type"))) {
                throw new TokenException("Unexpected token type");
            }

            return claims;
        } catch (ParseException | JOSEException e) {
            throw new TokenException("Invalid token: " + e.getMessage());
        }
    }

    private JWTClaimsSet.Builder baseClaims(User user, Duration ttl) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttl.toMillis());
        return new JWTClaimsSet.Builder()
                .subject(user.getEmail())
                .issuer(issuer)
                .issueTime(now)
                .expirationTime(exp)
                .jwtID(UUID.randomUUID().toString());
    }

    private String sign(JWTClaimsSet claims) {
        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("auth-key").build(),
                claims);
        try {
            signedJWT.sign(signer);
            return signedJWT.serialize();
        } catch (JOSEException e) {
            log.error("Failed to sign JWT: {}", e.getMessage());
            throw new TokenException("Failed to sign JWT token");
        }
    }
}
