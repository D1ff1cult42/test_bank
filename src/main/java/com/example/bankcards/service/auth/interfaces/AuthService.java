package com.example.bankcards.service.auth.interfaces;

import com.example.bankcards.dto.request.LoginRequest;
import com.example.bankcards.dto.request.RegisterRequest;
import com.example.bankcards.security.record.AuthTokens;

public interface AuthService {

    AuthTokens register(RegisterRequest request);

    AuthTokens login(LoginRequest request);

    AuthTokens refreshToken(String refreshTokenStr);

    void logoutAll(String email);
}
