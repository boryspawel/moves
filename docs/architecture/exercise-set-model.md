# Model zestawów ćwiczeń

Zestaw ćwiczeń jest niezależną, owner-scoped definicją należącą do specjalisty. Nie
zawiera uczestnika, terminu, planowanej sesji ani wykonania. Granice decyzji opisuje
[ADR-013](../adr/ADR-013-independent-versioned-exercise-sets.md).

## Agregaty i niezmienniki

`ExerciseSet` jest trwałą tożsamością zestawu: przechowuje autora, widoczność i
wersje. `ExerciseSetVersion` przechowuje metadane, uporządkowane pozycje i ich
typowane dawki. Wersja przechodzi przez `DRAFT`, `PUBLISHED` i `RETIRED`.

- Tylko draft można zmieniać.
- Opublikowana wersja jest niezmienna; zmiana wymaga kolejnego draftu.
- Pozycja wskazuje dokładną opublikowaną `ExerciseVersion` i zachowuje minimalny
  snapshot prezentacyjny: nazwę, numer wersji, wersję schematu profilu, wzorce
  ruchu oraz wymagany sprzęt.
- Pozycje mają ciągłą, unikalną kolejność w wersji zestawu.
- `ExerciseSetVersion.lockVersion` jest jedynym tokenem optimistic locking dla
  mutacji draftu. Każda udana mutacja zwraca aktualną wartość tokenu.
- Wycofanie blokuje nowe użycie zestawu, ale nie zmienia materializowanych
  snapshotów rewizji planu ani wykonania.

Encje są utrwalane przez JPA/Hibernate. Publiczne API zwraca DTO, nie encje.

## Dawkowanie i warianty

`Dose` jest kontraktem OpenAPI `oneOf` z dyskryminatorem `type`. Obsługiwane typy
to `STRENGTH`, `ISOMETRIC`, `MOBILITY`, `STRETCH`, `BREATHING` i `AEROBIC`.
Walidacja chroni dodatnie wartości, poprawne zakresy i wymagane pola danego typu.

Wersja bazowa, krótka lub minimalna jest pełnym, niezmiennym snapshotem zestawu.
`SHORT` i `MINIMUM` wskazują opublikowaną wersję źródłową tego samego zestawu;
nie są deltą ani automatycznie wyliczoną redukcją. Wariant sesji wybiera już
materializowane recepty i nie zmienia dawki zestawu.

## Katalog i API

Specjalistyczne API zestawów działa pod `/api/v1/specialist/exercise-sets` i
obsługuje tworzenie, odczyt, edycję draftu, pozycje, publikację, kolejny draft,
draft wariantu oraz wycofanie. Publikacja przyjmuje `expectedVersion`; odrzucane
są błędy techniczne, konflikt współbieżności, niepoprawny request, brak wskazanej
wersji ćwiczenia lub próba zmiany wersji niebędącej draftem.

Picker katalogowy korzysta z `POST /api/v2/exercises/search` oraz
`GET /api/v2/exercises/versions/{id}/preview`. Zwraca wyłącznie aktualne,
opublikowane wersje wybieralne do nowego zestawu. Picker przekazuje dokładne
`exerciseVersionId`; zapis pozycji pozostaje odpowiedzialnością zestawu.

## Planowanie i wykonanie

Plan wskazuje jedną dokładną opublikowaną `ExerciseSetVersion`. Przy dołączeniu
źródła rewizja planu materializuje uporządkowane pozycje, typowane dawki oraz
snapshoty katalogowe. Aktywacja i wykonanie czytają ten lokalny snapshot, więc
późniejsze wycofanie lub nowa wersja zestawu nie zmienia wcześniej utworzonej
rewizji ani faktów wykonania.

Przydzielenie uczestnikowi udostępnia wyłącznie zredagowany snapshot opublikowanej
wersji. Nie udostępnia draftów, nie zmienia właściciela i nie tworzy ogólnego ACL.
Autoryzacja pozostaje oparta na roli, aktywnej relacji oraz capability
`SHARE_EXERCISE_SETS`.

## Analiza i anatomia

Analiza zestawu jest deterministyczna i doradcza. Zwraca metryki oraz sugestie
dotyczące struktury, czasu, sprzętu i kompletności danych; nie jest oceną kliniczną
ani biomechaniczną. Wynik opublikowanej wersji jest utrwalonym snapshotem.

Ekspozycja anatomiczna jest odczytową projekcją danych snapshotu. Może wskazywać
braki danych i nigdy nie jest przedstawiana jako pomiar siły, obciążenia ścięgna
ani ocena ryzyka. Szczegóły opisują [analiza Exercise Set](exercise-set-analysis.md)
i [ekspozycja anatomiczna zestawu](exercise-set-anatomy-exposure.md).
