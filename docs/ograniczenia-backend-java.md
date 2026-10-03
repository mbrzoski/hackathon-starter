# Anioł Stróż: ograniczenia architektoniczne backendu (Java 21)

Ten plik zastępuje [ograniczenia-backend.md](ograniczenia-backend.md), czyli wersję dla Node/NestJS. Źródłem jest [ai-architecture-v2.md](ai-architecture-v2.md), a frontend web i Android opisuje [ograniczenia-frontend.md](ograniczenia-frontend.md). Ograniczenia „MUSI” / „NIE WOLNO” są obowiązkowe. Każdą zmianę zespół musi uzgodnić i wpisać do sekcji „Odstępstwa” na końcu.

## 1. Stos i struktura

| ID | Ograniczenie | Dlaczego |
|---|---|---|
| BE-01 | Java 21 (LTS) i Spring Boot 3.x (aktualna wersja z obsługą Javy 21). Budowanie przez Maven z wrapperem (`mvnw`) w repozytorium. | Zespół wybrał Javę. Spring Boot daje gotowe HTTP, WebSocket, konfigurację i testy. |
| BE-02 | Wątki wirtualne włączone (`spring.threads.virtual.enabled=true`). Wywołania blokujące (Claude, zapis do bazy) piszemy jako zwykły kod synchroniczny. Rozpoznawanie Vosk działa na osobnym wątku platformowym (AUD-04). Nie używamy WebFlux ani Reactora. | Java 21 obsłuży wiele jednoczesnych operacji I/O bez programowania reaktywnego, które trudniej wytłumaczyć jury. |
| BE-03 | WebSocket przez `spring-boot-starter-websocket` z własnymi `TextWebSocketHandler` / `BinaryWebSocketHandler`. NIE używamy STOMP ani SockJS. | Kontrakt `/ws/events` (JSON) i `/ws/audio` (ramki binarne) musi być identyczny jak w architekturze. STOMP zmieniłby protokół widziany przez frontend. |
| BE-04 | Jeden proces, jedno gospodarstwo domowe, jedna aktywna rozmowa naraz. Nie robimy kont użytkowników, wielodostępności ani skalowania. | Zakres hackathonu. Ograniczenie podajemy jury otwarcie. |
| BE-05 | Pakiety według funkcji, nie według warstw: `events`, `call`, `stt`, `risk`, `ai`, `audit`, `alerts`, `settings`, `demo`, `config`. Pakiet `ai` nie zależy od `risk`: oddaje trafienia, a resztą zajmuje się `call`. Sprawdza to test ArchUnit. | Każdą część da się wytłumaczyć i przetestować osobno. |
| BE-06 | Modele API (żądania, odpowiedzi, payloady zdarzeń) generujemy z `openapi.yaml` (CON-03). Wewnętrzny model domeny (np. `CallState`, wyniki klasyfikatora) to rekordy Javy (`record`), niemutowalne. Unie typów (np. rodzaje zdarzeń) jako `sealed interface` z rekordami i `switch` z dopasowaniem wzorców. Typy wyliczeniowe (`StageId`, `RiskLevel`, `Mode`) jako `enum`. | Język Javy 21 wymusza kompletność obsługi przypadków. |
| BE-07 | Dane trwałe w SQLite (`org.xerial:sqlite-jdbc`) przez `JdbcClient` ze Springa albo w plikach JSON / JSONL. Bez JPA/Hibernate i bez zewnętrznej bazy. | Prostota. Mało tabel i brak relacji. |
| BE-08 | Konfiguracja przez `application.yml` i zmienne środowiskowe (`@ConfigurationProperties` z walidacją). Sekrety tylko w zmiennych środowiskowych. NIE WOLNO commitować kluczy ani ich logować. W repozytorium jest `.env.example`. | Bezpieczeństwo, publiczne repozytorium. |

## 2. Kontrakt z frontendem: `openapi.yaml`

Jedynym źródłem prawdy o kontrakcie jest plik **`contracts/openapi.yaml`** (OpenAPI 3.1). Zespół zrezygnował z osobnych plików JSON Schema i z pakietu npm.

| ID | Ograniczenie | Dlaczego |
|---|---|---|
| CON-01 | `contracts/openapi.yaml` opisuje wszystkie endpointy REST (`paths`, każda operacja z `operationId` i tagiem) i wszystkie typy (`components/schemas`): `TranscriptSegment`, `StageHit`, `RiskUpdate`, `Alert`, `Decision`, `SystemStatus`, `EventEnvelope` (oneOf z `discriminator` po `type`), `Settings`, `Scenario`, `StageHits` (odpowiedź Claude), `AudioControl`, `ProblemDetail`. WebSockety `/ws/events` i `/ws/audio` są opisane w rozszerzeniu `x-websockets` z `$ref` do tych samych schematów. | Jeden kontrakt dla Javy, Angulara i Androida. |
| CON-02 | Kontrakt zmieniamy tylko w tej kolejności: najpierw `openapi.yaml`, potem generowanie kodu w backendzie i frontendzie, na końcu implementacja. NIE WOLNO dodawać endpointu ani pola w kodzie bez zmiany w `openapi.yaml`. | Kontrakt nie rozjeżdża się z kodem. |
| CON-03 | Backend generuje z kontraktu interfejsy i modele: `openapi-generator-maven-plugin` (generator `spring`, `interfaceOnly=true`, `useSpringBoot3=true`, `useJakartaEe=true`, `openApiNullable=false`), kod w `target/`, bez commitowania. Kontrolery implementują wygenerowane interfejsy. Testy MockMvc z `swagger-request-validator-mockmvc` sprawdzają żądania i odpowiedzi względem `openapi.yaml`. Zdarzenia WebSocket są walidowane względem `components/schemas` (`com.networknt:json-schema-validator`, bo schematy OpenAPI 3.1 to JSON Schema 2020-12). | Rozjazd z kontraktem psuje kompilację albo testy. |
| CON-04 | Jackson: pola w JSON w `camelCase`, daty jako ISO-8601 (`WRITE_DATES_AS_TIMESTAMPS=false`), enumy jako nazwy (`AUTHORITY_CLAIM`), `FAIL_ON_UNKNOWN_PROPERTIES=true` na wejściu. | Zgodność z kontraktem, a nieznane pola od razu wychodzą na jaw. |
| CON-05 | Normalizacja tekstu do walidacji cytatów (małe litery, bez polskich znaków diakrytycznych i interpunkcji, zredukowane spacje) jest zaimplementowana w Javie (`QuoteNormalizer`, `java.text.Normalizer`, NFD) i w TS we frontendzie. Obie implementacje przechodzą te same wektory testowe z `contracts/test-vectors/normalize.json`. | Frontend podświetla cytaty tak samo, jak backend je zatwierdza. Uwaga na „ł”, które nie rozkłada się w NFD i wymaga ręcznej zamiany na „l”. |
| CON-06 | Każde wejście przechodzi walidację: żądania HTTP przez Bean Validation na wygenerowanych modelach, wiadomości WebSocket i pliki scenariuszy przez walidator schematów z `openapi.yaml`, a odpowiedź Claude przez structured output i `QuoteValidator`. | Odporność na błędne dane. |
| CON-07 | Odpowiedź Claude mapujemy na ręcznie napisany rekord `StageHitsResponse` w pakiecie `ai`, a nie na model z generatora. Test sprawdza, że jego JSON przechodzi walidację względem `components/schemas/StageHits`. | Adnotacje generatora mogłyby zmienić schemat wysyłany do API Claude. |

## 3. Granice AI (najważniejsze)

| ID | Ograniczenie | Dlaczego |
|---|---|---|
| AI-01 | Claude zwraca tylko dowody: `stage`, `segmentId`, dosłowny `quote`, `speakerRole`. NIE WOLNO, żeby model zwracał poziom ryzyka, pewność, tekst dla seniora ani decyzję. | Wynik ma być deterministyczny i do obrony (sekcja 5 architektury). |
| AI-02 | Jedno wywołanie Messages API na finalny segment, najwyżej jedno wywołanie w locie na rozmowę. Segmenty, które przyjdą w trakcie, idą w jednym kolejnym wywołaniu (np. `ReentrantLock` + flaga „dirty” na rozmowę). Nie używamy agentów, multi-agent, narzędzi, RAG, embeddingów, MCP ani Spring AI. | Opóźnienie i prostota. Spring AI dodaje warstwę abstrakcji, której nie potrzebujemy i którą trudniej wytłumaczyć. |
| AI-03 | Oficjalne SDK `com.anthropic:anthropic-java` (aktualna wersja z Maven Central). Model `claude-sonnet-5-5` (konfigurowalny), `maxTokens(512)`, wysiłek przez `OutputConfig.builder().effort(OutputConfig.Effort.LOW)`. Structured output przez `outputConfig(StageHits.class)` z rekordu albo przez ręczny schemat `JsonOutputFormat`, jeśli typowany builder nie pozwala ustawić jednocześnie effort. Rozumowanie wyłączamy przez `thinking: {type: "between_tools"}` (tylko Sonnet 5.5). Jeśli wersja SDK nie ma dla tego typowanej klasy, używamy udokumentowanego w SDK mechanizmu dodatkowych pól body. Kształt wywołania sprawdzamy w dokumentacji SDK, nie zgadujemy. | Parametry modelu sprawdzone 3.10.2026 w oficjalnej dokumentacji Anthropic. |
| AI-04 | Haiku 4.5 tylko do porównania w ewaluacji, i wtedy bez pola `between_tools`. Nie może być modelem głównym, bo jego wycofanie zaplanowano najwcześniej na 15.10.2026. | Ciągłość działania po hackathonie. |
| AI-05 | Timeout 2500 ms na żądanie, `maxRetries(0)` w kliencie SDK. Zawsze sprawdzamy `stopReason()` przed odczytem treści. `refusal`, `max_tokens`, timeout, 429 i 5xx: pomijamy cykl i zapisujemy to w audycie. Wyjątki łapiemy według typowanych klas SDK, od najbardziej szczegółowej, nigdy po tekście komunikatu. | Spóźniony wynik nic nie daje; słowa kluczowe działają dalej. |
| AI-06 | Prompt systemowy (rubryka z `src/main/resources/prompts/stage-rubric.pl.md`) idzie jako `TextBlockParam` z `CacheControlEphemeral` przez `.systemOfTextBlockParams(...)`. Transkrypcja tylko przyrasta, z breakpointem cache na końcu. NIE WOLNO wstawiać do promptu znacznika czasu ani innych zmiennych danych przed transkrypcją. | Prompt caching obniża koszt i opóźnienie. Zwykłe `.system(String)` nie przenosi cache. |
| AI-07 | Transkrypcja to niezaufane dane. Trafia w tagu `<transcript>` z instrukcją ignorowania poleceń w treści. | Rozmówca może próbować prompt injection. |
| AI-08 | `QuoteValidator` MUSI odrzucić każde trafienie z nieistniejącym `segmentId` albo z cytatem, którego nie ma w tym segmencie po normalizacji (CON-05). Odrzucone trafienia nie wpływają na ryzyko, ale trafiają do audytu. | AI nie może wywołać alarmu zmyślonym dowodem. |
| AI-09 | Do Claude ani do STT NIE WOLNO wysyłać imienia seniora, kontaktów, numerów telefonów ani identyfikatorów urządzeń. Sprawdza to test na zbudowanym `MessageCreateParams`. | Minimalizacja danych. |
| AI-10 | Informacje zwrotne (fałszywy alarm, potwierdzone oszustwo) zapisujemy tylko jako etykiety do ewaluacji. NIE WOLNO dopisywać ich automatycznie do promptu ani zmieniać na ich podstawie reguł. | Ryzyko zatrucia danych, zgoda, przewidywalność. |

## 4. Logika deterministyczna

| ID | Ograniczenie | Dlaczego |
|---|---|---|
| DET-01 | Poziom ryzyka liczy czysta, statyczna funkcja `RiskEngine.computeLevel(List<StageHit> validatedHits, Sensitivity s)` bez zależności od Springa. Ma testy parametryzowane (`@ParameterizedTest`) dla każdej reguły i każdej czułości. | Przewidywalność, regułę da się pokazać na jednym slajdzie. |
| DET-02 | Trafienia z `speakerRole` równym `SENIOR` albo `BACKGROUND` nie liczą się do MONEY_REQUEST ani PAYMENT_CHANNEL. | Zdanie „nie podam kodu BLIK” albo telewizja w tle nie mogą wywołać alarmu. |
| DET-03 | `KeywordDetector` (wzorce `java.util.regex.Pattern` z flagami `CASE_INSENSITIVE | UNICODE_CASE`, odporne na brak polskich znaków) działa zawsze na segmentach interim i finalnych. | Fallback i baseline do porównania. |
| DET-04 | Teksty alertów pochodzą tylko z wcześniej napisanych szablonów (`src/main/resources/templates/alerts.pl.yml`), które przejrzał zespół. Wybiera je kod. NIE WOLNO generować tekstu dla seniora na żywo. | Bezpieczny komunikat dla osoby w stresie. |
| DET-05 | Jeden alert na poziom na rozmowę. Poziom w trakcie rozmowy może tylko rosnąć. Wyjątkiem jest „nie licz tego etapu” od rodziny albo zmiana czułości. Drugi wyjątek (D-05): na tym samym poziomie powstaje kolejny alert, gdy zmienia się szablon na bardziej szczegółowy (np. kanał płatności po autorytecie i pieniądzach), maksymalnie raz na szablon. | Bez migotania alertów. |
| DET-06 | Backend NIE MA żadnej funkcji rozłączania, dzwonienia ani blokowania numerów. SMS (opcjonalny) dostaje tylko rodzina, a w trybach demo wiadomość ma prefiks „[DEMO]”. | Decyzje podejmuje człowiek. |

## 5. Audio i STT

| ID | Ograniczenie | Dlaczego |
|---|---|---|
| AUD-01 | Kontrakt audio bez zmian: `/ws/audio`, PCM 16 kHz, mono, 16-bit LE, ramki 100 ms (3200 bajtów) jako `BinaryMessage`, sterowanie jako `TextMessage` JSON (`start`, `stop`, `pause`, `resume`). Limit rozmiaru ramki ustawiony w kontenerze WebSocket (np. 64 KB). | Web i Android wysyłają ten sam format. |
| AUD-02 | Audio NIE MOŻE być zapisywane na dysk, logowane ani buforowane dłużej, niż wymaga przekazanie do STT. Jedyny wyjątek to nagrania zespołu w `recordings/` dla trybu REPLAY, opisane w README. | Prywatność rozmówcy i seniora. |
| AUD-03 | STT jest schowane za interfejsem `SttProvider`. Domyślnie `VoskSttProvider`: lokalny, offline (`com.alphacephei:vosk` i `net.java.dev.jna:jna`, `Recognizer` dla 16000 Hz, język polski), z modelem `vosk-model-small-pl-0.22` ładowanym ze ścieżki z konfiguracji (`app.stt.vosk.model-path`, zmienna `APP_STT_VOSK_MODEL_PATH`). Modelu nie ma w repozytorium. Do testów `FakeSttProvider`. Wybór przez konfigurację. API sprawdzamy w dokumentacji Vosk dla Javy. Zewnętrznego (chmurowego) dostawcy STT nie dodajemy bez wpisu w „Odstępstwach”. | Wymiana dostawcy bez zmian w pipeline. Audio zostaje na urządzeniu. |
| AUD-04 | Rozpoznawanie Vosk to natywny kod obciążający procesor, wołany przez JNA. Każda rozmowa ma jeden dedykowany wątek platformowy, który czyta ramki z ograniczonej kolejki, woła `acceptWaveForm` i odbiera wyniki częściowe i końcowe. NIE WOLNO uruchamiać rozpoznawania na wątkach wirtualnych ani w tym wątku wywoływać Claude lub robić blokującego I/O. Gotowy segment przekazujemy do executora na wątkach wirtualnych (`Executors.newVirtualThreadPerTaskExecutor()`) i od razu wracamy do rozpoznawania. Przepełnienie kolejki ramek ustawia `stt` na `degraded`. | Natywne wywołania przypinają wątek wirtualny do wątku nośnego, a zablokowany wątek rozpoznawania gubi mowę. |
| AUD-05 | Audio NIE opuszcza urządzenia: STT działa lokalnie, więc nie ma dostawcy STT, jego retencji ani logowania. Biblioteka Vosk, JNA i model trafiają do listy ujawnień (licencje). Zewnętrzny STT wymaga wpisu w „Odstępstwach” i ponownej oceny prywatności. | Dane trzeciej osoby. |
| AUD-06 | Segmenty interim idą tylko do słów kluczowych i do UI. Claude dostaje wyłącznie segmenty finalne. | Koszt i stabilność cytatów. |
| AUD-07 | Bez zgody seniora i rodziny w ustawieniach backend odrzuca audio w trybie LIVE (zamyka sesję WebSocket z kodem 1008 i komunikatem). Tryby demo działają bez zgody. | Zgoda przed przetwarzaniem. |

## 6. Współbieżność i stan

| ID | Ograniczenie | Dlaczego |
|---|---|---|
| CC-01 | Stan rozmowy (`CallState`) zmieniany jest tylko pod blokadą tej rozmowy albo w jednym wątku na rozmowę. Kolekcje współdzielone są typu `ConcurrentHashMap` / `CopyOnWriteArrayList`. | Callbacki STT, wyniki Claude i decyzje przychodzą równolegle. |
| CC-02 | Wysyłanie do sesji WebSocket przez `ConcurrentWebSocketSessionDecorator` (limit czasu i bufora). Wolny klient nie blokuje innych. | `WebSocketSession.sendMessage` nie jest bezpieczne wątkowo. |
| CC-03 | Na wątkach wirtualnych NIE WOLNO blokować się w `synchronized` przy wywołaniach sieciowych. Używamy `ReentrantLock`. | W Javie 21 `synchronized` przypina wątek wirtualny do wątku nośnego. |
| CC-04 | Zadania okresowe (heartbeat, retencja, wykrywanie ciszy) przez `@Scheduled`, każde z jasnym interwałem w konfiguracji. | Przewidywalne i testowalne z fałszywym zegarem (`java.time.Clock` wstrzykiwany jako bean). |

## 7. Dane, prywatność i retencja

| ID | Ograniczenie | Dlaczego |
|---|---|---|
| DAT-01 | Transkrypcja jest trzymana tylko w pamięci przez czas rozmowy. Po rozmowie bez alertu usuwamy ją całą. Po rozmowie z alertem zostają tylko cytowane segmenty ±2 segmenty kontekstu. | Minimalizacja danych. |
| DAT-02 | Retencja ustawiana od 1 do 90 dni (domyślnie 30). Czyszczenie przy starcie i co godzinę. `DELETE /api/data` usuwa wszystko. | Kontrola po stronie rodziny. |
| DAT-03 | W audycie po rozmowie bez alertu usuwamy pola z tekstem i zostawiamy tylko liczby. | Ta sama zasada co DAT-01. |
| DAT-04 | Na hackathonie używamy wyłącznie danych syntetycznych i nagrań role-play zespołu. | Regulamin i etyka. |
| DAT-05 | Push FCM i SMS zawierają tylko poziom i jedno zdanie, bez cytatów z rozmowy. Push wysyłamy przez Firebase Admin SDK dla Javy (opcjonalnie). | Minimalizacja danych u kolejnych dostawców. |

## 8. API, bezpieczeństwo HTTP i zdarzenia

| ID | Ograniczenie | Dlaczego |
|---|---|---|
| API-01 | Endpointy i zdarzenia są dokładnie takie, jak w sekcji 6.5 architektury. Nowy endpoint wymaga wpisu w „Odstępstwach”. Swagger UI serwuje plik `contracts/openapi.yaml` (nie generujemy opisu z kodu) tylko w profilu `dev`. | Frontend web i Android polegają na tym samym kontrakcie. |
| API-02 | Każde zdarzenie i każdy rekord audytu ma pole `mode` (LIVE / REPLAY / SCRIPTED / MOCK). | Uczciwe oznaczanie demo. |
| API-03 | Po podłączeniu klient `/ws/events` dostaje od razu aktualny stan. | Odświeżenie strony albo powrót aplikacji Android z tła nie gubi stanu. |
| API-04 | Backend działa za reverse proxy z HTTPS na tym samym originie co frontend (`server.forward-headers-strategy=framework`). CORS jest wyłączony w produkcji, a w profilu `dev` dopuszczamy tylko `localhost`. Origin połączeń WebSocket ograniczony przez `setAllowedOrigins`. | Spójność z WEB-02, WEB-03 i AND-02. |
| API-05 | Endpointy deweloperskie (`/api/dev/*`) działają tylko w profilu `dev` (`@Profile("dev")`). | Nikt z zewnątrz nie wstrzyknie fałszywego alertu. |
| API-06 | Błędy HTTP w jednym formacie: `ProblemDetail` (RFC 9457) z `@RestControllerAdvice`. Komunikaty dla UI po polsku w polu `detail`. | Jeden sposób obsługi błędów na frontendzie. |
| API-07 | Rejestracja tokenu FCM (opcjonalnie): `POST /api/devices` z `{ role: "family", fcmToken }`. | Push dla Androida bez kont użytkowników. |

## 9. Odporność i obserwowalność

| ID | Ograniczenie | Dlaczego |
|---|---|---|
| OBS-01 | Każda awaria publikuje `system.status` (`audio`, `stt`, `ai`, `backend`) ze stanem `ok`, `degraded` albo `down` i komunikatem po polsku. NIE WOLNO, żeby awaria przeszła po cichu. | Senior i rodzina muszą wiedzieć, że ochrona nie działa. |
| OBS-02 | Progi: 10 s ciszy lub brak ramek → audio `down`. STT: błąd rozpoznawania (brak modelu, wyjątek natywny) → 3 próby ponownego utworzenia rozpoznawacza (1, 2, 4 s), potem `down`. AI: 3 kolejne błędy → `degraded`, a pierwszy sukces przywraca `ok`. | Spójne zachowanie, które da się przetestować. |
| OBS-03 | Każde wywołanie Claude trafia do audytu: model, effort, zakres segmentów, `usage` (wejście, odczyt z cache, wyjście), opóźnienie, `stopReason`, surowy wynik, walidacja każdego trafienia, poziom przed i po, wynik słów kluczowych dla tych samych segmentów. | Dowód dla jury i źródło liczb do PDF. |
| OBS-04 | Koszt w `/api/audit/summary` liczymy z `usage` i cennika z konfiguracji (Sonnet 5.5: 2 / 0,20 / 10 USD za 1M tokenów wejście / odczyt z cache / wyjście), na `BigDecimal`. W PDF podajemy tylko zmierzone liczby. | Uczciwość i brak błędów zaokrągleń. |
| OBS-05 | Logi przez SLF4J/Logback, bez audio, pełnych transkrypcji, kluczy i danych z ustawień. Spring Boot Actuator tylko z `health`, wystawiony w profilu `dev` albo za proxy. | Prywatność i bezpieczeństwo. |

## 10. Testy i jakość

| ID | Ograniczenie |
|---|---|
| TST-01 | JUnit 5 + AssertJ + Mockito. Logika deterministyczna (RiskEngine, KeywordDetector, QuoteValidator, QuoteNormalizer, wybór szablonu, retencja, przejścia statusów) ma testy jednostkowe bez kontekstu Springa. |
| TST-02 | Testy NIE wywołują prawdziwego Claude ani STT. Do tego służą WireMock dla HTTP Anthropic albo mock interfejsu `StageClassifier`, `FakeSttProvider` i tryb MOCK. |
| TST-03 | Testy kontraktowe z `openapi.yaml` (CON-03, CON-07) i wektory normalizacji (CON-05) muszą przechodzić w `mvn verify`. Każdy endpoint ma co najmniej jeden test MockMvc z walidatorem OpenAPI. |
| TST-04 | Test ArchUnit pilnuje zależności między pakietami (BE-05) oraz zakazu użycia `synchronized` w pakietach `ai` i `stt` (CC-03). |
| TST-05 | Ewaluacja: osobny profil lub klasa main (`EvalRunner`), która uruchamia 12 scenariuszy w trzech wariantach (tylko słowa kluczowe, tylko AI, oba) i zapisuje tabelę właściwości bez procentów i bez „wyniku punktowego”. |

## 11. Czego backend nie robi (lista zamknięta)

- Nie podejmuje decyzji za seniora i nie steruje telefonem.
- Nie uczy się automatycznie na podstawie informacji zwrotnych.
- Nie przechowuje audio i nie przechowuje transkrypcji rozmów bez alertu.
- Nie obsługuje wielu gospodarstw, kont użytkowników ani logowania.
- Nie używa Spring AI, LangChain4j, STOMP, WebFlux ani JPA.
- Bez internetu nie ma Claude, więc działają tylko słowa kluczowe („podstawowa ochrona”), a UI to pokazuje. STT działa lokalnie i nie potrzebuje internetu.

## 12. Do ujawnienia w zgłoszeniu (dodatki względem wersji Node)

Licencje sprawdzamy na starcie: Spring Boot (Apache 2.0), `anthropic-java` (licencja z repozytorium SDK), Vosk (`com.alphacephei:vosk`) z modelem `vosk-model-small-pl-0.22` i JNA (licencje do potwierdzenia w ich repozytoriach), sqlite-jdbc, Jackson, openapi-generator, swagger-parser, swagger-request-validator, networknt json-schema-validator, ArchUnit, WireMock, springdoc-openapi, opcjonalnie Firebase Admin SDK i Twilio Java SDK.

## Odstępstwa

| Data | ID | Zmiana | Kto zdecydował | Dlaczego |
|---|---|---|---|---|
| 2026-10-03 | AUD-03, AUD-04, AUD-05, OBS-02, BE-02 | STT zmienione z Azure AI Speech na lokalny, offline Vosk (`vosk-model-small-pl-0.22`). Zmienione treści AUD-03…AUD-05, OBS-02 i BE-02, usunięte klucze `AZURE_SPEECH_*`. | Zespół | Audio nie opuszcza urządzenia, brak kluczy, regionu i retencji u dostawcy, słowa kluczowe działają bez internetu. Koszt: słabszy polski model niż chmurowe STT, jakość do zmierzenia na nagraniach zespołu. |
| 2026-10-03 | API-01 | Dodane ścieżki poza sekcją 6.5 architektury: `/api/alerts`, `/api/alerts/{id}/decision`, `/api/demo/*`, `/api/calls`, `/api/calls/{id}/audit`, `/api/audit/summary`, `/api/dev/emit` (tylko `dev`) oraz `DELETE /api/data` (kasowanie wszystkiego, DAT-02). Wszystkie w `contracts/openapi.yaml`. | Wpis na polecenie użytkownika, do potwierdzenia przez zespół | Potrzebne dla historii alertów, demo, audytu AI i kontroli danych przez rodzinę. |
| 2026-10-03 | CON-03 | Część modeli z generatora zastąpiona ręcznymi rekordami Javy przez `importMappings` w `pom.xml` (m.in. `Alert`, `Decision`, `EventEnvelope`, `StageId`, `RiskLevel`, `AuditRecord`, `ProblemDetail`). Interfejsy zostają z generatora. | Wpis na polecenie użytkownika, do potwierdzenia przez zespół | Rekordy, `sealed interface` i enumy z `@JsonValue` są zgodne ze stylem projektu (CLAUDE.md). |
| 2026-10-03 | BE-07 | Schemat bazy w `schema.sql` (`spring.sql.init.mode=always`, `CREATE TABLE IF NOT EXISTS`), druga tabela `audit_calls` obok `audit_records`. Pula Hikari z jednym połączeniem. | Wpis na polecenie użytkownika, do potwierdzenia przez zespół | Jeden zapis naraz w SQLite; lista rozmów audytu potrzebuje startu, końca i maksymalnego poziomu. |
| 2026-10-03 | CON-06 | Walidator scenariuszy: networknt json-schema-validator 2.0.1 (nie 1.5.8). Nowa biblioteka testowa: `openapi-request-validator-mockmvc` 3.0.0 (Atlassian, Apache 2.0). Obie w liście ujawnień w `README.md`. | Wpis na polecenie użytkownika, do potwierdzenia przez zespół | Powód zmiany wersji do uzupełnienia przez autora zmiany. Biblioteka testowa sprawdza każde żądanie MockMvc względem `openapi.yaml` (CON-06). |
| 2026-10-03 | API-06 | Konflikt (np. rozmowa już trwa, odtwarzanie już działa) kończy się `409` jako `ProblemDetail`. | Wpis na polecenie użytkownika, do potwierdzenia przez zespół | Czytelny komunikat dla UI zamiast 500. |
| 2026-10-03 | DET-05 | Drugi wyjątek od „jeden alert na poziom”: kolejny alert na tym samym poziomie, gdy zmienia się szablon na bardziej szczegółowy (maksymalnie raz na szablon). | Wpis na polecenie użytkownika, do potwierdzenia przez zespół | Senior słyszy tekst z najważniejszym etapem (np. kanał płatności), a nie ogólny. Decyzja D-05. |
| 2026-10-03 | DAT-02 | Retencja: `app.retention.days` 1 do 90 (domyślnie 30), czyszczenie przy starcie i co godzinę, `DELETE /api/data`. Bez ustawień z UI do czasu BE-09. | Wpis na polecenie użytkownika, do potwierdzenia przez zespół | Kontrola danych po stronie rodziny w MVP. |
| 2026-10-03 | DAT-01, DAT-03 | Tekst audytu AI trwającej rozmowy jest tylko w pamięci i trafia do SQLite dopiero przy zakończeniu rozmowy z alertem (D-33). Wiersz trwającej rozmowy ma same liczby. | Wpis na polecenie użytkownika, do potwierdzenia przez zespół | Zgodność z zasadą „bez alertu tekst nie trafia na dysk”, także po awarii w trakcie rozmowy. |
