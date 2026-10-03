# Decyzje do podjęcia: pierwszy test z prawdziwym Claude

Wynik pierwszego testu BE-06 z prawdziwym API (rozwiązuje część D-17 z `tasks/decyzje-do-podjecia.md`). Test: scenariusz `02-fake-police-paraphrase`, tryb SCRIPTED, prędkość 3x, profil `dev`, `master` po merge BE-06 i FE-04 (ba81fcd), model `claude-sonnet-5-5`, `effort=low`.

Co zostało sprawdzone, a czego nie:
- Sprawdzone: log backendu (`AiAnalyzer`), `GET /api/alerts`, jedno proste wywołanie `curl` do API.
- Nie sprawdzone: wygląd ekranu seniora i panelu rodziny (cytaty, znaczek AI, status AI). Nie widziałem przeglądarki.
- Test jednorazowy, jedna rozmowa, jedno połączenie sieciowe. Liczby czasu to pomiar z jednego przebiegu, nie statystyka.

## Status po poprawkach (findingi F-15 i F-19)

- **DC-01 (limit czasu):** zdecydowane. Domyślnie 8000 ms, konfigurowalne (`APP_CLAUDE_TIMEOUT_MS`), wpis w „Odstępstwach” (AI-05). Do zmierzenia na więcej rozmowach, czy wystarcza.
- **DC-02 (`maxTokens`):** zdecydowane wariantem szybkim. Domyślnie 1024, konfigurowalne (`APP_CLAUDE_MAX_TOKENS`), wpis w „Odstępstwach” (AI-03). Wariant „tylko nowe trafienia” nie został zrobiony, więc przy bardzo długich rozmowach `MAX_TOKENS` jest nadal możliwe.
- **DC-04 (cache transkrypcji):** nadal do pomiaru. Audyt zapisuje już tokeny zapisu do cache (F-19), więc koszt transkrypcji w cache da się teraz zmierzyć.
- Pozostałe punkty bez zmian.

## 1. Wyniki

### 1.1 Limit 2500 ms (domyślny, AI-05): wszystko kończy się `TIMEOUT`

| Zakres segmentów | Czas | Wynik |
|---|---|---|
| s1 | 3619 ms | `TIMEOUT`, 0 tokenów |
| s1–s6 | 2512 ms | `TIMEOUT` |
| s1–s8 | 2514 ms | `TIMEOUT` |
| s1–s11 | 2512 ms | `TIMEOUT` |
| s1–s14 | 2510 ms | `TIMEOUT` |

Skutek: scenariusz 02 (napisany bez słów kluczowych) nie dał żadnego alertu, bo wykryć go mogło tylko AI.

Diagnostyka: proste zapytanie (16 tokenów, bez schematu i bez rubryki) przez `curl` do `https://api.anthropic.com/v1/messages` zwraca HTTP 200 w około 1,4 s (3 próby). Klucz jest ważny, a połączenie z API działa. Realne zapytanie jest większe (rubryka około 3,5 tys. tokenów, structured output, `thinking: between_tools`), więc przekracza limit.

### 1.2 Limit 8000 ms (`APP_CLAUDE_TIMEOUT_MS=8000`): wywołania działają

| Zakres | Czas | Tokeny (nowe / z cache / wyjście) | Trafienia / ważne | Wynik |
|---|---|---|---|---|
| s1 | 3944 ms | 69 / 3553 / 12 | 0 / 0 | `end_turn` |
| s1–s6 | 2542 ms | 69 / 3815 / 231 | 3 / 3 | `end_turn` |
| s1–s9 | 3456 ms | 69 / 3553 / 425 | 6 / 6 | `end_turn` |
| s1–s13 | 3675 ms | 69 / 3553 / 512 | 0 / 0 | `max_tokens` (`MAX_TOKENS`) |

Alert z tej rozmowy: poziom `high`, szablon `high-general`, `triggeredBy: both`. Etapy: `AUTHORITY_CLAIM` (s3, AI), `URGENT_THREAT` (s5, AI), `MONEY_REQUEST` (s8, słowa kluczowe). Czyli AI wykryło groźbę i podanie się za funkcjonariusza w parafrazie bez słów kluczowych.

### 1.3 Pozostałe obserwacje

- Kształt żądania został przyjęty przez API (`effort=low`, `thinking: between_tools`, `maxTokens 512`, schemat odpowiedzi z kontraktu): `error=null`, `stopReason=end_turn`. To zamyka część ryzyka D-17.
- Cache: do tokenów z cache liczy się rubryka (około 3,5 tys. tokenów), a transkrypcja za każdym razem jest liczona jako nowe wejście (tu 69 tokenów, rozmowa jest krótka). Zgodne z D-21.
- Szacunek kosztu jednego wywołania z tabeli: około 0,001 USD (cache) plus 0,002–0,005 USD (wyjście 231–512 tokenów), czyli rząd 0,003–0,006 USD. To obliczenie z cennika z `docs/architecture.md`, nie pomiar z rachunku.
- `.env` nie jest wczytywany przez Spring. Klucz trafił do procesu przez załadowanie `.env` do środowiska powłoki przed startem (`ANTHROPIC_API_KEY`). W logu nie było ostrzeżenia o mocku, więc użyty został `ClaudeStageClassifier`.

## 2. Decyzje do podjęcia

| ID | Waga | Temat | Fakty z testu | Opcje | Rekomendacja |
|---|---|---|---|---|---|
| DC-01 | Wysoka | Limit czasu wywołania 2500 ms (AI-05) | Przy 2500 ms 5 z 5 wywołań kończy się `TIMEOUT`. Przy 8000 ms czasy to 2,5–3,9 s, więc większość i tak przekracza 2,5 s. Bez poprawki w trybie z prawdziwym AI alert z samego AI praktycznie się nie pojawi. | (a) Podnieść limit do 6–8 s i zmienić AI-05 (wpis w „Odstępstwach”). (b) Zostawić 2500 ms i skrócić zapytanie (krótsza rubryka, mniej tekstu, niższy `maxTokens`). (c) Podnieść limit tylko dla pierwszego wywołania w rozmowie. | Zacząć od (a) jako tymczasowego, zmierzyć rozkład czasów na więcej rozmowach, potem zdecydować o (b). Budżet z `architecture.md` („alert w 3–4 s od zdania”) trzeba wtedy skorygować w PDF o zmierzone liczby. |
| DC-02 | Wysoka | `maxTokens 512` i `MAX_TOKENS` | Wyjście rośnie z liczbą trafień: 12, 231, 425, potem 512 i ucięcie. Każde wywołanie zwraca wszystkie trafienia od początku rozmowy, więc w dłuższej rozmowie przekroczenie jest pewne. Ucięte wywołanie nic nie wnosi (0 trafień), a liczy się jako porażka do progu 3 błędów z rzędu. | (a) Prosić model tylko o nowe trafienia od ostatniego zakresu, resztę trzyma kod. (b) Podnieść `maxTokens` (np. 1024), kosztem czasu i ceny. (c) Zostawić. | (a), bo skraca i wyjście, i czas, a stan etapów i tak trzyma deterministyczny kod. To zmiana kontraktu odpowiedzi (`StageHits`) i promptu. Alternatywnie (b) jako szybkie obejście do demo. Wymaga zmiany AI-03 (parametr), więc decyzja człowieka. |
| DC-03 | Średnia | Zachowanie po `MAX_TOKENS` i po `TIMEOUT` | W teście `MAX_TOKENS` zostawił 0 trafień z zakresu s1–s13, a wcześniej znalezione trafienia (6) zostają w stanie rozmowy. Po 3 takich wynikach z rzędu status `ai` ma przejść na `degraded` (OBS-02). Nie sprawdziłem, co wtedy pokazują ekrany. | Obecne zachowanie jest zgodne ze specyfikacją. | Sprawdzić w UI: komunikat „Podstawowa ochrona (bez AI)” u seniora i status w panelu rodziny po trzech porażkach z rzędu (FE-02, FE-04). |
| DC-04 | Średnia | Cache transkrypcji (D-21) | Z cache czytana jest tylko rubryka. Przy krótkiej rozmowie (69 tokenów nowych) różnica jest mała, przy 10–20 minutach transkrypcja urośnie do tysięcy tokenów liczonych jako nowe wejście przy każdym wywołaniu. | (a) Zostawić. (b) Jeden blok na segment albo breakpoint na poprzednim końcu transkrypcji, żeby poprzedni prefiks czytał się z cache. | Zmierzyć na rozmowie 10+ minut (scenariusz 08 „powolne oszustwo”) i dopiero zdecydować. |
| DC-05 | Niska | Ładowanie `.env` | Backend nie wczytuje `.env`, a README mówi o kopiowaniu `.env.example` do `.env`. Ktoś, kto tylko zrobi kopię, dostanie po cichu mocka (w `dev`) i rozmowy oznaczone MOCK. | (a) Opisać w README wymóg eksportu zmiennych (skrypt startowy). (b) Wczytywanie `.env` przez `spring.config.import` w profilu `dev`. | (a) w README i skrypt startowy. Pamiętać o zasadzie 8: klucz tylko w środowisku, nigdy w repozytorium. |
| DC-06 | Niska | Scenariusze bez mocków | Mocki istnieją tylko dla 01, 02, 03. Przy kluczu użyty jest prawdziwy Claude dla wszystkich, ale tryb MOCK (bez klucza) zwraca puste odpowiedzi dla pozostałych 9 (D-22). | Dopisać przy ewaluacji (BE-12, TST-05). | Bez zmian teraz. |

## 3. Co sprawdzić w kolejnym teście

1. Wygląd ekranu seniora i panelu rodziny po alercie z AI: cytaty z podświetleniem, etykieta „AI i słowa kluczowe”, głos czytający `shortText` i `advice`.
2. Scenariusze 06 (telewizor w tle) i 07 (senior odmawia podania kodu): czy AI poprawnie oznacza rolę (`background`, `senior`) i czy nie podnosi poziomu fałszywie.
3. Scenariusz 08 (powolne oszustwo): czas, `MAX_TOKENS` i koszt przy długiej transkrypcji.
4. Trzy porażki z rzędu (np. z zepsutym kluczem): czy status `degraded` jest widoczny na obu ekranach i wraca na `ok` po pierwszym sukcesie.
5. Zapisy rozkładu czasów (p50/p95) z 5–10 rozmów, do liczb w PDF (OBS-03, po zadaniu BE-07 z logiem audytu).
