# UC-01: wyniki testów

Data: 2026-10-03. Stan: `master` po commicie ba81fcd (BE-06, FE-04). Przypadek użycia jest opisany w [docs/use-cases.md](../docs/use-cases.md).

Środowisko: backend `make run-backend` (profil `dev`, AI bez klucza, więc gotowe odpowiedzi MOCK) oraz trzy aplikacje z `npm start` (senior :4201, listen :4202, family :4203). Testy uruchamiał Claude:

- skrypt łączył się z `/ws/events` przez proxy każdej aplikacji i sprawdzał każdą wiadomość względem kontraktu;
- Chrome bez okna miał otwarte wszystkie trzy aplikacje naraz (senior 390×844, Nasłuch 800×1280, rodzina 390×844);
- dodatkowo: wywołania API, odczyt SQLite i testy automatyczne.

## Wyniki

| # | Przypadek | Wynik |
|---|---|---|
| 1.0 | UC-01: scenariusz 01, trzy aplikacje naraz | ✅ Ten sam alert na `/senior`, `/listen` i `/family`, różnica 0–3 ms |
| 1.1 | Alert u seniora | ✅ Tekst z szablonu, źródło, „Dlaczego?”, trzy decyzje |
| 1.3 | Negatywne 04, 05, 07 | ✅ Brak alertu |
| 1.3 | Negatywne 06 (telewizor w tle) | ❌ Fałszywy alarm wysoki ze słów kluczowych (błąd 2) |
| 1.4 / 2.0 | 02 (parafraza) | ✅ Alert od AI (MOCK), poziom średni, potem wysoki |
| 1.5 | 12 (pułapka 112) | ⚠️ Rada poprawna („Rozłącz się i odczekaj minutę…”), poziom średni zamiast oczekiwanego wysokiego (błąd 5) |
| 1.6 | Decyzja seniora | ✅ Zapisana, a u rodziny „Mama wybrała: rozłączam się · 19:28” |
| 1.7 | Koniec rozmowy | ✅ „Rozmowa zakończona” u seniora, Nasłuch wraca do „Czekam na rozmowę” |
| 1.8 | Stany awarii u seniora i rodziny | ✅ Te same stany i kolejność, powrót do zielonego |
| 1.8 | Stany awarii na Nasłuchu | ❌ Niespójne z seniorem i rodziną (błąd 3) |
| 1.9 | Restart backendu w trakcie rozmowy | ❌ Błąd 1. Po poprawce (a) i (b) frontendu: ✅ pasek „offline” nad alertem u seniora, po powrocie backendu komunikat o przerwanym połączeniu, Nasłuch wraca do czekania. Zostaje (c). |
| 1.10 | API odtwarzania i decyzji | ✅ 404, 400, 202, 409, 204. Zła rola w `/ws/events` dostaje 1008. |
| 1.11 | Baza (DAT-01) | ✅ Rozmowa z alertem zapisana, rozmowy bez alertu (04, 05, 07) bez śladu. `ignored_stages` i `labels.jsonl` zapisane. |
| 1.13 | Znaczek trybu | ⚠️ Przeskakuje między MOCK i SCRIPTED (błąd 4) |
| 2.8 | Rodzina: dźwięk, tytuł, decyzja | ✅ „⚠ Alert” i trzy sygnały przy alercie wysokim. Decyzja rodziny z „nie licz tego etapu” zapisana. |
| 2.9 | Odświeżenie panelu rodziny | ✅ Historia wczytana z decyzjami |
| 2.10 | Opóźnienie | ✅ Alert 3–4 ms po segmencie, który go wywołał (słowa kluczowe, AI w MOCK) |
| — | Kontrakt `/ws/events` | ✅ 0 błędnych wiadomości we wszystkich przebiegach |
| — | Testy automatyczne | ✅ Frontend 76/76, backend 373/373 |

## Znalezione błędy

| # | Waga | Błąd | Gdzie |
|---|---|---|---|
| 1 | Wysoka | Restart backendu w trakcie rozmowy. (a) Senior dalej widzi alert, ale nie wie, że system nie działa. Nasłuch i rodzina po 250 ms pokazują „offline”. (b) Po powrocie backendu alert u seniora i na Nasłuchu „wisi”, bo backend trzyma rozmowę tylko w pamięci i `call.ended` nigdy nie przychodzi. (c) Alert nie trafia do bazy, bo zapis następuje dopiero na koniec rozmowy. Rodzina traci go po odświeżeniu, a decyzja o nim dostaje 404. | (a), (b) frontend; (c) backend (`RetainAlertedCallHook`) |
| 2 | Wysoka | Scenariusz 06 (telewizor w tle) daje fałszywy alarm wysoki. Słowa kluczowe nie odróżniają lektora w telewizji od rozmówcy. | backend (słowa kluczowe, silnik ryzyka) |
| 3 | Średnia | Nasłuch nie korzysta z `selectSeniorStatus`. Przy awarii AI pokazuje „Czekam na rozmowę” zamiast „Podstawowa ochrona”. Przy `audio = degraded` pokazuje „Nie słyszę rozmowy”, a senior i rodzina „Ochrona wstrzymana”. | frontend (`pages/listen`) |
| 4 | Średnia | Znaczek trybu przeskakuje w trakcie rozmowy. Zdarzenia rozmowy mają tryb MOCK (AI bez klucza), a heartbeat backendu co 10 s ma SCRIPTED. Znaczek pokazuje tryb ostatniego zdarzenia. | frontend (`ModeBadge`) albo backend (tryb heartbeatu) |
| 5 | Niska | Scenariusz 12 kończy się na poziomie średnim, a oczekiwany jest wysoki. Rada jest poprawna. | backend (silnik ryzyka albo oczekiwanie w scenariuszu) |

## Czego nie przetestowano

- **Wymaga zespołu i urządzeń:** głos syntezy na telefonie (1.2), wygląd na prawdziwych urządzeniach, słyszalność sygnału u rodziny.
- **Wymaga klucza API:** prawdziwe AI (2.1–2.3). Testy szły na gotowych odpowiedziach MOCK.
- **Jeszcze niezaimplementowane:** cały M3 (audio, HTTPS, mikrofon w Nasłuchu) i audyt AI (BE-07).

## Uwagi

- Testy dopisały alerty i decyzje do `backend/aniol.db` i `backend/data/labels.jsonl`. Wpisy z gotowych odpowiedzi AI mają tryb MOCK.
- Dwie decyzje seniora (`hung_up` o 17:28:25 i `called_trusted` o 17:28:45 UTC) nie pochodziły ze skryptów testowych. Najpewniej to otwarte karty przeglądarki, które połączyły się z serwerami testowymi na tych samych portach.

## Poprawki

| Błąd | Stan | Co zmieniono |
|---|---|---|
| 1a | ✅ Naprawiony (frontend) | Senior: gdy kanał zdarzeń jest rozłączony w widoku alertu, decyzji albo końca rozmowy, nad treścią jest czerwony pasek „Anioł Stróż jest offline”. Alert zostaje na ekranie, bo ostrzeżenie dalej jest ważne. |
| 1b | ✅ Naprawiony (frontend) | `EventsService` po każdym połączeniu czyta `startedAt` z `GET /api/status` (wygenerowany `StatusService`). Nowa wartość oznacza restart backendu, więc trwająca rozmowa jest oznaczana jako przerwana (`ActiveCall.interrupted`). Senior widzi przez 15 s „Połączenie z Aniołem Stróżem zostało przerwane. Ta rozmowa nie jest już sprawdzana.”, potem stan spoczynkowy. Nasłuch pokazuje alarm tylko w trakcie rozmowy i wraca do „Czekam na rozmowę”. Sprawdzone testami i na żywo (zatrzymanie i start backendu w trakcie scenariusza 01). |
| 1c | ⏳ Otwarty (backend) | Alert zapisuje się w bazie dopiero na koniec rozmowy (`RetainAlertedCallHook`). Po restarcie w trakcie rozmowy alert i decyzje o nim przepadają. |
