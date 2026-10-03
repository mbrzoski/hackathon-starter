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
