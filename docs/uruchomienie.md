# Uruchomienie Anioła Stróża po sklonowaniu repozytorium

Instrukcja dla Linuksa i Windowsa. Pełne szczegóły (demo przez HTTPS, certyfikaty na telefonach): `README.md` i `deploy/README.md`.

## Wymagania (Linux i Windows)

- **JDK 21**
- **Node.js** (z npm)
- **Google Chrome** (mikrofon i testy end-to-end)

## Linux

```bash
git clone https://github.com/mbrzoski/hackathon-starter.git
cd hackathon-starter

# opcjonalnie: klucz do prawdziwego Claude (bez niego działa tryb MOCK z gotowymi odpowiedziami)
export ANTHROPIC_API_KEY=sk-ant-...

# terminal 1: backend
make run-backend            # albo: cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# terminal 2: trzy aplikacje frontendu
make run-frontend           # albo: cd frontend && npm install && npm start
```

## Windows (PowerShell)

Na Windowsie zwykle nie ma `make`, więc uruchamiamy komendy bezpośrednio.

```powershell
git clone https://github.com/mbrzoski/hackathon-starter.git
cd hackathon-starter

# opcjonalnie: klucz do prawdziwego Claude
$env:ANTHROPIC_API_KEY = "sk-ant-..."

# terminal 1: backend
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# terminal 2: frontend
cd frontend
npm install
npm start
```

## Bez klucza Anthropic (tylko MOCK)

Klucz nie jest potrzebny, żeby obejrzeć całe demo. Wystarczy go **nie ustawiać** i uruchomić backend z profilem `dev` (komendy wyżej, bez linii z `ANTHROPIC_API_KEY`).

- Backend nie wywołuje wtedy Claude. Zamiast tego używa gotowych odpowiedzi dla scenariuszy 01, 02 i 03 (tryb **MOCK**), a ekrany pokazują znaczek MOCK.
- Słowa kluczowe działają jak zwykle jako zabezpieczenie.
- Uruchamiaj symulację z panelu rodziny („Zasymuluj połączenie”) albo z ekranu audytu (http://localhost:4203/audit).
- Nie zadziała analiza własnych, nowych rozmów przez AI (tylko słowa kluczowe).
- Można też wymusić tryb jawnie: Linux `export APP_MODE=MOCK`, Windows `$env:APP_MODE = "MOCK"`.
- Wyjątek: profil inny niż `dev` (np. `prod`, Docker) **bez klucza nie wystartuje**, dopóki nie ustawisz `APP_MODE=MOCK`. `make demo` robi to sam, gdy nie ma klucza.

## Adresy po uruchomieniu

| Aplikacja | URL |
|---|---|
| Senior | http://localhost:4201/senior |
| Nasłuch | http://localhost:4202/listen |
| Panel rodziny | http://localhost:4203/family |
| Ustawienia (kreator) | http://localhost:4203/setup |
| Audyt | http://localhost:4203/audit |
| Status backendu | http://localhost:8080/api/status |

Backend działa na porcie 8080, a frontend przekazuje do niego `/api` i `/ws`.

## Szybkie demo bez mikrofonu

1. Otwórz panel rodziny: http://localhost:4203/family.
2. W ustawieniach zaznacz obie zgody (senior i rodzina) i uzupełnij kreator.
3. Wróć na panel i kliknij „Zasymuluj połączenie”.
4. Alert pojawi się na wszystkich trzech ekranach. Działa to bez klucza API (tryb MOCK, patrz wyżej).

## Tryb LIVE (prawdziwy mikrofon i Vosk)

1. Pobierz model rozpoznawania mowy (ok. 50 MB, nie ma go w repozytorium):
   - Linux: `make download-vosk-model`
   - Windows: `.\scripts\download-vosk-model.ps1`
2. Uruchom backend jak wyżej. Sam znajdzie model w `backend/models/`. Inna lokalizacja: zmienna `APP_STT_VOSK_MODEL_PATH`.
3. Do prawdziwych odpowiedzi AI ustaw `ANTHROPIC_API_KEY`.
4. Otwórz Nasłuch (http://localhost:4202/listen) i zezwól przeglądarce na mikrofon. Na `http://localhost` mikrofon działa bez HTTPS.

## Ważne

- **Klucz API** podajemy tylko przez zmienną środowiskową (albo plik `.env` wzorowany na `.env.example`, którego nie commitujemy). Spring sam nie wczytuje `.env`, więc klucz trzeba wyeksportować do powłoki.
- **Po `git pull`** zrestartuj frontend: działające serwery trzymają stary kod.
- **Demo na telefonach i tabletach w Wi-Fi** (HTTPS, potrzebny Docker): `make demo`. Szczegóły w `deploy/README.md`.
- **Testy:** `make test` (backend) i `cd frontend && npm test` (frontend).
