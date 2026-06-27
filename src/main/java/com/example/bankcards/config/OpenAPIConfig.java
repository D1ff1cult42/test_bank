package com.example.bankcards.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenAPIConfig {

    @Bean
    public OpenAPI openAPI(){
        return new OpenAPI()
                .info(new Info()
                        .title("API системы управления банковскими картами")
                        .version("1.0")
                        .description("""
                               REST API для управления банковскими картами: выпуск, блокировка, \
                               просмотр и переводы между своими картами.

                               Аутентификация — JWT (RS256). Access- и refresh-токены передаются \
                               в HttpOnly cookie, поэтому в Swagger UI отдельная авторизация не требуется: \
                               выполните POST /api/auth/login, после чего cookie проставятся автоматически \
                               и защищённые запросы будут проходить.

                               Роли: USER — работа со своими картами; ADMIN — управление картами и пользователями.""")
                        .contact(new Contact()
                                .name("D1ff1cult42")
                                .email("orbitovkek@gmail.com")));
    }
}