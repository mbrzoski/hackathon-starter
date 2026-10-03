# Anioł Stróż

HackYeah 2026. A device next to a senior's landline listens to the call, recognises the stages of a "fake police" scam and warns the senior and their family, showing the caller's literal words. People always make the decisions. See `CLAUDE.md` and `docs/`.

## Run

Requires JDK 21.

```bash
make test           # cd backend && ./mvnw verify
make run-backend    # dev profile, http://localhost:8080/api/status
make run-frontend   # three apps: senior :4201, listen :4202, family :4203 (proxy /api, /ws to :8080)
```

Configuration: copy `.env.example` to `.env` (never commit it). Secrets only come from environment variables.

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
