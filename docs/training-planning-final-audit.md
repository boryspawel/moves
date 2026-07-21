# Końcowy audyt vertical slice planowania treningowego

Data audytu: 2026-07-21

## Zakres ukończony

Zrealizowano prompty 1–10 serii `moves-codex-implementation-prompts-training-planning.md`: granice foundation, taksonomię anatomii, wersjonowany profil ćwiczenia, rewizje planu, planned load, capabilities i consent, Safety V2, atomową aktywację, executed load z alertami oraz współpracę trener–fizjoterapeuta.

Audyt capability potwierdza użycie wszystkich capabilities opieki nad uczestnikiem: `PLAN_PERFORMANCE`, `PLAN_FUNCTIONAL_RECOVERY`, `SET_PERFORMANCE_BUDGET`, `SET_CLINICAL_RESTRICTION`, `VIEW_EFFECTIVE_RESTRICTION`, `VIEW_CLINICAL_RATIONALE`, `ACKNOWLEDGE_PERFORMANCE_WARNING` i `OVERRIDE_CLINICAL_BLOCK`. Capability publikacji ćwiczeń i reguł safety są osobnymi pojęciami redakcyjnymi.

Plan zachowuje jednego ownera i jawnie zakresowanych collaborators. Zablokowana rewizja może zostać skierowana do fizjoterapeuty, który proponuje zmianę albo oznacza gotowość do ponownej walidacji. Clinical restriction ma historię rewizji. DTO trenera nie zawiera source type ani clinical rationale. Każda operacja ponownie sprawdza profil, relację, zgodę, purpose, capability i ownership lub collaboration scope.

## Weryfikacja techniczna

- Migracje V001–V015 są liniowe. Testy obejmują czystą bazę oraz upgrade realistycznego fixture z V005.
- Wewnętrzne relacje nowych tabel mają FK, CHECK constraints i indeksy; referencje między kontekstami pozostają UUID walidowanymi przez porty.
- Testy architektury kontrolują granice modułów i brak cykli. Audyt nie wykazał nowych cross-schema native queries, publicznych setterów, `FetchType.EAGER`, tymczasowych adapterów ani TODO/FIXME.
- Planned i executed load są osobnymi profilami wielowymiarowymi, z wersją kalkulatora/reguł; nie istnieje globalny score.
- OpenAPI jest generowane z uruchomionej aplikacji, a klient `typescript-fetch` przez OpenAPI Generator 7.24.0. Pliki wygenerowane nie są edytowane ręcznie.
- Backend CI używa Temurin Java 25 i wykonuje pełne `mvn --batch-mode verify`.

Ścieżki akceptacyjne są pokryte przez testy integracyjne modułów planowania, workflow, safety i execution:

- trener: performance plan, planned load, warning, acknowledgement, aktywacja, wykonanie i adherence;
- fizjoterapeuta: functional recovery, clinical restriction, assessment/override, aktywacja, pain/post24h, alert i kolejna rewizja;
- współpraca: hard block, minimalny envelope, review, zmiana lub override, revalidation i aktywacja bez clinical leak;
- self-directed: plan uczestnika, warning, nieomijalny clinical hard block i bezpieczna aktywacja;
- retry/concurrency, cofnięta zgoda, zakończona relacja, nieaktualny assessment, correction/reversal oraz recovery projekcji.

## Świadomie odłożone

- Nie dodano runtime'owego edytora reguł safety. Reguły są wersjonowanym kodem podlegającym review i release; ewentualny przyszły workflow publikacji musi egzekwować `PUBLISH_SAFETY_RULE` i nie może współdzielić uprawnień z publikacją treści ćwiczeń.
- Nie rozszerzano funkcjonalności Angulara. W tym zakresie aktualizowany jest wyłącznie snapshot OpenAPI i wygenerowany klient, zgodnie z zakresem serii backendowej.
- Nie modyfikowano `spec.md`.

## Blokery

Brak.
