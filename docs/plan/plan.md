# Anioł Stróż: plan implementacji (Java 21 + Angular + Android)

Plan opiera się na trzech dokumentach: [architecture.md](../architecture.md), [ograniczenia-backend-java.md](../ograniczenia-backend-java.md) i [ograniczenia-frontend.md](../ograniczenia-frontend.md).

Każde zadanie kończy się działającą funkcjonalnością, którą da się zobaczyć albo sprawdzić poleceniem. Do każdego jest gotowy prompt dla agenta kodującego (Claude Code lub podobnego).

## Kolejność implementacji

Status: ✅ zrobione = zmergowane do `master` (wg historii git), ⏳ = brak w repozytorium. Stan na commit ce7d241. Hotfix ce7d241 dodał brakujące pliki ekranu seniora (FE-02/FE-03). Build i testy nie były uruchamiane (przegląd z kodu). Uwagi do zadań są w `tasks/backend-review-findings.md` i `tasks/frontend-review-findings.md`.

Numer to kolejność, w jakiej warto zaczynać zadania. W kolumnie „Ścieżka” jest osoba, która zwykle je robi, więc zadania z różnych ścieżek mogą iść równolegle. Zadanie zaczynamy dopiero wtedy, gdy wszystko z kolumny „Wymaga” jest już zmergowane.

| # | ID | Zadanie | Ścieżka | Wymaga | Status |
|---|---|---|---|---|---|
| 1 | BE-01 | Fundament repozytorium, `CLAUDE.md`, `/api/status` | A | — | ✅ zrobione |
| 2 | CT-01 | Kontrakty JSON Schema, rekordy Javy, typy TS i zod, wektory normalizacji | A | 1 | ✅ zrobione |
| 3 | EV-01 | Rubryka etapów oszustwa (prompt systemowy) | D | — | ✅ zrobione |
| 4 | EV-02 | 12 syntetycznych scenariuszy | D | 2 | ✅ zrobione |
| 5 | BE-02 | Kanał zdarzeń `/ws/events` | B | 2 | ✅ zrobione |
| 6 | FE-01 | Szkielet Angulara, kanał zdarzeń w UI, znaczek trybu | C | 2, 5 | ✅ zrobione |
| 7 | BE-03 | Tryb SCRIPTED: rozmowa odtwarzana z pliku | B | 5, 4 (wystarczy 1 scenariusz) | ✅ zrobione |
| 8 | BE-04 | Słowa kluczowe, silnik ryzyka, szablony, alert | A | 7 | ✅ zrobione |
| 9 | BE-05 | Decyzje seniora i rodziny | B | 8 | ✅ zrobione |
| 10 | FE-02 | Ekran seniora: stan ochrony | C | 6 | ✅ zrobione (uwaga: FF-13) |
| 11 | FE-03 | Ekran seniora: alert, głos, trzy przyciski | C | 10, 8, 9 | ✅ zrobione (uwaga: FF-12) |
| | | **Kamień milowy M1: scenariusz SCRIPTED kończy się alertem na ekranie seniora (bez AI)** | | | ✅ osiągnięty wg kodu (ce7d241), nie zweryfikowany uruchomieniem; uwagi FF-02, FF-12, FF-13 w `tasks/frontend-review-findings.md` |
| 12 | BE-06 | Claude: etapy oszustwa, walidacja cytatów, tryb MOCK | A | 8, 3 | ⏳ do zrobienia |
| 13 | BE-07 | Log audytu AI | A | 12 | ⏳ do zrobienia |
| 14 | FE-04 | Panel rodziny | D | 6, 8, 9 | ⏳ do zrobienia |
| | | **M2: alert z dowodami od AI widoczny u seniora i rodziny** | | | |
| 15 | BE-08 | Tryb LIVE: odbiór audio i lokalne STT (Vosk) | B | 7 | ⏳ do zrobienia |
| 16 | WEB-01 | Serwer WWW: HTTPS, reverse proxy, nagłówki bezpieczeństwa | C | 6, 5 | ⏳ do zrobienia |
| 17 | FE-05 | Mikrofon i transkrypcja na żywo | C | 15, 16 | ⏳ do zrobienia |
| | | **M3: mówienie do tabletu daje alert na żywo** | | | |
| 18 | BE-09 | Ustawienia, zgody, czułość, retencja | B | 8 | ⏳ do zrobienia |
| 19 | FE-06 | Kreator konfiguracji i zgód | D | 18 | ⏳ do zrobienia |
| 20 | BE-10 | Odporność i uczciwe statusy błędów | B | 12, 15 | ⏳ do zrobienia |
| 21 | FE-07 | Ekran audytu i wybór scenariusza demo | D | 13 | ⏳ do zrobienia |
| 22 | EV-04 | Nagrania demo zespołu | D | 7 | ⏳ do zrobienia |
| 23 | BE-11 | Tryb REPLAY: nagranie przez lokalne STT (Vosk) i prawdziwe AI | B | 15, 22 | ⏳ do zrobienia |
| 24 | BE-12 | Runner ewaluacji: słowa kluczowe vs AI | A | 12, 4 | ⏳ do zrobienia |
| 25 | EV-03 | Ewaluacja, mocki, tabela do PDF | A | 24 | ⏳ do zrobienia |
| | | **M4: demo gotowe (LIVE, REPLAY, audyt, liczby do PDF)** | | | |
| 26 | FE-08 | Dostępność, wygląd i zrzuty ekranów | C | 11, 14 | ⏳ do zrobienia |
| 27 | AND-01 | Android: aplikacja seniora w trybie kiosku (Capacitor) | C | 17, 26 | ⏳ do zrobienia |
| 28 | AND-02 | (opcjonalnie) Android: powiadomienia push dla rodziny | D + B | 27, 9 | ⏳ do zrobienia |
| 29 | BE-13 | (opcjonalnie) SMS do rodziny | B | 9, 18 | ⏳ do zrobienia |
| 30 | FE-09 | (opcjonalnie) Wersja angielska UI dla jury | D | 26 | ⏳ do zrobienia |

## Ścieżki dla 4 osób

- **A, backend AI:** BE-01, CT-01, BE-04, BE-06, BE-07, BE-12, EV-03
- **B, backend zdarzenia i audio:** BE-02, BE-03, BE-05, BE-08, BE-09, BE-10, BE-11, BE-13
- **C, frontend senior, serwer WWW i Android:** FE-01, FE-02, FE-03, WEB-01, FE-05, FE-08, AND-01
- **D, frontend rodzina, audyt i treści:** EV-01, EV-02, FE-04, FE-06, FE-07, EV-04, AND-02, FE-09

Dla zespołu trzyosobowego połączcie ścieżki A i B w jedną ścieżkę backendu, a D rozdzielcie między pozostałe osoby.

## Orientacyjny harmonogram (start 23:00)

| Godzina | Cel |
|---|---|
| 23:00–01:00 | Zadania 1–6: repozytorium, kontrakty, kanał zdarzeń, szkielet UI, rubryka |
| 01:00–05:00 | Zadania 7–11: **M1** |
| 05:00–10:00 | Zadania 12–17: **M2** i **M3** |
| 10:00–15:00 | Zadania 18–25: **M4** |
| 15:00–19:00 | Zadania 26–27, opcjonalne tylko gdy M4 działa; nagranie filmu |
| 19:00–21:00 | PDF (10 slajdów), opis, lista ujawnień, wysłanie zgłoszenia przed 21:00 |

## Zasady wspólne (po BE-01 są zapisane w `CLAUDE.md`)

- AI zwraca tylko dowody (etap + dosłowny cytat). Poziom ryzyka, teksty alertów i akcje są deterministyczne.
- System nigdy nie rozłącza rozmowy, nie dzwoni i nie blokuje numerów.
- Audio nigdy nie trafia na dysk. Transkrypcja zostaje w pamięci, chyba że rozmowa wywołała alert.
- Każdy ekran i każde zdarzenie niesie tryb pracy: LIVE / REPLAY / SCRIPTED / MOCK.
- Klucze API są tylko w zmiennych środowiskowych.
- Kontrakt między frontendem a backendem jest tylko w `contracts/openapi.yaml`.
