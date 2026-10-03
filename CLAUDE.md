# Anioł Stróż

Projekt na HackYeah 2026. Urządzenie przy telefonie stacjonarnym seniora słucha rozmowy, rozpoznaje etapy oszustwa (np. „na policjanta”) i ostrzega seniora oraz rodzinę. Pokazuje przy tym dosłowne cytaty z rozmowy. Decyzje zawsze podejmuje człowiek.

## Przeczytaj przed pracą

- `docs/ograniczenia-backend-java.md` i `docs/ograniczenia-frontend.md`: zasady „MUSI” / „NIE WOLNO” z identyfikatorami (np. AI-03, WEB-03). Odstępstwo zgłoś człowiekowi, nie wprowadzaj go sam.
- `docs/plan/`: lista zadań w kolejności. Rób tylko zadanie, które dostałeś, i nie zaczynaj następnego.

## Struktura

- `backend/`: Java 21, Spring Boot 3, Maven (`mvnw`), wątki wirtualne, pakiety według funkcji: `events`, `call`, `stt`, `risk`, `ai`, `audit`, `alerts`, `settings`, `demo`, `config`
- `frontend/`: Angular (standalone, signals, Material), statyczne SPA; Android przez Capacitor z tej samej bazy kodu
- `contracts/schemas/`: JSON Schema, jedyne źródło prawdy o kontrakcie. Zmiana kontraktu: schemat → `npm run generate` w `contracts/` → rekord Javy → testy kontraktowe.
- `deploy/`: Caddy lub nginx (HTTPS, reverse proxy `/api` i `/ws`)

## Komendy

```bash
cd backend && ./mvnw verify          # testy backendu (w tym kontraktowe i ArchUnit)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
cd contracts && npm run generate     # typy TS i zod ze schematów
cd frontend && npm test && npm start # testy i serwer deweloperski z proxy
deploy/run-demo.sh                   # całość za HTTPS
```

## Zasady, których nie wolno łamać

1. **AI zwraca tylko dowody**: etap i dosłowny cytat. Poziom ryzyka, teksty alertów i decyzje są deterministyczne w kodzie (`risk/`, szablony w `resources/templates/`).
2. **Każdy cytat od AI przechodzi przez `QuoteValidator`.** Cytat, którego nie ma w transkrypcji, nie wpływa na ryzyko.
3. **System nigdy nie rozłącza, nie dzwoni i nie blokuje numerów.**
4. **Audio nigdy nie trafia na dysk ani do logów.** Transkrypcja zostaje tylko w pamięci, chyba że rozmowa wywołała alert. Wyjątek: nagrania zespołu w `backend/recordings/`.
5. **Do Claude i STT nie wysyłamy danych z ustawień** (imię seniora, kontakty, numery).
6. **Każde zdarzenie, rekord audytu i ekran ma tryb** LIVE / REPLAY / SCRIPTED / MOCK.
7. **Awaria nigdy nie przechodzi po cichu**: zawsze publikujemy `system.status`. Przy awarii AI działają słowa kluczowe.
8. **Klucze API tylko w zmiennych środowiskowych.** Nigdy w repozytorium, logach, frontendzie ani APK.

## Claude API

- SDK `com.anthropic:anthropic-java`, model `claude-sonnet-5-5`, effort `LOW`, `thinking: between_tools` (tylko na tym modelu), structured output, `maxTokens 512`, timeout 2500 ms, `maxRetries 0`.
- Rubryka w `backend/src/main/resources/prompts/stage-rubric.pl.md` z cache. Transkrypcja tylko przyrasta. Bez znaczników czasu w prompcie.
- Zawsze sprawdzaj `stopReason()` przed odczytem treści. Kształtu API nie zgaduj, sprawdź go w dokumentacji SDK.
- Nie używamy narzędzi (function calling), agentów, RAG, MCP, Spring AI ani LangChain4j.

## Styl

- Kod, identyfikatory i komentarze po angielsku. Teksty dla użytkownika po polsku.
- Java: rekordy, `sealed interface`, `enum`. `ReentrantLock` zamiast `synchronized`. `Clock` wstrzykiwany. Błędy HTTP jako `ProblemDetail`.
- Angular: typy tylko z `@aniol/contracts`. Poziom ryzyka zawsze słowami, nigdy w procentach. Stan zawsze ikoną i tekstem.
- Każda nowa logika deterministyczna ma test. Testy nie wołają prawdziwego Claude ani STT (WireMock, `FakeSttProvider`, tryb MOCK).
- Nowa biblioteka, model albo API trafia do listy ujawnień w `README.md` (nazwa, licencja, cel).


## Commity

- Podczas commitowania nie dodawaj co-authored-by do wiadomości commita.