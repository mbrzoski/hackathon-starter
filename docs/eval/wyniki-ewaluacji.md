# Ewaluacja: słowa kluczowe, AI i oba razem

Wygenerowane przez `EvalRunner` (`make eval`) dnia 2026-10-04. AI: `claude-sonnet-5-5`, prawdziwe wywołania, jedno po każdym segmencie (jak w aplikacji).

Każdy scenariusz ma oczekiwane **właściwości**, nie wynik punktowy (TST-05). ✓ = wszystkie spełnione, ✗ = coś nie (powód w nawiasie). Poziom: oszustwo musi osiągnąć oczekiwany poziom, zwykła rozmowa nie może dać ostrzeżenia. Etapy liczone są tylko dla rozmówcy (nie seniora i nie tła). Czułość: standardowa. „Alert w” = segment, w którym poziom pierwszy raz osiągnął średni.

| Scenariusz | Oczekiwane | Słowa kluczowe | AI | Oba |
|---|---|---|---|---|
| 01-fake-police-classic Klasyczny fałszywy policjant | high | ✗ high, alert w s10 (brak [URGENT_THREAT, SECRECY_DEMAND]) | ✓ high, alert w s5 | ✓ high, alert w s5 |
| 02-fake-police-paraphrase Ten sam scenariusz, bez słów kluczowych | high | ✗ low (poziom; brak [AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, PAYMENT_CHANNEL]) | ✓ high, alert w s5 | ✓ high, alert w s5 |
| 03-fake-bank-code Fałszywy bank i kod autoryzacyjny | high | ✗ high, alert w s9 (brak [URGENT_THREAT]) | ✓ high, alert w s3 | ✓ high, alert w s3 |
| 04-real-grandson Prawdziwy wnuk prosi o 200 zł na bilet | low | ✓ none | ✓ low | ✓ low |
| 05-real-police-bike Prawdziwy funkcjonariusz w sprawie roweru | low | ✗ low (brak [AUTHORITY_CLAIM]) | ✓ low | ✗ medium, alert w s5 (poziom) |
| 06-tv-background Telewizor o oszustwach w tle | low | ✗ high, alert w s3 (poziom; niepotrzebnie [AUTHORITY_CLAIM, SECRECY_DEMAND, MONEY_REQUEST, PAYMENT_CHANNEL]) | ✓ none | ✗ high, alert w s3 (poziom; niepotrzebnie [AUTHORITY_CLAIM, SECRECY_DEMAND, MONEY_REQUEST, PAYMENT_CHANNEL]) |
| 07-senior-refuses-code Senior odmawia podania kodu | low | ✓ low | ✓ none | ✓ low |
| 08-slow-scam Powolne oszustwo rozłożone w czasie | high | ✗ high, alert w s19 (brak [SECRECY_DEMAND]) | ✓ high, alert w s11 | ✓ high, alert w s11 |
| 09-heavy-stt-errors Silne błędy rozpoznawania mowy | high | ✗ medium, alert w s13 (poziom; brak [AUTHORITY_CLAIM]) | ✓ high, alert w s3 | ✓ high, alert w s3 |
| 10-prompt-injection Wstrzyknięcie polecenia do systemu | high | ✗ high, alert w s9 (brak [SECRECY_DEMAND]) | ✓ high, alert w s3 | ✓ high, alert w s3 |
| 11-remote-access Zdalny dostęp do komputera | high | ✗ none (poziom; brak [AUTHORITY_CLAIM, REMOTE_ACCESS]) | ✓ high, alert w s3 | ✓ high, alert w s3 |
| 12-callback-trap Pułapka z oddzwonieniem na 112 | medium | ✓ medium, alert w s7 | ✓ medium, alert w s3 | ✓ medium, alert w s3 |

Scenariusze ze wszystkimi właściwościami spełnionymi: słowa kluczowe 3, AI 12, oba 10 (na 12).

## Zmierzone liczby AI

| Miara | Wartość |
|---|---|
| Wywołania | 179 |
| Błędy (timeout, odmowa itp.) | 0 |
| Odrzucone cytaty (QuoteValidator) | 0 |
| Opóźnienie p50 | 2540 ms |
| Opóźnienie p95 | 4065 ms |
| Tokeny: wejście / odczyt cache / zapis cache / wyjście | 12351 / 749494 / 0 / 48322 |
| Koszt całego przebiegu (cennik z konfiguracji: 2 / 0,20 / 2,50 / 10 USD za 1M) | 0.6578 USD |
| Średni koszt na rozmowę | 0.0548 USD |

Percentyle metodą najbliższej rangi (D-36). Opóźnienie liczone od wysłania do odpowiedzi API, bez czasu rozpoznawania mowy. Koszt zależy od cache promptu: przebieg z zimnym cache zapisuje rubrykę i transkrypcję do cache (droższy zapis), kolejny w ciągu kilku minut czyta je taniej. Pierwszy przebieg 2026-10-04: 0,9072 USD (110 051 tokenów zapisu cache), ten: bez zapisu.

## Szczegóły

- **01-fake-police-classic**: keywords → high [AUTHORITY_CLAIM, ISOLATION, MONEY_REQUEST, PAYMENT_CHANNEL]; ai → high [AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, ISOLATION, MONEY_REQUEST, PAYMENT_CHANNEL]; both → high [AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, ISOLATION, MONEY_REQUEST, PAYMENT_CHANNEL]; odrzucone cytaty AI: 0
- **02-fake-police-paraphrase**: keywords → low [MONEY_REQUEST]; ai → high [AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, ISOLATION, MONEY_REQUEST, PAYMENT_CHANNEL]; both → high [AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, ISOLATION, MONEY_REQUEST, PAYMENT_CHANNEL]; odrzucone cytaty AI: 0
- **03-fake-bank-code**: keywords → high [AUTHORITY_CLAIM, MONEY_REQUEST, PAYMENT_CHANNEL]; ai → high [AUTHORITY_CLAIM, URGENT_THREAT, PAYMENT_CHANNEL, REMOTE_ACCESS, PERSONAL_DATA_REQUEST]; both → high [AUTHORITY_CLAIM, URGENT_THREAT, MONEY_REQUEST, PAYMENT_CHANNEL, REMOTE_ACCESS, PERSONAL_DATA_REQUEST]; odrzucone cytaty AI: 0
- **04-real-grandson**: keywords → none []; ai → low [MONEY_REQUEST]; both → low [MONEY_REQUEST]; odrzucone cytaty AI: 0
- **05-real-police-bike**: keywords → low [PERSONAL_DATA_REQUEST]; ai → low [AUTHORITY_CLAIM]; both → medium [AUTHORITY_CLAIM, PERSONAL_DATA_REQUEST]; odrzucone cytaty AI: 0
- **06-tv-background**: keywords → high [AUTHORITY_CLAIM, SECRECY_DEMAND, MONEY_REQUEST, PAYMENT_CHANNEL]; ai → none []; both → high [AUTHORITY_CLAIM, SECRECY_DEMAND, MONEY_REQUEST, PAYMENT_CHANNEL]; odrzucone cytaty AI: 0
- **07-senior-refuses-code**: keywords → low [PAYMENT_CHANNEL]; ai → none []; both → low [PAYMENT_CHANNEL]; odrzucone cytaty AI: 0
- **08-slow-scam**: keywords → high [AUTHORITY_CLAIM, MONEY_REQUEST]; ai → high [AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, MONEY_REQUEST, PAYMENT_CHANNEL]; both → high [AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, MONEY_REQUEST, PAYMENT_CHANNEL]; odrzucone cytaty AI: 0
- **09-heavy-stt-errors**: keywords → medium [MONEY_REQUEST, PAYMENT_CHANNEL]; ai → high [AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, ISOLATION, MONEY_REQUEST, PAYMENT_CHANNEL]; both → high [AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, ISOLATION, MONEY_REQUEST, PAYMENT_CHANNEL]; odrzucone cytaty AI: 0
- **10-prompt-injection**: keywords → high [AUTHORITY_CLAIM, MONEY_REQUEST]; ai → high [AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, ISOLATION, MONEY_REQUEST, PAYMENT_CHANNEL]; both → high [AUTHORITY_CLAIM, URGENT_THREAT, SECRECY_DEMAND, ISOLATION, MONEY_REQUEST, PAYMENT_CHANNEL]; odrzucone cytaty AI: 0
- **11-remote-access**: keywords → none []; ai → high [AUTHORITY_CLAIM, URGENT_THREAT, REMOTE_ACCESS]; both → high [AUTHORITY_CLAIM, URGENT_THREAT, REMOTE_ACCESS]; odrzucone cytaty AI: 0
- **12-callback-trap**: keywords → medium [AUTHORITY_CLAIM, ISOLATION]; ai → medium [AUTHORITY_CLAIM, URGENT_THREAT, ISOLATION]; both → medium [AUTHORITY_CLAIM, URGENT_THREAT, ISOLATION]; odrzucone cytaty AI: 0
