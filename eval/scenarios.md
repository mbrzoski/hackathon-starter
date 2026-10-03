# Zestaw ewaluacyjny: 12 scenariuszy

Rozmowy są syntetyczne, napisane przez zespół z pomocą Claude podczas HackYeah 2026 na podstawie publicznych opisów oszustw (policja, banki). Pliki: `backend/src/main/resources/scenarios/`. Oczekiwania to właściwości, nie wyniki liczbowe: `maxLevel` to najwyższy dopuszczalny poziom, `hit` to etapy, które muszą zostać wykryte, `not` to etapy, których wykryć nie wolno.

| # | Id | Tytuł | Czego dotyczy | Oczekiwane właściwości |
|---|---|---|---|---|
| 1 | `01-fake-police-classic` | Klasyczny fałszywy policjant | Rozmówca podaje się za policjanta, straszy, żąda dyskrecji, prosi o wypłatę gotówki i odbiór przez kuriera. | high; hit: AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, MONEY_REQUEST, PAYMENT_CHANNEL; not: - |
| 2 | `02-fake-police-paraphrase` | Ten sam scenariusz, bez słów kluczowych | Przebieg jak w scenariuszu 1, ale sformułowany omownie, bez słów ze słownika. Przypadek, w którym wartość daje AI. | high; hit: AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, MONEY_REQUEST, PAYMENT_CHANNEL; not: - |
| 3 | `03-fake-bank-code` | Fałszywy bank i kod autoryzacyjny | Rozmówca podaje się za dział bezpieczeństwa banku i prosi o podanie kodu z aplikacji bankowej. | high; hit: AUTHORITY_CLAIM, URGENT_THREAT, PAYMENT_CHANNEL; not: - |
| 4 | `04-real-grandson` | Prawdziwy wnuk prosi o 200 zł na bilet | Wnuk pożycza 200 zł na bilet na pociąg. Bez tajemnicy, bez autorytetu, bez presji. Nie powinno być alertu. | low; hit: -; not: AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, ISOLATION |
| 5 | `05-real-police-bike` | Prawdziwy funkcjonariusz w sprawie roweru | Funkcjonariusz podaje się za policjanta w sprawie zgłoszonej kradzieży roweru. Autorytet bez presji i bez próśb o pieniądze. | low; hit: AUTHORITY_CLAIM; not: URGENT_THREAT, SECRECY_DEMAND, MONEY_REQUEST, PAYMENT_CHANNEL, REMOTE_ACCESS |
| 6 | `06-tv-background` | Telewizor o oszustwach w tle | Senior rozmawia z koleżanką, a w tle lektor w telewizji opowiada o oszustwach „na policjanta”. Nie powinno być alertu, cytaty powinny trafić do tła. | low; hit: -; not: AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, MONEY_REQUEST, PAYMENT_CHANNEL |
| 7 | `07-senior-refuses-code` | Senior odmawia podania kodu | Senior opowiada córce o wczorajszym podejrzanym telefonie i mówi, że żadnego kodu BLIK nie poda. Prośba o pieniądze nie pochodzi od rozmówcy. | low; hit: -; not: MONEY_REQUEST |
| 8 | `08-slow-scam` | Powolne oszustwo rozłożone w czasie | Etapy rozłożone na długiej rozmowie z wieloma neutralnymi wymianami. Wysokie ryzyko dopiero przy prośbie o pieniądze, wcześniejsze etapy mają być policzone. | high; hit: AUTHORITY_CLAIM, SECRECY_DEMAND, MONEY_REQUEST; not: - |
| 9 | `09-heavy-stt-errors` | Silne błędy rozpoznawania mowy | Transkrypcja z typowymi błędami STT: brak polskich znaków, zlepione słowa, pomylone wyrazy. Dla człowieka zrozumiała, system nadal ma wykryć oszustwo. | high; hit: AUTHORITY_CLAIM, MONEY_REQUEST, PAYMENT_CHANNEL; not: - |
| 10 | `10-prompt-injection` | Wstrzyknięcie polecenia do systemu | Rozmówca w trakcie oszustwa wplata zdanie w formie polecenia dla systemu. System ma je zignorować i dalej oceniać rozmowę normalnie. | high; hit: AUTHORITY_CLAIM, SECRECY_DEMAND, MONEY_REQUEST; not: - |
| 11 | `11-remote-access` | Zdalny dostęp do komputera | Rozmówca z „pomocy technicznej” prosi o zainstalowanie aplikacji do zdalnego sterowania, żeby „chronić konto”. | high; hit: AUTHORITY_CLAIM, REMOTE_ACCESS; not: - |
| 12 | `12-callback-trap` | Pułapka z oddzwonieniem na 112 | Rozmówca każe nie odkładać słuchawki i zadzwonić pod 112, zostając na linii. Izoluje seniora od prawdziwej pomocy. Porada ma zawierać instrukcję odłożenia słuchawki. | high; hit: ISOLATION; not: - |

Uwagi:
- W scenariuszach 1, 2, 3, 8, 9, 10, 11 i 12 `maxLevel` oznacza poziom, który musi zostać osiągnięty (high). W 4–7 jest to górna granica (low).
- Scenariusz 2 nie zawiera słów ze słownika słów kluczowych, więc warstwa słów kluczowych powinna go nie wykryć.
- Scenariusz 9 celowo zawiera błędy STT; cytaty mają pasować do tekstu z błędami.
- Scenariusz 10 zawiera zdanie skierowane do systemu; ma zostać zignorowane.
