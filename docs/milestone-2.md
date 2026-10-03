# Milestone 2: alert z dowodami od AI u seniora i rodziny

Tag: `milestone-2`. Stan na commit 4cfd095 (`master`, po scaleniu `feature/findings_V3`).

Dokument opisuje, co powinno już działać po M2, i jak zmieniła się baza względem [milestone-1.md](milestone-1.md). Opis powstał z czytania kodu, planu, findingów i `git log`. Nie uruchamiałem aplikacji, `./mvnw verify` ani `npm test`. Liczby testów (443 backend) pochodzą z `tasks/backend-review-findings.md`, a wynik testów na żywo z Claude (MOCK i prawdziwe API) z `tasks/uc-01-wyniki-testow.md` i `tasks/decyzje_do_podjecia_claude.md`.

## 1. Status M2 wg `docs/plan/plan.md`

Cel M2 to „alert z dowodami od AI widoczny u seniora i rodziny”.

| Zadanie | Status |
|---|---|
| BE-06 (Claude: etapy oszustwa, walidacja cytatów, tryb MOCK) | ✅ zmergowane (b46b586, 628cbbc) |
| BE-07 (log audytu AI) | ✅ zmergowane (scalone w c48d1a1) |
| FE-04 (panel rodziny) | ✅ zmergowane (1b3fdf4, 70966fb) |
| Poprawki findingów | ✅ backend F-01, F-02, F-04, F-09, F-15…F-23 (443 testy), frontend FF-01, FF-02, FF-05, FF-06, FF-08, FF-09, FF-11…FF-14, FF-16…FF-18 |
| Poza planem M2: część BE-09 (retencja DAT-02, `DELETE /api/data`) | ⚠️ zrobione tylko to; ustawienia, zgody i czułość dalej do zrobienia |

**M2 jest osiągnięty wg kodu.** Dla scenariusza bez słów ze słownika (02, parafraza) alert powstaje wyłącznie z trafień AI, każde po walidacji `QuoteValidator`. Ten sam alert dostają ekran seniora, widok Nasłuch i panel rodziny (UC-01 w `tasks/uc-01-wyniki-testow.md`, tam w trybie MOCK). Test z prawdziwym Claude dał finding F-23, już naprawiony.

Zastrzeżenia: build i testy nie były uruchomione przy pisaniu tego dokumentu. Z testów UC-01 zostają znane błędy poza zakresem M2 (fałszywy alarm w scenariuszu 06 ze słów kluczowych, poziom średni zamiast wysokiego w scenariuszu 12, niespójne stany awarii na Nasłuchu, przeskakujący znaczek trybu), opisane w `tasks/uc-01-wyniki-testow.md`.

## 2. Co doszło od M1

### Backend

| Funkcja | Jak sprawdzić | Zadanie |
|---|---|---|
| Klasyfikator etapów (`StageClassifier`) | `ClaudeStageClassifier` (SDK `anthropic-java`, `claude-sonnet-5-5`, effort `LOW`, structured output, `maxRetries 0`, timeout 8000 ms, `maxTokens` 1024) albo `MockStageClassifier` bez klucza | BE-06 |
| Tryb MOCK | gotowe odpowiedzi z `resources/mocks/` dla scenariuszy 01, 02 i 03; bez `ANTHROPIC_API_KEY` w profilu `dev` | BE-06 |
| Kolejka wywołań | jedno wywołanie w locie na rozmowę, flaga „dirty”, `ReentrantLock`, wątki wirtualne (`CallClassificationQueue`) | BE-06 |
| `QuoteValidator` | cytat musi być w segmencie (po normalizacji), min. 3 znaki; odrzucony nie wpływa na ryzyko | BE-06 |
| Awaria AI | 3 kolejne błędy → `system.status` `ai` `degraded`, sukces → `ok`; działają słowa kluczowe | BE-06 |
| `GET /api/calls`, `GET /api/calls/{id}/audit`, `GET /api/audit/summary` | lista rozmów, wpisy audytu, podsumowanie (czasy, tokeny, koszt z cennika, `rejectedQuotes`, `lateResults`) | BE-07 |
| Audyt na każde wywołanie AI | model, effort, zakres segmentów, usage z zapisem cache, opóźnienie, `stopReason`, surowy wynik, walidacja każdego trafienia, poziomy przed i po, `mode` | BE-07 |
| `GET /api/status`, `POST /api/dev/emit` w kontrakcie | interfejsy `StatusApi`, `DevApi` z generatora | F-01 |
| Snapshot `/ws/events` | alerty bieżącej rozmowy, ostatni `risk.update`, decyzje | F-04 |
| Retencja i `DELETE /api/data` | `app.retention.days` 1–90 (domyślnie 30), czyszczenie przy starcie i co godzinę, 204 po usunięciu wszystkiego | BE-09 (część) |

Czego jeszcze nie ma: audio i STT (BE-08), ustawienia, zgody i czułość (BE-09, `SettingsStore` w UI wciąż pusty), odporność statusów (BE-10: F-03), tryby LIVE i REPLAY, `QuoteValidator` dla segmentów interim (BE-08), runner ewaluacji, HTTPS i `deploy/` (WEB-01).

### Frontend

Panel rodziny (`/family`): karta alertu z poziomem słowami, trybem i źródłem wykrycia, oś etapów, cytaty z podświetleniem po normalizacji (CON-05), fragment rozmowy ±2 segmenty (tylko dla trwającej rozmowy, FF-15), przyciski decyzji z „nie licz tego etapu” (tylko trwająca rozmowa), historia z `GET /api/alerts` wczytywana ponownie po odnowieniu połączenia, sygnał dźwiękowy i tytuł karty tylko przy nowym alercie HIGH, pasek stanu systemu. Trzy aplikacje (`senior`, `listen`, `family`) z jednej bazy. Ekran seniora czyta teraz także `advice`, pokazuje „decyzja niezapisana” i nie czyści stanu przy powtórnym `call.started`. Testy: `family.spec`, `alert-notifier.spec`, `quote-match.spec`, `listen.spec`, rozszerzone `senior.spec`, `events.service.spec`, `decision-outbox.spec`.

Ekrany `/setup` i `/audit` to dalej placeholdery (FE-06, FE-07).

## 3. Baza danych: zmiany względem M1

Tabele `alerts`, `alert_segments`, `decisions` bez zmian. Doszły dwie tabele audytu (BE-07):

```
audit_calls (1) ······< audit_records (N)    powiązanie logiczne po call_id, bez FK
```

| Tabela | Klucz | Zawartość |
|---|---|---|
| `audit_calls` | `call_id` | `mode`, `started_at`, `ended_at`, `max_level`, `had_alert` |
| `audit_records` | `id` autoinkrement | wiersz na wywołanie AI: `mode`, `recorded_at`, `segment_range`, `model`, `effort`, tokeny (wejście, odczyt i zapis cache, wyjście), `latency_ms`, `stop_reason`, `error`, `raw_output`, `hits_json`, `keyword_hits_json`, `level_before`, `level_after`, `hit_count`, `rejected_hits`, `text_cleared`, `late` |

Zasady zapisu:
- Tekst audytu (`raw_output`, cytaty w `hits_json` i `keyword_hits_json`) jest w pamięci do końca rozmowy (F-17). Do bazy idzie od razu część liczbowa. Po rozmowie z alertem dochodzi tekst (`text_cleared = 0`), po rozmowie bez alertu tekst nie trafia na dysk w ogóle (`text_cleared = 1`, `raw_output` NULL). W trakcie rozmowy `GET /api/calls/{id}/audit` pokazuje tekst z pamięci.
- Sprawdzenie „rozmowa skończona”, zapis rekordu i `callEnded` są pod jedną blokadą (F-18).
- `late = 1`: wynik AI, który przyszedł po końcu rozmowy (F-23). Nie jest walidowany, ma `rejected_hits = 0`, nie liczy się do `rejectedQuotes`, ale liczy się do czasu i kosztu.
- Kolumny `cache_creation_input_tokens` i `late` dodano migracją, więc istniejąca baza `aniol.db` zostaje (F-19, F-23).
- Retencja kasuje wiersze starsze niż `app.retention.days` z `alerts`, `alert_segments`, `decisions`, `audit_records` i `audit_calls` w jednej transakcji. Tabele są usuwane jawnie, więc brak `PRAGMA foreign_keys=ON` nie przeszkadza (rozważany w M1). `DELETE /api/data` czyści te same pięć tabel.
- Uwaga: `DELETE /api/data` ani retencja nie dotykają `backend/data/labels.jsonl` (w kodzie `RetentionService` nie ma o nim wzmianki), choć wymaganie z BE-09 mówiło o usunięciu także etykiet. Do decyzji przy BE-09.

### Mini scenariusze (nowe i zmienione)

**S7. Alert z dowodów AI** (scenariusz 02, parafraza, brak słów ze słownika).
1. Segmenty trafiają do pamięci. `CallClassificationQueue` wysyła do klasyfikatora przyrastającą transkrypcję (tylko tekst, bez ustawień i numerów, AI-09).
2. Odpowiedź (etap, `segment_id`, cytat, rola) przechodzi przez `QuoteValidator`. Zwalidowane trafienia idą do `CallService.addHits`, `RiskEngine` podnosi poziom, powstaje `risk.update` i `alert.created` z szablonu. Odrzuconych cytatów nie widać w ryzyku, ale są w audycie.
3. Każde wywołanie to wiersz audytu (w pamięci tekst, w bazie liczby). Po końcu rozmowy z alertem tekst audytu zapisuje się do `audit_records`.

**S8. Awaria AI w trakcie rozmowy.** Trzy kolejne błędy (timeout, `stopReason` inny niż `end_turn`, wyjątek SDK) dają `system.status` `ai` `degraded` z komunikatem po polsku. Rozmowa działa dalej na słowach kluczowych. Pierwszy sukces przywraca `ok`. Błędy są w audycie.

**S9. Wynik AI po końcu rozmowy.** Ostatnia odpowiedź przychodzi po `call.ended`: rekord ma `late = 1`, trafienia nie są walidowane ani liczone jako odrzucone, `/api/audit/summary` pokazuje `lateResults`.

**S10. Rodzina kasuje dane.** `DELETE /api/data` → 204, pięć tabel puste. Etykiety w `labels.jsonl` zostają (patrz uwaga wyżej).

## 4. Znane luki M2

Backend (`tasks/backend-review-findings.md`): otwarte F-10, F-11, F-13 (niskie), wpisy do „Odstępstw” (reszta F-01). Odroczone: F-03 (heartbeat nadpisuje awarię, BE-10), F-06 (domyślny profil `dev`, WEB-01).

Frontend (`tasks/frontend-review-findings.md`): otwarte FF-15 (kontekst i czas w historii alertów, wymaga zmiany backendu i kontraktu). Odroczone: FF-03, FF-07 (FE-05), FF-04 (BE-10), FF-19 (BE-08, BE-10), FF-10 (FE-08).

Do pomiaru: realne czasy odpowiedzi Claude (limit 8 s), `MAX_TOKENS` przy dłuższych rozmowach i koszt z zapisem cache (DC-02, DC-04 w `tasks/decyzje_do_podjecia_claude.md`). Decyzje D-01…D-13 w `tasks/decyzje-do-podjecia.md` czekają na zespół.
