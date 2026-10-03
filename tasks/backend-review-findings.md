# Review backendu: findingi (po walidacji względem `docs/plan/plan.md`)

Stan: `master`, commit ca9d7db. Źródła reguł: `CLAUDE.md`, `docs/ograniczenia-backend-java.md`, `docs/architecture.md`, `docs/plan/plan.md`.

Uwagi:
- `./mvnw verify` nie został uruchomiony (brak `java` w PATH). Wnioski pochodzą z czytania kodu.
- Walidacja: finding zostaje aktualny, jeśli dotyczy zadania już zrobionego (BE-01…BE-05, CT-01, EV-01, EV-02, FE-01). Jeśli dotyczy funkcji z jeszcze niezrobionego zadania, jest przeniesiony do „Wymagania na przyszłe zadania”.

## 1. Findingi aktualne (dotyczą zrobionych zadań)

| ID | Waga | Zadanie | Reguła | Miejsce | Opis | Rekomendacja |
|---|---|---|---|---|---|---|
| F-01 | Średnia | BE-01, BE-02, BE-03 | CON-02, API-01 | `contracts/openapi.yaml`, `StatusController`, `DevEmitController` | `GET /api/status` (BE-01) i `POST /api/dev/emit` (BE-02) nie ma w `paths`. Odstępstwa od docs (ręcznie pisane rekordy zamiast generowanych: CON-03/BE-06; enumy małymi literami na wire: CON-04; zdarzenia `call.started`/`call.ended`; rola `audit`; `GET /api/alerts`, `GET /api/demo/scenarios`, `POST /api/demo/stop` ponad sekcję 6.5 architektury) nie są wpisane do tabeli „Odstępstwa”. | Dodać oba endpointy do kontraktu. Wpisać odstępstwa do „Odstępstw” albo zgłosić człowiekowi. |
| F-02 | Średnia | CT-01 | CON-05, TST-03 | `QuoteNormalizerTest`, `QuoteNormalizer` | CT-01 obejmuje wektory normalizacji, ale test Javy nie czyta `contracts/test-vectors/normalize.json`, ma własne `@CsvSource`. Javadoc wskazuje nieistniejący `contracts/ts/normalize.ts` (TS jest w `frontend/src/app/shared/normalize.ts`). Zgodność Java↔TS nie jest wymuszona. | Test parametryzowany wczytuje wspólny plik. Poprawić ścieżkę w Javadocu. |
| F-04 | Średnia | BE-02 | API-03 | `EventBus.snapshot` | `lastAlert` nie jest czyszczony po `call.ended`, więc odświeżona strona dostaje stary alert bez aktywnej rozmowy. Snapshot nie zawiera ostatniego `risk.update` ani `alert.decision`. Potwierdzone przez `senior.ts` (FE-02/FE-03): ekran seniora zamyka alert po zdarzeniu `alert.decision`, więc po odświeżeniu strony, bez decyzji w snapshocie, ten sam alert wróci jako niezdecydowany. Stary alert po końcu rozmowy frontend sam maskuje (widok „zakończona”). | Naprawić teraz (FE-02/FE-03 już zmergowane): czyścić `lastAlert` przy końcu tej rozmowy, dodać do snapshotu poziom i decyzje, dopisać test. |
| F-09 | Niska | BE-05 | zasada 7 | `DecisionService.record` | `ignoredStages` dla zakończonej rozmowy: `ignoreStages` zwraca `false`, wynik jest ignorowany. API zwraca 201 i zapisuje `ignored_stages`, a nic się nie zmienia. | Zwrócić 409/400 albo opublikować status. |
| F-10 | Niska | BE-02, CT-01 | TST-03, CON-03 | testy | Walidator OpenAPI jest tylko w `AlertsControllerTest` i `DemoControllerTest`. Zdarzenia `/ws/events` nie są walidowane względem `components/schemas`. Brak testu rekordy↔schematy (obiecanego w `contracts/package-info`). | Dodać walidację zdarzeń przez `networknt` i test rekordy↔schematy. |
| F-11 | Niska | BE-01, CT-01 | dokumentacja | `CLAUDE.md`, `contracts/package-info`, `docs/` | `CLAUDE.md` mówi „bez `paths`”, a `openapi.yaml` ma `paths`. `contracts/package-info` odwołuje się do nieistniejących `contracts/schemas`. Plan nazywa CT-01 „Kontrakty JSON Schema”, a kontrakt to OpenAPI. Dokumenty `ograniczenia-*` linkują do nieistniejących plików. | Ujednolicić dokumenty. |
| F-13 | Niska | BE-02 | CC-02 | `EventBus.publish`, `register` | Wysyłka poza blokadą, a `register` trzyma blokadę podczas snapshotu: nowy klient może dostać zdarzenie dwa razy albo w złej kolejności, wbrew komentarzowi „snapshot, then live events”. | Poprawić komentarz albo kolejność. |

## 2. Findingi odroczone (realne, ale z zadania zaplanowanego później)

| ID | Waga | Zadanie docelowe | Reguła | Miejsce | Opis | Co zrobić |
|---|---|---|---|---|---|---|
| F-03 | Średnia | BE-10 (Odporność i uczciwe statusy błędów) | OBS-01, zasada 7 | `HeartbeatService` | Heartbeat co 10 s publikuje `BACKEND = OK` bezwarunkowo i nadpisuje `DEGRADED` z `CallService`, `RetainAlertedCallHook`, `DecisionService`. Statusy awarii już istnieją (BE-04, BE-05), więc luka jest realna. | Rozwiązać w BE-10: jawny model stanu komponentu, `OK` tylko po zdjęciu awarii. |
| F-06 | Niska | WEB-01 (serwer WWW, HTTPS), deploy | API-05, OBS-05 | `application.yml` | `spring.profiles.default: dev`: start bez profilu włącza `/api/dev/emit`, DEBUG, szczegóły `health`, originy `localhost`. `deploy/` jeszcze nie istnieje. | Przy WEB-01 ustawić bezpieczny domyślny profil, `dev` jawnie w `Makefile` i `.env.example`. |

## 3. Findingi usunięte jako nadmiarowe (funkcja jeszcze nie jest zadaniem)

| Dawne ID | Dlaczego usunięty | Wymaganie przeniesione do |
|---|---|---|
| F-05 (`addHits` bez `QuoteValidator`) | `QuoteValidator` to BE-06. Dziś hity pochodzą tylko z `KeywordDetector`. | BE-06 (niżej) |
| F-07 (brak retencji, czyszczenia, `DELETE /api/data`) | To zakres BE-09 (ustawienia, retencja). | BE-09 (niżej) |
| F-08 (hit z interim cytuje segment przyszłego finala) | SCRIPTED daje tylko segmenty finalne. Interim pojawia się dopiero z STT (BE-08). | BE-08 (niżej) |
| F-12 (komunikat 409 zawsze „rozmowa już trwa”) | Dziś jest jedna przyczyna 409, więc jest to ryzyko hipotetyczne. | — |
| F-14 (słowa kluczowe ignorują mówiącego) | Scenariusz 07 ma tylko jeden etap (`PAYMENT_CHANNEL`), więc kończy na LOW i nie daje alertu. Mapowanie `speaker`→rola ma sens dopiero z diaryzacją STT. | BE-06, BE-08 (niżej) |

## 4. Wymagania na przyszłe zadania (z usuniętych findingów)

- **BE-06:** `QuoteValidator` ma być bramką przed `CallService.addHits`. Hit z `source=LLM` bez walidacji ma być odrzucany (zasada 2, AI-08). Poprawić Javadoc `risk/package-info`, który już mówi o walidatorze cytatów. Rozważyć mapowanie etykiety mówiącego na `SpeakerRole` dla słów kluczowych (DET-02).
- **BE-08:** zdecydować, czy alert może opierać się na hicie z segmentu interim. Jeśli final nie nadejdzie albo ma inny tekst, alert cytuje segment, którego nie ma w transkrypcji.
- **BE-09:** retencja 1–90 dni, czyszczenie przy starcie i co godzinę, `DELETE /api/data` (tabele `alerts`, `alert_segments`, `decisions` oraz `data/labels.jsonl`). `ON DELETE CASCADE` wymaga `PRAGMA foreign_keys=ON`, a w kodzie go nie znalazłem. Usuwać jawnie albo włączyć pragmę.
- **BE-07:** pakiet `audit` z polem `mode` w każdym rekordzie (zasada 6).
- **README.md:** dopisać do listy ujawnień nowe biblioteki (`networknt json-schema-validator`, `sqlite-jdbc`, `openapi-generator`, `ArchUnit`, `swagger-request-validator`).

## 5. Kolejność poprawek (propozycja)

1. Teraz: F-04 oraz brakujące pliki FE-02/FE-03 (zob. `docs/plan/plan.md`).
2. Teraz, małe: F-01, F-02.
3. Przy okazji: F-09, F-10, F-11, F-13.
4. W swoich zadaniach: F-03 (BE-10), F-06 (WEB-01).
