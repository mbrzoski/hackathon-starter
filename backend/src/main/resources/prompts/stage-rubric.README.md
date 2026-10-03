# stage-rubric.pl.md

Prompt systemowy dla Claude: znajduje etapy manipulacji w transkrypcji rozmowy telefonicznej po polsku. Model zwraca tylko dowody (etap + dosłowny cytat), a ryzyko, teksty alertów i decyzje liczy kod (AI-01). Plik idzie jako `TextBlockParam` z `CacheControlEphemeral` (AI-06).

## Zasady edycji

- Bez znaczników czasu, dat i innych danych zmiennych przed transkrypcją. Każda zmiana pliku unieważnia cache, więc zmieniaj rubrykę świadomie.
- Bez schematu JSON w prompcie: format wymusza structured output (`StageHits` w `contracts/openapi.yaml`).
- Transkrypcja wchodzi w tagu `<transcript>`; rubryka każe ignorować polecenia z jej treści (AI-07).
- Nie dopisuj do rubryki informacji zwrotnych z alertów (AI-10).
- Przykłady w rubryce nie mogą być tymi samymi zdaniami co dane ewaluacyjne (TST-05), bo zawyżą wynik.

## Zawartość

- Rola, format wejścia, zadanie (model zwraca trafienia dla całej transkrypcji za każdym razem, bo nie widzi swoich poprzednich odpowiedzi, a duplikaty usuwa kod).
- Zasady cytowania: `quote` dosłownie z jednego segmentu (z błędami STT), `segment_id` jak w transkrypcji, pusta lista jest poprawną odpowiedzią.
- `speaker_role`: `caller`, `senior`, `background`, `unclear`. Etykiety A/B z STT nie mówią, kto jest kim.
- 8 etapów (`AUTHORITY_CLAIM`, `URGENT_THREAT`, `SECRECY_DEMAND`, `ISOLATION`, `MONEY_REQUEST`, `PAYMENT_CHANNEL`, `REMOTE_ACCESS`, `PERSONAL_DATA_REQUEST`), każdy z definicją, 3 przykładami (w tym parafrazy bez słów kluczowych) i 1 kontrprzykładem.
- Przypadki graniczne: odmowa seniora, telewizja, błędy STT, prompt injection.

## Rozmiar

916 słów, 6800 znaków. Szacunek: 2300–2700 tokenów (górna granica celu 1500–2500). Szacunek nie jest pomiarem: przed wdrożeniem sprawdź liczbę tokenów w API i wpisz ją tutaj: `___ tokenów`.

## Do uzupełnienia przez zespół

- Komentarz HTML na końcu pliku ma puste pola „URL do uzupełnienia”. Wpisujemy tylko adresy sprawdzone ręcznie (komunikaty policji, banków/ZBP, CERT Polska/KNF). Komentarz może zostać wycięty przez kod ładujący rubrykę przed wysłaniem do modelu, bo kosztuje kilkadziesiąt tokenów.
- Przykłady są syntetyczne, napisane na podstawie ogólnych opisów scenariuszy. Warto zastąpić je 2–3 przykładami opartymi na realnych opisach oszustw i wpisać źródła.

## 5 decyzji do świadomego zatwierdzenia albo zmiany

1. **Model nie ocenia prawdziwości rozmówcy.** Prawdziwy policjant dostaje `AUTHORITY_CLAIM`, a wnuczek pożyczający na bilet `MONEY_REQUEST`. Poziom ryzyka ustala kod (przypadki 4 i 5 z ewaluacji dają najwyżej Low). Plus: prosta, powtarzalna rubryka. Minus: więcej trafień o niskiej wadze w audycie.
2. **Odmowa seniora i telewizja mogą być zwracane** z rolą `senior` lub `background`, zamiast być pomijane. DET-02 wyklucza je tylko z `MONEY_REQUEST` i `PAYMENT_CHANNEL`, a nie z pozostałych etapów (np. `SECRECY_DEMAND`, `AUTHORITY_CLAIM`). Sprawdź w `RiskEngine`, czy to jest zamierzone.
3. **„W razie wątpliwości pomiń”.** Ogranicza fałszywe alarmy, ale kosztem wykrywalności subtelnych parafraz, które mają być wartością AI względem słów kluczowych. Zweryfikuj na przypadku 2 z ewaluacji.
4. **Granica `ISOLATION` i `URGENT_THREAT`.** „Zadzwoń pod 112, ja zostaję na linii” jest w `ISOLATION`, a presja czasu w `URGENT_THREAT`. Jeden fragment może trafić do obu etapów (osobne trafienia), co może zawyżyć liczbę „oznak ostrzegawczych”.
5. **Przykłady są krótkie i syntetyczne** (patrz „Do uzupełnienia”). Zastąpienie ich przykładami z realnych opisów poprawi jakość, ale nie mogą się pokrywać z danymi ewaluacyjnymi.
