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

`make demo` prints one address, e.g. `http://192.168.1.50`. Open it on each phone or tablet in the same Wi-Fi: the start page downloads the certificate (once per device) and links to the senior, listen and family apps. On this laptop run `deploy/trust-ca.sh` once (while the demo runs). Ctrl+C stops the demo and deletes its generated files (`deploy/site/`, `deploy/ca.crt`).

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
| `make clean-demo` | Stops the demo containers and deletes the generated `deploy/site/` and `deploy/ca.crt`. `make demo` does this itself on exit (Ctrl+C); use it after a hard kill. Volumes stay, so device certificates keep working. |
| `make e2e-demo` | Use case UC-01 in Chrome against the running demo at `https://localhost` (failure states are skipped: no `/api/dev/emit` in prod). |
| `deploy/trust-ca.sh` | Trusts the demo CA on this laptop (macOS asks for your password once). |

Details, the certificate on Android and iPhone, and troubleshooting: `deploy/README.md`.

## LIVE mode: local speech recognition (Vosk)

LIVE mode transcribes the call on the machine itself with Vosk. It needs no key and no internet, and the audio never leaves the device (it is not stored or logged either). The model (about 50 MB, not in the repository) is downloaded once:

```bash
make download-vosk-model          # or: scripts/download-vosk-model.sh   (Windows: scripts\download-vosk-model.ps1)
make run-backend                  # picks up backend/models/vosk-model-small-pl-0.22
```

The script is idempotent and unpacks into `backend/models/` (git-ignored). Another location: set `APP_STT_VOSK_MODEL_PATH` to the model directory. `app.stt.provider=fake` (env `APP_STT_PROVIDER`) switches to the test double that recognises nothing.

Who finds the warning signs: with `ANTHROPIC_API_KEY` set, Claude does (evidence only: stage and quote; the risk level is still computed by the code). The built-in keyword dictionary is only a safety net: `APP_RISK_KEYWORD_BASELINE=fallback` (default) uses it when the model is not available (no key, so MOCK, or its last call failed), `always` uses it next to the model, `off` never. The words that the family adds under "Konfiguracja konta seniora" on the family panel are always matched on the backend (never sent to Claude) and give at least a MEDIUM alert.

A call opens when speech is heard on the listening device and ends after 10 s without speech; the device keeps listening for the next one. Tunable with `APP_STT_CALL_SILENCE_MS` (10000), `APP_STT_CALL_SPEECH_RMS` (300, of 32768) and `APP_STT_CALL_SPEECH_FRAMES` (3 frames of 100 ms in a row).

The backend starts without the model; SCRIPTED and MOCK need none. A LIVE call without it is refused: `system.status` `stt` = `down` ("Brak modelu rozpoznawania mowy") and the `/ws/audio` session is closed with code 1011; no call is created.

The senior device connects to `/ws/audio` and sends JSON text frames `{"type":"start"|"stop"|"pause"|"resume"}` and binary frames of PCM (16 kHz, mono, 16-bit little endian, 3200 bytes = 100 ms). Close codes: 1008 no consent or a call is already active, 1009 message over 64 KB, 1011 recognition cannot start. The messages are the schema `AudioControl` in `contracts/openapi.yaml` (`x-websockets`); an unknown command or field closes the session with 1008.

The audio path never fails silently: if no sound arrives for 10 s (no frames, or only flat ones, as from a muted microphone) `system.status` `audio` goes `down`, and it returns to `ok` when sound comes back (`app.stt.silence.timeout-ms`, `app.stt.silence.check-interval-ms`); a connection that drops without `stop` ends the call and also sets `audio` to `down`. A pause is not silence.

### Trying it without a microphone: `SendWavTool`

Plays a WAV file (PCM, 16 kHz, mono, 16-bit; other formats are rejected with a message) into `/ws/audio` as `start`, frames of 3200 bytes every 100 ms, then `stop`. JDK only. The transcript shows up on `/ws/events` and in the UIs.

```bash
cd backend && ./mvnw test-compile
java -cp target/test-classes pl.aniolstroz.tools.SendWavTool call.wav [ws://localhost:8080/ws/audio] [speed]
# speed: 1 = real time (default), 4 = four times faster
```

Convert any recording first, for example `ffmpeg -i in.mp3 -ar 16000 -ac 1 -c:a pcm_s16le call.wav`.

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
| Vosk (`com.alphacephei:vosk` 0.3.45) | Apache 2.0 (confirmed in vosk-api `COPYING` and the POM) | Local, offline Polish speech-to-text in the backend (AUD-03); audio never leaves the device. The jar bundles the native libraries for Windows, Linux and macOS |
| JNA (`net.java.dev.jna:jna` 5.17.0) | LGPL 2.1 or later, or Apache 2.0 (dual; `SPDX-License-Identifier: Apache-2.0 OR LGPL-2.1-or-later`, confirmed in the JNA repository and POM; we use it under Apache 2.0) | Native access used by Vosk |
| Vosk model `vosk-model-small-pl-0.22` | Apache 2.0 (confirmed on the Vosk models page) | Polish model for the recogniser (WER 11.6 to 18.4 on its test sets); downloaded by `scripts/download-vosk-model.*`, not part of the repository |
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
