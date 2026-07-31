# Exercise Set Builder UI

**Status: SET-04–SET-06 implemented; SET-06A diagnostics in progress.** This document is the canonical description of the
specialist-facing builder UI. The aggregate and HTTP contract remain defined by the
[exercise-set model](exercise-set-model.md); catalog search and selection are defined in
[exercise-catalog search](exercise-catalog-search.md). Delivery state and follow-on work
are maintained in [current state](../status/current-state.md) and the
[exercise-set builder roadmap](../roadmap/exercise-set-builder.md).

## Scope and routes

The UI is available only to a completed-onboarding user with the `SPECIALIST` role. It
does not show or collect participant, appointment, session, execution, plan, or safety
data. A set is an independent, owner-scoped definition.

| Route                                                    | Screen            | Behaviour                                                                                          |
| -------------------------------------------------------- | ----------------- | -------------------------------------------------------------------------------------------------- |
| `/exercise-sets`                                         | List              | Lists owned sets, resolves the latest version and links to draft edit or published read-only view. |
| `/exercise-sets/new`                                     | Create            | Calls `ApiFacade.exerciseSets.create()` and redirects to the first draft editor.                   |
| `/exercise-sets/:exerciseSetId/versions/:versionId/edit` | Draft editor      | Edits draft metadata and items.                                                                    |
| `/exercise-sets/:exerciseSetId/versions/:versionId`      | Published version | Uses the same presenter in read-only mode.                                                         |

The route layer uses the existing authentication, completed-onboarding and specialist
role guards. Generated API models and `ApiFacade.exerciseSets` are the only transport
surface; the UI does not duplicate API DTOs or edit the generated client.

```mermaid
flowchart TD
  L[Exercise-set list] --> N[/exercise-sets/new]
  N --> C[create first draft]
  C --> E[Draft editor]
  L -->|latest draft| E
  L -->|latest published| R[Read-only version]
  E --> P[ExercisePickerComponent]
  P -->|ExerciseSelection.exerciseVersionId| A[Typed item request]
  A --> E
  E -->|PublishRequest.expectedVersion| R
```

## Components and interaction

`ExerciseSetListPage` owns loading and presentation of `SetView` and its version
summaries. `ExerciseSetEditorPage` is the container for metadata, phase sections, item
actions, a compact summary, the picker hand-off and the publish confirmation dialog.
`DoseEditorComponent` is the focused reactive-form presenter for generated `Dose`.

The editor groups items by the generated phase enum:

- `PREPARATION`
- `MAIN`
- `ACCESSORY`
- `COOLDOWN`

Each draft phase exposes an explicit add action. Existing items expose keyboard-operable
move-up, move-down, edit-dose and remove buttons; ordering therefore does not depend on
drag and drop. Published versions render the same phase and summary information but do
not expose mutation controls.

## Selection and typed doses

The builder reuses `ExercisePickerComponent` unchanged. The picker remains responsible
for catalog search, filters, preview and emitting its exact `ExerciseSelection` contract.
The builder consumes its `exerciseVersionId` when forming generated `ItemRequest`; it
does not infer a different version or persist picker presentation data.

`DoseEditorComponent` narrows the generated discriminated union by `Dose.type`:
`STRENGTH`, `AEROBIC`, `ISOMETRIC`, `MOBILITY`, `STRETCH`, and `BREATHING`. Its reactive
controls require all values exposed for the selected dose shape before add or update is
enabled. It never supplies default values for missing dose fields. Editing an existing
dose similarly requires deliberate entry of a complete new dose rather than silently
reusing or inventing values.

## Draft persistence, conflicts and publication

Metadata controls debounce for 500 ms before calling the generated metadata operation.
Item add, update, remove and move commands also use generated request types. Every draft
mutation carries the current `VersionView.lockVersion` as the optimistic-lock token, and
the editor replaces its state with the returned view before the next command; it neither
increments the token locally nor fetches a version solely to refresh it.
When a request receives a 409 conflict, the editor announces the conflict and presents
two explicit choices: refresh the version from the server, or cancel the local metadata
change. It does not automatically overwrite concurrent work.

Publication is an explicit confirmation dialog. The generated
`publishVersion({ setId, versionId, publishRequest: { expectedVersion } })` call sends
the same current lock token; after a successful publish the UI navigates to the canonical
published read-only route. Publication remains available niezależnie od tytułu, profilu,
liczby pozycji, kolejności i sugestii. Przycisk może być nieaktywny wyłącznie podczas
zapisu, publikowania lub rzeczywistego błędu technicznego. Serwer odrzuca wyłącznie
błędy techniczne, w tym 403, 409, nieistniejącą wersję ćwiczenia, niespójny request i
próbę zmiany opublikowanej wersji.

## Sugestie do zestawu SET-05A i ekspozycja SET-06

Backend udostępnia sprawdzenie drafu i utrwalony wynik opublikowanej wersji pod endpointem
`.../versions/{versionId}/analysis`; szczegóły opisuje [analiza Exercise Set](exercise-set-analysis.md).
Builder pokazuje prostą sekcję „Sugestie do zestawu” z neutralnymi komunikatami, na
przykład o braku tytułu, profilu lub ćwiczeń, częstych zmianach sprzętu, kolejności albo
niedostępnym szacowanym czasie. Sugestia może prowadzić akcją „Przejdź do pola”, lecz
nigdy nie blokuje publikacji. W głównym widoku nie pokazuje polityki, jej wersji, czasu
analizy, kodów reguł, statusu „Zablokowany”, sekcji „Blokery”, komunikatów angielskich,
ani technicznych danych completeness i mapping version.

Brak tytułu jest prezentowany jako „Zestaw bez nazwy”, brak profilu jako „Nie określono”,
a pusta sekcja opisu nie jest renderowana. Są to wartości prezentacyjne, nie dane
zapisywane do domeny.

SET-06 dodaje niezależny od analizy strukturalnej odczyt „Ekspozycja i wzorce” z
`.../versions/{versionId}/anatomy`. Sekcja ma własne loading/error/stale, odświeżanie
klawiaturowym przyciskiem oraz przyjmuje draftową odpowiedź wyłącznie dla aktualnej
wersji blokady. Kanały, udziały, rozbicia, dowody, wzorce, findings i braki danych są
informacją odczytową. Kanoniczne ograniczenie i granicę snapshotu opisuje
[ekspozycja anatomiczna zestawu](exercise-set-anatomy-exposure.md).

SET-06A rozszerza ten wyłącznie odczytowy panel o diagnostykę wersjonowanego
mapowania wizualnego: kompletność mapowania oraz przekazane przez API kody regionów,
warstwę i widok. Panel pobiera aktywne metadane z
`GET /api/v1/anatomy/visual-regions` przez wygenerowany `ApiFacade`, ale nie buduje
mapy ciała i nie zawiera lokalnego słownika anatomii. Przy braku pełnego przypisania
pokazuje: „Część struktur nie ma jeszcze przypisanego regionu mapy ciała. Wyniki
tabelaryczne pozostają dostępne.”

SET-06B udostępnia również `visualRegionExposures`, przygotowane przez backend do
przyszłego prototypu mapy. Builder nadal nie wykonuje mapowania struktur ani progów;
komponent mapy może łączyć SVG tylko po `visualRegionCode`. Ponieważ SVG V1 obejmuje
12/32 regionów, pozostałe 20 musi pozostać w niezależnym zestawieniu tekstowym.

## Accessibility and responsive behaviour

The pages include loading/error/status announcements through live regions, semantic
headings and lists, labelled form controls, native dialog semantics and focusable page
main content. All primary actions are regular buttons or links with visible text, so
selection, saving, ordering, deletion and publication can be completed with a keyboard.

The layout uses a two-column editor and summary at wider sizes, reducing to a single
column on narrow screens. Dose controls and item actions wrap rather than requiring
horizontal interaction. The existing picker retains its own responsive, reduced-motion
and preview-focus behaviour.

## SET-07B partial body map

The reusable `app-body-map` is used by the common version editor route for both drafts
and published-version reads. Its only map input is persisted `visualRegionExposures`; it
uses exact `visualRegionCode` values to address SVG geometry and displays the backend
concentration band, share and raw value without thresholds or aggregation in Angular.
The map is explicitly a 12/32-region technical prototype. Every returned exposure stays
in the textual list; a code without SVG geometry receives `Brak geometrii w mapie V1`.
The component presents the stored visual mapping version and must not reinterpret a
historical result using live reference metadata. It is a qualitative presentation, not a
biomechanical, clinical-risk, force or safety assessment.

### SET-07B1 runtime QA

`web/e2e/specialist-body-map.spec.ts` uses a real Compose backend, a specialist OIDC
storage state generated by `web/e2e/specialist.global-setup.ts`, and an isolated
exercise-set fixture created through public APIs. Run `npm run test:e2e --
specialist-body-map` after `docker compose up --build` is healthy. `E2E_BASE_URL` is
optional and defaults to `http://localhost:4200`; required local credentials are
`E2E_SPECIALIST_USERNAME` and `E2E_SPECIALIST_PASSWORD`. Setup uses the authenticated
public catalog API (`POST /api/v2/exercises/search`, then version previews) and evaluates
deterministic one- and two-version published candidates through the draft anatomy endpoint
until it finds the smallest set covering all map requirements. `E2E_BODY_MAP_EXERCISE_VERSION_IDS` is an optional
comma-separated override for a known local catalog. The anatomy endpoint remains the
final authority: setup fails with an actionable override hint unless the created fixture
yields FRONT, BACK, both channels, mapped and no-geometry visual regions. No secret is
stored in Git. The test opens the draft builder
before the immutable published/history route at 1440×900, 390×844 and 320×700,
including SVG/API failures, console/page errors, keyboard focus return and
document-level horizontal overflow.
