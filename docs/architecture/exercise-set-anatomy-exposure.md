# Ekspozycja anatomiczna zestawu ćwiczeń

**Status: SET-06B adds the persisted visual-region projection.** Ten dokument jest kanonicznym opisem odczytu
ekspozycji anatomicznej w builderze. Model zestawu pozostaje opisany w
[modelu Exercise Set](exercise-set-model.md), a ogólną analizę strukturalną
opisuje [analiza Exercise Set](exercise-set-analysis.md). Decyzję o niezmiennych
snapshotach i rozdzieleniu kanałów zapisuje [ADR-016](../adr/ADR-016-versioned-anatomy-exposure-snapshots.md).

## Granica i niezmienność

`GET /api/v1/specialist/exercise-sets/{setId}/versions/{versionId}/anatomy`
analizuje draft z dokładnych opublikowanych snapshotów ćwiczeń. Dla opublikowanej
wersji zwraca utrwalony wynik publikacji, a nie analizę bieżącego katalogu.
Wynik zachowuje politykę, wersję blokady, czas analizy, kompletność, kompletność
mapowania wizualnego, kanały,
bezpośrednie ekspozycje struktur, wzorce ruchu, findings, braki danych i
rozbicia wraz z dowodami oraz `visualRegionExposures`. UI jest wyłącznie czytelnikiem tego kontraktu.

## Projekcja regionów wizualnych SET-06B

`visualRegionExposures` jest jedynym wejściem mapy SVG. SVG jest wyłącznie geometrią:
nie zawiera znaczenia klinicznego ani mapowania struktur. Backend tworzy projekcję z
utrwalonego w snapshocie pozycji, wersjonowanego mapowania wizualnego, nigdy z nazw
struktur ani z aktualnego mapowania przy odczycie historii. Wynik podaje
`visualMappingVersion`, kompletność i wersjonowaną politykę concentration band.
Top-level `visualMappingVersion` jest zawsze tokenem: numeryczną wersją, gdy wszystkie
projektowane źródła jej używają, `MIXED` dla różnych wersji lub `UNAVAILABLE` bez
projekcji. Każdy `visualRegionExposure` ma dodatkowo dokładne numeryczne
`mappingVersion`, więc historyczna projekcja nie jest niejednoznaczna.

Każdy wpis zawiera kod regionu, `view`, `layer`, laterality, kanał, źródłowe struktury,
breakdown pozycji/ćwiczeń, zakres współczynników oraz `rawValue`. `rawValue` to suma
`coefficientHigh` unikalnych bezpośrednich wkładów (`unit=COEFFICIENT_HIGH_SUM`), nie
jest obciążeniem biomechanicznym. `shareWithinChannel` ma mianownik równy sumie takich
`rawValue` w tym samym kanale i jest obliczany wyłącznie przez backend. Polityka
`visual-region-concentration-policy-v1` nadaje `NO_DATA`, `LOW`, `SIGNIFICANT` lub
`DOMINANT`; frontend nie zna progów.

Projekcja korzysta tylko z kanonicznego strumienia wkładów `ALLOCATION` (bez agregatów
hierarchii). Deduplication key to `(itemId, channel, laterality, contributionId,
visualRegionCode)`, więc wkład nie może zostać zsumowany drugi raz w tym samym regionie.
Struktury bez mapowania pozostają w wynikach struktur i `unmappedStructures`, bez
fikcyjnego regionu.

## Prezentacja

Sekcja buildera **„Ekspozycja i wzorce”** korzysta z wygenerowanego klienta przez
`ApiFacade.exerciseSets`. Dla drafu wynik jest oznaczany jako nieaktualny po
zmianie wersji i odświeżany na żądanie; odpowiedź jest przyjmowana tylko dla
aktualnego `lockVersion`. Dla publikacji wyświetlany jest niezmienny snapshot.

Widok pokazuje kanały, kompletność, bezpośrednie udziały z jednostką `j.`,
rozbicia udziałów, pewność i stopień dowodów, wzorce ruchu oraz findings i
braki danych. Rozbicia dowodów są dostępne przez natywny element `details`,
obsługiwany klawiaturą.

Obowiązuje dokładne ograniczenie prezentowane w UI:

> Ekspozycja anatomiczna jest opisem jakościowym i nie stanowi oceny klinicznej ani pomiaru siły.

Nie jest to diagnoza, zalecenie dla uczestnika ani suma obciążenia treningowego.
Ocena bezpieczeństwa i dopasowania do osoby pozostaje w swoich kontekstach.

## Mapowanie wizualne SET-06A

`GET /api/v1/anatomy/visual-regions` zwraca aktywne metadane regionów mapy ciała.
Frontend pobiera je wyłącznie przez wygenerowany klient i `ApiFacade`; nie utrzymuje
własnego słownika anatomii, kodów regionów ani reguł przypisania struktur. Odpowiedź
analizy zestawu zachowuje wersjonowaną kompletność mapowania z jej snapshotu, więc
wynik publikacji nie zależy od późniejszych zmian katalogu lub aktywnej listy regionów.

Builder nie renderuje jeszcze mapy ciała. Zamiast niej pokazuje diagnostykę warstwy i
widoku oraz kody niezamapowanych regionów, gdy kontrakt je zwraca. Przy mapowaniu
częściowym prezentuje dokładny komunikat:

> Część struktur nie ma jeszcze przypisanego regionu mapy ciała. Wyniki tabelaryczne pozostają dostępne.

Kody są danymi diagnostycznymi z API, nie frontendowym tłumaczeniem ani źródłem
znaczenia klinicznego.

## Granica geometrii SVG SET-07A1

Granica danych ma postać: `anatomical structure → visualRegionExposures → visualRegionCode → SVG geometry`.
Pierwsze przejście pozostaje w wersjonowanym mapowaniu backendowym V047;
`visualRegionCode` jest jedynym kontraktem, który może połączyć je z geometrią. Drugi
krok jest wyłącznie ręcznie recenzowanym manifestem narzędziowym i nie tworzy słownika
`structure → region` w frontendzie. Atlas może pokazać pomocniczy podział FRONT/BACK z
położenia, ale nie jest on decyzją semantyczną ani walidacją wpisu manifestu.

## Transport i kompatybilność

Snapshot OpenAPI i wygenerowany klient są odświeżane wyłącznie przez
`npm run api:refresh`; pliki pod `web/src/app/api/generated/` nie są edytowane
ręcznie. Prezenter korzysta z wygenerowanego `AnatomyAnalysisView` i operacji
`ApiFacade.exerciseSets.anatomy`, a aktywne metadane regionów przez wygenerowany
`AnatomyReferenceControllerApi` udostępniony przez `ApiFacade`.
