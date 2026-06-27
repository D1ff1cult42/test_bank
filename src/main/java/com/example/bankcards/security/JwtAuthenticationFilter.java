package com.example.bankcards.security;

import com.example.bankcards.exception.TokenException;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CookieUtils cookieUtils;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        cookieUtils.readCookie(request, CookieUtils.ACCESS_TOKEN_COOKIE).ifPresent(token -> {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                return;
            }
            try {
                JWTClaimsSet claims = jwtService.parseAndValidate(token, JwtService.TOKEN_TYPE_ACCESS);
                String email = claims.getSubject();
                String role = claims.getStringClaim("role");

                var authentication = new UsernamePasswordAuthenticationToken(
                        email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (TokenException e) {
                log.debug("Access token rejected: {}", e.getMessage());
            } catch (Exception e) {
                log.warn("Failed to process access token: {}", e.getMessage());
            }
        });

        filterChain.doFilter(request, response);
    }
}
