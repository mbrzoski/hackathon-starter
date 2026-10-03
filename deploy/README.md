# deploy: serwer WWW dla demo

## Najkrócej

| Cel | Komenda | Certyfikaty? |
|---|---|---|
| Praca i testy na laptopie | `make run-backend` + `make run-frontend`, potem `make e2e` | **Nie.** `http://localhost` to bezpieczny kontekst, mikrofon działa. |
| Demo na urządzeniach w tej samej sieci Wi-Fi | `make demo` (test dymny uruchamia się sam), potem `make e2e-demo` | Raz na urządzenie: otwórz `http://<adres-laptopa>` i dotknij „Pobierz ca.crt”. |
| Demo bez instalowania certyfikatów | `make demo-tunnel` | **Nie.** Publiczny adres `*.trycloudflare.com` z prawdziwym certyfikatem. |

`make demo` buduje frontend i backend, uruchamia Dockera i sprawdza serwer testem dymnym. Na koniec wypisuje adres strony startowej, np. `http://192.168.1.50`. Ten adres wpisujesz na tablecie i telefonie. Strona ma przycisk „Pobierz ca.crt”, krótką instrukcję i przyciski Senior, Nasłuch i Panel rodziny. Na laptopie certyfikat dodaje jedna komenda: `deploy/trust-ca.sh`.

Caddy serwuje trzy aplikacje frontendu i jest reverse proxy dla backendu. Wszystko działa pod **jednym adresem HTTPS**:

| Ścieżka | Aplikacja | Urządzenie |
|---|---|---|
| `/senior` | aplikacja seniora | telefon komórkowy seniora |
| `/listen` | Nasłuch | telefon albo tablet przy telefonie, na którym toczy się rozmowa |
| `/family`, `/setup`, `/audit` | panel rodziny | telefon albo komputer rodziny |
| `/api/*`, `/ws/*` | backend (proxy) | — |

Jeden origin oznacza jeden certyfikat, jeden adres tunelu i brak CORS (WEB-02). Pliki trzech buildów różnią się tylko `index.html`. `run-demo.sh` kopiuje je do `deploy/site/` i zmienia nazwy na `index-senior.html`, `index-listen.html` i `index-family.html`.

## Zawartość

| Plik | Do czego |
|---|---|
| `run-demo.sh` | Buduje frontend i backend, składa `site/`, uruchamia całość w Dockerze. |
| `docker-compose.yml` | `backend` (obraz z `backend/Dockerfile`, Java 21 JRE), `caddy`, opcjonalnie `cloudflared` (profil `tunnel`). |
| `caddy/aniol.caddy` | Wspólna konfiguracja: pliki statyczne, fallback SPA, proxy, nagłówki bezpieczeństwa, cache. |
| `caddy/Caddyfile.lan` | Wariant (a): HTTPS w sieci lokalnej z własnym CA Caddy. |
| `caddy/Caddyfile.tunnel` | Wariant (b): HTTP za tunelem HTTPS (Cloudflare Tunnel). |
| `caddy/start.html` | Strona startowa pod `http://<adres>/`: certyfikat i przyciski do trzech aplikacji. |
| `smoke-test.mjs` | Test dymny serwera (czysty Node): trasy, nagłówki, cache, TLS sprawdzany pobranym `ca.crt`, API, WebSocket, strona startowa. `make smoke`. |
| `trust-ca.sh` | Dodaje CA demo do zaufanych na tym laptopie (macOS: pęk kluczy logowania, Linux: `update-ca-certificates`). |
| `../frontend/e2e/uc-01.mjs` | Test UC-01 w Chrome: trzy aplikacje naraz, scenariusz, alert, decyzje. `make e2e` (dev) i `make e2e-demo` (demo). |

## Wymagania

- Docker Desktop (uruchomiony), Node.js i JDK 21 na laptopie z demo (frontend i jar budują się na laptopie).
- Opcjonalnie `.env` w katalogu głównym repozytorium (wzór: `.env.example`). Bez `ANTHROPIC_API_KEY` skrypt sam ustawia `APP_MODE=MOCK`, bo profil `prod` bez klucza nie startuje. Znaczek trybu pokazuje wtedy „MOCK”.

## Wariant (a): HTTPS w sieci lokalnej (domyślny)

```bash
deploy/run-demo.sh
```

Skrypt wykrywa adres laptopa w Wi-Fi (`ipconfig getifaddr en0`; inny adres: `LAN_IP=192.168.1.50 deploy/run-demo.sh`), zapisuje CA do `deploy/ca.crt`, uruchamia test dymny i wypisuje adres strony startowej (z `brew install qrencode` także kod QR). Caddy wystawia certyfikat ze **swojego CA** (`tls internal`). Każde urządzenie musi raz zaufać temu CA, inaczej przeglądarka pokaże ostrzeżenie, a mikrofon i WebSocket nie zadziałają.

Przeglądarka łącząca się po adresie IP nie wysyła nazwy serwera (SNI). Dlatego skrypt przekazuje adres laptopa jako `default_sni`, a Caddy w kontenerze wie, który certyfikat podać. Skrypt sam dopisuje też do `PATH` narzędzia Docker Desktop (`docker-credential-desktop`), bez których pobieranie obrazów się nie udaje.

### Instalacja certyfikatu CA na Androidzie (tablet albo telefon)

1. Urządzenie w tej samej sieci Wi-Fi co laptop.
2. W Chrome otwórz `http://<adres-laptopa>`, np. `http://192.168.1.50` (zwykłe HTTP, bez „s”), i dotknij „Pobierz ca.crt”.
3. Ustawienia → Bezpieczeństwo i prywatność → Więcej ustawień zabezpieczeń → Szyfrowanie i dane logowania → Zainstaluj certyfikat → **Certyfikat CA** → „Zainstaluj mimo to” → wybierz `ca.crt`. Nazwy menu różnią się między producentami. Szukaj „Zainstaluj certyfikat” w wyszukiwarce ustawień.
4. Sprawdzenie: Ustawienia → … → Zaufane dane logowania → zakładka Użytkownik. Powinien tam być „Caddy Local Authority”.
5. Wróć na stronę startową i dotknij przycisku aplikacji tego urządzenia. Kłódka bez ostrzeżeń oznacza sukces.

Chrome na Androidzie ufa certyfikatom CA dodanym przez użytkownika. Przyszła aplikacja Capacitor (AND-01) będzie potrzebowała `network_security_config` z zaufaniem do certyfikatów użytkownika albo innego rozwiązania. iPhone (np. telefon rodziny): otwórz `http://<adres>/ca.crt` w Safari, zainstaluj profil (Ustawienia → Pobrany profil), potem Ustawienia → Ogólne → To urządzenie → Ustawienia zaufania certyfikatów → włącz „Caddy Local Authority”. Laptop: `deploy/trust-ca.sh` (macOS zapyta raz o hasło).

CA trzyma wolumen `caddy-data`. Dopóki go nie usuniesz (`docker compose down -v`), zainstalowany certyfikat działa przy każdym kolejnym uruchomieniu.

### Ręcznie do zrobienia (wariant a)

- Zgoda Zapory macOS na połączenia przychodzące do Dockera na portach 80 i 443, jeśli macOS o nią zapyta.
- Instalacja certyfikatu CA na każdym urządzeniu (wyżej).
- Sprawdzenie, czy Wi-Fi hackathonu pozwala urządzeniom łączyć się ze sobą. Sieci z izolacją klientów na to nie pozwalają. Wtedy: hotspot z telefonu albo wariant (b).
- Adres laptopa zmienia się po ponownym podłączeniu do Wi-Fi. Wtedy uruchom skrypt jeszcze raz: certyfikat CA zostaje, zmienia się tylko adres.

## Wariant (b): tunel HTTPS (plan B)

```bash
deploy/run-demo.sh tunnel
```

Kontener `cloudflared` otwiera „quick tunnel” Cloudflare (bez konta) do Caddy na porcie 80. Skrypt wypisuje publiczny adres `https://<losowe-słowa>.trycloudflare.com`. Certyfikat jest publiczny, więc na urządzeniach nic nie trzeba instalować. Urządzenia potrzebują tylko internetu, a nie tej samej sieci.

TLS kończy się w Cloudflare, a do Caddy przychodzi zwykłe HTTP z `X-Forwarded-Proto: https`. Caddy ufa temu nagłówkowi tylko od adresów prywatnych (`trusted_proxies private_ranges`, czyli kontenera `cloudflared`) i przekazuje go backendowi. Backend widzi wtedy publiczny origin, więc kontrola originu WebSocketu („ten sam origin”) przechodzi bez wpisywania losowego adresu do konfiguracji.

### Ręcznie do zrobienia (wariant b)

- Internet na laptopie i urządzeniach.
- Adres zmienia się przy każdym starcie. Trzeba go za każdym razem otworzyć na urządzeniach (np. kod QR z adresu).
- Adres jest publiczny, a aplikacja nie ma logowania. Uruchamiaj tunel tylko na czas demo i zamknij go zaraz po nim (Ctrl+C).
- Quick tunnel nie ma gwarancji dostępności. Stały adres wymaga konta Cloudflare i nazwanego tunelu (`cloudflared tunnel create`) z własną domeną.

## Sprawdzenie

```bash
make smoke      # test dymny działającego demo (robi to też make demo)
make e2e-demo   # UC-01 w Chrome na działającym demo
```

Ręcznie:

```bash
# -k pomija weryfikację certyfikatu; zamiast tego: --cacert ca.crt (pobrany z http://<adres>/ca.crt)
curl -kI https://localhost/senior
curl -kI https://localhost/main-XXXXXXXX.js   # dowolny plik z hashem z deploy/site
curl -k  https://localhost/api/status
```

Oczekiwane dla `/senior`: `200`, `cache-control: no-cache`, `content-security-policy: default-src 'self'; connect-src 'self' wss://<host>; script-src 'self'; …`, `permissions-policy: microphone=(self)`, `referrer-policy: no-referrer`, `x-content-type-options: nosniff`. Dla plików z hashem w nazwie: `cache-control: public, max-age=31536000, immutable`. `http://<adres>/` to strona startowa, `/ca.crt` to certyfikat, a wszystko inne przekierowuje na HTTPS.

Uwagi do CSP (WEB-05):

- `font-src` ma też `'self'`. Font Public Sans jest w buildzie (WEB-13), z Google Fonts nic nie pobieramy.
- Build produkcyjny nie wstawia skryptów inline. Inline'owanie krytycznego CSS jest wyłączone w `angular.json`, bo dodawało skrypt inline. Walidator zdarzeń kompiluje się przy `npm run generate:api` (Ajv standalone), więc w przeglądarce nie ma `eval` ani `new Function`.

## Zatrzymanie i dane

- Ctrl+C w terminalu ze skryptem zatrzymuje kontenery (`docker compose down`).
- Baza SQLite i `labels.jsonl` są w wolumenie `backend-data`, a CA Caddy w `caddy-data`. Usunięcie wszystkiego: `docker compose -f deploy/docker-compose.yml down -v`. Wtedy certyfikat CA na urządzeniach trzeba zainstalować od nowa.

## Bez Dockera

Gdy Docker nie działa: zbuduj tak jak `run-demo.sh` (frontend, jar, `deploy/site`), a potem uruchom backend i Caddy ręcznie. Caddy: [caddyserver.com/download](https://caddyserver.com/download) albo `brew install caddy`.

Porty 8088 (HTTP) i 8443 (HTTPS) nie wymagają `sudo`:

```bash
SPRING_PROFILES_ACTIVE=prod APP_MODE=MOCK APP_EVENTS_ALLOWED_ORIGINS=https://192.168.1.50:8443 \
  java -jar backend/target/backend-0.0.1-SNAPSHOT.jar

cd deploy/caddy
SITE_ADDRESS="https://192.168.1.50" DEFAULT_SNI=192.168.1.50 HTTP_PORT=8088 HTTPS_PORT=8443 \
  BACKEND=localhost:8080 SITE_ROOT=../site CADDY_DIR=. \
  CADDY_CA_DIR="$HOME/Library/Application Support/Caddy/pki/authorities/local" \
  caddy run --config Caddyfile.lan --adapter caddyfile
```

Adresy mają wtedy port: strona startowa pod `http://192.168.1.50:8088`, aplikacje pod `https://192.168.1.50:8443/senior`. Przyciski strony startowej prowadzą wtedy na port 443, więc aplikacje trzeba otwierać ręcznie. Tak był sprawdzany ten katalog (Caddy 2.11, bez Dockera).

## Gdy coś nie działa

| Objaw | Przyczyna i rozwiązanie |
|---|---|
| `make demo`: „Docker is not running” | Uruchom Docker Desktop. |
| Wszystkie aplikacje dev pokazują „offline” po `git pull` | Serwery `npm start` serwują stary kod (`generate:api` odtwarza `src/app/api`). Zatrzymaj je i uruchom `make run-frontend` ponownie. |
| Ostrzeżenie o certyfikacie na urządzeniu | Certyfikat CA nie jest zainstalowany albo pochodzi z innego CA (po `down -v`). Pobierz go jeszcze raz ze strony startowej. |
| Urządzenie nie otwiera `http://<adres>` | Wi-Fi z izolacją klientów albo inna sieć. Hotspot z telefonu albo `make demo-tunnel`. |
| Backend nie startuje w Dockerze | Brak `ANTHROPIC_API_KEY` przy trybie innym niż MOCK. `make demo` przełącza wtedy na MOCK sam, a przy ręcznym `docker compose` ustaw `APP_MODE=MOCK`. |
