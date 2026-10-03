# Anioł Stróż: ograniczenia architektoniczne frontendu

Dotyczy: aplikacji webowej Angular serwowanej przez serwer WWW oraz aplikacji Android. Źródło: [ai-architecture-v2.md](ai-architecture-v2.md). Ograniczenie z „MUSI” / „NIE WOLNO” jest obowiązkowe; zmiana wymaga decyzji zespołu i wpisu w sekcji „Odstępstwa” na końcu.

## 1. Jak Android wpisuje się w architekturę

Dokument architektury zakłada jedną aplikację Angular z trasami `/senior`, `/family`, `/setup`, `/audit`. Android **nie jest osobnym frontendem z osobną logiką**. To ta sama aplikacja Angular opakowana w Capacitor, z kilkoma natywnymi dodatkami tam, gdzie przeglądarka nie wystarcza.

| Rola | Urządzenie | Forma | Po co Android |
|---|---|---|---|
| Senior | Tablet z Androidem przy telefonie stacjonarnym | Aplikacja Capacitor, tryb kiosku, trasa `/senior` | Ekran zawsze włączony, autostart, mikrofon bez ponownych pytań o zgodę, praca jako dedykowane urządzenie |
| Rodzina | Telefon z Androidem | Aplikacja Capacitor, trasa `/family` (opcjonalnie) | Powiadomienia push (FCM), gdy aplikacja jest zamknięta |
| Rodzina, jury, zespół | Dowolna przeglądarka | Aplikacja webowa z serwera WWW | `/family`, `/setup`, `/audit` bez instalacji |

**Kluczowe ograniczenie Androida:** urządzenie słucha **telefonu stacjonarnego akustycznie** (mikrofon tabletu przy głośniku telefonu). Android od wersji 10 nie pozwala zwykłym aplikacjom nagrywać dźwięku rozmów GSM prowadzonych na tym samym telefonie. Anioł Stróż **nie** chroni rozmów komórkowych na tym tablecie ani na telefonie seniora. Trzeba to powiedzieć jury wprost.

**Kolejność na hackathonie:** najpierw aplikacja webowa (działa na tablecie w Chrome). Opakowanie w Capacitor robimy dopiero, gdy pełny przepływ działa w przeglądarce. Push FCM jest opcjonalny.

## 2. Ograniczenia wspólne (web i Android)

| ID | Ograniczenie | Dlaczego |
|---|---|---|
| FE-01 | MUSI istnieć jedna baza kodu Angular dla web i Android. NIE WOLNO pisać osobnych ekranów w Kotlinie/Javie. | Zespół zna Angulara; dwie implementacje to podwójna praca i rozjazd zachowania. |
| FE-02 | Typy i serwisy HTTP MUSZĄ być generowane z `contracts/openapi.yaml` (openapi-generator, generator `typescript-angular`) do `frontend/src/app/api/`. Kod wygenerowany nie jest edytowany ręcznie. NIE WOLNO kopiować ani definiować typów API ponownie. | Jeden kontrakt z backendem. |
| FE-03 | Frontend NIE WOLNO wywoływać Claude ani STT bezpośrednio. Wszystko idzie przez backend. | Klucze API nie mogą trafić do przeglądarki ani do APK; backend waliduje cytaty i liczy ryzyko. |
| FE-04 | Frontend NIE WOLNO liczyć poziomu ryzyka ani generować tekstów alertów. Wyświetla `RiskUpdate`, `Alert.templateId` i tekst szablonu z backendu. | Logika deterministyczna jest w jednym miejscu (architektura, sekcja 5). |
| FE-05 | Każdy ekran MUSI pokazywać znaczek trybu (LIVE / REPLAY / SCRIPTED / MOCK) i stan połączenia. | Uczciwość wobec jury i użytkownika (sekcja 10). |
| FE-06 | Ekran seniora NIE MOŻE być zielony, gdy backend, audio lub STT nie działa. Priorytet stanów: offline > nie słyszę > wstrzymana > podstawowa ochrona > chroniony. | System nigdy nie milczy przy awarii (sekcja 6.6). |
| FE-07 | Poziom ryzyka MUSI być pokazywany słowami. NIE WOLNO pokazywać procentów ani „pewności AI”. | Brak prawdziwie wyliczonej pewności (sekcja 7). |
| FE-08 | Każdy alert MUSI dać dostęp do dowodów: nazwa etapu, dosłowny cytat, czas, źródło (AI / słowa kluczowe / oba). | Weryfikowalność wyników. |
| FE-09 | Aplikacja NIE MOŻE sama rozłączać rozmowy, dzwonić ani wysyłać wiadomości. Przycisk „Zadzwoń do…” pokazuje zapisany numer albo otwiera `tel:` dopiero po dotknięciu przez człowieka. | Człowiek decyduje (sekcja 7). |
| FE-10 | Numer do oddzwonienia MUSI pochodzić z ustawień. NIE WOLNO pokazywać numeru z treści rozmowy. | Oszust podaje „numer weryfikacyjny”. |
| FE-11 | Audio NIE MOŻE być zapisywane na urządzeniu: bez MediaRecorder, IndexedDB, plików, cache. | Prywatność (sekcja 6.7). |
| FE-12 | `localStorage` tylko na wygodę (język, zwinięte panele), zawsze w try/catch. Ustawienia, alerty i decyzje są w backendzie. | Stan współdzielony i trwały jest po stronie serwera. |
| FE-13 | Wszystkie wiadomości z WebSocket MUSZĄ być walidowane w czasie działania przez Ajv (tryb JSON Schema 2020-12) względem `components/schemas/EventEnvelope` z `openapi.yaml`; błędne są odrzucane z ostrzeżeniem w konsoli. | Odporność na zmiany kontraktu. |
| FE-14 | Teksty dla seniora po polsku; kod, identyfikatory i komentarze po angielsku. | Spójność z backendem. |

## 3. Aplikacja webowa Angular i serwer WWW

| ID | Ograniczenie | Dlaczego |
|---|---|---|
| WEB-01 | Budujemy aplikację jako statyczny SPA (`ng build`). Bez SSR. | Aplikacja działa w czasie rzeczywistym po stronie klienta; SSR nic nie daje, a komplikuje WebSocket i dostęp do mikrofonu. |
| WEB-02 | Serwer WWW (nginx albo Caddy) serwuje pliki statyczne i działa jako reverse proxy dla `/api` i `/ws` do backendu, z obsługą upgrade WebSocket (`Upgrade`, `Connection`, długi `proxy_read_timeout`). | Jeden origin: brak problemów z CORS i cookies. |
| WEB-03 | Serwer MUSI działać po HTTPS (również na demo w sieci lokalnej). Wyjątek tylko dla `localhost`. | `getUserMedia` (mikrofon) i powiadomienia działają tylko w secure context. Na hackathonie: Caddy z certyfikatem albo tunel HTTPS. |
| WEB-04 | Fallback SPA: każda nieznana ścieżka zwraca `index.html`. | Trasy `/senior`, `/family` po odświeżeniu. |
| WEB-05 | Nagłówki bezpieczeństwa: `Content-Security-Policy` (skrypty tylko z własnego origin, `connect-src` tylko własny origin i `wss:` tego samego hosta), `Permissions-Policy: microphone=(self)`, `Referrer-Policy: no-referrer`. | Ochrona przed wstrzyknięciem kodu; mikrofon tylko dla naszej aplikacji. |
| WEB-06 | `index.html` bez cache, zasoby z hashem z długim cache. | Po wdrożeniu poprawki wszyscy dostają nową wersję. |
| WEB-07 | Zgoda na mikrofon i odtwarzanie dźwięku MUSI być uruchomiona gestem użytkownika (przycisk „Włącz ochronę”). | Przeglądarki blokują autoplay i syntezę mowy bez interakcji. |
| WEB-08 | Przechwytywanie audio przez AudioWorklet, konwersja do 16 kHz mono Int16, ramki 100 ms. NIE WOLNO wysyłać WebM/Opus z MediaRecorder. | Kontrakt audio z backendem (architektura, „INTEGRATION CONTRACTS”). |
| WEB-09 | Synteza mowy przez `speechSynthesis` z głosem `pl-PL`. Gdy brak głosu polskiego, pokaż tekst i komunikat; nie zgłaszaj błędu. | Dostępność bez dodatkowego API. |
| WEB-10 | Docelowe przeglądarki: Chrome/Edge (aktualne) na tablecie i laptopie, Chrome na Androidzie, Safari na iOS tylko dla `/family`. | Ograniczony czas testów; AudioWorklet i speechSynthesis najstabilniejsze w Chromium. |
| WEB-11 | Ekran `/senior`: tekst min. 28 px, przyciski min. 64 px (decyzje 72 px), kontrast min. 7:1, stan zawsze ikona + tekst, bez przewijania w stanie alertu, `aria-live="assertive"` dla alertu. | Użytkownik 75+. |
| WEB-12 | Ekran `/family` mobile first od szerokości 360 px; `/audit` czytelny na zrzucie 16:9. | Rodzina na telefonie; audyt na slajdzie. |
| WEB-13 | Biblioteki zewnętrzne tylko z npm, wpisane do listy ujawnień (licencja + cel). Bez skryptów z CDN w produkcji. | Wymóg ujawnienia i CSP. |

## 4. Aplikacja Android (Capacitor)

| ID | Ograniczenie | Dlaczego |
|---|---|---|
| AND-01 | Android to powłoka Capacitor wokół tej samej aplikacji Angular (`npx cap add android`). Natywny kod tylko jako wtyczki Capacitor, gdy przeglądarka nie wystarcza. | FE-01. |
| AND-02 | Aplikacja ładuje zbudowane pliki z APK i łączy się z backendem przez `https://` / `wss://` (adres z konfiguracji builda). NIE WOLNO włączać ruchu nieszyfrowanego (`cleartext`) poza buildem deweloperskim. | Bezpieczeństwo, spójność z WEB-03. |
| AND-03 | Uprawnienia w manifeście: `RECORD_AUDIO`, `INTERNET`, `WAKE_LOCK`; `POST_NOTIFICATIONS` tylko w wersji rodziny. NIE WOLNO prosić o `READ_PHONE_STATE`, `READ_CALL_LOG`, `CALL_PHONE`, `ANSWER_PHONE_CALLS`, dostęp do SMS ani kontaktów. | Minimalne uprawnienia; aplikacja nie steruje telefonem. |
| AND-04 | Wersja seniora: ekran stale włączony (`keepScreenOn`), orientacja dowolna, uruchamianie w trybie przypięcia ekranu / kiosku (screen pinning lub tryb dedykowanego urządzenia, jeśli zespół zdąży). | Urządzenie stoi przy telefonie i ma działać bez obsługi. |
| AND-05 | Ciągłe słuchanie w tle (wyłączony ekran, inna aplikacja na wierzchu) wymaga usługi pierwszoplanowej (foreground service typu `microphone`) ze stałym powiadomieniem „Anioł Stróż słucha”. Na hackathonie NIE implementujemy tego: aplikacja seniora działa tylko na pierwszym planie, a ekran jest stale włączony. Jeśli aplikacja traci mikrofon, pokazuje „Nie słyszę rozmowy”. | Android ogranicza dostęp do mikrofonu w tle; uczciwy stan zamiast cichej awarii. |
| AND-06 | Android NIE słucha rozmów GSM na tym samym urządzeniu (patrz sekcja 1). Opis w sklepie, PDF i UI mówi o telefonie stacjonarnym. | Ograniczenie systemu Android, nie nasza decyzja. |
| AND-07 | Push dla rodziny (opcjonalnie): Firebase Cloud Messaging przez `@capacitor/push-notifications`. Treść powiadomienia: poziom i jedno zdanie, **bez cytatów z rozmowy**. Cytaty dopiero po otwarciu aplikacji. Token FCM rejestrowany w backendzie. | Minimalizacja danych w systemach Google; push to kolejny zewnętrzny serwis do ujawnienia. |
| AND-08 | Mikrofon w WebView: zgoda systemowa `RECORD_AUDIO` przed `getUserMedia`; obsłuż odmowę czytelnym komunikatem po polsku. | WebView wymaga obu zgód. |
| AND-09 | Minimalna wersja: Android 10 (API 29). Testujemy na jednym konkretnym tablecie i jednym telefonie zespołu. | Ograniczony czas testów. |
| AND-10 | APK podpisujemy kluczem deweloperskim zespołu; NIE publikujemy w Google Play podczas hackathonu. Link do APK w zgłoszeniu tylko jeśli działa bez kont. | Brak czasu na proces publikacji. |
| AND-11 | Klucze API NIE MOGĄ znaleźć się w APK (APK łatwo zdekompilować). | FE-03. |

## 5. Czego frontend nie robi (lista zamknięta)

- Nie wywołuje modeli AI ani STT.
- Nie liczy ryzyka, nie tworzy tekstów alertów.
- Nie rozłącza, nie dzwoni, nie blokuje numerów, nie wysyła SMS.
- Nie zapisuje audio ani transkrypcji na urządzeniu.
- Nie ma kont użytkowników ani logowania (jedno gospodarstwo domowe w demo; wymienić jako ograniczenie).
- Nie chroni rozmów komórkowych.

## 6. Do potwierdzenia na starcie

- Czy na sali da się wystawić HTTPS (Caddy + domena lub tunel); bez tego mikrofon działa tylko na `localhost`.
- Czy zespół ma tablet z Androidem 10+ i polskim głosem syntezy mowy.
- Czy FCM (konto Firebase) jest warte czasu, czy wystarczy otwarty panel `/family`.

## Odstępstwa

| Data | ID | Zmiana | Kto zdecydował | Dlaczego |
|---|---|---|---|---|
| 2026-10-03 | FE-01 | Zamiast jednej aplikacji z trasami `/senior`, `/family`, `/setup`, `/audit` są trzy buildy z jednej bazy kodu (`fileReplacements` w `angular.json`): `senior` (`/senior`, telefon lub tablet seniora, port 4201), `listen` (`/listen`, nowa trasa: tablet z mikrofonem przy telefonie stacjonarnym, port 4202), `family` (`/family`, `/setup`, `/audit`, port 4203). `/listen` łączy się z `/ws/events` z rolą `senior`, `/audit` z rolą `audit`. Logika jest wspólna, więc FE-01 („jedna baza kodu”) dalej obowiązuje. | Zespół przy FE-01 (wpis z review FF-05, do potwierdzenia przez zespół) | Słuchanie rozmowy i ekran seniora to dwa różne urządzenia. Każdy bundle zawiera tylko swoje ekrany, więc tablet przy telefonie nie ma paneli rodziny ani audytu. |
| 2026-10-03 | WEB-07 | Ekran Nasłuch (`/listen`) uruchamia mikrofon sam, bez dotknięcia, i ponawia próbę co 5 s po awarii. Przycisk „Włącz ochronę” zostaje tylko jako zapas, gdy przeglądarka zablokuje automatyczny start. Na `/senior` przycisk „Włącz ochronę” zostaje, bo odblokowuje głos (synteza mowy). | Zespół (polecenie użytkownika: ochrona włączona cały czas, senior ma jak najmniej klikać) | Senior nie obsługuje urządzenia nasłuchującego. Pierwsze zezwolenie na mikrofon trzeba dać raz przy konfiguracji; potem przeglądarka z zapamiętanym zezwoleniem startuje sama. |
