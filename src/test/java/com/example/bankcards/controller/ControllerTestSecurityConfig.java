package com.example.bankcards.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Облегчённая security-конфигурация для {@code @WebMvcTest}: отключает CSRF и делает контекст stateless,
 * оставляя включённой method-security ({@link EnableMethodSecurity}), чтобы проверять {@code @PreAuthorize}.
 * Аутентификация в тестах задаётся через {@code @WithMockUser}, поэтому JWT-фильтр здесь не нужен.
 */
@TestConfiguration
@EnableMethodSecurity
public class ControllerTestSecurityConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
