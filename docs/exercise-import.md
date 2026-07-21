# Masowy import ćwiczeń

## Granice i model

`exerciseimport` odpowiada za artefakt, parsing, staging, problemy, mapowania i kandydatury. `exercisecatalog` przejmuje dane dopiero jako szkic. `anatomyreference` pozostaje właścicielem struktur, `loadanalysis` czyta profile opublikowanej wersji, `trainingplanning` wskazuje konkretny `exerciseVersionId`, a `safety` zachowuje dane uczestnika i reguły bezpieczeństwa. Pola źródłowe `contraindications`, `injuries`, `treatment` i `safeFor` pozostają tylko w `raw_payload` i generują ostrzeżenie `UNVERIFIED_SAFETY_FIELD`.

Migracja V017 tworzy staging, semantyczne tabele katalogu, słowniki sprzętu/pozycji/jednostek oraz triggery niezmienności. Nie seeduje ontologii anatomii. V018 jest dokładnym schematem metadanych Spring Batch 6 dla PostgreSQL; automatyczna inicjalizacja jest wyłączona.

## JSONL 1.0

Plik jest UTF-8, jeden obiekt JSON na linię, MIME `application/x-ndjson` (akceptowane są też jawnie wymienione typy zgodności). Każdy rekord ma `schemaVersion: "moves.exercise-import/1.0"`. Pełny kontrakt jest w [exercise-import-1.0.schema.json](contracts/exercise-import-1.0.schema.json), a neutralne fixture w `src/test/resources/fixtures/`.

Domyślne limity to 10 MiB na plik i 256 KiB na linię. Oryginał jest zapisany strumieniowo, otrzymuje SHA-256 i niezmienny klucz magazynowy. Błąd JSON jednej linii tworzy `MALFORMED_JSON` z `row_number`; nie zatrzymuje innych rekordów. Błąd UTF-8 jest błędem całego batcha.

Dokument referencyjny pokazuje bogatszą, zagnieżdżoną reprezentację koncepcyjną. Wykonywalny kontrakt 1.0 w tym pionowym wycinku jest celowo bardziej zwarty i odzwierciedla istniejące zamknięte klasyfikacje katalogu (`stimulusType`, `fatigueProfile`, `technicalLevel`, `environment`) oraz istniejący profil obciążenia. Nie należy traktować przykładu koncepcyjnego jako drugiego, równoległego formatu 1.0; rozszerzenie obwiedni wymaga nowej wersji schematu albo jawnego adaptera, aby zachować deterministyczne hashe.

## Statusy i pipeline

Rekord używa: `RECEIVED`, `PARSED`, `NORMALIZED`, `INVALID`, `BLOCKED_BY_MAPPING`, `BLOCKED_BY_LICENSE`, `MATCH_CANDIDATES`, `READY_FOR_DRAFT`, `DRAFTED`, `UNCHANGED`, `REJECTED`. Batch używa `RECEIVED`, `QUEUED`, `PROCESSING`, `COMPLETED`, `COMPLETED_WITH_ISSUES`, `FAILED`. Funkcja `exercise_import.refresh_batch_projection` odbudowuje status i liczniki z rekordów; liczniki nie są źródłem prawdy.

Etapy Spring Batch mają chunk 50 i retry tylko dla przejściowych błędów dostępu do danych. Parse jest strumieniowym taskletem, a etapy rekordowe idempotentnymi chunkami. Unikalność `(batch_id,row_number)`, `import_record_id`, `(exercise_id,version_number)` i blokada advisory dla agregatu zapobiegają duplikatom. Nieudany job wznawia się przez `POST .../batches/{id}/restart`; zakończone rekordy mają status wykluczający ponowne wykonanie kroku.

## Normalizacja, walidacja i matching

Normalizacja `unicode-nfkc-sort-v1` stosuje NFKC, redukcję whitespace, BCP 47 locale, wielkie stabilne kody, zatwierdzone mapowania i sortowanie zbiorów semantycznych. Kroki instrukcji zachowują kolejność. SHA-256 powstaje z deterministycznego JSON wszystkich pól wykonania, dawki, klasyfikacji i ekspozycji.

Walidacja obejmuje kontrakt, słowniki, licencję źródła i rekordu, instrukcje, cele, ruch, dawkę, load profile i opublikowaną anatomię. Problem ma stabilny kod, etap, severity, JSON Pointer i komunikat. Nieznana wartość słownika tworzy `import_mapping(PENDING)` i blokuje rekord.

Matching `deterministic-catalog-v1` stosuje kolejno source reference, checksum, a następnie ograniczoną listę (maks. 5) po nazwie/aliasie i cechach: locale, wzorzec, pozycja, sprzęt, jednostronność i charakter obciążenia. Wynik i powody są trwałe. Inne źródło zawsze wymaga `SAME`, `DIFFERENT` albo `UNSURE` człowieka.

## Szkic, recenzja i publikacja

Brak powiązania tworzy nowe `Exercise` i v1; zmiana powiązanego rekordu tworzy kolejny `DRAFT`; ten sam checksum daje `UNCHANGED`. Szkic i source reference są tworzone w jednej transakcji. Publikacja wymaga najnowszych akceptacji `CONTENT`, `TECHNIQUE`, `ANATOMY_EXPOSURE`, `LICENSE` oraz `MEDIA`, jeśli są media, a także kompletnego load/anatomy/evidence i braku nierozwiązanych `ERROR/BLOCKER`. Cel `THERAPEUTIC_EXERCISE` wymaga co najmniej dwóch różnych reviewer subjects. Użytkownik pochodzi wyłącznie z JWT Keycloak.

Publikacja atomowo ustawia `PUBLISHED`, dopisuje audyt i `ExerciseVersionPublished` do istniejącego outbox. Trigger blokuje zmianę wersji i wszystkich semantycznych dzieci. Wycofanie zmienia tylko stan i timestamp; definicja pozostaje niezmienna.

## API i role

Endpointy mają prefiks `/api/v1`; operacyjne ścieżki to `/admin/exercise-import/...` i `/admin/exercise-versions/...`. Dostęp ma `CONTENT_ADMIN` lub `SYSTEM_ADMIN`; `forceReprocess=true` wymaga `SYSTEM_ADMIN`. Upload wymaga `Idempotency-Key`, zwraca 202, `batchId`, `Location` i URL statusu. Dostępne są źródła, upload/status, filtrowane rekordy, szczegóły, decyzja match/mapowania, szkic, diff, recenzje, publikacja oraz eksport problemów JSONL/CSV. UI jest pod `/admin/exercise-import`.

## Uruchomienie, retencja i diagnostyka

Compose montuje nazwany wolumen `motion-exercise-import` w `/var/lib/moves/exercise-import`; testy używają osobnego katalogu tymczasowego. Sprawdzaj `exercise_import.import_batch`, `batch_*`, `import_issue` i log kroku. Restart kontenerów zachowuje bazę i artefakt.

Artefaktu nie wolno usuwać niezależnie od batcha. Retencja MVP jest bezterminowa. Planowane usuwanie musi najpierw objąć zatwierdzoną politykę, zakończone batch-e poza okresem audytu, wpis audytowy i transakcję oznaczającą rekord; dopiero osobny worker usuwa plik. Nie używać ręcznego `rm` na wolumenie.

Ograniczenia MVP: tylko JSONL 1.0, lokalny filesystem za portem, brak pobierania mediów i brak automatycznego tworzenia anatomii. UI nie jest pełnym systemem redakcyjnym. Następny krok to kontrolowana obsługa retencji oraz media upload korzystający z tego samego wzorca portu.
