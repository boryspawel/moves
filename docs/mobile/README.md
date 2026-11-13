# Moves Mobile — aplikacja Android dla specjalisty

**Status:** M1 — fundament Android/APK i kod OIDC gotowe; prawdziwe logowanie na urządzeniu niepotwierdzone, ekrany biznesowe poza zakresem. **Data:** 2026-11-12.

## Dokumentacja i prototyp

- [01 — Product MVP i podział Android/Web](01-product-mvp.md)
- [02 — User journeys, nawigacja i dziewięć ekranów](02-information-architecture.md)
- [03 — SOTA, architektura i bezpieczeństwo](03-architecture-security.md)
- [04 — Etapowy plan wdrożenia i backlog](04-implementation-plan.md)
- [05 — Wyniki audytu M0 (2026-11-12)](05-m0-assessment.md): API, Keycloak, zgody, minimalizacja danych i blokery.
- [06 — MOB-004: spike generatora Kotlin OpenAPI (2026-11-12)](06-mob-004-openapi-generator.md): pełny generator pozostaje NO-GO z powodu `Dose`; M1 używa małego, ręcznie typowanego adaptera.
- [07 — M1: fundament Android i build APK](07-m1-android-build.md): konfiguracja CLI/CI, adapter REST i granice zakresu.
- [08 — MOB-011: OIDC/Keycloak](08-m1-oidc-keycloak.md): PKCE, konfiguracja dev/prod, bramki odbioru.
- [Interaktywny prototyp dziewięciu ekranów](prototype/index.html) — demonstrator HTML z fikcyjnymi danymi, **bez backendu**, nie jest kodem produkcyjnym Androida.

Prototyp jest samowystarczalnym plikiem HTML. Zrzuty PNG z sesji projektowej nie są załączane do repozytorium; komplet dziewięciu ekranów można otwierać przez menu prototypu.

## Źródła AS-IS

- [Stan bieżący](../status/current-state.md), [architektura techniczna](../architecture/technical-overview.md).
- [ADR-004 REST/OpenAPI](../adr/ADR-004-rest-openapi.md), [ADR-008 kontekst/capability/zgoda](../adr/ADR-008-capability-context-consent-and-collaboration.md).
- [Dokumentacja uczestnika](../architecture/participant-documentation.md), [lifecycle terminu](../architecture/appointment-lifecycle-history.md).
- [Snapshot OpenAPI](../../web/openapi/openapi.json) oraz kontrolery w src/main/java/com/motionecosystem.

## Decyzja

**Android:** operacyjna obsługa całej wizyty od agendy po zakończenie. **Web:** projektowanie planów, zestawów ćwiczeń, analizy i administracja. **Backend:** jedyne źródło prawdy dla danych, praw dostępu, safety, zgód, planów i zamknięcia wizyty.

Implementacja: natywny Kotlin + Jetpack Compose, online-first. Stan AS-IS przed M1 nie miał modułu Android; M1 dodał minimalny moduł i build APK. **M0 wykonał statyczny audyt źródeł, nie potwierdził logowania na emulatorze.** Zapis i mobilna prezentacja pomiarów biomedycznych S08 pozostają wyłączone z bieżącego MVP do czasu egzekwowania właściwej zgody na backendzie. Nie tworzymy w mobile mechanizmu zgód; dokumentacja medyczna ogranicza się do notatek z informacji ustnych, bez załączników, wyników badań ani skanów.
