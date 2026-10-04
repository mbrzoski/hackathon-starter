# Anioł Stróż: ustalenia do prezentacji (PDF, maks. 10 slajdów)

Plik uzupełniany na bieżąco odpowiedziami zespołu. Źródło danych wyjściowych: `docs/prezentacja-dane-i-prompt.md`.

## Slajd 1. Tytuł

- Nazwa: Anioł Stróż
- Hasło: „Ostrzega seniora w trakcie rozmowy. Pokazuje dosłowne słowa oszusta. Decyzję zawsze podejmuje człowiek.”
- Podtytuł: HackYeah 2026, Open Task „Artificial Intelligence”
- Linki na slajdzie: repozytorium, film z dema (adresy: `[UZUPEŁNIJ]`)
- Grafika: zrzut ekranu alertu u seniora (czerwony ekran z cytatem, ze znaczkiem trybu)
- Zespół: imiona i role (`[UZUPEŁNIJ]`)

## Slajd 2. Problem

- Hak otwierający: oś scenariusza „na policjanta” krok po kroku (autorytet, groźba, tajemnica, izolacja, pieniądze, kanał płatności), czyli oszustwo ma rozpoznawalne etapy.
- Luka obecnych zabezpieczeń: nie działają w trakcie rozmowy (blokady numerów, opóźnienia przelewów i kampanie działają przed lub po).
- Liczba o skali problemu: brak (nie podajemy bez publicznego źródła).

## Slajd 3. Rozwiązanie

- Forma: diagram 4 elementów (telefon seniora -> Nasłuch -> backend -> alert), pod spodem jedno zdanie o każdym kroku.
- Wyjaśnienie jednym zdaniem: Nasłuch to osobne urządzenie (Android nie pozwala nagrywać rozmowy na tym samym telefonie), stoi obok i słucha głośnika.
- Odbiorcy alertu (wszystkie trzy): `/senior` (czerwony ekran, głos, „Dlaczego?”, 3 przyciski), `/family` (poziom, etapy, cytaty z kontekstem, sygnał dźwiękowy), `/listen` (stan i alarm przy urządzeniu).
- Bez zdjęcia urządzenia, sam diagram.

## Slajd 4. Demo i „wow”

- Scena: tylko klasyczny fałszywy policjant (scenariusz 01): alert po zdaniu o tajemnicy i wypłacie gotówki, równocześnie u seniora i w panelu rodziny.
- Bez sceny 02 (omówienia), 12 (pułapka 112) i 06 (telewizor) na tym slajdzie.
- Tryb filmu: LIVE (mikrofon, prawdziwy Vosk i Claude). Tryb podany na slajdzie.
- Zrzuty ekranu: alert u seniora oraz panel rodziny (ze znaczkiem trybu). Link do filmu: `[UZUPEŁNIJ]`.

## Slajd 5. Rola AI

- Układ: dwie kolumny „AI robi” / „AI nie robi”.
  - AI robi: lokalne STT (Vosk) oraz Claude, który zwraca tylko etap + dosłowny cytat + rolę mówiącego (rozmówca / senior / tło / niejasne).
  - AI nie robi: nie liczy ryzyka, nie pisze tekstów alertów, nie decyduje o powiadomieniach, nie rozłącza, nie dzwoni, nie blokuje numerów (to deterministyczny kod i człowiek).
- 8 etapów jako ikony z podpisem: autorytet, groźba/presja, tajemnica, izolacja, prośba o pieniądze, kanał płatności, zdalny dostęp, dane osobowe.
- Odrzucone techniki, jedna linijka: bez narzędzi (function calling), RAG, agentów i MCP, bo rubryka mieści się w jednym prompcie, a AI nie podejmuje żadnych akcji.
- Podpisy techniczne: Claude Sonnet 5.5 (effort low, bez myślenia, structured output); Vosk `vosk-model-small-pl-0.22`, lokalnie i offline; rubryka (ok. 3,5 tys. tokenów) w cache, transkrypcja tylko przyrasta.

## Slajd 6. Architektura

- Diagram: poziomy potok z granicą prywatności. Nasłuch -> backend (Vosk, słowa kluczowe, klasyfikator Claude, QuoteValidator, silnik ryzyka, szablony alertów, log audytu) -> trzy ekrany (`/senior`, `/family`, `/listen`).
- Granica prywatności zaznaczona na diagramie: dźwięk zostaje na urządzeniu, do chmury (Claude API) idzie tylko tekst.
- Kolory: (1) AI vs deterministyczne (AI dostarcza tylko dowody, decyzje w kodzie), (2) lokalnie vs chmura (Vosk lokalnie, Claude w chmurze). Bez oznaczania trybów.
- Stos na slajdzie: Java 21 + Spring Boot 3; Angular + Material (trzy aplikacje z jednej bazy kodu); kontrakt OpenAPI jako jedyne źródło prawdy (rekordy Javy i typy TS z jednego pliku). Bez Caddy/Docker/SQLite.

## Slajd 7. Zaufanie i kontrola

- Wyróżnione mechanizmy: (1) dosłowny cytat + `QuoteValidator` (cytat spoza transkrypcji nie wpływa na ryzyko), (2) poziom ryzyka słowami, bez procentów.
- Reguły w 3 linijkach: wysoki = prośba o pieniądze lub kanał płatności + co najmniej jedno z: autorytet, groźba, tajemnica, izolacja; średni = dwa różne etapy; niski = jeden etap, zapis bez pokazywania seniorowi.
- Zrzut ekranu: rozwinięte „Dlaczego?” u seniora (cytaty z podpisem źródła: AI / słowa kluczowe / oba).
- Poza tym slajdem (decyzja użytkownika): trzy decyzje seniora i uczciwe stany awarii nie są tu wyróżnione. Zasadę „system nigdy nie rozłącza, nie dzwoni, nie blokuje” trzeba i tak gdzieś powiedzieć (slajd 3, 5 lub 9, do ustalenia).

## Slajd 8. Dowody

- Główny dowód: tabela tylko dla scenariuszy 01–03 (słowa kluczowe vs AI vs razem), opisana jako próbka, nie pełna ewaluacja 12 scenariuszy. Wyniki do uzupełnienia po uruchomieniu z prawdziwym Claude: `[UZUPEŁNIJ]`.
- Jedyna liczba: czas od zdania do alertu w trybie LIVE (`[UZUPEŁNIJ]`; dotąd zmierzono tylko Claude: 2,5–3,9 s na wywołanie, jedna rozmowa). Bez kosztu, liczby testów i trafności Vosk.
- Porażki pokazane wprost: scenariusze 06 (telewizor w tle, fałszywy alarm ze słów kluczowych) i 12 (pułapka 112, poziom średni zamiast wysokiego) oznaczone jako znane błędy.
- Do rozstrzygnięcia: 06 i 12 leżą poza zakresem 01–03, więc albo dodać je jako dwa wiersze „porażek” do tabeli, albo pokazać w tekście pod nią.

## Slajd 9. Prywatność i ograniczenia

- Układ: po lewej tabela „dane -> dokąd -> czy zapisywane” (dźwięk: tylko lokalny Vosk, nigdy zapisywany; tekst: Claude API, w pamięci, na dysk tylko przy alercie; ustawienia, kontakty i numery: nigdy do Claude ani STT; klucze API: tylko zmienne środowiskowe). Po prawej ograniczenia.
- Ograniczenia wymienione wprost (wybrane 2): hałas i mały model Vosk (częstsze błędy niż w chmurowym STT); nie zawsze wiadomo, kto mówi (rozmówca, senior, telewizor).
- Pominięte na slajdzie (decyzja użytkownika): alert po decydującym zdaniu, fałszywe alarmy i nowe scenariusze, RODO / przetwarzanie mowy osoby dzwoniącej.
- Dwa krótkie punkty o kontroli: system nigdy nie rozłącza, nie dzwoni i nie blokuje numerów; uczciwe stany awarii (nigdy zielony, gdy system nie słyszy; „Podstawowa ochrona (bez AI)”).
- Uwaga: układ zakładał 5 ograniczeń, wybrano 2. Dokument architektury zaleca nazwać RODO jako następny krok, więc warto je dodać choćby do slajdu 10.

## Slajd 10. Stan, plan, zespół, ujawnienia

- Stan (tylko to, co działa; potwierdzić przez `make test` i `make e2e`): rdzeń (kontrakty, alerty, decyzje, trzy ekrany, tryb SCRIPTED, słowa kluczowe, silnik ryzyka) oraz LIVE (Vosk, mikrofon, HTTPS). Klasyfikator Claude, walidator i log audytu nie są wymienione w stanie (nie wybrano), choć pojawiają się na slajdach 5–7.
- Następne kroki: kreator zgód i ustawień, ekran audytu; Android (Capacitor, kiosk) i push/SMS; ocena RODO i pilotaż z rodzinami. Bez adaptera linii.
- Zespół: imiona i role `[UZUPEŁNIJ]`; linki: repozytorium i film `[UZUPEŁNIJ]`.
- Ujawnienia: skrót na slajdzie (Claude Sonnet 5.5 przez Claude API, Vosk + model `vosk-model-small-pl-0.22`, Spring Boot, Angular, Claude / Claude Code jako narzędzia AI, dane syntetyczne) i odesłanie do pełnej listy w `README.md`.
- Jedno zdanie o dokumencie architektury: koncepcja i architektura powstały w dniu hackathonu z pomocą Claude; przed hackathonem istniało tylko puste repozytorium git; cały kod, prompty, dane, nagrania i UI powstały podczas HackYeah.

---

## Do domknięcia przed generowaniem

1. Zespół (nazwa, imiona, role), adres repozytorium, adres filmu.
2. Zrzuty ekranu: alert u seniora (slajd 1 i 4), panel rodziny (slajd 4), rozwinięte „Dlaczego?” (slajd 7).
3. Film w trybie LIVE (scenariusz 01) i pomiar czasu od zdania do alertu.
4. Tabela 01–03: słowa kluczowe vs AI vs razem, uruchomiona z prawdziwym Claude.
5. Rozstrzygnięcia: gdzie pokazać porażki 06 i 12 (slajd 8); czy dodać RODO (slajd 9 lub 10); czy w stanie na slajdzie 10 wymienić AI i audyt.
6. Slajd 9 ma 2 ograniczenia zamiast 5; slajd 7 nie wyróżnia decyzji człowieka (jest na slajdzie 9 jako zasada).
