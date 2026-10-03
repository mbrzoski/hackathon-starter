# Review backendu: findingi

Stan: `master`, commit 35487c4. Źródła reguł: `CLAUDE.md`, `docs/ograniczenia-backend-java.md`, `docs/architecture.md`.

Uwagi do review:
- `./mvnw verify` nie został uruchomiony (brak `java` w PATH). Wnioski pochodzą z czytania kodu.
- `docs/plan/` nie istnieje, więc zakres zrobionych zadań nie był weryfikowalny.

## Findingi

| ID | Waga | Reguła | Miejsce | Opis | Rekomendacja |
|---|---|---|---|---|---|
| F-01 | Wysoka | CON-02, API-01 | `contracts/openapi.yaml`, `StatusController`, `DevEmitController` | `GET /api/status` i `POST /api/dev/emit` nie ma w `paths` ani w „Odstępstwach”. Poza sekcją 6.5 architektury są też: `GET /api/alerts`, `GET /api/demo/scenarios`, `POST /api/demo/stop`, zdarzenia `call.started` i `call.ended`, rola `audit` w `/ws/events`. Brak wpisu o ręcznie pisanych rekordach zamiast generowanych (CON-03/BE-06) i o enumach małymi literami na wire (CON-04). | Dodać endpointy do kontraktu. Wpisać odstępstwa do tabeli „Odstępstwa” albo zgłosić człowiekowi. |
| F-02 | Średnia | CON-05, TST-03 | `QuoteNormalizerTest`, `QuoteNormalizer` | Test Javy nie czyta `contracts/test-vectors/normalize.json`, ma własne `@CsvSource`. Javadoc wskazuje nieistniejący `contracts/ts/normalize.ts` (TS jest w `frontend/src/app/shared/normalize.ts`). Zgodność Java↔TS nie jest wymuszona. | Test parametryzowany ma wczytywać wspólny plik wektorów. Poprawić ścieżkę w Javadocu. |
| F-03 | Średnia | OBS-01, zasada 7 | `HeartbeatService` | Co 10 s publikuje `BACKEND = OK` bezwarunkowo i nadpisuje `DEGRADED` z `CallService`, `RetainAlertedCallHook`, `DecisionService`. Awaria znika z UI po najwyżej 10 s. | Heartbeat nie może przywracać `OK` po awarii. Potrzebny jawny model stanu komponentu. |
| F-04 | Średnia | API-03 | `EventBus.snapshot` | `lastAlert` nie jest czyszczony po `call.ended`. Odświeżona strona dostaje stary alert bez aktywnej rozmowy. Snapshot nie zawiera `risk.update` ani `alert.decision`, więc alert wraca jako niezdecydowany. | Czyścić `lastAlert` przy końcu rozmowy. Dodać do snapshotu poziom ryzyka i decyzje. Dopisać test. |
| F-05 | Średnia | Zasada 2, AI-08 | `CallService.addHits` | Przyjmuje `StageHit` z flagą `validated` ustawianą przez wywołującego, bez sprawdzenia cytatu. `QuoteValidator` nie istnieje, a `risk/package-info` twierdzi, że istnieje. Dziś hity pochodzą tylko z `KeywordDetector`. | `QuoteValidator` jako bramka przed `addHits`. Hit `source=LLM` bez walidacji odrzucać. Poprawić Javadoc. |
| F-06 | Średnia | API-05, OBS-05 | `application.yml` | `spring.profiles.default: dev`. Start bez profilu włącza `/api/dev/emit`, DEBUG, szczegóły `health`, originy `localhost`. `deploy/` jeszcze nie istnieje. | Domyślny profil bezpieczny. `dev` ustawiać jawnie w `Makefile` i `.env.example`. |
| F-07 | Niska–średnia | DAT-02 | `AlertStore`, `LabelWriter` | Fragmenty transkrypcji w SQLite (`aniol.db`) i etykiety w `data/labels.jsonl` są zapisywane bez retencji, czyszczenia (start, co godzinę) i bez `DELETE /api/data`. | Zrobić retencję albo zaznaczyć jako znaną lukę. |
| F-08 | Niska | Zasada 2, DAT-01 | `CallService.addSegment`, `CallState.segmentIdFor` | Hit z interim dostaje `segId` przyszłego finala. Gdy final nie nadejdzie albo ma inny tekst, alert cytuje nieistniejący segment. `TranscriptExcerpt` nie zachowa wtedy kontekstu, a cytat jest niewerfyikowalny. | Rozstrzygnąć przy zadaniu STT: alert tylko z hitów po finalu albo walidacja po finalu. |
| F-09 | Niska | Zasada 7 | `DecisionService.record` | `ignoredStages` dla zakończonej rozmowy: `ignoreStages` zwraca `false`, wynik jest ignorowany. API zwraca 201 i zapisuje `ignored_stages`, a nic się nie zmienia. | Zwrócić 409/400 albo opublikować status. |
| F-10 | Niska | TST-03, CON-03 | testy | Walidator OpenAPI tylko w `AlertsControllerTest` i `DemoControllerTest`. Zdarzenia WebSocket nie są walidowane względem `components/schemas`. Brak testu rekordy↔schematy (obiecanego w `contracts/package-info`). | Dodać walidację zdarzeń przez `networknt` i test rekordy↔schematy. |
| F-11 | Niska | dokumentacja | `docs/`, `CLAUDE.md`, `contracts/package-info` | Brak `docs/plan/`. `CLAUDE.md` mówi „bez `paths`”, a `openapi.yaml` ma `paths`. `contracts/package-info` odwołuje się do nieistniejących `contracts/schemas`. Dokumenty `ograniczenia-*` linkują do nieistniejących plików. | Ujednolicić dokumenty. |
| F-12 | Niska | API-06 | `ApiExceptionHandler` | Każde 409 dostaje „rozmowa już trwa”. | Przekazywać komunikat z wyjątku. |
| F-13 | Niska | CC-02 | `EventBus.publish`, `register` | Wysyłka poza blokadą, a `register` trzyma blokadę podczas snapshotu. Nowy klient może dostać zdarzenie dwa razy albo w złej kolejności. Wysyłka pod blokadą rozmowy: wolny klient trzyma ją do `sendTimeLimitMs` (5 s). | Opisać ograniczenie albo wysyłać z osobnej kolejki. |
| F-14 | Niska (do potwierdzenia) | DET-02 | `KeywordDetector` | Hity zawsze mają `SpeakerRole.UNCLEAR`, więc DET-02 nie działa dla słów kluczowych. „Nie podam kodu BLIK” od seniora liczy się jako `PAYMENT_CHANNEL`. Nie sprawdzono, czy scenariusz 07 jest tym objęty. | Opisać w README jako ograniczenie albo mapować `speaker` na rolę. |

## Jeszcze nie zrobione (to nie naruszenia)

- Pakiet `ai` (klient Claude, `QuoteValidator`, audyt wywołań).
- `/ws/audio`, `SttProvider`, zgoda w trybie LIVE (AUD-07).
- `settings`, retencja i `DELETE /api/data`.
- `audit` (OBS-02…04).
- `springdoc` w profilu `dev`, `deploy/`.
- `POST /api/devices` (opcjonalny), `EvalRunner` (TST-05).
- Wpisy do listy ujawnień w `README.md` (`networknt`, `sqlite-jdbc`, `openapi-generator`, `ArchUnit`).

## Kolejność poprawek (propozycja)

1. F-01, F-03, F-04.
2. F-05, F-06, F-02.
3. Reszta wg wagi.
