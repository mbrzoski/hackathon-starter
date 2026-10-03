# Decyzje do podjęcia

Zebrane z prac nad: obiektem rozmowy i trybem SCRIPTED, wykrywaniem deterministycznym i alertami, decyzjami (alert.decision), `StageClassifier` i `QuoteValidator`.

Każdy punkt to miejsce, w którym specyfikacja była niejednoznaczna, sprzeczna albo czegoś w niej brakowało. Kod już działa według kolumny „Przyjęte”. Kolumna „Rekomendacja” mówi, co bym zostawił. Zmiany reguł z `docs/ograniczenia-*` trzeba wpisać do tabeli „Odstępstwa” (zasada z CLAUDE.md).

Legenda wagi: **Wysoka**: wpływa na uczciwość demo, bezpieczeństwo albo kontrakt. **Średnia**: zmienia zachowanie widoczne dla użytkownika. **Niska**: porządek, wygoda.

## 1. Reguły ryzyka i alerty

| ID | Waga | Temat | Przyjęte w kodzie | Alternatywa | Rekomendacja |
|---|---|---|---|---|---|
| D-01 | Wysoka | „policja + BLIK = średni” (architektura, sekcja 5) kontra reguła high (pieniądze/kanał płatności + autorytet) | Reguły stosowane tak samo dla każdego źródła, więc „policja + BLIK” daje **HIGH**. Inaczej scenariusz 01 nie osiągnąłby high bez AI, a to wymóg testu | Trafienia samych słów kluczowych ograniczone do MEDIUM, high tylko z AI | Zostawić. Zdecydować, czy poprawić zdanie w `architecture.md`. Skutek uboczny: scenariusz 06 (telewizor w tle) daje fałszywy HIGH ze słów kluczowych, a to ma naprawić AI (rola `background`) |
| D-02 | Średnia | Znaczenie „Calm: dwa etapy na medium, trzy na high” | Medium wymaga 2 etapów, high wymaga kombinacji high **i** co najmniej 3 etapów; kombinacja z dwóch etapów daje medium | Calm inaczej niż Standard także dla medium | Zostawić, ale potwierdzić z autorem reguły |
| D-03 | Średnia | Klucz deduplikacji trafień | **etap + segId + źródło** (specyfikacja: etap + segId). Bez źródła trafienie AI i słów kluczowych na tym samym segmencie zlałyby się w jedno i `triggeredBy = both` byłoby nieosiągalne | Klucz dosłowny; `both` liczone inaczej | Zostawić |
| D-04 | Średnia | Które trafienia „zdecydowały o poziomie” (`triggeredBy`) | Dla HIGH z kombinacji pieniądze + presja liczą się tylko trafienia tych dwóch rodzajów etapów; w pozostałych przypadkach wszystkie liczone etapy | Źródła wszystkich liczonych trafień (prostsze, mniej precyzyjne) | Zostawić |
| D-05 | Średnia | Jeden alert na poziom, tekst fixowany w chwili wzrostu | Alert na HIGH w scenariuszu 01 powstał przy AUTORYTET + PIENIĄDZE, więc dostał szablon `high-general`, a nie `high-payment-channel` (kanał płatności pojawił się później) | Drugi alert na ten sam poziom albo aktualizacja alertu, gdy zmieni się szablon | Rozważyć. Teraz tekst czytany seniorowi może nie wymieniać najważniejszego etapu |
| D-06 | Średnia | Segmenty interim | Interim nie zajmuje numeru; dostaje id następnego finalnego, więc interim i final tej samej wypowiedzi to jeden klucz trafienia | Własne numery dla interim | Zostawić, sprawdzić z prawdziwym STT (BE-06+) |
| D-07 | Niska | `URGENT_THREAT` bez słów kluczowych | Pusta lista wzorców; groźba zostaje dla AI | Kilka ostrożnych wzorców | Zostawić |
| D-08 | Niska | Poszerzone wzorce słów kluczowych | Dodane odmiany: „dział*”, „nie odkładać słuchawki”, „pieni[ae]dz”, „gotówk”, „dyskrecj”. Cofnięte „zosta* na linii”, bo łapało wypowiedź seniora | Trzymać się dokładnie listy z zadania | Zostawić |
| D-09 | Niska | Treść szablonów | `shortText` = jedno zdanie, `advice` = reszta; głos czyta oba po kolei (razem dają dokładnie zdanie z zadania). Szablony `high-general` i `medium-general` napisałem sam | Zespół przegląda i poprawia `alerts.pl.yml` | **Do przeglądu przez zespół** (DET-04: teksty ma przejrzeć zespół) |
| D-10 | Średnia | Czułość | Zawsze STANDARD (`DefaultSensitivitySource`), do czasu zadania `settings` | – | Podmienić przy zadaniu `settings` |

## 2. Dane, retencja, decyzje

| ID | Waga | Temat | Przyjęte w kodzie | Alternatywa | Rekomendacja |
|---|---|---|---|---|---|
| D-11 | Wysoka | Retencja „w pamięci” po zakończeniu rozmowy | `CallState` po `end()` zostaje z wyciętą transkrypcją, ale **nic go nie trzyma** po zakończeniu rozmowy; trwały jest tylko SQLite | Rejestr zakończonych rozmów w pamięci z retencją i czyszczeniem (DAT-02) | Zrobić przy zadaniu retencji (DAT-02) |
| D-12 | Średnia | Które decyzje może podjąć kto | Senior: `hung_up`, `called_trusted`, `false_alarm`. Rodzina: `confirmed_scam`, `false_alarm`. Reszta: 400. `ignoredStages` tylko od rodziny | Brak ograniczeń | Zostawić; frontend musi wysyłać tylko dozwolone pary |
| D-13 | Średnia | Etykiety (`labels.jsonl`) | Tylko identyfikatory etapów (bez cytatów) i pole `mode`, żeby etykiety SCRIPTED/MOCK nie mieszały się z LIVE | Pełne trafienia z cytatami | Zostawić. Ewaluacja powinna filtrować po `mode` |
| D-14 | Średnia | `ignoredStages` po zakończeniu rozmowy | Decyzja zapisana, ale poziomu nie ma co przeliczać; działa tylko na aktywną rozmowę. Raz wystawione alerty zostają, a po ponownym wzroście poziomu nie ma drugiego alertu na ten sam poziom | Rejestr zakończonych rozmów (D-11) i przeliczanie wstecz | Zostawić do D-11 |
| D-15 | Niska | Tabela `decisions` bez klucza obcego | Alert aktywnej rozmowy trafia do `alerts` dopiero po jej końcu, a senior decyduje wcześniej | Zapisywać alert od razu przy utworzeniu | Zostawić |
| D-16 | Niska | Imię seniora | `Settings` nie ma pola z imieniem seniora, więc test AI-09 sprawdza numery i imiona kontaktów oraz przykładowe imię | Dodać pole do kontraktu `Settings` | Dodać przy zadaniu `settings` |

## 3. Warstwa AI

| ID | Waga | Temat | Przyjęte w kodzie | Alternatywa | Rekomendacja |
|---|---|---|---|---|---|
| D-17 | Wysoka | Brak weryfikacji z prawdziwym API | Żaden test nie woła Anthropic. Nie sprawdzono, czy API przyjmie schemat z kontraktu razem z `effort: low` i `thinking: between_tools`, ani ile wynoszą opóźnienie i `cacheReadInputTokens` | – | **Zrobić jedno prawdziwe wywołanie** (scenariusz 01), gdy będzie `ANTHROPIC_API_KEY` w środowisku. Plik `.env` nie jest wczytywany automatycznie: klucz trzeba wyeksportować |
| D-18 | Wysoka | `prod` bez klucza | Profil inny niż `dev` bez `ANTHROPIC_API_KEY` **nie startuje** (zamiast po cichu używać mocka). Przez to `DevEmitControllerTest.Prod` ma `app.mode=MOCK` | Start z mockiem i statusem `degraded` | Zostawić. Potwierdzić, że o to chodzi |
| D-19 | Wysoka | Tryb MOCK a oznaczenie rozmowy | Gdy działa mock, `ScriptedPlayer` oznacza rozmowę jako **MOCK** (nie SCRIPTED), żeby dane z mocka nie wyglądały na wynik prawdziwego AI | Zawsze SCRIPTED | Zostawić. Odnotować: `POST /api/demo/replay` przyjmuje `mode: SCRIPTED`, a zdarzenia mogą nieść MOCK |
| D-20 | Średnia | Schemat odpowiedzi | Ręczny `JsonOutputFormat` ze schematem z kontraktu (wymóg: surowy tekst do audytu i jedno źródło prawdy). SDK umożliwia też typowane `outputConfig(Class)` z `effort` | Typowane `outputConfig` z rekordem | Zostawić |
| D-21 | Średnia | Cache transkrypcji | Zgodnie ze specyfikacją: jeden blok `<transcript>` z breakpointem na końcu. Prefiks z poprzedniego wywołania nie jest ponownie czytany z cache (trafia tylko rubryka) | Jeden blok na segment | Zdecydować po pomiarze kosztów w prawdziwym wywołaniu (D-17) |
| D-22 | Średnia | Mocki | Są dla scenariuszy 01, 02, 03. Pozostałe dostają pustą odpowiedź | Mocki dla wszystkich 12 | Dopisać przy ewaluacji (TST-05) |
| D-23 | Średnia | Progi statusu AI | Po 3 kolejnych błędach `system.status` (`ai`, `degraded`), pierwszy sukces przywraca `ok` (OBS-02) | – | Zostawić |
| D-24 | Niska | `contracts/StageHits.java` | Nieużywany po wprowadzeniu `ai.StageHitsResponse` (CON-07) | Usunąć | Usunąć albo zostawić jako rekord kontraktu |
| D-25 | Niska | Heartbeat nadpisuje statusy | Znany finding F-03: heartbeat co 10 s ustawia `BACKEND = OK` i kasuje `DEGRADED` z `CallService`, hooka retencji, `DecisionService` i etykiet | Model stanu komponentu | Rozwiązać w BE-10 |

## 4. Kontrakt i dokumentacja

| ID | Waga | Temat | Stan | Rekomendacja |
|---|---|---|---|---|
| D-26 | Wysoka | Tabela „Odstępstwa” w `docs/ograniczenia-backend-java.md` jest pusta | Zrobiłem rzeczy, które ją dotyczą: ścieżki `/api/demo`, `/api/alerts` w `openapi.yaml`, ręczne rekordy zamiast modeli z generatora, `importMappings`, networknt 2.0.1 (nie 1.5.8), nowy `openapi-request-validator-mockmvc`, SQLite z `schema.sql`, `ProblemDetail` 409 | Wpisać do tabeli (decyzja człowieka) |
| D-27 | Średnia | Pole `delayMs` w scenariuszu | Zadanie mówiło `pauseMs`, schemat ma `delayMs`; użyłem schematu | Zostawić |
| D-28 | Średnia | Nazwy wygenerowanych typów frontendu | `DecisionRequest` ma własne enumy (`DecisionRequestActorEnum` itd.). `Decision` zostawiłem bez zmian, bo frontend używa `DecisionActorEnum` | Przy okazji ujednolicić enumy w kontrakcie (osobne schematy `Actor`, `DecisionType`) |
| D-29 | Niska | Zmiany w `generate-api.mjs` | Moją poprawkę dla Windows nadpisała zmiana kolegi (`process.execPath`, FF-06). Zostaje wersja kolegi | – |

## 5. Środowisko

| ID | Waga | Temat | Stan |
|---|---|---|---|
| D-30 | Średnia | `JAVA_HOME` w tej powłoce wskazuje na JDK 17, projekt wymaga 21 | Budowałem z `jdk-21.0.12` ustawionym tylko na czas sesji. Warto poprawić `JAVA_HOME` na stałe |
| D-31 | Średnia | Frontend nie startował: brak plików `audio.service`, `decision-outbox`, `senior-status` w `core/` (importowane w `senior.ts`) | Pliki nie były w żadnej gałęzi. Do sprawdzenia, czy kolega je już dopchnął |
| D-32 | Niska | Ostrzeżenie networknt „Unknown keyword components” przy ładowaniu scenariuszy | Nieszkodliwe; wyciszyć rejestracją słowa kluczowego |
