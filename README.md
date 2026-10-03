# Anioł Stróż

HackYeah 2026. A device next to a senior's landline listens to the call, recognises the stages of a "fake police" scam and warns the senior and their family, showing the caller's literal words. People always make the decisions. See `CLAUDE.md` and `docs/`.

## Run

Requires JDK 21.

```bash
make test           # cd backend && ./mvnw verify
make run-backend    # dev profile, http://localhost:8080/api/status
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
| Claude / Claude Code (Anthropic) | Anthropic terms | Concept, architecture notes and coding assistance (pre-event architecture document disclosed as such) |
