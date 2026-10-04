# Anioł Stróż: dane do prezentacji i prompt (PDF, maks. 10 slajdów)

Zasady organizatora: PDF, **maksymalnie 10 slajdów**. Mogą zawierać zrzuty ekranu, link do repozytorium, linki do dema, grafiki i inne materiały związane z projektem lub zadaniem. Kategoria: Open Task „Artificial Intelligence”.

Stan danych: z repozytorium na 4.10.2026, ok. 00:10 (63 commity). Wszystko poniżej pochodzi z dokumentów i git logu. Pola `[UZUPEŁNIJ]` musi wpisać człowiek.

---

## CZĘŚĆ A. Co musisz dodać (zanim wkleisz prompt)

| # | Co | Po co | Skąd |
|---|---|---|---|
| 1 | Nazwa zespołu, imiona i role (3–4 osoby) | Slajd 10 | Ty |
| 2 | Link do repozytorium (publiczny lub z dostępem dla jury) | Wymóg organizatora | GitHub |
| 3 | Link do dema (`make demo-tunnel` daje publiczny adres `trycloudflare.com`, ale zmienia się przy każdym starcie, więc najlepiej link do filmu) i/lub link do filmu z dema | Slajdy 4 i 10 | Nagraj film w trybie LIVE i podaj w nim tryb |
| 4 | **Zrzuty ekranu** (w repo nie ma żadnego): ekran seniora w stanie alertu (czerwony, „Dlaczego?”, 3 przyciski), panel rodziny z podświetlonymi cytatami, widok Nasłuchu, ekran stanu błędu („Podstawowa ochrona (bez AI)”) | Slajdy 3, 4, 7 | `make run-backend` + `make run-frontend`, scenariusz 01 i 02 w trybie SCRIPTED, każdy zrzut z widocznym znaczkiem trybu |
| 5 | Zdjęcie urządzenia obok telefonu stacjonarnego (tablet + słuchawka) | Slajd 3 lub 4 | Zrób na żywo |
| 6 | **Zmierzone liczby** (zamiast szacunków z `architecture.md`): czas od zdania do alertu w LIVE (p50), koszt na rozmowę z logu audytu, trafność Vosk na własnych nagraniach | Slajd 8 | Log audytu (`GET /api/audit/summary`), `docs/architecture.md` wprost zabrania cytowania szacunków |
| 7 | **Tabela ewaluacji słowa kluczowe vs AI na 12 scenariuszach** | Slajd 8, najważniejszy dowód wartości AI | Zadania BE-12 i EV-03 jeszcze nie zrobione. Uruchom 12 scenariuszy z prawdziwym Claude i wypełnij tabelę z części B, slajd 8. Jeśli nie zdążysz, napisz na slajdzie uczciwie „wyniki dla scenariuszy 01–03”. |
| 8 | Ujawnienie powstania dokumentu architektury | Slajd 10, ujawnienia | Zdanie na slajdzie: „koncepcja i architektura powstały w dniu hackathonu z pomocą Claude; przed hackathonem istniało tylko puste repozytorium git; cały kod, prompty, dane, nagrania i UI powstały podczas HackYeah”. |
| 9 | Potwierdzenie, które zadania naprawdę działają na dzień oddania | Wszystkie slajdy | Plan w `docs/plan/plan.md` jest nieaktualny: BE-08 (LIVE, Vosk), WEB-01 (HTTPS) i FE-05 (mikrofon) są już zmergowane, choć plan pokazuje „do zrobienia”. Uruchom `make test` i `make e2e` i wpisz wynik. |
| 10 | Logo / identyfikacja wizualna | Spójny wygląd | UI używa fontu Public Sans i jest czerwony/zielony wg stanu. Jeśli macie logo, podaj. |

**Nie obiecuj w prezentacji** (wg planu nie zrobione albo niezweryfikowane): kreator konfiguracji i zgód (FE-06), ekran audytu (FE-07, to placeholder), tryb REPLAY (BE-11), aplikacja Android (AND-01), SMS (BE-13), push (AND-02), wersja angielska UI. Pokaż je tylko jako „następne kroki”.

---

## CZĘŚĆ B. Dane do prezentacji (źródło prawdy dla slajdów)

### Slajd 1. Tytuł

- Nazwa: **Anioł Stróż**
- Hasło (propozycja): „Ostrzega seniora w trakcie rozmowy. Pokazuje dosłowne słowa oszusta. Decyzję zawsze podejmuje człowiek.”
- HackYeah 2026, Open Task „Artificial Intelligence”
- Zespół, repozytorium, demo: `[UZUPEŁNIJ]`

### Slajd 2. Problem

- Oszustwo „na policjanta”: dzwoniący podaje się za policjanta lub prokuratora, straszy (bliski w tarapatach, oszczędności zagrożone), żąda tajemnicy, trzyma seniora na linii i doprowadza do wypłaty gotówki, przekazania jej „kurierowi”, podania kodu BLIK albo przelewu na „bezpieczne konto”.
- Rozmowa ma **rozpoznawalny scenariusz z etapami**. Ofiara jest pod presją właśnie w trakcie rozmowy.
- Istniejące zabezpieczenia nie działają **w trakcie rozmowy**: blokady numerów, opóźnienia przelewów i kampanie informacyjne. Słowa kluczowe łapią oczywiste przypadki, ale dają fałszywe alarmy (prawdziwy policjant, telewizor w tle) i nie łapią omówień („to musi zostać między nami”).
- Senior często nie zainstaluje aplikacji na smartfonie, a ma telefon stacjonarny.
- `[UZUPEŁNIJ]` jedna liczba o skali problemu z publicznego źródła (policja, UKE, NASK lub banki) z podaniem źródła. W repozytorium nie ma żadnej, więc nie wymyślaj.

### Slajd 3. Rozwiązanie i jak to działa (UC-01)

Urządzenie nasłuchujące (telefon lub tablet) stoi obok telefonu seniora, który rozmawia przez głośnik. Android nie pozwala nagrywać rozmowy na tym samym urządzeniu (AND-06), więc to osobne urządzenie.

Przebieg:
1. Ktoś dzwoni, senior rozmawia na głośniku.
2. Nasłuch wysyła dźwięk (PCM 16 kHz, mono) do backendu, który zamienia mowę na tekst **lokalnie** (Vosk).
3. Słowa kluczowe i AI (Claude) znajdują **etapy oszustwa z dosłownym cytatem**. Każdy cytat sprawdza `QuoteValidator`.
4. Deterministyczny silnik ryzyka wylicza poziom (brak / niski / średni / wysoki).
5. Przy średnim lub wysokim: ten sam alert w kilku milisekundach trafia do trzech miejsc: ekran seniora (czerwony, głos, „Dlaczego?” z cytatami, trzy przyciski), panel rodziny (poziom, etapy, cytaty z kontekstem, sygnał dźwiękowy) i ekran Nasłuchu.
6. Senior wybiera: „Rozłączam się” / „Zadzwoń do [kontakt]” / „To fałszywy alarm”. Rodzina widzi decyzję i może potwierdzić oszustwo lub oznaczyć fałszywy alarm.

Trzy aplikacje z jednej bazy kodu Angular: `/senior`, `/listen`, `/family`.

### Slajd 4. Demo i „wow”

- Scena: kolega dzwoni do „babci” jako fałszywy policjant. Po zdaniu typu „proszę nikomu nie mówić i wypłacić pieniądze” tablet robi się czerwony i mówi: „Ten rozmówca prosił o zachowanie tajemnicy i wypłatę pieniędzy. Prawdziwa policja tak nie robi. Możesz się rozłączyć.” W tej samej chwili telefon rodziny pokazuje alert z podświetlonymi cytatami.
- Druga scena (10 s): **pułapka z oddzwonieniem**. Oszust każe zadzwonić pod 112, ale zostaje na linii. Alert radzi odłożyć słuchawkę, odczekać minutę i dzwonić z numeru zapisanego w telefonie. Szablon dla scenariusza 12 zawiera tę instrukcję.
- Trzecia scena: ten sam scenariusz 02 (omówienia, bez słów ze słownika). Słowa kluczowe nic nie wykrywają, AI wykrywa. To pokazuje wartość AI.
- Zrzuty ekranu: `[UZUPEŁNIJ]` (część A, pkt 4). Link do filmu / dema: `[UZUPEŁNIJ]`.
- Podaj na slajdzie, w jakim trybie jest film (LIVE / SCRIPTED / MOCK).

### Slajd 5. Rola AI (dlaczego AI i co dokładnie robi)

Dwie części AI:
1. **Strumieniowe rozpoznawanie mowy po polsku**, lokalnie i offline: Vosk, model `vosk-model-small-pl-0.22` (Apache 2.0, opublikowany WER 11,6–18,4 zależnie od zbioru testowego).
2. **Claude Sonnet 5.5** (`claude-sonnet-5-5`, effort `low`, thinking `between_tools` czyli bez myślenia, structured output): rozpoznaje 8 etapów oszustwa w zaszumionej, parafrazowanej mowie i zwraca **tylko etap + dosłowny cytat + rolę mówiącego** (rozmówca / senior / tło / niejasne).

Osiem etapów: podanie się za władzę, groźba/presja, żądanie tajemnicy, izolacja, prośba o pieniądze, kanał płatności (BLIK, „bezpieczne konto”, kurier), zdalny dostęp, prośba o dane osobowe.

Czego AI **nie** robi: nie liczy poziomu ryzyka, nie pisze tekstów alertów, nie decyduje, kogo powiadomić, nie rozłącza, nie dzwoni, nie blokuje numerów.

Odrzucone świadomie (i dlaczego): narzędzia/function calling, RAG, embeddingi, MCP, agenci, multi-agent, pamięć między rozmowami. Powód: rubryka mieści się w jednym prompcie, żadna akcja nie należy do AI, jedno wywołanie na zdanie jest najszybsze, najtańsze i każdy z zespołu potrafi je wyjaśnić linijka po linijce.

Rubryka (`stage-rubric.pl.md`, ok. 3,5 tys. tokenów, w cache) zawiera po 2–3 polskie przykłady na etap i przykłady „to nie jest ten etap”. Transkrypcja jest traktowana jako dane od nieznanej osoby: polecenia w niej są ignorowane (odporność na prompt injection, scenariusz 10).

### Slajd 6. Architektura i przepływ danych

Diagram do narysowania (od lewej):

```
Telefon seniora (głośnik) --dźwięk--> Nasłuch (tablet, /listen, mikrofon)
   --PCM 16 kHz, 100 ms, WebSocket /ws/audio--> Backend (Java 21, Spring Boot 3, wątki wirtualne)
      ├─ Vosk (lokalnie, dedykowany wątek na rozmowę) -> segmenty tekstu
      ├─ Warstwa słów kluczowych (deterministyczna, działa offline)
      ├─ Klasyfikator etapów -> Claude API (tylko tekst) -> trafienia {etap, segment, cytat, rola}
      ├─ QuoteValidator (cytat musi być w segmencie)
      ├─ Silnik ryzyka + stan rozmowy (deterministyczny)
      ├─ Alert z szablonu (deterministyczny)
      ├─ Log audytu AI (SQLite)
      └─ /ws/events --> /senior, /family, /listen (Angular)
```

Stos: Java 21, Spring Boot 3, Maven; Angular (standalone, signals, Material); SQLite; Caddy (HTTPS, reverse proxy); Docker; kontrakt `contracts/openapi.yaml` jako jedyne źródło prawdy (rekordy Javy i typy TS z niego); testy architektury ArchUnit.

Szczegóły warte wzmianki: jedno wywołanie Claude naraz na rozmowę (kolejka z flagą „dirty”), transkrypcja tylko przyrasta (cache promptu), z wątku rozpoznawania mowy nie wołamy Claude.

### Slajd 7. Zaufanie, weryfikacja i kontrola człowieka

- **Dowód przy każdym alercie:** dosłowny cytat, numer segmentu, 2 segmenty kontekstu, podświetlenie w transkrypcji.
- **Brak zmyślonych dowodów:** `QuoteValidator` odrzuca cytat, którego nie ma w segmencie (po normalizacji białych znaków i polskich znaków, min. 3 znaki). Odrzucony cytat nie wpływa na ryzyko, ale jest widoczny w audycie.
- **Brak fałszywej pewności:** poziom ryzyka tylko słowami (brak / niski / średni / wysoki), nigdy w procentach. Reguły widoczne na jednym slajdzie: *wysoki* = prośba o pieniądze lub kanał płatności + co najmniej jeden z: autorytet, groźba, tajemnica, izolacja; *średni* = dwa różne etapy manipulacji; *niski* = jeden etap, zapis bez pokazywania seniorowi.
- **Źródło sygnału:** każdy alert mówi „wykryte przez AI / słowa kluczowe / oba”.
- **Człowiek decyduje:** system nigdy nie rozłącza, nie dzwoni, nie blokuje numerów. Trzy decyzje seniora, potwierdzenie lub fałszywy alarm po stronie rodziny, „nie licz tego etapu” w alercie. Decyzje zapisują się jako etykiety do kolejnej oceny, **nie** uczą modelu automatycznie.
- **Uczciwy stan:** nigdy zielony, gdy system nie słyszy. Awaria dźwięku/STT: „Nie słyszę rozmowy”. Awaria AI: „Podstawowa ochrona (bez AI)” i dalej działają słowa kluczowe (3 kolejne błędy -> status `degraded`). Awaria backendu: „Anioł Stróż jest offline”.
- **Znaczek trybu** LIVE / REPLAY / SCRIPTED / MOCK na każdym ekranie, zdarzeniu i rekordzie audytu.

### Slajd 8. Dowody i pomiary

**Zbiór ewaluacyjny: 12 syntetycznych scenariuszy** (napisanych z Claude na podstawie publicznych opisów policji i banków; żadnych prawdziwych ofiar). Walidowane względem schematu w testach.

| # | Scenariusz | Oczekiwanie | Słowa kluczowe | AI | Razem |
|---|---|---|---|---|---|
| 01 | Klasyczny fałszywy policjant | wysoki | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` |
| 02 | To samo, omówieniami (bez słów ze słownika) | wysoki | nie wykrywa (z założenia) | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` |
| 03 | Fałszywy bank + kod autoryzacyjny | wysoki | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` |
| 04 | Prawdziwy wnuk prosi o 200 zł | brak alertu | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` |
| 05 | Prawdziwy policjant, kradzież roweru | brak alertu | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` |
| 06 | Telewizor o oszustwach w tle | brak alertu | **fałszywy alarm (znany błąd)** | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` |
| 07 | Senior mówi „nie podam kodu BLIK” | brak MONEY_REQUEST | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` |
| 08 | Powolne oszustwo (15 min) | wysoki przy prośbie o pieniądze | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` |
| 09 | Silne błędy STT | wysoki | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` |
| 10 | Wstrzyknięcie polecenia do systemu | wysoki, polecenie zignorowane | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` |
| 11 | Zdalny dostęp do komputera | wysoki | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` |
| 12 | Pułapka z oddzwonieniem na 112 | wysoki + porada „odłóż słuchawkę” | `[UZUPEŁNIJ]` | `[UZUPEŁNIJ]` | w teście UC-01 wyszedł średni zamiast wysokiego |

Zmierzone dotąd (jedna rozmowa, scenariusz 02, prawdziwy Claude, 3.10; to nie statystyka):
- Czas wywołania Claude: **2,5–3,9 s** (limit 8 s; przy limicie 2,5 s wszystkie 5 wywołań kończyło się timeoutem, więc limit podniesiono).
- Tokeny na wywołanie: ok. 3,5 tys. z cache (rubryka), 69 nowych wejściowych, 12–512 wyjściowych.
- Szacowany koszt jednego wywołania: ok. 0,003–0,006 USD (wyliczenie z cennika, nie z rachunku). Dla 10-minutowej rozmowy architektura szacuje 0,20–0,60 USD. **Zastąp zmierzonym.**
- Alert z AI w tej rozmowie: poziom wysoki, `triggeredBy: both`; AI wykryło podanie się za funkcjonariusza i groźbę w parafrazie, a prośbę o pieniądze wykryły słowa kluczowe.
- Opóźnienie alertu po segmencie w trybie z mockiem AI: 3–4 ms (to opóźnienie samej aplikacji, bez AI).
- Docelowo z architektury: alert w 3–4 s od decydującego zdania. `[UZUPEŁNIJ zmierzone w LIVE]`.

Jakość kodu i testów (stan z dokumentów, odśwież po `make test`):
- Backend: 443 testy według findingów (373 w teście UC-01), w tym ArchUnit i testy kontraktu każdego żądania względem OpenAPI. Frontend: 76 testów (Vitest). Test end-to-end UC-01 w Chrome (`make e2e`): trzy aplikacje naraz, 0 błędnych wiadomości względem kontraktu.
- Testy nie wołają prawdziwego Claude ani STT (WireMock, `FakeSttProvider`, tryb MOCK).
- ok. 6,8 tys. linii kodu Javy w `main`, 54 pliki testów backendu, 17 plików testów frontendu, 63 commity w ~34 godziny.

### Slajd 9. Prywatność, ograniczenia i uczciwość

Prywatność (privacy by design):

| Dane | Dokąd | Czy zapisywane |
|---|---|---|
| Dźwięk rozmowy | Tylko do lokalnego Vosk w backendzie. **Nie opuszcza urządzenia.** | Nigdy (ani na dysk, ani do logów) |
| Tekst rozmowy | Claude API (Anthropic), tylko tekst | W pamięci na czas rozmowy; zapisywany tylko, gdy rozmowa wywołała alert (cytowane segmenty ±2), 1–90 dni, domyślnie 30, z możliwością skasowania (`DELETE /api/data`) |
| Imię seniora, kontakty, numery | Tylko backend | Tak, **nigdy nie wysyłane do Claude ani STT** |
| Klucze API | Tylko zmienne środowiskowe | Nigdy w repozytorium, logach, frontendzie |

Ograniczenia (powiedz wprost):
- Szum i mikrofon w głośniku telefonu obniżają skuteczność; mały polski model Vosk myli się częściej niż STT w chmurze (zmierzone na własnych nagraniach: `[UZUPEŁNIJ]`).
- Bez adaptera linii nie zawsze wiadomo, kto mówi (rozmówca, senior, telewizor).
- Nowe scenariusze oszustw spoza ośmiu etapów mogą umknąć.
- Fałszywe alarmy, gdy prawdziwy krewny pilnie prosi o pieniądze; znany przypadek: telewizor w tle (scenariusz 06) daje alarm ze słów kluczowych, bo reguły nie uwzględniają roli „tło” z AI.
- Alert przychodzi **po** decydującym zdaniu, nie przed nim.
- Alert dociera tylko do otwartych aplikacji (docelowo push/SMS).
- Claude wymaga internetu; bez niego działają tylko słowa kluczowe.
- Testowane wyłącznie na rozmowach syntetycznych i odegranych, nie na prawdziwych ofiarach. Ocena RODO przetwarzania mowy osoby trzeciej (dzwoniącego) jest następnym krokiem, nie „załatwioną sprawą”. To nie jest porada prawna.

### Slajd 10. Stan, plan, zespół, linki, ujawnienia

Stan na dziś (tylko to, co jest w kodzie; potwierdź przed oddaniem):
- Zrobione: kontrakty, kanał zdarzeń, tryb SCRIPTED, słowa kluczowe + silnik ryzyka + szablony, decyzje seniora i rodziny, ekran seniora, panel rodziny, klasyfikator Claude z walidacją cytatów i trybem MOCK, log audytu AI, lokalne STT Vosk i tryb LIVE (`/ws/audio`), serwer WWW z HTTPS (Caddy), mikrofon i transkrypcja na żywo (FE-05), śledzenie całego procesu w logach.
- Następne kroki: kreator konfiguracji i zgód, ekran audytu, tryb REPLAY, runner ewaluacji, aplikacja Android (Capacitor, tryb kiosku), push/SMS dla rodziny, adapter linii telefonicznej zamiast mikrofonu, ocena RODO, współpraca z bankami/operatorami (MCP/integracje jako opcja na przyszłość).

Zespół: `[UZUPEŁNIJ]`. Repozytorium: `[UZUPEŁNIJ]`. Demo: `[UZUPEŁNIJ]`. Film: `[UZUPEŁNIJ]`.

Ujawnienia (skrót; pełna lista w `README.md`): Claude Sonnet 5.5 przez Claude API (Anthropic); `anthropic-java` 2.68.0 (MIT); Vosk 0.3.45 (Apache 2.0) + JNA (Apache 2.0 / LGPL); model `vosk-model-small-pl-0.22` (Apache 2.0); Spring Boot, SQLite (Xerial), Angular, Angular Material, RxJS, Ajv, Vitest, OpenAPI Generator, Caddy, cloudflared, WireMock, ArchUnit, Public Sans (OFL). Narzędzia AI w pracy: Claude i Claude Code (koncepcja, architektura, kod, dokumentacja, testy). Dane: syntetyczne scenariusze wygenerowane z Claude na podstawie publicznych opisów oszustw, nagrania odegrane przez zespół. Koncepcja i architektura powstały w dniu hackathonu z pomocą Claude (przed hackathonem istniało tylko puste repozytorium git).

---

## CZĘŚĆ C. Dopasowanie do kryteriów kategorii (żeby jury widziało odpowiedzi wprost)

| Wymóg kategorii | Gdzie na slajdach |
|---|---|
| AI ma sensowną rolę dla konkretnej potrzeby użytkownika | 2, 5 |
| Kierunek „pomoc w analizie sytuacji i porównaniu możliwych działań” | 3, 4 (alert tłumaczy sytuację i daje trzy działania) |
| Kierunek „poprawa dostępności” | 3, 4 (głos, duże przyciski, trzy wybory, brak czytania długich tekstów; ekran seniora min. 24 px tekstu i przyciski 64 px) |
| Jak współpracują komponenty | 6 |
| Korzyści | 2, 3 |
| Decyzje techniczne i dlaczego | 5, 6 |
| Możliwości i ograniczenia | 8, 9 |
| Jak użytkownik weryfikuje wyniki i zachowuje kontrolę | 7 |
| Jeden konkretny przypadek użycia | 3, 4 |

---

## CZĘŚĆ D. Prompt do generatora prezentacji

Wklej poniższy prompt do narzędzia (Claude, Gamma, Canva, Google Slides z Gemini itp.), a pod nim wklej **Część B** (dane) z uzupełnionymi polami `[UZUPEŁNIJ]`. Zrzuty ekranu i zdjęcia dołącz jako pliki albo zostaw na nie oznaczone miejsca.

````text
Jesteś doświadczonym projektantem prezentacji na hackathony technologiczne. Przygotuj prezentację projektu „Anioł Stróż” na HackYeah 2026 (Open Task „Artificial Intelligence”). Wszystko po polsku.

FORMAT I LIMITY
- Dokładnie 10 slajdów, 16:9, docelowo eksport do PDF. Nie więcej niż 10 slajdów, bez slajdów „dziękuję” ani agendy ponad limit.
- Jury ma kilka minut na przejrzenie PDF-a bez prezentera. Każdy slajd musi być zrozumiały bez komentarza.
- Maksymalnie ok. 40 słów tekstu głównego na slajd poza tabelami i podpisami. Duże, czytelne nagłówki będące wnioskiem (np. „AI znajduje dowody, człowiek decyduje”), a nie etykiety typu „Architektura”.
- Minimalny rozmiar tekstu 18 pt. Wysoki kontrast.

STYL
- Spokojny, godny zaufania, nowoczesny. Bez klip-artów i stockowych zdjęć uśmiechniętych ludzi.
- Kolorystyka pochodzi ze stanów produktu: zielony (spokój, ochrona działa), czerwony (alert), żółty (ostrzeżenie o stanie systemu), do tego neutralne szarości i ciemny granat. Font: Public Sans lub podobny bezszeryfowy.
- Ikony razem z tekstem tam, gdzie pokazujesz stan. Poziom ryzyka zawsze słowami (brak / niski / średni / wysoki), nigdy w procentach.
- Spójny układ na wszystkich slajdach, numer slajdu, mały znaczek „Anioł Stróż” w rogu.

STRUKTURA (jeden slajd = jedna myśl)
1. Tytuł: nazwa, hasło, HackYeah 2026 Open Task AI, zespół, linki (repozytorium, demo, film).
2. Problem: oszustwo „na policjanta”, scenariusz z etapami, istniejące zabezpieczenia nie działają w trakcie rozmowy.
3. Rozwiązanie: jak to działa w 6 krokach, prosty diagram przepływu (telefon -> Nasłuch -> backend -> alert w trzech miejscach), miejsce na zdjęcie urządzenia obok telefonu stacjonarnego.
4. Demo i moment „wow”: zrzuty ekranu alertu u seniora i w panelu rodziny, cytat oszusta podświetlony, pułapka z oddzwonieniem na 112, link do filmu.
5. Rola AI: dwie części (lokalne STT Vosk, Claude z rubryką etapów), osobno „co AI robi” i „czego AI nie robi”, krótka lista świadomie odrzuconych technik z jednym zdaniem uzasadnienia.
6. Architektura: czytelny diagram komponentów i przepływu danych, zaznacz granicę „dźwięk nie opuszcza urządzenia, do chmury idzie tylko tekst”, oraz co jest deterministyczne a co AI.
7. Zaufanie i kontrola: dosłowny cytat + walidator cytatów, reguły ryzyka na jednym małym schemacie, trzy decyzje seniora, uczciwe stany awarii, znaczek trybu. Użyj zrzutu ekranu z „Dlaczego?”.
8. Dowody: tabela 12 scenariuszy (słowa kluczowe vs AI vs razem) z kolorowymi znacznikami trafione/chybione oraz 3–4 zmierzone liczby (czas odpowiedzi, koszt, liczba testów). Podkreśl scenariusz 02, w którym tylko AI wykrywa oszustwo, oraz pokaż uczciwie scenariusze, w których zawodzi.
9. Prywatność i ograniczenia: tabela „dane -> dokąd -> czy zapisywane” i pięć najważniejszych ograniczeń podanych wprost.
10. Stan i dalsze kroki: co działa dziś (tylko to, co jest w danych jako zrobione), następne kroki, zespół, linki, skrócona lista ujawnień (modele, biblioteki, narzędzia AI, dane).

ZASADY TREŚCI (ważne)
- Używaj WYŁĄCZNIE faktów i liczb z dostarczonych danych. Niczego nie wymyślaj: żadnych statystyk, wyników, nazw, osób, cytatów ani zrzutów ekranu. Jeśli brakuje danych albo zostało pole [UZUPEŁNIJ], zostaw na slajdzie wyraźnie oznaczone puste miejsce (np. szara ramka „TU: zrzut ekranu alertu seniora”) i wypisz mi na końcu listę takich miejsc.
- Nie obiecuj funkcji, które w danych są w sekcji „następne kroki”. Mają być pokazane tylko jako plan.
- Zachowaj uczciwy ton: pokaż ograniczenia i znane błędy (np. scenariusz 06). Jury ma ocenić zrozumienie możliwości i granic AI, nie tylko sukces. Nie używaj słów „rewolucyjny”, „100%”, „niezawodny”, „gwarantuje”.
- Nie sugeruj, że system rozłącza rozmowy, dzwoni lub blokuje numery. Nigdy tego nie robi.
- Liczby podawaj tak, jak w danych, z zaznaczeniem, czy to pomiar, czy szacunek.
- Teksty widoczne dla użytkownika w produkcie (np. „Rozłączam się”, „Podstawowa ochrona (bez AI)”) zostaw dosłownie po polsku.

ODPOWIEDŹ
1. Najpierw krótka propozycja tytułów slajdów (10 linii) do mojej akceptacji.
2. Po akceptacji: pełna treść każdego slajdu (tytuł, tekst, opis grafiki/diagramu, dokładne miejsce na zrzut ekranu), notatki prelegenta po 2–3 zdania na slajd.
3. Na końcu: lista brakujących elementów, które muszę dodać, oraz lista rzeczy, które sprawdziłeś jako zgodne z zasadą „maksymalnie 10 slajdów”.
````

---

## CZĘŚĆ E. Lista kontrolna przed wysłaniem

- [ ] Dokładnie 10 slajdów lub mniej, PDF otwiera się poprawnie, czcionki osadzone.
- [ ] Wszystkie pola `[UZUPEŁNIJ]` wypełnione albo usunięte.
- [ ] Każdy zrzut ekranu ma widoczny znaczek trybu (LIVE / SCRIPTED / MOCK) i nie pokazuje klucza API ani prawdziwych danych.
- [ ] Linki działają w oknie incognito (repozytorium, film, demo). Link `trycloudflare.com` zmienia się przy każdym starcie, więc wpisz stały link do filmu.
- [ ] Liczby są pomiarami z logu audytu, nie szacunkami z `architecture.md`; szacunki oznaczone jako szacunki.
- [ ] Ujawnienie, że koncepcja i architektura powstały w dniu hackathonu (wcześniej tylko puste repozytorium git), narzędzi AI i danych syntetycznych jest na slajdzie 10 lub w opisie zgłoszenia.
- [ ] Brak obietnic funkcji niezrobionych.
- [ ] Termin: zgłoszenie przed 21:00 (plan zakłada margines do 23:00); potwierdź platformę (HackTribe czy Challenge Rocket).
