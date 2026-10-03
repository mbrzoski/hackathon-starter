# Generic AI Hackathon Starter

Neutral technical starter for a 4-person team: Java 21 · Spring Boot 3.5 · Maven · Angular 19 · PostgreSQL · Docker Compose · Claude API.
It contains **infrastructure only** - no task-specific entities, workflows, datasets or business logic.

> **Before using it in a competition:** read that competition's own rules. The general HackYeah 2026 rules say nothing about AI tools,
> generated code, external APIs or pre-written code; competition-specific rules are published at the event start. If anything is unclear,
> ask the organizers instead of assuming. See section 10.

## 1. Repository tree

```
.
├── docker-compose.yml          # db + backend + frontend
├── .env.example                # copy to .env (never commit .env)
├── backend/
│   ├── pom.xml  Dockerfile
│   └── src/
│       ├── main/java/com/hackathonstarter/
│       │   ├── HackathonStarterApplication.java
│       │   ├── common/        ApiError, GlobalExceptionHandler      (uniform error body)
│       │   ├── config/        AppProperties, WebConfig (CORS), RequestIdFilter
│       │   ├── health/        HealthController                       (GET /api/health)
│       │   ├── web/           LlmController                          (generic demo endpoints)
│       │   └── llm/
│       │       ├── LlmClient               <- the interface to mock
│       │       ├── AnthropicClaudeClient   <- real adapter (Messages API)
│       │       ├── MockLlmClient           <- offline stub (LLM_PROVIDER=mock)
│       │       ├── LoggingLlmClient, LlmCallLog(+Repository)   latency/token audit in PostgreSQL
│       │       ├── model/       provider-neutral request/response records
│       │       ├── structured/  StructuredOutputService (schema-validated JSON, auto-retry)
│       │       └── tools/       ToolHandler, ToolRegistry, ToolCallingService, CurrentTimeTool (example)
│       ├── main/resources/  application.yml, application-mock.yml, db/migration/V1__create_llm_call_log.sql
│       └── test/            unit tests + PostgresIntegrationTest (Testcontainers)
└── frontend/
    ├── package.json  angular.json  proxy.conf.json  Dockerfile  nginx.conf
    ├── public/mock/*.json      <- mock API responses (same shape as the backend)
    └── src/
        ├── environments/       environment.ts (prod) · .development.ts · .mock.ts
        └── app/
            ├── core/           api.service.ts, models.ts, resource.ts (loading/error/data signals)
            ├── shared/         icon, mode-badge, connection-status, risk-level-chip, loading, error-banner, empty-state, card
            └── pages/          index, listen (Nasłuch), senior, family, not-found
```

## 2. Run instructions

Requirements: JDK 21, Maven (or `./mvnw`), Node 20+/22, Docker.

**Everything in containers (demo mode)**
```bash
cp .env.example .env          # set ANTHROPIC_API_KEY, or LLM_PROVIDER=mock to run offline
docker compose up --build
# UI: http://localhost:8081   API: http://localhost:8080/api/health   Swagger: http://localhost:8080/swagger-ui.html
```

**Local development (hot reload)**
```bash
docker compose up -d db                                  # PostgreSQL only
cd backend && SPRING_PROFILES_ACTIVE=mock mvn spring-boot:run     # or export ANTHROPIC_API_KEY and drop the profile
cd frontend && npm install && npm start                  # http://localhost:4200, proxies /api -> :8080
```

**Frontend without any backend** (for the Angular teammate): `cd frontend && npm run start:mock` - responses come from `public/mock/*.json`.

## 3. Environment variables

| Variable | Default | Purpose |
|---|---|---|
| `ANTHROPIC_API_KEY` | _(empty)_ | Claude API key. Never committed; read from the environment only. |
| `LLM_PROVIDER` | `anthropic` | `anthropic` or `mock` (offline stub). |
| `ANTHROPIC_MODEL` | `claude-sonnet-5-5` | Model id used when a request does not override it. |
| `ANTHROPIC_BASE_URL` | `https://api.anthropic.com` | Override for proxies/test servers. |
| `LLM_MAX_TOKENS` / `LLM_TIMEOUT` | `1024` / `60s` | Output cap / HTTP timeout. |
| `DB_URL` `DB_USER` `DB_PASSWORD` | local `hackathon` DB | Backend DB connection (compose sets these). |
| `DB_NAME` `DB_PORT` | `hackathon` / `5432` | Compose database settings. |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | Comma-separated origins allowed to call `/api`. |
| `SPRING_PROFILES_ACTIVE` | _(none)_ | `mock` = fake LLM, no key needed. |
| `BACKEND_PORT` `FRONTEND_PORT` `LOG_LEVEL` | `8080` `8081` `INFO` | Ports / log level. |

## 4. Example generic Claude request
```bash
curl -s localhost:8080/api/llm/chat -H 'Content-Type: application/json' \
  -d '{"system":"Answer in one sentence.","message":"What is a hackathon?"}'
```
```json
{ "text": "A hackathon is a time-boxed event where teams build a working prototype.",
  "model": "claude-sonnet-5-5", "stopReason": "end_turn", "usage": { "inputTokens": 21, "outputTokens": 17 } }
```

## 5. Example generic tool call
The model decides to call `get_current_time`, the backend runs it and feeds the result back:
```bash
curl -s localhost:8080/api/llm/tool-chat -H 'Content-Type: application/json' \
  -d '{"message":"What time is it in Warsaw?","tools":["get_current_time"]}'
```
```json
{ "text": "It is 14:00 in Warsaw.",
  "steps": [{ "tool": "get_current_time", "input": { "timezone": "Europe/Warsaw" },
              "output": "2026-10-03T14:00:00+02:00[Europe/Warsaw]", "error": false }],
  "usage": { "inputTokens": 40, "outputTokens": 25 }, "truncated": false }
```
Add your own tool by implementing `ToolHandler` as a `@Component` - it is registered automatically.

## 6. Example structured output
```bash
curl -s localhost:8080/api/llm/structured -H 'Content-Type: application/json' -d '{
  "prompt": "A short note about hackathons",
  "schema": { "type":"object", "required":["title","tags"],
              "properties": { "title":{"type":"string"}, "tags":{"type":"array","items":{"type":"string"}} } } }'
```
```json
{ "data": { "title": "Why hackathons work", "tags": ["teamwork", "prototyping"] } }
```
The model is forced to answer via a tool whose input schema is yours; the result is validated locally and re-requested once with the
validation errors if invalid. Still invalid -> `422` with `details`. In Java: `structuredOutputService.generate(system, prompt, schema, MyRecord.class)`.

## 7. Example Angular -> Spring call
```ts
// any component
private api = inject(ApiService);
chat = createResource<ChatResponse>();
send() { this.chat.load(this.api.chat({ message: 'Hello' })); }   // -> POST /api/llm/chat
```
```html
@if (chat.loading()) { <app-loading /> }
@else if (chat.error(); as e) { <app-error-banner [message]="e" /> }
@else if (chat.data(); as r) { {{ r.text }} }
@else { <app-empty-state /> }
```
`ApiService` is the only class that knows URLs. With `npm run start:mock` the same call is served from `public/mock/llm-chat.json`.

## 8. Testing commands
```bash
cd backend && mvn test              # 22 unit/slice tests; the 4 Testcontainers tests need Docker and skip themselves without it
cd backend && mvn verify            # same, plus packaging
cd frontend && npm test             # Karma/Jasmine, headless Chrome
cd frontend && npm run build        # type-checks templates and bundles
```

## 9. Extension points (documentation only - nothing speculative is built in)
- **New domain feature:** add a package next to `llm/` (`controller` + `service` + `repository`), a Flyway migration `V2__*.sql`, a DTO in `frontend/src/app/core/models.ts`, a method in `ApiService`, and a mock JSON in `public/mock/`.
- **Another model/provider:** implement `LlmClient`, select it in `LlmConfig` via `app.llm.provider`.
- **Tools:** implement `ToolHandler` (`@Component`). Delete `CurrentTimeTool` if unused.
- **RAG / pgvector:** use the `pgvector/pgvector:pg16` image, a migration with `CREATE EXTENSION vector`, an `EmbeddingClient` interface next to `LlmClient`, and inject retrieved context into `LlmRequest.system`.
- **MCP / agents:** `ToolCallingService` is the loop to extend or replace; MCP tools can be exposed as `ToolHandler`s.
- **Streaming:** add an SSE endpoint calling `stream: true` in `AnthropicClaudeClient`; the model records are already provider-neutral.
- **Auth:** none by design; add Spring Security only if the task requires it.

## 10. Why the starter stays generic and safe
- The only persisted entity is `llm_call_log` (an audit of model calls); the only tool is a clock; the endpoints take arbitrary prompts/schemas. There is no task vocabulary anywhere.
- No dataset, API, code or logic specific to any competition task is included; the starter was derived solely from public tooling.
- Secrets exist only as environment variables; `.env` is git-ignored, `.env.example` holds blanks; the API key is never logged or returned (`/api/health` only reports whether it is set).
- Errors share one JSON shape and never leak stack traces or internals; every request gets an `X-Request-Id` for log correlation.
- **Rules gate (verified for the general HackYeah 2026 rules, "Regulamin"):** the general rules require participants to be the authors of their contribution and not infringe third-party rights (section 6) and say nothing about AI-assisted development, code generation, external APIs, starter repositories or required repository format. Task-specific rules are announced at the event start (sections 4.6-4.7). **Therefore the following are still UNVERIFIED and must be checked against the selected competition's rules before relying on this starter:** whether AI-assisted coding and pre-existing starter code are allowed, whether Claude/external APIs are allowed, attribution/disclosure duties, submission format and deadline (the submission site is open 3 Oct 23:00 - 4 Oct 23:00, 2026). If any is restricted, drop the affected part (e.g. run with `LLM_PROVIDER=mock`) or ask the organizers.

## Biblioteki

Nowa biblioteka, model albo API trafia na tę listę (nazwa, licencja, cel).

| Nazwa | Licencja | Cel |
|---|---|---|
| `@fontsource/public-sans` (Public Sans) | OFL-1.1 | Font interfejsu, serwowany z własnego origin (bez CDN) |
