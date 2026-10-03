# Review frontendu: findingi

Stan: `master`, commit 4cfd095 (po FE-04 i poprawkach findingów frontendu). Źródła reguł: `CLAUDE.md`, `docs/ograniczenia-frontend.md`, `docs/architecture.md`, `docs/plan/plan.md`.
Walidacja: po sprawdzeniu każdego findingu z zadaniem w `docs/plan/plan.md`. Finding zostaje aktualny, jeśli dotyczy zadania już zmergowanego (FE-01, FE-02, FE-03, FE-04). Jeśli naprawa należy do zadania zaplanowanego później, jest w sekcji „Odroczone”.

Uwagi do review:
- Identyfikatory findingów mają prefiks `FF-` (frontend finding), żeby nie mylić ich z regułami `FE-xx` z docs i zadaniami `FE-xx` z planu.
- Nie uruchomiłem builda ani testów (`frontend/node_modules` nie ma w repozytorium, nie instalowałem zależności). Wnioski pochodzą z czytania kodu.
- Findingi dotyczą tylko zrobionych zadań (FE-01, FE-02, FE-03). Funkcje z zadań jeszcze niezrobionych są w sekcji „Jeszcze nie zrobione”.

## 1. Zgodność z regułami

| Reguła | Status | Uwagi |
|---|---|---|
| FE-01 jedna baza kodu | ✅ | Trzy buildy (`senior`, `listen`, `family`) z jednej bazy przez `fileReplacements`. Odstępstwo od układu tras z docs opisuje FF-05. |
| FE-02 typy z kontraktu | ✅ | `scripts/generate-api.mjs` generuje `src/app/api/` z `contracts/openapi.yaml`. Katalog jest w `.gitignore`. Typy nie są definiowane ponownie. |
| FE-03 brak wywołań Claude i STT | ✅ | Frontend rozmawia tylko z `/api` i `/ws/events` tego samego origin. |
| FE-04 brak liczenia ryzyka i tekstów | ✅ | Wyświetla `RiskUpdate`, `shortText` i `advice` z backendu. |
| FE-05 znaczek trybu i stan połączenia | ✅ | `app-mode-badge` na każdym ekranie. Pasek offline w buildach `listen` i `family`. Senior pokazuje offline sam (`selectSeniorStatus`, `StatusPanel`). |
| FE-06 priorytet stanów | ⚠️ | `selectSeniorStatus` (test priorytetu) używany przez ekran seniora i pasek w panelu rodziny. Luki: FF-03 (`listen`), FF-04 (heartbeat), FF-19 (brak statusu = „działa”). |
| FE-07 ryzyko słowami | ✅ | `RISK_LEVEL_WORDS`, bez procentów. |
| FE-08 dowody w alercie | ⚠️ | Etap, dosłowny cytat i źródło są zawsze. Czas i fragment rozmowy tylko dla trwającej rozmowy (FF-15). |
| FE-09, FE-10 człowiek decyduje, numer z ustawień | ✅ | Numer tylko z `SettingsStore` (senior i zaufany kontakt), link `tel:` dopiero po dotknięciu, w panelu rodziny „Zadzwoń do: …” tylko gdy numer jest zapisany. `SettingsStore` jest na razie pusty (BE-09). |
| FE-11 brak zapisu audio | ✅ | Nie ma jeszcze żadnego przechwytywania audio. |
| FE-12 `localStorage` | ✅ | Nie jest używany. |
| FE-13 walidacja Ajv | ✅ | `event-validator.ts`: Ajv 2020 względem `EventEnvelope`. Błędne wiadomości są odrzucane z ostrzeżeniem. Bez schematu nic nie jest akceptowane. |
| FE-14 język | ✅ | Teksty po polsku, kod i komentarze po angielsku. |
| WEB-01 SPA bez SSR | ✅ | `@angular/build:application`, bez SSR. |
| WEB-07 gest użytkownika | ✅ (częściowo) | „Włącz ochronę” odblokowuje mowę (`VoiceService.unlock`) i woła `AudioService.start`, ale `AudioService` to na razie `NoopAudioService`. |
| WEB-09 synteza mowy pl-PL | ✅ | `VoiceService` wybiera pierwszy głos `pl`, bez głosu pokazuje „Brak głosu po polsku”, nie zgłasza błędu. Czyta `shortText` i `advice` w jednej wypowiedzi. |
| WEB-11 ekran seniora | ✅ (z kodu) | Tekst 28 px, przyciski decyzji 72 px, „Dlaczego?” 64 px, `aria-live="assertive"` dla alertu, stan ikoną i tekstem, kolory z opisanym kontrastem. Nie weryfikowałem wizualnie ani kontrastu. Na małym telefonie przewija się tylko obszar tekstu, nie strona. |
| WEB-12 rodzina mobile first | ✅ (z kodu) | Od 360 px: przyciski decyzji w jednej kolumnie do 400 px, dwie kolumny karty od 900 px, przełącznik 44 px. Nie oglądałem w przeglądarce. |
| FE-04 (zadanie): karta alertu, oś etapów, fragment rozmowy, przyciski decyzji | ⚠️ | Poziom słowami, tryb na karcie, źródło wykrycia, historia z `GET /api/alerts` plus zdarzenia na żywo, sygnał dźwiękowy i tytuł karty tylko dla nowego alertu HIGH, podświetlenie cytatu z normalizacją (CON-05), decyzje rodziny z `ignoredStages`. Testy: `family.spec`, `alert-notifier.spec`, `quote-match.spec`. Luki: FF-14, FF-15, FF-16, FF-17, FF-18. |
| WEB-13 biblioteki z npm i lista ujawnień | ✅ | Biblioteki są w `README.md`. Czcionka lokalnie przez `@fontsource`, bez CDN. |
| CLAUDE.md: typy z kontraktu, ryzyko słowami, stan ikoną i tekstem | ✅ | `Icon` + tekst w statusach. |
| CLAUDE.md: nowa logika ma test | ⚠️ | Ekran seniora, `senior-status` i `decision-outbox` mają testy. Luki: FF-07. |

## 2. Findingi aktualne (dotyczą zrobionych zadań)

| ID | Waga | Zadanie | Reguła | Miejsce | Opis | Rekomendacja |
|---|---|---|---|---|---|---|
| FF-15 | Średnia | FE-04 | FE-08, architektura 6.4 („quotes with timestamps and context”) | `pages/family/family.ts` (`segmentsFor`), `alert-card.html`, `stage-timeline.ts` | „Fragment rozmowy” (±2 segmenty) i czas przy etapach są tylko dla trwającej rozmowy (transkrypcja z `/ws/events`). Alert z historii (`GET /api/alerts`, odświeżona strona, rozmowa zakończona i zaczęta kolejna) nie ma kontekstu ani czasu, choć backend zapisuje dokładnie ten fragment w `alert_segments` (DAT-01). Rodzina po fakcie widzi sam cytat. Wymaga zmiany po stronie backendu: nic nie udostępnia zapisanego fragmentu. | Backend: dołączyć fragment do `AlertWithDecisions` albo dodać odczyt fragmentu alertu (zmiana kontraktu). Frontend: użyć go dla historii. |

## 3. Findingi odroczone (realne, ale naprawa należy do zadania z planu zaplanowanego później)

| ID | Waga | Zadanie docelowe | Opis | Co zrobić |
|---|---|---|---|---|
| FF-03 | Niska | FE-05 (mikrofon na `listen`) | Widok `listen` nadal sprawdza tylko stan `audio` i ignoruje `stt`, `ai`, `backend`. Wspólny selektor `selectSeniorStatus` już istnieje (FE-02), `listen` go nie używa. `listen` ma jeszcze TODO na mikrofon. | Przy FE-05 użyć `selectSeniorStatus` także w `listen`. |
| FF-04 | Niska | BE-10 (po stronie UI także `events.service`) | Brak wykrywania zerwanego połączenia po braku heartbeatu (backend wysyła `system.status` co 10 s). Przy połączeniu half-open UI może dalej pokazywać „chroniony”. Plan nie przypisuje tego wprost żadnemu zadaniu, a BE-10 („uczciwe statusy błędów”) obejmuje stronę backendu. | Dodać zegar braku zdarzeń (np. 25 s → offline i ponowne połączenie) razem z FE-02 albo BE-10. |
| FF-07 | Niska | FE-05 (`listen`) | Panel rodziny ma teraz testy (`family.spec`, `alert-notifier.spec`, `quote-match.spec`). Nadal brak testów dla `openAlert` i `call-view.ts` oraz priorytetu widoków `listen`. | Dopisać przy FE-05. |
| FF-10 | Informacja | FE-08 (wygląd) | Angular Material jest tylko motywem, komponenty to własny HTML, a `@angular/cdk` i `@angular/material` są zależnościami. | Zdecydować przy FE-08: używać komponentów Material albo usunąć zależność i poprawić opis w `CLAUDE.md`. |
| FF-19 | Niska | BE-08 / BE-10 (statusy audio i STT) | Pasek stanu w panelu rodziny (`SystemStatusBar`) i ekran seniora pokazują zielone „Anioł Stróż słucha. Nic nie jest nagrywane.”, gdy żaden komponent nie opublikował statusu, bo `selectSeniorStatus` traktuje brak statusu jako „działa”. Dziś nie ma audio, więc zielony stan jest nieprawdziwy poza demo SCRIPTED. Z FF-03 (`listen`) to ten sam wzorzec. | Gdy backend zacznie publikować `audio` i `stt` (BE-08, BE-10), traktować brak statusu przy rozmowie LIVE jako „nie słyszę”. |

## 4. Findingi zamknięte i usunięte

| Dawne ID | Dlaczego |
|---|---|
| FF-01 (brakujące pliki w FE-02/FE-03) | ZAMKNIĘTY w ce7d241: wszystkie 8 modułów jest w repozytorium (`audio.service`, `decision-outbox`, `senior-status`, `settings.store`, `voice.service`, `alert-view`, `decision-outcome`, `status-panel` oraz `decision-buttons`, `evidence-quotes`, `voice-readout`, `senior.scss`), dodano testy `senior.spec`, `senior-status.spec`, `decision-outbox.spec`. Nie uruchamiałem builda. |
| Część FF-08 (TODO i notatka w `family.html` o braku endpointu decyzji) | Przyciski decyzji są świadomie wyłączone, to zakres FE-04. Komentarz zniknie przy tym zadaniu. Z FF-08 zostały tylko README i `references/`. |
| FF-05, FF-14, FF-16, FF-17, FF-18 | ZAMKNIĘTE (testy frontendu zielone): FF-05 ma wpis w „Odstępstwach” (`ograniczenia-frontend.md`, 2026-10-03, do potwierdzenia przez zespół); FF-14: błąd decyzji rodziny pokazuje się na karcie z powodem i odblokowuje przyciski (`DecisionOutbox.send` ma callback `onFailed`), przełączniki „nie licz tego etapu” tylko dla alertów trwającej rozmowy; FF-16: historia wczytywana ponownie po odnowieniu połączenia; FF-17: „Senior wybrał(a)” albo imię z ustawień; FF-18: stopka mówi o wysyłce do dostawcy AI i zapisie fragmentów alertowanych rozmów. |
| FF-02, FF-06, FF-08, FF-09, FF-11, FF-12, FF-13 | ZAMKNIĘTE (zmiany z `2e0767c`, potwierdzone w kodzie, nie uruchamiałem testów): ponowne `call.started` tej samej rozmowy nie czyści stanu (FF-02); skrypty `generate-api.mjs` i `start-all.mjs` działają przez `process.execPath` bez `npx` (FF-06); README i `styles.scss` nie odwołują się już do `references/` ani „no paths” (FF-08); `console.warn` nie wypisuje treści wiadomości (FF-09); `DecisionOutbox.unsaved` i komunikat u seniora (FF-11, tylko ekran seniora, zob. FF-14); `advice` na ekranie i w głosie (FF-12); lokalna pauza przekazywana do `selectSeniorStatus` (FF-13). |

## 5. Jeszcze nie zrobione (to nie naruszenia)

- Mikrofon, AudioWorklet 16 kHz, ramki 100 ms (WEB-08), połączenie z `/ws/audio` (FE-05, BE-08).
- Prawdziwe przechwytywanie audio: `AudioService` to na razie `NoopAudioService`. `SettingsStore` czeka na `GET /api/settings` (BE-09).
- Serwer WWW: HTTPS, reverse proxy, nagłówki (WEB-02…WEB-06, zadanie WEB-01 z planu).
- Ekrany `/setup` i `/audit` to placeholdery (FE-06 i FE-07 z planu).
- Android/Capacitor (AND-01…AND-11).
- Wymagane później: wpisy do listy ujawnień dla Capacitora i wtyczek.

## 6. Kolejność poprawek (propozycja)

1. Zrobione: FF-14, FF-05, FF-16, FF-17, FF-18 (sekcja 4).
2. FF-15 (kontekst i czas w historii): wymaga zmiany backendu i kontraktu, czeka na decyzję.
3. W swoich zadaniach: FF-03, FF-04 i FF-19 (FE-05/BE-08/BE-10), FF-07 (FE-05), FF-10 (FE-08).
