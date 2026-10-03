# Review backendu: findingi (po walidacji względem `docs/plan/plan.md`)

Stan: `master`, commit c48d1a1 (po BE-06 i BE-07). Źródła reguł: `CLAUDE.md`, `docs/ograniczenia-backend-java.md`, `docs/architecture.md`, `docs/plan/plan.md`.

Uwagi:
- `./mvnw verify` na `master` (c48d1a1): 428 testów, 0 błędów (JDK 21). Findingi BE-06 i BE-07 pochodzą z czytania kodu; F-16 i F-18 nie zostały odtworzone uruchomieniem.
- Walidacja: finding zostaje aktualny, jeśli dotyczy zadania już zrobionego (BE-01…BE-07, CT-01, EV-01, EV-02, FE-01…FE-04). Jeśli dotyczy funkcji z jeszcze niezrobionego zadania, jest przeniesiony do „Wymagania na przyszłe zadania”.

## 1. Findingi aktualne (dotyczą zrobionych zadań)

| ID | Waga | Zadanie | Reguła | Miejsce | Opis | Rekomendacja |
|---|---|---|---|---|---|---|
| F-10 | Niska | BE-02, CT-01 | TST-03, CON-03 | testy | Walidator OpenAPI jest teraz w testach alertów, demo, statusu i `/api/dev/emit`. Zdarzenia `/ws/events` nie są walidowane względem `components/schemas`. Brak testu rekordy↔schematy (obiecanego w `contracts/package-info`). | Dodać walidację zdarzeń przez `networknt` i test rekordy↔schematy. |
| F-11 | Niska | BE-01, CT-01 | dokumentacja | `CLAUDE.md`, `contracts/package-info`, `docs/` | `CLAUDE.md` mówi „bez `paths`”, a `openapi.yaml` ma `paths`. `contracts/package-info` odwołuje się do nieistniejących `contracts/schemas`. Plan nazywa CT-01 „Kontrakty JSON Schema”, a kontrakt to OpenAPI. Dokumenty `ograniczenia-*` linkują do nieistniejących plików. | Ujednolicić dokumenty. |
| F-13 | Niska | BE-02 | CC-02 | `EventBus.publish`, `register` | Wysyłka poza blokadą, a `register` trzyma blokadę podczas snapshotu: nowy klient może dostać zdarzenie dwa razy albo w złej kolejności, wbrew komentarzowi „snapshot, then live events”. | Poprawić komentarz albo kolejność. |
| F-15 | Wysoka | BE-06 | AI-05, AI-03 | `AppProperties.Claude` (domyślnie 2500 ms), `ClaudeStageClassifier` (`MAX_TOKENS = 512`) | Pierwszy test z prawdziwym API (`tasks/decyzje_do_podjecia_claude.md`): przy limicie 2500 ms 5 z 5 wywołań kończy się `TIMEOUT` (realne czasy 2,5–3,9 s), więc z domyślną konfiguracją warstwa AI w trybie z kluczem praktycznie nie działa, a po 3 porażkach z rzędu każda rozmowa pokazuje „Analiza AI jest niedostępna”. Przy limicie 8 s odpowiedź jest ucięta przez `maxTokens 512` (`MAX_TOKENS`) już przy 13 segmentach, bo każde wywołanie zwraca wszystkie trafienia od początku rozmowy. Kod jest zgodny z regułą, ale reguła nie zgadza się z pomiarem. | Decyzja człowieka (DC-01, DC-02 w `tasks/decyzje_do_podjecia_claude.md`): podnieść limit i zmienić AI-05 i AI-03 z wpisem w „Odstępstwach” albo skrócić zapytanie i prosić model tylko o nowe trafienia. |
| F-16 | Wysoka | BE-06 | CC-01, CC-03 (kolejność blokad opisana w `CallService`) | `CallService.addSegment` → `announce(FinalSegmentAdded)` → `AiAnalyzer.onFinalSegment` → `queueFor` → `activeCall` → `CallService.active()` | `addSegment` trzyma blokadę rozmowy, a listener przy pierwszym finalnym segmencie rozmowy bierze blokadę slotu (`active()`). `end()` bierze najpierw blokadę slotu, potem rozmowy. Odwrócona kolejność dwóch blokad daje możliwy deadlock, gdy rozmowa kończy się w chwili pierwszego finalnego segmentu. Dokumentacja `CallService` mówi, że kod z blokadą rozmowy nigdy nie bierze blokady slotu. Okno jest wąskie (tylko pierwszy segment każdej rozmowy), więc łatwo je przeoczyć w testach. | Nie brać blokady slotu z listenera: przekazać `CallState` w zdarzeniu albo zbudować kolejkę poza blokadą rozmowy. Dodać test współbieżny (`end()` równolegle do pierwszego segmentu). |
| F-17 | Wysoka | BE-07 | zasada 4 (CLAUDE.md), DAT-01, DAT-03 | `AuditService.record`, `clearText`, `schema.sql` (`raw_output`, `hits_json`, `keyword_hits_json`) | Audyt zapisuje na dysk (SQLite) cytaty i surowy wynik AI w trakcie każdej rozmowy, także tej, która nie wywoła alertu. Po rozmowie bez alertu pola są zerowane (`UPDATE … NULL`), ale SQLite nie nadpisuje zwolnionych stron (brak `PRAGMA secure_delete`, brak `VACUUM`), więc tekst może zostać w pliku `aniol.db` i w WAL. Zasada brzmi: transkrypcja zostaje tylko w pamięci, chyba że rozmowa wywołała alert. Autor opisał to w Javadocu jako świadomy kompromis (sprzątanie po awarii przy starcie), ale to odstępstwo od zasady, którego nie wpisano. | Trzymać tekst audytu w pamięci do końca rozmowy i zapisać go tylko dla rozmowy z alertem (liczby zapisywać od razu) albo włączyć `PRAGMA secure_delete=ON`. W obu przypadkach wpis do „Odstępstw” lub decyzja człowieka. |
| F-18 | Średnia | BE-07 | DAT-03, CC-01 | `AuditService.record` (`endedWithoutAlert`, potem `INSERT`) | Sprawdzenie „rozmowa już skończona bez alertu” i zapis rekordu to dwa osobne zapytania. Gdy rozmowa kończy się między nimi (`callEnded` i `clearText` wykonują się na innym wątku), rekord zapisuje się z pełnym tekstem i `text_cleared = 0`, a sprzątanie przy starcie obejmuje tylko rozmowy bez `ended_at`. Tekst zostaje w bazie. Pula Hikari z jednym połączeniem szereguje pojedyncze zapytania, ale nie sekwencję sprawdzenie plus zapis. | Zapis i sprawdzenie w jednej transakcji albo `INSERT … SELECT` z warunkiem. Po zapisie powtórzyć sprawdzenie i wyczyścić tekst. Test współbieżny. |
| F-19 | Średnia | BE-07 | OBS-03, OBS-04 | `ClassifierResult.Usage`, `ClaudeStageClassifier.fromMessage`, `AuditStatistics.cost` | Zapisujemy tokeny wejścia, odczytu z cache i wyjścia, ale nie tokeny zapisu do cache (`cache_creation_input_tokens`, to pole jest w odpowiedzi API). Blok transkrypcji ma `cache_control` i rośnie z każdym wywołaniem, więc jego tokeny trafiają głównie do zapisu cache, a nie do `input_tokens`: w teście `inputTokens` stało na 69 niezależnie od długości rozmowy. Średni koszt w `/api/audit/summary` jest przez to zaniżony (zapis cache kosztuje więcej niż zwykłe wejście), choć `costNote` mówi o „koszcie wyliczonym z usage”. Wywołania z `TIMEOUT` mogą być rozliczone po stronie API, a w kosztach liczą się jako brak usage. | Dodać do `Usage` i do audytu tokeny zapisu do cache oraz cenę zapisu do konfiguracji cennika. Opisać w `costNote`, że wywołania bez usage nie są wliczone. Zdecydować o D-21 (cache transkrypcji) po pomiarze. |
| F-20 | Niska | BE-07 | API-01 | `AuditController`, `contracts/openapi.yaml` | `GET /api/calls` (lista rozmów w audycie) nie ma wzmianki w sekcji 6.5 architektury ani w „Odstępstwach” (`GET /api/calls/{id}/audit` i `/api/audit/summary` mają uzasadnienie w architekturze i w OBS-04). To dalszy ciąg F-01 (wpisy do „Odstępstw”). | Dopisać do „Odstępstw” razem z pozostałymi dodatkowymi endpointami. |
| F-21 | Niska | BE-07 | dokumentacja | `docs/milestone-1.md` (S1: „Baza: bez zmian”, sekcja 3) | Po BE-07 rozmowa bez alertu zostawia w bazie wiersze `audit_calls` i `audit_records` (liczby, a tekst tymczasowo, zob. F-17). Opis schematu i scenariusza S1 w `milestone-1.md` są nieaktualne. | Dopisać tabele audytu i poprawić S1. |
| F-22 | Informacja | BE-06 | AI-08 | `QuoteValidator.isValid` | Cytat jest uznany za poprawny, jeśli po normalizacji jest podciągiem segmentu, więc bardzo krótki cytat (np. jedno popularne słowo) przejdzie. Zgodne z literą AI-08, ale model może przypisać etap do segmentu, którego dowód jest błahy. | Rozważyć minimalną długość cytatu (np. 3 znaki albo 1 słowo) jako zabezpieczenie. Decyzja człowieka. |

## 1a. Zgodność BE-06 i BE-07 z regułami (stan na c48d1a1)

| Reguła | Status | Uwagi |
|---|---|---|
| AI-01 AI zwraca tylko dowody | ✅ | `StageHitsResponse`: etap, `segment_id`, cytat, `speaker_role`. Poziom, teksty i decyzje w kodzie. |
| AI-02 jedno wywołanie w locie na rozmowę | ✅ | `CallClassificationQueue` z flagą „dirty”, `ReentrantLock`, executor na wątkach wirtualnych. Brak narzędzi, agentów, RAG, MCP, Spring AI. |
| AI-03 SDK, model, `maxTokens`, effort, thinking | ✅ | `anthropic-java`, `claude-sonnet-5-5` z konfiguracji, `maxTokens 512`, `OutputConfig.Effort.LOW`, `ThinkingConfigBetweenTools` tylko dla tego modelu, ręczny `JsonOutputFormat` ze schematem z kontraktu. Parametry przyjęte przez prawdziwe API. Zob. F-15. |
| AI-04 Haiku tylko do porównania | ✅ | Model konfigurowalny, bez pola `between_tools` dla innych modeli. |
| AI-05 timeout, retry, `stopReason`, błędy | ⚠️ | `maxRetries(0)`, timeout z konfiguracji, `stopReason` sprawdzany przed treścią, wyjątki po typach SDK (nie po tekście), błędy zapisane w audycie. Zob. F-15 (limit 2500 ms jest za ciasny). |
| AI-06 rubryka z cache | ✅ | `systemOfTextBlockParams` z `CacheControlEphemeral`, transkrypcja z breakpointem na końcu, bez znacznika czasu. |
| AI-07 transkrypcja jako dane | ✅ | Tag `<transcript>` i instrukcja ignorowania poleceń. |
| AI-08 `QuoteValidator` | ✅ | `risk/QuoteValidator`: segment istnieje, znormalizowany cytat jest w segmencie, flaga wejściowa ignorowana. Zob. F-22. |
| AI-09 brak danych z ustawień | ✅ | `CallSnapshot` ma tylko transkrypcję, test sprawdza kontakty i numery. `scenarioId` nie idzie do API. |
| AI-10 etykiety tylko do ewaluacji | ✅ | Bez zmian od BE-05. |
| CON-07 ręczny rekord odpowiedzi | ✅ | `StageHitsResponse` i test zgodności ze schematem. |
| DET-01, DET-02 | ✅ | `RiskEngine` ma nowe `decidingHits` dla `triggeredBy` (D-04) i testy. |
| BE-05 zależności pakietów | ✅ | `ai` nie zależy od `risk`; szew w `call` (`AiAnalyzer`, `AuditRecorder`); reguła ArchUnit: `audit` nie zależy od `call`, `risk`, `alerts`, `demo`. |
| CC-01…CC-03 | ⚠️ | Brak `synchronized`, `ReentrantLock` wszędzie. Odwrócona kolejność blokad: F-16. |
| OBS-02 (AI: 3 błędy → `degraded`, sukces → `ok`) | ✅ | `AiAnalyzer`: licznik kolejnych porażek i status `ai` z komunikatem po polsku. |
| OBS-03 wpis audytu na każde wywołanie | ✅ | Model, effort, zakres segmentów, usage, opóźnienie, `stopReason`, surowy wynik, walidacja każdego trafienia, poziom przed i po, trafienia słów kluczowych. Brakuje tokenów zapisu cache: F-19. |
| OBS-04 koszt na `BigDecimal` z cennika z konfiguracji | ⚠️ | Jest (`AuditStatistics`, `app.audit.pricing`, `/api/audit/summary`, mocki wyłączone, percentyle metodą najbliższej pozycji). Zaniżony przez brakujące tokeny zapisu cache: F-19. |
| OBS-05 logi bez tekstu | ✅ | Logowane są tylko liczby i typy wyjątków (`AiAnalyzer`, `AuditRecorder`, `CallClassificationQueue`). |
| DAT-03 audyt po rozmowie bez alertu | ⚠️ | Pola tekstowe czyszczone, ale tekst zapisuje się na dysk w trakcie rozmowy i może zostać w stronach SQLite (F-17), a sprawdzenie jest podatne na wyścig (F-18). |
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

## 2a. Findingi zamknięte (naprawione, `./mvnw verify` zielone: 281 testów)

| ID | Co zrobiono |
|---|---|
| F-01 | Do `contracts/openapi.yaml` dodano `GET /api/status` (schemat `StatusResponse`) i `POST /api/dev/emit` (tylko profil `dev`). `StatusController` i `DevEmitController` implementują teraz wygenerowane interfejsy `StatusApi` i `DevApi` (importMappings w `pom.xml`). `StatusControllerTest` i `DevEmitControllerTest` sprawdzają odpowiedzi względem kontraktu. **Nadal otwarte:** wpisy do tabeli „Odstępstwa” w docs (decyzja człowieka: ręcznie pisane rekordy, enumy małymi literami, zdarzenia `call.*`, rola `audit`, dodatkowe endpointy). |
| F-02 | `QuoteNormalizerTest` czyta wspólne wektory `contracts/test-vectors/normalize.json` (14 przypadków), tak jak wersja TS. Poprawiono ścieżkę w Javadocu `QuoteNormalizer`. |
| F-04 | `EventBus` trzyma teraz alerty aktywnej rozmowy, ostatni `risk.update` i decyzje, a snapshot wysyła je po `call.started`. Po `call.ended` dane tej rozmowy znikają ze snapshotu, nowa rozmowa zaczyna z pustym stanem, a decyzja o alercie spoza bieżącej rozmowy nie jest zapamiętywana. 5 nowych testów w `EventsWebSocketTest`. |
| F-09 | `DecisionService.record` odrzuca (400) decyzję z `ignoredStages` dla zakończonej rozmowy, zanim cokolwiek zapisze i opublikuje. Decyzja bez `ignoredStages` działa jak wcześniej. Zmiana kolejności zdarzeń: `risk.update` z ignorowania etapów jest teraz przed `alert.decision`. 2 nowe testy, zaktualizowany opis 400 w kontrakcie. |

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

1. F-16 (możliwy deadlock) oraz F-17 i F-18 (tekst rozmów w bazie audytu), bo dotyczą bezpieczeństwa i prywatności.
2. F-15 (limit czasu i `maxTokens`, decyzja człowieka) i F-19 (koszt), bo wpływają na liczby do PDF.
3. Wpisy do „Odstępstw” (F-01, F-20), potem F-10, F-11, F-13, F-21, F-22 przy okazji.
4. W swoich zadaniach: F-03 (BE-10), F-06 (WEB-01).
