# Trading Bot — Hardened Build

## Critical Security Fixes Applied

| # | Issue | Fix |
|---|---|---|
| 1 | API keys stored as plaintext | AES-256-GCM via `EncryptionService` + `EncryptedStringConverter` |
| 2 | `/api/auth/update` was unauthenticated | Moved to `/api/users/me` (PUT), uses `@AuthenticationPrincipal` |
| 3 | JWT secret length not validated | Throws on startup if < 32 bytes |
| 4 | CORS open to `*` | Reads explicit origins from `CORS_ALLOWED_ORIGINS` |
| 5 | WebSocket unauthenticated | `JwtHandshakeInterceptor` validates JWT on connect |
| 6 | No idempotency on order placement | `client_order_id` UUID + DB unique index |
| 7 | Money math used `double` | All monetary fields are `BigDecimal` |
| 8 | No stop-loss / take-profit validation | Service rejects invalid configs |
| 9 | `HttpClient` had no timeouts | Connect + per-request timeouts |
| 10 | No retry / circuit breaker | Resilience4j on all Alpaca calls |
| 11 | Used raw `RuntimeException` | Custom exception hierarchy + `@RestControllerAdvice` |
| 13 | `User` JSON exposed password & API keys | `UserDto` projection + `@JsonIgnore` |
| 15 | Dead WebSocket handler | Now broadcasts `ORDER_PLACED` per-user |
| 18 | Hardcoded "top 10" in repo | `Pageable` parameters in controllers |
| 20 | `ddl-auto=update` in production | Replaced with Flyway migrations |
| 21 | Zero unit tests | Added `AlpacaServiceTest` + `EncryptionServiceTest` |

Plus: daily loss limit, kill-switch (`tradingEnabled` flag), structured logging via MDC.

## Setup

```bash
cp .env.example .env
# Fill in real values, generate secrets:
openssl rand -base64 64        # JWT_SECRET
openssl rand -base64 64        # ENCRYPTION_SECRET

# Run
./mvnw spring-boot:run

# Test
./mvnw test
```

## Endpoint Changes (Breaking)

| Before | After |
|---|---|
| `POST /api/auth/update` (unauthenticated!) | `PUT /api/users/me` (requires JWT) |
| `User` returned in admin list | `UserDto` (no secrets) |
| `GET /api/trade/orders` (all rows) | `GET /api/trade/orders?page=0&size=50` |
| `ws://host/ws/trades` | `ws://host/ws/trades?token=<JWT>` |

## Going to Production Checklist

- [ ] Use a secrets manager (Vault / AWS Secrets Manager) instead of `.env`.
- [ ] Run `ENCRYPTION_SECRET` rotation procedure if compromised (not covered here).
- [ ] Paper trade for at least 2 weeks before live capital.
- [ ] Set conservative `dailyLossLimit` per user.
- [ ] Monitor `actuator/health` and `circuitbreaker.alpaca` metrics.
- [ ] Review Alpaca rate limits (200 req/min) before high-frequency trading.
