# Anioł Stróż

HackYeah 2026. A device next to a senior's landline listens to the call, recognises the stages of a "fake police" scam and warns the senior and their family, showing the caller's literal words. People always make the decisions. See `CLAUDE.md` and `docs/`.

## Run

Requires JDK 21 and Node.js. The demo also needs Docker Desktop (running) and Google Chrome for the end-to-end test.

Configuration: copy `.env.example` to `.env` (never commit it). Secrets only come from environment variables. Without `ANTHROPIC_API_KEY` the dev profile and `make demo` use canned AI answers (mode MOCK).

### Development (no certificates needed)

```bash
make run-backend    # terminal 1: backend, dev profile, http://localhost:8080/api/status
make run-frontend   # terminal 2: three apps, senior :4201, listen :4202, family :4203 (proxy /api, /ws to :8080)
```

Open http://localhost:4201/senior, http://localhost:4202/listen and http://localhost:4203/family. `http://localhost` counts as a secure context, so the microphone works without HTTPS. After `git pull`, restart `make run-frontend`: `generate:api` rewrites `src/app/api` and running dev servers keep the old code.

### Tests

```bash
make test           # backend: unit, contract and ArchUnit tests (./mvnw verify)
cd frontend && npm test   # frontend unit tests (Vitest)
make e2e            # UC-01 end to end in Chrome against the dev servers above (both must be running)
```

### Demo on real devices (HTTPS)

```bash
make demo           # LAN: builds everything, starts backend + Caddy in Docker, runs the smoke test
make e2e-demo       # UC-01 end to end against the running demo
```

`make demo` prints one address, e.g. `http://192.168.1.50`. Open it on each phone or tablet in the same Wi-Fi: the start page downloads the certificate (once per device) and links to the senior, listen and family apps. On this laptop run `deploy/trust-ca.sh` once. Ctrl+C stops the demo.

### All commands

| Command | What it does |
|---|---|
| `make build` | Builds the backend jar without tests. |
| `make test` | Runs all backend tests (`./mvnw verify`). |
| `make run-backend` | Starts the backend with the `dev` profile on port 8080. |
| `make run-frontend` | Installs frontend packages and starts the three apps on ports 4201 (senior), 4202 (listen), 4203 (family). |
| `make e2e` | Use case UC-01 in Chrome against the dev servers: three apps at once, a normal call without an alert, a scam call whose alert reaches all three, the senior's and the family's decisions, the end of the call, failure states, contract of every event, no console errors. Needs `make run-backend` and `make run-frontend`. |
| `make demo` | Demo in the LAN over HTTPS: builds frontend and backend, starts backend and Caddy in Docker, saves Caddy's CA to `deploy/ca.crt`, runs the smoke test, prints the start page address. |
| `make demo-tunnel` | Same through a public Cloudflare quick tunnel (`https://….trycloudflare.com`): no certificates on the devices, but the address is public and changes on every start. |
| `make smoke` | Smoke test of the running demo: routes of the three apps, security headers, cache, TLS checked with the downloaded CA, API, WebSocket, start page. |
| `make e2e-demo` | Use case UC-01 in Chrome against the running demo at `https://localhost` (failure states are skipped: no `/api/dev/emit` in prod). |
| `deploy/trust-ca.sh` | Trusts the demo CA on this laptop (macOS asks for your password once). |

Details, the certificate on Android and iPhone, and troubleshooting: `deploy/README.md`.

## Disclosure list

| Name | Licence | Purpose |
|---|---|---|
| Spring Boot (web, websocket, validation, actuator, test) | Apache 2.0 | Backend framework |
| ArchUnit | Apache 2.0 | Architecture tests |
| Maven Wrapper | Apache 2.0 | Reproducible build |
| Spring JDBC (`JdbcClient`) and HikariCP | Apache 2.0 | Access to the local SQLite database |
| sqlite-jdbc (Xerial) | Apache 2.0 | SQLite driver; stores alerts and cited transcript excerpts of alerted calls only |
| Jackson YAML dataformat | Apache 2.0 | Reads the keyword dictionary and alert templates |
| networknt json-schema-validator | Apache 2.0 | Validates scenario files against `components/schemas` of `contracts/openapi.yaml` |
| OpenAPI Generator (maven plugin, `spring` generator) | Apache 2.0 | Build-time generation of the `/api/demo/*` interface and request models from `contracts/openapi.yaml` |
| openapi-request-validator-mockmvc (Atlassian) | Apache 2.0 | Tests: every MockMvc request and response is checked against `contracts/openapi.yaml` |
| Claude Sonnet 5.5 (`claude-sonnet-5-5`) via the Claude API | Anthropic terms | Stage detection with verbatim quotes (evidence only; risk, texts and decisions stay in code) |
| `com.anthropic:anthropic-java` 2.68.0 (with OkHttp) | MIT (OkHttp: Apache 2.0) | Official Java client for the Claude API |
| WireMock (`wiremock-standalone`) | Apache 2.0 | Tests: stands in for the Anthropic API, no real calls |
| Vosk (`com.alphacephei:vosk`) | Apache 2.0 (verify in its repository) | Local, offline Polish speech-to-text in the backend (task BE-08); audio never leaves the device |
| JNA (`net.java.dev.jna:jna`) | LGPL 2.1 or Apache 2.0 (dual, verify) | Native access used by Vosk |
| Vosk model `vosk-model-small-pl-0.22` | Apache 2.0 (per the Vosk models page, verify) | Polish model for the recogniser; downloaded separately, not part of the repository |
| Claude / Claude Code (Anthropic) | Anthropic terms | Concept, architecture notes and coding assistance (pre-event architecture document disclosed as such) |
| Angular, Angular CLI, Angular Material, CDK | MIT | Frontend framework and UI components |
| RxJS | Apache 2.0 | Router events in the frontend |
| Vitest, jsdom | MIT | Frontend unit tests |
| OpenAPI Generator CLI (`typescript-angular`) | Apache 2.0 | Generates frontend models and services from `contracts/openapi.yaml` |
| yaml | ISC | Reads the contract in `frontend/scripts/generate-api.mjs` |
| Ajv, ajv-formats | MIT | Validates every `/ws/events` message against the contract (FE-13) |
| Public Sans (`@fontsource/public-sans`) | OFL-1.1 | UI font from the visual system |
| Caddy | Apache 2.0 | Demo web server: HTTPS (local CA), static files, reverse proxy, security headers (`deploy/`) |
| cloudflared (Cloudflare Tunnel) | Apache 2.0 | Plan B for the demo: public HTTPS tunnel to Caddy (`deploy/`, profile `tunnel`) |
| Eclipse Temurin 21 JRE (Docker image) | GPLv2 with Classpath Exception | Runtime of the backend container |
