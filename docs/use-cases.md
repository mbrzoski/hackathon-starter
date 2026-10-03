# Anioł Stróż: przypadki użycia

## UC-01: ostrzeżenie o oszustwie w trakcie rozmowy (główny przypadek)

Ktoś dzwoni do seniora. Urządzenie nasłuchujące słyszy rozmowę i wysyła dźwięk do backendu. Gdy backend rozpozna oszustwo, alert dostaje senior w aplikacji na swoim telefonie komórkowym i rodzina w panelu. Alert nie zostaje tylko na urządzeniu nasłuchującym.

### Urządzenia (założenia na teraz)

| Urządzenie | Aplikacja | Rola w UC-01 |
|---|---|---|
| Telefon albo tablet obok telefonu, na którym senior rozmawia | Nasłuch, `/listen` (strona web, port 4202 w dev) | Słucha rozmowy przez mikrofon i wysyła dźwięk do backendu. Pokazuje stan i alarm. |
| Telefon komórkowy seniora | Aplikacja seniora, `/senior` (port 4201 w dev; docelowo aplikacja na telefon) | Dostaje alert: czerwony ekran, głos, „Dlaczego?” i trzy decyzje. |
| Telefon albo komputer rodziny | Panel rodziny, `/family` (strona web, port 4203 w dev) | Dostaje alert z dowodami i decyduje. |
| Serwer | Backend | Zamiana mowy na tekst, słowa kluczowe, AI, poziom ryzyka, alert, zapis. |

Nasłuch słyszy rozmowę akustycznie, przez głośnik telefonu. Android nie pozwala aplikacji nagrywać rozmowy telefonicznej prowadzonej na tym samym urządzeniu (AND-06), więc Nasłuch musi być osobnym urządzeniem niż telefon, na którym toczy się rozmowa.

### Warunki wstępne

- Na Nasłuchu ktoś dotknął „Włącz ochronę” i zgodził się na mikrofon.
- Aplikacja seniora i panel rodziny są otwarte i połączone z backendem.

### Przebieg

1. Ktoś dzwoni do seniora. Senior odbiera i rozmawia przez głośnik.
2. Nasłuch słyszy rozmowę i wysyła dźwięk (PCM 16 kHz, mono) przez `/ws/audio`.
3. Backend zaczyna rozmowę (`call.started`) i zamienia mowę na tekst (`transcript.segment`).
4. Backend ocenia rozmowę. Słowa kluczowe i AI znajdują etapy oszustwa z dosłownym cytatem, a `QuoteValidator` sprawdza każdy cytat AI. Silnik ryzyka wylicza poziom (`risk.update`).
5. Wynik zależy od poziomu ryzyka:
   - brak albo niski: senior nic nie widzi, alertu nie ma;
   - średni albo wysoki: backend tworzy alert (`alert.created`) z tekstem z szablonu.
6. Alert trafia jednocześnie do trzech miejsc:
   - `/senior`: czerwony ekran, odczytanie tekstu głosem, „Dlaczego?” z cytatami i trzy decyzje;
   - `/family`: karta z poziomem, etapami, cytatami i fragmentem rozmowy oraz sygnał dźwiękowy przy poziomie wysokim;
   - `/listen`: ekran alarmu.
7. Senior wybiera „Rozłączam się”, „Zadzwoń do…” albo „To fałszywy alarm” (`alert.decision`). Rodzina widzi tę decyzję. Może zadzwonić do seniora, potwierdzić oszustwo albo oznaczyć fałszywy alarm.
8. Rozmowa się kończy (`call.ended`). Rozmowa z alertem zostaje zapisana. Bez alertu transkrypcja jest kasowana.

### Ścieżki awarii

| Awaria | Co widzą senior, rodzina i Nasłuch |
|---|---|
| Nasłuch nie słyszy dźwięku albo zamiana mowy na tekst nie działa | „Nie słyszę rozmowy”, nigdy zielony stan |
| AI nie działa | Alerty dalej przychodzą ze słów kluczowych. Ekrany pokazują „Podstawowa ochrona (bez AI)”. |
| Backend nie działa | „Anioł Stróż jest offline” na wszystkich trzech aplikacjach |

### Wynik

Senior i rodzina dowiadują się o podejrzanej rozmowie w jej trakcie. Widzą dosłowne słowa rozmówcy, które wywołały alert. Decyzję zawsze podejmuje człowiek. System nie rozłącza rozmowy, nie dzwoni i nie blokuje numerów.

### Otwarte kwestie

| # | Kwestia | Dlaczego ważna |
|---|---|---|
| A | Mikrofon uruchamia Nasłuch, nie aplikacja seniora. Dziś przycisk „Włącz ochronę” u seniora woła `AudioService.start()`. | Dźwięk płynie tylko z Nasłuchu (krok 2). |
| B | „Wstrzymaj dla tej rozmowy” jest w aplikacji seniora, a dźwięk wysyła inne urządzenie. | Bez ścieżki przez backend (zmiana kontraktu) albo przeniesienia przycisku do Nasłuchu pauza nic nie wstrzyma. |
| C | Nasłuch słyszy tylko dźwięk i nie wie, kiedy zaczyna się i kończy rozmowa. | W trybie LIVE trzeba ustalić, skąd biorą się `call.started` i `call.ended` (krok 3 i 8). |
| D | Alert dociera tylko do otwartych aplikacji. Aplikacja seniora działa tylko na pierwszym planie (AND-05). | Zamknięta aplikacja oznacza brak ostrzeżenia. Docelowo push (AND-02) albo SMS (BE-13). |
| E | Makieta Nasłuchu pokazuje „Senior otrzymał ostrzeżenie” i „Rodzina została powiadomiona”, a backend nie potwierdza doręczenia. | Bez potwierdzenia takiego komunikatu nie wolno pokazać. |
| F | Nasłuch łączy się z `/ws/events` z rolą `senior`. | Backend nie odróżnia Nasłuchu od telefonu seniora, co jest potrzebne np. do potwierdzeń doręczenia (E). |
