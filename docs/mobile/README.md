# Moves Mobile — aplikacja Android dla specjalisty

**Status:** zaakceptowany kierunek UX i Mobile MVP, dokumentacja przed implementacją. **Data:** 2026-11-12.

## Dokumentacja i prototyp

- [01 — Product MVP i podział Android/Web](01-product-mvp.md)
- [02 — User journeys, nawigacja i dziewięć ekranów](02-information-architecture.md)
- [03 — SOTA, architektura i bezpieczeństwo](03-architecture-security.md)
- [04 — Etapowy plan wdrożenia i backlog](04-implementation-plan.md)
- [Interaktywny prototyp dziewięciu ekranów](prototype/index.html) — demonstrator HTML z fikcyjnymi danymi, **bez backendu**, nie jest kodem produkcyjnym Androida.

Prototyp jest samowystarczalnym plikiem HTML. Zrzuty PNG z sesji projektowej nie są załączane do repozytorium; komplet dziewięciu ekranów można otwierać przez menu prototypu.

## Źródła AS-IS

- [Stan bieżący](../status/current-state.md), [architektura techniczna](../architecture/technical-overview.md).
- [ADR-004 REST/OpenAPI](../adr/ADR-004-rest-openapi.md), [ADR-008 kontekst/capability/zgoda](../adr/ADR-008-capability-context-consent-and-collaboration.md).
- [Dokumentacja uczestnika](../architecture/participant-documentation.md), [lifecycle terminu](../architecture/appointment-lifecycle-history.md).
- [Snapshot OpenAPI](../../web/openapi/openapi.json) oraz kontrolery w src/main/java/com/motionecosystem.

## Decyzja

**Android:** operacyjna obsługa całej wizyty od agendy po zakończenie. **Web:** projektowanie planów, zestawów ćwiczeń, analizy i administracja. **Backend:** jedyne źródło prawdy dla danych, praw dostępu, safety, zgód, planów i zamknięcia wizyty.

Implementacja: natywny Kotlin + Jetpack Compose, online-first. Brak modułu Android w stanie AS-IS. M0 jest wymagany przed budową ekranów: Keycloak/OIDC, kontrakty, polityka PHI i szkiców.
