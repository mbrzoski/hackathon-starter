# Review frontendu: findingi

Stan: `master`, commit ce7d241 (po hotfixie FF-01). Źródła reguł: `CLAUDE.md`, `docs/ograniczenia-frontend.md`, `docs/architecture.md`, `docs/plan/plan.md`.
Walidacja: po sprawdzeniu każdego findingu z zadaniem w `docs/plan/plan.md`. Finding zostaje aktualny, jeśli dotyczy zadania już zmergowanego (FE-01, FE-02, FE-03). Jeśli naprawa należy do zadania zaplanowanego później, jest w sekcji „Odroczone”.

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
| FE-06 priorytet stanów | ⚠️ | `selectSeniorStatus` ma test priorytetu dla wszystkich kombinacji. Luki: FF-03 (`listen`), FF-04 (heartbeat), FF-13 (lokalna pauza pokazuje zielony stan). |
| FE-07 ryzyko słowami | ✅ | `RISK_LEVEL_WORDS`, bez procentów. |
| FE-08 dowody w alercie | ✅ | Panel rodziny i „Dlaczego?” u seniora: etap, dosłowny cytat, czas. Źródło (AI / słowa kluczowe) jest w obu widokach. |
| FE-09, FE-10 człowiek decyduje, numer z ustawień | ✅ | Numer tylko z `SettingsStore`, link `tel:` dopiero po dotknięciu „Zadzwoń teraz”. `SettingsStore.firstContact` jest na razie zawsze `null` (TODO do `GET /api/settings`), więc ekran pokazuje „Brak zapisanego numeru”. |
| FE-11 brak zapisu audio | ✅ | Nie ma jeszcze żadnego przechwytywania audio. |
| FE-12 `localStorage` | ✅ | Nie jest używany. |
| FE-13 walidacja Ajv | ✅ | `event-validator.ts`: Ajv 2020 względem `EventEnvelope`. Błędne wiadomości są odrzucane z ostrzeżeniem. Bez schematu nic nie jest akceptowane. |
| FE-14 język | ✅ | Teksty po polsku, kod i komentarze po angielsku. |
| WEB-01 SPA bez SSR | ✅ | `@angular/build:application`, bez SSR. |
| WEB-07 gest użytkownika | ✅ (częściowo) | „Włącz ochronę” odblokowuje mowę (`VoiceService.unlock`) i woła `AudioService.start`, ale `AudioService` to na razie `NoopAudioService`. |
| WEB-09 synteza mowy pl-PL | ✅ (z uwagą FF-12) | `VoiceService` wybiera pierwszy głos `pl`, bez głosu pokazuje „Brak głosu po polsku”, nie zgłasza błędu. Czyta jednak tylko `shortText` (FF-12). |
| WEB-11 ekran seniora | ✅ (z kodu) | Tekst 28 px, przyciski decyzji 72 px, „Dlaczego?” 64 px, `aria-live="assertive"` dla alertu, stan ikoną i tekstem, kolory z opisanym kontrastem. Nie weryfikowałem wizualnie ani kontrastu. Na małym telefonie przewija się tylko obszar tekstu, nie strona. |
| WEB-12 rodzina mobile first | ✅ (do sprawdzenia wizualnie) | Dwie kolumny od 900 px wg komentarza. |
| WEB-13 biblioteki z npm i lista ujawnień | ✅ | Biblioteki są w `README.md`. Czcionka lokalnie przez `@fontsource`, bez CDN. |
| CLAUDE.md: typy z kontraktu, ryzyko słowami, stan ikoną i tekstem | ✅ | `Icon` + tekst w statusach. |
| CLAUDE.md: nowa logika ma test | ⚠️ | Ekran seniora, `senior-status` i `decision-outbox` mają testy. Luki: FF-07. |

## 2. Findingi aktualne (dotyczą zrobionych zadań)

| ID | Waga | Zadanie | Reguła | Miejsce | Opis | Rekomendacja |
|---|---|---|---|---|---|---|
| FF-02 | Niska | FE-01 | FE-06, API-03 | `core/events.service.ts`, `apply` → `call.started` | Zdarzenie `call.started` zawsze czyści segmenty, ryzyko, alerty i decyzje. Backend po (ponownym) połączeniu wysyła w snapshocie `call.started` tej samej rozmowy. Po zerwaniu i odnowieniu połączenia w trakcie rozmowy transkrypcja znika (snapshot jej nie zawiera). Od naprawy backendowego F-04 snapshot niesie alerty, ryzyko i decyzje, więc te wracają zaraz po `call.started`. | Czyścić stan tylko gdy `callId` jest inny niż bieżący. Dodać test: ponowne `call.started` z tym samym `callId`. |
| FF-05 | Średnia | FE-01 | FE-01 (docs), „Odstępstwa” | `app-target*.ts`, `angular.json`, `frontend/README.md` | Docs opisują jedną aplikację z trasami `/senior`, `/family`, `/setup`, `/audit`. Kod ma trzy osobne aplikacje (senior, listen, family), trasę `/listen` i rolę `audit` w `/ws/events`. `/listen` (tablet przy telefonie) i `/senior` (telefon/tablet seniora) są rozdzielone. Tabela „Odstępstwa” w `ograniczenia-frontend.md` jest pusta. | Wpisać odstępstwo do „Odstępstw” albo zgłosić człowiekowi. |
| FF-06 | Niska (do potwierdzenia) | FE-01 | CLAUDE.md (komendy) | `package.json` (`start`), `scripts/generate-api.mjs` | `npm start` używa składni bash (`( … & … & wait)`), a `generate-api.mjs` woła `execFileSync('npx', …)` bez `shell: true`. W cmd i PowerShellu na Windows to się nie uruchomi (`npx` to `npx.cmd`). `npm test` i `npm run build` też wołają `generate:api`. Nie sprawdzałem na Windows. | Użyć `npm exec`/`shell: true` i skryptu wieloplatformowego (np. `concurrently`) albo opisać wymóg Git Bash. |
| FF-09 | Niska | FE-01 | prywatność (FE-11, duch zasady 4) | `core/event-validator.ts` | Przy odrzuceniu wiadomości `console.warn` loguje cały obiekt (`data`), czyli może wypisać fragment transkrypcji w konsoli przeglądarki. | Logować tylko `type` i błędy walidacji. |
| FF-08 | Niska | FE-01 | dokumentacja | `frontend/README.md`, `styles.scss` | README mówi „contract has no `paths` yet”, a kontrakt ma `paths`. `styles.scss` i README odwołują się do `references/*.pdf`, którego nie ma w repozytorium. | Zaktualizować README. Dodać albo usunąć odwołania do plików referencyjnych. |
| FF-12 | Średnia | FE-03 | FE-04 (kontrakt alertu), architektura 6.4, DET-04 | `pages/senior/voice-readout.ts`, `alert-view.ts` | Głos czyta i ekran pokazuje tylko `shortText`. Szablony backendu mówią: „głos czyta `shortText`, a potem `advice`”, a w scenariuszu demo senior słyszy też radę („Prawdziwa policja nigdy tego nie robi. Możesz się rozłączyć”). `advice` pojawia się dopiero po „Rozłączam się”. Test `senior.spec` zakłada czytanie samego `shortText`. | Czytać `shortText` i `advice` (jedna wypowiedź), pokazać `advice` na ekranie alertu. Poprawić test. |
| FF-13 | Średnia | FE-02 | FE-06 | `pages/senior/senior.ts` (`togglePause`), `status-panel.ts` | „Wstrzymaj dla tej rozmowy” zmienia tylko etykietę przycisku. Panel stanu dalej pokazuje zielone „Anioł Stróż słucha. Nic nie jest nagrywane.”, bo stan `paused` w `selectSeniorStatus` wynika wyłącznie z backendowego `audio = degraded`, a `AudioService` jest na razie pusty. Ekran jest zielony, gdy ochrona jest wstrzymana. | Lokalna pauza ma zmieniać widok na „Ochrona wstrzymana” (żółty), niezależnie od statusu z backendu. Dodać test. |
| FF-11 | Niska | FE-03 | zasada „awaria nie po cichu” (duch FE-06) | `core/decision-outbox.ts` | Odrzucenie decyzji (4xx, np. 404 po restarcie backendu, gdy alert aktywnej rozmowy zniknął) kończy się tylko `console.warn`. Senior i rodzina nie dowiadują się, że decyzja nie została zapisana. Błędy sieci i 5xx są ponawiane bez końca z limitem odstępu 30 s. | Pokazać krótką informację przy odrzuceniu. Rozważyć limit prób. |

## 3. Findingi odroczone (realne, ale naprawa należy do zadania z planu zaplanowanego później)

| ID | Waga | Zadanie docelowe | Opis | Co zrobić |
|---|---|---|---|---|
| FF-03 | Niska | FE-05 (mikrofon na `listen`) | Widok `listen` nadal sprawdza tylko stan `audio` i ignoruje `stt`, `ai`, `backend`. Wspólny selektor `selectSeniorStatus` już istnieje (FE-02), `listen` go nie używa. `listen` ma jeszcze TODO na mikrofon. | Przy FE-05 użyć `selectSeniorStatus` także w `listen`. |
| FF-04 | Niska | BE-10 (po stronie UI także `events.service`) | Brak wykrywania zerwanego połączenia po braku heartbeatu (backend wysyła `system.status` co 10 s). Przy połączeniu half-open UI może dalej pokazywać „chroniony”. Plan nie przypisuje tego wprost żadnemu zadaniu, a BE-10 („uczciwe statusy błędów”) obejmuje stronę backendu. | Dodać zegar braku zdarzeń (np. 25 s → offline i ponowne połączenie) razem z FE-02 albo BE-10. |
| FF-07 | Niska | FE-04 (panel rodziny) | Nadal brak testów dla `openAlert`, `evidenceByStage`, podświetlania cytatów (`rows`) i priorytetu widoków `listen`. Ekran seniora, `selectSeniorStatus` i `DecisionOutbox` mają już testy. | Dopisać testy w ramach FE-04 i FE-05. |
| FF-10 | Informacja | FE-08 (wygląd) | Angular Material jest tylko motywem, komponenty to własny HTML, a `@angular/cdk` i `@angular/material` są zależnościami. | Zdecydować przy FE-08: używać komponentów Material albo usunąć zależność i poprawić opis w `CLAUDE.md`. |

## 4. Findingi zamknięte i usunięte

| Dawne ID | Dlaczego |
|---|---|
| FF-01 (brakujące pliki w FE-02/FE-03) | ZAMKNIĘTY w ce7d241: wszystkie 8 modułów jest w repozytorium (`audio.service`, `decision-outbox`, `senior-status`, `settings.store`, `voice.service`, `alert-view`, `decision-outcome`, `status-panel` oraz `decision-buttons`, `evidence-quotes`, `voice-readout`, `senior.scss`), dodano testy `senior.spec`, `senior-status.spec`, `decision-outbox.spec`. Nie uruchamiałem builda. |
| Część FF-08 (TODO i notatka w `family.html` o braku endpointu decyzji) | Przyciski decyzji są świadomie wyłączone, to zakres FE-04. Komentarz zniknie przy tym zadaniu. Z FF-08 zostały tylko README i `references/`. |

## 5. Jeszcze nie zrobione (to nie naruszenia)

- Mikrofon, AudioWorklet 16 kHz, ramki 100 ms (WEB-08), połączenie z `/ws/audio` (FE-05, BE-08).
- Prawdziwe przechwytywanie audio: `AudioService` to na razie `NoopAudioService`. `SettingsStore` czeka na `GET /api/settings` (BE-09).
- Serwer WWW: HTTPS, reverse proxy, nagłówki (WEB-02…WEB-06, zadanie WEB-01 z planu).
- Panel rodziny: przyciski decyzji są wyłączone (`disabled`), zadanie FE-04.
- Ekrany `/setup` i `/audit` to placeholdery (FE-06 i FE-07 z planu).
- Android/Capacitor (AND-01…AND-11).
- Wymagane później: wpisy do listy ujawnień dla Capacitora i wtyczek.

## 6. Kolejność poprawek (propozycja)

1. FF-02, FF-12, FF-13 (wpływają na demo M1).
2. FF-05 (wpis w „Odstępstwach”), FF-06 (jeśli zespół pracuje na Windows), FF-09, FF-08, FF-11.
3. W swoich zadaniach: FF-03 i FF-04 (FE-02/FE-05/BE-10), FF-07 (FE-04), FF-10 (FE-08).
