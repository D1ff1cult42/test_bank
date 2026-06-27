# Система управления банковскими картами

Backend-приложение на Spring Boot для управления банковскими картами: аутентификация по JWT
(в HttpOnly cookie), управление пользователями (ADMIN), выпуск/блокировка/удаление карт,
просмотр своих карт с поиском и пагинацией, переводы между своими картами.

## Технологии

Java 17, Spring Boot 3.3, Spring Security, Spring Data JPA, PostgreSQL, Liquibase, Docker,
JWT (Nimbus JOSE, RS256), Swagger/OpenAPI (springdoc).

## Архитектура

```
controller   — REST-эндпоинты (Auth, Card, User)
service      — бизнес-логика (auth / card / user, разделение interface + impl)
repository   — Spring Data JPA
entity       — User, Card
security     — JWT (RS256), фильтр, cookie, шифрование номера карты (AES-256/GCM)
scheduler    — перевод просроченных карт в статус EXPIRED
exception    — доменные исключения + глобальный обработчик
dto          — request / response (records)
```

### Безопасность
- **Аутентификация**: JWT RS256, access + refresh токены в HttpOnly/Secure/SameSite cookie.
- **Ротация refresh-токена** с обнаружением повторного использования (через `tokenVersion`).
- **Роли**: `USER`, `ADMIN` (`@EnableMethodSecurity` + проверки маршрутов).
- **Шифрование номера карты**: AES-256/GCM (`CardNumberConverter`); в БД — шифротекст,
  для поиска/уникальности — SHA-256 хэш, для отображения — маска `**** **** **** 1234`.
- **CSRF** (double-submit cookie) и **CORS** настраиваются через конфиг.

## Запуск через Docker Compose

```bash
docker compose up --build
```

Поднимется PostgreSQL и приложение (`http://localhost:8080`). Liquibase применит миграции
автоматически при старте.

## Локальный запуск

1. Поднять PostgreSQL (например, только БД из compose):
   ```bash
   docker compose up -d db
   ```
2. Запустить приложение:
   ```bash
   ./mvnw spring-boot:run
   ```

## Конфигурация (переменные окружения)

| Переменная | Назначение | По умолчанию |
|---|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL PostgreSQL | `jdbc:postgresql://localhost:5432/bankcards` |
| `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | креды БД | `bankcards` / `bankcards` |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY` | RSA-ключи (PEM) для JWT | генерируются эфемерно (dev) |
| `CARD_ENCRYPTION_KEY` | AES-256 ключ (Base64, 32 байта) для шифрования номеров | небезопасный dev-ключ |
| `AUTH_COOKIE_SECURE` | флаг Secure для cookie | `false` (dev) |
| `AUTH_CORS_ALLOWED_ORIGINS` | разрешённые origin (через запятую) | `http://localhost:3000,http://localhost:8080` |
| `AUTH_CSRF_ENABLED` | включение CSRF-защиты | `true` |
| `card.expiry.cron` | расписание шедулера просрочки | `0 5 0 * * *` (ежедневно 00:05) |

> Для production обязательно задать `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY`, `CARD_ENCRYPTION_KEY`
> и `AUTH_COOKIE_SECURE=true`.

## Документация API

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI (статическая спецификация): [`docs/openapi.yaml`](docs/openapi.yaml)

## Основные эндпоинты

### Auth (`/api/auth`)
| Метод | Путь | Описание |
|---|---|---|
| POST | `/register` | регистрация (роль USER) |
| POST | `/login` | вход |
| POST | `/refresh` | обновление токенов (ротация) |
| POST | `/logout` | выход |
| POST | `/logout-all` | отзыв всех сессий |

### Карты (`/api/cards`)
| Метод | Путь | Доступ | Описание |
|---|---|---|---|
| POST | `/` | ADMIN | выпуск карты |
| GET | `/` | ADMIN | все карты (поиск + пагинация) |
| GET | `/my` | USER | свои карты (поиск + пагинация) |
| GET | `/{id}` | владелец | карта |
| GET | `/{id}/balance` | владелец | баланс |
| POST | `/{id}/block-request` | владелец | запрос блокировки |
| POST | `/transfer` | USER | перевод между своими картами |
| POST | `/{id}/block` | ADMIN | блокировка |
| POST | `/{id}/activate` | ADMIN | активация |
| DELETE | `/{id}` | ADMIN | удаление (soft) |

### Пользователи (`/api/users`, только ADMIN)
| Метод | Путь | Описание |
|---|---|---|
| POST | `/` | создать пользователя с ролью |
| GET | `/` | список (поиск + пагинация) |
| GET | `/{id}` | пользователь |
| PATCH | `/{id}/role` | смена роли |
| POST | `/{id}/block` | блокировка |
| POST | `/{id}/activate` | активация |
| DELETE | `/{id}` | удаление (каскадно с картами) |

## Тесты

```bash
./mvnw test
```

Покрыта ключевая бизнес-логика карт: переводы (успех, недостаток средств, та же карта),
изоляция владельца (доступ к чужой карте → 404), проверка статусов (заблокирована/просрочена).
