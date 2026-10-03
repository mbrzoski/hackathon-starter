# Review backendu: findingi (po walidacji względem `docs/plan/plan.md`)

Stan: `master`, po scaleniu poprawek F-01…F-23 (BE-01…BE-07). Źródła reguł: `CLAUDE.md`, `docs/ograniczenia-backend-java.md`, `docs/architecture.md`, `docs/plan/plan.md`.

Uwagi:
- `./mvnw verify` po scaleniu: wynik w opisie commita scalającego (JDK 21). Findingi BE-06 i BE-07 pochodziły z czytania kodu; F-16 i F-18 potwierdzone testami regresji.
- Walidacja: finding zostaje aktualny, jeśli dotyczy zadania już zrobionego (BE-01…BE-07, CT-01, EV-01, EV-02, FE-01…FE-04). Jeśli dotyczy funkcji z jeszcze niezrobionego zadania, jest przeniesiony do „Wymagania na przyszłe zadania”.

## 1. Findingi aktualne (dotyczą zrobionych zadań)

| ID | Waga | Zadanie | Reguła | Miejsce | Opis | Rekomendacja |
|---|---|---|---|---|---|---|
| F-10 | Niska | BE-02, CT-01 | TST-03, CON-03 | testy | Walidator OpenAPI jest teraz w testach alertów, demo, statusu i `/api/dev/emit`. Zdarzenia `/ws/events` nie są walidowane względem `components/schemas`. Brak testu rekordy↔schematy (obiecanego w `contracts/package-info`). | Dodać walidację zdarzeń przez `networknt` i test rekordy↔schematy. |
| F-11 | Niska | BE-01, CT-01 | dokumentacja | `CLAUDE.md`, `contracts/package-info`, `docs/` | `CLAUDE.md` mówi „bez `paths`”, a `openapi.yaml` ma `paths`. `contracts/package-info` odwołuje się do nieistniejących `contracts/schemas`. Plan nazywa CT-01 „Kontrakty JSON Schema”, a kontrakt to OpenAPI. Dokumenty `ograniczenia-*` linkują do nieistniejących plików. | Ujednolicić dokumenty. |
| F-13 | Niska | BE-02 | CC-02 | `EventBus.publish`, `register` | Wysyłka poza blokadą, a `register` trzyma blokadę podczas snapshotu: nowy klient może dostać zdarzenie dwa razy albo w złej kolejności, wbrew komentarzowi „snapshot, then live events”. | Poprawić komentarz albo kolejność. |

## 1a. Zgodność BE-06 i BE-07 z regułami (po poprawkach F-15…F-23)

| Reguła | Status | Uwagi |
|---|---|---|
| AI-01 AI zwraca tylko dowody | ✅ | `StageHitsResponse`: etap, `segment_id`, cytat, `speaker_role`. Poziom, teksty i decyzje w kodzie. |
| AI-02 jedno wywołanie w locie na rozmowę | ✅ | `CallClassificationQueue` z flagą „dirty”, `ReentrantLock`, executor na wątkach wirtualnych. Brak narzędzi, agentów, RAG, MCP, Spring AI. |
| AI-03 SDK, model, `maxTokens`, effort, thinking | ✅ | `anthropic-java`, `claude-sonnet-5-5` z konfiguracji, `maxTokens 512`, `OutputConfig.Effort.LOW`, `ThinkingConfigBetweenTools` tylko dla tego modelu, ręczny `JsonOutputFormat` ze schematem z kontraktu. Parametry przyjęte przez prawdziwe API. Zob. F-15. |
| AI-04 Haiku tylko do porównania | ✅ | Model konfigurowalny, bez pola `between_tools` dla innych modeli. |
| AI-05 timeout, retry, `stopReason`, błędy | ✅ | `maxRetries(0)`, timeout z konfiguracji (domyślnie 8000 ms po F-15, wpis w „Odstępstwach”), `stopReason` przed treścią, wyjątki po typach SDK, błędy w audycie. |
| AI-06 rubryka z cache | ✅ | `systemOfTextBlockParams` z `CacheControlEphemeral`, transkrypcja z breakpointem na końcu, bez znacznika czasu. |
| AI-07 transkrypcja jako dane | ✅ | Tag `<transcript>` i instrukcja ignorowania poleceń. |
| AI-08 `QuoteValidator` | ✅ | `risk/QuoteValidator`: segment istnieje, znormalizowany cytat jest w segmencie, flaga wejściowa ignorowana. Zob. F-22. |
| AI-09 brak danych z ustawień | ✅ | `CallSnapshot` ma tylko transkrypcję, test sprawdza kontakty i numery. `scenarioId` nie idzie do API. |
| AI-10 etykiety tylko do ewaluacji | ✅ | Bez zmian od BE-05. |
| CON-07 ręczny rekord odpowiedzi | ✅ | `StageHitsResponse` i test zgodności ze schematem. |
| DET-01, DET-02 | ✅ | `RiskEngine` ma nowe `decidingHits` dla `triggeredBy` (D-04) i testy. |
| BE-05 zależności pakietów | ✅ | `ai` nie zależy od `risk`; szew w `call` (`AiAnalyzer`, `AuditRecorder`); reguła ArchUnit: `audit` nie zależy od `call`, `risk`, `alerts`, `demo`. |
| CC-01…CC-03 | ✅ | Brak `synchronized`, `ReentrantLock`; kolejność blokad poprawiona (F-16) i pilnowana testem. |
| OBS-02 (AI: 3 błędy → `degraded`, sukces → `ok`) | ✅ | `AiAnalyzer`: licznik kolejnych porażek i status `ai` z komunikatem po polsku. |
| OBS-03 wpis audytu na każde wywołanie | ✅ | Model, effort, zakres segmentów, usage z zapisem cache, opóźnienie, `stopReason`, surowy wynik, walidacja każdego trafienia, poziomy przed i po, trafienia słów kluczowych; wyniki po końcu rozmowy oznaczone jako spóźnione (F-23). |
| OBS-04 koszt na `BigDecimal` z cennika z konfiguracji | ✅ | Z tokenami zapisu do cache i ceną z konfiguracji (F-19), wywołania bez usage opisane w `costNote`. |
| OBS-05 logi bez tekstu | ✅ | Logowane są tylko liczby i typy wyjątków (`AiAnalyzer`, `AuditRecorder`, `CallClassificationQueue`). |
| DAT-03 audyt po rozmowie bez alertu | ✅ | Tekst audytu w pamięci do końca rozmowy, na dysk tylko po alercie (F-17), zapis pod jedną blokadą (F-18). |
| API-02 tryb w rekordzie audytu | ✅ | `mode` w `audit_calls` i `audit_records`. Rozmowy z mockiem są oznaczone MOCK. |
| Zasada 7 (awaria nie po cichu) | ✅ | Porażki AI i błędy zapisu audytu publikują `system.status` (F-03 dalej dotyczy heartbeatu). |
| Zasada 8 (klucz tylko w środowisku) | ✅ | `ANTHROPIC_API_KEY` z `Environment`, nigdzie nie logowany. Bez klucza profil inny niż `dev` nie startuje (D-18). |
| TST-01, TST-02 testy bez prawdziwego API | ✅ | WireMock, `MockStageClassifier`; 428 testów zielone. |
| TST-03 testy MockMvc z walidatorem kontraktu | ✅ | `AuditControllerTest` dla trzech endpointów audytu. |

## 1b. Review BE-08 (tryb LIVE: odbiór audio i lokalne STT), stan `master` 2b7a83f

Zakres: pakiet `stt` (`AudioWebSocketHandler`, `AudioWebSocketConfig`, `SttSupervisor`, `SegmentDispatcher`, `VoskSttProvider`, `VoskModelHolder`, `FakeSttProvider`, `SttConfig`), jego styk z `CallService`, `KeywordDetector`, `AppProperties`, `contracts/openapi.yaml`, README i testy. Oceniane względem `CLAUDE.md` i `docs/ograniczenia-backend-java.md`. `docs/plan/plan.md` nadal oznacza BE-08 jako „do zrobienia”, a kod już jest na `master`.
Metoda: czytanie kodu. Nie uruchamiałem `./mvnw verify` (w tym środowisku nie ma `JAVA_HOME`), więc stan testów jest nieznany. Znacznik: ✅ spełnione, ⚠️ spełnione z lukami, ❌ niespełnione.

### Zgodność z regułami

| Reguła | Status | Uwagi |
|---|---|---|
| AUD-01 kontrakt audio, limit ramki 64 KB | ⚠️ | Format, `start`/`stop`/`pause`/`resume` i limit 64 KB w kontenerze (test `messagesOver64KbAreRefusedByTheContainer`, kod 1009) są. Rozmiar ramki nie jest sprawdzany (F-30). |
| AUD-02 audio nie na dysk, nie do logów | ✅ | Ramki idą do kolejki (maks. 100 ramek, 10 s) i do `Recognizer`. Logi mają tylko nazwy klas wyjątków, `SttSegment.toString` ukrywa tekst. |
| AUD-03 `SttProvider`, Vosk, model ze ścieżki, `FakeSttProvider` | ✅ | `VoskSttProvider`, `FakeSttProvider`, wybór przez `app.stt.provider`, model ładowany leniwie, backend startuje bez modelu. |
| AUD-04 wątek platformowy, brak Claude w wątku STT | ✅ | `vosk-recognizer` to wątek platformowy, wyniki przez `SegmentDispatcher` na wątki wirtualne, przepełnienie kolejki daje `degraded`. |
| AUD-05 audio nie opuszcza urządzenia, ujawnienia | ✅ | Vosk, JNA i model są w `README.md`. Testowe `awaitility` jest tylko tranzytywne. |
| AUD-06 interim tylko do słów kluczowych i UI | ✅ | `AiAnalyzer` reaguje na `FinalSegmentAdded`. Ale patrz F-27. |
| AUD-07 zgoda przed LIVE | ⚠️ | Zamknięcie 1008 jest i ma test, ale produkcyjny `PermissiveConsentChecker` zawsze zwraca `true` (F-32, odroczone do BE-09). |
| BE-02, BE-03, BE-04 | ✅ | Wątki wirtualne w executorze, własny `BinaryWebSocketHandler`, jedna rozmowa (test: drugi `start` odrzucony). |
| BE-05, TST-04 | ✅ | ArchUnit: `stt` nie zależy od `ai`/`risk`/`alerts`/`audit`/`demo`, `call` nie zależy od `stt`, brak `synchronized`. |
| CC-01, CC-03 | ⚠️ | `ReentrantLock` wszędzie. Luka: pole `provider` w `SttSupervisor` nie jest `volatile` (F-29). |
| CC-04 zadania okresowe | ✅ | `AudioWebSocketHandler.checkSilence` (`@Scheduled`, `app.stt.silence.check-interval-ms`), zegar `Clock` wstrzykiwany (naprawione: F-25). |
| OBS-01 awaria zawsze w `system.status` | ✅ | Brak modelu, błąd Vosk, przepełnienie, pauza, cisza (`NO_SOUND`) i zerwane połączenie (`CONNECTION_LOST`) publikują status (naprawione: F-25, F-31). |
| OBS-02 progi | ✅ | STT: 3 próby po 1, 2 i 4 s, potem `down`, powrót do `ok` po udanym restarcie. Audio: 10 s bez ramek lub z samymi płaskimi ramkami → `down`, dźwięk przywraca `ok` (naprawione: F-25). |
| OBS-05 logi bez treści | ✅ | W pakiecie `stt` logowane są tylko typy wyjątków. |
| API-02, zasada 6: tryb na zdarzeniu | ✅ | `LiveStatusPublisher` i `calls.start(Mode.LIVE)`. |
| CON-01, CON-02 kontrakt najpierw | ✅ | `AudioControl` i `x-websockets` (`/ws/audio`, `/ws/events`, kody zamknięcia) są w `contracts/openapi.yaml`, rekord `contracts/AudioControl` (naprawione: F-24). |
| CON-06 walidacja wejścia WebSocket | ✅ | Polecenia czyta ścisły `ObjectReader` (`FAIL_ON_UNKNOWN_PROPERTIES`) do rekordu `AudioControl`, test `AudioControlTest` sprawdza zgodność ze schematem (naprawione: F-24). |
| TST-02 testy bez prawdziwego STT | ⚠️ | `VoskSttProviderSmokeTest` uruchamia prawdziwy Vosk, gdy jest model (F-34). |
| Zasady 3, 4, 5, 8 z `CLAUDE.md` | ✅ | Brak rozłączania i dzwonienia, audio tylko w pamięci, do STT nie idą dane z ustawień, brak kluczy (Vosk ich nie potrzebuje). |

### Findingi BE-08

| ID | Waga | Zadanie | Reguła | Miejsce | Opis | Rekomendacja |
|---|---|---|---|---|---|---|
| F-24 | Średnia | BE-08 | CON-01, CON-02, CON-06 | `contracts/openapi.yaml`, `AudioWebSocketHandler.commandOf` | Kontrakt nie opisuje `/ws/audio`: nie ma schematu `AudioControl` ani rozszerzenia `x-websockets`. Polecenia (`start`, `stop`, `pause`, `resume`) i kody zamknięcia (1008, 1011) są zdefiniowane tylko w kodzie, a frontend (FE-05) nie ma z czego generować typów. | Dodać do kontraktu `AudioControl` (z `additionalProperties: false`) i `x-websockets` dla `/ws/audio` z kodami zamknięcia, potem rekord w `contracts/` i walidację wiadomości względem schematu. Zrobić przed FE-05. |
| F-25 | Średnia | BE-08 (albo BE-10, do ustalenia) | OBS-01, OBS-02, CC-04, zasada 7 | `AudioWebSocketHandler` | Nikt nie sprawdza, czy ramki nadal przychodzą. Wyciszony mikrofon, zawieszona karta dźwiękowa albo klient, który przestał wysyłać bez zamykania gniazda, zostawiają `audio = OK`, „Ochrona działa”. Architektura (tabela błędów) i OBS-02 wymagają `down` po 10 s ciszy lub braku ramek. Nie ma też zadania `@Scheduled` z `Clock`. | Zapisywać czas ostatniej ramki (`Clock`), co kilka sekund sprawdzać próg 10 s z konfiguracji, publikować `audio = DOWN` i po powrocie ramek `OK`. Pauza użytkownika nie liczy się do ciszy. Test z fałszywym zegarem. Jeśli zespół uznaje to za BE-10, wpisać to do planu. |
| F-26 | Średnia | BE-08 | DET-02, DET-03 | `KeywordDetector.detect` (`SpeakerRole.UNCLEAR`), `VoskSttProvider` (`SpeakerLabel.UNKNOWN`) | Vosk bez modelu mówców zawsze zwraca `UNKNOWN`, a słowa kluczowe zawsze `UNCLEAR`. `RiskEngine` wyklucza tylko `SENIOR` i `BACKGROUND`, więc w LIVE zdanie seniora „nie podam kodu BLIK” albo telewizor w tle liczy się jako `MONEY_REQUEST` i `PAYMENT_CHANNEL`. To dawny F-14, który miał wrócić przy BE-08. | Zdecydować i zapisać: w LIVE trafienia słów kluczowych z `UNCLEAR` albo wymagają potwierdzenia przez AI (rola z `StageHit`), albo ograniczenie trafia do „Odstępstw” i do opisu demo. Test scenariusza z zaprzeczeniem seniora. |
| F-27 | Średnia | BE-08 | zasada 2 z `CLAUDE.md` (cytat musi być w transkrypcji), DET-03, AUD-06 | `CallService.addSegment`, `CallState.segmentIdFor`, `VoskSttProvider.emitFinal` | Otwarte wymaganie z sekcji 4: słowa kluczowe działają na interim (zgodnie z DET-03), a trafienie dostaje `segId` przyszłego finala i cytuje zdanie z interim. Vosk często zmienia tekst częściowy przy finale, a `emitFinal` pomija pusty wynik bez publikacji, więc numer `sN` dostaje potem inna wypowiedź. Alert może cytować zdanie, którego nie ma w transkrypcji (albo które stoi pod cudzym `segId`), a `TranscriptExcerpt` zachowa złe sąsiedztwo. Deduplikacja po (etap, `segId`, źródło) nie pozwala, żeby final poprawił cytat. | Zdecydować (to był warunek BE-08): albo trafienie z interim jest tymczasowe i po finale sprawdzane `QuoteValidator`-em względem tekstu finala (inaczej wycofywane), albo alert powstaje dopiero z finala. Test: interim z frazą, final bez niej. |
| F-28 | Niska | BE-08 | AUD-01, kontrakt `TranscriptSegment` | `VoskSttProvider.fedBytes` | Czasy `tStartMs`/`tEndMs` liczone są od zera dla każdego nowego dostawcy. Po restarcie rozpoznawacza (OBS-02) czas w tej samej rozmowie cofa się. Odrzucone ramki przy przepełnieniu kolejki nie są wliczane, więc czas rozmija się z zegarem. | Przekazywać dostawcy przesunięcie (czas od startu rozmowy z `Clock`) albo liczyć czas po stronie `AudioWebSocketHandler`. Test restartu. |
| F-29 | Niska | BE-08 | CC-01 | `SttSupervisor.provider`, `write()` | `write()` (wątek Tomcata) czyta `provider` bez blokady i bez `volatile`, a piszą go wątki wirtualne i `stt-retry` pod blokadą. Brak gwarancji widoczności zmiany po restarcie rozpoznawacza. | Zrobić pole `volatile`. |
| F-30 | Niska | BE-08 | AUD-01 | `AudioWebSocketHandler.handleBinaryMessage` | Rozmiar ramki nie jest sprawdzany: puste, nieparzyste (rozjechane próbki 16-bit) i duże (do 64 KB) ramki idą do Vosk. | Odrzucać (zamknięcie 1008 z komunikatem po polsku) ramki o długości innej niż wielokrotność 2 bajtów, a opcjonalnie inne niż 3200 B. |
| F-31 | Średnia | BE-08 | OBS-01, zasada 7 | `AudioWebSocketHandler.finish`, `afterConnectionClosed`, `handleTransportError` | Zerwanie gniazda (Wi-Fi tabletu, zamknięta karta) kończy rozmowę, ale nie publikuje `audio = DOWN`. Status zostaje „Ochrona działa” bez żadnego źródła dźwięku. Podobnie po końcu rozmowy przy `stt = DOWN` ten status zostaje do następnego startu. Bez `stop` od klienta backend nie odróżnia awarii od zwykłego końca. | Przy zamknięciu bez wcześniejszego `stop` publikować `audio = DOWN` z komunikatem po polsku (a przy `stop` przywracać stan „bezczynny”), przy końcu rozmowy czyścić `stt`. Test zerwania gniazda. |
| F-32 | Średnia | BE-09 (odroczone) | AUD-07 | `settings/PermissiveConsentChecker` | Produkcyjny `ConsentChecker` zawsze zwraca `true`, więc każdy klient na tym originie może włączyć nasłuch LIVE bez zgody. Kod jest świadomym zaślepieniem („Replace it, do not extend it”), a test używa własnego stubu. | Zastąpić przy BE-09 (zgody z ustawień). Do tego czasu dopisać do README/„Odstępstw”, że LIVE nie wymaga zgody. |
| F-33 | Niska | BE-08 | TST-01 | `VoskSttProvider` | Logika dostawcy (łączenie interim i final, czasy, średnia pewność, dławienie kolejki, `getFinalResult` przy `stop`) jest testowana tylko ciszą z prawdziwym modelem, więc bez modelu nie jest testowana wcale. | Wydzielić rozpoznawacz za małym interfejsem i przetestować logikę na atrapie (bez Vosk i bez modelu). |
| F-34 | Niska | BE-08 | TST-02, `CLAUDE.md` („testy nie wołają prawdziwego STT”) | `VoskSttProviderSmokeTest` | Test wywołuje prawdziwy Vosk, gdy model leży na dysku (`@EnabledIf`), więc na maszynie z modelem `./mvnw verify` uruchamia natywne STT. | Oznaczyć jako test ręczny/integracyjny (`@Tag`, osobny profil) albo dopisać wyjątek do „Odstępstw”. |

### Stan poprawek średnich findingów (branch `fix/be-08-medium-findings`)

`./mvnw verify` zielone: 507 testów, 1 pominięty (test z prawdziwym Vosk, bez modelu). Wcześniej `master` nie kompilował testów (`RetentionServiceTest` nie znał parametru `Stt` w `AppProperties`), co też naprawiono.

| ID | Status | Co zrobiono |
|---|---|---|
| F-24 | ZAMKNIĘTY | `AudioControl` i `x-websockets` w `contracts/openapi.yaml`, rekord `contracts/AudioControl`, ścisły odczyt poleceń (nieznane pole lub polecenie zamyka sesję kodem 1008). Testy: `AudioControlTest`, `AudioWebSocketTest`. Typy we frontendzie powstają z generatora (`audio-control.ts`). |
| F-25 | ZAMKNIĘTY | `SilenceWatch` i `@Scheduled` `checkSilence`: 10 s bez ramek lub z samymi płaskimi ramkami (najgłośniejsza próbka poniżej 8 z 32768) daje `audio = down`, dźwięk przywraca `ok`, pauza nie jest ciszą. Konfiguracja `app.stt.silence.timeout-ms` i `check-interval-ms`. Testy: `SilenceWatchTest`, `AudioSilenceTest`. |
| F-26 | UDOKUMENTOWANY | Bez diaryzacji nie da się tego naprawić w kodzie bez decyzji zespołu. Wpis w „Odstępstwach” (`docs/ograniczenia-backend-java.md`) do potwierdzenia. Otwarta decyzja: potwierdzenie przez AI dla `MONEY_REQUEST` i `PAYMENT_CHANNEL` z rolą `UNCLEAR`. |
| F-27 | ZAMKNIĘTY | Trafienie z interim jest tymczasowe. Gdy przychodzi final tego samego `segId`, trafienia powstają od nowa z tekstu finala, a niepotwierdzone są wycofywane (`risk.update` z mniejszą liczbą etapów). Poziom nie spada (DET-05), a alert już pokazany zostaje. Testy w `CallServiceRiskTest`. Zostaje: alert pokazany z tekstu interim, którego final nie potwierdził, ma w sobie ten cytat. |
| F-31 | ZAMKNIĘTY | Zamknięcie gniazda bez `stop` i błąd transportu publikują `audio = down` („Połączenie z mikrofonem zostało przerwane”). Czyste `stop` niczego nie zmienia. Testy w `AudioWebSocketTest`. Status `stt` po końcu rozmowy zostaje taki, jaki był, do następnego startu. |
| F-32 | ODROCZONY | Zadanie BE-09 (zgody), poza zakresem tej poprawki. |

### Uwagi poza findingami

- `docs/plan/plan.md` i `docs/milestone-*.md` nie wiedzą jeszcze o BE-08, a kod (`7251d63`, scalony `2b7a83f`) jest na `master`. Do uzupełnienia po poprawkach.
- Kolejność poprawek (propozycja): F-27 i F-31 (uczciwość alertów i statusów), potem F-25, F-24 (przed FE-05), F-26 (decyzja), reszta przy okazji. F-32 przy BE-09.

## 2. Findingi odroczone (realne, ale z zadania zaplanowanego później)

| ID | Waga | Zadanie docelowe | Reguła | Miejsce | Opis | Co zrobić |
|---|---|---|---|---|---|---|
| F-03 | Średnia | BE-10 (Odporność i uczciwe statusy błędów) | OBS-01, zasada 7 | `HeartbeatService` | Heartbeat co 10 s publikuje `BACKEND = OK` bezwarunkowo i nadpisuje `DEGRADED` z `CallService`, `RetainAlertedCallHook`, `DecisionService`. Statusy awarii już istnieją (BE-04, BE-05), więc luka jest realna. | Rozwiązać w BE-10: jawny model stanu komponentu, `OK` tylko po zdjęciu awarii. |
| F-06 | Niska | WEB-01 (serwer WWW, HTTPS), deploy | API-05, OBS-05 | `application.yml` | `spring.profiles.default: dev`: start bez profilu włącza `/api/dev/emit`, DEBUG, szczegóły `health`, originy `localhost`. `deploy/` jeszcze nie istnieje. | Przy WEB-01 ustawić bezpieczny domyślny profil, `dev` jawnie w `Makefile` i `.env.example`. |

## 2a. Findingi zamknięte (naprawione, `./mvnw verify` zielone: 443 testy)

| ID | Co zrobiono |
|---|---|
| F-01 | Do `contracts/openapi.yaml` dodano `GET /api/status` (schemat `StatusResponse`) i `POST /api/dev/emit` (tylko profil `dev`). `StatusController` i `DevEmitController` implementują teraz wygenerowane interfejsy `StatusApi` i `DevApi` (importMappings w `pom.xml`). `StatusControllerTest` i `DevEmitControllerTest` sprawdzają odpowiedzi względem kontraktu. **Nadal otwarte:** wpisy do tabeli „Odstępstwa” w docs (decyzja człowieka: ręcznie pisane rekordy, enumy małymi literami, zdarzenia `call.*`, rola `audit`, dodatkowe endpointy). |
| F-02 | `QuoteNormalizerTest` czyta wspólne wektory `contracts/test-vectors/normalize.json` (14 przypadków), tak jak wersja TS. Poprawiono ścieżkę w Javadocu `QuoteNormalizer`. |
| F-04 | `EventBus` trzyma teraz alerty aktywnej rozmowy, ostatni `risk.update` i decyzje, a snapshot wysyła je po `call.started`. Po `call.ended` dane tej rozmowy znikają ze snapshotu, nowa rozmowa zaczyna z pustym stanem, a decyzja o alercie spoza bieżącej rozmowy nie jest zapamiętywana. 5 nowych testów w `EventsWebSocketTest`. |
| F-09 | `DecisionService.record` odrzuca (400) decyzję z `ignoredStages` dla zakończonej rozmowy, zanim cokolwiek zapisze i opublikuje. Decyzja bez `ignoredStages` działa jak wcześniej. Zmiana kolejności zdarzeń: `risk.update` z ignorowania etapów jest teraz przed `alert.decision`. 2 nowe testy, zaktualizowany opis 400 w kontrakcie. |
| F-15 | Domyślny timeout Claude to teraz 8000 ms, a `maxTokens` 1024. Oba są w konfiguracji (`app.claude.timeout-ms`, `app.claude.max-tokens`, zmienne `APP_CLAUDE_TIMEOUT_MS`, `APP_CLAUDE_MAX_TOKENS`, walidowane `@Positive`, w `.env.example`). Zaktualizowane AI-03, AI-05, `CLAUDE.md`, `architecture.md` i wpis w „Odstępstwach”. Testy: konfiguracja, żądanie z `max_tokens` z konfiguracji. Wariant „tylko nowe trafienia” (delta) nie został zrobiony (decyzja: wariant szybki). |
| F-16 | `FinalSegmentAdded` niesie `CallState`, a `AiAnalyzer` nie woła już `CallService.active()` spod blokady rozmowy, więc blokada slotu nie jest brana po blokadzie rozmowy. Test regresji w `AiAnalyzerTest`: `end()` trzyma blokadę slotu, gdy listener działa pod blokadą rozmowy. Test wykrywa błąd: po przywróceniu starego zachowania kończy się `TimeoutException`. |
| F-17 | `AuditService` trzyma tekst rekordów (surowy wynik i cytaty) w pamięci do końca rozmowy. Do bazy idzie od razu tylko część liczbowa, a tekst po rozmowie z alertem; po rozmowie bez alertu nie trafia na dysk w ogóle. W trakcie rozmowy `GET /api/calls/{id}/audit` pokazuje tekst z pamięci. DAT-03 i `schema.sql` zaktualizowane. |
| F-18 | Sprawdzenie „rozmowa skończona” i zapis rekordu oraz `callEnded` odbywają się pod jedną blokadą (`ReentrantLock`). Test wyścigu (100 rund) i testy tekstu w bazie. |
| F-19 | `usage` ma `cacheCreationInputTokens` (z odpowiedzi API), zapisywane w audycie (kolumna z automatyczną migracją istniejącej bazy), w kontrakcie, w cenniku (`cacheCreationPerMillionUsd`, domyślnie 2,50 USD, do sprawdzenia w cenniku) i w kosztach. `costNote` mówi, że wywołania bez usage (np. timeout) nie są wliczone. |
| F-20 | Wpis o `GET /api/calls` w „Odstępstwach”. |
| F-21 | `milestone-1.md`: opis zasady DAT-01, tabel audytu i scenariusza S1 zaktualizowany. |
| F-22 | `QuoteValidator` odrzuca cytat krótszy niż 3 znaki po normalizacji (AI-08 zaktualizowane, testy). |
| F-23 | Znaleziony w teście z prawdziwym Claude (scenariusz 02): ostatnia odpowiedź AI przyszła po zakończeniu rozmowy, więc nie została zwalidowana, ale audyt zapisał jej 10 trafień jako `validated=false` i `/api/audit/summary` pokazało `rejectedQuotes: 10` przy faktycznie 0 odrzuconych cytatach. Naprawione: `AiCallReport` i `AuditEntry` mają flagę `late` (wynik po końcu rozmowy), rekord audytu ma pole `late` (kolumna z automatyczną migracją), podsumowanie ma `lateResults`, a trafienia spóźnionych wyników nie są liczone w `rejectedQuotes` (`rejected_hits = 0`). Spóźnione wyniki zostają w statystykach czasu i kosztu, bo kosztowały. Kontrakt, `OBS-03` i testy zaktualizowane (443 testy). |

## 3. Findingi usunięte jako nadmiarowe (funkcja jeszcze nie jest zadaniem)

| Dawne ID | Dlaczego usunięty | Wymaganie przeniesione do |
|---|---|---|
| F-05 (`addHits` bez `QuoteValidator`) | `QuoteValidator` to BE-06. Dziś hity pochodzą tylko z `KeywordDetector`. | BE-06 (niżej) |
| F-07 (brak retencji, czyszczenia, `DELETE /api/data`) | To zakres BE-09 (ustawienia, retencja). | BE-09 (niżej) |
| F-08 (hit z interim cytuje segment przyszłego finala) | SCRIPTED daje tylko segmenty finalne. Interim pojawia się dopiero z STT (BE-08). | BE-08 (niżej) |
| F-12 (komunikat 409 zawsze „rozmowa już trwa”) | Dziś jest jedna przyczyna 409, więc jest to ryzyko hipotetyczne. | — |
| F-14 (słowa kluczowe ignorują mówiącego) | Scenariusz 07 ma tylko jeden etap (`PAYMENT_CHANNEL`), więc kończy na LOW i nie daje alertu. Mapowanie `speaker`→rola ma sens dopiero z diaryzacją STT. | BE-06, BE-08 (niżej) |

## 4. Wymagania na przyszłe zadania (z usuniętych findingów)

- **BE-06:** ZROBIONE: `QuoteValidator` jest bramką w `AiAnalyzer` przed `CallService.addHits`, a hit LLM bez walidacji nie wpływa na ryzyko.
- **BE-08:** zdecydować, czy alert może opierać się na hicie z segmentu interim. Jeśli final nie nadejdzie albo ma inny tekst, alert cytuje segment, którego nie ma w transkrypcji.
- **BE-09:** retencja 1–90 dni, czyszczenie przy starcie i co godzinę, `DELETE /api/data` (tabele `alerts`, `alert_segments`, `decisions` oraz `data/labels.jsonl`). `ON DELETE CASCADE` wymaga `PRAGMA foreign_keys=ON`, a w kodzie go nie znalazłem. Usuwać jawnie albo włączyć pragmę.
- **BE-07:** ZROBIONE: pakiet `audit` z polem `mode` w każdym rekordzie (zasada 6).
- **README.md:** dopisać do listy ujawnień nowe biblioteki (`networknt json-schema-validator`, `sqlite-jdbc`, `openapi-generator`, `ArchUnit`, `swagger-request-validator`).

## 5. Kolejność dalszych poprawek (propozycja)

1. Wpisy do „Odstępstw” dla pozostałych dodatkowych endpointów i zdarzeń (reszta F-01), potem F-10, F-11, F-13 przy okazji.
2. W swoich zadaniach: F-03 (BE-10), F-06 (WEB-01).
3. Do pomiaru po poprawkach: realne czasy odpowiedzi Claude (limit 8 s), `MAX_TOKENS` przy dłuższych rozmowach i koszt z zapisem cache (DC-02, DC-04 w `tasks/decyzje_do_podjecia_claude.md`).
