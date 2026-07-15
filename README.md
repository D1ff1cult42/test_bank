# Система управления банковскими картами

Backend-приложение на Spring Boot для управления банковскими картами: аутентификация по JWT
(в HttpOnly cookie), управление пользователями (ADMIN), выпуск/блокировка/удаление карт,
просмотр своих карт с поиском и пагинацией, переводы между своими картами.

## Технологии

Java 17, Spring Boot 3.3, Spring Security, Spring Data JPA, PostgreSQL, Liquibase, Docker,
JWT (Nimbus JOSE, RS256), Swagger/OpenAPI (springdoc), observability — Grafana LGTM+P
(Loki, Tempo, Prometheus, Alloy, Pyroscope).

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

Поднимется PostgreSQL, две реплики приложения за **nginx**-балансировщиком
(`http://localhost:8080`), а также полный стек наблюдаемости. Liquibase применит
миграции автоматически при старте.

## Балансировка нагрузки (nginx)

Перед приложением стоит **nginx** (единственный сервис, публикующий `8080` наружу),
балансирует между двумя репликами `app1`/`app2` (`least_conn`, `max_fails=3
fail_timeout=10s`). JWT-аутентификация полностью stateless (токены в HttpOnly cookie,
без server-side сессий) — sticky sessions не нужны, запрос от одного клиента спокойно
обслуживается разными репликами.

- конфиг: `nginx/nginx.conf`
- какая реплика ответила — видно в заголовке ответа `X-Upstream-Addr` и в access-логе
  nginx (`upstream=...`)
- обе реплики отдельно видны в Prometheus (`instance="app1:8080"` / `"app2:8080"`),
  в Loki (`container="bankcards-app1"` / `"bankcards-app2"`) и в Tempo
  (`service.instance.id="app1"` / `"app2"`, resource-атрибут из `$HOSTNAME`,
  см. `management.opentelemetry.resource-attributes` в `application.yml`)
- в Pyroscope обе реплики пишут профили под общим `application="bankcards"`
  (нормально для горизонтального масштабирования одного сервиса)

Проверить балансировку:
```bash
for i in $(seq 1 10); do curl -sI http://localhost:8080/actuator/health | grep -i x-upstream-addr; done
```

## Observability (Grafana LGTM stack)

Метрики, логи и трейсы собираются единым агентом **Grafana Alloy** и визуализируются
в **Grafana**:

- приложение отдаёт метрики Prometheus на `/actuator/prometheus` и трейсы по OTLP —
  Alloy их скрейпит/принимает;
- Alloy читает stdout всех контейнеров compose-проекта (через Docker socket) и
  пушит логи в **Loki**;
- Alloy скрейпит `/actuator/prometheus` и делает `remote_write` в **Prometheus**;
- Alloy принимает OTLP-трейсы от приложения (`OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`)
  и экспортирует их в **Tempo**;
- приложение профилируется агентом **Pyroscope Java** (`-javaagent:/app/pyroscope.jar`,
  подключён в Dockerfile), профили пушатся напрямую в **Pyroscope** сервер;
- в Grafana датасорсы (Prometheus/Loki/Tempo/Pyroscope) прописаны через provisioning
  (`observability/grafana/provisioning`) с настроенной корреляцией
  log → trace → metric → profile (exemplars, derived fields, service graph,
  traces-to-profiles).

| Сервис | URL |
|---|---|
| Grafana | http://localhost:3000 (анонимный доступ, роль Admin) |
| Prometheus | http://localhost:9090 |
| Loki | http://localhost:3100 |
| Tempo | http://localhost:3200 |
| Pyroscope | http://localhost:4040 |
| Alloy UI | http://localhost:12345 |

Конфиги стека — в `observability/{prometheus,loki,tempo,alloy,grafana}`.

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
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | адрес приёма OTLP-трейсов (Alloy) | `http://localhost:4318/v1/traces` |
| `PYROSCOPE_SERVER_ADDRESS` | адрес Pyroscope-сервера для пуша профилей | `http://pyroscope:4040` |

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
