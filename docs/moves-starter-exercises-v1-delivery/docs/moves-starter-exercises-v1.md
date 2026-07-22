# Moves starter exercises V1 — raport wykonania

## 1. Stan początkowy

- Repozytorium: `boryspawel/moves`
- Gałąź bazowa: `main`
- HEAD odczytany przez GitHub: `7f0b435cfa2550b7c6c7129cb05c4fad0c441ed7`
- Stan working tree: **nieobserwowalny przez konektor GitHub**; pakiet powstał lokalnie poza repozytorium, bez commita i push.
- Najwyższa migracja widoczna w `main`: `V026__create_recovery_episodes.sql`.
- Kontrakt importowy: `moves.exercise-import/1.0`.
- Słowniki as-is:
  - sprzęt: `BODYWEIGHT`, `MAT`, `DUMBBELL`, `BAND`;
  - pozycje: `STANDING`, `SUPINE`, `PRONE`, `KNEELING`;
  - dawkowanie: `REP`, `SECOND`, `METER`.
- Liczba opublikowanych struktur anatomii wynikająca z migracji produkcyjnych: **0**. Jest to wniosek ze statycznej analizy migracji, a nie zapytanie do działającej bazy. Istniejący test integracyjny dodaje wyłącznie `TEST_WHOLE_BODY` w setupie.

## 2. Braki reprezentacji i decyzje

| Potrzebna wartość | Typ | Istnieje as-is | Działanie |
|---|---|---:|---|
| `SEATED`, `SIDE_LYING`, `QUADRUPED`, `HALF_KNEELING`, `SPLIT_STANCE` | pozycja | nie | V028 |
| `FRONT_SUPPORT`, `SIDE_SUPPORT`, `SQUAT` | pozycja | nie | V028 |
| `CHAIR`, `BENCH`, `KETTLEBELL`, `STEP_BOX`, `STRAP`, `WALL` | sprzęt | nie | V028 |
| `MINUTE`, `CONTACT` | jednostka | nie | niepotrzebne w V1; użyto `SECOND`, `REP`, `METER` |
| anatomia katalogowa | taksonomia | brak seeda | V027: kontrolowany, opublikowany podzbiór używany przez katalog |

Nie użyto nieadekwatnych zamienników pozycji ani sprzętu. `ISOMETRIC` przy stretchingu statycznym oznacza statyczną ekspozycję bez ruchu; nie oznacza pomiaru siły izometrycznej.

Wcześniej planowany lokalny plik `V019__seed_anatomy_reference_v1.sql` nie może być użyty pod tą nazwą, ponieważ `main` zawiera już `V019__add_participant_time_zone.sql`. Pakiet używa kolejnych numerów V027 i V028.

## 3. Pokrycie katalogu

| Segment | Liczba | Poziomy | Główne regiony | Sprzęt |
|---|---:|---|---|---|
| Ogólny trening siłowy | 36 | FOUNDATIONAL, INTERMEDIATE | całe ciało, kończyny, tułów | masa ciała, hantle, guma, kettlebell, ławka |
| Mobilność | 22 | FOUNDATIONAL, INTERMEDIATE | staw skokowy, biodro, kręgosłup, bark, szyja, nadgarstek | mata, ściana, krzesło |
| Stretching | 18 | FOUNDATIONAL, INTERMEDIATE | łydka, udo, biodro, klatka, bark, szyja, nadgarstek | mata, ściana, krzesło, pasek, ławka |
| Ćwiczenia terapeutyczne | 32 | FOUNDATIONAL, INTERMEDIATE | kolano, stopa, biodro, lędźwie, bark, szyja | mata, guma, ściana, krzesło, stopień |
| Równowaga i kontrola motoryczna | 8 | FOUNDATIONAL, INTERMEDIATE | stopa, staw skokowy, biodro, tułów | ściana opcjonalnie |
| Oddychanie i regeneracja | 4 | FOUNDATIONAL | klatka piersiowa, tułów | mata, krzesło |

## 4. Jakość danych

- Rekordy: **120**
- Aliasy: **120**
- Rekordy jednostronne: **63**
- Contributions: średnio **3.55**, zakres **3–4**
- Rozmiar pliku: **186341 B**
- Najdłuższa linia: **1705 B**
- SHA-256: `b48464dd4ccd574b68d013233314ad14bba46160363d1995d29c3e8a5e31e3f1`

### Wzorce ruchowe
- `BREATHING`: 4
- `CARRY`: 2
- `HINGE`: 6
- `LOCOMOTION`: 3
- `LUNGE`: 4
- `MOBILITY`: 40
- `OTHER`: 37
- `PULL`: 6
- `PUSH`: 11
- `ROTATION`: 1
- `SQUAT`: 6

### Typy bodźca
- `BALANCE`: 3
- `MOBILITY`: 40
- `MOTOR_CONTROL`: 14
- `RECOVERY`: 4
- `STRENGTH`: 59

### Cele
- `RECOVERY`: 22
- `THERAPEUTIC_EXERCISE`: 32
- `TRAINING`: 66
- `WARM_UP`: 22

### Poziomy techniczne
- `FOUNDATIONAL`: 76
- `INTERMEDIATE`: 44

### Sprzęt
- `BAND`: 11
- `BENCH`: 4
- `BODYWEIGHT`: 7
- `CHAIR`: 25
- `DUMBBELL`: 10
- `KETTLEBELL`: 2
- `MAT`: 38
- `STEP_BOX`: 3
- `STRAP`: 1
- `WALL`: 21

### Pozycje
- `FRONT_SUPPORT`: 4
- `HALF_KNEELING`: 2
- `KNEELING`: 4
- `PRONE`: 3
- `QUADRUPED`: 5
- `SEATED`: 27
- `SIDE_LYING`: 3
- `SIDE_SUPPORT`: 1
- `SPLIT_STANCE`: 8
- `SQUAT`: 2
- `STANDING`: 44
- `SUPINE`: 17

## 5. Źródła i recenzja

Sidecar evidence zawiera co najmniej jedno źródło dla każdego rekordu. Wykorzystano stanowisko ACSM, metaanalizy stretchingu i ROM oraz wytyczne kliniczne JOSPT dla kolana, stawu skokowego, ścięgna Achillesa, odcinka lędźwiowego, barku i szyi. Dla równowagi wykorzystano przegląd Cochrane, a dla oddychania przegląd systematyczny z 2026 r.

- Rekordy wymagające recenzji klinicznej: **36**
- Niska pewność dowodów: **4**
- Współczynniki ekspozycji pozostają przedziałami redakcyjnymi, nie pomiarami EMG, siły ani ryzyka.
- Publikacja `THERAPEUTIC_EXERCISE` wymaga dwóch niezależnych reviewerów zgodnie z istniejącym guardem bazy.

## 6. Walidacja

### Wykonane lokalnie

- parsowanie 120 linii JSON: PASS;
- JSON Schema 2020-12: PASS;
- komplet wymaganych pól i brak pól nieobsługiwanych: PASS;
- enumy i słowniki po V028: PASS;
- unikalność `sourceRecordKey`: PASS;
- globalna unikalność nazw i aliasów po NFKC/casefold: PASS;
- dawkowanie i współczynniki: PASS;
- wszystkie kody anatomii istnieją w V027 i mają status `PUBLISHED`: PASS;
- brak konfliktów parent–child w tej samej gałęzi alokacji: PASS;
- segmenty 36/22/18/32/8/4: PASS;
- sidecary zgodne z JSONL: PASS;
- UTF-8, limit pliku i linii, kolejność deterministyczna: PASS;
- powtarzalny SHA-256: PASS.

### Niewykonane

Nie uruchomiono `sdkman use java 25`, Maven, PostgreSQL/Testcontainers ani rzeczywistego Spring Batch, ponieważ konektor GitHub nie udostępnia checkoutu ani środowiska wykonawczego repozytorium. Do pakietu dodano test `MovesStarterExercisesV1IntegrationTest`, który wykonuje import, tworzy 120 draftów i sprawdza wymuszony reimport jako 120 × `UNCHANGED`.

### Autorska próba redakcyjno-techniczna

Sprawdzono statycznie 30 rekordów w wymaganym rozkładzie: 6 siłowych, 5 mobilnościowych, 5 stretchingowych, 8 terapeutycznych, 3 równoważne/kontroli motorycznej i 3 oddechowe. Kontrola obejmowała tożsamość ruchu, nazwę, instrukcje, pozycję, sprzęt, wzorzec, dawkę, anatomię, stronę, konflikty hierarchiczne, język medyczny oraz źródło. Nie jest to niezależna recenzja kliniczna.

Rekordy próby:
- `moves-starter-v1:strength:band-pull-apart`
- `moves-starter-v1:strength:band-pulldown`
- `moves-starter-v1:strength:band-row`
- `moves-starter-v1:strength:band-triceps-extension`
- `moves-starter-v1:strength:bilateral-calf-raise`
- `moves-starter-v1:strength:bodyweight-hip-hinge`
- `moves-starter-v1:mobility:active-cross-body-shoulder`
- `moves-starter-v1:mobility:adductor-rock-back`
- `moves-starter-v1:mobility:ankle-circles`
- `moves-starter-v1:mobility:ankle-knee-to-wall`
- `moves-starter-v1:mobility:cat-camel`
- `moves-starter-v1:stretching:butterfly-adductor-stretch`
- `moves-starter-v1:stretching:child-pose-thoracolumbar-stretch`
- `moves-starter-v1:stretching:cross-body-posterior-shoulder-stretch`
- `moves-starter-v1:stretching:doorway-chest-stretch`
- `moves-starter-v1:stretching:half-kneeling-hip-flexor-stretch`
- `moves-starter-v1:therapeutic:band-ankle-dorsiflexion`
- `moves-starter-v1:therapeutic:band-ankle-eversion`
- `moves-starter-v1:therapeutic:band-ankle-inversion`
- `moves-starter-v1:therapeutic:band-shoulder-external-rotation`
- `moves-starter-v1:therapeutic:band-shoulder-internal-rotation`
- `moves-starter-v1:therapeutic:band-terminal-knee-extension`
- `moves-starter-v1:therapeutic:bent-knee-calf-raise`
- `moves-starter-v1:therapeutic:bird-dog`
- `moves-starter-v1:balance:anteroposterior-weight-shift`
- `moves-starter-v1:balance:controlled-march`
- `moves-starter-v1:balance:heel-to-toe-walk`
- `moves-starter-v1:breathing:paced-longer-exhale`
- `moves-starter-v1:breathing:prone-crocodile-breathing`
- `moves-starter-v1:breathing:seated-360-breathing`

## 7. Rejestracja źródła

Rzeczywisty obecny request API:

```json
{
  "code": "MOVES_STARTER_V1",
  "displayName": "Moves starter exercises V1",
  "defaultLocale": "pl-PL",
  "licenseCode": "MOVES-INTERNAL-AUTHORING-1.0",
  "licenseVerified": true
}
```

Aktualne API nie przechowuje `source_type`, osobnej flagi `redistribution_allowed` ani tekstu licencji. `INTERNAL_AUTHORING` jest więc intencją operacyjną opisaną w tym dokumencie; prawo do redystrybucji jest zapisane w każdym rekordzie.

## 8. Pliki

- `src/main/resources/reference/exercises/v1/moves-starter-exercises-v1.jsonl` — 120 rekordów.
- `...-coverage.csv` — rozłączne segmenty i klasyfikacje.
- `...-evidence.csv` — źródła, zastosowanie i ograniczenia.
- `src/main/resources/db/migration/V027__seed_starter_anatomy_reference_v1.sql` — opublikowany podzbiór anatomii.
- `src/main/resources/db/migration/V028__extend_exercise_import_dictionaries.sql` — minimalne rozszerzenie pozycji i sprzętu.
- `scripts/validate-moves-starter-exercises-v1.py` — walidator statyczny.
- `src/test/java/com/motionecosystem/exerciseimport/MovesStarterExercisesV1IntegrationTest.java` — test realnego pipeline’u do uruchomienia w repo.
- `docs/moves-starter-exercises-v1.md` — ten raport.
- `docs/contracts/exercise-import-1.0.schema.json` — kopia kontraktu użyta przez walidację pakietu.

## 9. Ograniczenia V1

- Brak ćwiczeń ze sztangą, wyciągiem, drążkiem, piłką i rollerem; nie były konieczne dla pierwszego katalogu.
- Brak ruchów eksplozywnych, plyometrii i ćwiczeń o dużej złożoności.
- Podzbiór anatomii jest katalogowy i celowo mniejszy niż docelowa taksonomia anatomiczna.
- Brak mediów, diagnoz, przeciwwskazań i reguł safety.
- Wszystkie ćwiczenia terapeutyczne pozostają draftami do niezależnej recenzji.
- Import integracyjny i Maven verify muszą zostać uruchomione po nałożeniu pakietu na aktualny checkout `main`.
