# Moves — masowy import ćwiczeń i docelowy model danych

Data decyzji: 2026-07-21  
Zakres: `anatomyreference`, `exercisecatalog`, przygotowanie danych dla `trainingplanning`, `loadanalysis` i `safety`

## 1. Decyzja

Rekomendowanym źródłem prawdy jest **relacyjny model PostgreSQL**, uzupełniony przez `JSONB` wyłącznie w warstwie wejściowej i dla danych jeszcze niezinterpretowanych. Import powinien być asynchronicznym, wznawialnym pipeline'em:

```text
SOURCE → RAW → NORMALIZED → VALIDATED → MATCHED → DRAFT → REVIEWED → PUBLISHED
```

Nie należy importować danych bezpośrednio do tabel produkcyjnych ani automatycznie publikować ćwiczeń pochodzących z zewnętrznego katalogu. Każdy rekord źródłowy zachowuje provenance, licencję, hash i pełny ślad decyzji. Dopiero zatwierdzenie redakcyjne tworzy lub aktualizuje szkic `ExerciseVersion`; publikacja tworzy niezmienną wersję używaną w planach.

### Dlaczego PostgreSQL, a nie baza dokumentowa lub grafowa

- plan wskazuje dokładną, niezmienną wersję ćwiczenia;
- anatomia, sprzęt, wzorce ruchowe, źródła i media mają relacje wiele-do-wielu;
- potrzebne są klucze obce, unikalność, transakcje, audyt i kontrola publikacji;
- wyszukiwanie katalogowe dobrze obsłużą projekcje SQL i indeksy;
- hierarchia anatomii ma małą głębokość i nie uzasadnia osobnej bazy grafowej;
- `JSONB` pozostaje przydatny dla surowego wejścia, diagnostyki i rozszerzeń źródłowych, ale nie powinien zastępować modelu domenowego.

## 2. Najważniejsze rozdzielenia domenowe

1. **Import nie jest katalogiem.** Import przechowuje dane obce i błędy; katalog przechowuje dane zatwierdzone przez Moves.
2. **`Exercise` nie jest nazwą.** Jest stabilną tożsamością przepisywalnego ruchu; nazwy są lokalizowane i wersjonowane.
3. **`ExerciseVersion` nie jest wariantem.** Jest niezmiennym stanem jednego ćwiczenia. Istotnie różne wykonanie, zmieniające dawkę lub ekspozycję, jest osobnym `Exercise`.
4. **Plan wskazuje `exerciseVersionId`, nigdy „najnowszą wersję”.**
5. **Katalog opisuje ekspozycję, nie przeciwwskazania.** Ograniczenia konkretnej osoby należą do `safety`.
6. **Treść źródłowa nie jest dowodem.** Deklaracja dostawcy, publikacja naukowa i opinia eksperta mają różne typy provenance.
7. **Media mają własną licencję.** Prawo do importu opisu nie oznacza prawa do kopiowania zdjęcia lub filmu.

## 3. Granica tożsamości ćwiczenia

`Exercise` powinien odpowiadać jednostce, którą można jednoznacznie przepisać i wykonać. Przykładowo:

- `bodyweight-squat`, `goblet-squat` i `box-squat` to osobne ćwiczenia;
- zmiana języka, korekta literówki lub nowy film nie tworzą nowego ćwiczenia;
- zmiana chwytu, podporu, sprzętu, pozycji, jednostronności, dominującego ROM albo charakteru obciążenia tworzy osobne ćwiczenie, jeżeli może zmienić dawkowanie, bezpieczeństwo lub profil ekspozycji;
- progresje i regresje są relacjami między ćwiczeniami, a nie dowolnym tekstowym tagiem;
- drobne parametry planu, np. liczba powtórzeń, czas, tempo i obciążenie, należą do recepty ćwiczenia, nie tworzą nowego ćwiczenia.

Nie należy tworzyć jednego megarekordu „przysiad” z nieograniczonym zestawem warunkowych wariantów. Utrudniłoby to wersjonowanie, safety i jednoznaczne wykonanie.

## 4. Model warstwy importowej

Warstwa importowa może znajdować się w schemacie PostgreSQL `exercise_catalog`, ale jej tabele powinny być wyraźnie oddzielone prefiksem `import_`.

### `import_source`

Rejestr źródeł i praw do wykorzystania danych.

```text
id UUID
code VARCHAR UNIQUE
display_name VARCHAR
source_type MANUAL_FILE | PARTNER_API | OPEN_DATASET | INTERNAL_AUTHORING
base_uri VARCHAR?
license_code VARCHAR?
license_text_snapshot TEXT?
terms_uri VARCHAR?
commercial_use_allowed BOOLEAN
derivatives_allowed BOOLEAN
redistribution_allowed BOOLEAN
attribution_text TEXT?
license_verified_by UUID?
license_verified_at TIMESTAMPTZ?
status ACTIVE | SUSPENDED | REVOKED
created_at TIMESTAMPTZ
```

Brak pozytywnej decyzji licencyjnej blokuje publikację, nawet jeśli parsowanie przebiegło poprawnie.

### `import_batch`

Jedno przesłanie pliku albo jeden przebieg konektora.

```text
id UUID
source_id UUID FK
request_key VARCHAR
original_filename VARCHAR?
format JSONL | XLSX | CSV_ZIP | API
schema_version VARCHAR
content_sha256 CHAR(64)
status RECEIVED | PROCESSING | NEEDS_MAPPING | NEEDS_REVIEW | COMPLETED | FAILED | CANCELLED
record_count INT
accepted_count INT
warning_count INT
rejected_count INT
created_by UUID
created_at TIMESTAMPTZ
started_at TIMESTAMPTZ?
finished_at TIMESTAMPTZ?
failure_summary TEXT?
```

Unikalność: `(source_id, request_key)` oraz, zależnie od polityki, `(source_id, content_sha256)`. Ponowienie tego samego żądania zwraca istniejący batch.

### `import_record`

Niezmienny rekord z wejścia i jego stan przetwarzania.

```text
id UUID
batch_id UUID FK
row_number INT
source_record_key VARCHAR?
source_revision VARCHAR?
raw_payload JSONB
raw_sha256 CHAR(64)
normalized_payload JSONB?
normalized_sha256 CHAR(64)?
status RECEIVED | PARSED | NORMALIZED | INVALID | MATCH_CANDIDATES | READY_FOR_DRAFT | DRAFTED | REJECTED
matched_exercise_id UUID?
created_draft_version_id UUID?
created_at TIMESTAMPTZ
processed_at TIMESTAMPTZ?
```

Unikalność: `(batch_id, row_number)`. Dodatkowy indeks na `(source_record_key, source_revision)`.

### `import_source_reference`

Trwałe powiązanie zewnętrznego rekordu z tożsamością Moves.

```text
source_id UUID
source_record_key VARCHAR
exercise_id UUID
last_source_revision VARCHAR?
last_raw_sha256 CHAR(64)
first_seen_batch_id UUID
last_seen_batch_id UUID
link_status ACTIVE | DISPUTED | DETACHED
linked_by UUID
linked_at TIMESTAMPTZ
PRIMARY KEY (source_id, source_record_key)
```

To jest jedyny przypadek, w którym kolejny import może automatycznie rozpoznać istniejącą tożsamość. Nadal nie aktualizuje opublikowanej wersji — tworzy propozycję zmian.

### `import_mapping`

Wersjonowane mapowania słowników dostawcy na pojęcia Moves.

```text
id UUID
source_id UUID
mapping_type EQUIPMENT | MOVEMENT_PATTERN | PLANE | CONTRACTION | LOAD_CHARACTERISTIC | ANATOMY | UNIT
source_value VARCHAR
target_code VARCHAR?
status PROPOSED | APPROVED | REJECTED | IGNORED
confidence NUMERIC(4,3)?
mapping_version INT
decided_by UUID?
decided_at TIMESTAMPTZ?
UNIQUE (source_id, mapping_type, source_value, mapping_version)
```

Automatyczne sugestie mogą powstawać regułowo lub przez model językowy, ale do mapowań anatomicznych i klinicznych wymagane jest zatwierdzenie eksperta.

### `import_issue`

```text
id UUID
record_id UUID
stage PARSE | NORMALIZE | LICENSE | TAXONOMY | DUPLICATE | SEMANTIC | CLINICAL | MEDIA
severity INFO | WARNING | ERROR | BLOCKER
code VARCHAR
json_pointer VARCHAR?
message TEXT
details JSONB?
resolved_by UUID?
resolved_at TIMESTAMPTZ?
resolution_note TEXT?
```

### `import_match_candidate`

```text
record_id UUID
exercise_id UUID
algorithm_version VARCHAR
score NUMERIC(5,4)
reasons JSONB
decision SAME | DIFFERENT | UNSURE | NOT_REVIEWED
decided_by UUID?
decided_at TIMESTAMPTZ?
PRIMARY KEY (record_id, exercise_id, algorithm_version)
```

## 5. Docelowy model katalogu

### `exercise`

Stabilna tożsamość ćwiczenia.

```text
id UUID PK
slug VARCHAR UNIQUE
status ACTIVE | WITHDRAWN
created_at TIMESTAMPTZ
created_by UUID
```

Nie przechowuje instrukcji, anatomii ani parametrów wykonania.

### `exercise_version`

Semantyczna, niezmienna po publikacji definicja.

```text
id UUID PK
exercise_id UUID FK
version_number INT
status DRAFT | IN_REVIEW | CHANGES_REQUESTED | APPROVED | PUBLISHED | WITHDRAWN
replaces_version_id UUID?
purpose TRAINING | THERAPEUTIC_EXERCISE | WARM_UP | COOL_DOWN | ASSESSMENT
body_position_code VARCHAR
unilateral_policy BILATERAL | LEFT_RIGHT | ALTERNATING | NOT_APPLICABLE
difficulty_band INTRODUCTORY | BASIC | INTERMEDIATE | ADVANCED
semantic_checksum CHAR(64)
change_summary TEXT?
created_from_import_record_id UUID?
created_by UUID
created_at TIMESTAMPTZ
approved_by UUID?
approved_at TIMESTAMPTZ?
published_at TIMESTAMPTZ?
UNIQUE (exercise_id, version_number)
```

Po `PUBLISHED` dane wersji i wszystkie semantyczne dzieci są tylko do odczytu. Zmiana tworzy nowy draft z `replaces_version_id`.

### `exercise_version_text`

```text
exercise_version_id UUID
locale VARCHAR
title VARCHAR
short_description TEXT
start_position TEXT
execution_summary TEXT
breathing_cue TEXT?
completion_criteria TEXT?
plain_language_level STANDARD | SIMPLE
PRIMARY KEY (exercise_version_id, locale)
```

W MVP zmiana instrukcji technicznej tworzy nową `ExerciseVersion`. Jeżeli później korekty tłumaczeń staną się częste, można wydzielić niezależne wersjonowanie treści, bez zmiany modelu semantycznego.

### `exercise_instruction_step`

```text
id UUID
exercise_version_id UUID
locale VARCHAR
step_number SMALLINT
instruction TEXT
cue_type SETUP | ACTION | BREATHING | CONTROL | FINISH
UNIQUE (exercise_version_id, locale, step_number)
```

### `exercise_alias`

Alias służy do wyszukiwania i deduplikacji, nie jest odrębną tożsamością.

```text
exercise_id UUID
locale VARCHAR
alias_normalized VARCHAR
alias_display VARCHAR
origin INTERNAL | IMPORTED
source_id UUID?
UNIQUE (locale, alias_normalized, exercise_id)
```

### `exercise_relation`

```text
source_exercise_id UUID
target_exercise_id UUID
relation_type PROGRESSION_TO | REGRESSION_TO | ALTERNATIVE_TO | RELATED_TO
rationale TEXT?
review_status DRAFT | APPROVED
PRIMARY KEY (source_exercise_id, target_exercise_id, relation_type)
```

Relacja nie może wskazywać ćwiczenia na nie samo, a `PROGRESSION_TO` i `REGRESSION_TO` powinny być kontrolowane pod kątem sprzeczności oraz cykli.

### Słowniki i klasyfikacje

W kodzie mogą istnieć stabilne enumy tylko dla małych, zamkniętych pojęć. Słowniki rozwijane redakcyjnie powinny być tabelami z kodem, statusem i wersją:

- `movement_pattern`;
- `equipment`;
- `body_position`;
- `dose_metric`;
- `load_characteristic`;
- `anatomical_structure` w module `anatomyreference`.

### `exercise_movement_characteristic`

```text
id UUID
exercise_version_id UUID
movement_pattern_code VARCHAR
plane_of_motion SAGITTAL | FRONTAL | TRANSVERSE | MULTIPLANAR | NOT_APPLICABLE
joint_action_code VARCHAR?
contraction_type DYNAMIC | ISOMETRIC | CONCENTRIC_EMPHASIS | ECCENTRIC_EMPHASIS | MIXED
load_characteristic_code VARCHAR
rom_band SMALL | MODERATE | LARGE | VARIABLE | NOT_APPLICABLE
impact_band NONE | LOW | MODERATE | HIGH
compression_band UNKNOWN | LOW | MODERATE | HIGH
shear_band UNKNOWN | LOW | MODERATE | HIGH
rotation_band NONE | LOW | MODERATE | HIGH
calculation_role PRIMARY | SECONDARY | DESCRIPTIVE_ONLY
```

### `exercise_contribution`

Zgodnie z wcześniejszym modelem Moves:

```text
id UUID
exercise_version_id UUID
anatomical_structure_id UUID
role PRIMARY | SECONDARY | STABILIZER
load_channel VARCHAR
contribution_band LOW | MODERATE | HIGH
coefficient_low NUMERIC?
coefficient_high NUMERIC?
confidence_class LOW | MODERATE | HIGH
evidence_grade STRONG | MODERATE | WEAK | EXPERT_ONLY
calculation_role ALLOCATION | DESCRIPTIVE_ONLY
laterality_rule SAME_AS_EXECUTION | LEFT | RIGHT | BILATERAL | NOT_APPLICABLE
condition_expression JSONB?
review_status DRAFT | APPROVED | REJECTED
```

`condition_expression` może reprezentować wyłącznie zamknięty, walidowany język warunków, nigdy dowolny kod. Jeżeli warunków jest wiele albo istotnie zmieniają technikę, należy utworzyć osobne ćwiczenie.

### `exercise_equipment`

```text
exercise_version_id UUID
equipment_id UUID
requirement REQUIRED | OPTIONAL | ALTERNATIVE
quantity SMALLINT?
notes TEXT?
PRIMARY KEY (exercise_version_id, equipment_id)
```

### `exercise_dose_capability`

Definiuje, jakie parametry może przyjąć recepta w planie; nie przechowuje konkretnej dawki.

```text
exercise_version_id UUID
metric_code REPS | DURATION | DISTANCE | EXTERNAL_LOAD | HOLD_DURATION | RPE_TARGET | RIR_TARGET | TEMPO | ROM
role REQUIRED | OPTIONAL | DERIVED
unit_code VARCHAR?
minimum_value NUMERIC?
maximum_value NUMERIC?
default_value NUMERIC?
PRIMARY KEY (exercise_version_id, metric_code)
```

### Dowody i provenance

```text
evidence_source
  id, type PUBLICATION | GUIDELINE | DATASET | SOURCE_CATALOG | EXPERT_JUDGMENT,
  title, authors?, publication_year?, doi?, pmid?, uri?, license_code?, citation

exercise_evidence_link
  exercise_version_id, contribution_id?, movement_characteristic_id?, evidence_source_id,
  claim_type, support_level, excerpt_locator?, reviewer_note?
```

Źródło należy przypisać do konkretnego twierdzenia, a nie tylko ogólnie do ćwiczenia.

### Media

```text
media_asset
  id, storage_ref?, external_uri?, sha256?, mime_type, duration_ms?, width?, height?,
  license_code?, attribution_text?, commercial_use_allowed, redistribution_allowed,
  rights_verified_by?, rights_verified_at?, status

exercise_media
  exercise_version_id, media_asset_id, role DEMO | START_POSITION | END_POSITION | THUMBNAIL,
  sort_order, review_status
```

Plik może zostać skopiowany do magazynu obiektowego tylko po potwierdzeniu praw. W przeciwnym razie zachowujemy metadane lub odrzucamy materiał; nie hotlinkujemy niepewnych zasobów w produkcie.

### Recenzja

```text
exercise_review
  id, exercise_version_id, review_type CONTENT | TECHNIQUE | ANATOMY | CLINICAL | LICENSE | MEDIA,
  reviewer_id, decision APPROVE | REQUEST_CHANGES | REJECT,
  checklist_version, comments, created_at
```

Publikacja wymaga co najmniej:

- zatwierdzenia treści i techniki;
- zatwierdzenia profilu anatomii/ekspozycji przez uprawnionego eksperta;
- kontroli licencji źródeł i mediów;
- braku nierozwiązanych `BLOCKER` i `ERROR`;
- drugiej niezależnej recenzji dla ćwiczeń terapeutycznych i danych używanych przez safety.

## 6. Kanoniczny format importu

Podstawowym formatem powinien być **JSON Lines (`.jsonl`, UTF-8)**: jeden niezależny rekord na linię. Pozwala przetwarzać duże pliki strumieniowo, raportować błąd konkretnego rekordu i reprezentować zagnieżdżone dane bez mnożenia arkuszy.

XLSX powinien być formatem redakcyjnym dla ludzi, konwertowanym do tego samego kontraktu. Płaski CSV nadaje się tylko do prostych katalogów; dla instrukcji, anatomii, dowodów i mediów wymaga zestawu powiązanych plików w ZIP.

Przykład rekordu:

```json
{
  "schemaVersion": "moves.exercise-import/1.0",
  "source": {
    "code": "partner-a",
    "recordKey": "EX-1042",
    "revision": "7",
    "locale": "pl-PL"
  },
  "identity": {
    "title": "Przysiad z masą ciała",
    "aliases": ["przysiad bez obciążenia"]
  },
  "definition": {
    "purpose": ["TRAINING", "THERAPEUTIC_EXERCISE"],
    "bodyPosition": "STANDING",
    "unilateralPolicy": "BILATERAL",
    "difficultyBand": "BASIC",
    "startPosition": "Stań stabilnie, ze stopami mniej więcej na szerokość bioder.",
    "steps": [
      {"order": 1, "type": "ACTION", "text": "Cofnij biodra i ugnij kolana."},
      {"order": 2, "type": "CONTROL", "text": "Utrzymaj stopy oparte o podłoże."},
      {"order": 3, "type": "FINISH", "text": "Wróć spokojnie do pozycji stojącej."}
    ]
  },
  "classification": {
    "movementPatterns": ["SQUAT"],
    "planes": ["SAGITTAL"],
    "contractionTypes": ["DYNAMIC"],
    "loadCharacteristics": ["COMPRESSION", "STABILIZATION"],
    "impactBand": "NONE"
  },
  "equipment": [],
  "doseCapabilities": [
    {"metric": "REPS", "role": "REQUIRED"},
    {"metric": "TEMPO", "role": "OPTIONAL"},
    {"metric": "RPE_TARGET", "role": "OPTIONAL"}
  ],
  "contributions": [
    {
      "anatomyCode": "MUSCLE_GROUP:KNEE_EXTENSORS",
      "role": "PRIMARY",
      "loadChannel": "DYNAMIC_EXPOSURE",
      "band": "HIGH",
      "confidence": "MODERATE",
      "evidenceGrade": "EXPERT_ONLY",
      "calculationRole": "ALLOCATION"
    }
  ],
  "evidence": [
    {
      "localKey": "src-1",
      "type": "SOURCE_CATALOG",
      "title": "Partner A exercise catalogue",
      "uri": "https://example.invalid/exercises/EX-1042"
    }
  ],
  "media": [
    {
      "uri": "https://example.invalid/media/EX-1042.mp4",
      "role": "DEMO",
      "licenseCode": "CONTRACTUAL",
      "commercialUseAllowed": true,
      "redistributionAllowed": true
    }
  ]
}
```

Sekcja `contributions` może zostać pominięta. Rekord trafi wtedy do kolejki uzupełnienia eksperckiego, ale nie może być opublikowany jako pełne ćwiczenie przeznaczone dla `loadanalysis` lub `safety`.

Pola źródłowe typu `contraindications`, `injuries`, `treatment`, `safeFor` należy zachować w `raw_payload` i pokazać recenzentowi jako niezweryfikowane twierdzenia. Nie wolno mapować ich automatycznie na ograniczenia uczestnika ani reguły safety.

## 7. Pipeline importu

### Krok 1 — rejestracja źródła

- zatwierdzenie pochodzenia i licencji;
- zapis wersji warunków/licencji;
- utworzenie adaptera albo szablonu mapowania;
- określenie, które pola można importować, a które tylko zachować do wglądu.

### Krok 2 — przyjęcie pliku

- obliczenie SHA-256 przed przetwarzaniem;
- utworzenie `import_batch`;
- zapis oryginału jako niezmiennego artefaktu audytowego;
- walidacja MIME, kodowania, limitu rozmiaru i wersji schematu;
- skanowanie plików multimedialnych, jeżeli archiwum może je zawierać.

### Krok 3 — parsowanie

- strumieniowy odczyt;
- jeden `import_record` na rekord;
- błąd jednego rekordu nie unieważnia całego batcha, chyba że uszkodzony jest kontrakt pliku;
- raw payload nie jest nadpisywany.

### Krok 4 — normalizacja

- normalizacja Unicode, białych znaków, kodów języka i jednostek;
- mapowanie słowników dostawcy przez zatwierdzone `import_mapping`;
- rozdzielenie danych znanych, nieznanych i zabronionych;
- wygenerowanie deterministycznego `normalized_sha256`.

Nie należy „poprawiać” instrukcji przez LLM w sposób ukryty. Wygenerowana propozycja musi być oznaczona, zachować wersję promptu/modelu i przejść recenzję.

### Krok 5 — walidacja

Poziomy:

1. `SCHEMA` — typy, wymagane pola, rozmiary, formaty;
2. `REFERENCE` — istnienie kodów anatomii, sprzętu, jednostek i wzorców;
3. `SEMANTIC` — zgodność parametrów, np. izometria wymaga czasu, a `LEFT_RIGHT` nie może mieć reguły `BILATERAL`;
4. `CONSISTENCY` — brak sprzecznych klasyfikacji, cykli i podwójnego alokowania rodzica/dziecka anatomii;
5. `LICENSE` — prawo do treści i każdego medium;
6. `EDITORIAL` — kompletność, prosty język, brak diagnoz i niebezpiecznych instrukcji;
7. `EXPERT` — technika, anatomia, zastosowanie terapeutyczne i ekspozycja.

### Krok 6 — deduplikacja

Kolejność:

1. dokładne `(source_id, source_record_key)` — automatyczne rozpoznanie istniejącego powiązania;
2. identyczny `normalized_sha256` — automatyczna sugestia duplikatu;
3. dopasowanie kandydatów na podstawie aliasów, języka, wzorca ruchu, pozycji, sprzętu, jednostronności i charakteru obciążenia;
4. ręczna decyzja `SAME` albo `DIFFERENT`.

Podobieństwo nazwy nigdy nie powinno automatycznie scalać ćwiczeń z różnych źródeł. Algorytm ma generować maksymalnie kilka kandydatów oraz zrozumiałe powody dopasowania.

### Krok 7 — utworzenie szkicu

- brak dopasowania: nowy `Exercise` i `ExerciseVersion(DRAFT)`;
- potwierdzone dopasowanie bez zmiany semantycznej: aktualizacja aliasu/provenance albo brak operacji;
- potwierdzone dopasowanie ze zmianą: nowa wersja `DRAFT`, nigdy modyfikacja `PUBLISHED`;
- konflikt źródeł: szkic z flagą do adjudykacji, bez automatycznego wyboru „nowszego” źródła.

### Krok 8 — recenzja i publikacja

- widok różnic między źródłem, aktualną wersją i szkicem;
- osobne kolejki: treść, technika, anatomia/ekspozycja, licencja/media;
- możliwość zatwierdzenia wielu rekordów tylko wtedy, gdy mają ten sam typ zmian i nie zawierają klinicznych rozbieżności;
- publikacja w jednej transakcji;
- event `ExerciseVersionPublished` przez outbox.

## 8. Idempotencja, współbieżność i wydajność

- identyfikator idempotencji: `(source_id, request_key)`;
- identyczny plik nie tworzy drugiego batcha bez jawnego `forceReprocess`;
- identyczny rekord w kolejnym batchu kończy się jako `UNCHANGED`;
- produkcyjny zapis draftu chroni unikalność `(exercise_id, version_number)` i optimistic locking;
- rekord może być przetwarzany przez jeden worker; pobieranie przez `FOR UPDATE SKIP LOCKED`;
- operacje wykonywane w chunkach, np. 100–500 rekordów, a nie w jednej transakcji dla całego pliku;
- retry tylko dla błędów przejściowych; błąd domenowy trafia do `import_issue`, nie do nieskończonej pętli;
- parser nie ładuje całego pliku do pamięci;
- indeksy co najmniej na statusach kolejki, source key, hashach i `exercise_id`;
- statystyki batcha są projekcją odbudowywalną, nie jedynym źródłem prawdy.

Do realizacji pipeline'u rekomendowany jest **Spring Batch** z repozytorium metadanych w PostgreSQL: daje chunking, restart, skip/retry i jednoznaczny stan joba. Warstwa domenowa pozostaje niezależna od Spring Batch; step wywołuje porty aplikacyjne `NormalizeImportRecord`, `ValidateImportRecord`, `FindExerciseMatch` i `CreateExerciseDraft`.

Kafka nie jest potrzebna do MVP. Po publikacji wystarczy transakcyjny outbox, zgodny z modularnym monolitem.

## 9. API administracyjne

Minimalny kontrakt:

```text
POST   /admin/exercise-import/sources
POST   /admin/exercise-import/batches                 upload/uruchomienie konektora
GET    /admin/exercise-import/batches/{id}
GET    /admin/exercise-import/batches/{id}/records
GET    /admin/exercise-import/records/{id}
POST   /admin/exercise-import/records/{id}/match       decyzja SAME/DIFFERENT
POST   /admin/exercise-import/mappings/{id}/decision
POST   /admin/exercise-import/records/{id}/create-draft
POST   /admin/exercise-versions/{id}/reviews
POST   /admin/exercise-versions/{id}/publish
```

Upload powinien zwracać `202 Accepted` i identyfikator batcha. Pobieranie list musi być paginowane. Eksport błędów do CSV/JSONL powinien zawierać `row_number`, `source_record_key`, `stage`, `severity`, `code`, `json_pointer` i komunikat.

## 10. MVP importu

### Zakres pierwszego etapu

1. `JSONL` jako format kanoniczny oraz prosty szablon XLSX dla redaktorów;
2. `import_source`, `import_batch`, `import_record`, `import_issue`, `import_source_reference` i `import_mapping`;
3. pipeline Spring Batch: parse → normalize → validate → match candidates → draft;
4. deterministyczne mapowania słowników;
5. wykrywanie duplikatów bez automatycznego scalania między źródłami;
6. widok recenzenta z diffem i błędami;
7. niezmienna publikacja `ExerciseVersion`;
8. osobna weryfikacja licencji mediów;
9. pierwszy kontrolowany import 30–50 ćwiczeń;
10. następnie import 300–500 ćwiczeń i pomiar kosztu recenzji.

### Odłożyć

- automatyczne generowanie pełnych profili anatomii przez AI;
- import wszystkich struktur FMA/Uberon;
- bazę grafową;
- automatyczne pobieranie materiałów z internetu;
- samoczynną publikację „zaufanego dostawcy”;
- komputerową ocenę jakości ruchu z filmu;
- import reguł przeciwwskazań bez osobnego procesu safety;
- budowę osobnego mikroserwisu importowego i Kafki.

## 11. Kryteria jakości i metryki

- 100% opublikowanych wersji ma źródło i decyzję licencyjną;
- 100% planów wskazuje konkretny `exerciseVersionId`;
- 0 automatycznych cross-source merge bez decyzji człowieka;
- 0 modyfikacji danych opublikowanej wersji;
- 100% ćwiczeń terapeutycznych ma wymaganą podwójną recenzję;
- odsetek rekordów `UNCHANGED`, `DUPLICATE_CANDIDATE`, `BLOCKED_BY_MAPPING`, `BLOCKED_BY_LICENSE` i `PUBLISHED` jest raportowany per źródło;
- mierzony jest czas głównej adnotacji, recenzji i adjudykacji;
- raportowana jest zgodność ekspertów dla kategorii i pasm ekspozycji;
- każdy batch można wznowić po awarii bez duplikacji;
- ponowny import tego samego pliku daje ten sam wynik.

## 12. Rekomendowana kolejność implementacji

1. Ustalić zamknięty zakres słowników MVP: wzorce ruchowe, sprzęt, pozycje, jednostki, kanały i minimalna anatomia.
2. Zaimplementować produkcyjny szkielet `Exercise`/`ExerciseVersion` z niezmiennością publikacji.
3. Zaimplementować provenance i licencje, zanim pojawią się zewnętrzne dane.
4. Dodać staging i pipeline importu.
5. Dodać deduplikację i powiązania źródłowe.
6. Dodać workflow recenzji i publikacji.
7. Wykonać import pilotażowy 30–50 ręcznie zweryfikowanych ćwiczeń.
8. Dopiero po analizie błędów rozszerzyć katalog do 300–500 pozycji.

## 13. Ostateczna rekomendacja

Moves powinno budować **własny, kuratorowany katalog**, a zewnętrzne zbiory traktować jako materiał wejściowy, nie prawdę domenową. Najważniejszym elementem rozwiązania nie jest szybkie wczytanie tysięcy nazw, lecz kontrolowane przejście od obcego rekordu do jednoznacznej, wersjonowanej i audytowalnej definicji ćwiczenia. Model `PostgreSQL + staging JSONB + niezmienne ExerciseVersion + ekspercka publikacja` jest najprostszy, wystarczająco skalowalny i zgodny z wcześniej przyjętą architekturą Moves.
