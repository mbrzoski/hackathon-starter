# Milestone 1: stan backendu i schemat bazy

Tag: `milestone-1`. Stan na commit ce7d241 (`master`, po hotfixie FE-02 + FE-03).

Dokument opisuje, co moim zdaniem powinno już działać, i jak działa baza danych. Opis powstał z czytania kodu. Nie uruchamiałem aplikacji ani `./mvnw verify` (brak `java` w PATH), więc nic tu nie jest potwierdzone testem na żywo.

## 1. Status M1 wg `docs/plan/plan.md`

Cel M1 to „scenariusz SCRIPTED kończy się alertem na ekranie seniora (bez AI)”.

| Zadanie | Status |
|---|---|
| BE-01, CT-01, EV-01, EV-02, BE-02, FE-01, BE-03, BE-04, BE-05 | ✅ zmergowane |
| FE-02 (stan ochrony u seniora), FE-03 (alert, głos, trzy przyciski) | ✅ zmergowane (54ea35a + hotfix ce7d241) |

Strona backendowa M1 jest kompletna i nie zmieniła się od ca9d7db (kolejne commity dotyczą tylko `frontend/`).

**M1 jest osiągnięty wg kodu.** Hotfix ce7d241 dodał brakujące pliki ekranu seniora (`audio.service`, `decision-outbox`, `senior-status`, `settings.store`, `voice.service`, `alert-view`, `decision-buttons`, `decision-outcome`, `evidence-quotes`, `status-panel`, `voice-readout`, style) oraz testy (`senior.spec`, `senior-status.spec`, `decision-outbox.spec`). Nie uruchamiałem builda, testów ani aplikacji, więc „działa” oznacza „kod jest kompletny i spójny z kontraktem”. Ograniczenia widoczne w kodzie: `AudioService` to `NoopAudioService` (brak mikrofonu do zadania BE-08/FE-05), a `SettingsStore.firstContact` jest zawsze `null` (do BE-09), więc przycisk „Zadzwoń do…” pokazuje „Brak zapisanego numeru”.

Co robi ekran seniora: widoki start („Włącz ochronę”), stan ochrony (5 stanów wg priorytetu FE-06), alert dla poziomu MEDIUM i HIGH (tekst z szablonu backendu, czytany głosem `pl-PL`, „Dlaczego?” z cytatami), trzy decyzje (`POST /api/alerts/{id}/decision` przez `DecisionOutbox` z ponawianiem) oraz „Rozmowa zakończona”. Decyzję seniora z innego urządzenia rozpoznaje po zdarzeniu `alert.decision`. Znane uwagi: głos czyta tylko `shortText` bez `advice` (FF-12), lokalna pauza nie zmienia zielonego stanu (FF-13), a po ponownym połączeniu w trakcie rozmowy stan jest czyszczony (FF-02) — szczegóły w `tasks/frontend-review-findings.md`.

## 2. Co powinno już działać (backend)

| Funkcja | Jak sprawdzić | Zadanie |
|---|---|---|
| Start aplikacji z profilem `dev` | `make run-backend` | BE-01 |
| `GET /api/status` | zwraca tryb, wersję, czas startu | BE-01 |
| Kanał `/ws/events?role=senior\|family\|audit` | po połączeniu przychodzi snapshot, potem zdarzenia. Zła rola: zamknięcie z kodem 1008. | BE-02 |
| Heartbeat | co 10 s `system.status` dla `backend` | BE-02 |
| `POST /api/dev/emit` (tylko `dev`) | wstrzykuje dowolne zdarzenie do kanału | BE-02 |
| `GET /api/demo/scenarios` | lista 12 scenariuszy | BE-03 |
| `POST /api/demo/replay` | body `{scenarioId, mode: "SCRIPTED", speed}`. 202 start, 404 nieznany scenariusz, 409 trwa inna rozmowa. | BE-03 |
| `POST /api/demo/stop` | przerywa odtwarzanie i kończy rozmowę | BE-03 |
| Rozmowa SCRIPTED | zdarzenia `call.started`, `transcript.segment` (same finalne), `call.ended` | BE-03 |
| Słowa kluczowe | każdy segment przechodzi przez `KeywordDetector` | BE-04 |
| Silnik ryzyka | `risk.update` przy każdej zmianie zbioru trafień. Poziom tylko rośnie. | BE-04 |
| Alerty | po wejściu na MEDIUM albo HIGH jedno `alert.created` na poziom, tekst z szablonu | BE-04 |
| Zapis alertu | po końcu rozmowy z alertem zapis do SQLite. Bez alertu transkrypcja jest kasowana. | BE-04 |
| `POST /api/alerts/{id}/decision` | senior: `hung_up`, `called_trusted`, `false_alarm`. Rodzina: `confirmed_scam`, `false_alarm` (+ `ignoredStages`). 201, 400 zły aktor, 404 nieznany alert. | BE-05 |
| `GET /api/alerts?limit=` | alerty aktywnej rozmowy i zapisane, z decyzjami | BE-05 |
| Etykiety do ewaluacji | `false_alarm` i `confirmed_scam` dopisują linię do `backend/data/labels.jsonl` | BE-05 |

Czego jeszcze nie ma: Claude i `QuoteValidator`, audio i STT, ustawienia i zgody, audyt, retencja, tryby REPLAY i MOCK. `POST /api/demo/replay` z innym trybem niż SCRIPTED zwraca 400.

Przykładowy przebieg do ręcznego sprawdzenia: `make run-backend`, połączyć się z `/ws/events?role=senior`, wywołać `POST /api/demo/replay` ze scenariuszem `01-fake-police-classic` i `speed: 5`. Oczekiwane: segmenty, `risk.update` rosnące do `high`, alert `high-secrecy-money`, `call.ended` z `hadAlert: true`. W wersji z samymi słowami kluczowymi scenariusze bez słów ze słownika (np. 02) nie dadzą alertu. To zamierzone: to przypadek dla AI.

## 3. Baza danych

SQLite, plik `aniol.db` w katalogu roboczym (zmienna `APP_DB_FILE`), jedno połączenie (`hikari.maximum-pool-size: 1`). Schemat z `backend/src/main/resources/schema.sql` tworzy się przy każdym starcie (`CREATE TABLE IF NOT EXISTS`). Testy używają bazy w pamięci. Plik `*.db` jest w `.gitignore`.

Zasada nadrzędna (DAT-01): do bazy trafiają tylko rozmowy, które wywołały alert. Rozmowa bez alertu nie zostawia żadnego śladu. Transkrypcja i trafienia trzymane są tylko w pamięci (`CallState`), dopóki rozmowa trwa.

### Tabele

```
alerts (1) ──────< alert_segments (N)    klucz złożony (alert_id, seg_id), FK ON DELETE CASCADE
alerts (1) ·······< decisions (N)         powiązanie tylko logiczne po alert_id, bez FK
```

| Tabela | Klucz | Zawartość |
|---|---|---|
| `alerts` | `alert_id` (UUID) | `call_id`, `level`, `template_id`, `short_text`, `advice`, `triggered_by`, `created_at`, `mode`, `stages_json` (lista trafień z cytatami jako JSON) |
| `alert_segments` | (`alert_id`, `seg_id`) | wycinek transkrypcji: `t_start_ms`, `t_end_ms`, `text`, `speaker`, `cited` (1 = cytowany, 0 = kontekst ±2) |
| `decisions` | `id` autoinkrement | `alert_id`, `actor`, `decision`, `decided_at`, `mode`, `ignored_stages` (nazwy po przecinku) |

Konwencja zapisu wartości: `level`, `triggered_by`, `actor`, `decision` małymi literami, `mode` wielkimi (`SCRIPTED`), `ignored_stages` nazwami enuma (`SECRECY_DEMAND,ISOLATION`), czas jako tekst ISO-8601.

Zależności i ograniczenia, o których warto wiedzieć:
- `decisions` nie ma klucza obcego celowo: alert aktywnej rozmowy dostaje decyzję zanim trafi do `alerts` (zapis dopiero przy końcu rozmowy).
- `ON DELETE CASCADE` w `alert_segments` zadziała tylko, jeśli w SQLite włączono `PRAGMA foreign_keys=ON`. W kodzie nie znalazłem takiego ustawienia, więc kaskada prawdopodobnie jest nieaktywna. Przy implementacji `DELETE /api/data` trzeba usuwać tabele jawnie.
- Ten sam segment może wystąpić w kilku alertach jednej rozmowy: każdy alert ma własną kopię swoich segmentów (klucz złożony na to pozwala).
- Poza bazą jest jeszcze `backend/data/labels.jsonl`. Zawiera `alertId`, `callId`, `mode`, listę etapów, decyzję, aktora i czas. Nie ma w nim cytatów ani tekstu rozmowy.
- Nie ma jeszcze retencji ani czyszczenia (zadanie BE-09). Wszystko, co zapisane, zostaje.

### Mini scenariusze

**S1. Rozmowa bez alertu** (np. scenariusz 04 „prawdziwy wnuk”).
1. `call.started`, segmenty trafiają do pamięci, najwyżej poziom LOW (jedna lub zero oznak).
2. Koniec rozmowy: `DiscardTranscriptHook` czyści transkrypcję.
3. Baza: bez zmian. `call.ended` ma `hadAlert: false`.

**S2. Klasyczny fałszywy policjant** (scenariusz 01, same słowa kluczowe).
1. Po segmencie z „policja” i „nikomu nie mów” poziom rośnie do MEDIUM → jeden alert `medium-general`.
2. Po segmencie z „wypłać” poziom rośnie do HIGH → drugi alert `high-secrecy-money`. Alerty są dwa, po jednym na poziom, a poziom nigdy nie spada.
3. Koniec rozmowy: `RetainAlertedCallHook` dla każdego alertu wybiera segmenty cytowane przez jego trafienia plus 2 segmenty kontekstu z każdej strony.
4. Baza: 2 wiersze w `alerts`, po N wierszy w `alert_segments` dla każdego (z `cited` 1 lub 0). W pamięci zostaje tylko suma tych segmentów, reszta jest skasowana. Zapis każdego alertu jest transakcją.

**S3. Senior decyduje w trakcie rozmowy.**
1. Alert istnieje tylko w pamięci aktywnej rozmowy. `POST /api/alerts/{id}/decision` z `senior/hung_up` znajduje go przez `LiveCallAccess`.
2. Baza: wiersz w `decisions` z `alert_id` alertu, którego jeszcze nie ma w `alerts`. Publikowane jest `alert.decision`.
3. Po końcu rozmowy alert dochodzi do `alerts`. Powiązanie działa po `alert_id`.
4. Gdyby zapis alertu się nie udał, decyzja zostaje osierocona, a UI dostaje `system.status` `degraded`.

**S4. Rodzina po rozmowie oznacza fałszywy alarm.**
1. Alert jest już w `alerts`, więc `DecisionService` czyta go z bazy.
2. Baza: kolejny wiersz w `decisions` (`family`, `false_alarm`, `mode` skopiowany z alertu). Alert może mieć wiele decyzji, kolejność wg `id`.
3. Dopisanie linii do `labels.jsonl` (to decyzje typu `false_alarm` i `confirmed_scam`). Gdy zapis pliku się nie uda, decyzja zostaje ważna i pojawia się status `degraded`.
4. `GET /api/alerts` zwraca alert z listą decyzji.

**S5. Rodzina każe „nie liczyć tego etapu” w trakcie rozmowy.**
1. Decyzja `family` z `ignoredStages: [SECRECY_DEMAND]`. W `decisions` zapisuje się `ignored_stages = "SECRECY_DEMAND"`.
2. `CallService.ignoreStages` przelicza poziom bez tego etapu i publikuje `risk.update`. To jedyny wyjątek od zasady, że poziom tylko rośnie. Już utworzone alerty zostają.
3. Jeśli rozmowa już się skończyła, etap nie jest ignorowany, ale decyzja i tak się zapisuje (finding F-09 z `tasks/backend-review-findings.md`).

**S6. Odświeżenie strony w trakcie rozmowy.**
1. Nowy klient `/ws/events` dostaje snapshot: ostatnie statusy komponentów, aktywna rozmowa i ostatni alert.
2. Snapshot nie zawiera poziomu ryzyka ani decyzji, a po końcu rozmowy nadal może zawierać stary alert (finding F-04). Frontend (FF-02) czyści stan przy każdym `call.started`, więc po ponownym połączeniu w trakcie rozmowy ekran seniora traci decyzje i pokaże ten sam alert jako niezdecydowany. `GET /api/alerts` mógłby odtworzyć listę z decyzjami, ale frontend jeszcze go nie woła.

## 4. Znane luki M1

Pełna lista w `tasks/backend-review-findings.md`. Najważniejsze dla M1: F-01 (endpointy poza kontraktem), F-03 (heartbeat nadpisuje awarię), F-04 (snapshot po odświeżeniu), F-06 (domyślny profil `dev`).

Frontend (`tasks/frontend-review-findings.md`): FF-02 (czyszczenie stanu przy ponownym `call.started`, razem z backendowym F-04 daje alert wracający jako niezdecydowany), FF-12 (głos bez `advice`), FF-13 (pauza nie zmienia stanu na „wstrzymana”), FF-05 (trzy aplikacje poza „Odstępstwami”).
