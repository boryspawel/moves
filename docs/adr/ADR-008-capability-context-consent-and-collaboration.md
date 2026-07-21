# ADR-008: Jawny kontekst capability, zgoda i współpraca przy planie

Status: accepted

## Kontekst

Jedno konto specjalisty może mieć zweryfikowane zakresy trenera i fizjoterapeuty. Sama rola techniczna z tokenu nie określa celu operacji ani zakresu danych uczestnika. Plan ma jednego właściciela, ale jego bezpieczna walidacja może wymagać współpracy obu profesji.

## Decyzja

- Każda operacja specjalisty wskazuje jawny `ActingContext` i purpose.
- Centralny port autoryzacji sprawdza aktywne konto i profil, zweryfikowany zakres zawodowy, aktywną relację, aktualną zgodę oraz wymagane capability.
- Cofnięcie zgody lub zakończenie relacji blokuje następny odczyt i zapis; zachowane rekordy audytowe i domenowe podlegają polityce retencji.
- Plan ma dokładnie jednego ownera. Collaborator otrzymuje jawny zakres `VIEW_PLAN`, `EDIT_DRAFT` lub `REVIEW_SAFETY`, który nie zastępuje kontroli capability i zgody.
- Review zablokowanej rewizji jest osobnym, audytowanym workflow. Odpowiedź fizjoterapeuty jest referencją do decyzji, a nie kopią danych klinicznych.
- Trener otrzymuje wyłącznie effective safety envelope: target, side, kanał, limity, okres i bezpieczny explanation code. Source type, clinical rationale, diagnoza, wywiad i notatki nie są częścią tego DTO.
- Widok clinical rationale i zapis lub rewizja clinical restriction wymagają kontekstu fizjoterapeuty, relacji i osobnego zakresu zgody.
- Capability redakcyjne `PUBLISH_EXERCISE_CONTENT` i `PUBLISH_SAFETY_RULE` są oddzielone od capabilities opieki nad uczestnikiem.

## Konsekwencje

Autoryzacja zasobowa pozostaje w przypadkach użycia, a nie tylko w kontrolerach. Konto z dwiema profesjami nie dziedziczy przypadkowo szerszego dostępu. Współpraca pozostawia audytowalną historię bez ujawniania trenerowi danych klinicznych. Każdy nowy przypadek użycia musi wybrać capability, purpose i minimalny kontrakt odpowiedzi.
