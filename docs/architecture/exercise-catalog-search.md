# Wyszukiwanie katalogu ćwiczeń

**Status: zaimplementowane w SET-03.** To odczytowy kontrakt katalogu dla reużywalnego
pickera i przyszłego buildera zestawów; nie jest API zapisu `ExerciseSet`.

## Granica i API

`exercisecatalog` jest właścicielem wyszukiwania. `ExerciseSet` używa minimalnego portu,
nigdy HTTP ani repozytorium JPA katalogu. Picker emituje `ExerciseSelection` z
`exerciseId`, dokładnym `exerciseVersionId`, nazwą, sugerowanym typem dawki i małą
prezentacją — nie zapisuje zestawu.

## Granica redakcyjna

Publiczne `/catalog` oraz API wyszukiwania pozostają wyłącznie readerem opublikowanych
wersji. Osobny, chroniony `CONTENT_ADMIN` endpoint `GET /api/v1/admin/exercises` jest
projekcją nawigacyjną aktualnej (najwyższej) wersji, z backendowym `availableActions`
i `expectedVersion`; szczegół pobiera też `GET .../capabilities`. Frontend nie odgaduje
przejść lifecycle. Wersje published i withdrawn są niemutowalne; kolejna wersja jest
nowym draftem. Te projekcje nie są modelem audytu ani nie zmieniają historycznych
referencji opublikowanych wersji.

| Operacja | Endpoint | Wynik |
|---|---|---|
| Wyszukiwanie | `POST /api/v2/exercises/search` | projekcja wyników, cursor i facety |
| Podgląd | `GET /api/v2/exercises/versions/{exerciseVersionId}/preview` | lekki podgląd wersji |

Wynik zawiera wersję i ćwiczenie, numer wersji, nazwę, streszczenie, typ, poziom,
wzorce ruchu, sprzęt, jakościową anatomię, media reference i `selectable`. Nie eksponuje
encji JPA ani całego agregatu katalogu.

## Wybieralność i normalizacja

Wyniki i podgląd dopuszczają tylko aktualną opublikowaną wersję ćwiczenia. Drafty,
review, withdrawn oraz starsze publikacje przy nowszej opublikowanej wersji nie są
wybieralne. Nie zmienia to historycznych referencji wersji w zestawach.

Migracja [V039](../../src/main/resources/db/migration/V039__add_exercise_catalog_search_indexes.sql)
instaluje `pg_trgm`, immutable `exercise_catalog.fold_search_text(text)` i indeksy.
Folding trimuje tekst, normalizuje odstępy, obniża wielkość liter i mapuje polskie znaki.
Backend obsługuje `pl-PL`, query do 120 znaków i ignoruje puste query. Istniejące
locale-aware `exercise_alias` są źródłem synonimów; SET-03 nie fabrykuje aliasów.

## Filtry, facety, ranking i cursor

Filtry: wzorzec ruchu, poziom, sprzęt, pozycja, stronność, struktura anatomiczna, typ
struktury i zastosowanie. Wartości wewnątrz grupy są łączone przez OR, grupy przez AND.
Anatomia jest klasyfikacją katalogową, nie analizą bezpieczeństwa lub obciążenia.

Facety liczone są dla bieżącej frazy i wszystkich filtrów poza własną grupą. Mają stabilne
`group`, `value`, opcjonalny klucz etykiety, `count` oraz `active`; implementacja nie
wykonuje query per wartość.

Sortowanie obsługuje `RELEVANCE`, `NAME` i `RECENTLY_PUBLISHED`. Trafność preferuje
dokładną/prefiksową nazwę, alias, dopasowanie częściowe i trigram. Stabilny tie-breaker
to znormalizowana nazwa i `exerciseVersionId`. Seek cursor (limit 1–50) jest URL-safe
Base64 tuple sortowania z fingerprintem SHA-256 requestu; błędny cursor zwraca
`INVALID_CURSOR`.

## Indeksy i UI

V039 tworzy GIN trigram dla nazw i aliasów oraz indeksy aktualnej publikacji, wzorców,
sprzętu, celów i struktur. Serwis korzysta z ograniczonej liczby native JPA read queries:
kandydaci, batch enrichment i stała liczba agregacji facetów — bez pełnego odczytu
tabeli, filtrowania w Javie i N+1 per wynik.

`ExercisePickerComponent`, używany przez `/catalog`, ma debounce 250 ms, filtry, facety,
doładowanie kursorem, deduplikację po `exerciseVersionId`, preview dialog i polskie
etykiety. Używa semantycznych kontrolek, `aria-live`, zarządzania focusem, breakpointu
mobile oraz `prefers-reduced-motion`; UUID nie jest wyświetlany użytkownikowi.

Testy Testcontainers/MockMvc pokrywają aliasy, polskie znaki, filtry, facety, cursor i
preview. Pomiar planu wykonania na dużym reprezentatywnym datasecie nie jest jeszcze
artefaktem repozytorium i nie deklaruje się SLA.
