# Review backendu: findingi (po walidacji względem `docs/plan/plan.md`)

Stan: `master`, commit ca9d7db. Źródła reguł: `CLAUDE.md`, `docs/ograniczenia-backend-java.md`, `docs/architecture.md`, `docs/plan/plan.md`.

Uwagi:
- `./mvnw verify` nie został uruchomiony (brak `java` w PATH). Wnioski pochodzą z czytania kodu.
- Walidacja: finding zostaje aktualny, jeśli dotyczy zadania już zrobionego (BE-01…BE-05, CT-01, EV-01, EV-02, FE-01). Jeśli dotyczy funkcji z jeszcze niezrobionego zadania, jest przeniesiony do „Wymagania na przyszłe zadania”.

## 1. Findingi aktualne (dotyczą zrobionych zadań)

| ID | Waga | Zadanie | Reguła | Miejsce | Opis | Rekomendacja |
|---|---|---|---|---|---|---|
| F-10 | Niska | BE-02, CT-01 | TST-03, CON-03 | testy | Walidator OpenAPI jest teraz w testach alertów, demo, statusu i `/api/dev/emit`. Zdarzenia `/ws/events` nie są walidowane względem `components/schemas`. Brak testu rekordy↔schematy (obiecanego w `contracts/package-info`). | Dodać walidację zdarzeń przez `networknt` i test rekordy↔schematy. |
| F-11 | Niska | BE-01, CT-01 | dokumentacja | `CLAUDE.md`, `contracts/package-info`, `docs/` | `CLAUDE.md` mówi „bez `paths`”, a `openapi.yaml` ma `paths`. `contracts/package-info` odwołuje się do nieistniejących `contracts/schemas`. Plan nazywa CT-01 „Kontrakty JSON Schema”, a kontrakt to OpenAPI. Dokumenty `ograniczenia-*` linkują do nieistniejących plików. | Ujednolicić dokumenty. |
| F-13 | Niska | BE-02 | CC-02 | `EventBus.publish`, `register` | Wysyłka poza blokadą, a `register` trzyma blokadę podczas snapshotu: nowy klient może dostać zdarzenie dwa razy albo w złej kolejności, wbrew komentarzowi „snapshot, then live events”. | Poprawić komentarz albo kolejność. |

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

- **BE-06:** `QuoteValidator` ma być bramką przed `CallService.addHits`. Hit z `source=LLM` bez walidacji ma być odrzucany (zasada 2, AI-08). Poprawić Javadoc `risk/package-info`, który już mówi o walidatorze cytatów. Rozważyć mapowanie etykiety mówiącego na `SpeakerRole` dla słów kluczowych (DET-02).
- **BE-08:** zdecydować, czy alert może opierać się na hicie z segmentu interim. Jeśli final nie nadejdzie albo ma inny tekst, alert cytuje segment, którego nie ma w transkrypcji.
- **BE-09:** retencja 1–90 dni, czyszczenie przy starcie i co godzinę, `DELETE /api/data` (tabele `alerts`, `alert_segments`, `decisions` oraz `data/labels.jsonl`). `ON DELETE CASCADE` wymaga `PRAGMA foreign_keys=ON`, a w kodzie go nie znalazłem. Usuwać jawnie albo włączyć pragmę.
- **BE-07:** pakiet `audit` z polem `mode` w każdym rekordzie (zasada 6).
- **README.md:** dopisać do listy ujawnień nowe biblioteki (`networknt json-schema-validator`, `sqlite-jdbc`, `openapi-generator`, `ArchUnit`, `swagger-request-validator`).

## 5. Kolejność dalszych poprawek (propozycja)

1. Wpisy do „Odstępstw” (część F-01), potem F-10, F-11, F-13 przy okazji.
2. W swoich zadaniach: F-03 (BE-10), F-06 (WEB-01).
