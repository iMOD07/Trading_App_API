# Trading Bot — IBKR Edition

A production-grade Spring Boot trading bot connected to **Interactive Brokers** via the TWS API.

## Architecture

```
┌────────────────────┐
│   This Spring App  │
│   (your code)      │
└──────────┬─────────┘
           │ TCP Socket
           │ port 4002 (paper) / 4001 (live)
           ↓
┌────────────────────┐
│   IB Gateway       │  ← installed locally; logs into your IBKR account
│   (separate Java)  │
└──────────┬─────────┘
           │ HTTPS
           ↓
┌────────────────────┐
│   IBKR Servers     │
└────────────────────┘
```

**Critical:** This app does NOT talk to IBKR directly. It talks to **IB Gateway** (or TWS) running on the same machine, which holds your IBKR login session.

## Prerequisites

1. **IBKR Pro account** (Lite does not support full API).
2. **IB Gateway** installed: <https://www.interactivebrokers.com/en/trading/ibgateway-stable.php>
3. **TWS API JAR** downloaded: <https://interactivebrokers.github.io/>
4. **Java 17+** and **Maven 3.8+**.
5. **PostgreSQL 14+**.

## One-Time Setup

### Step 1: Install the TWS API JAR locally

The TWS API jar is NOT in Maven Central. You must install it manually:

```bash
# Download TwsApi.jar from https://interactivebrokers.github.io/
# (Java edition; current version 10.30.x as of 2026)

# Place it under ./lib/TwsApi.jar then run:
mvn install:install-file \
  -Dfile=lib/TwsApi.jar \
  -DgroupId=com.interactivebrokers \
  -DartifactId=tws-api \
  -Dversion=10.30.01 \
  -Dpackaging=jar
```

If your jar version differs, update `<tws-api.version>` in `pom.xml`.

### Step 2: Configure IB Gateway

1. Launch IB Gateway, log in with your IBKR username/password (use **Paper Trading** mode first).
2. **Configure → Settings → API → Settings:**
   - ✅ Enable ActiveX and Socket Clients
   - ✅ Read-Only API: **OFF** (you need to place orders)
   - **Socket port:** `4002` (paper) or `4001` (live)
   - **Trusted IPs:** add `127.0.0.1`
   - Set "Master API client ID" to a number you'll use in `IBKR_CLIENT_ID`
3. **Configure → Settings → API → Precautions:**
   - Disable all "Bypass" warnings if you want safety prompts in IB Gateway itself
4. **Configure → Settings → Lock and Exit:**
   - Set "Auto restart" time (IBKR forces a daily restart). Note this time — your app's reconnect logic will handle it.

### Step 3: Database & secrets

```bash
createdb trading
cp .env.example .env

# Generate secrets:
echo "JWT_SECRET=$(openssl rand -base64 64 | tr -d '\n=')"
# Add to .env
```

### Step 4: Run

```bash
./mvnw clean install
./mvnw test                           # validate without IB Gateway
./mvnw spring-boot:run                # start; will connect to IB Gateway on boot
```

If IB Gateway isn't running yet, the app starts anyway and retries with exponential backoff.

## Key API Endpoints

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/auth/register` | Sign up (no Alpaca keys needed) |
| `POST` | `/api/auth/login` | Get JWT |
| `GET`  | `/api/users/me` | Read your profile |
| `PUT`  | `/api/users/me` | Update profile (password, IBKR account ID) |
| `POST` | `/api/trade/order` | Place a bracket order |
| `POST` | `/api/trade/orders/{id}/cancel` | Cancel an open order |
| `GET`  | `/api/trade/account` | IBKR account summary (NetLiq, BuyingPower, …) |
| `GET`  | `/api/trade/orders?page=0&size=50` | Paginated history |
| `GET`  | `/api/trade/connection` | Check IB Gateway connectivity |
| `POST` | `/api/admin/users/{id}/trading/{enabled}` | Kill switch per user |
| `WS`   | `/ws/trades?token=<JWT>` | Live order status / fills |

## How Bracket Orders Work in IBKR

Unlike Alpaca's single-request bracket, IBKR requires **3 linked orders**:

```
Parent (BUY)              ← STP LMT entry
   │
   ├── Child 1 (SELL)     ← LMT @ takeProfit
   │       transmit=false
   │
   └── Child 2 (SELL)     ← STP @ stopLoss
           transmit=true  ← THIS triggers all 3 to be sent at once
```

If parent fills, only one child can fill — the other is auto-cancelled (OCA).

## Daily Restart of IB Gateway

IBKR forces IB Gateway to restart every day (default 11:45 PM ET). The app handles this:

1. `connectionClosed()` callback fires.
2. All in-flight `CompletableFuture`s are completed exceptionally.
3. `IbkrConnectionManager` schedules reconnect with exponential backoff.
4. Once `nextValidId` is received again, the app is ready.

**Avoid placing orders during the restart window.**

## Troubleshooting

| Symptom | Fix |
|---|---|
| `IB Gateway not connected` on every request | IB Gateway crashed / not running. Restart it. |
| Error 502 "Couldn't connect to TWS" | Wrong port. Live=4001/7496, Paper=4002/7497. |
| Error 326 "Client ID already in use" | Another bot is using the same `IBKR_CLIENT_ID`. Change it. |
| `nextValidId not received yet` | App started before handshake completed. Wait 1-2 sec. |
| All orders rejected with error 201 | Account doesn't have permission for that product/exchange. |
| `Pacing violation` in logs | You exceeded ~50 messages/sec. Throttle your strategy. |

## Production Checklist

- [ ] Use **Paper Trading** for at least 2 weeks before going live.
- [ ] Subscribe to required market data: <https://www.interactivebrokers.com/en/pricing/market-data-pricing.php>
- [ ] Set conservative `dailyLossLimit` per user.
- [ ] Run IB Gateway as a service that auto-starts on boot (e.g., `systemd` on Linux, `Task Scheduler` on Windows).
- [ ] Place IB Gateway on the same machine/VPC as the Spring app to minimize socket latency.
- [ ] Monitor `/actuator/health` and `/api/trade/connection` from your alerting system.
- [ ] Test the daily-restart scenario manually before relying on it in production.
