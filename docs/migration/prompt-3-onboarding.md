# Prompt 3 — migracja onboardingu

## Pochodzenie

- repo: `../ruszsie`;
- implementacja bazowa: `5fecadfa4ab99a9f27f26084b19013ef22aafbc7`;
- późniejsze formatowanie modeli: `2d7656e8b1b8d5da145a1bf570a6985fa6b0b033`;
- stan źródła przy migracji: czysty `main`, HEAD `2d7656…`.

## Mapowanie

| Źródło | Cel | Decyzja |
|---|---|---|
| `onboarding.LegalAcknowledgement` | `consent.LegalAcknowledgement` | PORT unikalności konto/typ/wersja; nowe neutralne typy |
| `AcknowledgementType` | `TERMS_OF_SERVICE`, `PRIVACY_NOTICE` | REWRITE; usunięto wymagane AGE_18 i COMMUNITY_RULES |
| `UserProfile` | osobne `ParticipantProfile`, `SpecialistProfile` | REWRITE zależny od rodzaju profilu |
| `AgeBand` | brak | REJECT; wiek nie jest obowiązkowym polem pierwszego slice |
| `ActivityPreference`, `ActivityType` | brak | REJECT; model matchingu przyszłego RuszSię |
| `RecurringAvailability` | `availability.RecurringSlot` | PORT + walidacja nakładania |
| `OnboardingService.Stage` | rozszerzalny `Stage` + `missingSteps` | REWRITE; stan zależny od roli |
| `OnboardingController` | `/api/v1/onboarding` | REWRITE; subject wyłącznie z Keycloak JWT |
| `UserAccount` | `identityaccess.PrincipalAccount` | REWRITE; lokalne UUID + unikalny tekstowy `sub` |
| `OnboardingIntegrationTests` | `OnboardingIntegrationTest` | PORT scenariuszy na Boot 4.1/PostgreSQL 18 |
| prototypowe JWT/sesje/Google | brak | REJECT |

## Zachowane zachowania

- idempotentny zapis bieżącej wersji potwierdzenia;
- ponowne wyliczanie stanu z danych źródłowych;
- replace semantics dostępności;
- IANA time zone i granice `end > start`;
- brak możliwości wskazania obcego konta w body;
- odmowa dla konta nieaktywnego;
- PostgreSQL/Flyway/Testcontainers/MockMvc.

## Nowe zachowania

- wybór `PARTICIPANT` albo `SPECIALIST` jest idempotentny i nieodwracalny bez przyszłego zatwierdzonego procesu;
- trener/fizjoterapeuta to `SpecialistKind`, nie wyłącznie rola techniczna;
- state zwraca wszystkie `missingSteps` i pierwszy bieżący `stage`;
- dostępność odrzuca nakładające się przedziały tego samego dnia i strefy;
- istotne zmiany trafiają do append-only `audit.audit_event`.

## Walidacja

`mvn -q verify` z Java 25: sukces. Łącznie 10 testów, 0 failures/errors/skips. Test onboardingowy zawiera trzy scenariusze integracyjne. Flyway wykonał V001 i V002 na PostgreSQL 18.4.

## Dług i nierozstrzygnięcia

- brak modelu opiekuna małoletniego — celowo, do decyzji prawnej/domenowej;
- community rules pojawi się dopiero w opt-in gamifikacji;
- nie wdrożono revoke/expiry dla potwierdzeń prawnych, ponieważ semantyka dokumentów i okresy ważności nie są zatwierdzone;
- walidacja overlap między różnymi strefami czasowymi wymaga reguły biznesowej; dziś porównywane są sloty tego samego dnia i tej samej strefy;
- auto-provision lokalnego konta przy pierwszym poprawnym tokenie wymaga później obsługi wyścigu na unikalnym `sub`;
- specjalista nie ma jeszcze weryfikacji kwalifikacji ani organizacji.
